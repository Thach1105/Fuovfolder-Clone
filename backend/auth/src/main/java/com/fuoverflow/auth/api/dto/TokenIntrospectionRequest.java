package com.fuoverflow.auth.api.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
public record TokenIntrospectionRequest(@NotBlank String token,@NotNull TokenType tokenType){ public enum TokenType{ACCESS,REFRESH}}