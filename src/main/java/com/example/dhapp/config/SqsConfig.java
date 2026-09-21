package com.example.dhapp.config;

import java.net.URI;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.SqsClientBuilder;

/**
 * application.yml の {@code app.sqs.*} から Amazon SQS クライアントを組み立てる。
 *
 * <p>HTTP は JDK の {@code HttpURLConnection} を使う。クライアントは Spring が
 * 破棄するときに {@link SqsClient#close()} する。</p>
 */
@Configuration
public class SqsConfig {

    @Bean(destroyMethod = "close")
    public SqsClient sqsClient(
            @Value("${app.sqs.region:ap-northeast-1}") String region,
            @Value("${app.sqs.endpoint:}") String endpoint,
            @Value("${app.sqs.access-key:}") String accessKey,
            @Value("${app.sqs.secret-key:}") String secretKey,
            @Value("${app.sqs.connect-timeout-ms:3000}") long connectTimeoutMs,
            @Value("${app.sqs.read-timeout-ms:5000}") long readTimeoutMs,
            @Value("${app.sqs.api-call-timeout-ms:5000}") long apiCallTimeoutMs) {

        return createClient(region, endpoint, accessKey, secretKey,
                connectTimeoutMs, readTimeoutMs, apiCallTimeoutMs);
    }

    /**
     * 設定値から SQS クライアントを作る。認証情報は、アクセスキーとシークレットが
     * 両方あるときだけ静的に使い、両方空ならデフォルト認証チェーンに任せる。
     */
    public static SqsClient createClient(String region, String endpoint, String accessKey, String secretKey,
            long connectTimeoutMs, long readTimeoutMs, long apiCallTimeoutMs) {

        if (connectTimeoutMs <= 0 || readTimeoutMs <= 0 || apiCallTimeoutMs <= 0) {
            throw new IllegalStateException(
                    "app.sqs のタイムアウトは正のミリ秒で指定する。connect="
                            + connectTimeoutMs + ", read=" + readTimeoutMs + ", apiCall=" + apiCallTimeoutMs);
        }
        if (apiCallTimeoutMs < readTimeoutMs) {
            throw new IllegalStateException(
                    "app.sqs.api-call-timeout-ms (" + apiCallTimeoutMs
                            + ") は app.sqs.read-timeout-ms (" + readTimeoutMs + ") 以上にする。");
        }

        SqsClientBuilder builder = SqsClient.builder()
                .region(Region.of(region))
                .httpClientBuilder(UrlConnectionHttpClient.builder()
                        .connectionTimeout(Duration.ofMillis(connectTimeoutMs))
                        .socketTimeout(Duration.ofMillis(readTimeoutMs)))
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .apiCallTimeout(Duration.ofMillis(apiCallTimeoutMs))
                        .apiCallAttemptTimeout(Duration.ofMillis(readTimeoutMs))
                        .build());
        if (StringUtils.hasText(accessKey) || StringUtils.hasText(secretKey)) {
            if (!StringUtils.hasText(accessKey) || !StringUtils.hasText(secretKey)) {
                throw new IllegalStateException(
                        "app.sqs.access-key と app.sqs.secret-key は両方設定するか、両方空にしてデフォルト認証チェーンを使う。");
            }
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKey, secretKey)));
        }
        if (StringUtils.hasText(endpoint)) {
            builder.endpointOverride(URI.create(endpoint.trim()));
        }
        return builder.build();
    }
}
