package com.kitchensink.api.controller.member;

import com.kitchensink.core.member.service.MemberChangeRequestService;
import com.kitchensink.core.user.service.UserInfoUserDetails;
import com.kitchensink.persistence.member.dto.MemberUpdateDTO;
import com.kitchensink.persistence.member.repo.MemberRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

// Self-service endpoints for the logged-in member
@Controller
@RequiredArgsConstructor
@RequestMapping("/member/me")
public class MemberSelfController {
    private final MemberRepository memberRepo;
    private final MemberChangeRequestService changeService;

    // 1) View my details (server-rendered page)
    @GetMapping
    public String viewMe(@AuthenticationPrincipal UserInfoUserDetails me, Model model) {
        var member = memberRepo.findByEmail(me.getUsername())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found"));
        model.addAttribute("member", member);
        return "member-self"; // templates/member-self.html
    }

    // 2) Update my details -> creates a PENDING change request and emails admin
    @PostMapping // (or PUT; using POST for form submits)
    public String updateMe(@AuthenticationPrincipal final UserInfoUserDetails me,
                           @Valid @ModelAttribute final MemberUpdateDTO dto,
                           final RedirectAttributes ra) {
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
