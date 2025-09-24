package com.kitchensink.core.member.service;

import com.kitchensink.core.member.dto.MemberResponseDTO;
import com.kitchensink.core.notification.email.service.impl.SmtpEmailService;
import com.kitchensink.persistence.member.dto.MemberSnapshot;
import com.kitchensink.persistence.member.dto.MemberUpdateDTO;
import com.kitchensink.persistence.member.model.Member;
import com.kitchensink.persistence.member.model.MemberChangeRequest;
import com.kitchensink.persistence.member.repo.MemberChangeRequestRepository;
import com.kitchensink.persistence.member.repo.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static com.kitchensink.persistence.common.dto.enums.ChangeType.DELETE;
import static com.kitchensink.persistence.common.dto.enums.ChangeType.UPDATE;
import static java.time.Instant.now;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemberChangeRequestService {

    private final MemberRepository memberRepo;
    private final MemberChangeRequestRepository changeRequestRepository;
    private final SmtpEmailService emailService;

    private static void updateMemberBefore(final MemberChangeRequest memberChangeRequest, final Member member) {
        memberChangeRequest.setBefore(new MemberSnapshot(
                member.getName(),
                member.getEmail(),
                member.getPhoneNumber(),
                member.getAge(),
                member.getPlace()
        ));
    }

    private static MemberChangeRequest buildMemberChangeRequest(final String memberEmail,
                                                                final MemberUpdateDTO memberUpdateDTO,
                                                                final Member member) {
        return MemberChangeRequest.builder()
                .memberId(member.getId())
                .memberEmail(member.getEmail())
                .type(UPDATE)
                .requested(memberUpdateDTO)
                .submittedBy(memberEmail)
                .submittedAt(now())
                .build();
    }

    @Transactional
    public void submitProfileUpdate(final String memberEmail, final MemberUpdateDTO memberUpdateDTO) {
        final var member = getMember(memberEmail);

        if (!hasAnyChange(member, memberUpdateDTO)) {
            throw new ResponseStatusException(BAD_REQUEST, "No changes detected.");
        }

        final var memberChangeRequest = buildMemberChangeRequest(memberEmail, memberUpdateDTO, member);
        updateMemberBefore(memberChangeRequest, member);
        saveMemberChangeRequest(memberChangeRequest);
        try {
            emailService.notifyAdminUpdate(member, memberUpdateDTO);
        } catch (Exception e) {
            log.error("error occurred while notify submit profile update:{}", e.getMessage(), e);
            throw new IllegalStateException(e);
        }
    }

    @Transactional
    public void submitDeleteRequest(final String userEmail) {
        final var member = getMember(userEmail);
        final var memberChangeRequest = getMemberChangeRequest(userEmail, member);
        updateMemberBefore(memberChangeRequest, member);
        saveMemberChangeRequest(memberChangeRequest);
        emailService.notifyAdminDelete(member);
    }

    private static MemberChangeRequest getMemberChangeRequest(final String userEmail, final Member member) {
        return MemberChangeRequest.builder()
                .memberId(member.getId())
                .memberEmail(member.getEmail())
                .type(DELETE)
                .submittedBy(userEmail)
                .submittedAt(now())
                .build();
    }

    private boolean hasAnyChange(final Member member, final MemberUpdateDTO memberUpdateDTO) {
        return (memberUpdateDTO.name() != null && !memberUpdateDTO.name().equals(member.getName())) ||
                (memberUpdateDTO.phoneNumber() != null && !memberUpdateDTO.phoneNumber().equals(member.getPhoneNumber())) ||
                (memberUpdateDTO.age() != member.getAge()) ||
                (memberUpdateDTO.place() != null && !memberUpdateDTO.place().equals(member.getPlace())) ||
                (memberUpdateDTO.email() != null && !memberUpdateDTO.email().equalsIgnoreCase(member.getEmail()));
    }

    private void saveMemberChangeRequest(final MemberChangeRequest memberChangeRequest) {
        changeRequestRepository.save(memberChangeRequest);
    }

    public MemberResponseDTO findByEmail(final String email) {
        final var member = getMember(email);
        // map entity -> DTO (inline or via MapStruct)
        return MemberResponseDTO.builder()
                .id(member.getId())
                .name(member.getName())
                .email(member.getEmail())
                .phoneNumber(member.getPhoneNumber())
                .age(member.getAge())
                .place(member.getPlace())
                .registrationDate(member.getRegistrationDate())
                .build();
    }

    private Member getMember(final String memberEmail) {
        return memberRepo.findByEmail(memberEmail)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Member not found"));
    }
}
