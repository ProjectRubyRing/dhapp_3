package com.example.dhapp.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;

import com.example.dhapp.dto.DemoRequest;
import com.example.dhapp.dto.DemoResponse;
import com.example.dhapp.dto.ExternalHttpGetResponse;
import com.example.dhapp.dto.FileUploadResponse;
import com.example.dhapp.service.DemoService;
import com.example.dhapp.service.ExternalApiClient;
import com.example.dhapp.service.ExternalHttpGetClient;
import com.example.dhapp.service.FileUploadService;
import com.example.dhapp.service.HttpsRedirectLocationService;
import com.example.dhapp.service.TransactionalDbService;
import com.example.dhapp.service.ValkeySessionService;

/**
 * demo / db / cache / external / external-http-get / file / log / https-redirect は、呼び出し時に
 * {@code DATA_OUTPUT_DIR/dummy.pdf} を作らない。
 */
class ListedApisDoNotWriteDummyPdfTest {

    @Test
    void callingTheListedApisDoesNotCreateDummyPdf() throws Exception {
        Path defaultPdf = Path.of("data", "dummy.pdf");
        String configuredDir = System.getenv("DATA_OUTPUT_DIR");
        Path configuredPdf = configuredDir == null || configuredDir.isBlank()
                ? null
                : Path.of(configuredDir, "dummy.pdf");
        FileStamp defaultBefore = stamp(defaultPdf);
        FileStamp configuredBefore = configuredPdf == null ? null : stamp(configuredPdf);

        DemoRequest request = new DemoRequest();
        request.setSessionId("session-001");
        request.setUserId("user-001");
        request.setMessage("hello");

        DemoService demoService = mock(DemoService.class);
        when(demoService.execute(any())).thenReturn(new DemoResponse());
        assertEquals(HttpStatus.OK, new DemoController(demoService).execute(request).getStatusCode());
        verify(demoService).execute(request);

        TransactionalDbService dbService = mock(TransactionalDbService.class);
        assertEquals(HttpStatus.OK, new DbController(dbService).execute(request).getStatusCode());
        verify(dbService).insertIntoBothDatabases(any(), anyString());

        ValkeySessionService valkeySessionService = mock(ValkeySessionService.class);
        when(valkeySessionService.saveSession(any(), anyString())).thenReturn("session:session-001");
        when(valkeySessionService.getSession(anyString())).thenReturn(Map.of("userId", "user-001"));
        assertEquals(HttpStatus.OK, new CacheController(valkeySessionService).execute(request).getStatusCode());
        verify(valkeySessionService).saveSession(any(), anyString());

        ExternalApiClient externalApiClient = mock(ExternalApiClient.class);
        when(externalApiClient.callExternalApi(any(), anyString())).thenReturn(200);
        assertEquals(HttpStatus.OK, new ExternalApiController(externalApiClient).execute(request).getStatusCode());
        verify(externalApiClient).callExternalApi(any(), anyString());

        ExternalHttpGetClient externalHttpGetClient = mock(ExternalHttpGetClient.class);
        when(externalHttpGetClient.call(anyString())).thenReturn(new ExternalHttpGetResponse());
        assertEquals(HttpStatus.OK, new ExternalHttpGetController(externalHttpGetClient).call().getStatusCode());
        verify(externalHttpGetClient).call(anyString());

        FileUploadService fileUploadService = mock(FileUploadService.class);
        when(fileUploadService.store(any(), anyString(), any(), anyString())).thenReturn(new FileUploadResponse());
        MockMultipartFile file = new MockMultipartFile(
                "file", "sample.bin", "application/octet-stream", new byte[] {1, 2, 3});
        assertEquals(HttpStatus.OK, new FileUploadController(fileUploadService)
                .upload(file, "note", new MockHttpServletRequest()).getStatusCode());
        verify(fileUploadService).store(any(), anyString(), any(), anyString());

        assertEquals(HttpStatus.OK, new LogTestController().errorTest(1).getStatusCode());

        HttpsRedirectController redirectController = new HttpsRedirectController(new HttpsRedirectLocationService());
        MockHttpServletRequest redirectRequest = new MockHttpServletRequest("GET", "/api/https-redirect/inspect");
        redirectRequest.setContextPath("/iwinmichl");
        redirectRequest.setScheme("https");
        redirectRequest.addHeader("Host", "public.example");
        redirectRequest.addHeader("X-Forwarded-Proto", "https");
        assertEquals(HttpStatus.OK, redirectController.inspect(redirectRequest).getStatusCode());
        assertEquals(HttpStatus.OK, redirectController.landed(redirectRequest).getStatusCode());
        MockHttpServletResponse redirectResponse = new MockHttpServletResponse();
        redirectController.issue(redirectRequest, redirectResponse);
        assertEquals(302, redirectResponse.getStatus());

        assertEquals(defaultBefore, stamp(defaultPdf));
        if (configuredPdf != null) {
            assertEquals(configuredBefore, stamp(configuredPdf));
        }
    }

    @Test
    void listedControllersDoNotReferenceDummyPdfGeneration() throws IOException {
        assertNoPdfCoupling(DemoController.class);
        assertNoPdfCoupling(DbController.class);
        assertNoPdfCoupling(CacheController.class);
        assertNoPdfCoupling(ExternalApiController.class);
        assertNoPdfCoupling(ExternalHttpGetController.class);
        assertNoPdfCoupling(FileUploadController.class);
        assertNoPdfCoupling(LogTestController.class);
        assertNoPdfCoupling(HttpsRedirectController.class);
    }

    private static void assertNoPdfCoupling(Class<?> controller) throws IOException {
        String resource = controller.getName().replace('.', '/') + ".class";
        byte[] bytes;
        try (InputStream in = controller.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("class resource not found: " + resource);
            }
            bytes = in.readAllBytes();
        }
        String pool = new String(bytes, StandardCharsets.ISO_8859_1);
        assertFalse(pool.contains("DummyPdfService"), controller.getSimpleName());
        assertFalse(pool.contains("createDummyPdf"), controller.getSimpleName());
        assertFalse(pool.contains("dummy.pdf"), controller.getSimpleName());
        assertFalse(pool.contains("PDDocument"), controller.getSimpleName());
    }

    private static FileStamp stamp(Path path) throws IOException {
        if (!Files.exists(path)) {
            return new FileStamp(false, -1, null);
        }
        return new FileStamp(true, Files.size(path), Files.getLastModifiedTime(path));
    }

    private record FileStamp(boolean exists, long size, FileTime modified) {
    }
}
