package me.xidentified.archgpt.utils;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.xidentified.archgpt.storage.model.Conversation;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

public class GoogleCloudService {
    private final String apiKey;

    public GoogleCloudService(String apiKey) {
        this.apiKey = apiKey;
    }

    public List<JsonObject> analyzePlayerMessageEntities(String message) throws IOException {
        String response = postRequest("https://language.googleapis.com/v1/documents:analyzeEntities", createJsonPayload(message));
        JsonObject responseJson = new Gson().fromJson(response, JsonObject.class);
        JsonArray entitiesArray = responseJson.getAsJsonObject("entities").getAsJsonArray();

        List<JsonObject> entities = new ArrayList<>();
        for (JsonElement element : entitiesArray) {
            entities.add(element.getAsJsonObject());
        }
        return entities;
    }

    private String postRequest(String urlString, String jsonInputString) throws IOException {
        HttpURLConnection connection = getHttpURLConnection(urlString, jsonInputString);

        try (BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder response = new StringBuilder();
            String responseLine;
            while ((responseLine = br.readLine()) != null) {
                response.append(responseLine.trim());
            }
            return response.toString();
        }
    }

    @NotNull
    private HttpURLConnection getHttpURLConnection(String urlString, String jsonInputString) throws IOException {
        URL url = new URL(urlString + "?key=" + apiKey);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Authorization", "Bearer " + apiKey);
        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        connection.setDoOutput(true);

        try (OutputStream os = connection.getOutputStream()) {
            byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }
        return connection;
    }

    private String createJsonPayload(String text) {
        JsonObject document = new JsonObject();
        document.addProperty("type", "PLAIN_TEXT");
        document.addProperty("content", text);

        JsonObject json = new JsonObject();
        json.add("document", document);
        return new Gson().toJson(json);
    }

    public boolean isConversationRelatedToPast(String playerMessage, List<Conversation> pastConversations) {
        // Split the player's message into words
        Set<String> playerMessageWords = new HashSet<>(Arrays.asList(playerMessage.toLowerCase().split("\\s+")));

        // Iterate through past conversations to find any related ones
        for (Conversation pastConversation : pastConversations) {
            Set<String> pastMessageWords = new HashSet<>(Arrays.asList(pastConversation.getMessage().toLowerCase().split("\\s+")));
            pastMessageWords.retainAll(playerMessageWords); // Intersection of words

            if (!pastMessageWords.isEmpty()) {
                // If there are common words between the player's message and past message
                return true;
            }
        }
        return false;
    }

    public List<Conversation> getRelevantPastConversations(String playerMessage, List<Conversation> pastConversations) {
        return pastConversations.stream()
                .filter(pastConversation -> isConversationRelatedToPast(playerMessage, pastConversations))
                .collect(Collectors.toList());
    }

    // Methods below related to NPC emotional responses
    public String tailorNpcResponse(String playerMessage, List<Conversation> pastConversations) {
        // Filter relevant past conversations
        List<Conversation> relevantConversations = getRelevantPastConversations(playerMessage, pastConversations);

        // Extract and concatenate the content of relevant conversations
        String relevantPastConversations = relevantConversations.stream()
                .map(Conversation::getMessage)
                .collect(Collectors.joining(" "));

        // Analyze sentiment of the player's message
        try {
            JsonObject sentiment = analyzePlayerMessageSentiment(playerMessage);
            String npcTone = determineNpcTone(sentiment);
            return String.format("Respond %s. %s", npcTone, relevantPastConversations);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public JsonObject analyzePlayerMessageSentiment(String message) throws IOException {
        String response = postRequest("https://language.googleapis.com/v1/documents:analyzeSentiment", createJsonPayload(message));
        return new Gson().fromJson(response, JsonObject.class).getAsJsonObject("documentSentiment");
    }


    private String determineNpcTone(JsonObject sentiment) {
        double score = sentiment.get("score").getAsDouble();
        double magnitude = sentiment.get("magnitude").getAsDouble();

        if (score > 0.5) {
            return magnitude > 0.5 ? "in an enthusiastic and positive manner" : "in a cheerful and positive manner";
        } else if (score < -0.5) {
            return magnitude > 0.5 ? "with strong concern or skepticism" : "with mild concern or curiosity";
        } else {
            if (magnitude > 0.5) {
                return "in a somewhat emotional but controlled manner";
            } else {
                return "in a neutral tone";
            }
        }
    }

}