package com.quickstarts.kitchensink.controller;

import com.quickstarts.kitchensink.repo.MemberChangeRequestRepository;
import com.quickstarts.kitchensink.service.MemberChangeRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import static com.quickstarts.kitchensink.dto.enums.Status.PENDING;


@Controller
@RequestMapping("/admin/requests")
//@PreAuthorize("hasAuthority('ADMIN')")
@RequiredArgsConstructor
public class AdminRequestsPageController {

    private final MemberChangeRequestRepository reqRepo;
    private final MemberChangeRequestService changeService;

    @GetMapping
    public String page(Model model) {
        model.addAttribute("pending", reqRepo.findByStatusOrderBySubmittedAtAsc(PENDING));
        return "admin-requests";
    }
}

