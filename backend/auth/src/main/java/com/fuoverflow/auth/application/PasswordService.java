package com.fuoverflow.auth.application;

import com.fuoverflow.common.exception.BadRequestException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class PasswordService {
    private final PasswordEncoder encoder;

    public PasswordService(PasswordEncoder encoder) { this.encoder = encoder; }

    public String encode(String raw) { validatePolicy(raw); return encoder.encode(raw); }
    public boolean matches(String raw, String encoded) { return encoder.matches(raw, encoded); }

    public void validatePolicy(String raw) {
        if (raw == null || raw.length() < 8 || raw.length() > 128) {
            throw new BadRequestException("WEAK_PASSWORD", "Password does not meet policy");
        }
    }
}
