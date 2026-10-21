package com.dbdesigner.web;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class Dtos {

    private Dtos() {
    }

    public record RegisterRequest(@NotBlank @Email String email,
                                  @NotBlank @Size(min = 6, message = "A senha deve ter ao menos 6 caracteres") String password,
                                  @NotBlank String name) {
    }

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {
    }

    public record UserDto(Long id, String email, String name) {
    }

    public record AuthResponse(String token, UserDto user) {
    }

    public record CreateProjectRequest(@NotBlank String name,
                                       @Pattern(regexp = "oracle|mysql|postgres") String dbType) {
    }

    /** Campos nulos não são alterados. */
    public record UpdateProjectRequest(String name,
                                       @Pattern(regexp = "oracle|mysql|postgres") String dbType,
                                       JsonNode schema) {
    }

    public record ProjectDto(Long id, String name, String dbType, Instant updatedAt, int tableCount,
                             int fieldCount, String shareToken, JsonNode schema) {
    }

    public record ShareResponse(String shareToken) {
    }

    public record DdlRequest(@Pattern(regexp = "oracle|mysql|postgres") String dbType, JsonNode schema) {
    }

    public record ImportRequest(@NotBlank String sql) {
    }
}
