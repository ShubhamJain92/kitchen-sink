package com.kitchensink.core.member.dto;

import lombok.Builder;

@Builder
public record CreateMemberResponseDTO(String memberId, String message) {
}
