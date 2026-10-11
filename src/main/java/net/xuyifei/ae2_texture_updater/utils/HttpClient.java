package net.xuyifei.ae2_texture_updater.utils;

import net.xuyifei.ae2_texture_updater.AE2TextureUpdater;
import net.xuyifei.ae2_texture_updater.config.ModConfig;
import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import org.jetbrains.annotations.NotNull;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.concurrent.TimeUnit;

/**
 * Lazily built shared {@link OkHttpClient}, configured once from
 * {@code download.*} config values (timeouts and optional proxy).
 */
public final class HttpClient {
    private static volatile OkHttpClient instance;

    private HttpClient() {
    }

    public static @NotNull OkHttpClient getInstance() {
        if (instance == null) {
            synchronized (HttpClient.class) {
                if (instance == null) {
                    instance = buildClient();
                }
            }
        }
        return instance;
    }

    private static OkHttpClient buildClient() {
        ModConfig.Download download = AE2TextureUpdater.CONFIG.download;
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(download.connect_timeout_seconds, TimeUnit.SECONDS)
                .readTimeout(download.download_timeout_seconds, TimeUnit.SECONDS)
                .writeTimeout(download.download_timeout_seconds, TimeUnit.SECONDS)
                .callTimeout(download.connect_timeout_seconds + download.download_timeout_seconds, TimeUnit.SECONDS);

        if (download.proxy.enabled) {
            applyProxy(builder, download.proxy);
        }
        return builder.build();
    }

    private static void applyProxy(OkHttpClient.Builder builder, ModConfig.Proxy proxy) {
        Proxy.Type type = proxy.proxyType == ModConfig.ProxyType.HTTP ? Proxy.Type.HTTP : Proxy.Type.SOCKS;
        builder.proxy(new Proxy(type, new InetSocketAddress(proxy.host, proxy.port)));

        if (proxy.useAuthorization) {
            builder.proxyAuthenticator((route, response) -> response.request().newBuilder()
                    .header("Authorization", Credentials.basic(proxy.username, proxy.password))
                    .build());
        }
    }
}
