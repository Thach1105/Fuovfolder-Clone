package com.fuoverflow.auth.exception;

public class OAuthEmailNotVerifiedException extends RuntimeException {
    public OAuthEmailNotVerifiedException(String message) {
        super(message);
    }
}
