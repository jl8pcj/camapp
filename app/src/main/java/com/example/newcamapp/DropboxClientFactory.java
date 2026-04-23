package com.example.newcamapp;

import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.http.OkHttp3Requestor;
import com.dropbox.core.oauth.DbxCredential;
import com.dropbox.core.v2.DbxClientV2;

import okhttp3.OkHttpClient;

/**
 * Dropboxへの接続を管理するクラス。
 * SSLエラーや通信の切断を回避するため、OkHttpを使用し
 * 自動リフレッシュ設定を最適化した安定版です。
 */
public class DropboxClientFactory {

    private static final String REFRESH_TOKEN = "X8Ogs3jedLAAAAAAAAAAAdX899MydSQM8e_-5P5Z60lliQR4iJXRxl81YUmkc4oX";
    private static final String CLIENT_ID = "kwps3743vkv4xqp";
    private static final String CLIENT_SECRET = "znr5vz3i6myjixw";

    private static DbxClientV2 client;

    public static DbxClientV2 getClient() {
        if (client == null) {
            // 通信の安定性を高めるため、標準のJavaコネクタではなくOkHttp3を使用するように設定
            OkHttpClient okHttpClient = new OkHttpClient.Builder()
                    .retryOnConnectionFailure(true) // 失敗時に再試行
                    .build();

            DbxRequestConfig config = DbxRequestConfig.newBuilder("dropbox/cam-app")
                    .withHttpRequestor(new OkHttp3Requestor(okHttpClient))
                    .build();

            // トークンの自動更新（リフレッシュ）設定
            // accessTokenを空にし、expiresAtを0にすることで、初回通信時に必ず最新のトークンを取得します
            DbxCredential credential = new DbxCredential(
                    "",            // access_token
                    0L,            // expiresAt
                    REFRESH_TOKEN,
                    CLIENT_ID,
                    CLIENT_SECRET
            );

            client = new DbxClientV2(config, credential);
        }
        return client;
    }
}