# 完了・中止タスク

`docs/devel/work/tasks/done/`

完了・中止したタスク（`task.<milestone>.md`）を保管する。

カレントタスク `../task.md` と分離し、`tasks/` 直下を着手中タスクだけに保つ。
これにより着手中タスクの有無が `../task.md` の存在だけで判定できる。

## 配置するもの

- 完了したタスク（Front Matter `result: completed`）
- 中止したタスク（Front Matter `result: abandoned`）

`<milestone>` は完了日 `YYYYMMDD`、`YYYYMMDD-<短い説明>`、リリース名等を使用。

```text
task.20260605.md
task.20260601-oauth.md
task.v1-release.md
```

## `archive/` との違い

- `done/` — 最近完了・中止したタスク。振り返りやレビューで参照する
- `../../archive/` — 古くなった・参照頻度が下がった文書

古くなった `task.<milestone>.md` は `../../archive/` へ移動するか削除する。
