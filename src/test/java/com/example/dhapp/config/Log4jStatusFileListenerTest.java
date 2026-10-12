package com.example.dhapp.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.logging.log4j.status.StatusLogger;
import org.junit.jupiter.api.Test;

/**
 * RollingFile が握りつぶした Stale file handle が status ファイルに残ること。
 */
class Log4jStatusFileListenerTest {

    @Test
    void recordsStaleFileHandleFromTheStatusLogger() throws Exception {
        Path dir = Files.createTempDirectory("dhapp-status");
        Path statusFile = dir.resolve(Log4jStatusFileListener.FILE_NAME);
        Log4jStatusFileListener listener = Log4jStatusFileListener.register(statusFile);
        try {
            StatusLogger.getLogger().error(
                    "Unable to write to stream " + dir.resolve("tracelog"),
                    new FileSystemException(dir.resolve("tracelog").toString(), null, "Stale file handle"));
            String text = Files.readString(statusFile);
            assertTrue(text.contains("Stale file handle"), text);
            assertTrue(text.contains("tracelog"), text);
        } finally {
            StatusLogger.getLogger().removeListener(listener);
        }
    }
}
