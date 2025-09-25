package com.kitchensink.api.view.controller.admin;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.net.URLEncoder;

import static java.nio.charset.StandardCharsets.UTF_8;

@Controller
public class AdminGateController {

    private static String enc(String s) {
        return URLEncoder.encode(s, UTF_8);
    }

    @GetMapping("/auth/admin-gate")
    public void adminGate(@RequestParam("go") String go,
                          HttpServletResponse servletResponse,
                          Authentication auth) throws IOException {

        String target = (go != null && go.startsWith("/")) ? go : "/admin/requests";

        if (auth == null || !auth.isAuthenticated()) {
            // not logged in → go login and come back
            servletResponse.sendRedirect("/login?redirect=" + enc(target));
            return;
        }

        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> "ADMIN".equals(a.getAuthority()) || "ROLE_ADMIN".equals(a.getAuthority()));

        if (isAdmin) {
            servletResponse.sendRedirect(target);
        } else {
            // logged in but not admin → ask to sign in as admin (no forced logout)
            servletResponse.sendRedirect("/login?reauth=1&redirect=" + enc(target));
        }
    }
}
