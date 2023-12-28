package me.xidentified.archgpt.utils;

import java.io.IOException;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

public class TranslationService {

    private final String translationAPIEndpoint; // Endpoint of the translation API
    private final String apiKey; // API key for the translation service

    public TranslationService(String translationAPIEndpoint, String apiKey) {
        this.translationAPIEndpoint = translationAPIEndpoint;
        this.apiKey = apiKey;
    }

    public String translateText(String text, String targetLocale) {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost(translationAPIEndpoint);
            httpPost.setHeader("Authorization", "Bearer " + apiKey);
            httpPost.setHeader("Content-Type", "application/json");

            String requestBody = buildTranslationRequestBody(text, targetLocale);
            httpPost.setEntity(new StringEntity(requestBody));

            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                if (response.getStatusLine().getStatusCode() == 200) {
                    String jsonResponse = EntityUtils.toString(response.getEntity());
                    return extractTranslatedText(jsonResponse); // Method to parse and return the translated text
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null; // Or handle error appropriately
    }

    private String buildTranslationRequestBody(String text, String targetLocale) {
        JsonObject rootObject = new JsonObject();
        JsonArray contentsArray = new JsonArray();

        JsonObject textObject = new JsonObject();
        textObject.addProperty("text", text);
        contentsArray.add(textObject);

        rootObject.add("contents", contentsArray);
        rootObject.addProperty("targetLanguageCode", targetLocale);
        // If needed, you can also set a source language code

        return rootObject.toString();
    }

    private String extractTranslatedText(String jsonResponse) {
        JsonObject responseObj = JsonParser.parseString(jsonResponse).getAsJsonObject();
        JsonArray translationsArray = responseObj.getAsJsonArray("translations");

        if (translationsArray != null && translationsArray.size() > 0) {
            JsonObject translationObject = translationsArray.get(0).getAsJsonObject();
            JsonElement translatedTextElement = translationObject.get("translatedText");
            if (translatedTextElement != null) {
                return translatedTextElement.getAsString();
            }
        }
        return null;
    }

}