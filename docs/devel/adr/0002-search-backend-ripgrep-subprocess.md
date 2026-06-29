# ADR-0002: 検索バックエンドは ripgrep のサブプロセス実行を採用する

## ステータス

Accepted（2026-06-29）

## 背景

`search_code`／`list_files` の内容検索・ファイル列挙バックエンドを選定する。
当初は Python 版同様に `ripgrep` バイナリーのサブプロセス実行を前提として
いたが、次の代替案の検討依頼を受け、実際に調査した。

- in-JVM の高速文字列検索ライブラリー（StringSearch）
- ripgrep の Java 実装・移植・FFI バインディング

選定が満たすべき要件は次のとおり。

- `pattern` は **Rust regex crate 構文**に準拠する（`spec.md` §4.1.1）
- `.gitignore`／隠しファイル／バイナリーを除外する（`requirements.md` §4.1.3）
- 100万行／5GB 規模でも `search_code` は p95 2秒以内（同 §5.1）
- 個別リポジトリの失敗を他へ波及させない障害分離（同 §5.2）
- Python 版とのクライアント観察可能な振る舞いのパリティ（同 §5.7）

## 検討した選択肢（実調査に基づく）

| 選択肢 | 実装形態 | 正規表現方言 | gitignore/隠し/バイナリー除外 | 並列走査 | 障害分離 | 成熟度 | 結論 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| A. ripgrep サブプロセス | 外部バイナリー | Rust regex（仕様一致） | 完全 | 全コア | あり（別プロセス） | 本番実績 | **採用** |
| B. java-ripgrep（JNA FFI） | Rust ライブラリーを FFI 呼出 | Rust regex | rg 由来 | rg 由来 | なし（JVM 内） | デモ明記・非本番 | 不採用 |
| C. GrepTool（spring-ai-agent-utils） | 純 Java | Java 標準 regex（仕様と差異） | 記述なし | 記述なし | あり（同一プロセス内） | 不明（ライセンス/版不明） | 不採用 |
| D. StringSearch | 純 Java ＋ 一部 C++ | Boyer-Moore 系・ワイルドカード（非 regex） | なし | なし | — | — | 不採用（別問題） |

## 決定

**A. ripgrep バイナリーのサブプロセス実行を採用する。**

`ProcessBuilder`（シェル非経由）で `rg` を起動し、`--json` 出力を Jackson で
パースする。Python 版と同一バイナリーを用いることで、正規表現方言・ignore
意味論・性能・障害分離をすべて満たし、パリティを保証する。

### 理由

- **C（GrepTool）**: 純 Java で外部依存が無い点は魅力だが、(1) Java 標準
  regex は Rust regex crate と構文・意味論が異なり（look-around の扱い等）、
  既存パターンの互換が崩れる、(2) `.gitignore`／隠し／バイナリー除外と並列
  走査が文書化されておらず、要件 §4.1.3 を自前で実装する必要がある、
  (3) Java の正規表現はバックトラッキング型で SIMD リテラル最適化を持たず、
  5GB／100万行で p95 2秒の保証が不確実、(4) 成熟度・ライセンスが不明。
- **B（FFI）**: Rust regex／ignore／並列をそのまま活かせるが、(1) 公開されて
  いるのはデモであり非本番、(2) ripgrep はライブラリーとしての安定 ABI を
  提供していない（公式ディスカッション）、(3) FFI 境界のクラッシュは JVM
  ごと巻き込み、障害分離要件 §5.2 に反する、(4) プラットフォーム別ネイティブ
  配布が複雑化する。per-call のプロセス起動コストは無視できる水準であり
  （ADR-0001 の検討および性能分析）、FFI の利得が小さい。
- **D（StringSearch）**: in-memory の文字列マッチを速くするライブラリーで
  あり、並列走査・ignore・I/O・正規表現という支配的コストを解かない。本ツール
  群の要件には噛み合わない。
- **A**: 上記のいずれの差分も持たず、Python 版と同一バイナリーで全要件を満たす。

## 帰結

### メリット

- 要件（regex 方言・ignore 意味論・大規模性能・障害分離）を完全充足。
- Python 版とのパリティを保証。
- 配布が単純（`rg` バイナリー前提を devcontainer／コンテナーに同梱）。

### デメリット／受容したトレードオフ

- `rg` バイナリーの存在を前提とする（実行環境に同梱が必要）。
- per-call のサブプロセス起動コストを払う（無視できる水準）。
- `read_file` は引き続きプロセスを起動しない（純 Java のファイル入出力）。

## 当初判断の訂正

本 ADR の作成過程で「ripgrep 級の純 Java 実装は存在しない」とした当初の
断定は不正確だった。実調査の結果、純 Java の grep（GrepTool）と FFI デモ
（java-ripgrep）が存在する。ただしいずれも本要件を本番品質で満たさないため、
結論（ripgrep サブプロセス採用）は変わらない。

## レビュー条件（この決定を再評価する契機）

- Rust regex 方言・gitignore 意味論・大規模性能・JVM 隔離を満たす、本番品質の
  純 Java または FFI による ripgrep 等価実装が登場した。
- 性能スモーク（`task.md` の TASK-048）で、サブプロセス方式が要件性能を
  満たさないと判明した。

## 参考

- [BurntSushi/ripgrep](https://github.com/BurntSushi/ripgrep)
- [ripgrep をライブラリーとして他言語から使えるか（公式ディスカッション）](https://github.com/BurntSushi/ripgrep/discussions/2058)
- [brownian-motion/java-ripgrep（JNA FFI デモ）](https://github.com/brownian-motion/java-ripgrep)
- [spring-ai-agent-utils GrepTool（純 Java grep）](https://github.com/spring-ai-community/spring-ai-agent-utils/blob/main/spring-ai-agent-utils/docs/GrepTool.md)
- リファレンス実装 要件書 §1.3／§6.2（ripgrep 選定の根拠）
