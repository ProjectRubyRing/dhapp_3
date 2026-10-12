package com.example.dhapp.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.RollingFileAppender;
import org.apache.logging.log4j.core.appender.rolling.CompositeTriggeringPolicy;
import org.apache.logging.log4j.core.appender.rolling.DefaultRolloverStrategy;
import org.apache.logging.log4j.core.appender.rolling.TimeBasedTriggeringPolicy;
import org.apache.logging.log4j.core.appender.rolling.TriggeringPolicy;
import org.apache.logging.log4j.core.config.Configurator;
import org.junit.jupiter.api.Test;
import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;

/**
 * tracelog が Log4j2 2.25.3 の時間ローテーションで、アプリログを受け取ること。
 * デフォルトの LoggerContext は触らない（他テストの log4j2-test.xml を壊さない）。
 */
class TraceLogConfigurationTest {

    @Test
    void slf4jBindsToLog4j2_25_3AndLogbackIsAbsent() throws Exception {
        ILoggerFactory factory = LoggerFactory.getILoggerFactory();
        assertEquals("org.apache.logging.slf4j.Log4jLoggerFactory", factory.getClass().getName());
        assertEquals("2.25.3", LogManager.class.getPackage().getImplementationVersion());
        assertEquals("2.25.3",
                org.apache.logging.log4j.core.LoggerContext.class.getPackage().getImplementationVersion());
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("ch.qos.logback.classic.LoggerContext"));
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("org.apache.logging.slf4j.SLF4JProvider"));
    }

    @Test
    void tracelogRollsHourlyAndReceivesTheAppLogger() throws Exception {
        Path dir = Files.createTempDirectory("dhapp-tracelog");
        String previous = System.setProperty("LOG_OUT_DIR", dir.toString().replace('\\', '/'));
        LoggerContext context = null;
        try {
            URL resource = TraceLogConfigurationTest.class.getClassLoader().getResource("log4j2-spring.xml");
            assertTrue(resource != null, "log4j2-spring.xml がクラスパスに無い");
            context = Configurator.initialize("dhapp-tracelog-" + UUID.randomUUID(),
                    TraceLogConfigurationTest.class.getClassLoader(), resource.toURI());

            RollingFileAppender trace = context.getConfiguration().getAppender("TRACE_FILE");
            assertTrue(trace.getFileName().endsWith("/tracelog") || trace.getFileName().endsWith("\\tracelog"),
                    trace.getFileName());
            assertTrue(trace.getFilePattern().contains("%d{yyyy-MM-dd-HH}"), trace.getFilePattern());
            assertFalse(trace.getFilePattern().contains("%i"), trace.getFilePattern());
            // ポリシーが 1 つでも Log4j2 は CompositeTriggeringPolicy で包む。
            CompositeTriggeringPolicy policies = assertInstanceOf(
                    CompositeTriggeringPolicy.class, trace.getTriggeringPolicy());
            TriggeringPolicy[] children = policies.getTriggeringPolicies();
            assertEquals(1, children.length);
            TimeBasedTriggeringPolicy timePolicy = assertInstanceOf(TimeBasedTriggeringPolicy.class, children[0]);
            assertEquals(1, timePolicy.getInterval());
            assertInstanceOf(DefaultRolloverStrategy.class, trace.getManager().getRolloverStrategy());

            context.getLogger("com.example.dhapp.controller.HealthCheckController")
                    .info("GET /api/healthcheck");
            trace.getManager().flush();

            String written = Files.readString(dir.resolve("tracelog"));
            assertTrue(written.contains("GET /api/healthcheck"), written);
            assertTrue(Files.exists(dir.resolve("application.log")));
            assertFalse(Files.exists(dir.resolve("error.log")) && Files.size(dir.resolve("error.log")) > 0
                    && Files.readString(dir.resolve("error.log")).contains("GET /api/healthcheck"));
        } finally {
            if (context != null) {
                Configurator.shutdown(context);
            }
            if (previous == null) {
                System.clearProperty("LOG_OUT_DIR");
            } else {
                System.setProperty("LOG_OUT_DIR", previous);
            }
        }
    }
}
