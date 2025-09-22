package com.quickstarts.kitchensink.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Controller
public class AdminGateController {

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    @GetMapping("/auth/admin-gate")
    public void adminGate(@RequestParam("go") String go,
                          HttpServletResponse res,
                          Authentication auth) throws IOException {

        // only allow app-internal targets
        String target = (go != null && go.startsWith("/")) ? go : "/admin/requests";

        if (auth == null || !auth.isAuthenticated()) {
            // not logged in → go login and come back
            res.sendRedirect("/login?redirect=" + enc(target));
            return;
        }

        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> "ADMIN".equals(a.getAuthority()) || "ROLE_ADMIN".equals(a.getAuthority()));

        if (isAdmin) {
            res.sendRedirect(target);
        } else {
            // logged in but not admin → ask to sign in as admin (no forced logout)
            res.sendRedirect("/login?reauth=1&redirect=" + enc(target));
        }
    }

    /*@GetMapping("/admin/requests")
    public String adminRequests(Authentication auth, HttpServletRequest req) {
        String self = req.getRequestURI() + (req.getQueryString() != null ? "?" + req.getQueryString() : "");
        if (auth == null || !auth.isAuthenticated()) {
            // not logged in → login then come back
            return "redirect:/login?redirect=" + URLEncoder.encode(self, StandardCharsets.UTF_8);
        }
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> "ADMIN".equals(a.getAuthority()) || "ROLE_ADMIN".equals(a.getAuthority()));
        if (!isAdmin) {
            // logged in but not admin → re-auth as admin
            return "redirect:/login?reauth=1&redirect=" + URLEncoder.encode(self, StandardCharsets.UTF_8);
        }
        // logged in as admin → show your admin console (or route to your SPA)
        return "redirect:/index.html"; // or return "admin-requests" if you add a Thymeleaf page
    }*/
}
