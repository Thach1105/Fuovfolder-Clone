package com.fuoverflow.auth.application;

import com.fuoverflow.auth.config.AuthProperties;
import com.fuoverflow.auth.support.PasswordResetLinks;
import com.fuoverflow.common.config.CorsProperties;
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
import java.time.Duration;
import java.util.Locale;

@Service
public class PasswordResetEmailSender {
    private static final String TEMPLATE = "mail/password-reset";

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final AuthProperties properties;
    private final CorsProperties corsProperties;

    public PasswordResetEmailSender(
            JavaMailSender mailSender,
            TemplateEngine templateEngine,
            AuthProperties properties,
            CorsProperties corsProperties) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.properties = properties;
        this.corsProperties = corsProperties;
    }

    @Async
    public void send(String email, String displayName, String token) {
        AuthProperties.PasswordReset config = properties.passwordReset();
        if (config == null || !config.enabled()) {
            return;
        }
        if (!StringUtils.hasText(config.from())) {
            throw new IllegalStateException("Mail sender address is not configured. Set MAIL_FROM or MAIL_USERNAME.");
        }

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper message = new MimeMessageHelper(mimeMessage, false, StandardCharsets.UTF_8.name());
            message.setFrom(config.from());
            message.setTo(email);
            message.setSubject(StringUtils.hasText(config.subject()) ? config.subject() : "Reset your Fuexam password");
            message.setText(renderHtml(displayName, token, config.resetUrlBase(), config.tokenTtl()), true);
            mailSender.send(mimeMessage);
        } catch (MessagingException exception) {
            throw new IllegalStateException("Failed to create password reset email", exception);
        } catch (MailException exception) {
            throw new IllegalStateException("Failed to send password reset email", exception);
        }
    }

    private String renderHtml(String displayName, String token, String resetUrlBase, Duration tokenTtl) {
        Context context = new Context(Locale.ENGLISH);
        context.setVariable("displayName", StringUtils.hasText(displayName) ? displayName : "Fuexam user");
        context.setVariable("resetLink", PasswordResetLinks.buildLink(
                resetUrlBase, corsProperties.allowedOrigins(), token));
        context.setVariable("expiresIn", formatTtl(tokenTtl == null ? Duration.ofHours(1) : tokenTtl));
        return templateEngine.process(TEMPLATE, context);
    }

    private static String formatTtl(Duration ttl) {
        Duration value = ttl == null ? Duration.ofHours(1) : ttl;
        long hours = value.toHours();
        if (hours >= 1 && value.minusHours(hours).isZero()) {
            return hours + (hours == 1 ? " hour" : " hours");
        }
        long minutes = Math.max(1, value.toMinutes());
        return minutes + (minutes == 1 ? " minute" : " minutes");
    }
}
