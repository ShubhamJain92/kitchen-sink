package com.quickstarts.kitchensink.dto;

public record MemberSnapshot(
        String name,
        String email,
        String phoneNumber,
        int age,
        String place
) {
}
