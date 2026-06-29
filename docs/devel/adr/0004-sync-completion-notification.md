# ADR-0004: 同期完了通知に PID ファイル ＋ SIGHUP を採用する

## ステータス

Accepted（2026-06-29）

## 背景

外部の `codesearch-sync` 実行（cron / systemd timer 等）が完了したとき、常駐
している `serve` プロセスへ「ワークスペースが更新された」ことを再起動なしで
伝えたい（要件 §5.2、リファレンス実装の `SIGHUP` 機構）。JVM には標準の
シグナル送受信 API が無いため、実現方式を決める。

## 検討した選択肢

| 選択肢 | 仕組み | 障害分離 | 移植性 | 結論 |
| --- | --- | --- | --- | --- |
| A. PID ファイル ＋ OS `SIGHUP` | serve が PID を書き、sync が `kill -HUP`、serve が `sun.misc.Signal` で受信 | プロセス分離維持 | Linux/macOS で動作（対象環境） | **採用** |
| B. ファイル監視（WatchService） | serve がワークスペースの変更を監視 | 維持 | OS非依存 | 不採用（監視対象が広く誤検知・実装複雑） |
| C. 通知しない（次回スケジュールで反映） | — | — | — | 不採用（即時反映できない） |

## 決定

**A. PID ファイル ＋ SIGHUP を採用する。**

- `serve` は起動時に PID ファイルを書き、`sun.misc.Signal` で `HUP` ハンドラーを
  登録する。ハンドラーは `RepositoryManager#refreshStatesFromDisk` を呼び、
  ディスク上のワークスペースから状態を再読込する（検索は常にローカルを読むため
  これで十分）。
- `codesearch-sync` は全リポジトリを 1 回同期したのち、PID ファイルを読み、
  `kill -HUP <pid>` をシェル非経由（`ProcessBuilder`）で送る。プロセスが無ければ
  ベストエフォートで無視する。
- シグナル受信は標準外 API（`sun.misc.Signal`、`jdk.unsupported` モジュール）に
  依存するため、**standalone モジュール限定**で使用する。`core` には持ち込まない
  （層分離維持、ADR-0001）。

## 帰結

- 対象環境（Linux/macOS）でのみ動作する。Windows は対象外（要件 §6.1）。
- `sun.misc.Signal` への依存は将来の JDK で変更され得る。変更時は本 ADR を
  再評価し、`java.lang.ProcessHandle` ベースの代替やファイル監視へ切り替える。
- PID ファイルの配置・ライフサイクル（起動時作成・終了時削除）は standalone の
  CLI が管理する。
