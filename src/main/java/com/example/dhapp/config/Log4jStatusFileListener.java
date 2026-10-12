package com.example.dhapp.config;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.status.StatusData;
import org.apache.logging.log4j.status.StatusListener;
import org.apache.logging.log4j.status.StatusLogger;

/**
 * Log4j2 の StatusLogger（アペンダ内部エラー）をファイルへ残す。
 *
 * <p>RollingFile の書き込み失敗は {@code ignoreExceptions=true} のため
 * アプリケーション例外にならない。Stale file handle は StatusLogger の ERROR として
 * 出るので、それを {@code ${LOG_OUT_DIR}/log4j2-status.log} に追記する。
 * このファイル自体はローテーションしない。ログ出力に Log4j2 は使わない。</p>
 */
public final class Log4jStatusFileListener implements StatusListener {

    static final String FILE_NAME = "log4j2-status.log";

    private static volatile Log4jStatusFileListener installed;

    private final PrintStream out;

    private Log4jStatusFileListener(PrintStream out) {
        this.out = out;
    }

    /**
     * プロセスに 1 つだけ登録する。出力先はシステムプロパティ {@code LOG_OUT_DIR}、
     * 無ければ環境変数、それも無ければ {@code logs}。
     */
    public static void registerDefault() {
        if (installed != null) {
            return;
        }
        synchronized (Log4jStatusFileListener.class) {
            if (installed != null) {
                return;
            }
            Log4jStatusFileListener listener = open(resolveLogDir().resolve(FILE_NAME));
            StatusLogger.getLogger().registerListener(listener);
            installed = listener;
        }
    }

    /** テスト用。呼び出し側が {@link #close()} する。 */
    static Log4jStatusFileListener register(Path file) {
        Log4jStatusFileListener listener = open(file);
        StatusLogger.getLogger().registerListener(listener);
        return listener;
    }

    static Path resolveLogDir() {
        String configured = System.getProperty("LOG_OUT_DIR");
        if (configured == null || configured.isBlank()) {
            configured = System.getenv("LOG_OUT_DIR");
        }
        if (configured == null || configured.isBlank()) {
            return Path.of("logs");
        }
        return Path.of(configured);
    }

    private static Log4jStatusFileListener open(Path file) {
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            PrintStream stream = new PrintStream(
                    Files.newOutputStream(file, java.nio.file.StandardOpenOption.CREATE,
                            java.nio.file.StandardOpenOption.APPEND),
                    true,
                    StandardCharsets.UTF_8);
            return new Log4jStatusFileListener(stream);
        } catch (IOException e) {
            e.printStackTrace(System.err);
            return new Log4jStatusFileListener(new PrintStream(System.err, true, StandardCharsets.UTF_8));
        }
    }

    @Override
    public void log(StatusData data) {
        try {
            String message = data.getMessage() == null ? "" : data.getMessage().getFormattedMessage();
            out.println(Instant.ofEpochMilli(data.getTimestamp()) + " " + data.getLevel() + " " + message);
            Throwable throwable = data.getThrowable();
            if (throwable != null) {
                throwable.printStackTrace(out);
            }
            out.flush();
        } catch (RuntimeException ex) {
            ex.printStackTrace(System.err);
        }
    }

    @Override
    public Level getStatusLevel() {
        return Level.ERROR;
    }

    @Override
    public void close() throws IOException {
        // removeListener が close を呼ぶ。ここで removeListener すると再帰する。
        if (installed == this) {
            installed = null;
        }
        if (out != System.err && out != System.out) {
            out.close();
        }
    }
}
