package com.kitchensink.api.controller.member;


import com.kitchensink.api.controller.user.UserController;
import com.kitchensink.core.user.dto.CreateUserRequest;
import com.kitchensink.core.user.dto.UserResponse;
import com.kitchensink.core.user.service.UserService;
import com.kitchensink.persistence.common.dto.enums.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserService userService;

    @InjectMocks
    private UserController controller;

    private CreateUserRequest createUserRequest;
    private UserResponse userResponse;

    @BeforeEach
    void setUp() {
        createUserRequest = new CreateUserRequest("Test User", "test@example.com", "password123",
                Set.of(Role.ADMIN));

        userResponse = new UserResponse("user created successfully !!");
    }

    @Test
    void create_ValidRequest_ReturnsUserResponse() {
        // Arrange
        when(userService.createUser(createUserRequest)).thenReturn(userResponse);

        // Act
        ResponseEntity<UserResponse> response = controller.create(createUserRequest);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(userResponse, response.getBody());
        verify(userService).createUser(createUserRequest);
    }

    @Test
    void create_ServiceThrowsException_PropagatesException() {
        // Arrange
        when(userService.createUser(createUserRequest))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid request"));

        // Act & Assert
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> controller.create(createUserRequest));
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals("Invalid request", exception.getReason());
        verify(userService).createUser(createUserRequest);
    }
}
