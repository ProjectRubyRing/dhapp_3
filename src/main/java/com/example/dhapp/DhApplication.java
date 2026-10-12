package com.example.dhapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

/**
 * Spring Boot entry point.
 *
 * WildFly に WAR としてデプロイするため SpringBootServletInitializer を継承する。
 * DataSource は WildFly 側で XA データソースとして定義し JNDI 経由で参照するため、
 * Spring Boot の DataSourceAutoConfiguration は無効化する。
 */
@SpringBootApplication(exclude = { DataSourceAutoConfiguration.class })
public class DhApplication extends SpringBootServletInitializer {

    static {
        // WildFly は log4j2-spring.xml を vfs: URL で渡す。クラス初期化は
        // Spring のログ初期化より前なので、ここで許可リストへ vfs を足す。
        Log4jVfsProtocol.allow();
    }

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
        return builder.sources(DhApplication.class);
    }

    public static void main(String[] args) {
        SpringApplication.run(DhApplication.class, args);
    }
}
