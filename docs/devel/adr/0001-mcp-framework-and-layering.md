# ADR-0001: MCP フレームワーク選定と層分離

## ステータス

Accepted（2026-06-29）

## 背景

Git 管理ソースコード検索の MCP サーバーを Java で実装する。
リファレンス実装（Python 版）の外部仕様を踏襲しつつ、次の制約を満たす
必要がある。

- **MCP 仕様準拠**: Tools／Resources のスキーマ、エラーコード、
  トランスポートが MCP プロトコル仕様に追随し続ける。
- **2トランスポート両対応**: stdio と Streamable HTTP の両方をサポートする。
- **2系統の利用形態**: 単独利用（CLI で起動するスタンドアロン）と
  Spring Boot 統合（既存アプリへ組み込み）の両方を破綻なく提供する。
- **保守性**: MCP 仕様は現在もアクティブに更新されている。プロトコル
  追随コストを当方が負わない方が長期保守上有利。

## 決定1: MCP フレームワークは公式 MCP Java SDK を採用する

`io.modelcontextprotocol.sdk:mcp`（公式 Java SDK）を採用する。

### 検討した選択肢

| 選択肢 | MCP 仕様追随 | stdio／HTTP 両対応 | 追加依存 | 結論 |
| --- | --- | --- | --- | --- |
| A. 公式 MCP Java SDK（core モジュール） | SDK が追随 | コアに同梱 | 最小 | **採用** |
| B. Spring AI の MCP 抽象に全面依存 | SDK 経由で追随 | HTTP は得意、stdio も SDK 経由 | Spring 一式 | 不採用（core が Spring に縛られる） |
| C. 自前 JSON-RPC ＋ MCP 実装 | 当方が恒久的に追随 | 全て自前 | プロトコル一式 | 不採用（費用対効果なし） |

### 根拠

- 公式 Java SDK のコア `io.modelcontextprotocol.sdk:mcp` は、外部 Web
  フレームワークなしで **stdio／SSE／Streamable HTTP** のサーバー
  トランスポートを同梱する。Streamable HTTP はサーブレットベースの
  `HttpServletStreamableServerTransportProvider` として提供される。
- sync／async 両 API（`McpServer.sync()`／`McpServer.async()`）を備え、
  ツール／リソースは `addTool`／`addResource` で登録できる。
- 仕様策定元（MCP プロジェクト、Spring AI と協働）が保守するため、仕様
  追随コストを SDK 側に委譲できる。
- C（自前実装）はトランスポート3種対応・スキーマ整合・互換試験までを
  内製することになり、本プロジェクトのスコープから逸脱する。

これは Python 版 ADR-0001（公式 `mcp` Python SDK 採用）と同一の論理構造で
あり、信頼性・正確性・効率のいずれでも公式 SDK が優位である。

## 決定2: core はフレームワーク非依存とし、ランタイムを差し替え可能にする

実装を3モジュールに分割し、中核ロジック（`codesearch-mcp-core`）を
Spring・サーブレットコンテナー・CLI のいずれにも依存させない。

| モジュール | 役割 | 依存 |
| --- | --- | --- |
| `codesearch-mcp-core` | ツール・リソース・ドメインロジック。MCP の specification と部品を公開 | MCP Java SDK（core）／Jackson |
| `codesearch-mcp-standalone` | 単独利用。CLI ＋ 組込みサーブレットコンテナー ＋ stdio | core |
| `codesearch-mcp-spring-boot-starter` | Spring Boot 統合。自動構成で core を MCP サーバーへ登録 | core ＋ Spring Boot ＋ MCP Java SDK の Spring 連携 |

### 根拠

- 単独利用と Spring Boot 統合という2系統を両立させるには、トランスポート／
  ランタイムを境界の外へ追い出す ports-and-adapters 構成が必要。
- `core` はツール／リソースの specification とライフサイクル部品を返すだけで
  あり、サーバー起動やトランスポート選択をしない。
- `standalone` は specification を `McpServer.sync(...)` に載せて Jetty／
  stdio を起動する。`spring-boot-starter` は同じ specification を Bean
  登録して Spring AI MCP に載せる。
- Maven のモジュール境界がそのまま層間依存契約となり、`core` への Spring／
  Jetty／CLI の混入を構造的に防ぐ（Python 版の import-linter 相当）。
- 選択肢 B（Spring AI へ全面依存）は HTTP には便利だが、`core` を Spring に
  縛り、単独利用を軽量に保てないため不採用。

### Spring 統合の形態

Spring 統合は **Spring Boot Starter（自動構成つき）** とする。依存を追加する
だけで `core` のツール／リソースが MCP サーバーへ自動登録され、利用者体験が
最も良い。

## 帰結

### メリット

- MCP プロトコル仕様への自動追随。
- stdio／Streamable HTTP 両トランスポートが SDK 標準でサポート。
- 単独利用は軽量（Spring 非依存）、Spring Boot 統合は自動構成で容易。
- 検証・テストの焦点を、MCP プロトコル層ではなく本プロジェクト固有の
  ロジック（検索バックエンド、パス安全性、リポジトリ管理）に集中できる。

### デメリット／受容したトレードオフ

- モジュールが3つに増え、ビルド構成と依存管理がやや複雑になる。これは
  2系統の利用形態を両立させるための必要コストとして受容する。
- `core` の境界設計（specification 生成 API）に追加の設計工数がかかる。

## 未決事項（後続 ADR で確定する）

- **組込みサーブレットコンテナーの選定**（単独利用の HTTP）: Jetty を
  第一候補とするが、`HttpServletStreamableServerTransportProvider` の
  サーブレット API 名前空間（jakarta）とバージョン整合を一次情報で確認の
  うえ確定する。
- **同期完了通知方式**（`SIGHUP` 相当）: JVM に標準のシグナル送受信 API が
  無いため、非標準シグナル API の利用／OS シグナル受信／ファイル監視への
  置換のいずれかを別 ADR で判断する。

## 参考

- [公式 MCP Java SDK（GitHub）](https://github.com/modelcontextprotocol/java-sdk)
- [MCP Java SDK — Server](https://java.sdk.modelcontextprotocol.io/latest/server/)
- [Maven: io.modelcontextprotocol.sdk:mcp](https://mvnrepository.com/artifact/io.modelcontextprotocol.sdk/mcp)
- [Model Context Protocol Specification](https://spec.modelcontextprotocol.io/)
- リファレンス実装 ADR-0001（Python 版 MCP フレームワーク選定）
