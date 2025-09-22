package com.kitchensink.api.controller.auth;

import com.kitchensink.core.auth.dto.AuthRequest;
import com.kitchensink.core.auth.service.JwtService;
import com.kitchensink.core.user.service.UserInfoUserDetails;
import com.kitchensink.core.user.service.UserService;
import lombok.AllArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.view.RedirectView;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@RestController
@AllArgsConstructor
public class AuthController {

    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final UserService userService;

    @PostMapping("/authenticate")
    public String authenticateAndGetToken(@RequestBody final AuthRequest authRequest) {
        final var authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(authRequest.username(), authRequest.password()));
        if (authentication.isAuthenticated()) {
            return jwtService.generateToken(authRequest.username());
        } else {
            throw new UsernameNotFoundException("invalid user request !");
        }
    }

    @PostMapping("/reset-password")
    public RedirectView doReset(@AuthenticationPrincipal final UserInfoUserDetails me,
                                @RequestParam final String newPassword,
                                @RequestParam final String confirmPassword) {
        if (!newPassword.equals(confirmPassword)) {
            throw new ResponseStatusException(BAD_REQUEST, "Passwords do not match");
        }
        userService.resetPassword(me.getUsername(), newPassword);
        return new RedirectView("/login?pwdReset=1");
    }
}
