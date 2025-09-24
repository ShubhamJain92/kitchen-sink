package com.kitchensink.api.view.controller.admin;

import com.kitchensink.persistence.member.repo.MemberChangeRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import static com.kitchensink.persistence.common.dto.enums.Status.PENDING;


@Controller
@RequestMapping("/admin/requests")
@RequiredArgsConstructor
public class AdminRequestsPageController {

    private final MemberChangeRequestRepository reqRepo;

    @GetMapping
    public String page(final Model model) {
        model.addAttribute("pending", reqRepo.findByStatusOrderBySubmittedAtAsc(PENDING));
        return "admin-requests";
    }
}

