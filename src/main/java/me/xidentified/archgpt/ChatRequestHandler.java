package me.xidentified.archgpt;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import me.xidentified.archgpt.context.ContextManager;
import me.xidentified.archgpt.utils.ArchGPTConstants;
import me.xidentified.archgpt.utils.LocaleUtils;
import net.citizensnpcs.api.npc.NPC;
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
    private final ContextManager contextManager;

    public ChatRequestHandler(ArchGPT plugin) {
        this.plugin = plugin;
        this.contextManager = new ContextManager(plugin);
    }

    public enum RequestType {
        GREETING,
        CONVERSATION
    }

    public CompletableFuture<Object> processMCPRequest(Player player, NPC npc, String message, 
                                                    RequestType requestType, List<JsonObject> conversationState) {
        UUID playerUUID = player.getUniqueId();
        
        // Use a CompletableFuture to handle the async operation
        CompletableFuture<JsonObject> contextFuture = new CompletableFuture<>();
        
        // Schedule context gathering on the main thread
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            try {
                JsonObject context = contextManager.getOrganizedContext(player, npc, requestType);
                contextFuture.complete(context);
            } catch (Exception e) {
                contextFuture.completeExceptionally(e);
            }
        });

        return contextFuture.thenCompose(context -> {
            plugin.playerSemaphores.putIfAbsent(playerUUID, new Semaphore(1));

            return CompletableFuture.supplyAsync(() -> {
                try {
                    // Acquire the semaphore for this specific player
                    Semaphore semaphore = plugin.playerSemaphores.get(playerUUID);
                    semaphore.acquire();
                    
                    // Build MCP request using the context gathered on the main thread
                    JsonObject mcpRequest = buildMCPRequest(context, message, conversationState, requestType);
                    plugin.debugLog("MCP Request: " + mcpRequest.toString());

                    // Log the request for debugging
                    plugin.debugLog("Sending request to MCP server with provider: " + 
                    plugin.getConfigHandler().getMcpProvider() + ", model: " + plugin.getConfigHandler().getMcpModel());

                    // Send to MCP server
                    HttpRequest request = buildMCPHttpRequest(mcpRequest.toString());
                    HttpResponse<String> response = plugin.getHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
                    
                    int statusCode = response.statusCode();
                    plugin.debugLog("Received response from MCP server, Status Code: " + statusCode);

                    if (statusCode == 200) {
                        String jsonResponse = response.body();
                        JsonObject responseObject = JsonParser.parseString(jsonResponse).getAsJsonObject();
                        return extractAssistantResponseText(responseObject);
                    } else {
                        plugin.getLogger().severe("MCP Server Error: Status Code " + statusCode + " - " + response.body());
                        throw new RuntimeException("MCP Server Error: Status Code " + statusCode);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Thread was interrupted: " + e.getMessage());
                } catch (IOException | RuntimeException e) {
                    plugin.getLogger().severe("MCP Request Failed: " + e.getMessage());
                    throw new RuntimeException("MCP Request Failed: " + e.getMessage());
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
                plugin.getLogger().severe("Error processing MCP request: " + ex.getMessage());
                plugin.getConversationManager().endConversation(playerUUID);
                return null;
            });
        }).thenApply(assistantResponseText -> {
            // Process the response and prepare final result
            String response = assistantResponseText.trim();

            if (requestType == RequestType.GREETING) {
                return response;
            } else {
                // Additional processing for non-greeting requests
                String sanitizedPlayerMessage = message;

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

    private JsonObject buildMCPRequest(JsonObject context, String message, 
                                     List<JsonObject> conversationState, RequestType requestType) {
        JsonObject mcpRequest = new JsonObject();
        
        // Add context
        mcpRequest.add("context", context);
        
        // Add message
        mcpRequest.addProperty("message", message);
        
        // Add conversation history if available
        if (conversationState != null && !conversationState.isEmpty()) {
            JsonArray history = new JsonArray();
            for (JsonObject msg : conversationState) {
                history.add(msg);
            }
            mcpRequest.add("conversation_history", history);
        }
        
        // Add request type
        mcpRequest.addProperty("request_type", requestType.name());
        
        // Add provider information from config using the config handler
        mcpRequest.addProperty("provider", plugin.getConfigHandler().getMcpProvider());
        mcpRequest.addProperty("model", plugin.getConfigHandler().getMcpModel());
        mcpRequest.addProperty("max_tokens", plugin.getConfigHandler().getMcpMaxTokens());
        
        return mcpRequest;
    }

    private String extractAssistantResponseText(JsonObject responseObject) {
        if (responseObject.has("output")) {
            return responseObject.get("output").getAsString().trim();
        }
        plugin.getLogger().warning("Invalid response structure from MCP server");
        plugin.debugLog("MCP server response object: " + responseObject);
        return "I'm having trouble processing that right now.";
    }

    private HttpRequest buildMCPHttpRequest(String jsonRequestBody) {
        // Use the config handler to get the MCP server URL
        String mcpServerUrl = plugin.getConfigHandler().getMcpServerUrl();
        URI uri = URI.create(mcpServerUrl);

        return HttpRequest.newBuilder()
                .uri(uri)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonRequestBody, StandardCharsets.UTF_8))
                .build();
    }
}