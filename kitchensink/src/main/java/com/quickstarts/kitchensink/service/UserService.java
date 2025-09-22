package com.quickstarts.kitchensink.service;

import com.quickstarts.kitchensink.dto.CreateUserRequest;
import com.quickstarts.kitchensink.dto.enums.Role;
import com.quickstarts.kitchensink.dto.UserResponse;
import com.quickstarts.kitchensink.exception.UserCreationException;
import com.quickstarts.kitchensink.model.UserInfo;
import com.quickstarts.kitchensink.repo.UserInfoRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
@AllArgsConstructor
@Slf4j
public class UserService {

    private final UserInfoRepository userInfoRepository;
    private final PasswordEncoder encoder;

    public UserResponse createUser(final CreateUserRequest createUserRequest) throws UserCreationException {

        final var userInfo = UserInfo.builder()
                .userName(createUserRequest.email())
                .password(encoder.encode(createUserRequest.password()))
                .roles(Set.of(Role.ADMIN.name()))
                .build();
        try {
            UserInfo userInfo1 = userInfoRepository.save(userInfo);
            log.info("user created: {}", userInfo1);
        } catch (DataAccessException e) {
            log.error("Database error while creating user: {}", e.getMessage(), e);
            throw new UserCreationException("Failed to create user due to database error", e);
        } catch (Exception e1) {
            log.error("error occurred while creating user:{}", e1.getMessage(), e1);
            throw new UserCreationException("Failed to create user due to unknown error", e1);
        }

        return UserResponse.builder()
                .message("user created successfully !!")
                .build();
    }

    public void resetPassword(final String username, final String newPassword) {
        var user = userInfoRepository.findByUserName(username).orElseThrow();
        user.setPassword(encoder.encode(newPassword));
        user.setMustChangePassword(false);
        // user.setPasswordUpdatedAt(Instant.now());
        userInfoRepository.save(user);
    }
}
