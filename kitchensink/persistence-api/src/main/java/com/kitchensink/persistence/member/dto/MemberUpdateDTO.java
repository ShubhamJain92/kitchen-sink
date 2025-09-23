package com.kitchensink.persistence.member.dto;

import jakarta.validation.constraints.*;

public record MemberUpdateDTO(

        @Size(max = 25, message = "Name cannot be more than 25 characters")
        @Pattern(
                regexp = "^(?=\\S)(?!.*\\s{2,})[\\p{L}][\\p{L} .'-]{0,24}$",
                message = "Use letters, spaces, dot (.), hyphen (-), apostrophe ('); no digits; " +
                        "no leading/trailing/double spaces"
        )
        String name,
        @Email
        @Pattern(regexp = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$",
                message = "Invalid email format")
        String email,
        @Pattern(
                regexp = "^(?:\\+91[- ]?|0)?[6-9]\\d{9}$",
                message = "Enter a valid Indian mobile number (10 digits starting 6–9). Optional +91 or leading 0 allowed."
        )
        String phoneNumber,
        @Min(value = 1, message = "Age must be at least {value}")
        @Max(value = 120, message = "Age must not exceed {value}")
        int age,
        @Size(max = 50, message = "Place must be at most 50 characters")
        @Pattern(
                regexp = "^(?=\\S)(?!.*\\s{2,})[\\p{L}][\\p{L} .,'-]{0,49}$",
                message = "Use letters, spaces, . , - ', no digits or double spaces"
        )
        String place
) {
}
