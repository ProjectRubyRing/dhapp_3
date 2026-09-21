package com.example.dhapp.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.example.dhapp.dto.ExternalHttpGetResponse;

/**
 * 設定された URL へ HTTP GET するクライアント。
 *
 * <p>{@link ExternalApiClient}（設定 URL へ HTTP POST する既存 API）とは別の設定・別の
 * クライアントである。デモの 2PC フローには含めない。HTTP 呼び出しはトランザクションに
 * 参加できないため、ここからも DB は触らない。</p>
 *
 * <p>URL は {@code app.external-http-get.url} 固定で、リクエストからは受け取らない。
 * スキームは http / https のみ。リダイレクトは追従せず、上流のステータスをそのまま返す。
 * 4xx/5xx でも例外にせず、接続不可・タイムアウト・URL 不正だけを失敗として返す。</p>
 */
@Service
public class ExternalHttpGetClient {

    private static final Logger log = LoggerFactory.getLogger(ExternalHttpGetClient.class);

    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "EXTERNAL_HTTP_GET_FAILED";
    public static final String METHOD = "GET";

    /** レスポンスボディから読み取る最大バイト数。 */
    private static final int MAX_BODY_READ_BYTES = 8192;

    /** レスポンスに載せるボディプレビューの最大文字数。 */
    private static final int BODY_PREVIEW_MAX_CHARS = 2048;

    private static final String URL_HINT =
            "app.external-http-get.url（環境変数 EXTERNAL_HTTP_GET_URL）に "
                    + "http または https の絶対 URL を設定する。";

    private static final String CONNECT_HINT =
            "接続先の起動状態と app.external-http-get.connect-timeout-ms / read-timeout-ms "
                    + "（環境変数 EXTERNAL_HTTP_GET_CONNECT_TIMEOUT_MS / EXTERNAL_HTTP_GET_READ_TIMEOUT_MS）を確認する。";

    private final RestClient restClient;
    private final String externalUrl;

    public ExternalHttpGetClient(
            @Value("${app.external-http-get.url:http://localhost:9090/get}") String externalUrl,
            @Value("${app.external-http-get.connect-timeout-ms:2000}") int connectTimeoutMs,
            @Value("${app.external-http-get.read-timeout-ms:5000}") int readTimeoutMs) {

        this.externalUrl = externalUrl;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws IOException {
                super.prepareConnection(connection, httpMethod);
                // GET は既定でリダイレクトに追従する。検証 API では上流の 3xx をそのまま見せる。
                connection.setInstanceFollowRedirects(false);
            }
        };
        factory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .build();
    }

    public String getExternalUrl() {
        return externalUrl;
    }

    /**
     * 設定 URL へ GET し、ステータスとボディの先頭を返す。
     * 呼び出し元へは例外を投げない。
     */
    public ExternalHttpGetResponse call(String requestId) {
        long startedAt = System.currentTimeMillis();
        String url = externalUrl == null ? null : externalUrl.trim();

        URI target = parseHttpUrl(url);
        if (target == null) {
            log.warn("External HTTP GET rejected. requestId={}, url={}", requestId, externalUrl);
            return fail(requestId, externalUrl, startedAt, null,
                    "http または https の絶対 URL ではない: " + externalUrl, URL_HINT);
        }

        log.info("Calling external HTTP API (GET). url={}, requestId={}", target, requestId);
        try {
            return restClient.get()
                    .uri(target)
                    .exchange((request, response) -> readResponse(response, requestId, target, startedAt));
        } catch (RestClientException e) {
            log.error("External HTTP GET failed. url={}, requestId={}", target, requestId, e);
            return fail(requestId, target.toString(), startedAt, e, e.getMessage(), CONNECT_HINT);
        }
    }

    private ExternalHttpGetResponse readResponse(ClientHttpResponse response, String requestId,
            URI target, long startedAt) throws IOException {

        ExternalHttpGetResponse body = base(requestId, target.toString());
        body.setHttpStatus(response.getStatusCode().value());

        MediaType contentType = response.getHeaders().getContentType();
        if (contentType != null) {
            body.setResponseContentType(contentType.toString());
        }
        long declaredLength = response.getHeaders().getContentLength();
        if (declaredLength >= 0) {
            body.setResponseBodyLength(declaredLength);
        }
        body.setResponseBodyPreview(readPreview(response.getBody()));
        body.setStatus(STATUS_SUCCESS);
        body.setElapsedMs(System.currentTimeMillis() - startedAt);

        log.info("External HTTP GET responded. url={}, requestId={}, httpStatus={}, elapsedMs={}",
                target, requestId, body.getHttpStatus(), body.getElapsedMs());
        return body;
    }

    /**
     * http / https の絶対 URL だけを受け付ける。それ以外は null。
     * リクエスト由来の URL は受け取らない（オープンプロキシにしない）。
     */
    private static URI parseHttpUrl(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }
        if (url.indexOf('\r') >= 0 || url.indexOf('\n') >= 0 || url.indexOf(' ') >= 0) {
            return null;
        }
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            return null;
        }
        String scheme = uri.getScheme();
        if (scheme == null) {
            return null;
        }
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            return null;
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            return null;
        }
        return uri;
    }

    /** ボディの先頭だけを読む（検証用途なので全量は読まない）。 */
    private static String readPreview(InputStream in) throws IOException {
        if (in == null) {
            return null;
        }
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[1024];
        int total = 0;
        int read;
        while (total < MAX_BODY_READ_BYTES
                && (read = in.read(chunk, 0, Math.min(chunk.length, MAX_BODY_READ_BYTES - total))) != -1) {
            buffer.write(chunk, 0, read);
            total += read;
        }
        if (total == 0) {
            return "";
        }
        String text = buffer.toString(StandardCharsets.UTF_8);
        if (text.length() > BODY_PREVIEW_MAX_CHARS) {
            return text.substring(0, BODY_PREVIEW_MAX_CHARS) + "...(truncated)";
        }
        return text;
    }

    private static ExternalHttpGetResponse base(String requestId, String url) {
        ExternalHttpGetResponse response = new ExternalHttpGetResponse();
        response.setRequestId(requestId);
        response.setUrl(url);
        response.setMethod(METHOD);
        return response;
    }

    private static ExternalHttpGetResponse fail(String requestId, String url, long startedAt,
            Throwable cause, String message, String hint) {

        ExternalHttpGetResponse response = base(requestId, url);
        response.setStatus(STATUS_FAILED);
        response.setMessage(message);
        response.setHint(hint);
        if (cause != null) {
            response.setExceptionClass(cause.getClass().getName());
        }
        response.setElapsedMs(System.currentTimeMillis() - startedAt);
        return response;
    }
}
