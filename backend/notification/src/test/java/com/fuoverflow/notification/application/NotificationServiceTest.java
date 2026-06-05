package com.fuoverflow.notification.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuoverflow.notification.domain.NotificationTypes;
import com.fuoverflow.notification.persistence.NotificationEntity;
import com.fuoverflow.notification.persistence.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {
    @Mock
    NotificationRepository notificationRepository;
    @Mock
    NotificationChannelFilter channelFilter;
    @Mock
    NotificationEmailSender emailSender;
    @Mock
    PushNotificationSender pushSender;

    NotificationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationService(
                notificationRepository,
                channelFilter,
                emailSender,
                pushSender,
                new ObjectMapper());
    }

    @Test
    void createThreadReplyBatchDispatchesAllChannels() {
        List<UUID> recipients = List.of(UUID.randomUUID());
        when(channelFilter.enabledRecipientsByChannel(recipients, NotificationTypes.THREAD_REPLY))
                .thenReturn(Map.of(
                        NotificationTypes.CHANNEL_WEB, recipients,
                        NotificationTypes.CHANNEL_EMAIL, recipients,
                        NotificationTypes.CHANNEL_PUSH, recipients));
        when(notificationRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        service.createThreadReplyBatch(
                recipients,
                UUID.randomUUID(),
                "PRO192",
                UUID.randomUUID(),
                "alice");

        verify(emailSender).sendThreadReplyBatch(eq(recipients), any());
        verify(pushSender).sendThreadReplyBatch(eq(recipients), any());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<NotificationEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(notificationRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
    }
}
