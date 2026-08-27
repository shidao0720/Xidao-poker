package com.xidao.poker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

/**
 * 实时牌局只使用内存状态。历史数据库由条件配置手动创建连接池，保留这里的排除项可确保
 * 默认模式以及 PostgreSQL 临时不可用时，不会由通用 DataSource 自动配置阻止服务启动。
 */
@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
public class PokerApplication {
    public static void main(String[] args) {
        SpringApplication.run(PokerApplication.class, args);
    }
}
