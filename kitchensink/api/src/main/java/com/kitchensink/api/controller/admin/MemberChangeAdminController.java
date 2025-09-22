package com.kitchensink.api.controller.admin;

import com.kitchensink.core.notification.email.service.EmailService;
import com.kitchensink.persistence.member.model.MemberChangeRequest;
import com.kitchensink.persistence.member.repo.MemberChangeRequestRepository;
import com.kitchensink.persistence.member.repo.MemberRepository;
import com.kitchensink.persistence.user.repo.UserInfoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import static com.kitchensink.persistence.common.dto.enums.ChangeType.UPDATE;
import static com.kitchensink.persistence.common.dto.enums.Status.*;
import static java.net.URI.create;
import static java.time.Instant.now;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.SEE_OTHER;
import static org.springframework.http.ResponseEntity.status;

@RestController
@RequestMapping("/admin/requests")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ADMIN')")
public class MemberChangeAdminController {

    private static final String ADMIN_REQUESTS = "/admin/requests";
    private final MemberChangeRequestRepository memberChangeRequestRepository;
    private final MemberRepository memberRepo;
    private final UserInfoRepository userRepo;
    private final EmailService emailService;

    private static ResponseEntity<Void> seeOther(final String path) {
        return status(SEE_OTHER).location(create(path)).build();
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<Void> approve(@PathVariable final String id) {
        doApprove(id);
        return seeOther(ADMIN_REQUESTS);
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<Void> reject(@PathVariable final String id,
                                       @RequestParam(required = false) final String reason) {
        doReject(id, reason);
        return seeOther("/admin/requests");
    }

    private void doApprove(final String memberId) {
        final var memberChangeRequest = memberChangeRequestRepository.findById(memberId).orElseThrow();
        final var memberEmail = memberChangeRequest.getMemberEmail();
        if (memberChangeRequest.getStatus() != PENDING) {
            throw new ResponseStatusException(CONFLICT, "Not pending");
        }

        if (memberChangeRequest.getType() == UPDATE) {
            final var member = memberRepo.findById(memberChangeRequest.getMemberId()).orElseThrow();
            final var memberUpdateDTO = memberChangeRequest.getRequested();
            if (memberUpdateDTO.name() != null) member.setName(memberUpdateDTO.name());
            if (memberUpdateDTO.phoneNumber() != null) member.setPhoneNumber(memberUpdateDTO.phoneNumber());
            if (memberUpdateDTO.age() != 0) member.setAge(memberUpdateDTO.age());
            if (memberUpdateDTO.place() != null) member.setPlace(memberUpdateDTO.place());
            if (memberUpdateDTO.email() != null) {
                member.setEmail(memberUpdateDTO.email().trim().toLowerCase());
                final var userInfo = userRepo.findByUserName(memberChangeRequest.getMemberEmail()).orElseThrow();
                userInfo.setUserName(member.getEmail());
                userRepo.save(userInfo);
            }
            memberRepo.save(member);
            // Notify member (diff between before & requested)
            emailService.notifyMemberUpdateApproved(memberEmail, memberChangeRequest);
        } else {
            // DELETE: remove login + member
            // DELETE: send email first (we still have the address), then remove data
            final var member = memberRepo.findById(memberChangeRequest.getMemberId()).orElse(null);
            emailService.notifyMemberDeleteApproved(memberEmail, member != null ? member.getName() : null);
            deleteUser(memberChangeRequest);
            memberRepo.deleteById(memberChangeRequest.getMemberId());
        }
        memberChangeRequest.setStatus(APPROVED);
        memberChangeRequest.setReviewedAt(now());
        memberChangeRequestRepository.save(memberChangeRequest);
    }

    private void doReject(final String memberId, final String reason) {
        final var memberChangeRequest = memberChangeRequestRepository.findById(memberId).orElseThrow();
        memberChangeRequest.setStatus(REJECTED);
        memberChangeRequest.setRejectionReason(reason);
        memberChangeRequest.setReviewedAt(now());
        emailService.notifyMemberRejected(memberChangeRequest.getMemberEmail(), reason);
        memberChangeRequestRepository.save(memberChangeRequest);
    }

    private void deleteUser(final MemberChangeRequest memberChangeRequest) {
        userRepo.findByUserName(memberChangeRequest.getMemberEmail()).ifPresent(userRepo::delete);
    }
}
