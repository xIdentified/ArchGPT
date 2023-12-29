package me.xidentified.archgpt.utils;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

public class TranslationService {

    private final String libreTranslateAPIEndpoint;
    private final Logger logger;

    public TranslationService(String libreTranslateAPIEndpoint, Logger logger) {
        this.libreTranslateAPIEndpoint = libreTranslateAPIEndpoint;
        this.logger = logger;
    }

    public String translateText(String text, String sourceLang, String targetLang) {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            String fullEndpoint = libreTranslateAPIEndpoint + "/translate";
            HttpPost httpPost = new HttpPost(fullEndpoint);
            httpPost.setHeader("Content-Type", "application/json");

            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("q", text);
            requestBody.addProperty("source", sourceLang);
            requestBody.addProperty("target", targetLang);

            String jsonRequestBody = requestBody.toString();
            logger.info("Sending translation request: " + jsonRequestBody + " to " + fullEndpoint);
            httpPost.setEntity(new StringEntity(jsonRequestBody, StandardCharsets.UTF_8));

            HttpResponse response = httpClient.execute(httpPost);
            String jsonResponse = EntityUtils.toString(response.getEntity());
            logger.info("Translation API Response: " + jsonResponse);

            JsonObject responseObject = JsonParser.parseString(jsonResponse).getAsJsonObject();

            if (responseObject.has("translatedText")) {
                return responseObject.get("translatedText").getAsString();
            } else {
                logger.warning("Translation API Response missing 'translatedText': " + jsonResponse);
                return null;
            }
        } catch (Exception e) {
            logger.severe("Translation Service Error: " + e.getMessage());
            return null;
        }
    }

}