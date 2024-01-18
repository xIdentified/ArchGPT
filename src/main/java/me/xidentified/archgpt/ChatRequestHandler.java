package me.xidentified.archgpt;


import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.xidentified.archgpt.utils.ArchGPTConstants;
import me.xidentified.archgpt.utils.LocaleUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.apache.commons.lang3.tuple.Pair;
import org.bukkit.entity.Player;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;

public class ChatRequestHandler {
    private final ArchGPT plugin;

    public ChatRequestHandler(ArchGPT plugin) {
        this.plugin = plugin;
    }

    public enum RequestType {
        GREETING,
        CONVERSATION
    }

    public CompletableFuture<Object> processChatGPTRequest(Player player, JsonObject requestBody, RequestType requestType, Component playerMessageComponent, List<JsonObject> conversationState) {
        UUID playerUUID = player.getUniqueId();
        plugin.playerSemaphores.putIfAbsent(playerUUID, new Semaphore(1));

        return CompletableFuture.supplyAsync(() -> {
            try {
                // Acquire the semaphore for this specific player
                Semaphore semaphore = plugin.playerSemaphores.get(playerUUID);
                semaphore.acquire();

                HttpRequest request = buildHttpRequest(requestBody.toString());
                plugin.debugLog("Request body sent to GPT: " + requestBody);

                HttpResponse<String> response = plugin.getHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
                int statusCode = response.statusCode();
                plugin.debugLog("Received response from ChatGPT API, Status Code: " + statusCode);

                if (statusCode == 200) {
                    String jsonResponse = response.body();
                    JsonObject responseObject = JsonParser.parseString(jsonResponse).getAsJsonObject();
                    return extractAssistantResponseText(responseObject);
                } else if (statusCode == 503) {
                    plugin.getLogger().warning("ChatGPT API is currently unavailable. Please try again later.");
                    return "Sorry, I am unable to respond right now. Please try again later.";
                } else {
                    plugin.getLogger().severe("ChatGPT API Error: Status Code " + statusCode + " - " + response.body());
                    throw new RuntimeException("ChatGPT API Error: Status Code " + statusCode);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Thread was interrupted: " + e.getMessage());
            } catch (IOException | RuntimeException e) {
                plugin.getLogger().severe("ChatGPT API Request Failed: " + e.getMessage());
                throw new RuntimeException("ChatGPT API Request Failed: " + e.getMessage());
            } finally {
                // Ensure the semaphore is released for this player
                Semaphore semaphore = plugin.playerSemaphores.get(playerUUID);
                if (semaphore != null) {
                    semaphore.release();
                }
            }
        }).thenCompose(assistantResponseText -> {
            // Check if translation is needed
            String playerLocale = LocaleUtils.getPlayerLocale(player);
            plugin.debugLog("Player locale read as: " + playerLocale);
            if (!playerLocale.substring(0, 2).equalsIgnoreCase("en")) {
                String targetLang = playerLocale.substring(0, 2);
                return plugin.getTranslationService().translateText(assistantResponseText, targetLang)
                        .thenApply(translatedText -> translatedText != null ? translatedText : assistantResponseText);
            }
            plugin.debugLog("Final Processed Response: " + assistantResponseText);
            return CompletableFuture.completedFuture(assistantResponseText);

        }).exceptionally(ex -> {
                    // Handle exceptions - log the error and end the conversation
                    plugin.getLogger().severe("Error processing ChatGPT request: " + ex.getMessage());
                    plugin.getConversationManager().endConversation(playerUUID);
                    return null;
        }).thenApply(assistantResponseText -> {
            // Process the response and prepare final result
            String response = assistantResponseText.trim();

            if (requestType == RequestType.GREETING) {
                return response;
            } else {
                // Additional processing for non-greeting requests
                String sanitizedPlayerMessage = PlainTextComponentSerializer.plainText().serialize(playerMessageComponent);

                if (conversationState.size() > ArchGPTConstants.MAX_CONVERSATION_STATE_SIZE * 2) {
                    conversationState.subList(0, 2).clear();
                }

                JsonObject userMessageJson = new JsonObject();
                userMessageJson.addProperty("role", "user");
                userMessageJson.addProperty("content", sanitizedPlayerMessage);
                conversationState.add(userMessageJson);

                JsonObject assistantMessageJson = new JsonObject();
                assistantMessageJson.addProperty("role", "assistant");
                assistantMessageJson.addProperty("content", response);
                conversationState.add(assistantMessageJson);

                return Pair.of(response, conversationState);
            }
        });
    }

    private String extractAssistantResponseText(JsonObject responseObject) {
        if (responseObject.has("choices") && !responseObject.getAsJsonArray("choices").isEmpty()) {
            JsonObject choice = responseObject.getAsJsonArray("choices").get(0).getAsJsonObject();
            if (choice.has("message") && choice.getAsJsonObject("message").has("content")) {
                return choice.getAsJsonObject("message").get("content").getAsString().trim();
            }
        }
        plugin.getLogger().warning("Invalid response structure from ChatGPT API");
        plugin.debugLog("ChatGPT API response object: " + responseObject);
        return "";
    }

    private HttpRequest buildHttpRequest(String jsonRequestBody) {
        String apiKey = plugin.getConfigHandler().getOpenAiApiKey();
        URI uri = URI.create("https://api.openai.com/v1/chat/completions");

        return HttpRequest.newBuilder()
                .uri(uri)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonRequestBody, StandardCharsets.UTF_8))
                .build();
    }

}