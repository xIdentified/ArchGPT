package me.xidentified.archgpt.utils;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

public class TranslationService {

    private final String libreTranslateAPIEndpoint;

    public TranslationService(String libreTranslateAPIEndpoint) {
        this.libreTranslateAPIEndpoint = libreTranslateAPIEndpoint;
    }

    public String translateText(String text, String sourceLang, String targetLang) {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost(libreTranslateAPIEndpoint + "/translate");
            httpPost.setHeader("Content-Type", "application/json");

            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("q", text);
            requestBody.addProperty("source", sourceLang);
            requestBody.addProperty("target", targetLang);

            httpPost.setEntity(new StringEntity(requestBody.toString()));

            HttpResponse response = httpClient.execute(httpPost);
            String jsonResponse = EntityUtils.toString(response.getEntity());

            JsonObject responseObject = JsonParser.parseString(jsonResponse).getAsJsonObject();

            if (responseObject.has("translatedText")) {
                return responseObject.get("translatedText").getAsString();
            } else {
                // Handle the case where the expected key is not present
                System.err.println("The key 'translatedText' is not found in the JSON response");
                return null;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
