package com.example.dhapp.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

/**
 * {@code app.sqs.queue-url} のキューへ、半角スペース 1 文字を SendMessage する。
 *
 * <p>本文はトリムしない。SQS のメッセージ本文として空白だけを送るのがこの API の契約。</p>
 */
@Service
public class SqsEnqueueService {

    /** キューに積む本文。半角スペース（U+0020）1 文字。 */
    static final String MESSAGE_BODY = " ";

    private static final Logger log = LoggerFactory.getLogger(SqsEnqueueService.class);

    private final SqsClient sqsClient;
    private final String queueUrl;

    public SqsEnqueueService(SqsClient sqsClient,
            @Value("${app.sqs.queue-url:}") String queueUrl) {
        this.sqsClient = sqsClient;
        this.queueUrl = queueUrl;
    }

    /**
     * 設定されたキューへ {@link #MESSAGE_BODY} を 1 件送る。
     *
     * @param requestId ログと呼び出し元で突き合わせる識別子
     * @return 送信先と SQS が返した messageId
     */
    public SqsEnqueueResult enqueue(String requestId) {
        if (MESSAGE_BODY.length() != 1 || MESSAGE_BODY.charAt(0) != ' ') {
            throw new IllegalStateException("SQS message body must be a single half-width space");
        }
        if (!StringUtils.hasText(queueUrl)) {
            throw new IllegalStateException(
                    "SQS queue URL is not configured. Set app.sqs.queue-url or the SQS_QUEUE_URL environment variable.");
        }

        log.info("Sending SQS message. requestId={}, queueUrl={}, bodyLength={}",
                requestId, queueUrl, MESSAGE_BODY.length());

        SendMessageResponse response = sqsClient.sendMessage(SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(MESSAGE_BODY)
                .build());

        log.info("SQS message sent. requestId={}, queueUrl={}, messageId={}, md5OfMessageBody={}",
                requestId, queueUrl, response.messageId(), response.md5OfMessageBody());
        return new SqsEnqueueResult(queueUrl, MESSAGE_BODY, response.messageId(), response.md5OfMessageBody());
    }

    /**
     * SendMessage の結果。HTTP レスポンスへ載せる前の値。
     */
    public static final class SqsEnqueueResult {

        private final String queueUrl;
        private final String messageBody;
        private final String messageId;
        private final String md5OfMessageBody;

        public SqsEnqueueResult(String queueUrl, String messageBody, String messageId, String md5OfMessageBody) {
            this.queueUrl = queueUrl;
            this.messageBody = messageBody;
            this.messageId = messageId;
            this.md5OfMessageBody = md5OfMessageBody;
        }

        public String getQueueUrl() {
            return queueUrl;
        }

        public String getMessageBody() {
            return messageBody;
        }

        public String getMessageId() {
            return messageId;
        }

        public String getMd5OfMessageBody() {
            return md5OfMessageBody;
        }
    }
}
