package com.quickstarts.kitchensink.dto;

import lombok.Builder;

@Builder
public record UserResponse(String message) {
}
