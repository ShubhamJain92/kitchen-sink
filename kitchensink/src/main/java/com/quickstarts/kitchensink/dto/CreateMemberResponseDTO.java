package com.quickstarts.kitchensink.dto;

import lombok.Builder;

@Builder
public record CreateMemberResponseDTO(String memberId, String message) {
}
