package net.xuyifei.ae2_texture_updater.download;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.xuyifei.ae2_texture_updater.utils.HttpClient;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class UrlBuilder {
    public static final String USER_AGENT = "xuyife/AE2TextureUpdater/1.0 (xuyifeiDev@outlook.com)";

    public interface UrlCallback {
        void onSuccess(String downloadUrl);

        void onError(String errorMessage);
    }

    public static void getCurseForgeUrl(boolean useApiKey, String apiKey, String projectId,
                                        String fileId, String fileName, UrlCallback callback) {
        if (!useApiKey) {
            String url = buildCurseForgeCdnUrl(fileId, fileName);
            callback.onSuccess(url);
            return;
        }

        if (apiKey == null || apiKey.isEmpty()) {
            callback.onError("API Key is empty");
            return;
        }
        if (projectId == null || projectId.isEmpty()) {
            callback.onError("Project ID is empty");
            return;
        }

        OkHttpClient client = HttpClient.getInstance();

        Request request = new Request.Builder()
                .url("https://api.curseforge.com/v1/mods/" + projectId +
                        "/files?gameVersion=1.21.1&modLoaderType=6&index=0&pageSize=10")
                .addHeader("x-api-key", apiKey)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                callback.onError("Network error: " + e.getMessage());
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) {
                try (response) {
                    int code = response.code();

                    if (code == 401 || code == 403) {
                        callback.onError("Invalid API Key");
                        return;
                    }
                    if (code == 404) {
                        callback.onError("Project ID not found");
                        return;
                    }
                    if (code == 429) {
                        callback.onError("Rate limited, please try again later");
                        return;
                    }
                    if (!response.isSuccessful()) {
                        callback.onError("HTTP error: " + code + " - " + response.message());
                        return;
                    }

                    String body;
                    if (response.body() != null) {
                        body = response.body().string();
                    } else {
                        callback.onError("Empty response from API");
                        return;
                    }

                    if (body.isEmpty()) {
                        callback.onError("Empty response from API");
                        return;
                    }

                    JsonObject root = JsonParser.parseString(body).getAsJsonObject();
                    JsonArray dataArray = root.getAsJsonArray("data");

                    if (dataArray.isEmpty()) {
                        callback.onError("No mod files found");
                        return;
                    }

                    JsonObject latestFile = dataArray.get(0).getAsJsonObject();
                    int latestFileId = latestFile.get("id").getAsInt();
                    String latestFileName = latestFile.get("fileName").getAsString();

                    String downloadUrl = buildCurseForgeCdnUrl(String.valueOf(latestFileId), latestFileName);
                    callback.onSuccess(downloadUrl);
                } catch (Exception e) {
                    callback.onError("Parse error: " + e.getMessage());
                }
            }
        });
    }

    public static void getModrinthUrl(String projectId, UrlCallback callback) {
        if (projectId == null || projectId.isEmpty()) {
            callback.onError("Project ID is empty");
            return;
        }

        OkHttpClient client = HttpClient.getInstance();

        Request request = new Request.Builder()
                .url("https://api.modrinth.com/v2/project/" + projectId +
                        "/version")
                .header("User-Agent", USER_AGENT)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                callback.onError("Network error: " + e.getMessage());
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) {
                try (response) {
                    int code = response.code();

                    if (code == 404) {
                        callback.onError("Project ID not found");
                        return;
                    }
                    if (code == 429) {
                        callback.onError("Rate limited, please try again later");
                        return;
                    }
                    if (!response.isSuccessful()) {
                        callback.onError("HTTP error: " + code + " - " + response.message());
                        return;
                    }

                    String body;
                    if (response.body() != null) {
                        body = response.body().string();
                    } else {
                        callback.onError("Empty response from API");
                        return;
                    }

                    if (body.isEmpty()) {
                        callback.onError("Empty response from API");
                        return;
                    }

                    JsonArray versionsArray = JsonParser.parseString(body).getAsJsonArray();
                    for (JsonElement element : versionsArray) {
                        JsonObject versionObj = element.getAsJsonObject();

                        JsonArray supportedGameVersionArray = versionObj.get("game_versions").getAsJsonArray();
                        for (JsonElement supportedGameVersion : supportedGameVersionArray) {
                            if (supportedGameVersion.getAsString().equals("1.21.1")) {
                                JsonArray filesArray = versionObj.get("files").getAsJsonArray();
                                for (JsonElement file : filesArray) {
                                    JsonObject fileObj = file.getAsJsonObject();
                                    if (fileObj.get("filename").getAsString().endsWith(".jar")) {
                                        String downloadUrl = fileObj.get("url").getAsString();

                                        callback.onSuccess(downloadUrl);
                                        return;
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    callback.onError("Parse error: " + e.getMessage());
                }
            }
        });
    }

    private static String buildCurseForgeCdnUrl(String fileId, String fileName) {
        String prefix = fileId.substring(0, Math.min(4, fileId.length()));
        String suffix = fileId.length() > 4 ?
                fileId.substring(4, Math.min(7, fileId.length())) : "0";
        return String.format("https://mediafilez.forgecdn.net/files/%s/%s/%s",
                prefix, suffix, fileName);
    }
}