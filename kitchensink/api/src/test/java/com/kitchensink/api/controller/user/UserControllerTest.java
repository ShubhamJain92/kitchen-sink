package com.kitchensink.api.controller.user;


import com.kitchensink.core.user.dto.CreateUserRequest;
import com.kitchensink.core.user.dto.UserResponse;
import com.kitchensink.core.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserService userService;

    @InjectMocks
    private UserController controller;

    @Test
    void create_delegatesToService_andReturnsOkWithBody() {
        // Arrange: we can mock the request to avoid coupling to fields
        CreateUserRequest req = mock(CreateUserRequest.class);

        UserResponse expected = mock(UserResponse.class);
        when(userService.createUser(req)).thenReturn(expected);

        // Act
        ResponseEntity<UserResponse> resp = controller.create(req);

        // Assert
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(resp.getBody()).isSameAs(expected);
        verify(userService).createUser(req);
    }
}
