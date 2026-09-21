package com.example.dhapp.controller;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.dhapp.dto.ExternalHttpGetResponse;
import com.example.dhapp.service.ExternalHttpGetClient;

/**
 * 設定された外部 URL へ HTTP GET する API。
 *
 * GET /api/external-http-get/call
 *
 * {@code POST /api/external/execute}（HTTP POST）とは別コントローラ・別設定。
 * リクエストボディは受け取らない。接続先は {@code app.external-http-get.url}
 * （環境変数 {@code EXTERNAL_HTTP_GET_URL}）。Valkey・DB は呼ばない。
 * 接続不可・タイムアウト・URL 不正でも 500 にはせず、status=EXTERNAL_HTTP_GET_FAILED を返す。
 */
@RestController
@RequestMapping("/api/external-http-get")
public class ExternalHttpGetController {

    private static final Logger log = LoggerFactory.getLogger(ExternalHttpGetController.class);

    private final ExternalHttpGetClient externalHttpGetClient;

    public ExternalHttpGetController(ExternalHttpGetClient externalHttpGetClient) {
        this.externalHttpGetClient = externalHttpGetClient;
    }

    /**
     * <pre>
     * curl -i http://localhost:8080/iwinmichl/api/external-http-get/call
     * </pre>
     */
    @GetMapping(value = "/call", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ExternalHttpGetResponse> call() {
        long startedAt = System.currentTimeMillis();
        String requestId = UUID.randomUUID().toString();
        log.info("GET /api/external-http-get/call received. requestId={}", requestId);

        ExternalHttpGetResponse response = externalHttpGetClient.call(requestId);

        long elapsedMs = System.currentTimeMillis() - startedAt;
        log.info("GET /api/external-http-get/call done. requestId={}, status={}, httpStatus={}, elapsedMs={}",
                requestId, response.getStatus(), response.getHttpStatus(), elapsedMs);
        return ResponseEntity.ok(response);
    }
}
