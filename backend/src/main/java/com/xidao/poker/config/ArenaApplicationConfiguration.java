package com.xidao.poker.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xidao.poker.application.account.AccountService;
import com.xidao.poker.application.arena.ArenaApplicationService;
import com.xidao.poker.application.arena.ArenaEventSink;
import com.xidao.poker.application.arena.ArenaRoomRegistry;
import com.xidao.poker.web.arena.ArenaConnectionRegistry;
import com.xidao.poker.web.arena.ArenaDisconnectScheduler;
import com.xidao.poker.web.arena.ArenaEventWebSocketSink;
import com.xidao.poker.web.arena.ArenaHandshakeInterceptor;
import com.xidao.poker.web.arena.ArenaWebSocketHandler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Configuration
public class ArenaApplicationConfiguration {
    @Bean(destroyMethod = "shutdown")
    public ScheduledExecutorService arenaScheduler() {
        return Executors.newScheduledThreadPool(4, Thread.ofPlatform().name("arena-runtime-", 0).factory());
    }

    @Bean
    public ArenaConnectionRegistry arenaConnectionRegistry(PokerNetworkProperties properties) {
        return new ArenaConnectionRegistry(properties.sendTimeLimitMillis(), properties.sendBufferBytes());
    }

    @Bean
    public ArenaEventSink arenaEventSink(ArenaConnectionRegistry connections, ObjectMapper objectMapper) {
        return new ArenaEventWebSocketSink(connections, objectMapper);
    }

    @Bean
    public ArenaRoomRegistry arenaRoomRegistry(ScheduledExecutorService arenaScheduler,
                                               ArenaEventSink arenaEventSink,
                                               Clock pokerClock) {
        return new ArenaRoomRegistry(arenaScheduler, arenaEventSink, pokerClock);
    }

    @Bean
    public ArenaApplicationService arenaApplicationService(ArenaRoomRegistry rooms) {
        return new ArenaApplicationService(rooms);
    }

    @Bean
    public ArenaDisconnectScheduler arenaDisconnectScheduler(ScheduledExecutorService arenaScheduler,
                                                             ArenaApplicationService arenas,
                                                             ArenaConnectionRegistry connections) {
        return new ArenaDisconnectScheduler(arenaScheduler, arenas, connections);
    }

    @Bean
    public ArenaHandshakeInterceptor arenaHandshakeInterceptor(ObjectProvider<AccountService> accounts,
                                                               PokerPersistenceProperties persistence) {
        return new ArenaHandshakeInterceptor(accounts, persistence);
    }

    @Bean
    public ArenaWebSocketHandler arenaWebSocketHandler(ArenaApplicationService arenas,
                                                       ArenaConnectionRegistry connections,
                                                       ArenaDisconnectScheduler disconnects,
                                                       ObjectMapper objectMapper,
                                                       PokerNetworkProperties properties) {
        return new ArenaWebSocketHandler(arenas, connections, disconnects, objectMapper, properties);
    }
}
