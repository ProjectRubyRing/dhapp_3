package com.example.dhapp.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import com.example.dhapp.dto.HttpsRedirectInspectResponse;

/**
 * Location のスキーム判定。ヘッダが付いているだけでは https にしない。
 * Undertow の exchange があるときは、サーブレットの scheme よりそちらを使う。
 */
class HttpsRedirectLocationServiceTest {

    private final HttpsRedirectLocationService service = new HttpsRedirectLocationService();

    @Test
    void httpSchemeStaysHttpEvenWhenForwardedProtoIsHttps() {
        MockHttpServletRequest request = baseRequest();
        request.setScheme("http");
        request.addHeader("X-Forwarded-Proto", "https");

        HttpsRedirectInspectResponse body = service.describe(request, "req-1", false);

        assertEquals(HttpsRedirectLocationService.HTTP_NOT_CORRECTED, body.getStatus());
        assertFalse(body.isHttpsCorrected());
        assertEquals("http", body.getLocationScheme());
        assertEquals("http://public.example/iwinmichl/api/https-redirect/landed", body.getLocationHeader());
        assertEquals("/iwinmichl/api/https-redirect/landed", body.getRedirectPath());
        assertEquals(HttpsRedirectLocationService.SCHEME_SOURCE_SERVLET, body.getSchemeSource());
        assertNull(body.getUndertowExchangeScheme());
        assertTrue(body.getHint().contains("proxy-address-forwarding"));
    }

    @Test
    void httpsSchemeWithoutContainerTlsAndWithForwardedProtoIsCorrected() {
        MockHttpServletRequest request = baseRequest();
        request.setScheme("https");
        request.addHeader("X-Forwarded-Proto", "https");

        HttpsRedirectInspectResponse body = service.describe(request, "req-2", false);

        assertEquals(HttpsRedirectLocationService.HTTPS_CORRECTED, body.getStatus());
        assertTrue(body.isHttpsCorrected());
        assertEquals("https", body.getLocationScheme());
        assertEquals("https://public.example/iwinmichl/api/https-redirect/landed", body.getLocationHeader());
        assertFalse(body.isContainerTls());
        assertEquals("inspect", body.getEndpoint());
    }

    @Test
    void containerTlsIsNotTreatedAsAlbCorrection() {
        MockHttpServletRequest request = baseRequest();
        request.setScheme("https");
        request.addHeader("X-Forwarded-Proto", "https");
        request.setAttribute("jakarta.servlet.request.cipher_suite", "TLS_AES_128_GCM_SHA256");

        HttpsRedirectInspectResponse body = service.describe(request, "req-3", false);

        assertEquals(HttpsRedirectLocationService.HTTPS_DIRECT, body.getStatus());
        assertFalse(body.isHttpsCorrected());
        assertTrue(body.isContainerTls());
        assertEquals("https", body.getLocationScheme());
    }

    @Test
    void httpsWithoutForwardedProtoIsNotAlbCorrection() {
        MockHttpServletRequest request = baseRequest();
        request.setScheme("https");

        HttpsRedirectInspectResponse body = service.describe(request, "req-4", false);

        assertEquals(HttpsRedirectLocationService.HTTPS_WITHOUT_FORWARDED_PROTO, body.getStatus());
        assertFalse(body.isHttpsCorrected());
    }

    @Test
    void undertowExchangeSchemeWinsOverServletScheme() {
        MockHttpServletRequest request = baseRequest();
        request.setScheme("http");
        request.addHeader("X-Forwarded-Proto", "https");
        request.setAttribute("io.undertow.servlet.handlers.ServletRequestContext",
                new ServletRequestContextFake("https", "public.example"));

        HttpsRedirectInspectResponse body = service.describe(request, "req-5", false);

        assertEquals(HttpsRedirectLocationService.HTTPS_CORRECTED, body.getStatus());
        assertTrue(body.isHttpsCorrected());
        assertEquals(HttpsRedirectLocationService.SCHEME_SOURCE_UNDERTOW, body.getSchemeSource());
        assertEquals("http", body.getServletScheme());
        assertEquals("https", body.getUndertowExchangeScheme());
        assertTrue(body.isServletSchemeDisagrees());
        assertEquals("https://public.example/iwinmichl/api/https-redirect/landed", body.getLocationHeader());
        assertTrue(body.getHint().contains("exchange"));
    }

    @Test
    void servletSchemeHttpsDoesNotHideHttpExchange() {
        MockHttpServletRequest request = baseRequest();
        request.setScheme("https");
        request.addHeader("X-Forwarded-Proto", "https");
        request.setAttribute("io.undertow.servlet.handlers.ServletRequestContext",
                new ServletRequestContextFake("http", "public.example"));

        HttpsRedirectInspectResponse body = service.describe(request, "req-6", false);

        assertEquals(HttpsRedirectLocationService.HTTP_NOT_CORRECTED, body.getStatus());
        assertFalse(body.isHttpsCorrected());
        assertTrue(body.isServletSchemeDisagrees());
        assertEquals("http://public.example/iwinmichl/api/https-redirect/landed", body.getLocationHeader());
    }

    @Test
    void leftmostForwardedProtoIsWhatUndertowWouldRead() {
        MockHttpServletRequest request = baseRequest();
        request.setScheme("http");
        request.addHeader("X-Forwarded-Proto", "http, https");

        HttpsRedirectInspectResponse body = service.describe(request, "req-7", false);

        assertEquals("http", body.getForwardedProtoLeftmost());
        assertEquals(HttpsRedirectLocationService.HTTP_NOT_CORRECTED, body.getStatus());
        assertTrue(body.getHint().contains("左端"));
    }

    @Test
    void forwardedHeaderAloneDoesNotCountAsXForwardedProto() {
        MockHttpServletRequest request = baseRequest();
        request.setScheme("http");
        request.addHeader("Forwarded", "proto=https;host=public.example");

        HttpsRedirectInspectResponse body = service.describe(request, "req-8", false);

        assertEquals(HttpsRedirectLocationService.HTTP_NOT_CORRECTED, body.getStatus());
        assertTrue(body.getHint().contains("Forwarded"));
        assertNull(body.getForwardedProtoLeftmost());
    }

    @Test
    void missingHostFallsBackToServerNameAndKeepsNonDefaultPort() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/https-redirect/inspect");
        request.setContextPath("/iwinmichl");
        request.setScheme("http");
        request.setServerName("app.internal");
        request.setServerPort(8080);

        HttpsRedirectInspectResponse body = service.describe(request, "req-9", false);

        assertEquals("http://app.internal:8080/iwinmichl/api/https-redirect/landed", body.getLocationHeader());
    }

    @Test
    void defaultHttpsPortIsOmittedWhenHostHeaderIsAbsent() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/https-redirect/inspect");
        request.setContextPath("");
        request.setScheme("https");
        request.setServerName("public.example");
        request.setServerPort(443);
        request.addHeader("X-Forwarded-Proto", "https");

        HttpsRedirectInspectResponse body = service.describe(request, "req-10", false);

        assertEquals("https://public.example/api/https-redirect/landed", body.getLocationHeader());
        assertTrue(body.isHttpsCorrected());
    }

    @Test
    void controlCharactersInHostAreNotCopiedIntoLocation() {
        MockHttpServletRequest request = baseRequest();
        request.setScheme("https");
        request.addHeader("X-Forwarded-Proto", "https");
        request.removeHeader("Host");
        request.addHeader("Host", "public.example\r\nX-Injected: 1");
        request.setServerName("public.example");
        request.setServerPort(443);

        HttpsRedirectInspectResponse body = service.describe(request, "req-11", false);

        assertFalse(body.getLocationHeader().contains("\r"));
        assertFalse(body.getLocationHeader().contains("\n"));
        assertFalse(body.getLocationHeader().contains("Injected"));
        assertFalse(body.getHostHeader().contains("\r"));
        assertFalse(body.getHostHeader().contains("\n"));
        assertEquals("https://public.example/iwinmichl/api/https-redirect/landed", body.getLocationHeader());
    }

    @Test
    void landedRewordsTheSameVerdict() {
        MockHttpServletRequest request = baseRequest();
        request.setScheme("https");
        request.addHeader("X-Forwarded-Proto", "https");

        HttpsRedirectInspectResponse body = service.describe(request, "req-12", true);

        assertEquals("landed", body.getEndpoint());
        assertEquals(HttpsRedirectLocationService.HTTPS_CORRECTED, body.getStatus());
        assertTrue(body.getMessage().startsWith("リダイレクト先に到達した。"));
        assertTrue(body.getHint().contains("/api/https-redirect/issue"));
    }

    @Test
    void redirectPathIncludesContextPathAndDoesNotUseRequestInput() {
        MockHttpServletRequest request = baseRequest();
        request.setParameter("to", "https://evil.example/phish");

        assertEquals("/iwinmichl/api/https-redirect/landed", service.redirectPath(request));
    }

    private static MockHttpServletRequest baseRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/https-redirect/inspect");
        request.setContextPath("/iwinmichl");
        request.setServerName("localhost");
        request.setServerPort(8080);
        request.addHeader("Host", "public.example");
        return request;
    }

    public static final class ServletRequestContextFake {
        private final String scheme;
        private final String host;

        ServletRequestContextFake(String scheme, String host) {
            this.scheme = scheme;
            this.host = host;
        }

        public ExchangeFake getExchange() {
            return new ExchangeFake(scheme, host);
        }
    }

    public static final class ExchangeFake {
        private final String scheme;
        private final String host;

        ExchangeFake(String scheme, String host) {
            this.scheme = scheme;
            this.host = host;
        }

        public String getRequestScheme() {
            return scheme;
        }

        public String getHostAndPort() {
            return host;
        }
    }
}
