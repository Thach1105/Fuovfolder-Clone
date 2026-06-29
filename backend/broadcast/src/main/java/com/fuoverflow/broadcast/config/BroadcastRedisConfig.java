package com.fuoverflow.broadcast.config;

import com.fuoverflow.broadcast.infra.RedisBroadcastListener;
import com.fuoverflow.broadcast.infra.RedisBroadcastPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
public class BroadcastRedisConfig {

    @Bean
    RedisMessageListenerContainer broadcastListenerContainer(
            RedisConnectionFactory connectionFactory,
            RedisBroadcastListener listener) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(listener, new ChannelTopic(RedisBroadcastPublisher.CHANNEL));
        return container;
    }
}
