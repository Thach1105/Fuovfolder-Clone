package com.fuoverflow.notification.application;

import com.fuoverflow.common.notification.NotificationRecipientLookup;
import com.fuoverflow.notification.config.NotificationProperties;
import com.fuoverflow.notification.domain.ThreadReplyNotificationPayload;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class NotificationEmailSender {
    private static final Logger log = LoggerFactory.getLogger(NotificationEmailSender.class);
    private static final String TEMPLATE = "mail/thread-reply";

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final NotificationProperties properties;
    private final NotificationRecipientLookup recipientLookup;

    public NotificationEmailSender(
            JavaMailSender mailSender,
            TemplateEngine templateEngine,
            NotificationProperties properties,
            NotificationRecipientLookup recipientLookup) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.properties = properties;
        this.recipientLookup = recipientLookup;
    }

    @Async
    public void sendThreadReplyBatch(List<UUID> recipientUserIds, ThreadReplyNotificationPayload payload) {
        NotificationProperties.Email config = properties.email();
        if (config == null || !config.enabled() || !StringUtils.hasText(config.from())) {
            return;
        }
        if (recipientUserIds == null || recipientUserIds.isEmpty()) {
            return;
        }

        Map<UUID, NotificationRecipientLookup.Recipient> recipients = recipientLookup
                .findEmailEligible(recipientUserIds).stream()
                .collect(Collectors.toMap(NotificationRecipientLookup.Recipient::userId, r -> r, (a, b) -> a));

        for (UUID userId : recipientUserIds) {
            NotificationRecipientLookup.Recipient recipient = recipients.get(userId);
            if (recipient == null || !StringUtils.hasText(recipient.email())) {
                continue;
            }
            try {
                sendOne(config, recipient, payload);
            } catch (Exception ex) {
                log.warn("Failed to send thread reply email to user {}: {}", userId, ex.getMessage());
            }
        }
    }

    private void sendOne(
            NotificationProperties.Email config,
            NotificationRecipientLookup.Recipient recipient,
            ThreadReplyNotificationPayload payload) throws Exception {
        MimeMessage mimeMessage = mailSender.createMimeMessage();
        MimeMessageHelper message = new MimeMessageHelper(mimeMessage, false, StandardCharsets.UTF_8.name());
        message.setFrom(config.from());
        message.setTo(recipient.email());
        message.setSubject(StringUtils.hasText(config.subject())
                ? config.subject()
                : payload.title());
        message.setText(renderHtml(config, recipient, payload), true);
        mailSender.send(mimeMessage);
    }

    private String renderHtml(
            NotificationProperties.Email config,
            NotificationRecipientLookup.Recipient recipient,
            ThreadReplyNotificationPayload payload) {
        Context context = new Context(Locale.forLanguageTag("vi"));
        context.setVariable("displayName", StringUtils.hasText(recipient.displayName())
                ? recipient.displayName()
                : "bạn");
        context.setVariable("body", payload.body());
        context.setVariable("threadUrl", threadUrl(config, payload.threadId()));
        return templateEngine.process(TEMPLATE, context);
    }

    private String threadUrl(NotificationProperties.Email config, UUID threadId) {
        String base = config.threadUrlBase();
        if (!StringUtils.hasText(base)) {
            return "/threads/" + threadId;
        }
        return base.endsWith("/") ? base + threadId : base + "/" + threadId;
    }
}
