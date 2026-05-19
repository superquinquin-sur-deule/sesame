package org.superquinquin.members;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

public record MemberSummary(
        @Schema(required = true) int id,
        @Schema(required = true) int number,
        @Schema(required = true) String firstName,
        @Schema(required = true) String lastName,
        @Schema(nullable = true) String email,
        @Schema(required = true) MemberStatus status
) {}
