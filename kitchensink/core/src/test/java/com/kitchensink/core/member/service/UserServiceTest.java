package com.kitchensink.core.member.service;

import com.kitchensink.core.user.dto.CreateUserRequest;
import com.kitchensink.core.user.dto.UserResponse;
import com.kitchensink.core.user.service.UserService;
import com.kitchensink.persistence.user.model.UserInfo;
import com.kitchensink.persistence.user.repo.UserInfoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.Set;

import static com.kitchensink.persistence.common.dto.enums.Role.ADMIN;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserInfoRepository userInfoRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private CreateUserRequest createUserRequest;
    private UserInfo userInfo;

    @BeforeEach
    void setUp() {

        createUserRequest = new CreateUserRequest("test@example.com", "Test User", "password123", Set.of(ADMIN));
        userInfo = UserInfo.builder()
                .userName("test@example.com")
                .password("encodedPassword")
                .roles(Set.of(ADMIN.name()))
                .build();
    }

    @Test
    void createUser_ValidRequest_SavesUserAndReturnsSuccessResponse() {
        // Arrange
        when(passwordEncoder.encode("password123")).thenReturn("encodedPassword");
        when(userInfoRepository.save(any(UserInfo.class))).thenReturn(userInfo);

        // Act
        UserResponse response = userService.createUser(createUserRequest);

        // Assert
        assertNotNull(response);
        assertEquals("user created successfully !!", response.message());
        verify(passwordEncoder).encode("password123");
        verify(userInfoRepository).save(any(UserInfo.class));
    }

    @Test
    void createUser_RepositoryThrowsException_ThrowsRuntimeException() {
        // Arrange
        when(passwordEncoder.encode("password123")).thenReturn("encodedPassword");
        when(userInfoRepository.save(any(UserInfo.class))).thenThrow(new RuntimeException("Database error"));

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> userService.createUser(createUserRequest));
        assertEquals("Failed to create user due to unknown error", exception.getLocalizedMessage());
        verify(passwordEncoder).encode("password123");
        verify(userInfoRepository).save(any(UserInfo.class));
    }

    @Test
    void resetPassword_ValidUsername_UpdatesPasswordAndSavesUser() {
        // Arrange
        String username = "test@example.com";
        String newPassword = "newPassword123";
        when(userInfoRepository.findByUserName(username)).thenReturn(Optional.of(userInfo));
        when(passwordEncoder.encode(newPassword)).thenReturn("encodedNewPassword");
        when(userInfoRepository.save(any(UserInfo.class))).thenReturn(userInfo);

        // Act
        userService.resetPassword(username, newPassword);

        // Assert
        assertEquals("encodedNewPassword", userInfo.getPassword());
        assertFalse(userInfo.isMustChangePassword());
        verify(userInfoRepository).findByUserName(username);
        verify(passwordEncoder).encode(newPassword);
        verify(userInfoRepository).save(userInfo);
    }

    @Test
    void resetPassword_UserNotFound_ThrowsException() {
        // Arrange
        String username = "nonexistent@example.com";
        when(userInfoRepository.findByUserName(username)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(RuntimeException.class,
                () -> userService.resetPassword(username, "newPassword123"));
        verify(userInfoRepository).findByUserName(username);
        verify(passwordEncoder, never()).encode(anyString());
        verify(userInfoRepository, never()).save(any(UserInfo.class));
    }
}
