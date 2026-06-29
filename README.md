# codesearch-mcp-java

Git 管理されたソースコードを **チャット AI から直接 grep する** ための
Model Context Protocol（MCP）サーバーの Java 実装。`ripgrep` と
`git ls-files` をバックエンドに、ベクトル検索なしで高速・正確・引用可能な
コード検索を提供する。

本実装は Python 版
（[codesearch-mcp-python](https://github.com/nncdevel-io/codesearch-mcp-python)）
**とは独立した別個のプロダクト**であり、同じ外部仕様を意図的に共有するが、
両者の間に実行時・設定・バージョンの結合や互換保証はない。

## 特徴

- 埋め込み・ベクトル DB なし（`ripgrep` ＋ `git ls-files`）
- 結果は Git URL（行アンカー付き）で即引用できる（GitHub/GitLab/Bitbucket/Gitea）
- per-repo 分離 — 1 リポジトリの取得失敗が他に波及しない
- 同期はリクエストパスから分離（`sync` CLI ＋ 任意スケジューラー）
- stdio と Streamable HTTP の両トランスポート
- **2 つの利用形態**: 単独利用（CLI）と Spring Boot 統合（スターター）

## 公開ツール（5）＋ Resource

| ツール | 何ができる |
| --- | --- |
| `search_code` | ファイル内容の正規表現検索（output_mode: content/files_with_matches/count） |
| `list_files` | グロブによるファイル名検索（最終更新降順） |
| `list_tree` | ディレクトリ階層の俯瞰（ASCII ツリー） |
| `read_file` | 行範囲指定でのファイル読み取り |
| `list_repositories` | 設定済みリポジトリのカタログ（discovery） |

設定済みリポジトリは MCP の Resource（`codesearch://repo/{id}`）としても広告される。
完全な入出力スキーマは [`docs/devel/specs/spec.md`](docs/devel/specs/spec.md) を参照。

## モジュール構成

| モジュール | 役割 |
| --- | --- |
| `codesearch-mcp-core` | フレームワーク非依存の中核（ツール・リソース・ドメインロジック） |
| `codesearch-mcp-standalone` | 単独利用。CLI ＋ 組込み Jetty ＋ stdio |
| `codesearch-mcp-spring-boot-starter` | Spring Boot 統合（自動構成） |

## 前提

- Java 21（LTS）
- `git` と `ripgrep`（`rg`）が PATH 上に存在すること
- Linux または macOS

## 単独利用（standalone）

### 1. ビルド

```bash
./mvnw -pl standalone -am package
# 実行可能 jar: standalone/target/codesearch-mcp-standalone-<version>.jar
```

### 2. 設定（JSON）

`config/repos.json`（必須）を置く。例は
[`config/repos.json.example`](config/repos.json.example) を参照。認証情報は
`config/secrets.json`（パーミッション 600）に分離する。

### 3. 起動

```bash
# Streamable HTTP（チーム/常駐運用）
java -jar standalone/target/codesearch-mcp-standalone-*.jar \
    serve --transport http --host 127.0.0.1 --port 8000 --repos config/repos.json

# stdio（個人開発でクライアント直結）
java -jar standalone/target/codesearch-mcp-standalone-*.jar \
    serve --transport stdio --repos config/repos.json
```

CLI フラグは `config/server.json` ＞ 環境変数 ＞ 組込みデフォルトより優先される。
クライアント側のエンドポイントは `http://<host>:<port>/mcp`。

> HTTP で外部公開する場合、本サーバー自体に認証機構は無い。リバースプロキシや
> 内部ネットワーク制限で経路を絞ること（[`docs/devel/operation`](docs/devel/operation)）。

### MCP クライアント設定（Claude Code 例）

```bash
claude mcp add --transport http codesearch http://127.0.0.1:8000/mcp
```

### 定期同期

```bash
# 全リポジトリを 1 回同期し、稼働中の serve へ SIGHUP で再読込を通知する
java -jar standalone/target/codesearch-mcp-standalone-*.jar sync --repos config/repos.json
```

cron / systemd timer から `sync` を起動すれば、再起動なしで状態が反映される。
常駐スケジューラーを使う場合は `serve --enable-scheduler` を指定する。

## Spring Boot 統合

依存を追加し、`codesearch.mcp.repos-path` を設定すると、自動構成が MCP サーバーを
サーブレットコンテナーへ登録する。

```xml
<dependency>
  <groupId>io.nncdevel.codesearch</groupId>
  <artifactId>codesearch-mcp-spring-boot-starter</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

```yaml
codesearch:
  mcp:
    repos-path: config/repos.json
    endpoint: /mcp        # 任意（既定 /mcp）
```

## 開発・検証

```bash
./mvnw verify   # テスト ＋ Spotless ＋ Javadoc doclint（全モジュール）
```

## ドキュメント

- [`docs/devel/requirements/requirements.md`](docs/devel/requirements/requirements.md) — 要件
- [`docs/devel/specs/spec.md`](docs/devel/specs/spec.md) — 外部仕様
- [`docs/devel/design/architecture/architecture.md`](docs/devel/design/architecture/architecture.md) — 設計
- [`docs/devel/adr`](docs/devel/adr) — 設計判断の記録
- [`docs/devel/operation`](docs/devel/operation) — 運用

## ライセンス

MIT
