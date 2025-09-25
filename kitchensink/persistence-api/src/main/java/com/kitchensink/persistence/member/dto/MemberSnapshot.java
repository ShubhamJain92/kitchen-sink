package com.kitchensink.persistence.member.dto;

import com.kitchensink.persistence.member.model.Member;

public record MemberSnapshot(
        String name,
        String email,
        String phoneNumber,
        int age,
        String place
) {

    public static MemberSnapshot from(final Member m) {
        return new MemberSnapshot(m.getName(), m.getEmail(), m.getPhoneNumber(), m.getAge(), m.getPlace());
    }
}
