package me.xidentified.archgpt;


import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.xidentified.archgpt.utils.ArchGPTConstants;
import me.xidentified.archgpt.utils.LocaleUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import javax.naming.ServiceUnavailableException;
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

                HttpPost httpPost = getHttpPost();
                String jsonRequest = requestBody.toString();
                httpPost.setEntity(new StringEntity(jsonRequest, StandardCharsets.UTF_8));

                plugin.debugLog("Request body sent to GPT: " + jsonRequest);

                try (CloseableHttpResponse response = plugin.getHttpClient().execute(httpPost)) {
                    int statusCode = response.getStatusLine().getStatusCode();
                    plugin.debugLog("Received response from ChatGPT API, Status Code: " + statusCode);

                    if (response.getStatusLine().getStatusCode() == 200) {
                        String jsonResponse = EntityUtils.toString(response.getEntity());
                        JsonObject responseObject = JsonParser.parseString(jsonResponse).getAsJsonObject();
                        return extractAssistantResponseText(responseObject);
                    } else {
                        String responseString = EntityUtils.toString(response.getEntity());
                        if (statusCode == 503) {
                            throw new ServiceUnavailableException("ChatGPT API Service Unavailable: " + responseString);
                        } else {
                            throw new RuntimeException("ChatGPT API Error: " + responseString);
                        }
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Thread was interrupted: " + e.getMessage());
            } catch (ServiceUnavailableException e) {
                plugin.getLogger().warning("ChatGPT API is currently unavailable. Please try again later.");
                return "Sorry, I am unable to respond right now. Please try again later.";
            } catch (IOException e) {
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

    @NotNull
    private HttpPost getHttpPost() {
        String apiKey = plugin.getConfigHandler().getApiKey();
        String chatGptEndpoint = "https://api.openai.com/v1/chat/completions";
        HttpPost httpPost = new HttpPost(chatGptEndpoint);

        // Set the request headers
        httpPost.setHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
        httpPost.setHeader(HttpHeaders.CONTENT_TYPE, "application/json");
        return httpPost;
    }

}