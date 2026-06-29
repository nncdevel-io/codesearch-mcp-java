# 計画

`docs/devel/work/plans/`

**複数作業を束ねる計画・対応方針**を管理する。要件の塊を「どの順・どのフェーズで
進めるか」に並べる段であり、大規模開発や初期構築で用いる。小規模で工程分割が
不要なら省略してよい。

## 配置するもの

- 移行計画
- 改善計画
- リファクタリング計画
- リリース前作業計画
- 段階的対応方針

## backlog / tasks との関係

要件を起点に `plans`（順序・フェーズ）→ `backlog`（機能へ分解）→ `tasks`（実行）
と流れる。`plans` は順序づけの段で、機能の中身は `backlog`、実行手順は `tasks`
に書く（同じ内容を重複させない）。

- `plans/` = **どの順・どのフェーズで進めるか**（順序づけ）
- `backlog/` = **要件を機能＝レビュー単位に分解**（機能の中身）
- `tasks/` = **次に何をするか、どこを触るか**（実行）

1つの plan から複数の task が生まれる関係。要件が明確なら `backlog` を飛ばして
`plans` から直接 task に落としてよい。

例:

`migrate-to-typescript.md`:

> 3ヶ月かけて TypeScript への段階的移行。
> フェーズ1: 型定義整備 / フェーズ2: コアモジュール変換 / フェーズ3: strict化

`tasks/task.md`:

> tsconfig.json を以下の内容で追加、build スクリプトと CI を更新

plan ↔ task の参照は Front Matter `related` に双方向で記載する。

## ファイル命名

テーマを表す kebab-case（**日付プレフィックスは付けない**）:

```text
migrate-to-typescript.md
refactor-payment-module.md
v2-release-plan.md
```

plan は中長期で参照されるため、内容を表す名前のほうが識別しやすい。
