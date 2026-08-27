package com.xidao.poker.config;

import com.xidao.poker.application.room.RoomRegistry;
import com.xidao.poker.application.room.RoomService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** 组装纯应用层对象；Controller 与 WebSocket 适配器只依赖这些入口。 */
@Configuration
public class RoomApplicationConfiguration {
    @Bean
    public Clock pokerClock() {
        return Clock.systemUTC();
    }

    @Bean
    public RoomRegistry roomRegistry(Clock pokerClock) {
        return new RoomRegistry(pokerClock);
    }

    @Bean
    public RoomService roomService(RoomRegistry registry, Clock pokerClock) {
        return new RoomService(registry, pokerClock);
    }
}
