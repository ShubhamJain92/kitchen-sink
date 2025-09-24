package com.kitchensink.api.view.controller.login;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LoginPageController {
    @GetMapping("/login")
    public String login() {
        return "login";
    } // resolves templates/login.html
}
