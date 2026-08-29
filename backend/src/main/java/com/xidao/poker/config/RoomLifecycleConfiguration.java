package com.xidao.poker.config;

import com.xidao.poker.application.room.RoomService;
import com.xidao.poker.web.lifecycle.EmptyRoomCleanupScheduler;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/** 空房间 TTL 等应用生命周期任务的 Spring 调度适配器。 */
@Configuration
@EnableConfigurationProperties(PokerRoomProperties.class)
public class RoomLifecycleConfiguration {
    @Bean(name = "roomCleanupTaskScheduler")
    public ThreadPoolTaskScheduler roomCleanupTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("poker-room-cleanup-");
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        return scheduler;
    }

    @Bean
    public EmptyRoomCleanupScheduler emptyRoomCleanupScheduler(
            @Qualifier("roomCleanupTaskScheduler") TaskScheduler scheduler,
            RoomService rooms,
            PokerRoomProperties properties,
            PokerNetworkProperties networkProperties
    ) {
        if (properties.emptyTtl().compareTo(networkProperties.disconnectGrace()) <= 0) {
            throw new IllegalStateException("empty room ttl must be longer than disconnect grace");
        }
        return new EmptyRoomCleanupScheduler(
                scheduler,
                rooms,
                properties.emptyTtl(),
                properties.cleanupInterval()
        );
    }
}
