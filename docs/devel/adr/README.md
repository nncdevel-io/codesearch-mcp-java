# アーキテクチャ決定記録（ADR）

`docs/devel/adr/`

重要な設計判断・技術選定・方針決定を記録する。

ADR は設計文書ではなく**設計判断の履歴**である。`design/` 配下にネストさせず、
横断的な判断履歴として本ディレクトリに集約する。

## ファイル命名

4桁ゼロパディングの連番:

```text
0001-use-spring-boot.md
0002-database-migration-policy.md
0003-adopt-openapi.md
```

## 各 ADR に記載する内容

- 背景
- 判断内容
- 採用理由
- 代替案
- 影響
- ステータス（Proposed / Accepted / Superseded / Deprecated）
- 関連文書

## 隣接カテゴリとの境界

- 現在有効な設計内容 → `../design/<分類>/`
- 実装規約 → `../standards/`

## 相互参照

ADR には関連設計文書を記載する:

```md
## Related Documents

- ../design/database/migration.md
- ../testing/database-migration.md
```
