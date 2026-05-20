package org.superquinquin.members;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.LocalDate;

public record MemberDetail(
        @Schema(required = true) int id,
        @Schema(required = true) int number,
        @Schema(required = true) String firstName,
        @Schema(required = true) String lastName,
        @Schema(nullable = true) String email,
        @Schema(required = true) MemberStatus status,
        @Schema(nullable = true) String statusReason,
        @Schema(nullable = true) LocalDate joinedOn,
        @Schema(nullable = true) LocalDate leftOn,
        @Schema(nullable = true) NextShift nextShift,
        @Schema(nullable = true) Binome binome,
        @Schema(nullable = true) String photo
) {
    public record NextShift(
            @Schema(required = true) LocalDate date,
            @Schema(required = true) String time,
            @Schema(nullable = true) String role
    ) {}

    public record Binome(
            @Schema(required = true) int id,
            @Schema(required = true) int number,
            @Schema(required = true) String firstName,
            @Schema(required = true) String lastName,
            @Schema(required = true) MemberStatus status
    ) {}
}
