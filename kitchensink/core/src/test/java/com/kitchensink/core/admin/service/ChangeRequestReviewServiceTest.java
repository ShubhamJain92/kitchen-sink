package com.kitchensink.core.admin.service;

import com.kitchensink.core.notification.email.service.impl.SmtpEmailService;
import com.kitchensink.persistence.common.dto.enums.ChangeType;
import com.kitchensink.persistence.common.dto.enums.Role;
import com.kitchensink.persistence.common.dto.enums.Status;
import com.kitchensink.persistence.member.dto.MemberUpdateDTO;
import com.kitchensink.persistence.member.model.Member;
import com.kitchensink.persistence.member.model.MemberChangeRequest;
import com.kitchensink.persistence.member.repo.MemberChangeRequestRepository;
import com.kitchensink.persistence.member.repo.MemberRepository;
import com.kitchensink.persistence.user.model.UserInfo;
import com.kitchensink.persistence.user.repo.UserInfoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.Set;

import static com.kitchensink.persistence.common.dto.enums.Status.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class ChangeRequestReviewServiceTest {

    @Mock
    private MemberChangeRequestRepository changeReqRepo;
    @Mock
    private MemberRepository memberRepo;
    @Mock
    private UserInfoRepository userRepo;
    @Mock
    private SmtpEmailService emailService;

    @Captor
    private ArgumentCaptor<MemberChangeRequest> reqCaptor;

    private ChangeRequestReviewService service;

    private static MemberChangeRequest newReq(String id, String memberId, String email, ChangeType type, Status status) {
        var r = new MemberChangeRequest();
        r.setId(id);
        r.setMemberId(memberId);
        r.setMemberEmail(email);
        r.setType(type);
        r.setStatus(status);
        return r;
    }

    private static UserInfo getUserInfo() {
        return UserInfo.builder()
                .userName("old@x.com")
                .password("pass")
                .id("id")
                .mustChangePassword(false)
                .roles(Set.of(Role.MEMBER.name()))
                .build();
    }

    private static Member getMember(final String id, final String name, final String mail) {
        return Member.builder()
                .id(id)
                .name(name)
                .age(20)
                .email(mail)
                .build();
    }

    // ------------------------- approve: DELETE -------------------------

    @BeforeEach
    void setUp() {
        service = new ChangeRequestReviewService(changeReqRepo, memberRepo, userRepo, emailService);
    }

    @Test
    void approve_delete_sendsEmail_first_thenDeletes_andMarksApproved() {
        var req = newReq("r1", "m1", "a@x.com", ChangeType.DELETE, PENDING);
        var member = getMember("m1", "Alice", "d@x.com");

        when(changeReqRepo.findById("r1")).thenReturn(Optional.of(req));
        when(memberRepo.findById("m1")).thenReturn(Optional.of(member));
        when(userRepo.findByUserName("a@x.com")).thenReturn(Optional.empty());

        // Capture invocation order: email -> repo deletes -> save reviewed
        InOrder inOrder = inOrder(emailService, userRepo, memberRepo, changeReqRepo);

        assertDoesNotThrow(() -> service.approve("r1"));

        inOrder.verify(emailService).notifyMemberDeleteApproved("a@x.com", "Alice");
        inOrder.verify(userRepo).findByUserName("a@x.com");
        inOrder.verify(memberRepo).deleteById("m1");
        inOrder.verify(changeReqRepo, atLeastOnce()).save(reqCaptor.capture());

        var saved = reqCaptor.getValue();
        assertEquals(APPROVED, saved.getStatus());
        assertNull(saved.getRejectionReason());
        assertNotNull(saved.getReviewedAt());
    }

    @Test
    void approve_delete_whenMemberNotFound_stillEmails_thenDeletesById_andMarksApproved() {
        var req = newReq("r2", "missing", "b@x.com", ChangeType.DELETE, PENDING);

        when(changeReqRepo.findById("r2")).thenReturn(Optional.of(req));
        when(memberRepo.findById("missing")).thenReturn(Optional.empty());
        when(userRepo.findByUserName("b@x.com")).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> service.approve("r2"));

        verify(emailService).notifyMemberDeleteApproved("b@x.com", null);
        verify(memberRepo).deleteById("missing");
        verify(changeReqRepo, atLeastOnce()).save(any());
    }

    @Test
    void approve_delete_emailFailure_isSwallowed_andFlowContinues() {
        var req = newReq("r3", "m3", "c@x.com", ChangeType.DELETE, PENDING);
        var member = getMember("m3", "Carl", "d@x.com");

        when(changeReqRepo.findById("r3")).thenReturn(Optional.of(req));
        when(memberRepo.findById("m3")).thenReturn(Optional.of(member));
        when(userRepo.findByUserName("c@x.com")).thenReturn(Optional.empty());

        doThrow(new RuntimeException("smtp down"))
                .when(emailService).notifyMemberDeleteApproved(anyString(), any());

        assertDoesNotThrow(() -> service.approve("r3"));

        verify(memberRepo).deleteById("m3");
        verify(changeReqRepo, atLeastOnce()).save(any());
    }

    // ------------------------- approve: UPDATE -------------------------

    @Test
    void approve_update_withoutEmailChange_updatesFields_savesMember_sendsUpdateEmail_andMarksApproved() {
        var req = newReq("r4", "m4", "old@x.com", ChangeType.UPDATE, PENDING);
        // DTO: name=null, phone="999", age=30, place="Pune", email=null (no login rename)
        var dto = new MemberUpdateDTO(null, "old@x.com", "9627713570", 30, "Pune");
        // we need req.getRequested() to return dto
        req.setRequested(dto);

        var member = getMember("m4", "Carl", "d@x.com");

        when(changeReqRepo.findById("r4")).thenReturn(Optional.of(req));
        when(memberRepo.findById("m4")).thenReturn(Optional.of(member));
        when(userRepo.findByUserName("old@x.com")).thenReturn(Optional.of(getUserInfo()));

        service.approve("r4");

        // member updated & saved
        assertEquals("9627713570", member.getPhoneNumber());
        assertEquals(30, member.getAge());
        assertEquals("Pune", member.getPlace());
        verify(memberRepo).save(member);

        // email sent with "beforeEmail"
        verify(emailService).notifyMemberUpdateApproved("old@x.com", req);

        // marked approved
        verify(changeReqRepo, atLeastOnce()).save(reqCaptor.capture());
        assertEquals(APPROVED, reqCaptor.getValue().getStatus());
    }

    @Test
    void approve_update_duplicateKey_throws409() {
        var req = newReq("r5", "m5", "d@x.com", ChangeType.UPDATE, PENDING);
        var dto = new MemberUpdateDTO(null, "abc@gmail.com", "9627713570", 30, "Pune");
        req.setRequested(dto);

        var member = getMember("m5", "name", "d@x.com");
        when(changeReqRepo.findById("r5")).thenReturn(Optional.of(req));
        when(memberRepo.findById("m5")).thenReturn(Optional.of(member));

        var ex = assertThrows(ResponseStatusException.class, () -> service.approve("r5"));
        assertEquals(404, ex.getStatusCode().value());
        verify(emailService, never()).notifyMemberUpdateApproved(anyString(), any());
    }

    @Test
    void approve_update_emailChange_missingUserLogin_throws404() {
        var req = newReq("r6", "m6", "old@x.com", ChangeType.UPDATE, PENDING);
        // email change triggers userRepo.findByUserName(...).orElseThrow(...)
        var dto = new MemberUpdateDTO(null, "abc@gmail.com", "9627713570", 30, "Pune");
        req.setRequested(dto);

        var member = getMember("m6", "name", "old@x.com");

        when(changeReqRepo.findById("r6")).thenReturn(Optional.of(req));
        when(memberRepo.findById("m6")).thenReturn(Optional.of(member));
        when(userRepo.findByUserName("old@x.com")).thenReturn(Optional.empty());

        var ex = assertThrows(ResponseStatusException.class, () -> service.approve("r6"));
        assertEquals(404, ex.getStatusCode().value());
        verify(memberRepo, never()).save(any());
        verify(emailService, never()).notifyMemberUpdateApproved(anyString(), any());
    }

    @Test
    void approve_unsupportedType_throws400() {
        var req = newReq("r8", "m8", "e@x.com", null, PENDING); // unsupported
        when(changeReqRepo.findById("r8")).thenReturn(Optional.of(req));

        var ex = assertThrows(ResponseStatusException.class, () -> service.approve("r8"));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void approve_whenNotPending_throws409() {
        var req = newReq("r9", "m9", "f@x.com", ChangeType.DELETE, APPROVED);
        when(changeReqRepo.findById("r9")).thenReturn(Optional.of(req));
        var ex = assertThrows(ResponseStatusException.class, () -> service.approve("r9"));
        assertEquals(409, ex.getStatusCode().value());
    }

    @Test
    void approve_whenRequestNotFound_throws404() {
        when(changeReqRepo.findById("missing")).thenReturn(Optional.empty());
        var ex = assertThrows(ResponseStatusException.class, () -> service.approve("missing"));
        assertEquals(404, ex.getStatusCode().value());
    }

    @Test
    void approve_update_whenMemberMissing_throws404() {
        var req = newReq("r10", "nope", "g@x.com", ChangeType.UPDATE, PENDING);
        req.setRequested(new MemberUpdateDTO(null, "abc@gmail.com", "9627713570", 30, "Pune"));
        when(changeReqRepo.findById("r10")).thenReturn(Optional.of(req));
        when(memberRepo.findById("nope")).thenReturn(Optional.empty());
        var ex = assertThrows(ResponseStatusException.class, () -> service.approve("r10"));
        assertEquals(404, ex.getStatusCode().value());
    }

    @Test
    void reject_pending_withReason_andName_marksRejected_sendsEmailWithName() {
        var req = newReq("r11", "m11", "h@x.com", ChangeType.DELETE, PENDING);
        var member = getMember("m11", "Helen", "d@x.com");

        when(changeReqRepo.findById("r11")).thenReturn(Optional.of(req));
        when(memberRepo.findById("m11")).thenReturn(Optional.of(member));

        service.reject("r11", "Incomplete");

        verify(changeReqRepo, atLeastOnce()).save(reqCaptor.capture());
        var saved = reqCaptor.getValue();
        assertEquals(REJECTED, saved.getStatus());
        assertEquals("Incomplete", saved.getRejectionReason());
        assertNotNull(saved.getReviewedAt());

        verify(emailService).notifyMemberRejected("h@x.com", "Incomplete", "Helen");
    }

    // ------------------------- reject -------------------------

    @Test
    void reject_pending_withoutReason_andWithoutMemberName_marksRejected_sendsEmailWithNullName() {
        var req = newReq("r12", "m12", "i@x.com", ChangeType.UPDATE, PENDING);

        when(changeReqRepo.findById("r12")).thenReturn(Optional.of(req));
        when(memberRepo.findById("m12")).thenReturn(Optional.empty()); // no name available

        service.reject("r12", null);

        verify(changeReqRepo, atLeastOnce()).save(any());
        verify(emailService).notifyMemberRejected("i@x.com", null, null);
    }

    @Test
    void reject_whenNotPending_throws409_andDoesNotSendEmail() {
        var req = newReq("r13", "m13", "j@x.com", ChangeType.DELETE, APPROVED);
        when(changeReqRepo.findById("r13")).thenReturn(Optional.of(req));

        var ex = assertThrows(ResponseStatusException.class, () -> service.reject("r13", "x"));
        assertEquals(409, ex.getStatusCode().value());

        verify(emailService, never()).notifyMemberRejected(anyString(), any(), any());
        verify(changeReqRepo, never()).save(any());
    }

    @Test
    void reject_whenRequestNotFound_throws404() {
        when(changeReqRepo.findById("missing")).thenReturn(Optional.empty());
        var ex = assertThrows(ResponseStatusException.class, () -> service.reject("missing", "x"));
        assertEquals(404, ex.getStatusCode().value());
    }

    @Test
    void reject_emailFailure_isSwallowed_andMarksRejected() {
        var req = newReq("r14", "m14", "k@x.com", ChangeType.UPDATE, PENDING);
        when(changeReqRepo.findById("r14")).thenReturn(Optional.of(req));
        when(memberRepo.findById("m14")).thenReturn(Optional.of(Member.builder().age(20).build()));

        doThrow(new RuntimeException("smtp down"))
                .when(emailService).notifyMemberRejected(anyString(), any(), any());

        assertDoesNotThrow(() -> service.reject("r14", "bad"));

        verify(changeReqRepo, atLeastOnce()).save(reqCaptor.capture());
        assertEquals(REJECTED, reqCaptor.getValue().getStatus());
    }
}
