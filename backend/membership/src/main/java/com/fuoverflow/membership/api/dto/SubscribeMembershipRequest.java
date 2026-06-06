package com.fuoverflow.membership.api.dto;

import jakarta.validation.constraints.NotBlank;

public record SubscribeMembershipRequest(@NotBlank String planSlug) {
}
