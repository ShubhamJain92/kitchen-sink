package com.kitchensink.api.controller.admin;

import com.kitchensink.api.controller.member.SpringSecConfig;
import com.kitchensink.api.view.controller.admin.AdminGateController;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@WebMvcTest
@ActiveProfiles("test")
@Import(SpringSecConfig.class)
@ExtendWith(MockitoExtension.class)
@ContextConfiguration(classes = AdminGateController.class)
class AdminGateControllerTest {

    @InjectMocks
    private AdminGateController controller;

    @MockitoBean
    private HttpServletResponse response;

    @MockitoBean
    private Authentication authentication;

    @Captor
    private ArgumentCaptor<String> redirectCaptor;

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    @BeforeEach
    void resetMocks() {
        clearInvocations(response, authentication);
    }

    @Test
    void redirectsToLogin_whenNotAuthenticated_andGoIsValidInternalPath() throws IOException {
        // given
        String go = "/admin/requests?page=2";

        // when
        controller.adminGate(go, response, null);

        // then
        verify(response).sendRedirect(redirectCaptor.capture());
        assertThat(redirectCaptor.getValue())
                .isEqualTo("/login?redirect=" + enc(go));
        verifyNoMoreInteractions(response);
    }

    @Test
    void usesDefaultTarget_whenGoIsExternalOrInvalid() throws IOException {
        // given: external / invalid target should be ignored
        String go = "http://evil.example/phish";
        String defaultTarget = "/admin/requests";

        // when
        controller.adminGate(go, response, null);

        // then
        verify(response).sendRedirect(redirectCaptor.capture());
        assertThat(redirectCaptor.getValue())
                .isEqualTo("/login?redirect=" + enc(defaultTarget));
    }

    @Test
    void redirectsToTarget_whenAuthenticatedAdmin() throws IOException {
        // given
        String go = "/secure/admin";
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getAuthorities())
                .thenReturn((java.util.Collection) List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));


        // when
        controller.adminGate(go, response, authentication);

        // then
        verify(response).sendRedirect(redirectCaptor.capture());
        assertThat(redirectCaptor.getValue()).isEqualTo(go);
        verify(authentication, atLeastOnce()).isAuthenticated();
        verify(authentication, atLeastOnce()).getAuthorities();
    }

    @Test
    void redirectsToReauth_whenAuthenticatedButNotAdmin() throws IOException {
        // given
        String go = "/secure/admin";
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getAuthorities())
                .thenReturn((java.util.Collection) List.of(new SimpleGrantedAuthority("ROLE_USER")));


        // when
        controller.adminGate(go, response, authentication);

        // then
        verify(response).sendRedirect(redirectCaptor.capture());
        assertThat(redirectCaptor.getValue())
                .isEqualTo("/login?reauth=1&redirect=" + enc(go));
    }

    @Test
    void treatsPresentButUnauthenticated_likeNotLoggedIn() throws IOException {
        // given
        String go = "/somewhere";
        when(authentication.isAuthenticated()).thenReturn(false);

        // when
        controller.adminGate(go, response, authentication);

        // then
        verify(response).sendRedirect(redirectCaptor.capture());
        assertThat(redirectCaptor.getValue())
                .isEqualTo("/login?redirect=" + enc(go));
    }

    @Test
    void nullGoFallsBackToDefaultTarget() throws IOException {
        // given
        String defaultTarget = "/admin/requests";

        // when
        controller.adminGate(null, response, null);

        // then
        verify(response).sendRedirect(redirectCaptor.capture());
        assertThat(redirectCaptor.getValue())
                .isEqualTo("/login?redirect=" + enc(defaultTarget));
    }
}