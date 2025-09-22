package com.quickstarts.kitchensink.service;

import com.quickstarts.kitchensink.dto.MemberSnapshot;
import com.quickstarts.kitchensink.dto.MemberUpdateDTO;
import com.quickstarts.kitchensink.model.Member;
import com.quickstarts.kitchensink.model.MemberChangeRequest;
import com.quickstarts.kitchensink.repo.MemberChangeRequestRepository;
import com.quickstarts.kitchensink.repo.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static com.quickstarts.kitchensink.dto.enums.ChangeType.DELETE;
import static com.quickstarts.kitchensink.dto.enums.ChangeType.UPDATE;
import static com.quickstarts.kitchensink.dto.enums.Status.PENDING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MemberChangeRequestServiceTest {

    @Mock
    private MemberRepository memberRepo;
    @Mock
    private MemberChangeRequestRepository changeRequestRepository;
    @Mock
    private EmailService emailService;

    @InjectMocks
    private MemberChangeRequestService service;

    private Member existing;

    private static MemberUpdateDTO updateDto(
            String name, String email, String phone, int age, String place) {
        // Adjust constructor to match your record/class signature
        return new MemberUpdateDTO(name, email, phone, age, place);
    }

    @BeforeEach
    void setUp() {
        existing = new Member();
        existing.setId("m-123");
        existing.setName("Alice");
        existing.setEmail("alice@example.com");
        existing.setPhoneNumber("9990001111");
        existing.setAge(28);
        existing.setPlace("Pune");
    }

    @Nested
    class SubmitProfileUpdate {

        @Test
        @DisplayName("saves change request, builds 'before' snapshot, calls email")
        void happyPath() {
            // Arrange
            when(memberRepo.findByEmail("alice@example.com"))
                    .thenReturn(Optional.of(existing));
            when(changeRequestRepository.save(any(MemberChangeRequest.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            var dto = updateDto("Alice B", "a.b@example.com", "8887776666", 29, "Mumbai");

            // Act
            service.submitProfileUpdate("alice@example.com", dto);

            // Assert repository interactions
            ArgumentCaptor<MemberChangeRequest> cap = ArgumentCaptor.forClass(MemberChangeRequest.class);
            verify(changeRequestRepository).save(cap.capture());
            var saved = cap.getValue();

            assertThat(saved.getMemberId()).isEqualTo("m-123");
            assertThat(saved.getMemberEmail()).isEqualTo("alice@example.com");
            assertThat(saved.getType()).isEqualTo(UPDATE);
            assertThat(saved.getSubmittedBy()).isEqualTo("alice@example.com");
            assertThat(saved.getSubmittedAt()).isNotNull();
            assertThat(saved.getRequested()).isEqualTo(dto);

            // 'before' snapshot should mirror existing member state
            MemberSnapshot before = saved.getBefore();
            assertThat(before).isNotNull();
            assertThat(before.name()).isEqualTo("Alice");
            assertThat(before.email()).isEqualTo("alice@example.com");
            assertThat(before.phoneNumber()).isEqualTo("9990001111");
            assertThat(before.age()).isEqualTo(28);
            assertThat(before.place()).isEqualTo("Pune");

            // Email notification
            verify(emailService, times(1)).notifyAdminUpdate(existing, dto);
        }

        @Test
        @DisplayName("throws BAD_REQUEST when no changes detected")
        void noChangesThrowsBadRequest() {
            // Arrange: DTO identical to existing member (case-insensitive email equals)
            when(memberRepo.findByEmail("alice@example.com"))
                    .thenReturn(Optional.of(existing));
            var dto = updateDto("Alice", "ALICE@EXAMPLE.COM", "9990001111", 28, "Pune");

            // Act + Assert
            var ex = assertThrows(ResponseStatusException.class,
                    () -> service.submitProfileUpdate("alice@example.com", dto));
            assertEquals(400, ex.getStatusCode().value());
            assertTrue(ex.getReason().contains("No changes"));

            verify(changeRequestRepository, never()).save(any());
            verify(emailService, never()).notifyAdminUpdate(any(), any());
        }

        @Test
        @DisplayName("throws NOT_FOUND if member email not found")
        void memberNotFound() {
            when(memberRepo.findByEmail("missing@example.com")).thenReturn(Optional.empty());
            var dto = updateDto("X", "x@y", "1", 20, "Z");

            var ex = assertThrows(ResponseStatusException.class,
                    () -> service.submitProfileUpdate("missing@example.com", dto));
            assertEquals(404, ex.getStatusCode().value());
            assertTrue(ex.getReason().contains("Member not found"));

            verify(changeRequestRepository, never()).save(any());
            verify(emailService, never()).notifyAdminUpdate(any(), any());
        }

        @Test
        @DisplayName("rethrows RuntimeException if emailService.notifyAdminUpdate fails (after save)")
        void emailFailureRethrown() {
            // Arrange
            when(memberRepo.findByEmail("alice@example.com"))
                    .thenReturn(Optional.of(existing));
            when(changeRequestRepository.save(any(MemberChangeRequest.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            doThrow(new RuntimeException("smtp down"))
                    .when(emailService).notifyAdminUpdate(eq(existing), any(MemberUpdateDTO.class));

            var dto = updateDto("Alice B", null, null, 0, null); // at least one change (name)

            // Act + Assert
            var ex = assertThrows(RuntimeException.class,
                    () -> service.submitProfileUpdate("alice@example.com", dto));
            assertTrue(ex.getMessage().contains("smtp down"));

            // Save was attempted before email send
            verify(changeRequestRepository, times(1)).save(any(MemberChangeRequest.class));
        }
    }

    @Nested
    class SubmitDeleteRequest {

        @Test
        @DisplayName("throws CONFLICT if a pending request already exists")
        void conflictWhenPendingExists() {
            when(memberRepo.findByEmail("alice@example.com")).thenReturn(Optional.of(existing));
            when(changeRequestRepository.existsByMemberIdAndStatus("m-123", PENDING))
                    .thenReturn(true);

            var ex = assertThrows(ResponseStatusException.class,
                    () -> service.submitDeleteRequest("alice@example.com"));
            assertEquals(409, ex.getStatusCode().value());
            assertTrue(ex.getReason().contains("pending request"));

            verify(changeRequestRepository, never()).save(any());
            verify(emailService, never()).notifyAdminDelete(any());
        }

        @Test
        @DisplayName("saves delete request with 'before' snapshot and notifies admin")
        void happyPath() {
            when(memberRepo.findByEmail("alice@example.com")).thenReturn(Optional.of(existing));
            when(changeRequestRepository.existsByMemberIdAndStatus("m-123", PENDING))
                    .thenReturn(false);
            when(changeRequestRepository.save(any(MemberChangeRequest.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            service.submitDeleteRequest("alice@example.com");

            ArgumentCaptor<MemberChangeRequest> cap = ArgumentCaptor.forClass(MemberChangeRequest.class);
            verify(changeRequestRepository).save(cap.capture());
            var saved = cap.getValue();

            assertThat(saved.getMemberId()).isEqualTo("m-123");
            assertThat(saved.getMemberEmail()).isEqualTo("alice@example.com");
            assertThat(saved.getType()).isEqualTo(DELETE);
            assertThat(saved.getSubmittedBy()).isEqualTo("alice@example.com");
            assertThat(saved.getSubmittedAt()).isNotNull();

            // Before snapshot must be populated from existing member
            var before = saved.getBefore();
            assertThat(before).isNotNull();
            assertThat(before.name()).isEqualTo("Alice");
            assertThat(before.email()).isEqualTo("alice@example.com");
            assertThat(before.phoneNumber()).isEqualTo("9990001111");
            assertThat(before.age()).isEqualTo(28);
            assertThat(before.place()).isEqualTo("Pune");

            verify(emailService, times(1)).notifyAdminDelete(existing);
        }

        @Test
        @DisplayName("throws NOT_FOUND if member email not found on delete")
        void memberNotFoundOnDelete() {
            when(memberRepo.findByEmail("missing@example.com")).thenReturn(Optional.empty());

            var ex = assertThrows(ResponseStatusException.class,
                    () -> service.submitDeleteRequest("missing@example.com"));
            assertEquals(404, ex.getStatusCode().value());
            assertTrue(ex.getReason().contains("Member not found"));

            verify(changeRequestRepository, never()).save(any());
            verify(emailService, never()).notifyAdminDelete(any());
        }
    }
}
