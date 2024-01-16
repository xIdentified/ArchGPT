package me.xidentified.archgpt.utils;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.xidentified.archgpt.storage.model.Conversation;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class GoogleCloudService {
    private final String apiKey;
    private final Pattern inquiryPattern;

    public GoogleCloudService(String apiKey) {
        this.apiKey = apiKey;
        String inquiryKeywords = "earlier|before|previously|past|you said|you say";
        inquiryPattern = Pattern.compile("\\b(" + inquiryKeywords + ")\\b", Pattern.CASE_INSENSITIVE);
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

    public boolean isAskingAboutPastConversation(Component playerMessage) {
        String normalizedMessage = PlainTextComponentSerializer.plainText().serialize(playerMessage).toLowerCase();
        return inquiryPattern.matcher(normalizedMessage).find();
    }

    public boolean isConversationRelatedToPast(Component playerMessageComponent, List<Conversation> pastConversations) {
        // Serialize the Component to a plain text string
        String playerMessage = PlainTextComponentSerializer.plainText().serialize(playerMessageComponent).toLowerCase();

        // Split the player's message into words
        Set<String> playerMessageWords = new HashSet<>(Arrays.asList(playerMessage.split("\\s+")));

        // Common words that are not significant for conversation matching
        Set<String> commonWords = new HashSet<>(Arrays.asList("the", "and", "you", "i", "a", "to", "it", "is", "are", "was", "on", "of"));

        for (Conversation pastConversation : pastConversations) {
            Set<String> pastMessageWords = new HashSet<>(Arrays.asList(pastConversation.getMessage().toLowerCase().split("\\s+")));

            // Remove common words
            pastMessageWords.removeAll(commonWords);
            playerMessageWords.removeAll(commonWords);

            pastMessageWords.retainAll(playerMessageWords);

            // Check if there are enough common words (more than 2)
            if (pastMessageWords.size() > 2) {
                return true;
            }
        }
        return false;
    }

    public List<Conversation> getRelevantPastConversations(Component playerMessage, List<Conversation> pastConversations) {
        return pastConversations.stream()
                .filter(pastConversation -> isConversationRelatedToPast(playerMessage, pastConversations))
                .collect(Collectors.toList());
    }

    // Methods below related to NPC emotional responses
    public JsonObject analyzePlayerMessageSentiment(String message) throws IOException {
        String response = postRequest("https://language.googleapis.com/v1/documents:analyzeSentiment", createJsonPayload(message));
        return new Gson().fromJson(response, JsonObject.class).getAsJsonObject("documentSentiment");
    }

    private String determineNpcTone(JsonObject sentiment) {
        double score = sentiment.get("score").getAsDouble();
        double magnitude = sentiment.get("magnitude").getAsDouble();

        // Examples of more nuanced tone determination
        if (score > 0.7) {
            return "in a highly enthusiastic and uplifting manner";
        } else if (score > 0.3) {
            return "in a friendly and positive manner";
        } else if (score < -0.7) {
            return "with deep concern or strong skepticism";
        } else if (score < -0.3) {
            return "with a hint of concern or mild skepticism";
        } else {
            if (magnitude > 0.5) {
                return "in a reflective but neutral tone";
            } else {
                return "in a calm and balanced tone";
            }
        }
    }

}