package me.xidentified.archgpt.utils;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

public class TranslationService {
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final String libreTranslateAPIEndpoint;
    private final Logger logger;

    public TranslationService(String libreTranslateAPIEndpoint, Logger logger) {
        this.libreTranslateAPIEndpoint = libreTranslateAPIEndpoint;
        this.logger = logger;
    }

    public CompletableFuture<String> translateText(String text, String targetLang) {
        return CompletableFuture.supplyAsync(() -> {
            String fullEndpoint = libreTranslateAPIEndpoint + "/translate";

            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("q", text);
            requestBody.addProperty("source", "en");
            requestBody.addProperty("target", targetLang);

            String jsonRequestBody = requestBody.toString();
            logger.info("Sending translation request: " + jsonRequestBody + " to " + fullEndpoint);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(fullEndpoint))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonRequestBody, StandardCharsets.UTF_8))
                    .build();

            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                logger.info("Translation API Response: " + response.body());
                JsonObject responseObject = JsonParser.parseString(response.body()).getAsJsonObject();

                if (responseObject.has("translatedText")) {
                    return responseObject.get("translatedText").getAsString();
                } else {
                    logger.warning("Translation API Response missing 'translatedText': " + response.body());
                    return null;
                }
            } catch (Exception e) {
                logger.severe("Translation Service Error: " + e.getMessage());
                return null;
            }
        });
    }

}