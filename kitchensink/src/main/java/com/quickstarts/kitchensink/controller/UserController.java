package com.quickstarts.kitchensink.controller;

import com.quickstarts.kitchensink.dto.CreateUserRequest;
import com.quickstarts.kitchensink.dto.UserResponse;
import com.quickstarts.kitchensink.service.UserService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@AllArgsConstructor
@RequestMapping("/admin/user")
public class UserController {

    private final UserService service;

    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    //@PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> create(@Valid @RequestBody final CreateUserRequest req) {
        final var response = service.createUser(req);
        return ResponseEntity.ok(response);
    }
}
