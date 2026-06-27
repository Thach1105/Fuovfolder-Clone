package com.fuoverflow.auth.exception;

import com.fuoverflow.common.exception.ForbiddenException;

public class OAuthEmailNotVerifiedException extends ForbiddenException {
    public OAuthEmailNotVerifiedException() {
        super("OAUTH_EMAIL_NOT_VERIFIED",
              "Tài khoản OAuth2 chưa xác thực email. Vui lòng xác thực email trước khi đăng nhập.");
    }
}
