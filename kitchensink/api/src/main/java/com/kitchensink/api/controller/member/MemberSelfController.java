package com.kitchensink.api.controller.member;

import com.kitchensink.core.member.service.MemberChangeRequestService;
import com.kitchensink.core.user.service.UserInfoUserDetails;
import com.kitchensink.persistence.member.dto.MemberUpdateDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

// Self-service endpoints for the logged-in member
@Controller
@RequiredArgsConstructor
@RequestMapping("/member/me")
public class MemberSelfController {

    private final MemberChangeRequestService changeService;

    // 1) View my details (server-rendered page)
    @GetMapping
    public String viewMe(@AuthenticationPrincipal UserInfoUserDetails me, Model model) {
        final var member = changeService.findByEmail(me.getUsername());
        model.addAttribute("member", member);
        return "member-self"; // templates/member-self.html
    }

    // 2) Update my details -> creates a PENDING change request and emails admin
    @PostMapping
    public String updateMe(@AuthenticationPrincipal UserInfoUserDetails me,
                           @Valid @ModelAttribute MemberUpdateDTO dto,
                           RedirectAttributes ra) {
        changeService.submitProfileUpdate(me.getUsername(), dto);
        ra.addFlashAttribute("msg", "Your changes were submitted and are pending admin approval.");
        return "redirect:/member/me";
    }

    // 3) Delete myself -> creates a PENDING delete request and emails admin
    @PostMapping("/delete")
    public String deleteMe(@AuthenticationPrincipal UserInfoUserDetails me, RedirectAttributes ra) {
        changeService.submitDeleteRequest(me.getUsername());
        ra.addFlashAttribute("msg", "Your delete request was sent to the admin for approval.");
        return "redirect:/member/me";
    }
}
