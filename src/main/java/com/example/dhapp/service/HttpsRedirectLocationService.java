package com.example.dhapp.service;

import java.util.Locale;

import org.springframework.stereotype.Component;

import com.example.dhapp.dto.HttpsRedirectInspectResponse;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 相対パスの {@code sendRedirect} が書く {@code Location} を、コンテナと同じ材料で組み立てる。
 *
 * <p>Undertow は、引数が {@code /} で始まる相対パスのとき、絶対 URL を自分で連結する。
 * 使うスキームはサーブレットのラッパではなく {@code HttpServerExchange} の scheme である。
 * {@code proxy-address-forwarding=true} の {@code ProxyPeerAddressHandler} が
 * {@code X-Forwarded-Proto} のカンマ左端を exchange に書いたときだけ、その scheme は
 * {@code https} になる。Spring の {@code ForwardedHeaderFilter} はサーブレットの
 * {@code getScheme()} だけを変え、exchange は変えない。そのため scheme の取得は
 * exchange を優先する。</p>
 *
 * <p>{@code X-Forwarded-Proto} をこのクラスが読んで scheme を上書きすることはしない。
 * ヘッダが付いているのに scheme が {@code http} のまま、という未反映をそのまま返す。</p>
 */
@Component
public class HttpsRedirectLocationService {

    public static final String HTTPS_CORRECTED = "HTTPS_CORRECTED";
    public static final String HTTPS_DIRECT = "HTTPS_DIRECT";
    public static final String HTTPS_WITHOUT_FORWARDED_PROTO = "HTTPS_WITHOUT_FORWARDED_PROTO";
    public static final String HTTP_NOT_CORRECTED = "HTTP_NOT_CORRECTED";
    public static final String OTHER = "OTHER";

    public static final String SCHEME_SOURCE_UNDERTOW = "undertow-exchange";
    public static final String SCHEME_SOURCE_SERVLET = "servlet-request";
    public static final String REDIRECT_MECHANISM = "HttpServletResponse.sendRedirect";

    static final String LANDED_PATH = "/api/https-redirect/landed";
    static final String ISSUE_PATH = "/api/https-redirect/issue";
    private static final String UNDERTOW_CONTEXT_ATTRIBUTE =
            "io.undertow.servlet.handlers.ServletRequestContext";
    private static final String CIPHER_SUITE_JAKARTA = "jakarta.servlet.request.cipher_suite";
    private static final String CIPHER_SUITE_JAVAX = "javax.servlet.request.cipher_suite";

    /**
     * {@code sendRedirect} に渡すパス。先頭が {@code /} なので、コンテナはサーバールートからの
     * 絶対パスとしてスキームとホストを付ける。コンテキストパスを含めないと {@code /iwinmichl}
     * が落ちる。呼び出し元の入力は使わない。
     */
    public String redirectPath(HttpServletRequest request) {
        return contextPath(request) + LANDED_PATH;
    }

    /**
     * @param arrived {@code true} はリダイレクト先 {@code /landed}。判定材料は同じで、
     *                文言だけ「飛び先に着いた」ことを先に書く。
     */
    public HttpsRedirectInspectResponse describe(HttpServletRequest request, String requestId, boolean arrived) {
        String forwardedProto = sanitizeHeader(request.getHeader("X-Forwarded-Proto"));
        String leftmost = leftmostToken(forwardedProto);
        String forwardedHeader = sanitizeHeader(request.getHeader("Forwarded"));
        ExchangeView exchange = readUndertowExchange(request);

        String servletScheme = request.getScheme();
        String scheme = exchange != null ? exchange.scheme : servletScheme;
        scheme = sanitizeScheme(scheme);
        String schemeSource = exchange != null ? SCHEME_SOURCE_UNDERTOW : SCHEME_SOURCE_SERVLET;
        String host = resolveHost(request, exchange, scheme);
        String path = redirectPath(request);
        String location = scheme.isEmpty() ? "" : scheme + "://" + host + path;

        boolean containerTls = containerSawTls(request);
        boolean disagrees = exchange != null
                && servletScheme != null
                && !servletScheme.equalsIgnoreCase(exchange.scheme);

        Verdict verdict = judge(scheme, containerTls, leftmost, forwardedProto, forwardedHeader);
        if (disagrees) {
            verdict = verdict.withExtraHint(" servlet request の scheme は " + servletScheme
                    + " だが、Undertow exchange の scheme は " + exchange.scheme
                    + "。sendRedirect が使うのは exchange 側。");
        }
        if (arrived) {
            verdict = verdict.asArrival();
        }

        HttpsRedirectInspectResponse body = new HttpsRedirectInspectResponse();
        body.setEndpoint(arrived ? "landed" : "inspect");
        body.setStatus(verdict.status);
        body.setHttpsCorrected(verdict.httpsCorrected);
        body.setRequestId(requestId);
        body.setRedirectMechanism(REDIRECT_MECHANISM);
        body.setRedirectPath(path);
        body.setIssuePath(contextPath(request) + ISSUE_PATH);
        body.setLocationHeader(location);
        body.setLocationScheme(scheme);
        body.setSchemeSource(schemeSource);
        body.setServletScheme(servletScheme);
        body.setUndertowExchangeScheme(exchange == null ? null : exchange.scheme);
        body.setServletSchemeDisagrees(disagrees);
        body.setContainerTls(containerTls);
        body.setSecure(request.isSecure());
        body.setHostHeader(sanitizeHeader(request.getHeader("Host")));
        body.setServerName(request.getServerName());
        body.setServerPort(serverPort(request));
        body.setRequestUrl(requestUrl(request));
        body.setContextPath(contextPath(request));
        body.setForwardedProto(forwardedProto);
        body.setForwardedProtoLeftmost(leftmost);
        body.setForwardedHost(sanitizeHeader(request.getHeader("X-Forwarded-Host")));
        body.setForwardedPort(sanitizeHeader(request.getHeader("X-Forwarded-Port")));
        body.setForwardedFor(sanitizeHeader(request.getHeader("X-Forwarded-For")));
        body.setForwardedHeader(forwardedHeader);
        body.setMessage(verdict.message);
        body.setHint(verdict.hint);
        return body;
    }

    private static Verdict judge(String scheme, boolean containerTls, String leftmost,
            String forwardedProto, String forwardedHeader) {
        if ("https".equalsIgnoreCase(scheme) && !containerTls && "https".equalsIgnoreCase(leftmost)) {
            return new Verdict(HTTPS_CORRECTED, true,
                    "sendRedirect が組み立てる Location のスキームは https。"
                            + "コンテナとのソケットは平文で、X-Forwarded-Proto の左端が https。",
                    "確定は /api/https-redirect/issue をリダイレクトを追わずに呼び、"
                            + "応答の Location が https で始まること。"
                            + "Undertow 上では locationHeader は exchange の scheme と host を"
                            + " sendRedirect と同じ順で連結した値。");
        }
        if ("https".equalsIgnoreCase(scheme) && containerTls) {
            return new Verdict(HTTPS_DIRECT, false,
                    "Location のスキームは https。cipher_suite があるので、この接続はコンテナが TLS を終端している。",
                    "ALB からコンテナへ平文 HTTP で届き、X-Forwarded-Proto で https に直した状態ではない。"
                            + "偽装 ALB の先の http リスナ経由で呼び出す。");
        }
        if ("https".equalsIgnoreCase(scheme)) {
            return new Verdict(HTTPS_WITHOUT_FORWARDED_PROTO, false,
                    "Location のスキームは https だが、X-Forwarded-Proto の左端が https ではない。",
                    "偽装 ALB は、クライアントとの通信で見たスキームを X-Forwarded-Proto: https として"
                            + "置き換えて渡す。ALB 経由の是正とは判定しない。");
        }
        if ("http".equalsIgnoreCase(scheme)) {
            return new Verdict(HTTP_NOT_CORRECTED, false,
                    "sendRedirect が組み立てる Location のスキームは http。",
                    httpHint(leftmost, forwardedProto, forwardedHeader));
        }
        return new Verdict(OTHER, false,
                "Location のスキームを http でも https でも判定できなかった。",
                "scheme=" + scheme);
    }

    private static String httpHint(String leftmost, String raw, String forwardedHeader) {
        if ("https".equalsIgnoreCase(leftmost)) {
            return "X-Forwarded-Proto の左端は https だが、コンテナの request scheme は http のまま。"
                    + "Undertow の http-listener で proxy-address-forwarding=true になっているかを確認する。"
                    + "Spring の server.forward-headers-strategy だけでは、sendRedirect が読む"
                    + " exchange の scheme は変わらない。";
        }
        if (leftmost == null) {
            String base = "X-Forwarded-Proto が無い。偽装 ALB は HTTPS で受けた通信をコンテナへ HTTP で渡すとき、"
                    + "X-Forwarded-Proto: https を置き換えて付与する。";
            if (forwardedHeader != null && forwardedHeader.toLowerCase(Locale.ROOT).contains("proto=")) {
                return base + " RFC 7239 の Forwarded は、proxy-address-forwarding では読まれない。";
            }
            return base;
        }
        if ("http".equalsIgnoreCase(leftmost)
                && raw != null
                && raw.toLowerCase(Locale.ROOT).contains("https")) {
            return "X-Forwarded-Proto の左端が http。生値の後ろに https があっても Undertow は左端だけを使う。"
                    + "偽装 ALB は入ってきた値を捨て、自分が見た https を 1 つだけ書き込む。";
        }
        return "X-Forwarded-Proto の左端が " + leftmost
                + " なので、相対パスの sendRedirect は Location をそのスキームで絶対 URL にする。"
                + "偽装 ALB は https を左端に置く。";
    }

    private static String resolveHost(HttpServletRequest request, ExchangeView exchange, String scheme) {
        if (exchange != null && safeHost(exchange.hostAndPort)) {
            return exchange.hostAndPort;
        }
        // 制御文字や改行の手前までをホストとみなす。後ろに注入された行は Location に載せない。
        String fromHeader = hostPrefix(request.getHeader("Host"));
        if (fromHeader != null) {
            return fromHeader;
        }
        String name = hostPrefix(request.getServerName());
        if (name == null) {
            name = "localhost";
        }
        if (name.indexOf(':') >= 0 && !name.startsWith("[")) {
            name = "[" + name + "]";
        }
        if (!safeHost(name)) {
            return "invalid-host";
        }
        int port = serverPort(request);
        if (port <= 0 || isDefaultPort(scheme, port)) {
            return name;
        }
        return name + ":" + port;
    }

    /**
     * ホストとして使える先頭部分。空白、制御文字、{@code / \ ? # @} の手前で切る。
     */
    private static String hostPrefix(String raw) {
        if (raw == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(Math.min(raw.length(), 300));
        for (int i = 0; i < raw.length() && sb.length() < 300; i++) {
            char c = raw.charAt(i);
            if (c <= 0x20 || c == 0x7f || c == '/' || c == '\\' || c == '?' || c == '#' || c == '@') {
                break;
            }
            sb.append(c);
        }
        String prefix = sb.toString();
        return safeHost(prefix) ? prefix : null;
    }

    /**
     * {@code Host} が壊れていると、リクエスト実装がポート部分の解析で
     * {@link IllegalArgumentException} を投げることがある。診断 API はそこで落とさず、
     * ポート不明（0 以下）として扱う。Undertow の通常の {@code getServerPort} はソケットの
     * ポートを返すだけで、この例外は出さない。
     */
    private static int serverPort(HttpServletRequest request) {
        try {
            return request.getServerPort();
        } catch (IllegalArgumentException ex) {
            return -1;
        }
    }

    private static String requestUrl(HttpServletRequest request) {
        try {
            StringBuffer url = request.getRequestURL();
            return url == null ? null : url.toString();
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static boolean isDefaultPort(String scheme, int port) {
        if ("https".equalsIgnoreCase(scheme)) {
            return port == 443;
        }
        if ("http".equalsIgnoreCase(scheme)) {
            return port == 80;
        }
        return false;
    }

    /**
     * 平文ソケットには cipher_suite が付かない。{@code isSecure()} は scheme が https に
     * なっただけでも true になるため、TLS 終端の判定には使わない。
     */
    private static boolean containerSawTls(HttpServletRequest request) {
        Object cipher = request.getAttribute(CIPHER_SUITE_JAKARTA);
        if (cipher == null) {
            cipher = request.getAttribute(CIPHER_SUITE_JAVAX);
        }
        return cipher != null && !cipher.toString().isBlank();
    }

    /**
     * デプロイのクラスローダから Undertow が見えなくても、リクエスト実装のクラスローダと
     * リクエスト属性経由なら exchange を読める。読めない実行系（組み込み Tomcat や単体テスト）
     * では null を返し、サーブレットの scheme に倒す。
     */
    private static ExchangeView readUndertowExchange(HttpServletRequest request) {
        Object ctx = request.getAttribute(UNDERTOW_CONTEXT_ATTRIBUTE);
        if (ctx == null) {
            ctx = invokeUndertowCurrent(request);
        }
        if (ctx == null) {
            return null;
        }
        try {
            Object exchange = ctx.getClass().getMethod("getExchange").invoke(ctx);
            if (exchange == null) {
                return null;
            }
            Object scheme = exchange.getClass().getMethod("getRequestScheme").invoke(exchange);
            Object host = exchange.getClass().getMethod("getHostAndPort").invoke(exchange);
            if (scheme == null || scheme.toString().isBlank()) {
                return null;
            }
            return new ExchangeView(scheme.toString().trim(), host == null ? null : host.toString().trim());
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return null;
        }
    }

    private static Object invokeUndertowCurrent(HttpServletRequest request) {
        ClassLoader loader = request.getClass().getClassLoader();
        if (loader == null) {
            return null;
        }
        try {
            Class<?> type = Class.forName(
                    "io.undertow.servlet.handlers.ServletRequestContext", false, loader);
            return type.getMethod("current").invoke(null);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return null;
        }
    }

    private static String contextPath(HttpServletRequest request) {
        String context = request.getContextPath();
        return context == null ? "" : context;
    }

    static String leftmostToken(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        int comma = header.indexOf(',');
        String first = (comma < 0 ? header : header.substring(0, comma)).trim();
        return first.isEmpty() ? null : first;
    }

    private static String sanitizeScheme(String scheme) {
        if (scheme == null) {
            return "";
        }
        String trimmed = scheme.trim();
        if (trimmed.isEmpty() || trimmed.length() > 32) {
            return "";
        }
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            boolean ok = (c >= 'A' && c <= 'Z')
                    || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9')
                    || c == '+' || c == '-' || c == '.';
            if (!ok) {
                return "";
            }
        }
        return trimmed;
    }

    private static String sanitizeHeader(String value) {
        if (value == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(Math.min(value.length(), 512));
        for (int i = 0; i < value.length() && sb.length() < 512; i++) {
            char c = value.charAt(i);
            if (c == '\r' || c == '\n' || c == '\0' || c < 0x20 || c == 0x7f) {
                continue;
            }
            sb.append(c);
        }
        String sanitized = sb.toString().trim();
        return sanitized.isEmpty() ? null : sanitized;
    }

    private static boolean safeHost(String host) {
        if (host == null || host.isEmpty() || host.length() > 300) {
            return false;
        }
        for (int i = 0; i < host.length(); i++) {
            char c = host.charAt(i);
            if (c <= 0x20 || c == 0x7f || c == '/' || c == '\\' || c == '?' || c == '#' || c == '@') {
                return false;
            }
        }
        return true;
    }

    private static final class ExchangeView {
        private final String scheme;
        private final String hostAndPort;

        private ExchangeView(String scheme, String hostAndPort) {
            this.scheme = scheme;
            this.hostAndPort = hostAndPort;
        }
    }

    private static final class Verdict {
        private final String status;
        private final boolean httpsCorrected;
        private final String message;
        private final String hint;

        private Verdict(String status, boolean httpsCorrected, String message, String hint) {
            this.status = status;
            this.httpsCorrected = httpsCorrected;
            this.message = message;
            this.hint = hint;
        }

        private Verdict withExtraHint(String extra) {
            return new Verdict(status, httpsCorrected, message, hint + extra);
        }

        private Verdict asArrival() {
            return new Verdict(status, httpsCorrected,
                    "リダイレクト先に到達した。" + message,
                    "この応答は Location の飛び先。Location ヘッダ自体は /api/https-redirect/issue の 302 で確認する。"
                            + hint);
        }
    }
}
