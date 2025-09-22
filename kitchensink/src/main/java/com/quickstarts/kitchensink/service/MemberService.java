package com.quickstarts.kitchensink.service;

import com.mongodb.DuplicateKeyException;
import com.quickstarts.kitchensink.dto.CreateMemberRequestDTO;
import com.quickstarts.kitchensink.dto.MemberResponseDTO;
import com.quickstarts.kitchensink.dto.UpdateMemberRequest;
import com.quickstarts.kitchensink.exception.ConflictException;
import com.quickstarts.kitchensink.exception.NotFoundException;
import com.quickstarts.kitchensink.model.Member;
import com.quickstarts.kitchensink.model.UserInfo;
import com.quickstarts.kitchensink.repo.MemberRepository;
import com.quickstarts.kitchensink.repo.UserInfoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

import static com.quickstarts.kitchensink.dto.CreateMemberRequestDTO.to;
import static com.quickstarts.kitchensink.dto.enums.Role.MEMBER;
import static com.quickstarts.kitchensink.utils.MemberUtils.generateTempPassword;
import static com.quickstarts.kitchensink.utils.MemberUtils.toProperCase;
import static java.util.Optional.ofNullable;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final EmailService emailService;
    private final UserInfoRepository userInfoRepository;
    private final PasswordEncoder encoder;

    @Value("${app.login.url:http://localhost:8080/login}")
    private String loginUrl;

    @Transactional
    public Member registerMember(final CreateMemberRequestDTO createMemberRequestDTO) {
        final var member = to(createMemberRequestDTO);
        final var savedMember = memberRepository.save(member);
        final var tempPwd = generateTempPassword();
        final var user = buildUserForNewMember(createMemberRequestDTO, tempPwd, savedMember);
        userInfoRepository.save(user);

        notifyMemberWithWelcomeEmail(createMemberRequestDTO, tempPwd);
        return savedMember;
    }

    @Transactional
    public MemberResponseDTO update(final String memberId, final UpdateMemberRequest updateMemberRequest) {
        final var member = getMemberInfo(memberId);
        final var name = toProperCase(updateMemberRequest.name().trim().replaceAll("\\s+", " "));
        final var email = updateMemberRequest.email().trim().toLowerCase();
        final var phoneNumber = updateMemberRequest.phoneNumber();
        validateUpdateRequest(memberId, email, phoneNumber);
        final var memberToUpdate = getUpdatedMember(updateMemberRequest, member, name, email, phoneNumber);

        try {
            final var responseDTO = MemberResponseDTO.from(memberRepository.save(memberToUpdate));
            //notify members that their details are updated
            emailService.notifyMemberUpdated(email, name);
            return responseDTO;
        } catch (final DuplicateKeyException dk) {
            final var msg = ofNullable(dk.getMessage()).orElse("");
            if (msg.contains("email")) {
                throw new ConflictException("Email already in use");
            }
            if (msg.contains("phoneNumber")) {
                throw new ConflictException("Phone already in use");
            }
            if (msg.contains("index: _id_")) {
                throw new ConflictException("Duplicate memberId; update attempted as insert");
            }
            throw new ConflictException("A unique constraint was violated");
        }
    }

    @Transactional
    public void delete(final String id) {
        deleteUserByMemberId(id);
        final var member = getMemberInfo(id);
        //notify members that their account is deleted
        emailService.notifyMemberDeleted(member.getEmail(), member.getName());
        memberRepository.deleteById(id);
    }

    public Member getMemberInfo(final String memberId) {
        return memberRepository.findById(memberId).orElseThrow(() -> new NotFoundException("Member not found"));
    }

    private void validateUpdateRequest(final String memberId, final String email, final String phoneNumber) {
        if (memberRepository.existsByEmailAndIdNot(email, memberId)) {
            throw new ConflictException("Email already in use");
        }
        if (memberRepository.existsByPhoneNumberAndIdNot(phoneNumber, memberId)) {
            throw new ConflictException("Phone already in use");
        }
    }

    private void deleteUserByMemberId(final String memberId) {
        userInfoRepository.findByMemberId(memberId).ifPresent(user -> userInfoRepository.deleteById(user.getId()));
    }

    private void notifyMemberWithWelcomeEmail(final CreateMemberRequestDTO createMemberRequestDTO,
                                              final String tempPwd) {
        emailService.sendWelcomeWithTempPassword(createMemberRequestDTO.email(), createMemberRequestDTO.name(),
                tempPwd,
                loginUrl
        );
    }

    private UserInfo buildUserForNewMember(final CreateMemberRequestDTO createMemberRequestDTO,
                                           final String tempPwd,
                                           final Member savedMember) {
        return UserInfo.builder()
                .userName(createMemberRequestDTO.email())
                .password(encoder.encode(tempPwd))
                .roles(Set.of(MEMBER.name()))
                .mustChangePassword(true)
                .memberId(savedMember.getId())
                .build();
    }

    private static Member getUpdatedMember(
            final UpdateMemberRequest updateMemberRequest,
            final Member member,
            final String name,
            final String email,
            final String phoneNumber
    ) {
        member.setName(name);
        member.setEmail(email);
        member.setPhoneNumber(phoneNumber);
        member.setAge(updateMemberRequest.age());
        member.setPlace(updateMemberRequest.place().trim().replaceAll("\\s+", " "));
        return member;
    }
}
