package com.kitchensink.config;

import com.kitchensink.core.user.service.UserInfoUserDetails;
import com.kitchensink.core.user.service.UserInfoUserDetailsService;
import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;

import java.net.URLEncoder;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toSet;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@AllArgsConstructor
public class SecurityConfig {

    @Bean
    public UserDetailsService userDetailsService() {
        return new UserInfoUserDetailsService();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        final var authenticationProvider = new DaoAuthenticationProvider();
        authenticationProvider.setUserDetailsService(userDetailsService());
        authenticationProvider.setPasswordEncoder(passwordEncoder());
        return authenticationProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(final AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    // === Login success handler (must-reset -> redirect param -> role fallback) ===
    @Bean
    AuthenticationSuccessHandler loginSuccessHandler() {
        var saved = new SavedRequestAwareAuthenticationSuccessHandler();
        saved.setTargetUrlParameter("redirect");
        saved.setAlwaysUseDefaultTargetUrl(false);
        var requestCache = new HttpSessionRequestCache();

        return (request, response, authentication) -> {
            // must-change password first
            if (authentication.getPrincipal() instanceof UserInfoUserDetails u && u.mustResetPassword()) {
                response.sendRedirect("/reset-password");
                return;
            }

            var roles = authentication.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(toSet());

            // honor ?redirect=... (admin-only for /admin/**)
            String redirect = request.getParameter("redirect");
            if (redirect != null && redirect.startsWith("/")
                    && (!redirect.startsWith("/admin") || roles.contains("ADMIN"))) {
                saved.onAuthenticationSuccess(request, response, authentication);
                return;
            }

            // saved request (e.g., hit a protected URL first)
            var sr = requestCache.getRequest(request, response);
            if (sr != null) {
                saved.onAuthenticationSuccess(request, response, authentication);
                return;
            }

            if (roles.contains("ADMIN")) {
                response.sendRedirect("/index.html");
            } else if (roles.contains("MEMBER")) {
                response.sendRedirect("/member/me");
            } else {
                response.sendRedirect("/");
            }
        };
    }

    // ===== WEB CHAIN (form login, sessions) =====
    @Bean
    public SecurityFilterChain webChain(final HttpSecurity httpSecurity) throws Exception {
        httpSecurity.csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/reset-password",
                                "/css/**", "/js/**", "/assets/**", "/default-ui.css").permitAll()
                        .requestMatchers("/login").permitAll()
                        .requestMatchers("/auth/admin-gate").permitAll()
                        .requestMatchers("/reset-password", "/reset-password/**").authenticated()
                        .requestMatchers("/index.html").hasAuthority("ADMIN")
                        .requestMatchers("/member/me/**").hasAuthority("MEMBER")
                        .requestMatchers("/members", "/members/**", "/admin/**").hasAuthority("ADMIN")
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login").permitAll()  // use our template
                        .loginProcessingUrl("/login")
                        .successHandler(loginSuccessHandler())
                        .permitAll()
                )
                .logout(l -> l
                        .logoutUrl("/logout")                // endpoint to hit
                        .logoutSuccessUrl("/login?logout")   // redirect here after logout
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                        .clearAuthentication(true)
                ) // Optional: when a logged-in non-admin hits /admin/**, send them to login to re-auth as admin
                .exceptionHandling(e -> e.accessDeniedHandler((req, res, ex) -> {
                    String uri = req.getRequestURI();
                    String qs = req.getQueryString();
                    String full = uri + (qs != null ? "?" + qs : "");
                    String loc = "/login?reauth=1&redirect=" + URLEncoder.encode(full, UTF_8);
                    res.sendRedirect(loc);
                }));
        return httpSecurity.build();
    }
}
