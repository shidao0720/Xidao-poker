package com.xidao.poker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

/**
 * 实时牌局阶段只使用内存状态。数据库自动配置会在持久化模块落地时重新启用；
 * 在此之前不能因为本机没有 PostgreSQL 而阻止 HTTP / WebSocket 服务启动。
 */
@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
public class PokerApplication {
    public static void main(String[] args) {
        SpringApplication.run(PokerApplication.class, args);
    }
}
