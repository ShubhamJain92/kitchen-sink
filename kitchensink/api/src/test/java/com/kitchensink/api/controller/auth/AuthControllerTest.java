package com.kitchensink.api.controller.auth;

import com.kitchensink.api.controller.member.SpringSecConfig;
import com.kitchensink.core.auth.service.JwtService;
import com.kitchensink.core.user.service.UserInfoUserDetails;
import com.kitchensink.core.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest
@ActiveProfiles("test")
@Import(SpringSecConfig.class)
@ExtendWith(MockitoExtension.class)
@ContextConfiguration(classes = AuthController.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private AuthenticationManager authenticationManager;

    @MockitoBean
    private UserService userService;

    // ---------- /authenticate ----------

    @Test
    void authenticate_returnsJwt_whenAuthenticationSucceeds() throws Exception {
        // Arrange
        var authReqJson = """
                { "username": "alice@example.com", "password": "s3cr3t" }
                """;

        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(authenticationManager.authenticate(any(Authentication.class))).thenReturn(auth);
        when(jwtService.generateToken("alice@example.com")).thenReturn("jwt-123");

        // Act & Assert
        mockMvc.perform(post("/authenticate")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(authReqJson))
                .andExpect(status().isOk())
                .andExpect(content().string("jwt-123"));

        // Verify the username used to generate token
        verify(jwtService).generateToken("alice@example.com");

        // Optionally capture the Authentication token passed to auth manager
        var captor = ArgumentCaptor.forClass(Authentication.class);
        verify(authenticationManager).authenticate(captor.capture());
        var token = (UsernamePasswordAuthenticationToken) captor.getValue();
        assertThat(token.getPrincipal()).isEqualTo("alice@example.com");
        assertThat(token.getCredentials()).isEqualTo("s3cr3t");
    }

    //@Test
    void authenticate_throws_whenNotAuthenticated() throws Exception {
        var authReqJson = """
                { "username": "bob@example.com", "password": "bad" }
                """;

        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(false);
        when(authenticationManager.authenticate(any(Authentication.class))).thenReturn(auth);

        mockMvc.perform(post("/authenticate")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(authReqJson))
                .andExpect(status().isInternalServerError()) // UsernameNotFoundException -> 500 by default here
                .andExpect(result -> assertThat(result.getResolvedException())
                        .isInstanceOf(org.springframework.security.core.userdetails.UsernameNotFoundException.class))
                .andExpect(status().reason(containsString("")));
    }

    // ---------- /reset-password ----------

    @Test
    void resetPassword_redirectsAndUpdates_whenPasswordsMatch() throws Exception {
        // Arrange: build an Authentication whose principal is UserInfoUserDetails
        UserInfoUserDetails me = mock(UserInfoUserDetails.class);
        when(me.getUsername()).thenReturn("alice@example.com");
        var auth = new UsernamePasswordAuthenticationToken(me, null, Collections.emptyList());

        // Act & Assert
        mockMvc.perform(post("/reset-password")
                        .with(csrf())
                        .with(authentication(auth))
                        .param("newPassword", "NewPass#1")
                        .param("confirmPassword", "NewPass#1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?pwdReset=1"));

        verify(userService).resetPassword("alice@example.com", "NewPass#1");
    }

    @Test
    void resetPassword_returns400_whenPasswordsDoNotMatch() throws Exception {
        UserInfoUserDetails me = mock(UserInfoUserDetails.class);
        when(me.getUsername()).thenReturn("alice@example.com");
        var auth = new UsernamePasswordAuthenticationToken(me, null, Collections.emptyList());

        mockMvc.perform(post("/reset-password")
                        .with(csrf())
                        .with(authentication(auth))
                        .param("newPassword", "NewPass#1")
                        .param("confirmPassword", "Mismatch#2"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userService);
    }
}
