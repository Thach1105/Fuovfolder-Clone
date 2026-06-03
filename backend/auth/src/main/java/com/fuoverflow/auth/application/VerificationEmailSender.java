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
public class VerificationEmailSender {
    private static final String TEMPLATE = "mail/email-verification";

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final AuthProperties properties;

    public VerificationEmailSender(JavaMailSender mailSender, TemplateEngine templateEngine, AuthProperties properties) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.properties = properties;
    }

    @Async
    public void send(String email, String displayName, String token) {
        AuthProperties.EmailVerification config = properties.emailVerification();
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
            message.setSubject(StringUtils.hasText(config.subject()) ? config.subject() : "Verify your FuOverflow email");
            message.setText(renderHtml(displayName, token, config.verificationUrlBase()), true);
            mailSender.send(mimeMessage);
        } catch (MessagingException exception) {
            throw new IllegalStateException("Failed to create verification email", exception);
        } catch (MailException exception) {
            throw new IllegalStateException("Failed to send verification email", exception);
        }
    }

    private String renderHtml(String displayName, String token, String verificationUrlBase) {
        Context context = new Context(Locale.ENGLISH);
        context.setVariable("displayName", StringUtils.hasText(displayName) ? displayName : "FuOverflow user");
        context.setVariable("verificationLink", verificationLink(verificationUrlBase, token));
        context.setVariable("verificationToken", token);
        context.setVariable("expiresIn", "24 hours");
        return templateEngine.process(TEMPLATE, context);
    }

    private String verificationLink(String verificationUrlBase, String token) {
        if (!StringUtils.hasText(verificationUrlBase)) {
            return token;
        }
        String separator = verificationUrlBase.contains("?") ? "&" : "?";
        return verificationUrlBase + separator + "token=" + token;
    }
}
