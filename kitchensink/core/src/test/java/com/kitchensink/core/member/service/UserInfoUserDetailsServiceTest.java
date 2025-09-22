package com.kitchensink.core.member.service;

import com.kitchensink.core.user.service.UserInfoUserDetails;
import com.kitchensink.core.user.service.UserInfoUserDetailsService;
import com.kitchensink.persistence.user.model.UserInfo;
import com.kitchensink.persistence.user.repo.UserInfoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;
import java.util.Set;

import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserInfoUserDetailsServiceTest {

    @Mock
    private UserInfoRepository repository;

    @InjectMocks
    private UserInfoUserDetailsService service;

    @Test
    @DisplayName("loadUserByUsername: returns UserDetails mapped from repository")
    void loadUserByUsername_success() {
        // Arrange
        var user = UserInfo.builder()
                .id("u-1")
                .userName("alice@example.com")
                .password("ENC-PASS")
                .roles(Set.of("ADMIN"))
                .mustChangePassword(false)
                .build();
        when(repository.findByUserName("alice@example.com")).thenReturn(Optional.of(user));

        // Act
        var details = service.loadUserByUsername("alice@example.com");

        // Assert
        assertThat(details).isInstanceOf(UserInfoUserDetails.class);
        assertThat(details.getUsername()).isEqualTo("alice@example.com");
        assertThat(details.getPassword()).isEqualTo("ENC-PASS");

        // Authorities check (allow either "ADMIN" or "ROLE_ADMIN")
        Set<String> auths = details.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(toSet());
        assertThat(auths).anyMatch(a -> a.equals("ADMIN") || a.equals("ROLE_ADMIN"));

        verify(repository, times(1)).findByUserName(eq("alice@example.com"));
    }

    @Test
    @DisplayName("loadUserByUsername: throws UsernameNotFoundException when user missing")
    void loadUserByUsername_notFound() {
        // Arrange
        when(repository.findByUserName("missing@example.com")).thenReturn(Optional.empty());

        // Act + Assert
        var ex = assertThrows(
                UsernameNotFoundException.class,
                () -> service.loadUserByUsername("missing@example.com")
        );
        assertThat(ex.getMessage()).contains("missing@example.com");

        verify(repository, times(1)).findByUserName(eq("missing@example.com"));
    }
}
