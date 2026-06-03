package com.fuoverflow.auth.application;

import com.fuoverflow.auth.config.AuthProperties;
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
public class PasswordResetEmailSender {
    private static final String TEMPLATE = "mail/password-reset";

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final AuthProperties properties;

    public PasswordResetEmailSender(JavaMailSender mailSender, TemplateEngine templateEngine, AuthProperties properties) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.properties = properties;
    }

    @Async
    public void send(String email, String token) {
        AuthProperties.PasswordReset config = properties.passwordReset();
        if (config == null || !config.enabled()) {
            return;
        }
        if (!StringUtils.hasText(config.from())) {
            throw new IllegalStateException("Mail sender address is not configured for password reset.");
        }

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper message = new MimeMessageHelper(mimeMessage, false, StandardCharsets.UTF_8.name());
            message.setFrom(config.from());
            message.setTo(email);
            message.setSubject(StringUtils.hasText(config.subject()) ? config.subject() : "Reset your FuOverflow password");
            message.setText(renderHtml(token, config.resetUrlBase()), true);
            mailSender.send(mimeMessage);
        } catch (MessagingException exception) {
            throw new IllegalStateException("Failed to create password reset email", exception);
        } catch (MailException exception) {
            throw new IllegalStateException("Failed to send password reset email", exception);
        }
    }

    private String renderHtml(String token, String resetUrlBase) {
        Context context = new Context(Locale.ENGLISH);
        context.setVariable("resetLink", resetLink(resetUrlBase, token));
        context.setVariable("resetToken", token);
        context.setVariable("expiresIn", "15 minutes");
        return templateEngine.process(TEMPLATE, context);
    }

    private String resetLink(String resetUrlBase, String token) {
        if (!StringUtils.hasText(resetUrlBase)) {
            return token;
        }
        String separator = resetUrlBase.contains("?") ? "&" : "?";
        return resetUrlBase + separator + "token=" + token;
    }
}
