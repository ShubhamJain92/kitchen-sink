package com.quickstarts.kitchensink.controller;

import com.quickstarts.kitchensink.dto.MemberUpdateDTO;
import com.quickstarts.kitchensink.dto.enums.ChangeType;
import com.quickstarts.kitchensink.dto.enums.Status;
import com.quickstarts.kitchensink.model.Member;
import com.quickstarts.kitchensink.model.MemberChangeRequest;
import com.quickstarts.kitchensink.model.UserInfo;
import com.quickstarts.kitchensink.repo.MemberChangeRequestRepository;
import com.quickstarts.kitchensink.repo.MemberRepository;
import com.quickstarts.kitchensink.repo.UserInfoRepository;
import com.quickstarts.kitchensink.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.SEE_OTHER;

@ExtendWith(MockitoExtension.class)
class MemberChangeAdminControllerTest {

    @Mock
    private MemberChangeRequestRepository memberChangeRequestRepository;

    @Mock
    private MemberRepository memberRepo;

    @Mock
    private UserInfoRepository userRepo;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private MemberChangeAdminController controller;

    private MemberChangeRequest memberChangeRequest;
    private Member member;
    private UserInfo userInfo;
    private MemberUpdateDTO memberUpdateDTO;
    private static final String REQUEST_ID = "123";
    private static final String MEMBER_ID = "456";
    private static final String EMAIL = "test@example.com";
    private static final String NEW_EMAIL = "new@example.com";

    @BeforeEach
    void setUp() {
        memberUpdateDTO = new MemberUpdateDTO("Updated Name", NEW_EMAIL, "1234567890", 30, "New Place");

        member = new Member();
        member.setId(MEMBER_ID);
        member.setEmail(EMAIL);
        member.setName("Original Name");
        member.setPhoneNumber("0987654321");
        member.setAge(25);
        member.setPlace("Original Place");

        userInfo = new UserInfo();
        userInfo.setUserName(EMAIL);

        memberChangeRequest = new MemberChangeRequest();
        memberChangeRequest.setId(REQUEST_ID);
        memberChangeRequest.setMemberId(MEMBER_ID);
        memberChangeRequest.setMemberEmail(EMAIL);
        memberChangeRequest.setType(ChangeType.UPDATE);
        memberChangeRequest.setStatus(Status.PENDING);
        memberChangeRequest.setRequested(memberUpdateDTO);
    }

    @Test
    void approve_UpdateRequest_Pending_UpdatesMemberAndRedirects() {
        // Arrange
        when(memberChangeRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(memberChangeRequest));
        when(memberRepo.findById(MEMBER_ID)).thenReturn(Optional.of(member));
        when(userRepo.findByUserName(EMAIL)).thenReturn(Optional.of(userInfo));

        // Act
        ResponseEntity<Void> response = controller.approve(REQUEST_ID);

        // Assert
        assertEquals(SEE_OTHER, response.getStatusCode());
        assertEquals("/admin/requests", response.getHeaders().getLocation().getPath());
        verify(memberRepo).save(member);
        verify(userRepo).save(userInfo);
        verify(emailService).notifyMemberUpdateApproved(EMAIL, memberChangeRequest);
        verify(memberChangeRequestRepository).save(memberChangeRequest);
        assertEquals(Status.APPROVED, memberChangeRequest.getStatus());
        assertEquals(NEW_EMAIL, member.getEmail());
        assertEquals("Updated Name", member.getName());
        assertEquals("1234567890", member.getPhoneNumber());
        assertEquals(30, member.getAge());
        assertEquals("New Place", member.getPlace());
        assertEquals(NEW_EMAIL, userInfo.getUserName());
    }

    @Test
    void approve_DeleteRequest_Pending_DeletesMemberAndRedirects() {
        // Arrange
        memberChangeRequest.setType(ChangeType.DELETE);
        when(memberChangeRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(memberChangeRequest));
        when(memberRepo.findById(MEMBER_ID)).thenReturn(Optional.of(member));
        when(userRepo.findByUserName(EMAIL)).thenReturn(Optional.of(userInfo));

        // Act
        ResponseEntity<Void> response = controller.approve(REQUEST_ID);

        // Assert
        assertEquals(SEE_OTHER, response.getStatusCode());
        assertEquals("/admin/requests", response.getHeaders().getLocation().getPath());
        verify(emailService).notifyMemberDeleteApproved(EMAIL, member.getName());
        verify(userRepo).delete(userInfo);
        verify(memberRepo).deleteById(MEMBER_ID);
        verify(memberChangeRequestRepository).save(memberChangeRequest);
        assertEquals(Status.APPROVED, memberChangeRequest.getStatus());
    }

    @Test
    void approve_NonPendingRequest_ThrowsConflictException() {
        // Arrange
        memberChangeRequest.setStatus(Status.APPROVED);
        when(memberChangeRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(memberChangeRequest));

        // Act & Assert
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> controller.approve(REQUEST_ID));
        assertEquals(CONFLICT, exception.getStatusCode());
        assertEquals("Not pending", exception.getReason());
        verify(memberRepo, never()).save(any());
        verify(userRepo, never()).save(any());
        verify(emailService, never()).notifyMemberUpdateApproved(any(), any());
        verify(memberChangeRequestRepository, never()).save(any());
    }

    @Test
    void approve_RequestNotFound_ThrowsException() {
        // Arrange
        when(memberChangeRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(NoSuchElementException.class, () -> controller.approve(REQUEST_ID));
        verify(memberRepo, never()).save(any());
        verify(userRepo, never()).save(any());
        verify(emailService, never()).notifyMemberUpdateApproved(any(), any());
        verify(memberChangeRequestRepository, never()).save(any());
    }

    @Test
    void reject_PendingRequest_RejectsAndRedirects() {
        // Arrange
        String reason = "Invalid data";
        when(memberChangeRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(memberChangeRequest));

        // Act
        ResponseEntity<Void> response = controller.reject(REQUEST_ID, reason);

        // Assert
        assertEquals(SEE_OTHER, response.getStatusCode());
        assertEquals("/admin/requests", response.getHeaders().getLocation().getPath());
        verify(emailService).notifyMemberRejected(EMAIL, reason);
        verify(memberChangeRequestRepository).save(memberChangeRequest);
        assertEquals(Status.REJECTED, memberChangeRequest.getStatus());
        assertEquals(reason, memberChangeRequest.getRejectionReason());
    }

    @Test
    void reject_RequestNotFound_ThrowsException() {
        // Arrange
        when(memberChangeRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(NoSuchElementException.class, () -> controller.reject(REQUEST_ID, null));
        verify(emailService, never()).notifyMemberRejected(any(), any());
        verify(memberChangeRequestRepository, never()).save(any());
    }
}