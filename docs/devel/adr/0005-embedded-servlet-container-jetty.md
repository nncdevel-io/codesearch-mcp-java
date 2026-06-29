# ADR-0005: 単独利用の HTTP に組込み Jetty を採用する

## ステータス

Accepted（2026-06-29）

## 背景

単独利用（standalone）で Streamable HTTP を提供するには、MCP Java SDK の
`HttpServletStreamableServerTransportProvider`（jakarta サーブレット）を載せる
サーブレットコンテナーが要る。Spring は使わない（ADR-0001）。

## 検討した選択肢

| 選択肢 | jakarta サーブレット | 組込み容易性 | 依存量 | 結論 |
| --- | --- | --- | --- | --- |
| A. 組込み Jetty 12（ee10） | 対応（jakarta.servlet 6 / ee10） | 数行で起動 | 中 | **採用** |
| B. 組込み Tomcat | 対応 | やや手数 | 中 | 不採用（Jetty の方が組込みが軽い） |
| C. Undertow | サーブレットは別ブリッジ | 中 | 中 | 不採用 |

## 決定

**A. 組込み Jetty 12（`org.eclipse.jetty.ee10:jetty-ee10-servlet` ＋
`org.eclipse.jetty:jetty-server`）を採用する。**

- `HttpServletStreamableServerTransportProvider` は jakarta サーブレットであり、
  Jetty 12 ee10（jakarta.servlet 6）に `ServletContextHandler` 経由でそのまま
  登録できる。
- `ServerConnector` のポートに 0 を指定すると ephemeral ポートが割り当てられ、
  端点間テストで実ポートを取得して接続できる（`HttpEndToEndTest` で検証済み）。
- 検証済みバージョン: Jetty 12.0.16。

## 帰結

- standalone は Jetty に依存する（core は非依存のまま）。
- Spring Boot 統合では、同じサーブレットトランスポートを Spring の
  サーブレットコンテナーへ `ServletRegistrationBean` で登録する（ADR-0001）。
