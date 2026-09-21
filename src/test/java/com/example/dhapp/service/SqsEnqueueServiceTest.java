package com.example.dhapp.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;

import com.example.dhapp.config.SqsConfig;
import com.example.dhapp.controller.SqsController;
import com.example.dhapp.service.SqsEnqueueService.SqsEnqueueResult;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

class SqsEnqueueServiceTest {

    private static final String QUEUE_URL = "https://sqs.ap-northeast-1.amazonaws.com/123456789012/dhapp";

    @Test
    void enqueueSendsSingleHalfWidthSpace() {
        SqsClient client = mock(SqsClient.class);
        when(client.sendMessage(any(SendMessageRequest.class))).thenReturn(SendMessageResponse.builder()
                .messageId("mid-1")
                .md5OfMessageBody("7215ee9c7d9dc229d2921a40e899ec5f")
                .build());

        SqsEnqueueResult result = new SqsEnqueueService(client, QUEUE_URL).enqueue("req-1");

        ArgumentCaptor<SendMessageRequest> captor = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(client).sendMessage(captor.capture());
        SendMessageRequest sent = captor.getValue();
        assertEquals(" ", sent.messageBody());
        assertEquals(1, sent.messageBody().length());
        assertEquals(' ', sent.messageBody().charAt(0));
        assertEquals(0x20, sent.messageBody().codePointAt(0));
        assertEquals(QUEUE_URL, sent.queueUrl());
        assertEquals("mid-1", result.getMessageId());
        assertEquals(" ", result.getMessageBody());
    }

    @Test
    void enqueueDoesNotCallSqsWhenQueueUrlIsBlank() {
        SqsClient client = mock(SqsClient.class);
        SqsEnqueueService service = new SqsEnqueueService(client, "  ");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.enqueue("req-1"));

        assertTrue(ex.getMessage().contains("SQS_QUEUE_URL"));
        verify(client, never()).sendMessage(any(SendMessageRequest.class));
    }

    @Test
    void controllerReturnsTheSpaceThatWasQueued() {
        SqsEnqueueService service = mock(SqsEnqueueService.class);
        when(service.enqueue(any())).thenReturn(new SqsEnqueueResult(
                QUEUE_URL, " ", "mid-1", "7215ee9c7d9dc229d2921a40e899ec5f"));

        var response = new SqsController(service).enqueue();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("SUCCESS", response.getBody().getStatus());
        assertEquals(" ", response.getBody().getMessageBody());
        assertEquals(1, response.getBody().getMessageBodyLength());
        assertEquals("mid-1", response.getBody().getMessageId());
        assertEquals(QUEUE_URL, response.getBody().getQueueUrl());
    }

    @Test
    void applicationYmlDefinesTheSqsQueue() throws Exception {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application.yml", new ClassPathResource("application.yml"));
        assertEquals("${SQS_QUEUE_URL:}", property(sources, "app.sqs.queue-url"));
        assertEquals("${AWS_REGION:ap-northeast-1}", property(sources, "app.sqs.region"));
        assertEquals("${SQS_ENDPOINT:}", property(sources, "app.sqs.endpoint"));
    }

    @Test
    void clientUsesConfiguredRegionAndEndpoint() {
        try (SqsClient client = SqsConfig.createClient(
                "ap-northeast-1",
                "http://127.0.0.1:4566",
                "test-access-key",
                "test-secret-key",
                1000,
                1000,
                1000)) {
            assertEquals(Region.AP_NORTHEAST_1, client.serviceClientConfiguration().region());
            assertEquals(URI.create("http://127.0.0.1:4566"),
                    client.serviceClientConfiguration().endpointOverride().orElseThrow());
        }
    }

    @Test
    void clientRejectsAPartialStaticCredential() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> SqsConfig.createClient(
                "ap-northeast-1", "", "only-access-key", "", 1000, 1000, 1000));
        assertTrue(ex.getMessage().contains("secret-key"));
    }

    private static Object property(List<PropertySource<?>> sources, String key) {
        for (PropertySource<?> source : sources) {
            Object value = source.getProperty(key);
            if (value != null) {
                return value;
            }
        }
        throw new AssertionError("missing property: " + key);
    }
}
