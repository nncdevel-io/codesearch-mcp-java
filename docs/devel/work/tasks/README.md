# タスク

`docs/devel/work/tasks/`

**実行が決まった**作業指示を管理する。

## ファイル命名とカレント判定

タスクのステータスはファイル名で表現する。ファイルを開かずにカレントタスクの
有無を判定できる。

| ファイル名 | 意味 |
| --- | --- |
| `task.md` | カレントタスク（現在着手中、最大1つ。`tasks/` 直下） |
| `done/task.<milestone>.md` | 完了または中止したタスク（`done/` に配置） |

`<milestone>` は完了日 `YYYYMMDD`、`YYYYMMDD-<短い説明>`、リリース名等を使用。

```text
task.md                    # 着手中（tasks/ 直下）
done/task.20260605.md      # 完了
done/task.20260601-oauth.md
done/task.v1-release.md
```

完了・中止したタスクは `done/` に置き、`tasks/` 直下は着手中タスクのみに保つ。
詳細は `done/README.md` を参照。

「現在実施中のタスク」は `task.md` の存在で判定:

```bash
test -f docs/devel/work/tasks/task.md && echo "active task exists"
```

## ステータス遷移

```text
新規作成    → task.md を作成
完了        → git mv task.md done/task.<milestone>.md
中止        → git mv task.md done/task.<milestone>.md
               （Front Matter で result: abandoned）
アーカイブ  → 古くなった done/task.<milestone>.md を ../../archive/ へ移動または削除
```

## Front Matter

```yaml
---
title: タスクの説明
priority: high | medium | low
created: YYYY-MM-DD
started: YYYY-MM-DD
completed: YYYY-MM-DD
result: completed | abandoned
owner: 担当者
related:
  - ../plans/<関連plan>.md
---
```

ステータスはファイル名で表現するため Front Matter には含めない。

## 複数同時着手

本ガイドラインは「1人の人間と複数のAIで1つのタスクに集中協調」を前提とし、
カレントタスクは最大1つ。

複数同時並行が必要な場合は `task.<short-name>.md` 形式（例: `task.oauth.md`、
`task.payment.md`）を採用してよい。採用する場合は本 README で命名ルールを
明示する。

## pending タスクの扱い

実施は決まっているが未着手のタスクは `tasks/` には置かない。`../backlog/` で
管理し、着手時に `task.md` を作成する。

## 隣接カテゴリとの境界

- まだ実施するか決まっていない → `../backlog/`
- 複数作業をフェーズ・順序に束ねる → `../plans/`

task は `../backlog/` の機能を分解して作るほか、要件が明確なら要件から直接作って
よい。要件から task までの流れは `../README.md` を参照。
