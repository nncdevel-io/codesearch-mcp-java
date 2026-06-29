# Code Search MCP Server（Java版）— 仕様書

本書は Code Search MCP Server（Java版）の外部仕様を定義する。

要件書（`docs/devel/requirements/requirements.md`）で定めた「何を提供するか」
を受けて、クライアントから観察可能なインターフェイスを厳密に規定する。
内部の実現方法（モジュール構成、依存性注入、スケジューラー実装など）は設計書
の責務とし、本書では扱わない。

本実装は Python 版（[codesearch-mcp-python](https://github.com/nncdevel-io/codesearch-mcp-python)）
とは独立した別個のプロダクトである。本書の外部仕様は Python 版の仕様を
設計上の参照として作成し、同じ外部振る舞いを意図的に規定するが、両者の間に
実行時・設定・バージョンの結合や互換保証はない。各実装は独立して
バージョニングする。

## 1. 本書の位置付け

| 文書 | 視点 | 内容 |
| --- | --- | --- |
| 要件書 | 何を解決するか | スコープ、機能要件、非機能要件、外部依存の選定 |
| 仕様書（本書） | 何を提供するか | プロトコル準拠、ツール入出力、エラー、設定 |
| 設計書 | どう実現するか | モジュール、データフロー、内部構造 |

## 2. MCP プロトコル準拠

本サーバーは Model Context Protocol の公式仕様に準拠する。プロトコル固有の
事項（JSON-RPC 2.0 メッセージ構造、初期化シーケンス、capability negotiation
など）は公式仕様に委ね、本書ではサーバー固有のツール仕様を規定する。

サーバー情報は `serverInfo.name = "codesearch-mcp"`、`serverInfo.version` は
ビルド時のバージョンを広告する。

### 2.1 サポートするトランスポート

| トランスポート | 用途 |
| --- | --- |
| stdio | ローカル実行、デスクトップエージェント |
| Streamable HTTP | リモートデプロイ、Web チャットアプリケーション |

起動時のオプションでどちらを使うかを選択する。同一プロセスで両方を同時に
提供することはしない。

### 2.2 公開する capability

本サーバーは `tools` capability と `resources` capability を公開する。
`prompts` および `sampling` は公開しない。

### 2.3 公開するツール一覧

| ツール名 | 概要 |
| --- | --- |
| `search_code` | ファイル内容のパターン検索 |
| `list_files` | ファイル名パターンによるファイル列挙 |
| `list_tree` | ディレクトリ階層の俯瞰 |
| `read_file` | 指定範囲のファイル内容読み取り |
| `list_repositories` | 設定済みリポジトリのカタログを返す（Resources 非対応ホスト向け） |

## 3. 共通データ型

### 3.1 リポジトリ識別子

```json
{
  "type": "string",
  "pattern": "^[a-zA-Z0-9._-]+$",
  "minLength": 1,
  "maxLength": 64
}
```

### 3.2 相対パス

```json
{
  "type": "string",
  "minLength": 0,
  "maxLength": 4096,
  "description": "リポジトリルートからの相対パス。POSIX形式。先頭の / および .. の使用は禁止"
}
```

絶対パスの指定、および `..` を含むパスは `INVALID_PATH` エラーで拒否する。
空文字列はリポジトリルートを指す。

### 3.3 Git URL

Git ホスティングサービス上の該当箇所への URL。行番号アンカー付き。生成規則は
6章を参照。

## 4. ツール仕様

### 4.1 search_code

ファイル内容に対するパターン検索を行う。

#### 4.1.1 入力

| パラメーター | 型 | 必須 | 既定 | 制約・説明 |
| --- | --- | --- | --- | --- |
| `pattern` | string | 必須 | — | 検索パターン。Rust regex crate 構文（look-around 非対応）。1〜1024字 |
| `repository` | string | 必須 | — | リポジトリ識別子 |
| `path` | string | 任意 | ルート | リポジトリ内の相対パス |
| `glob` | string | 任意 | — | ファイル名グロブ（最大256字） |
| `type` | string | 任意 | — | ripgrep `--type` の言語種別 |
| `case_sensitive` | boolean | 任意 | `false` | 大文字小文字を区別するか |
| `output_mode` | enum | 任意 | `content` | `content` / `files_with_matches` / `count` |
| `context_before` | integer | 任意 | `0` | 一致行の前に含める行数（0〜20） |
| `context_after` | integer | 任意 | `0` | 一致行の後に含める行数（0〜20） |
| `max_results` | integer | 任意 | `50` | 返す最大ヒット数（1〜500） |

正規表現は ripgrep（Rust regex crate）が解釈する。バックエンドが Java 実装に
なっても、パターン方言は ripgrep のものを維持する。

#### 4.1.2 出力（content モード）

```json
{
  "type": "object",
  "properties": {
    "matches": {
      "type": "array",
      "items": {
        "type": "object",
        "properties": {
          "repository": { "type": "string" },
          "file_path": { "type": "string" },
          "line_number": { "type": "integer", "minimum": 1 },
          "line_content": { "type": "string" },
          "context_before": { "type": "array" },
          "context_after": { "type": "array" },
          "git_url": { "type": "string", "format": "uri" }
        },
        "required": ["repository", "file_path", "line_number", "line_content", "git_url"]
      }
    },
    "truncated": { "type": "boolean" },
    "total_matches": { "type": "integer" }
  },
  "required": ["matches", "truncated", "total_matches"]
}
```

#### 4.1.3 出力（files_with_matches モード）

`files` 配列（`repository` / `file_path` / `git_url`）と `truncated` を返す。

#### 4.1.4 出力（count モード）

`files` 配列（`repository` / `file_path` / `match_count` / `git_url`）と
`truncated` を返す。

#### 4.1.5 挙動

- `.gitignore` 記載ファイル・バイナリー・隠しファイルを検索対象から除外する
- `max_results` 超過時は結果を丸め、`truncated` を `true` にする

### 4.2 list_files

ファイル名パターンに一致するファイルを列挙する。

#### 4.2.1 入力

| パラメーター | 型 | 必須 | 既定 | 制約 |
| --- | --- | --- | --- | --- |
| `repository` | string | 必須 | — | リポジトリ識別子 |
| `pattern` | string | 必須 | — | グロブパターン（1〜256字） |
| `path` | string | 任意 | ルート | 検索起点の相対パス |
| `max_results` | integer | 任意 | `100` | 返す最大ファイル数（1〜500） |

#### 4.2.2 出力

`files` 配列（`repository` / `file_path` / `last_modified`（ISO 8601）/
`git_url`）と `truncated`。結果は `last_modified` の降順。

#### 4.2.3 挙動

- `.gitignore` 対象・バイナリーも結果に含める
- 上限到達時は `truncated` を `true` にする

### 4.3 list_tree

ディレクトリ階層を俯瞰するためのツリー表現を返す。

#### 4.3.1 入力

| パラメーター | 型 | 必須 | 既定 | 制約 |
| --- | --- | --- | --- | --- |
| `repository` | string | 必須 | — | リポジトリ識別子 |
| `path` | string | 任意 | ルート | 相対パス |
| `max_depth` | integer | 任意 | `2` | 最大深さ（1〜5） |
| `show_files` | boolean | 任意 | `true` | ファイルも含めるか |
| `max_entries` | integer | 任意 | `200` | 最大エントリー数（1〜1000） |

#### 4.3.2 出力

`repository` / `root_path` / `tree`（ASCII アート）/ `truncated` /
`entry_count` を返す。

#### 4.3.3 tree フィールドのフォーマット

```text
src/
├── main/
│   ├── java/
│   │   └── com/
│   │       └── example/
│   └── resources/
└── test/
    └── java/
```

- インデントは半角空白、罫線は Unicode 罫線素片（`├` `└` `│` `─`）
- ディレクトリ名は末尾に `/` を付与
- エントリーは名前順（ロケール非依存の Unicode コードポイント順）

#### 4.3.4 挙動

- Git で追跡されているファイルのみを含める（未追跡・`.gitignore` 対象は除外）
- `max_entries` 超過時は深さ優先でその時点までを返し `truncated` を `true` にする

### 4.4 read_file

指定したファイルの指定行範囲を読み取る。

#### 4.4.1 入力

| パラメーター | 型 | 必須 | 既定 | 制約 |
| --- | --- | --- | --- | --- |
| `repository` | string | 必須 | — | リポジトリ識別子 |
| `file_path` | string | 必須 | — | 相対パス |
| `start_line` | integer | 任意 | `1` | 開始行（1始まり） |
| `num_lines` | integer | 任意 | `100` | 読み取る行数（1〜2000） |

#### 4.4.2 出力

`repository` / `file_path` / `start_line` / `end_line` / `total_lines` /
`content` / `git_url` を返す。

#### 4.4.3 content フィールドのフォーマット

各行に「行番号 ＋ タブ文字（U+0009）＋ 内容」のプレフィックスを付与する。
行番号は右寄せ、幅は `end_line` の桁数に合わせる。

#### 4.4.4 ファイルサイズ制約

ファイル全体のサイズが 10 MB を超える場合は `FILE_TOO_LARGE` エラーを返す。
範囲を絞ってもファイル全体のサイズ判定が先行する。

#### 4.4.5 バイナリー判定

NUL バイトを含む、UTF-8 として無効など、テキストとして解釈できない場合は
`FILE_BINARY` エラーを返す。

### 4.5 list_repositories

設定済みリポジトリのカタログを返す。引数なし。出力は `repositories` 配列で、
各要素は `id` / `branch` / `hosting` / `hosting_base_url` / `exclude_paths` /
`refresh_interval_seconds` / `description` / `status` を持つ。

`status` は `repository` / `state`（`uninitialized` / `ready` / `failed`）/
`last_sync_at` / `last_outcome`（`success` / `failure` / `null`）/
`last_error` / `last_commit` を持つ。並び順は `repos.json` の記述順。

### 4.6 リポジトリ Resource

設定済みリポジトリは MCP の Resource として広告する。

- URI 規約: `codesearch://repo/{id}`（`{id}` は §3.1 のパターン）
- `resources/list`: `uri` / `name`（`Repository: {id}`）/ `title`（`{id}`）/
  `description` / `mimeType`（`application/json`）を返す
- `resources/read`: §4.5 の各リポジトリ要素と同じ JSON を 1 つの text content
  として返す
- `resources/list_changed` 通知は送出しない（カタログは起動時に固定）
- 未知 URI の `resources/read` は MCP プロトコル層の error 応答
  （メッセージ `"Unknown resource: <uri>"`）として返す。ツール側エラー形式
  （§5）は適用しない

## 5. エラー仕様

### 5.1 エラー応答形式

ドメインエラーは tool result の `isError: true` と `content` 配下のテキストで
表現する。テキストは以下の JSON 文字列とする。

```json
{
  "code": "REPO_NOT_FOUND",
  "message": "Repository 'main-app' is not configured",
  "details": { "repository": "main-app" }
}
```

### 5.2 エラーコード一覧

| コード | 意味 | 該当ツール | リカバリ可否 |
| --- | --- | --- | --- |
| `REPO_NOT_FOUND` | 指定リポジトリが設定にない | 全ツール | 不可 |
| `REPO_NOT_READY` | 初回 clone が未完了 | 全ツール | 可 |
| `INVALID_PATH` | パストラバーサルや絶対パスを検出 | path/file_path を取る全ツール | 不可 |
| `PATH_NOT_FOUND` | 指定パスがリポジトリ内に存在しない | path/file_path を取る全ツール | 不可 |
| `INVALID_PATTERN` | 検索/グロブパターンが不正 | search_code、list_files | 不可 |
| `FILE_TOO_LARGE` | ファイルサイズが上限超過 | read_file | 不可 |
| `FILE_BINARY` | テキストとして解釈できない | read_file | 不可 |
| `TIMEOUT` | ツール処理が制限時間を超過 | 全ツール | 可 |
| `BACKEND_FAILURE` | ripgrep または git の異常終了 | 全ツール | 可 |
| `INTERNAL_ERROR` | 上記以外のサーバー内部エラー | 全ツール | 可 |

### 5.3 エラーメッセージの規約

- `message` は英語、1 文、ピリオドなしで終わる
- `details` は JSON オブジェクトで、エラー固有の補助情報を含める
- 秘密情報（認証トークン、SSH 鍵のパスなど）を含めてはならない
- スタックトレースを含めてはならない

### 5.4 入力バリデーションエラー

JSON Schema 違反（型不一致、必須項目欠落、enum 範囲外、最小値・最大値違反
など）は MCP プロトコル層で `-32602 Invalid params` として返却される。ツール側
のエラーコード体系は適用しない。

## 6. Git URL 生成仕様

### 6.1 ホスティング別フォーマット（単一行）

| ホスティング | URL 形式 |
| --- | --- |
| GitHub | `{base}/blob/{branch}/{path}#L{line}` |
| GitLab | `{base}/-/blob/{branch}/{path}#L{line}` |
| Bitbucket | `{base}/src/{branch}/{path}#lines-{line}` |
| Gitea | `{base}/src/branch/{branch}/{path}#L{line}` |

### 6.2 範囲指定時のフォーマット

| ホスティング | 範囲 URL 形式 |
| --- | --- |
| GitHub | `{base}/blob/{branch}/{path}#L{start}-L{end}` |
| GitLab | `{base}/-/blob/{branch}/{path}#L{start}-{end}` |
| Bitbucket | `{base}/src/{branch}/{path}#lines-{start}:{end}` |
| Gitea | `{base}/src/branch/{branch}/{path}#L{start}-L{end}` |

### 6.3 パスの URL エンコード

- 区切り文字 `/` はエンコードしない
- 空白は `%20` にエンコードする
- 非 ASCII 文字は UTF-8 → percent-encoding でエンコードする
- 予約文字（`?` `#` `%`）はエンコードする

## 7. 設定ファイル仕様

設定形式は JSON とする（追加ライブラリー不要。既存依存の Jackson でパースする）。

### 7.1 repos.json

配置場所は CLI フラグ `--repos` ＞ 環境変数 `CODE_SEARCH_REPOS_PATH` ＞
auto-discovery `./config/repos.json` の順で解決する。候補が存在しない場合は
起動失敗（catalog は必須）。

```json
{
  "workspace_root": "/var/lib/codesearch/workspaces",
  "secrets": "/etc/codesearch/secrets.json",
  "repositories": [
    {
      "id": "main-app",
      "remote": "git@github.com:example/main-app.git",
      "branch": "main",
      "hosting": "github",
      "hosting_base_url": "https://github.com/example/main-app",
      "refresh_interval_seconds": 900,
      "exclude_paths": ["docs/generated/", "vendor/"],
      "description": "Backend service. Key dirs: billing/, auth/, models/."
    }
  ]
}
```

#### トップレベルフィールド

| フィールド | 必須 | 型 | 説明 |
| --- | --- | --- | --- |
| `workspace_root` | 任意 | string | clone 先。未指定なら `./workspaces` |
| `secrets` | 任意 | string | `secrets.json` のパス |
| `repositories` | 必須 | array of object | リポジトリ定義の配列 |

#### `repositories` 要素

| フィールド | 必須 | 型 | 制約 |
| --- | --- | --- | --- |
| `id` | 必須 | string | `^[a-zA-Z0-9._-]+$`、サーバー内一意 |
| `remote` | 必須 | string | Git のリモート URL（SSH または HTTPS） |
| `branch` | 必須 | string | 参照するブランチ名 |
| `hosting` | 必須 | enum | `github`/`gitlab`/`bitbucket`/`gitea` |
| `hosting_base_url` | 必須 | string | Git URL 生成の基底 URL |
| `refresh_interval_seconds` | 任意 | integer | 60 以上。省略時は 900 |
| `exclude_paths` | 任意 | array | 相対パスのプレフィックス |
| `description` | 任意 | string | 8192 字以内。リポの中身を表す自由文 |

### 7.2 secrets.json

認証情報を分離して格納する。パーミッションは 600 とする。配置場所は CLI
`--secrets` ＞ 環境変数 `CODE_SEARCH_SECRETS_PATH` ＞ `repos.json` の
`secrets` ＞ auto-discovery `./config/secrets.json` の順。

```json
{
  "secrets": {
    "main-app": { "auth_type": "token", "token": "ghp_xxxxxxxxxxxxxxxxxxxx" },
    "internal-lib": { "auth_type": "ssh_key", "ssh_key_path": "/etc/code-search/keys/internal-lib" }
  }
}
```

`secrets` は repository id をキーにしたオブジェクトで、各値は次を持つ。

| フィールド | 必須 | 型 | 説明 |
| --- | --- | --- | --- |
| `auth_type` | 必須 | enum | `token`/`ssh_key`/`none` |
| `token` | 条件 | string | `auth_type=token` の場合に必須 |
| `ssh_key_path` | 条件 | string | `auth_type=ssh_key` の場合に必須 |

`repositories` に対応しない orphan な `secrets.<id>` は警告
（`config_warning` ログイベント）を出して無視する。起動は中断しない。

### 7.3 server.json

`serve` の起動パラメーターを切り出す任意設定。配置場所は CLI `--config` ＞
環境変数 `CODE_SEARCH_CONFIG_PATH` ＞ auto-discovery `./config/server.json`
の順。どれもなければ組込みデフォルトを使う。

```json
{
  "transport": "stdio",
  "host": "127.0.0.1",
  "port": 8000,
  "enable_scheduler": false
}
```

| フィールド | 必須 | 型 | 既定値 | 説明 |
| --- | --- | --- | --- | --- |
| `transport` | 任意 | enum | `stdio` | `stdio` / `http` |
| `host` | 任意 | string | `127.0.0.1` | HTTP バインドアドレス |
| `port` | 任意 | integer | `8000` | HTTP バインドポート（1〜65535） |
| `enable_scheduler` | 任意 | boolean | `false` | in-process 定期同期スケジューラー |

CLI フラグは server.json の値より優先される。

### 7.4 起動時の検証

サーバー起動時に以下を検証し、違反があれば起動を中止する。

- JSON のパースに成功すること
- 必須フィールドが全て揃っていること
- `id` がサーバー内で一意であること
- `hosting` が enum の範囲内であること
- 秘密情報ファイルのパーミッションが 600 以下であること
- `server.json` の `port` が 1〜65535 の範囲内であること

orphan secret entry は警告に留め、起動は中断しない。

## 8. 性能保証

### 8.1 タイムアウト

| ツール | タイムアウト |
| --- | --- |
| `search_code` | 10 秒 |
| `list_files` | 5 秒 |
| `list_tree` | 5 秒 |
| `read_file` | 3 秒 |

### 8.2 同時実行数

同一サーバーインスタンスで同時に処理するツール呼び出しの上限は 16 件とする。
超過分は順次キューイングされる。キュー待機を含めた全体応答が 30 秒を超える
場合は `TIMEOUT` エラーを返す。

## 9. 互換性ポリシー

サーバーは Semantic Versioning 2.0.0 に従う。クライアントは未知の出力フィールド
を無視できなければならない。設定ファイルの必須フィールド追加は MAJOR を伴う。

## 10. 参考文献

- リファレンス実装の仕様書（Python版 `docs/spec/spec.md`）
- [Model Context Protocol Specification](https://spec.modelcontextprotocol.io/)
- [Semantic Versioning 2.0.0](https://semver.org/)
