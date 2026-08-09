package net.xuyifei.ae2_texture_updater.utils;

import net.xuyifei.ae2_texture_updater.AE2TextureUpdater;
import net.xuyifei.ae2_texture_updater.config.ModConfig;
import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import org.jetbrains.annotations.NotNull;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.concurrent.TimeUnit;

public class HttpClient {
    private static volatile OkHttpClient instance;

    public static @NotNull OkHttpClient getInstance() {
        if (instance == null) {
            synchronized (HttpClient.class) {
                if (instance == null) {
                     OkHttpClient temp1 = new OkHttpClient.Builder()
                            .connectTimeout(AE2TextureUpdater.CONFIG.download.connect_timeout_seconds, TimeUnit.SECONDS)
                            .readTimeout(AE2TextureUpdater.CONFIG.download.download_timeout_seconds, TimeUnit.SECONDS)
                            .writeTimeout(AE2TextureUpdater.CONFIG.download.download_timeout_seconds, TimeUnit.SECONDS)
                            .callTimeout(AE2TextureUpdater.CONFIG.download.connect_timeout_seconds + AE2TextureUpdater.CONFIG.download.download_timeout_seconds, TimeUnit.SECONDS)
                            .build();

                    if (AE2TextureUpdater.CONFIG.download.proxy.enabled) {
                        OkHttpClient temp2 = temp1
                                .newBuilder()
                                .proxy(new Proxy(AE2TextureUpdater.CONFIG.download.proxy.proxyType == ModConfig.ProxyType.HTTP ? Proxy.Type.HTTP : Proxy.Type.SOCKS,
                                        new InetSocketAddress(AE2TextureUpdater.CONFIG.download.proxy.host, AE2TextureUpdater.CONFIG.download.proxy.port)))
                                .build();

                        if (AE2TextureUpdater.CONFIG.download.proxy.useAuthorization) {
                            instance = temp2
                                    .newBuilder()
                                    .proxyAuthenticator((route, response) -> {
                                          String credential = Credentials.basic(AE2TextureUpdater.CONFIG.download.proxy.username, AE2TextureUpdater.CONFIG.download.proxy.password);
                                          return response.request().newBuilder().header("Authorization", credential).build();
                                    })
                                    .build();
                        } else {
                            instance = temp2;
                        }
                    } else {
                        instance = temp1;
                    }
                }
            }
        }
        return instance;
    }
}
