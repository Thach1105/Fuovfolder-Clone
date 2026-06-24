package com.fuoverflow.auth.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class OAuthIdentityLinkerUsernameTest {

    @Test
    void generatedUsernameMatchesPattern() throws Exception {
        String username = invokeGenerateUsername("test@example.com");
        assertThat(username).matches("[a-z0-9_-]+_[a-z0-9]+");
    }

    @Test
    void specialCharsInEmailAreStripped() throws Exception {
        String username = invokeGenerateUsername("Test.User+tag@example.com");
        assertThat(username).doesNotContain(".");
        assertThat(username).doesNotContain("+");
        assertThat(username).matches("[a-z0-9_-]+_[a-z0-9]+");
    }

    @Test
    void emptyLocalPartFallsBackToUser() throws Exception {
        String username = invokeGenerateUsername("!!!@example.com");
        assertThat(username).startsWith("user_");
    }

    @Test
    void noAtSignFallsBackToUser() throws Exception {
        String username = invokeGenerateUsername("malformed-email");
        assertThat(username).matches("[a-z0-9_-]+_[a-z0-9]+");
    }

    @RepeatedTest(50)
    void generatedUsernamesAreUnique() throws Exception {
        Set<String> names = new HashSet<>();
        for (int i = 0; i < 10; i++) {
            names.add(invokeGenerateUsername("same@example.com"));
        }
        assertThat(names).hasSizeGreaterThan(1);
    }

    @Test
    void longEmailLocalPartIsTruncated() throws Exception {
        String longLocal = "a".repeat(100) + "@example.com";
        String username = invokeGenerateUsername(longLocal);
        assertThat(username.length()).isLessThanOrEqualTo(70);
    }

    private String invokeGenerateUsername(String email) throws Exception {
        Method method = OAuthIdentityLinker.class.getDeclaredMethod("generateUsername", String.class);
        method.setAccessible(true);
        OAuthIdentityLinker linker = createLinkerForUsernameTest();
        return (String) method.invoke(linker, email);
    }

    private OAuthIdentityLinker createLinkerForUsernameTest() {
        return new OAuthIdentityLinker(null, null, null, null);
    }
}
