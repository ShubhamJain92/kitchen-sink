package com.quickstarts.kitchensink.config;

import com.quickstarts.kitchensink.filter.JwtAuthFilter;
import com.quickstarts.kitchensink.service.UserInfoUserDetailsService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.net.URLEncoder;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.springframework.security.config.http.SessionCreationPolicy.STATELESS;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@AllArgsConstructor
public class SecurityConfig {

    @Autowired
    @Qualifier("handlerExceptionResolver")
    private HandlerExceptionResolver handlerExceptionResolver;

    @Bean
    public JwtAuthFilter jwtAuthFilter() {
        return new JwtAuthFilter(handlerExceptionResolver);
    }

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
        var saved = new org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler();
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
                    .map(org.springframework.security.core.GrantedAuthority::getAuthority)
                    .collect(java.util.stream.Collectors.toSet());

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

    // ===== API CHAIN (JWT, stateless) =====
    @Bean
    @Order(1)
    public SecurityFilterChain apiChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/api/**", "/auth/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/**").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthFilter(), UsernamePasswordAuthenticationFilter.class)
        ;
        return http.build();
    }

    // ===== WEB CHAIN (form login, sessions) =====
    @Bean
    @Order(2)
    public SecurityFilterChain webChain(HttpSecurity http) throws Exception {
        http // keep CSRF ON, but ignore it for exactly this bootstrap endpoint
                .csrf(AbstractHttpConfigurer::disable)
                // CSRF ON by default -> good for form posts
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/reset-password",
                                "/css/**", "/js/**", "/assets/**", "/default-ui.css").permitAll()
                        .requestMatchers("/login").permitAll()
                        .requestMatchers("/auth/admin-gate").permitAll()
                        //.requestMatchers("/member/register").authenticated()
                        .requestMatchers("/reset-password", "/reset-password/**").authenticated()
                        .requestMatchers("/index.html").authenticated()
                        .requestMatchers(HttpMethod.POST, "/member/register").permitAll() // TEMP: for bootstrap only
                        .requestMatchers("/member/me/**").hasAuthority("MEMBER")
                        .requestMatchers("/member", "/member/**", "/admin/**").hasAuthority("ADMIN")
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login").permitAll()  // use our template
                        .loginProcessingUrl("/login")
                        .successHandler(loginSuccessHandler())
                        //.defaultSuccessUrl("/index.html", true)
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
        //.sessionManagement(sm -> sm.sessionCreationPolicy(ALWAYS))
        return http.build();
    }
}
