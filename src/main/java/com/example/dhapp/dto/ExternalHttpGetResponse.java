package com.example.dhapp.dto;

/**
 * {@code GET /api/external-http-get/call} のレスポンスボディ。
 *
 * <p>{@code status=SUCCESS} は外部サーバから HTTP 応答を受け取ったことを意味する。
 * 上流が 4xx/5xx でも SUCCESS とし、結果は {@code httpStatus} で見る。
 * 接続不可・タイムアウト・URL 不正のときは {@code EXTERNAL_HTTP_GET_FAILED} とし、
 * HTTP 500 にはしない（{@code POST /api/external/execute} と同じ検証 API の方針）。</p>
 */
public class ExternalHttpGetResponse {

    private String status;
    private String requestId;
    private String url;

    /** 外部へ送ったメソッド。本 API では常に GET。 */
    private String method;

    /** 外部サーバの HTTP ステータス。接続できなかった場合は null。 */
    private Integer httpStatus;

    private String responseContentType;

    /** Content-Length が分かるときだけ入る。不明なら null。 */
    private Long responseBodyLength;

    /** レスポンスボディの先頭（最大 2048 文字）。長い場合は末尾が {@code ...(truncated)}。 */
    private String responseBodyPreview;

    private long elapsedMs;
    private String message;
    private String hint;
    private String exceptionClass;

    public ExternalHttpGetResponse() {
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }

    public void setHttpStatus(Integer httpStatus) {
        this.httpStatus = httpStatus;
    }

    public String getResponseContentType() {
        return responseContentType;
    }

    public void setResponseContentType(String responseContentType) {
        this.responseContentType = responseContentType;
    }

    public Long getResponseBodyLength() {
        return responseBodyLength;
    }

    public void setResponseBodyLength(Long responseBodyLength) {
        this.responseBodyLength = responseBodyLength;
    }

    public String getResponseBodyPreview() {
        return responseBodyPreview;
    }

    public void setResponseBodyPreview(String responseBodyPreview) {
        this.responseBodyPreview = responseBodyPreview;
    }

    public long getElapsedMs() {
        return elapsedMs;
    }

    public void setElapsedMs(long elapsedMs) {
        this.elapsedMs = elapsedMs;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getHint() {
        return hint;
    }

    public void setHint(String hint) {
        this.hint = hint;
    }

    public String getExceptionClass() {
        return exceptionClass;
    }

    public void setExceptionClass(String exceptionClass) {
        this.exceptionClass = exceptionClass;
    }
}
