package com.fuoverflow.auth.application;

import com.fuoverflow.auth.config.AuthProperties;
import com.fuoverflow.auth.config.OAuth2Properties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
public class SetPasswordEmailSender {
    private static final String TEMPLATE = "mail/set-password";

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final AuthProperties authProperties;
    private final OAuth2Properties oauth2Properties;

    public SetPasswordEmailSender(JavaMailSender mailSender,
                                   TemplateEngine templateEngine,
                                   AuthProperties authProperties,
                                   OAuth2Properties oauth2Properties) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.authProperties = authProperties;
        this.oauth2Properties = oauth2Properties;
    }

    @Async
    public void send(String email, String displayName, String token) {
        AuthProperties.PasswordReset config = authProperties.passwordReset();
        if (config == null || !config.enabled()) {
            return;
        }
        if (!StringUtils.hasText(config.from())) {
            throw new IllegalStateException("Mail sender address is not configured.");
        }
        String setLink = oauth2Properties.frontendBaseUrl()
                + "/settings/security/set-password?token=" + token;
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper message = new MimeMessageHelper(mimeMessage, false, StandardCharsets.UTF_8.name());
            message.setFrom(config.from());
            message.setTo(email);
            message.setSubject("Đặt mật khẩu cho tài khoản Fuexam của bạn");
            message.setText(renderHtml(displayName, setLink), true);
            mailSender.send(mimeMessage);
        } catch (MessagingException e) {
            throw new IllegalStateException("Failed to create set-password email", e);
        } catch (MailException e) {
            throw new IllegalStateException("Failed to send set-password email", e);
        }
    }

    private String renderHtml(String displayName, String setLink) {
        Context context = new Context(Locale.ENGLISH);
        context.setVariable("displayName", StringUtils.hasText(displayName) ? displayName : "Fuexam user");
        context.setVariable("setLink", setLink);
        return templateEngine.process(TEMPLATE, context);
    }
}
