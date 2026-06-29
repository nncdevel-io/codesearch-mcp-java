---
guideline: docs-guideline
guideline_version: "1.2"
---

# 開発ドキュメント

`docs/devel/`

開発内部文書（設計・開発・テスト・運用・保守に必要な文書）を配置する。

## 構成

恒久的に参照する文書:

- `requirements/` — 要求・制約・前提
- `specs/` — 振る舞い・I/F仕様
- `design/` — 現在有効な設計内容
- `adr/` — 設計判断・技術選定の記録
- `standards/` — 実装規約
- `testing/` — テスト方針
- `quality/` — 品質基準
- `operation/` — リリース・運用手順

特定の作業に紐づく文書:

- `work/` — タスク、計画、調査、レビュー、メモ、アーカイブ

各ディレクトリの `README.md` に置き場と境界を記載している。文書を作成する際は
必ず該当 README を参照する。

## 横断規約

### 命名

- ディレクトリ名・ファイル名は英語 kebab-case（例: `database-migration.md`）
- ADR は4桁ゼロパディングの連番（例: `0001-use-spring-boot.md`）
- task ファイルは `task.md`（着手中）/ `done/task.<milestone>.md`（完了・中止）
- plan ファイルはテーマ名 kebab-case（日付プレフィックス不要）

### 本文言語

プロジェクト方針による。1つのリポジトリ内では本文言語を統一する。

### 恒久文書と作業文書の分離

`work/` 配下で得た知見のうち、今後も参照すべき内容は該当する恒久文書に反映する。
反映先は `work/README.md` の対応表を参照。

`work/` 配下の文書は作業完了後に以下のいずれかを行う:

- 恒久文書へ反映する
- `work/archive/` へ移動する
- 不要なら削除する
