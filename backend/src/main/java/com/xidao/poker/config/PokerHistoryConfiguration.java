package com.xidao.poker.config;

import com.xidao.poker.application.history.HandHistoryPublisher;
import com.xidao.poker.application.history.NoOpHandHistoryPublisher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 默认关闭历史数据库，确保纯局域网实时服务仍可零依赖启动。 */
@Configuration
@EnableConfigurationProperties(PokerPersistenceProperties.class)
public class PokerHistoryConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = "poker.persistence", name = "enabled", havingValue = "false", matchIfMissing = true)
    public HandHistoryPublisher disabledHandHistoryPublisher() {
        return NoOpHandHistoryPublisher.INSTANCE;
    }
}
