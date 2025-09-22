package com.kitchensink.core.member.dto;

import jakarta.validation.constraints.*;

public record UpdateMemberRequest(
        @NotBlank
        @Size(min = 2, max = 25)
        @Pattern(regexp = "^[A-Za-z .'-]{1,25}$",
                message = "Only letters, spaces, . - ' ; max 25 chars")
        String name,

        @NotBlank
        @Email
        @Size(min = 5, max = 254)
        @Pattern(regexp = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$",
                message = "Invalid email format")
        String email,

        // Accept +91, 91 or 10-digit; will normalize to +91XXXXXXXXXX
        @NotBlank
        @Pattern(regexp = "^(?:\\+91|91)?[6-9]\\d{9}$",
                message = "Indian mobile starting 6–9; allow +91/91")
        String phoneNumber,

        @NotNull @Min(1) @Max(120)
        Integer age,

        @NotBlank
        @Size(min = 2, max = 50)
        @Pattern(regexp = "^[A-Za-z .,'-]{1,50}$",
                message = "Only letters, spaces, . , - ' ; max 50 chars")
        String place,
        long version
) {
}
