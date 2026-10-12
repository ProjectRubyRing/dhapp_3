package com.example.dhapp.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 死活確認。
 *
 * GET /api/healthcheck
 *
 * DB・キャッシュ・外部通信・ファイル生成は行わない。応答本体は空で、常に HTTP 200 を返す。
 * INFO を 1 行出すのは、その書き込みが log4j2 の tracelog（正時ローテーション）に載り、
 * 時間をまたいだときに Stale file handle を観測できるようにするため。
 * アペンダは書き込み失敗をリクエストへ伝えないので、ローテーション失敗でも 200 のまま。
 */
@RestController
@RequestMapping("/api/healthcheck")
public class HealthCheckController {

    private static final Logger log = LoggerFactory.getLogger(HealthCheckController.class);

    @GetMapping
    public ResponseEntity<Void> healthcheck() {
        log.info("GET /api/healthcheck");
        return ResponseEntity.ok().build();
    }
}
