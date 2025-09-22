package com.quickstarts.kitchensink.dto;


import com.quickstarts.kitchensink.model.Member;
import lombok.Builder;

import java.time.LocalDate;

@Builder
public record MemberResponseDTO(
        String id,
        String name,
        String email,
        String phoneNumber,
        int age,
        String place,
        LocalDate registrationDate,
        Long version
) {
    public static MemberResponseDTO from(final Member member) {
        return MemberResponseDTO.builder()
                .id(member.getId())
                .name(member.getName())
                .email(member.getEmail())
                .phoneNumber(member.getPhoneNumber())
                .age(member.getAge())
                .place(member.getPlace())
                .registrationDate(member.getRegistrationDate())
                .build();
    }
}

