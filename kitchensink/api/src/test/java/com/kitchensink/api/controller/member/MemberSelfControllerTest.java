package com.kitchensink.api.controller.member;

import com.kitchensink.core.member.dto.MemberResponseDTO;
import com.kitchensink.core.member.service.MemberChangeRequestService;
import com.kitchensink.core.user.service.UserInfoUserDetails;
import com.kitchensink.persistence.member.dto.MemberUpdateDTO;
import com.kitchensink.persistence.member.model.Member;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MemberSelfControllerTest {

    @Mock
    private MemberChangeRequestService changeService;

    @Mock
    private UserInfoUserDetails userDetails;

    @Mock
    private Model model;

    @Mock
    private RedirectAttributes redirectAttributes;

    @InjectMocks
    private MemberSelfController controller;

    private MemberUpdateDTO memberUpdateDTO;
    private static final String USERNAME = "test@example.com";

    @BeforeEach
    void setUp() {
        Member member = new Member();
        member.setEmail(USERNAME);
        member.setName("Test User");
        member.setPhoneNumber("1234567890");
        memberUpdateDTO = new MemberUpdateDTO("Updated Name", "email", "56498412", 55, "delhi");
    }

    @Test
    void viewMe_MemberFound_ReturnsMemberSelfView() {
        // Arrange
        when(userDetails.getUsername()).thenReturn(USERNAME);
        final var memberResponseDTO = getMemberResponseDTO();
        when(changeService.findByEmail(USERNAME)).thenReturn(memberResponseDTO);

        // Act
        String viewName = controller.viewMe(userDetails, model);

        // Assert
        assertEquals("member-self", viewName);
        verify(model).addAttribute("member", memberResponseDTO);
        verify(changeService).findByEmail(USERNAME);
    }

    private static MemberResponseDTO getMemberResponseDTO() {
        return MemberResponseDTO.builder()
                .name("Test User")
                .email(USERNAME)
                .phoneNumber("1234567890")
                .age(55)
                .build();
    }

    @Test
    void viewMe_MemberNotFound_ThrowsNotFoundException() {
        // Arrange
        when(userDetails.getUsername()).thenReturn(USERNAME);
        when(changeService.findByEmail(USERNAME)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found"));

        // Act & Assert
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> controller.viewMe(userDetails, model));
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        assertEquals("Member not found", exception.getReason());
        verify(changeService).findByEmail(USERNAME);
        verify(model, never()).addAttribute(any(), any());
    }

    @Test
    void updateMe_ValidDTO_SubmitsUpdateAndRedirects() {
        // Arrange
        when(userDetails.getUsername()).thenReturn(USERNAME);

        // Act
        String viewName = controller.updateMe(userDetails, memberUpdateDTO, redirectAttributes);

        // Assert
        assertEquals("redirect:/member/me", viewName);
        verify(changeService).submitProfileUpdate(USERNAME, memberUpdateDTO);
        verify(redirectAttributes).addFlashAttribute("msg", "Your changes were submitted and are pending admin approval.");
    }

    @Test
    void deleteMe_SubmitsDeleteRequestAndRedirects() {
        // Arrange
        when(userDetails.getUsername()).thenReturn(USERNAME);

        // Act
        String viewName = controller.deleteMe(userDetails, redirectAttributes);

        // Assert
        assertEquals("redirect:/member/me", viewName);
        verify(changeService).submitDeleteRequest(USERNAME);
        verify(redirectAttributes).addFlashAttribute("msg", "Your delete request was sent to the admin for approval.");
    }
}