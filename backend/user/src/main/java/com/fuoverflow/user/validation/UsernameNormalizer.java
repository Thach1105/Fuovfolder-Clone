package com.fuoverflow.user.validation;

import com.fuoverflow.common.exception.BadRequestException;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class UsernameNormalizer {
    private static final Pattern USERNAME = Pattern.compile("^[a-zA-Z0-9._-]{3,64}$");
    private static final Set<String> RESERVED = Set.of("admin", "root", "system", "api", "auth", "login", "register", "me");

    public String normalize(String username) {
        String normalized = username == null ? null : username.trim().toLowerCase(Locale.ROOT);
        if (normalized == null || !USERNAME.matcher(normalized).matches() || RESERVED.contains(normalized)) {
            throw new BadRequestException("INVALID_USERNAME", "Invalid username");
        }
        return normalized;
    }
}
