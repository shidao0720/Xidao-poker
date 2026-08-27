package com.xidao.poker.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xidao.poker.application.room.GameApplicationService;
import com.xidao.poker.application.history.HandHistoryPublisher;
import com.xidao.poker.application.room.RoomDeliverySink;
import com.xidao.poker.application.room.RoomEventDispatcher;
import com.xidao.poker.application.room.RoomRegistry;
import com.xidao.poker.web.ws.DisconnectGraceScheduler;
import com.xidao.poker.web.ws.PokerWebSocketHandler;
import com.xidao.poker.web.ws.WebSocketConnectionRegistry;
import com.xidao.poker.web.ws.WebSocketRoomDeliverySink;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.time.Clock;
import java.util.concurrent.Executor;

@Configuration
@EnableConfigurationProperties(PokerNetworkProperties.class)
public class PokerNetworkConfiguration {
    @Bean
    public WebSocketConnectionRegistry webSocketConnectionRegistry(PokerNetworkProperties properties) {
        return new WebSocketConnectionRegistry(
                properties.sendTimeLimitMillis(),
                properties.sendBufferBytes()
        );
    }

    @Bean
    public RoomDeliverySink roomDeliverySink(
            WebSocketConnectionRegistry connections,
            ObjectMapper objectMapper
    ) {
        return new WebSocketRoomDeliverySink(connections, objectMapper);
    }

    @Bean(name = "roomDeliveryExecutor")
    public ThreadPoolTaskExecutor roomDeliveryExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(1_024);
        executor.setThreadNamePrefix("poker-room-delivery-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        return executor;
    }

    @Bean
    public RoomEventDispatcher roomEventDispatcher(
            RoomRegistry registry,
            RoomDeliverySink sink,
            @Qualifier("roomDeliveryExecutor") Executor executor
    ) {
        return new RoomEventDispatcher(registry, sink, executor);
    }

    @Bean
    public GameApplicationService gameApplicationService(
            RoomRegistry registry,
            RoomEventDispatcher dispatcher,
            HandHistoryPublisher historyPublisher
    ) {
        return new GameApplicationService(registry, dispatcher, historyPublisher);
    }

    @Bean(name = "disconnectTaskScheduler")
    public ThreadPoolTaskScheduler disconnectTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("poker-disconnect-");
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        return scheduler;
    }

    @Bean
    public DisconnectGraceScheduler disconnectGraceScheduler(
            @Qualifier("disconnectTaskScheduler") TaskScheduler taskScheduler,
            GameApplicationService games,
            WebSocketConnectionRegistry connections,
            PokerNetworkProperties properties,
            Clock pokerClock
    ) {
        return new DisconnectGraceScheduler(
                taskScheduler,
                games,
                connections,
                properties.disconnectGrace(),
                pokerClock
        );
    }

    @Bean
    public PokerWebSocketHandler pokerWebSocketHandler(
            GameApplicationService games,
            WebSocketConnectionRegistry connections,
            DisconnectGraceScheduler disconnects,
            ObjectMapper objectMapper,
            PokerNetworkProperties properties
    ) {
        return new PokerWebSocketHandler(games, connections, disconnects, objectMapper, properties);
    }
}
