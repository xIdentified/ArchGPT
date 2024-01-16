package me.xidentified.archgpt.utils;

import com.google.cloud.language.v2.*;
import me.xidentified.archgpt.storage.model.Conversation;
import org.bukkit.Bukkit;

import java.util.*;
import java.util.stream.Collectors;

// TODO: Finish this class - implement into NPCConversationManager
public class GoogleCloudService {
    private final LanguageServiceClient languageServiceClient;

    public GoogleCloudService(LanguageServiceClient languageServiceClient) {
        this.languageServiceClient = languageServiceClient;
    }

    // Below methods are related to NPC memory - sending only relevant previous context to the current API response
    public List<Entity> analyzePlayerMessageEntities(String message) {
        try {
            Document doc = Document.newBuilder().setContent(message).setType(Document.Type.PLAIN_TEXT).build();
            AnalyzeEntitiesRequest request = AnalyzeEntitiesRequest.newBuilder().setDocument(doc).build();
            AnalyzeEntitiesResponse response = languageServiceClient.analyzeEntities(request);
            return response.getEntitiesList();
        } catch (Exception e) {
            Bukkit.getLogger().warning("Error analyzing entities: " + e.getMessage());
            return Collections.emptyList(); // Return an empty list
        }
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

    public Sentiment analyzePlayerMessageSentiment(String message) {
        try {
            Document doc = Document.newBuilder().setContent(message).setType(Document.Type.PLAIN_TEXT).build();
            return languageServiceClient.analyzeSentiment(doc).getDocumentSentiment();
        } catch (Exception e) {
            Bukkit.getLogger().warning("Error analyzing sentiment: " + e.getMessage());
            return null; // Return null or a default sentiment
        }
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