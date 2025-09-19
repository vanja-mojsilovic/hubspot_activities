package com;


import io.github.cdimascio.dotenv.Dotenv;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import okhttp3.OkHttpClient;


public abstract class AbstractClass {
    protected static final String BASE_URL = "https://api.hubapi.com";
    protected static final OkHttpClient client = new OkHttpClient();


    protected String getAccessToken() {
        Dotenv dotenv = Dotenv.load();
        String token = dotenv.get("ACCESS_TOKEN");
        if (token == null || token.isEmpty()) {
            throw new IllegalStateException("ACCESS_TOKEN is missing. Set it in your .env file.");
        }
        return token;
    }

    protected String[] escapeCsv(String[] fields) {
        String[] escaped = new String[fields.length];
        for (int i = 0; i < fields.length; i++) {
            String field = fields[i];
            if (field.contains(",") || field.contains("\"")) {
                field = "\"" + field.replace("\"", "\"\"") + "\"";
            }
            escaped[i] = field;
        }
        return escaped;
    }

    protected String formatDate(String epochMillis) {
        try {
            long millis = Long.parseLong(epochMillis);
            Instant instant = Instant.ofEpochMilli(millis);
            return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    .withZone(ZoneId.systemDefault())
                    .format(instant);
        } catch (Exception e) {
            return "";
        }
    }

    public void shutdown() {
        client.connectionPool().evictAll();
        client.dispatcher().executorService().shutdown();
        System.exit(0);
    }
}
