package com.example.dhapp;

/**
 * Log4j 2.25 は設定 URL のプロトコルを {@code file, https, jar} だけ許可する。
 * WildFly / JBoss EAP のクラスローダが返す設定 URL は {@code vfs:} なので、
 * そのままでは起動時に
 * {@code Protocol vfs has not been enabled as an allowed protocol} になる。
 *
 * <p>システムプロパティ名は Log4j2 の
 * {@code UrlConnectionFactory.ALLOWED_PROTOCOLS}
 * （{@code log4j2.Configuration.allowedProtocols}）と一致させる。
 * 既に値が入っていて {@code vfs} を含む場合は変えない。</p>
 */
final class Log4jVfsProtocol {

    static final String PROPERTY = "log4j2.Configuration.allowedProtocols";

    private Log4jVfsProtocol() {
    }

    static void allow() {
        String current = System.getProperty(PROPERTY);
        if (current == null || current.isBlank()) {
            System.setProperty(PROPERTY, "file,https,jar,vfs");
            return;
        }
        for (String protocol : current.split(",")) {
            if ("vfs".equalsIgnoreCase(protocol.trim())) {
                return;
            }
        }
        System.setProperty(PROPERTY, current.trim() + ",vfs");
    }
}
