package me.xidentified.archgpt.utils;

import com.google.api.services.language.v1beta2.CloudNaturalLanguage;
import com.google.api.services.language.v1beta2.CloudNaturalLanguageScopes;
import com.google.api.services.language.v1beta2.model.*;
import me.xidentified.archgpt.storage.model.Conversation;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class GoogleCloudService {
    private final CloudNaturalLanguage languageService;

    public GoogleCloudService() throws IOException {
        HttpTransport httpTransport = new NetHttpTransport();
        JsonFactory jsonFactory = new JacksonFactory();

        // Load the service account key JSON file
        GoogleCredential credential = GoogleCredential.fromStream(new FileInputStream(jsonPath))
                .createScoped(CloudNaturalLanguageScopes.all());

        this.languageService = new CloudNaturalLanguage(httpTransport, jsonFactory, credential)
                .setApplicationName("ArchGPT")
                .build();
    }

    // Below methods are related to NPC memory - sending only relevant previous context to the current API response
    public List<Entity> analyzePlayerMessageEntities(String message) throws IOException {
        AnalyzeEntitiesRequest request = new AnalyzeEntitiesRequest()
                .setDocument(new Document().setContent(message).setType("PLAIN_TEXT"));
        AnalyzeEntitiesResponse response = languageService.documents().analyzeEntities(request).execute();
        return response.getEntities();
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
        Sentiment sentiment = analyzePlayerMessageSentiment(playerMessage);
        String npcTone = determineNpcTone(sentiment);

        // Construct the tailored NPC prompt
        return String.format("Respond %s. %s", npcTone, relevantPastConversations);
    }

    public Sentiment analyzePlayerMessageSentiment(String message) throws IOException {
        AnalyzeSentimentRequest request = new AnalyzeSentimentRequest()
                .setDocument(new Document().setContent(message).setType("PLAIN_TEXT"));
        AnalyzeSentimentResponse response = languageService.documents().analyzeSentiment(request).execute();
        return response.getDocumentSentiment();
    }

    private String determineNpcTone(Sentiment sentiment) {
        double score = sentiment.getScore();
        double magnitude = sentiment.getMagnitude();

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