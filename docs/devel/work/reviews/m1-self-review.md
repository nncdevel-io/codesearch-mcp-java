# M1 自己レビュー（仕様適合・要件カバレッジ）

対象: マイルストーン M1（Java版の完全パリティ移植）。本書は完了時点の
仕様適合と要件カバレッジを自己点検した記録（TASK-051）。

## 検証ゲート（機械的）

- `./mvnw verify`: 全 4 モジュール BUILD SUCCESS（core / standalone /
  spring-boot-starter / parent）。
- テスト: core 108 ＋ standalone 3 ＋ spring-boot-starter 3。
- Spotless（palantir）: 全モジュール clean。
- Javadoc doclint（`failOnWarnings`）: 全モジュール warning 0。
- markdownlint-cli2: 0 error。

## ツール仕様（spec §4）

| ツール | 実装 | 主な検証 |
| --- | --- | --- |
| `search_code` | `SearchCodeTool` | content/files_with_matches/count、glob/type、context→範囲 URL、truncated、exclude_paths（結合テスト） |
| `list_files` | `ListFilesTool` | gitignore 対象も含む、last_modified 降順、truncated（結合テスト） |
| `list_tree` | `ListTreeTool` ＋ `TreeBuilder` | 深さ/件数制限、show_files、コードポイント順、罫線（単体＋結合） |
| `read_file` | `ReadFileTool` ＋ `LineFormatter` | 行番号タブ整形、FILE_TOO_LARGE/FILE_BINARY、範囲/単一 URL（結合） |
| `list_repositories` | `ListRepositoriesTool` | カタログ＋status（結合） |

Resource `codesearch://repo/{id}`、`tools`/`resources` capability（prompts 非公開）
は `CapabilityProvider` で実装し、HTTP 端点間テスト（`HttpEndToEndTest`）で
公式 MCP クライアントから検証済み。

## エラーモデル（spec §5）

`ErrorCode` 10 種、`{code,message,details}` 直列化（`ErrorJson`）、ドメイン
エラーは `isError` ＋ JSON 文字列で返却（`ExecutionGuard`）。REPO_NOT_FOUND /
REPO_NOT_READY / INVALID_PATH / PATH_NOT_FOUND / FILE_* / TIMEOUT を単体・結合
テストで確認。

## Git URL（spec §6）

4 ホスティング × 単一/範囲/ファイル、URL エンコード規則を `GitUrl` で実装、
単体テストで網羅。

## 設定（spec §7）

JSON（Jackson、追加依存ゼロ、ADR-0003）。repos/secrets/server、パス解決
（CLI＞env＞file＞default）、起動時検証（必須・id 一意・hosting enum・port 範囲・
秘密ファイル権限 600）、orphan secret 警告を `ConfigLoader` で実装、単体テストで確認。

## 非機能（要件 §5）

- 性能: `SearchCodePerfSmokeTest`（400 ファイル規模で 2 秒未満）。本番規模の
  p95 保証は spec §8.3 前提下。
- 可用性: 起動時 `refreshStatesFromDisk` で同期前に検索提供。per-repo 障害分離
  （`GitSyncTest`）。
- セキュリティ: pathsafe 封じ込め、未設定リポジトリ拒否、シェル非経由
  （`ProcessBuilder`）、秘密の非ログ（`Redaction`）。
- 運用性: 構造化ログ、`list_repositories`/Resource status、DEBUG 時の
  ツール呼び出しトレース。

## 既知の制約・未達

- **JSON-RPC 全文 wire tee（要件 §5.6 の一部）**: SDK 2.0.0 がトランスポート層の
  傍受点を公開しないため、DEBUG トレースは tools/call 境界に限定。詳細と根拠は
  `docs/devel/operation/operations.md`「既知の制約」。
- **stdio 端点間**: 構築レベル（`StdioServerBuildTest`）＋ 共有スタックの HTTP
  端点間検証でカバー。サブプロセス stdio e2e は surefire のクラスパス制約で
  恒常テスト化を見送り、手動 probe で確認する方針。
- ライブラリーのバージョンアップ追随（mcp SDK 2.0.0 / Spring Boot 4.1.0 /
  Jetty 12）は今後のメンテナンス対象。

## バックログ（M1 範囲外）

`docs/devel/work/tasks/task.md` の Backlog 参照（リクエスト時 staleness 非ブロッキング
更新、コンテナー配布、probe クライアント）。
