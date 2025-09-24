package com.kitchensink.api.controller.user;

import com.kitchensink.core.user.dto.CreateUserRequest;
import com.kitchensink.core.user.dto.UserResponse;
import com.kitchensink.core.user.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import static org.springframework.http.HttpStatus.OK;

@Tag(name = "User Management", description = "create user")
@RestController
@AllArgsConstructor
@RequestMapping("/admin/user")
public class UserController {

    private final UserService service;

    @PostMapping
    @ResponseStatus(OK)
    public ResponseEntity<UserResponse> create(@Valid @RequestBody final CreateUserRequest req) {
        final var response = service.createUser(req);
        return ResponseEntity.ok(response);
    }
}
