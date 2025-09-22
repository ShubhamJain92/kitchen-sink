package com.kitchensink.api.controller.login;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PasswordPageController {

    @GetMapping("/reset-password")
    public String resetPasswordPage() {
        return "reset-password"; // resolves templates/reset-password.html
    }
}
