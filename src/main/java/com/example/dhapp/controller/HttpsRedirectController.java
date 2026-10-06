package com.example.dhapp.controller;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.example.dhapp.dto.HttpsRedirectInspectResponse;
import com.example.dhapp.service.HttpsRedirectLocationService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * ALB が HTTPS を終端し、JBoss EAP へは HTTP で渡す構成で、相対パスの
 * {@code sendRedirect} が付ける {@code Location} が {@code https} に直っているかを確認する。
 *
 * <p>この API は Location を書き換えない。コンテナが書く値を観測する。
 * 直る条件は、偽装 ALB が {@code X-Forwarded-Proto: https} を置き換えて渡し、
 * Undertow の {@code http-listener} が {@code proxy-address-forwarding=true} であること。</p>
 *
 * <pre>
 * curl -k -sI https://alb/iwinmichl/api/https-redirect/issue
 * curl -k -s  https://alb/iwinmichl/api/https-redirect/inspect
 * </pre>
 */
@RestController
@RequestMapping("/api/https-redirect")
public class HttpsRedirectController {

    private static final Logger log = LoggerFactory.getLogger(HttpsRedirectController.class);

    private final HttpsRedirectLocationService locationService;

    public HttpsRedirectController(HttpsRedirectLocationService locationService) {
        this.locationService = locationService;
    }

    /**
     * Location を出さず、sendRedirect が使う scheme と組み立て結果を JSON で返す。
     *
     * <pre>
     * curl -s http://localhost:8080/iwinmichl/api/https-redirect/inspect
     * </pre>
     */
    @GetMapping(value = "/inspect", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<HttpsRedirectInspectResponse> inspect(HttpServletRequest request) {
        String requestId = UUID.randomUUID().toString();
        log.info("GET /api/https-redirect/inspect received. requestId={}, servletScheme={}",
                requestId, request.getScheme());
        HttpsRedirectInspectResponse body = locationService.describe(request, requestId, false);
        log.info("GET /api/https-redirect/inspect done. requestId={}, status={}, httpsCorrected={}, "
                        + "locationScheme={}, schemeSource={}, locationHeader={}",
                requestId, body.getStatus(), body.isHttpsCorrected(), body.getLocationScheme(),
                body.getSchemeSource(), body.getLocationHeader());
        return ResponseEntity.ok(body);
    }

    /**
     * 相対パスで {@code sendRedirect} する。応答は 302 で、{@code Location} はコンテナが絶対 URL にする。
     * {@code curl -I}（HEAD）でもヘッダを見られる。POST は、アップロード失敗後の 302 と同じ入口にする。
     *
     * <pre>
     * curl -sI http://localhost:8080/iwinmichl/api/https-redirect/issue
     * curl -k -sI https://alb/iwinmichl/api/https-redirect/issue
     * </pre>
     */
    @RequestMapping(value = "/issue", method = {RequestMethod.GET, RequestMethod.HEAD, RequestMethod.POST})
    public void issue(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String requestId = UUID.randomUUID().toString();
        String path = locationService.redirectPath(request);
        log.info("/api/https-redirect/issue sendRedirect. requestId={}, method={}, path={}, servletScheme={}",
                requestId, request.getMethod(), path, request.getScheme());
        // 絶対 URL を渡すとコンテナはスキームを直さない。相対パスのときだけ exchange の scheme が付く。
        response.sendRedirect(path);
        log.info("/api/https-redirect/issue done. requestId={}, location={}",
                requestId, response.getHeader("Location"));
    }

    /**
     * {@code /issue} の Location の飛び先。クライアントが追ったあとの scheme を JSON で返す。
     *
     * <pre>
     * curl -s http://localhost:8080/iwinmichl/api/https-redirect/landed
     * </pre>
     */
    @GetMapping(value = "/landed", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<HttpsRedirectInspectResponse> landed(HttpServletRequest request) {
        String requestId = UUID.randomUUID().toString();
        log.info("GET /api/https-redirect/landed received. requestId={}, servletScheme={}",
                requestId, request.getScheme());
        HttpsRedirectInspectResponse body = locationService.describe(request, requestId, true);
        log.info("GET /api/https-redirect/landed done. requestId={}, status={}, httpsCorrected={}, requestUrl={}",
                requestId, body.getStatus(), body.isHttpsCorrected(), body.getRequestUrl());
        return ResponseEntity.ok(body);
    }
}
