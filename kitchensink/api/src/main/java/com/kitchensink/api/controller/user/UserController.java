package com.kitchensink.api.controller.user;

import com.kitchensink.core.user.dto.CreateUserRequest;
import com.kitchensink.core.user.dto.UserResponse;
import com.kitchensink.core.user.service.UserService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@AllArgsConstructor
@RequestMapping("/admin/user")
public class UserController {

    private final UserService service;

    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<UserResponse> create(@Valid @RequestBody final CreateUserRequest req) {
        final var response = service.createUser(req);
        return ResponseEntity.ok(response);
    }
}
