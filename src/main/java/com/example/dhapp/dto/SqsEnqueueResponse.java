package com.example.dhapp.dto;

/**
 * {@code POST /api/sqs/enqueue} のレスポンス。
 * {@code messageBody} はキューに積んだ本文で、半角スペース 1 文字。
 */
public class SqsEnqueueResponse {

    private String status;
    private String requestId;
    private String queueUrl;
    private String messageId;
    private String md5OfMessageBody;
    private String messageBody;
    private int messageBodyLength;

    public SqsEnqueueResponse() {
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

    public String getQueueUrl() {
        return queueUrl;
    }

    public void setQueueUrl(String queueUrl) {
        this.queueUrl = queueUrl;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getMd5OfMessageBody() {
        return md5OfMessageBody;
    }

    public void setMd5OfMessageBody(String md5OfMessageBody) {
        this.md5OfMessageBody = md5OfMessageBody;
    }

    public String getMessageBody() {
        return messageBody;
    }

    public void setMessageBody(String messageBody) {
        this.messageBody = messageBody;
    }

    public int getMessageBodyLength() {
        return messageBodyLength;
    }

    public void setMessageBodyLength(int messageBodyLength) {
        this.messageBodyLength = messageBodyLength;
    }
}
