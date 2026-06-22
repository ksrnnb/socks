# socks

Java で実装したシンプルな SOCKS5 プロキシサーバーです。学習用に、[RFC 1928](https://datatracker.ietf.org/doc/html/rfc1928) の最小限の機能を Java で実装しています。

## 必要環境

- Java 21 以上

## ビルドと実行

```sh
javac -d out src/*.java && java -cp out Main
```

起動すると `localhost:1080` で待ち受けます。

## 動作確認

`curl` の `--socks5` オプションでプロキシ経由のリクエストを送信できます。

```sh
# IPv4 / ドメイン名どちらも可
curl --socks5 localhost:1080 https://example.com

# プロキシ側でドメイン名を解決させる場合
curl --socks5-hostname localhost:1080 https://example.com
```

## 制限事項

- 対応コマンドは `CONNECT` のみ（`BIND` / `UDP ASSOCIATE` は非対応）
- 認証は「認証なし」のみ対応
- 接続先アドレスとして IPv6 を直接指定する形式（`ATYP=0x04`）の受信は未対応（ドメイン名解決の結果が IPv6 になる場合の応答のみ扱う）
- 学習・実験用の実装であり、本番環境での利用は想定していません

## ライセンス

[MIT License](LICENSE)
