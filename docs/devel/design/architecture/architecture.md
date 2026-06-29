# Code Search MCP Server（Java版）— 設計（アーキテクチャ）

本書は要件書（`docs/devel/requirements/requirements.md`）と仕様書
（`docs/devel/specs/spec.md`）を受けて、Java 実装の内部アーキテクチャを
定義する。クラスシグネチャーや細部のエッジ処理は各実装タスク（TDD）の
中で決める。

## 1. 確定した方針

| 論点 | 決定 | 根拠 |
| --- | --- | --- |
| 完成範囲 | 全機能を段階的に完成（P0〜P8） | 利用者選択。Python 版と完全パリティ |
| ターゲット | Java 21（LTS） | Virtual Threads で並行サブプロセス処理を素直に実装できる |
| ビルドツール | Maven マルチモジュール | 利用者選択。モジュール境界で層間依存契約を担保 |
| MCP フレームワーク | 公式 MCP Java SDK | 仕様策定元が保守し、stdio／Streamable HTTP を標準サポート。詳細は ADR-0001 |
| 層分離 | core はフレームワーク非依存。ランタイムは差し替え可能 | 単独利用と Spring Boot 統合の両立。詳細は ADR-0001 |
| 並行モデル | Virtual Threads ＋ Semaphore（上限16） | Python の asyncio ＋ セマフォの代替 |
| 外部コマンド | `ProcessBuilder`（シェル非経由） | インジェクション耐性、並列走査の利得 |
| 設定形式 | TOML を維持 | Python 版と設定互換 |
| Git 同期 | リクエストパスから完全分離 | 要件5.1／5.2と整合 |
| テスト | TDD（テスト先行）。単体＋結合 | 利用者選択 |

## 2. モジュール構成（Maven マルチモジュール）

親 POM（`packaging=pom`）の下に3モジュールを置く。`groupId` は
`io.nncdevel.codesearch`、ベースパッケージは `io.nncdevel.codesearch` とする。

```text
codesearch-mcp-java/                     親POM
├── codesearch-mcp-core/                 中核（フレームワーク非依存）
├── codesearch-mcp-standalone/           単独利用（CLI ＋ 組込みJetty ＋ stdio）
└── codesearch-mcp-spring-boot-starter/  Spring Boot 統合（自動構成）
```

### 2.1 依存方向

```text
standalone ──────┐
                 ├──▶ core ──▶ MCP Java SDK（core）／Jackson／tomlj
spring-boot- ────┘
  starter
```

- `core` は他のどのモジュールにも依存しない。Spring・サーブレット
  コンテナー・CLI ライブラリーを import してはならない。
- `standalone` と `spring-boot-starter` は `core` にのみ依存し、互いには
  依存しない。
- この依存方向そのものが層間契約であり、Python 版の import-linter 相当を
  Maven のモジュール境界で担保する。

### 2.2 core が公開する境界（ポート）

`core` は「サーバーの起動」や「トランスポートの選択」を行わない。代わりに
次を公開し、外側のランタイムがそれを各トランスポートへ接続する。

- **MCP ツール／リソースの specification**: MCP Java SDK のツール仕様
  （例: `SyncToolSpecification`）とリソース仕様のリストを生成する
  `CapabilityProvider`。
- **ライフサイクル部品**: `RepositoryManager`／`GitSync`／`SyncScheduler`
  など、起動・停止・同期を担う部品。
- **構成入力**: 解決済みの設定（`repos`／`secrets`／`server`）を受け取る
  ファクトリー。

これにより、`standalone` は `McpServer.sync(transportProvider)` に
specification を載せて起動し、`spring-boot-starter` は同じ specification を
Bean として Spring AI MCP に登録する。

## 3. core のパッケージ構成

| パッケージ | 責務 | Python 版の対応 |
| --- | --- | --- |
| `config` | `repos`／`secrets`／`server` の record モデル | `config/models.py` |
| `config` | 設定読込・起動時検証・パス解決 | `config/loader.py` |
| `repo` | リポジトリ登録・状態・ワークスペースパス・同期状況 | `repo/manager.py` |
| `repo` | clone／fetch／reset・認証・障害分離 | `repo/git_sync.py` |
| `repo` | 任意の定期同期ループ（ON／OFF） | `repo/scheduler.py` |
| `backend` | 非同期サブプロセス実行・タイムアウト・`BACKEND_FAILURE` 写像 | `backends/command.py` |
| `backend` | `rg` argv 構築と `--json` パース | `backends/ripgrep.py` |
| `backend` | `git ls-files` 実行 | `backends/git_ls.py` |
| `tool` | 5ツールの本体（search_code／list_files／list_tree／read_file／list_repositories） | `tools/*.py` |
| `tool.schema` | 入出力の record と JSON Schema | `tools/schemas.py` |
| `util` | パス安全（`..`・絶対パス拒否、realpath 封じ込め） | `pathsafe.py` |
| `util` | Git URL 生成（4ホスティング、単一／範囲、URL エンコード） | `giturl.py` |
| `error` | エラーコード、`{code,message,details}` 直列化、`isError` 結果 | `errors.py` |
| `mcp` | `CapabilityProvider`（ツール／リソース specification 生成）、実行ガード（セマフォ・タイムアウト）、JSON-RPC tee | `server.py` の core 相当 |
| `obs` | 構造化ログ・秘密情報秘匿・同期状況参照 | `logging.py`／`observability.py` |

## 4. ランタイム層

### 4.1 standalone（単独利用）

- `cli`: picocli による `serve --transport stdio|http` と `sync`
  サブコマンド、起動時検証、依存配線。
- `transport`: stdio は SDK の stdio トランスポート、HTTP は SDK の
  `HttpServletStreamableServerTransportProvider` を組込み Jetty に載せる。
- 同期完了通知（`SIGHUP` 相当）は ADR で確定した方式で実装する。

### 4.2 spring-boot-starter（Spring Boot 統合）

- `AutoConfiguration` が `core` の `CapabilityProvider` を呼び、ツール／
  リソース specification を Bean として公開する。
- 設定は Spring の `@ConfigurationProperties` で受けるか、`core` の TOML
  ローダーへ委譲するかを実装時に決める（既定は TOML ローダー委譲で設定
  互換性を保つ）。
- トランスポートは Spring AI MCP（webmvc／webflux）に委ねる。

## 5. データフロー（ツール呼び出し）

1. トランスポートが `tools/call` を受信し、SDK が inputSchema を検証する
   （違反は `-32602 Invalid params`）
2. 実行ガード: セマフォ取得（上限16。待機＋実行が30秒超で `TIMEOUT`）、
   ツール別タイムアウト開始（search 10秒／list_files 5秒／list_tree 5秒／
   read_file 3秒）
3. リポジトリ解決: 未設定は `REPO_NOT_FOUND`、未 clone は `REPO_NOT_READY`
4. パス検証: 絶対・`..` は `INVALID_PATH`、不在は `PATH_NOT_FOUND`
5. ツール処理（ripgrep／git ls-files／純 Java）
6. `git_url` 付与（単一行、または context 指定時は範囲アンカー）
7. 仕様 outputSchema で返却。ドメインエラーは `isError:true` ＋ JSON 文字列
   `{code,message,details}`

## 6. 非機能の実装方針

- **性能**: Virtual Threads で外部コマンドを並列実行し、`Future` ＋
  タイムアウトで打ち切る。同期はリクエストパス外。
- **可用性**: 起動時に同期完了を待たず既存ワークスペースで応答。個別
  リポジトリの取得失敗を分離。
- **信頼性**: 同期失敗はログし次回再試行。原因判別可能なエラーコードを返す。
- **セキュリティ**: realpath 封じ込め、未設定リポジトリ拒否、秘密情報
  非ログ、push 権限不要、`ProcessBuilder` でシェル非経由。
- **運用性**: 起動・同期・ツール呼び出しの構造化ログ、最終同期状況の参照、
  DEBUG 時の JSON-RPC 全文ログ。

## 7. テスト戦略（TDD）

- **単体**: pathsafe／giturl（4ホスティング × 単一・範囲・エンコード）／
  rg argv・JSON パース／list_tree 整形（深さ・件数・整列・罫線）／
  read_file 行番号・バイナリ・サイズ／config 検証／エラー直列化。
- **結合**: 一時 git リポジトリ（ローカル bare を「リモート」扱い）を
  fixture 化し、MCP Java SDK のインメモリ／同期クライアントで各ツールと
  同期（CLI 経路含む）を端点間検証する。`rg`／`git` は必須、無ければ明示
  スキップ。
- **Spring 統合**: スターターの自動構成が specification を正しく Bean 登録
  することを Spring の test slice で検証する。
- 各単位は red → green → refactor。

## 8. フェーズ分割（実行計画の骨格）

| フェーズ | 内容 |
| --- | --- |
| P0 | 親POM ＋ core 雛形（Maven／JUnit／lint／カバレッジ） ＋ errors・pathsafe・giturl（純粋関数、完全 TDD） |
| P1 | config モデル・読込・起動時検証 ＋ 設定例ファイル。TOML パーサー確定（ADR） |
| P2 | RepositoryManager ＋ GitSync（clone／fetch／reset／認証／障害分離）＋ readiness |
| P3 | CommandRunner（Virtual Threads）＋ ripgrep ラッパー |
| P4 | tools（read_file → search_code → list_files → list_tree、各 TDD） |
| P5 | CapabilityProvider 配線、5ツール ＋ Resources、実行ガード（並列・タイムアウト・キュー）、エラー形式、`-32602` 通過 |
| P6 | 同期トリガー整備（`sync` CLI ＋ 任意スケジューラー ＋ 同期完了通知）、構造化ログ、observability |
| P7 | standalone トランスポート（stdio ＋ Streamable HTTP）＋ 組込み Jetty 確定（ADR）、両端点間テスト |
| P8 | spring-boot-starter（自動構成）＋ 性能スモーク ＋ 要件／仕様チェックリスト自己レビュー |

詳細タスクは本設計を入力として plan-tasks スキルで展開する。

## 9. 関連 ADR

- `docs/devel/adr/0001-mcp-framework-and-layering.md`
  — MCP フレームワーク選定と層分離（core／standalone／spring-boot-starter）
- `docs/devel/adr/0002-search-backend-ripgrep-subprocess.md`
  — 検索バックエンドに ripgrep サブプロセスを採用（in-JVM／FFI 代替を不採用）
