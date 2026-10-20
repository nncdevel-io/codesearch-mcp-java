---
title: 依存ライブラリ・ビルドツールの更新と Java 21/25 での CI 検証
priority: medium
created: 2026-10-20
started: 2026-10-20
owner: t-izuno
---

マイルストーン: 依存ライブラリ・ビルドツールの更新
ゴール: Java のターゲットを 21（下限）に保ったまま依存ライブラリ・Maven プラグイン・Maven 本体を更新し、CI で Java 21 と 25 の両方で検証する

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
| TASK-001 | ✅ | 依存ライブラリ・プラグインの最新版を調査する | - |
| TASK-002 | ✅ | Spring Boot・MCP SDK・picocli を更新し、共有ライブラリを Spring Boot 管理版に揃える | TASK-001 |
| TASK-003 | ✅ | Maven プラグインと Maven Wrapper（Maven 本体）を更新する | TASK-001 |
| TASK-004 | ✅ | Spotless 3.x の整形規則変更に合わせてソースを再整形する | TASK-003 |
| TASK-005 | ✅ | install と dependency:analyze-only で検証する | TASK-002,TASK-004 |
| TASK-006 | ✅ | CI を Java 21/25 の matrix にし、README の前提を更新する | TASK-005 |
| TASK-007 | ✅ | ADR-0005 の検証済み Jetty バージョンを更新する | TASK-005 |

## タスク詳細（補足が必要な場合のみ）

### TASK-002

- 補足: Jackson 2.21.5、Jetty 12.1.12、JUnit 6.0.3、Logback 1.5.38、SLF4J 2.0.18 は Spring Boot 4.1.1 の管理版に合わせた
- 注意: starter を組み込む側の実行時の版と揃えるため、各ライブラリの最新版ではなく管理版を採用する

### TASK-006

- 補足: Java のターゲット（`maven.compiler.release`）は 21 のまま。17 は Virtual Threads を使っているため対象外
- 注意: 25 へ上げると starter・core を組み込む側にも Java 25 が必要になるため上げない

## Backlog一覧

| ID | Status | Summary | DependsOn |
| --- | --- | --- | --- |
| BACKLOG-001 | ⏳ | リクエスト時のstaleness判定で非ブロッキング更新を実装する | - |
| BACKLOG-002 | ⏳ | DockerfileでコンテナーDistributionを整備する | - |
| BACKLOG-003 | ⏳ | 動作確認用の参照MCPクライアント(probe)を追加する | - |
| BACKLOG-004 | ⏳ | dependency:analyze の既存警告（未宣言の使用・未使用の宣言）を解消する | - |
| BACKLOG-005 | ⏳ | MCP Java SDK 2.1.0 でトランスポート層の傍受フックの有無を確認し operations.md を更新する | - |
| BACKLOG-006 | ⏳ | MCP 仕様 2026-07-28 対応の SDK リリース後に追随し、README の未対応の注記を更新する | - |

## Backlog詳細（補足が必要な場合のみ）

### BACKLOG-001

- 補足: 設計上の任意機能。定期更新はsyncとスケジューラーで充足済み
- 注意: 応答をブロックせず応答後にバックグラウンド更新する

### BACKLOG-002

- 補足: 主=Container配布。git+ripgrep同梱、非rootユーザーで起動

### BACKLOG-004

- 補足: 今回の更新前から同じ警告が出ている（更新による増加なし）

### BACKLOG-005

- 補足: operations.md の「既知の制約」は SDK 2.0.0 時点の記述

### BACKLOG-006

- 補足: SDK の main には PR #1170（2026-07-28 対応のステートレスサーバー）がマージ済み。2.1.0 には含まれない
- 注意: 自前実装や未リリース版への依存はしない方針（SDK のリリースを待つ）
