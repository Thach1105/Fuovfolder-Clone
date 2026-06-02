package com.fuoverflow.auth.api.dto;
import jakarta.validation.constraints.*;
public record LoginRequest(@NotBlank @Size(max=320) String identifier,@NotBlank @Size(max=128) String password){}