package com.xidao.poker.config;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.xidao.poker.application.history.AsyncHandHistoryWriter;
import com.xidao.poker.application.history.HandHistoryPublisher;
import com.xidao.poker.application.history.HandHistoryRepository;
import com.xidao.poker.persistence.history.HandHistoryMapper;
import com.xidao.poker.persistence.history.FlywayHistorySchemaInitializer;
import com.xidao.poker.persistence.history.HistorySchemaInitializer;
import com.xidao.poker.persistence.history.PostgresHandHistoryRepository;
import com.xidao.poker.persistence.account.AccountMapper;
import com.xidao.poker.persistence.account.PostgresAccountService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.annotations.Mapper;
import org.flywaydb.core.Flyway;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import com.xidao.poker.application.room.GameApplicationService;
import com.xidao.poker.web.lifecycle.TableSettlementListener;

import javax.sql.DataSource;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.ZoneId;

@Configuration
@EnableTransactionManagement
@AutoConfigureAfter(MybatisPlusAutoConfiguration.class)
@MapperScan(basePackages = "com.xidao.poker.persistence", annotationClass = Mapper.class)
@ConditionalOnProperty(prefix = "poker.persistence", name = "enabled", havingValue = "true")
public class PokerPostgresHistoryConfiguration {
    @Bean(destroyMethod = "close")
    public HikariDataSource historyDataSource(PokerPersistenceProperties properties) {
        PokerPersistenceProperties.Database database = properties.database();
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setPoolName("poker-history-pool");
        dataSource.setJdbcUrl(database.jdbcUrl());
        dataSource.setUsername(database.username());
        dataSource.setPassword(database.password());
        dataSource.setMaximumPoolSize(database.maximumPoolSize());
        dataSource.setMinimumIdle(0);
        dataSource.setInitializationFailTimeout(-1);
        return dataSource;
    }

    @Bean
    public Flyway historyFlyway(DataSource historyDataSource) {
        return Flyway.configure()
                .dataSource(historyDataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(false)
                .load();
    }

    @Bean
    public HistorySchemaInitializer historySchemaInitializer(Flyway historyFlyway) {
        return new FlywayHistorySchemaInitializer(historyFlyway);
    }

    @Bean
    public PlatformTransactionManager historyTransactionManager(DataSource historyDataSource) {
        return new DataSourceTransactionManager(historyDataSource);
    }

    @Bean
    public HandHistoryRepository handHistoryRepository(
            HandHistoryMapper mapper,
            ObjectMapper objectMapper,
            SqlSessionFactory sqlSessionFactory,
            HistorySchemaInitializer schemaInitializer
    ) {
        // SqlSessionFactory 参数使该 Bean 显式依赖 MyBatis 完成初始化。
        if (sqlSessionFactory == null) throw new IllegalStateException("MyBatis session factory is required");
        return new PostgresHandHistoryRepository(mapper, objectMapper, schemaInitializer);
    }

    @Bean(destroyMethod = "close")
    public HandHistoryPublisher handHistoryPublisher(
            HandHistoryRepository repository,
            PokerPersistenceProperties properties
    ) {
        return new AsyncHandHistoryWriter(
                repository,
                properties.writerThreads(),
                properties.queueCapacity(),
                properties.maximumAttempts(),
                properties.initialRetryBackoff(),
                properties.shutdownWait()
        );
    }

    @Bean
    public PasswordEncoder accountPasswordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public PostgresAccountService accountService(
            AccountMapper mapper,
            HistorySchemaInitializer schemaInitializer,
            PasswordEncoder accountPasswordEncoder
    ) {
        return new PostgresAccountService(
                mapper,
                schemaInitializer,
                accountPasswordEncoder,
                new SecureRandom(),
                Clock.systemUTC(),
                ZoneId.of("Asia/Shanghai")
        );
    }

    @Bean
    public TableSettlementListener tableSettlementListener(
            PostgresAccountService economy,
            @Qualifier("disconnectTaskScheduler") TaskScheduler taskScheduler,
            GameApplicationService games,
            Clock pokerClock
    ) {
        return new TableSettlementListener(economy, taskScheduler, games, pokerClock);
    }
}
