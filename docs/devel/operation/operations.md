# 運用手順

本サーバーの常設運用・同期戦略・監視・デバッグについて述べる。

## 起動

単独利用は実行可能 jar を `serve` で起動する。トランスポートは stdio または
Streamable HTTP を選ぶ（spec §2.1）。

```bash
java -jar codesearch-mcp-standalone-<version>.jar \
    serve --transport http --host 127.0.0.1 --port 8000 --repos config/repos.json
```

設定値の優先順位は CLI ＞ 環境変数（`CODE_SEARCH_REPOS_PATH` /
`CODE_SEARCH_SECRETS_PATH` / `CODE_SEARCH_WORKSPACE_ROOT` /
`CODE_SEARCH_CONFIG_PATH`）＞ `config/server.json` ＞ 組込みデフォルト。

起動時は同期完了を待たず、既存ワークスペースから readiness を導出して即座に
検索を提供する（要件 §5.2）。

## HTTP 公開時のセキュリティ

本サーバー自体に認証機構は無い（要件 §5.4）。HTTP で公開する場合は次のいずれかで
経路を絞る。

- 内部ネットワーク／VPN 内に限定する
- リバースプロキシで Bearer トークンや mTLS を前段に置く

## 同期戦略

同期はリクエストパスから分離されている（design §2）。トリガーは 2 系統。

1. **`sync` CLI**（外部定期実行）

   ```bash
   java -jar codesearch-mcp-standalone-<version>.jar sync --repos config/repos.json
   ```

   cron / systemd timer / Kubernetes CronJob から起動する。完了後、稼働中の
   `serve` へ PID ファイル経由で `SIGHUP` を送り、再起動なしで状態を再読込させる
   （ADR-0004）。PID ファイルはワークスペースルート直下
   （`.codesearch-serve.pid`）。

2. **in-process スケジューラー**（任意）

   `serve --enable-scheduler` で各リポジトリの `refresh_interval_seconds`
   間隔の定期同期を有効化する。既定は無効。

`SIGHUP` 通知は Linux/macOS のみ。Windows は対象外（要件 §6.1）。

## 監視

- 起動・同期・ツール呼び出しの主要イベントは構造化（JSON）ログとして
  **標準エラー出力**に出る（stdout は stdio トランスポートの MCP チャネルの
  ため汚さない）。
- 各リポジトリの最終同期状況は `list_repositories` ツールおよび
  `codesearch://repo/{id}` Resource の `status`（state / last_sync_at /
  last_outcome / last_error / last_commit）で確認できる。
- 秘密情報（トークン・SSH 鍵パス等）はログに出力されない（要件 §5.4、
  `Redaction` による再帰マスク）。

## デバッグログ（JSON-RPC トレース）

ロガー `io.nncdevel.codesearch`（または個別カテゴリー）のレベルを `DEBUG` に
すると、各ツール呼び出しのリクエスト（引数）と結果（`is_error`）が
`jsonrpc_tool_call` / `jsonrpc_tool_result` イベントとして出力される（要件 §5.6）。
既定の `INFO` では出力されない。

```xml
<!-- logback 例 -->
<logger name="io.nncdevel.codesearch" level="DEBUG"/>
```

### 既知の制約（正直な範囲）

要件 §5.6 が想定する「MCP の JSON-RPC メッセージ（リクエスト・レスポンス・
通知）の**全文**」のうち、本実装が DEBUG 出力するのは **ツール呼び出し境界**
（`tools/call` の引数と結果の成否）である。`initialize` / `tools/list` /
通知などトランスポート層を流れる全メッセージの wire レベル tee は、公式
MCP Java SDK 2.0.0 がトランスポート層の傍受フックを公開していないため、
現時点では提供していない。SDK が傍受点を公開した場合に拡張する
（リファレンス Python 実装は低レベルストリームを tee していたが、Java SDK では
同等の差込点が無い）。

## バックエンド要件

`git` と `ripgrep`（`rg`）が PATH 上に存在すること。コンテナー配布では同梱する。

## 性能の前提

要件 §5.1 の性能保証は、1 リポジトリ 5GB 以下・サーバー全体 50 リポジトリ以下、
4 vCPU / 8GB RAM・SSD・同時実行 16 以下を前提とする（spec §8.3）。
