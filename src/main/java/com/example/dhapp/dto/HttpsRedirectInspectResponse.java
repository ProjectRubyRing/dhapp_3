package com.example.dhapp.dto;

/**
 * {@code GET /api/https-redirect/inspect} と {@code GET /api/https-redirect/landed} の応答。
 *
 * <p>{@code httpsCorrected=true} は、相対パスの {@code sendRedirect} が組み立てる
 * {@code Location} のスキームが {@code https} で、かつその https がコンテナ自身の TLS
 * 終端ではなく {@code X-Forwarded-Proto} の反映であること。実ヘッダの確定は
 * {@code /api/https-redirect/issue} の 302 を、リダイレクトを追わずに見る。</p>
 */
public class HttpsRedirectInspectResponse {

    private String endpoint;
    private String status;
    private boolean httpsCorrected;
    private String requestId;
    private String redirectMechanism;
    private String redirectPath;
    private String issuePath;
    private String locationHeader;
    private String locationScheme;
    private String schemeSource;
    private String servletScheme;
    private String undertowExchangeScheme;
    private boolean servletSchemeDisagrees;
    private boolean containerTls;
    private boolean secure;
    private String hostHeader;
    private String serverName;
    private int serverPort;
    private String requestUrl;
    private String contextPath;
    private String forwardedProto;
    private String forwardedProtoLeftmost;
    private String forwardedHost;
    private String forwardedPort;
    private String forwardedFor;
    private String forwardedHeader;
    private String message;
    private String hint;

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isHttpsCorrected() {
        return httpsCorrected;
    }

    public void setHttpsCorrected(boolean httpsCorrected) {
        this.httpsCorrected = httpsCorrected;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getRedirectMechanism() {
        return redirectMechanism;
    }

    public void setRedirectMechanism(String redirectMechanism) {
        this.redirectMechanism = redirectMechanism;
    }

    public String getRedirectPath() {
        return redirectPath;
    }

    public void setRedirectPath(String redirectPath) {
        this.redirectPath = redirectPath;
    }

    public String getIssuePath() {
        return issuePath;
    }

    public void setIssuePath(String issuePath) {
        this.issuePath = issuePath;
    }

    public String getLocationHeader() {
        return locationHeader;
    }

    public void setLocationHeader(String locationHeader) {
        this.locationHeader = locationHeader;
    }

    public String getLocationScheme() {
        return locationScheme;
    }

    public void setLocationScheme(String locationScheme) {
        this.locationScheme = locationScheme;
    }

    public String getSchemeSource() {
        return schemeSource;
    }

    public void setSchemeSource(String schemeSource) {
        this.schemeSource = schemeSource;
    }

    public String getServletScheme() {
        return servletScheme;
    }

    public void setServletScheme(String servletScheme) {
        this.servletScheme = servletScheme;
    }

    public String getUndertowExchangeScheme() {
        return undertowExchangeScheme;
    }

    public void setUndertowExchangeScheme(String undertowExchangeScheme) {
        this.undertowExchangeScheme = undertowExchangeScheme;
    }

    public boolean isServletSchemeDisagrees() {
        return servletSchemeDisagrees;
    }

    public void setServletSchemeDisagrees(boolean servletSchemeDisagrees) {
        this.servletSchemeDisagrees = servletSchemeDisagrees;
    }

    public boolean isContainerTls() {
        return containerTls;
    }

    public void setContainerTls(boolean containerTls) {
        this.containerTls = containerTls;
    }

    public boolean isSecure() {
        return secure;
    }

    public void setSecure(boolean secure) {
        this.secure = secure;
    }

    public String getHostHeader() {
        return hostHeader;
    }

    public void setHostHeader(String hostHeader) {
        this.hostHeader = hostHeader;
    }

    public String getServerName() {
        return serverName;
    }

    public void setServerName(String serverName) {
        this.serverName = serverName;
    }

    public int getServerPort() {
        return serverPort;
    }

    public void setServerPort(int serverPort) {
        this.serverPort = serverPort;
    }

    public String getRequestUrl() {
        return requestUrl;
    }

    public void setRequestUrl(String requestUrl) {
        this.requestUrl = requestUrl;
    }

    public String getContextPath() {
        return contextPath;
    }

    public void setContextPath(String contextPath) {
        this.contextPath = contextPath;
    }

    public String getForwardedProto() {
        return forwardedProto;
    }

    public void setForwardedProto(String forwardedProto) {
        this.forwardedProto = forwardedProto;
    }

    public String getForwardedProtoLeftmost() {
        return forwardedProtoLeftmost;
    }

    public void setForwardedProtoLeftmost(String forwardedProtoLeftmost) {
        this.forwardedProtoLeftmost = forwardedProtoLeftmost;
    }

    public String getForwardedHost() {
        return forwardedHost;
    }

    public void setForwardedHost(String forwardedHost) {
        this.forwardedHost = forwardedHost;
    }

    public String getForwardedPort() {
        return forwardedPort;
    }

    public void setForwardedPort(String forwardedPort) {
        this.forwardedPort = forwardedPort;
    }

    public String getForwardedFor() {
        return forwardedFor;
    }

    public void setForwardedFor(String forwardedFor) {
        this.forwardedFor = forwardedFor;
    }

    public String getForwardedHeader() {
        return forwardedHeader;
    }

    public void setForwardedHeader(String forwardedHeader) {
        this.forwardedHeader = forwardedHeader;
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
}
