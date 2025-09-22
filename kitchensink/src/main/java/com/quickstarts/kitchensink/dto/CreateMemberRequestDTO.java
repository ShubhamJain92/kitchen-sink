package com.quickstarts.kitchensink.dto;

import com.quickstarts.kitchensink.model.Member;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import static java.time.LocalDate.now;

@Builder
public record CreateMemberRequestDTO(
        @NotBlank(message = "Name is required")
        @Size(max = 25, message = "Name cannot be more than 25 characters")
        @Pattern(
                regexp = "^(?=\\S)(?!.*\\s{2,})[\\p{L}][\\p{L} .'-]{0,24}$",
                message = "Use letters, spaces, dot (.), hyphen (-), apostrophe ('); " +
                        "no digits; no leading/trailing/double spaces"
        )
        String name,
        @NotBlank
        @Email
        String email,
        @NotBlank(message = "Phone number is required")
        @Pattern(
                regexp = "^(?:\\+91[- ]?|0)?[6-9]\\d{9}$",
                message = "Enter a valid Indian mobile number (10 digits starting 6–9)."
        )
        String phoneNumber,
        int age,
        @NotBlank(message = "Place is required")
        @Size(max = 50, message = "Place can be of at most 50 characters")
        @Pattern(
                regexp = "^(?=\\S)(?!.*\\s{2,})[\\p{L}][\\p{L} .,'-]{0,49}$",
                message = "Use letters, spaces, dot (.), comma (,), hyphen (-), apostrophe ('), no digits or double spaces"
        )
        String place
) {

    public static Member to(final CreateMemberRequestDTO createMemberRequestDTO) {
        return Member.builder()
                .name(createMemberRequestDTO.name())
                .email(createMemberRequestDTO.email())
                .phoneNumber(createMemberRequestDTO.phoneNumber())
                .age(createMemberRequestDTO.age)
                .place(createMemberRequestDTO.place)
                .registrationDate(now())
                .build();
    }
}
