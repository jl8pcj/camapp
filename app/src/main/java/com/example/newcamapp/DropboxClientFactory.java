package com.example.newcamapp;

import android.content.Context;
import android.util.Log;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.http.OkHttp3Requestor;
import com.dropbox.core.oauth.DbxCredential;
import com.dropbox.core.v2.DbxClientV2;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Properties;
import okhttp3.OkHttpClient;

/**
 * Dropboxへの接続を管理するクラス。
 * 外部ファイル (assets/dropbox_config.properties) から設定を読み込みます。
 */
public class DropboxClientFactory {

    private static DbxClientV2 client;

    public static DbxClientV2 getClient(Context context) {
        if (client == null) {
            // 外部ファイルから設定を読み込み
            Properties props = loadConfig(context);

            String refreshToken = props.getProperty("refresh_token", "");
            String clientId = props.getProperty("client_id", "");
            String clientSecret = props.getProperty("client_secret", "");

            OkHttpClient okHttpClient = new OkHttpClient.Builder()
                    .retryOnConnectionFailure(true)
                    .build();

            DbxRequestConfig config = DbxRequestConfig.newBuilder("dropbox/cam-app")
                    .withHttpRequestor(new OkHttp3Requestor(okHttpClient))
                    .build();

            // トークンの自動更新設定
            DbxCredential credential = new DbxCredential(
                    "",            // access_token
                    0L,            // expiresAt
                    refreshToken,
                    clientId,
                    clientSecret
            );

            client = new DbxClientV2(config, credential);
        }
        return client;
    }

    // MainActivityなどからも利用できるように public に設定
    public static Properties loadConfig(Context context) {
        Properties props = new Properties();
        try {
            // assetsからpropertiesファイルをUTF-8で読み込む
            InputStream is = context.getAssets().open("dropbox_config.properties");
            props.load(new InputStreamReader(is, "UTF-8"));
        } catch (Exception e) {
            Log.e("DropboxFactory", "Config file not found or load error", e);
        }
        return props;
    }
} // ← このクラスを閉じるカッコが抜けていると、ご提示のエラーになります