package com.quickstarts.kitchensink.controller;

import com.quickstarts.kitchensink.config.UserInfoUserDetails;
import com.quickstarts.kitchensink.dto.MemberUpdateDTO;
import com.quickstarts.kitchensink.model.Member;
import com.quickstarts.kitchensink.repo.MemberRepository;
import com.quickstarts.kitchensink.service.MemberChangeRequestService;
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

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MemberSelfControllerTest {

    @Mock
    private MemberRepository memberRepo;

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

    private Member member;
    private MemberUpdateDTO memberUpdateDTO;
    private static final String USERNAME = "test@example.com";

    @BeforeEach
    void setUp() {
        member = new Member();
        member.setEmail(USERNAME);
        member.setName("Test User");
        member.setPhoneNumber("1234567890");

        memberUpdateDTO = new MemberUpdateDTO("Updated Name", "email", "56498412", 55, "delhi");
    }

    @Test
    void viewMe_MemberFound_ReturnsMemberSelfView() {
        // Arrange
        when(userDetails.getUsername()).thenReturn(USERNAME);
        when(memberRepo.findByEmail(USERNAME)).thenReturn(Optional.of(member));

        // Act
        String viewName = controller.viewMe(userDetails, model);

        // Assert
        assertEquals("member-self", viewName);
        verify(model).addAttribute("member", member);
        verify(memberRepo).findByEmail(USERNAME);
    }

    @Test
    void viewMe_MemberNotFound_ThrowsNotFoundException() {
        // Arrange
        when(userDetails.getUsername()).thenReturn(USERNAME);
        when(memberRepo.findByEmail(USERNAME)).thenReturn(Optional.empty());

        // Act & Assert
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> controller.viewMe(userDetails, model));
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        assertEquals("Member not found", exception.getReason());
        verify(memberRepo).findByEmail(USERNAME);
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