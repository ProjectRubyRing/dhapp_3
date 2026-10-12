package com.example.dhapp.config;

import jakarta.annotation.PostConstruct;

import org.springframework.context.annotation.Configuration;

/**
 * 起動後に Log4j2 の StatusLogger を {@code log4j2-status.log} へつなぐ。
 * 時間ローテーションは起動より後に起きるので、ビーン生成後の登録で足りる。
 */
@Configuration
public class Log4jStatusFileConfiguration {

    @PostConstruct
    public void registerStatusFile() {
        Log4jStatusFileListener.registerDefault();
    }
}
