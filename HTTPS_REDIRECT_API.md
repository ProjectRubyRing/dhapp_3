# HTTPS リダイレクトの Location 確認 API

ALB（この環境では偽装コンテナ）が HTTPS を終端し、JBoss EAP へは平文 HTTP で渡す構成で、アプリの相対パス `sendRedirect` が付ける `Location` が `https` に直っているかを確認する。

コンテキストパスは `/iwinmichl`。

```
GET  /iwinmichl/api/https-redirect/inspect
GET  /iwinmichl/api/https-redirect/issue
HEAD /iwinmichl/api/https-redirect/issue
POST /iwinmichl/api/https-redirect/issue
GET  /iwinmichl/api/https-redirect/landed
```

この API は `Location` を書き換えない。コンテナが書く値を観測する。

## なぜ http になるか

Undertow は、`sendRedirect` の引数が `/` で始まる相対パスのとき、次の連結で絶対 URL にする。

```text
Location = exchange の scheme + "://" + Host + パス
```

ALB からコンテナへのソケットは平文なので、ヘッダを読む前の scheme は `http` である。ホスト名は `Host` のままなので、観測される形は `http://公開ホスト/パス` になる。

直る条件は次の二つが揃うこと。

1. 偽装 ALB が、クライアントとの通信で見たスキームを `X-Forwarded-Proto: https` として**置き換えて**渡す。カンマの左端だけが使われる。`http, https` の追記は `http` のままになる。
2. EAP の `http-listener` で `proxy-address-forwarding=true`。これで `ProxyPeerAddressHandler` が exchange の scheme を `https` に書き換えてからサーブレットが動く。

`server.forward-headers-strategy=framework`（Spring の `ForwardedHeaderFilter`）は、サーブレットの `getScheme()` だけを変える。`sendRedirect` が読む exchange の scheme は変わらない。このアプリではその設定を有効にしない。

RFC 7239 の `Forwarded: proto=https` も、`proxy-address-forwarding` では読まれない。偽装 ALB は `X-Forwarded-Proto` を送る。

絶対 URL（`http://...` で始まる文字列）を `sendRedirect` に渡した場合、コンテナはその文字列をそのまま `Location` に書く。この API の `/issue` は、コンテキストパス付きの相対パスだけを渡す。

```text
sendRedirect("/iwinmichl/api/https-redirect/landed")
```

## 偽装 ALB に期待する動き

実装は問わない。次を満たすこと。

| 項目 | 値 |
|---|---|
| クライアントから ALB | HTTPS |
| ALB から JBoss EAP | HTTP（`http-listener`） |
| `X-Forwarded-Proto` | `https` を 1 つだけ。入ってきた値は捨てて置き換える |
| `Host` | 公開ホスト名のまま（`:8080` は付かない） |
| EAP | `proxy-address-forwarding=true` |

`X-Forwarded-For` と `X-Forwarded-Port` は Location のスキーム判定には不要。レスポンスには記録する。

## 確認手順

リダイレクトは追わない。`curl -I` は HEAD で、既定では `Location` を追わない。

コンテナ直叩き（是正前の見え方。scheme が http のままなら Location も http）:

```
curl -s http://localhost:8080/iwinmichl/api/https-redirect/inspect
curl -sI http://localhost:8080/iwinmichl/api/https-redirect/issue
```

ヘッダだけ先に付けて、リスナが読んでいるかを見る（`proxy-address-forwarding=true` のときだけ Location が https になる）:

```
curl -sD - -o /dev/null \
  -H 'Host: public.example' \
  -H 'X-Forwarded-Proto: https' \
  http://localhost:8080/iwinmichl/api/https-redirect/issue
```

偽装 ALB 経由（ここが本確認）:

```
curl -k -s https://alb/iwinmichl/api/https-redirect/inspect
curl -k -sI https://alb/iwinmichl/api/https-redirect/issue
```

成功時の 302:

```text
HTTP/1.1 302 Found
Location: https://<公開ホスト>/iwinmichl/api/https-redirect/landed
```

`/landed` は飛び先。クライアントが `Location` を追ったあとの scheme を JSON で返す。Location そのものは `/issue` の応答ヘッダで見る。

POST も同じ 302 を返す。アップロード失敗後のリダイレクトと同じ入口で確認したいときに使う。

```
curl -k -sI -X POST https://alb/iwinmichl/api/https-redirect/issue
```

## inspect の判定

| status | httpsCorrected | 意味 |
|---|---|---|
| `HTTPS_CORRECTED` | `true` | Location のスキームが https。コンテナのソケットは平文（`cipher_suite` 無し）。`X-Forwarded-Proto` の左端が https |
| `HTTP_NOT_CORRECTED` | `false` | Location のスキームが http。ヘッダが無い、左端が http、またはリスナがヘッダを読んでいない |
| `HTTPS_DIRECT` | `false` | Location は https だが、コンテナ自身が TLS を終端している。今回の「ALB からコンテナは HTTP」の確認にはならない |
| `HTTPS_WITHOUT_FORWARDED_PROTO` | `false` | Location は https だが、`X-Forwarded-Proto` の左端が https ではない。ALB 経由の是正とは見なさない |
| `OTHER` | `false` | scheme が http でも https でもない |

`isSecure()` は scheme が https になっただけでも true になる。TLS 終端の判定には使わず、`jakarta.servlet.request.cipher_suite`（無ければ `javax.servlet.request.cipher_suite`）の有無を `containerTls` に出す。

`schemeSource` は次のどちらか。

| 値 | 意味 |
|---|---|
| `undertow-exchange` | `HttpServerExchange.getRequestScheme()` と `getHostAndPort()` を使った。sendRedirect と同じ材料 |
| `servlet-request` | exchange を読めない実行系。`HttpServletRequest.getScheme()` と `Host` で組み立てた |

`servletSchemeDisagrees=true` は、サーブレットの scheme と exchange の scheme が違う。JSON の `locationHeader` は exchange 側に合わせる。Spring のフィルタだけが scheme を https にしている状態は、ここで `HTTP_NOT_CORRECTED` になる。

WildFly 上ではリクエスト属性 `io.undertow.servlet.handlers.ServletRequestContext` か、リクエスト実装のクラスローダから `ServletRequestContext.current()` を呼んで exchange を読む。war のクラスローダが Undertow を見えなくても動く。読めない場合は例外にせず `servlet-request` に倒す。

## レスポンス項目

| 項目 | 内容 |
|---|---|
| `endpoint` | `inspect` または `landed` |
| `status` | 上の判定 |
| `httpsCorrected` | `HTTPS_CORRECTED` のときだけ true |
| `redirectMechanism` | 常に `HttpServletResponse.sendRedirect` |
| `redirectPath` | `/issue` が `sendRedirect` に渡すパス |
| `issuePath` | 実ヘッダを見るパス |
| `locationHeader` | 同じ材料で連結した絶対 URL。確定は `/issue` の応答ヘッダ |
| `locationScheme` | その URL のスキーム |
| `schemeSource` | `undertow-exchange` または `servlet-request` |
| `servletScheme` | `request.getScheme()` |
| `undertowExchangeScheme` | exchange の scheme。Undertow 以外は null |
| `servletSchemeDisagrees` | 上の二つが違う |
| `containerTls` | cipher_suite がある |
| `secure` | `request.isSecure()`。是正の成否には使わない |
| `hostHeader` | 今の `Host` |
| `forwardedProto` | `X-Forwarded-Proto` の生値 |
| `forwardedProtoLeftmost` | Undertow が採用する左端 |
| `forwardedHost` / `forwardedPort` / `forwardedFor` | 対応するヘッダ。判定には使わない |
| `forwardedHeader` | RFC 7239 の `Forwarded`。proxy-address-forwarding は読まない |
| `requestUrl` | `request.getRequestURL()`。`locationHeader` とは一致しないことがある |
| `message` / `hint` | 判定の説明と、次に見る場所 |

クエリとボディは無い。`/issue` は JSON を返さない。

## アプリ側でやらないこと

- `Location` の `http://` を `https://` に書き換えるフィルタは置かない。置き換えると、リスナ設定が無くても成功に見えてしまう。
- `redirect:` ビューや、絶対 URL を自分で連結して `setHeader("Location", ...)` する実装にはしない。今回直したいのはコンテナの `sendRedirect` である。
