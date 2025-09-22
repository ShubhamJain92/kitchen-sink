package com.quickstarts.kitchensink.dto;

import com.quickstarts.kitchensink.dto.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record CreateUserRequest(
        @NotBlank @Size(min = 1, max = 50)
        String name,
        @Email
        String email,
        @NotBlank
        @Size(min = 8, max = 128)
        String password,
        Set<Role> roles
) {
}
