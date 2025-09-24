package com.kitchensink.core.admin.service;

import com.kitchensink.core.notification.email.service.impl.SmtpEmailService;
import com.kitchensink.persistence.common.dto.enums.Status;
import com.kitchensink.persistence.member.model.Member;
import com.kitchensink.persistence.member.model.MemberChangeRequest;
import com.kitchensink.persistence.member.repo.MemberChangeRequestRepository;
import com.kitchensink.persistence.member.repo.MemberRepository;
import com.kitchensink.persistence.user.repo.UserInfoRepository;
import com.mongodb.DuplicateKeyException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static com.kitchensink.persistence.common.dto.enums.ChangeType.DELETE;
import static com.kitchensink.persistence.common.dto.enums.ChangeType.UPDATE;
import static com.kitchensink.persistence.common.dto.enums.Status.*;
import static java.time.Instant.now;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChangeRequestReviewService {

    private final MemberChangeRequestRepository changeRequestRepository;
    private final MemberRepository memberRepo;
    private final UserInfoRepository userRepo;
    private final SmtpEmailService emailService;

    @Transactional
    public void approve(final String requestId) {
        final var changeRequest = getPendingRequestOrThrow(requestId);

        if (changeRequest.getType() == UPDATE) {
            approveUpdate(changeRequest);
        } else if (changeRequest.getType() == DELETE) {
            approveDelete(changeRequest);
        } else {
            throw new ResponseStatusException(BAD_REQUEST, "Unsupported change type");
        }

        markReviewed(changeRequest, APPROVED, null);
    }

    @Transactional
    public void reject(final String requestId, final String reason) {
        final var existingChangeRequest = getExistingRequestOrThrow(requestId);
        if (existingChangeRequest.getStatus() != PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Request is not pending");
        }
        markReviewed(existingChangeRequest, REJECTED, reason);
        final var member = getMember(existingChangeRequest);
        final var memberName = member != null ? member.getName() : null;
        // Notify member
        notify(() -> emailService.notifyMemberRejected(existingChangeRequest.getMemberEmail(), reason, memberName));
    }

    /* ---------- helpers ---------- */

    private MemberChangeRequest getExistingRequestOrThrow(final String id) {
        return changeRequestRepository.findById(id).orElseThrow(() ->
                new ResponseStatusException(NOT_FOUND, "Change request not found"));
    }

    private MemberChangeRequest getPendingRequestOrThrow(final String id) {
        final var req = getExistingRequestOrThrow(id);
        if (req.getStatus() != PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Request is not pending");
        }
        return req;
    }

    private void approveUpdate(final MemberChangeRequest req) {
        final var member = memberRepo.findById(req.getMemberId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Member not found"));

        final var beforeEmail = member.getEmail(); // for diff/email hints
        final var dto = req.getRequested();

        if (dto.name() != null) member.setName(dto.name());
        if (dto.phoneNumber() != null) member.setPhoneNumber(dto.phoneNumber());
        member.setAge(dto.age());
        if (dto.place() != null) member.setPlace(dto.place());

        if (dto.email() != null) {
            final var normalized = dto.email().trim().toLowerCase();
            member.setEmail(normalized);

            // keep login username in sync
            final var userInfo = userRepo.findByUserName(req.getMemberEmail())
                    .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "User login not found"));
            userInfo.setUserName(normalized);
            userRepo.save(userInfo);
        }

        try {
            memberRepo.save(member);
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Unique constraint violation on member update");
        }

        notify(() -> emailService.notifyMemberUpdateApproved(beforeEmail, req));
    }

    private void approveDelete(final MemberChangeRequest memberChangeRequest) {
        // Send email first while we still have the address
        final var member = getMember(memberChangeRequest);
        final var memberName = member != null ? member.getName() : null;
        notify(() -> emailService.notifyMemberDeleteApproved(memberChangeRequest.getMemberEmail(), memberName));

        // Delete login + member data
        userRepo.findByUserName(memberChangeRequest.getMemberEmail()).ifPresent(userRepo::delete);
        memberRepo.deleteById(memberChangeRequest.getMemberId());
    }

    private Member getMember(final MemberChangeRequest memberChangeRequest) {
        return memberRepo.findById(memberChangeRequest.getMemberId()).orElse(null);
    }

    private void markReviewed(final MemberChangeRequest changeRequest,
                              final Status status,
                              final String reason) {
        changeRequest.setStatus(status);
        changeRequest.setRejectionReason(reason);
        changeRequest.setReviewedAt(now());
        changeRequestRepository.save(changeRequest);
    }

    /**
     * Never let email failures roll back DB changes. Log and continue.
     */
    private void notify(final Runnable emailCall) {
        try {
            emailCall.run();
        } catch (Exception ex) {
            log.error("Email notify failed: {}", ex.getMessage());
        }
    }
}
