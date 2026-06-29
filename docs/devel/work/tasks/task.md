# TASKS

マイルストーン: M1（Code Search MCP Server Java版の完全パリティ移植）
ゴール: Python版と同一の外部仕様を持つMCPサーバーを、core/standalone/spring-boot-starterの3モジュールで完成させ、単独利用とSpring Boot統合の両方で5ツール+Resources+Git同期+stdio/HTTPを提供する

## ワークフロールール

- タスク着手時にステータスを 🚧 に更新する
- タスク完了時にステータスを ✅ に更新する
- DependsOn のタスクがすべて ✅ でないタスクには着手しない

## ステータス表記ルール

| Status | 意味 |
| ---- | ----- |
| ⏳ | 未着手、TODO |
| 🚧 | 作業中、IN_PROGRESS |
| 🧪 | 確認待ち、REVIEW |
| ✅ | 完了、DONE |
| 🚫 | 中止、CANCELLED |

## タスク一覧

| ID | Status | Summary | DependsOn |
| --- | --- | --- | --- |
| TASK-001 | ✅ | 親POMと3モジュールのMavenマルチモジュール雛形を構築する | - |
| TASK-002 | ✅ | coreにJUnit5/Spotless/JaCoCoの検証環境を整備する | TASK-001 |
| TASK-003 | ✅ | 仕様書spec.mdを外部契約参照・JSON採用で作成する | - |
| TASK-004 | ✅ | ErrorCodeとToolError(エラーJSON生成)を実装する | TASK-002,TASK-003 |
| TASK-005 | ✅ | 相対パス検証(絶対/../拒否・正規化)を実装する | TASK-004 |
| TASK-006 | ✅ | ワークスペース封じ込めパス解決(realpath)を実装する | TASK-005 |
| TASK-007 | ✅ | 4ホスティングの単一行Git URL生成を実装する | TASK-004 |
| TASK-008 | ✅ | Git URLの行範囲アンカー生成とURLエンコードを実装する | TASK-007 |
| TASK-009 | ✅ | 設定形式(JSON/Jackson)を選定しADRに記録する | TASK-001 |
| TASK-010 | ✅ | repos/secrets/serverの設定record(モデル)を実装する | TASK-003,TASK-007 |
| TASK-011 | ✅ | 設定ローダーと起動時検証(権限600含む)を実装する | TASK-010,TASK-009,TASK-004 |
| TASK-012 | ✅ | repos/secrets/serverの設定例ファイルを作成する | TASK-011 |
| TASK-013 | ✅ | 結合テスト用bare gitリモートfixtureを用意する | TASK-002 |
| TASK-014 | ✅ | RepositoryManager(状態/パス/同期状況)を実装する | TASK-010,TASK-004 |
| TASK-015 | ✅ | git cloneによる初期取得と認証処理を実装する | TASK-014,TASK-013 |
| TASK-016 | ✅ | git fetch/reset更新とリポジトリ単位障害分離を実装する | TASK-015 |
| TASK-017 | ✅ | Virtual Threadsベースのシェル非経由コマンドランナーを実装する | TASK-004 |
| TASK-018 | ✅ | ripgrep検索/列挙のargv構築を実装する | TASK-002 |
| TASK-019 | ✅ | ripgrep --json出力のマッチ抽出(Jackson)を実装する | TASK-018 |
| TASK-020 | ✅ | git ls-files実行バックエンドを実装する | TASK-017 |
| TASK-021 | ✅ | ツール入出力record(スキーマ)とToolContextを実装する | TASK-010,TASK-003 |
| TASK-022 | ✅ | read_fileの行番号整形とバイナリ/サイズ判定を実装する | TASK-021 |
| TASK-023 | ✅ | read_fileツール本体を実装する | TASK-022,TASK-006,TASK-008,TASK-014 |
| TASK-024 | ✅ | search_codeツール本体を実装する | TASK-019,TASK-017,TASK-008,TASK-014 |
| TASK-025 | ✅ | list_filesツール本体を実装する | TASK-019,TASK-017,TASK-008,TASK-014 |
| TASK-026 | ✅ | list_treeのASCIIツリー整形を実装する | TASK-021 |
| TASK-027 | ✅ | list_treeツール本体(git ls-files連携)を実装する | TASK-026,TASK-020,TASK-014 |
| TASK-028 | ✅ | 検索系ツールにexclude_pathsフィルターを適用する | TASK-024,TASK-025,TASK-027 |
| TASK-029 | ⏳ | ツール実行ガード(並列セマフォ16/タイムアウト/エラー)を実装する | TASK-004 |
| TASK-030 | ⏳ | list_repositoriesツールを実装する | TASK-014,TASK-021 |
| TASK-031 | ⏳ | リポジトリResources(codesearch://repo/{id})を実装する | TASK-014,TASK-021 |
| TASK-032 | ⏳ | CapabilityProvider(5ツール+Resources仕様生成)を実装する | TASK-023,TASK-024,TASK-025,TASK-027,TASK-028,TASK-029,TASK-030,TASK-031 |
| TASK-033 | ⏳ | 構造化ログと秘密情報の秘匿を実装する | TASK-002 |
| TASK-034 | ⏳ | 最終同期状況の参照機能(observability)を実装する | TASK-014 |
| TASK-035 | ⏳ | 任意のin-process定期同期スケジューラーを実装する | TASK-016 |
| TASK-036 | ⏳ | 同期完了通知方式を選定しADRに記録する | TASK-001 |
| TASK-037 | ⏳ | 同期完了通知(serve再起動なし反映)を実装する | TASK-036,TASK-035 |
| TASK-038 | ⏳ | DEBUG時にJSON-RPCを全文出力するtee傍受を実装する | TASK-033,TASK-032 |
| TASK-039 | ⏳ | picocliによるserve/syncのCLIを実装する | TASK-032,TASK-011,TASK-035 |
| TASK-040 | ⏳ | stdioトランスポートの端点間テストを通す | TASK-039,TASK-013 |
| TASK-041 | ⏳ | 組込みサーブレットコンテナーを選定しADRに記録する | TASK-001 |
| TASK-042 | ⏳ | Streamable HTTP(組込みJetty)トランスポートを配線する | TASK-041,TASK-039,TASK-032 |
| TASK-043 | ⏳ | Streamable HTTPトランスポートの端点間テストを通す | TASK-042,TASK-013 |
| TASK-044 | ⏳ | standalone実行可能成果物(uber-jar)をビルドする | TASK-039 |
| TASK-045 | ⏳ | DEBUG時出力/INFO時非出力を端点間テストで検証する | TASK-038,TASK-040 |
| TASK-046 | ⏳ | spring-boot-starterの自動構成でcoreを登録する | TASK-032 |
| TASK-047 | ⏳ | spring-boot-starterの自動構成をtest sliceで検証する | TASK-046 |
| TASK-048 | ⏳ | 性能スモーク(規模前提下のsearch_code 2秒未満)を実施する | TASK-040,TASK-043 |
| TASK-049 | ⏳ | GitHub Actions CIでビルド/テスト/lintを強制する | TASK-002 |
| TASK-050 | ⏳ | READMEと導入/運用ドキュメントを整備する | TASK-044,TASK-046 |
| TASK-051 | ⏳ | 仕様適合チェックと要件カバレッジの自己レビューを実施する | TASK-045,TASK-047,TASK-048,TASK-050 |

## タスク詳細（補足が必要な場合のみ）

### TASK-003

- 補足: 入出力スキーマ・エラーコード・Git URL・設定はPython版spec.mdを踏襲
- 注意: 設定はJSON(Jackson)。設定互換は非目標。配置先は docs/devel/specs/spec.md

### TASK-011

- 補足: 検証はJSONパース・必須項目・id一意・hosting enum・秘密ファイル権限600
- 注意: 違反時は起動を中止する

### TASK-015

- 補足: 認証は token / ssh_key / none に対応する
- 注意: 参照ブランチのみの単一ブランチ取得とする

### TASK-016

- 注意: 個別リポジトリの取得失敗を他リポジトリへ波及させない

### TASK-022

- 注意: サイズ超過は FILE_TOO_LARGE、バイナリは FILE_BINARY を返す

### TASK-024

- 補足: 出力モードは content / files_with_matches / count
- 注意: max_results 超過時は truncated を立てる

### TASK-027

- 注意: git追跡ファイルのみ対象（未追跡・.gitignore対象は除外）

### TASK-028

- 補足: exclude_paths はリポジトリ設定のプレフィックス一致で除外

### TASK-029

- 補足: 全体30秒超過と各ツール別タイムアウトの両方を扱う

### TASK-035

- 補足: 既定は無効、設定で有効化する
- 注意: リクエスト処理をブロックしない

### TASK-036

- 補足: JVMは標準シグナルAPIを持たないため実現方式を比較し決定する

### TASK-046

- 補足: 形態はSpring Boot Starter(AutoConfiguration)
- 注意: coreはSpringに依存させない

## Backlog一覧

| ID | Status | Summary | DependsOn |
| --- | --- | --- | --- |
| BACKLOG-001 | ⏳ | リクエスト時のstaleness判定で非ブロッキング更新を実装する | - |
| BACKLOG-002 | ⏳ | DockerfileでコンテナーDistributionを整備する | - |
| BACKLOG-003 | ⏳ | 動作確認用の参照MCPクライアント(probe)を追加する | - |

## Backlog詳細（補足が必要な場合のみ）

### BACKLOG-001

- 補足: 設計上の任意機能。定期更新はsyncとスケジューラーで充足済み
- 注意: 応答をブロックせず応答後にバックグラウンド更新する

### BACKLOG-002

- 補足: 主=Container配布。git+ripgrep同梱、非rootユーザーで起動
