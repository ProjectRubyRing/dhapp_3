package com.example.dhapp.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.example.dhapp.controller.ExternalHttpGetController;
import com.example.dhapp.dto.ExternalHttpGetResponse;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * {@code GET /api/external-http-get/call} が、設定 URL へ HTTP GET することの確認。
 * 既存の POST 用 {@link ExternalApiClient} とは別設定・別クライアントである。
 */
class ExternalHttpGetClientTest {

    @Test
    void callSendsGetAndReturnsTheResponseBody() throws Exception {
        AtomicReference<CapturedRequest> captured = new AtomicReference<>();
        HttpServer server = start("/get", exchange -> {
            captured.set(readRequest(exchange));
            byte[] body = "{\"message\":\"応答\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json;charset=UTF-8");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        try {
            String url = baseUrl(server) + "/get?ping=1";
            ExternalHttpGetResponse response = new ExternalHttpGetClient(url, 1000, 1000).call("req-1");

            CapturedRequest request = captured.get();
            assertNotNull(request);
            assertEquals("GET", request.method());
            assertEquals("/get?ping=1", request.uri());
            assertEquals(0, request.body().length);
            assertNull(request.contentType());

            assertEquals(ExternalHttpGetClient.STATUS_SUCCESS, response.getStatus());
            assertEquals("req-1", response.getRequestId());
            assertEquals(url, response.getUrl());
            assertEquals("GET", response.getMethod());
            assertEquals(Integer.valueOf(200), response.getHttpStatus());
            assertTrue(response.getResponseContentType().startsWith("application/json"));
            assertEquals(Long.valueOf("{\"message\":\"応答\"}".getBytes(StandardCharsets.UTF_8).length),
                    response.getResponseBodyLength());
            assertEquals("{\"message\":\"応答\"}", response.getResponseBodyPreview());
            assertNull(response.getMessage());
            assertTrue(response.getElapsedMs() >= 0);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void upstreamErrorStatusIsStillACompletedCall() throws Exception {
        HttpServer server = start("/down", exchange -> {
            readRequest(exchange);
            byte[] body = "unavailable".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain;charset=UTF-8");
            exchange.sendResponseHeaders(503, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        try {
            ExternalHttpGetResponse response = new ExternalHttpGetClient(baseUrl(server) + "/down", 1000, 1000)
                    .call("req-err");

            assertEquals(ExternalHttpGetClient.STATUS_SUCCESS, response.getStatus());
            assertEquals(Integer.valueOf(503), response.getHttpStatus());
            assertEquals("unavailable", response.getResponseBodyPreview());
            assertEquals(Long.valueOf(11), response.getResponseBodyLength());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void redirectIsNotFollowed() throws Exception {
        HttpServer server = start("/redir", exchange -> {
            readRequest(exchange);
            exchange.getResponseHeaders().set("Location", "http://127.0.0.1:1/should-not-be-requested");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        try {
            ExternalHttpGetResponse response = new ExternalHttpGetClient(baseUrl(server) + "/redir", 1000, 1000)
                    .call("req-redir");

            assertEquals(ExternalHttpGetClient.STATUS_SUCCESS, response.getStatus());
            assertEquals(Integer.valueOf(302), response.getHttpStatus());
            assertEquals("GET", response.getMethod());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void longBodyPreviewIsTruncated() throws Exception {
        byte[] body = "A".repeat(9000).getBytes(StandardCharsets.UTF_8);
        HttpServer server = start("/long", exchange -> {
            readRequest(exchange);
            exchange.getResponseHeaders().set("Content-Type", "text/plain");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        try {
            ExternalHttpGetResponse response = new ExternalHttpGetClient(baseUrl(server) + "/long", 1000, 1000)
                    .call("req-long");

            assertEquals(ExternalHttpGetClient.STATUS_SUCCESS, response.getStatus());
            assertEquals(Long.valueOf(9000), response.getResponseBodyLength());
            assertTrue(response.getResponseBodyPreview().endsWith("...(truncated)"));
            assertTrue(response.getResponseBodyPreview().startsWith("AAA"));
            assertTrue(response.getResponseBodyPreview().length() < body.length);
        } finally {
            server.stop(0);
        }
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void readTimeoutFailsBeforeTheLateResponse() throws Exception {
        HttpServer server = start("/slow", exchange -> {
            try {
                Thread.sleep(2000);
                byte[] body = "late".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            } catch (Exception ignored) {
                exchange.close();
            }
        });
        try {
            ExternalHttpGetResponse response = new ExternalHttpGetClient(baseUrl(server) + "/slow", 1000, 300)
                    .call("req-slow");

            assertEquals(ExternalHttpGetClient.STATUS_FAILED, response.getStatus());
            assertNull(response.getHttpStatus());
            assertNotNull(response.getMessage());
            assertNotNull(response.getExceptionClass());
            assertTrue(response.getHint().contains("EXTERNAL_HTTP_GET_READ_TIMEOUT_MS"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void connectionRefusedStaysInsideTheResponse() throws Exception {
        int port = closedPort();
        String url = "http://127.0.0.1:" + port + "/get";

        ExternalHttpGetResponse response = new ExternalHttpGetClient(url, 500, 500).call("req-down");

        assertEquals(ExternalHttpGetClient.STATUS_FAILED, response.getStatus());
        assertEquals(url, response.getUrl());
        assertEquals("GET", response.getMethod());
        assertNull(response.getHttpStatus());
        assertNotNull(response.getMessage());
        assertTrue(response.getHint().contains("EXTERNAL_HTTP_GET_CONNECT_TIMEOUT_MS"));
    }

    @Test
    void controllerReturnsHttp200WhenTheGetCannotConnect() throws Exception {
        int port = closedPort();
        ExternalHttpGetClient client = new ExternalHttpGetClient(
                "http://127.0.0.1:" + port + "/get", 500, 500);

        ResponseEntity<ExternalHttpGetResponse> response = new ExternalHttpGetController(client).call();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(ExternalHttpGetClient.STATUS_FAILED, response.getBody().getStatus());
        assertEquals("GET", response.getBody().getMethod());
        assertNotNull(response.getBody().getRequestId());
        assertFalse(response.getBody().getRequestId().isBlank());
    }

    @Test
    void rejectsUrlThatIsNotAbsoluteHttp() {
        List<String> rejected = List.of(
                "",
                "   ",
                "not-a-url",
                "file:///C:/Windows/win.ini",
                "ftp://example.com/a",
                "http:///missing-host",
                "http://example.com/a b",
                "http://example.com/a\nGET");
        for (String url : rejected) {
            ExternalHttpGetResponse response = new ExternalHttpGetClient(url, 500, 500).call("req-bad");
            assertEquals(ExternalHttpGetClient.STATUS_FAILED, response.getStatus(), url);
            assertNull(response.getHttpStatus(), url);
            assertEquals("GET", response.getMethod(), url);
            assertTrue(response.getMessage().contains("絶対 URL"), url);
            assertTrue(response.getHint().contains("EXTERNAL_HTTP_GET_URL"), url);
        }
    }

    @Test
    void getRouteIsGetOnly() throws Exception {
        ExternalHttpGetClient client = mock(ExternalHttpGetClient.class);
        ExternalHttpGetResponse body = new ExternalHttpGetResponse();
        body.setStatus(ExternalHttpGetClient.STATUS_SUCCESS);
        body.setMethod("GET");
        body.setHttpStatus(200);
        when(client.call(anyString())).thenReturn(body);

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ExternalHttpGetController(client)).build();

        mockMvc.perform(get("/api/external-http-get/call"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.method").value("GET"))
                .andExpect(jsonPath("$.httpStatus").value(200));
        mockMvc.perform(post("/api/external-http-get/call"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void controllerReturnsTheClientResult() {
        ExternalHttpGetClient client = mock(ExternalHttpGetClient.class);
        ExternalHttpGetResponse body = new ExternalHttpGetResponse();
        body.setStatus(ExternalHttpGetClient.STATUS_SUCCESS);
        body.setMethod("GET");
        body.setHttpStatus(200);
        body.setUrl("http://example.test/get");
        when(client.call(anyString())).thenReturn(body);

        ResponseEntity<ExternalHttpGetResponse> response = new ExternalHttpGetController(client).call();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(body, response.getBody());
        ArgumentCaptor<String> requestId = ArgumentCaptor.forClass(String.class);
        verify(client).call(requestId.capture());
        assertFalse(requestId.getValue().isBlank());
    }

    @Test
    void applicationYmlDefinesGetSeparatelyFromPost() throws Exception {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application.yml", new ClassPathResource("application.yml"));

        assertEquals("${EXTERNAL_API_URL:http://localhost:9090/receive}",
                property(sources, "app.external-api.url"));
        assertEquals("${EXTERNAL_HTTP_GET_URL:http://localhost:9090/get}",
                property(sources, "app.external-http-get.url"));
        assertEquals("${EXTERNAL_HTTP_GET_CONNECT_TIMEOUT_MS:2000}",
                property(sources, "app.external-http-get.connect-timeout-ms"));
        assertEquals("${EXTERNAL_HTTP_GET_READ_TIMEOUT_MS:5000}",
                property(sources, "app.external-http-get.read-timeout-ms"));
    }

    private static HttpServer start(String path, com.sun.net.httpserver.HttpHandler handler) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(path, handler);
        server.start();
        return server;
    }

    private static String baseUrl(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static CapturedRequest readRequest(HttpExchange exchange) throws IOException {
        byte[] body = exchange.getRequestBody().readAllBytes();
        return new CapturedRequest(
                exchange.getRequestMethod(),
                exchange.getRequestURI().toString(),
                body,
                exchange.getRequestHeaders().getFirst("Content-Type"));
    }

    private static int closedPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            return socket.getLocalPort();
        }
    }

    private static Object property(List<PropertySource<?>> sources, String key) {
        for (PropertySource<?> source : sources) {
            Object value = source.getProperty(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private record CapturedRequest(String method, String uri, byte[] body, String contentType) {
    }
}
