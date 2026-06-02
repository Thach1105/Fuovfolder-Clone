package com.fuoverflow.auth.api.dto;
import jakarta.validation.constraints.*;
public record RegisterRequest(
        @NotBlank @Email @Size(max=320) String email,
        @NotBlank @Size(min=3,max=64) String username,
        @NotBlank @Size(min=8,max=128) String password,
        @NotBlank @Size(max=120) String displayName,
        @Size(max=120) String campus){

}