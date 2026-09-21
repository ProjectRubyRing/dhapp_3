package com.example.dhapp.controller;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.dhapp.dto.SqsEnqueueResponse;
import com.example.dhapp.service.SqsEnqueueService;
import com.example.dhapp.service.SqsEnqueueService.SqsEnqueueResult;

/**
 * application.yml の {@code app.sqs.queue-url} へ、半角スペース 1 文字を積む API。
 *
 * POST /api/sqs/enqueue
 *
 * リクエストボディは受け取らない。本文は常に半角スペース 1 文字。
 * キュー URL 未設定や SQS の送信失敗は {@code GlobalExceptionHandler} が 500 にする。
 */
@RestController
@RequestMapping("/api/sqs")
public class SqsController {

    private static final Logger log = LoggerFactory.getLogger(SqsController.class);

    private final SqsEnqueueService sqsEnqueueService;

    public SqsController(SqsEnqueueService sqsEnqueueService) {
        this.sqsEnqueueService = sqsEnqueueService;
    }

    /**
     * <pre>
     * curl -i -X POST http://localhost:8080/iwinmichl/api/sqs/enqueue
     * </pre>
     */
    @PostMapping(value = "/enqueue", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SqsEnqueueResponse> enqueue() {
        long startedAt = System.currentTimeMillis();
        String requestId = UUID.randomUUID().toString();
        log.info("POST /api/sqs/enqueue received. requestId={}", requestId);

        SqsEnqueueResult result = sqsEnqueueService.enqueue(requestId);

        SqsEnqueueResponse response = new SqsEnqueueResponse();
        response.setStatus("SUCCESS");
        response.setRequestId(requestId);
        response.setQueueUrl(result.getQueueUrl());
        response.setMessageId(result.getMessageId());
        response.setMd5OfMessageBody(result.getMd5OfMessageBody());
        response.setMessageBody(result.getMessageBody());
        response.setMessageBodyLength(result.getMessageBody().length());

        long elapsedMs = System.currentTimeMillis() - startedAt;
        log.info("POST /api/sqs/enqueue done. requestId={}, status={}, queueUrl={}, messageId={}, "
                        + "bodyLength={}, elapsedMs={}",
                requestId, response.getStatus(), response.getQueueUrl(), response.getMessageId(),
                response.getMessageBodyLength(), elapsedMs);
        return ResponseEntity.ok(response);
    }
}
