package com.fuoverflow.user.validation;

import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class EmailNormalizer {
    public String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
