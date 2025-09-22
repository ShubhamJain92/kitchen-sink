package com.quickstarts.kitchensink.config;

import com.quickstarts.kitchensink.model.UserInfo;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public class UserInfoUserDetails implements UserDetails {

    private final List<GrantedAuthority> authorities;
    private final UserInfo user;

    public UserInfoUserDetails(UserInfo userInfo) {
        this.user = userInfo;                            // <-- assign it!
        this.authorities = userInfo.getRoles().stream()
                .map(r -> (GrantedAuthority) new SimpleGrantedAuthority(r))
                .toList();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    public boolean mustResetPassword() {
        return user.isMustChangePassword();
    }

    @Override
    public String getPassword() {
        return user.getPassword();
    }

    @Override
    public String getUsername() {
        return user.getUserName();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
