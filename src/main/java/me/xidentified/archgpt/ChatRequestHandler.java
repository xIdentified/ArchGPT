package me.xidentified.archgpt;


import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.xidentified.archgpt.utils.LocaleUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import javax.naming.ServiceUnavailableException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ChatRequestHandler {
    private final ArchGPT plugin;

    public ChatRequestHandler(ArchGPT plugin) {
        this.plugin = plugin;
    }

    public enum RequestType {
        GREETING,
        CONVERSATION
    }

    public CompletableFuture<Object> processChatGPTRequest(Player player, JsonObject requestBody, RequestType requestType, Component playerMessageComponent, List<Component> conversationState) {
        UUID playerUUID = player.getUniqueId();
        plugin.playerSemaphores.putIfAbsent(playerUUID, new Semaphore(1));

        return CompletableFuture.supplyAsync(() -> {
            try (CloseableHttpClient httpClient = HttpClients.custom()
                    .disableCookieManagement()
                    .build()) {
                // Acquire the semaphore for this specific player
                Semaphore semaphore = plugin.playerSemaphores.get(playerUUID);
                semaphore.acquire();

                HttpPost httpPost = getHttpPost();
                String jsonRequest = requestBody.toString();
                httpPost.setEntity(new StringEntity(jsonRequest, StandardCharsets.UTF_8));

                plugin.debugLog("Request body sent to GPT: " + jsonRequest);

                try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
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
                    plugin.getManager().endConversation(playerUUID);
                    return null;
        }).thenApply(assistantResponseText -> {
            // Process the response and prepare final result
            Component responseComponent = Component.text(assistantResponseText.trim());

            if (requestType == RequestType.GREETING) {
                return responseComponent;
            } else {
                // Additional processing for non-greeting requests
                Component sanitizedResponse = sanitizeAPIResponse(responseComponent);
                String sanitizedPlayerMessage = StringEscapeUtils.escapeJson(PlainTextComponentSerializer.plainText().serialize(playerMessageComponent));

                if (conversationState.size() > ArchGPTConstants.MAX_CONVERSATION_STATE_SIZE * 2) {
                    conversationState.subList(0, 2).clear();
                }

                conversationState.add(Component.text("user: " + sanitizedPlayerMessage));
                conversationState.add(Component.text("assistant: ").append(sanitizedResponse));

                return Pair.of(sanitizedResponse, conversationState);
            }
        });
    }

    private String extractAssistantResponseText(JsonObject responseObject) {
        if (responseObject.has("choices") && !responseObject.getAsJsonArray("choices").isEmpty()) {
            JsonObject choice = responseObject.getAsJsonArray("choices").get(0).getAsJsonObject();
            if (choice.has("message") && choice.getAsJsonObject("message").has("content")) {
                String responseText = choice.getAsJsonObject("message").get("content").getAsString().trim();

                // Check if the long messages should be split
                boolean shouldSplitLongMessages = plugin.getConfigHandler().isShouldSplitLongMsg();
                if (shouldSplitLongMessages) {
                    return insertBreakTags(responseText);
                } else {
                    return capitalizeSentences(responseText);
                }
            }
        }
        plugin.getLogger().warning("Invalid response structure from ChatGPT API");
        plugin.debugLog("ChatGPT API response object: " + responseObject);
        return "";
    }

    private String insertBreakTags(String text) {
        int sentenceCountPerChunk = 2;
        StringBuilder modifiedText = new StringBuilder();
        int sentenceCount = 0;
        int startIndex = 0;

        for (int i = 0; i < text.length(); i++) {
            // Check for sentence end (considering space or end of text after punctuation)
            if ((text.charAt(i) == '.' || text.charAt(i) == '?' || text.charAt(i) == '!') && (i + 1 == text.length() || text.charAt(i + 1) == ' ')) {
                sentenceCount++;
                if (sentenceCount == sentenceCountPerChunk) {
                    // Add the text chunk to the modified text
                    modifiedText.append(capitalizeSentences(text.substring(startIndex, i + 1).trim()));
                    if (i + 1 < text.length()) {
                        modifiedText.append("<br>");
                    }
                    startIndex = i + 1;
                    sentenceCount = 0;
                }
            }
        }

        // Add the remaining part of the text if any
        if (startIndex < text.length()) {
            modifiedText.append(capitalizeSentences(text.substring(startIndex).trim()));
        }

        return modifiedText.toString();
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

    private Component sanitizeAPIResponse(Component chatGptResponseComponent) {
        String response = PlainTextComponentSerializer.plainText().serialize(chatGptResponseComponent);

        // Process each word, skipping placeholders
        response = Arrays.stream(response.split(" "))
                .map(word -> {
                    // Check if the word is a placeholder
                    if (word.startsWith("%") && word.endsWith("%")) {
                        return word;
                    }

                    // Process non-placeholder words
                    else if (word.contains("_")) {
                        return Arrays.stream(word.split("_"))
                                .map(this::capitalizeFirstLetter)
                                .collect(Collectors.joining(" "));
                    } else {
                        return word.toLowerCase();
                    }
                })
                .collect(Collectors.joining(" "));

        response = capitalizeSentences(response);
        response = response.replace(" i ", " I ");
        response = response.replace(" i'm ", " I'm ");

        return Component.text(response);
    }

    private String capitalizeSentences(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }

        // Capitalize the first letter of the string
        str = capitalizeFirstLetter(str);

        // Create a pattern to find sentence ends followed by a lowercase letter
        Pattern pattern = Pattern.compile("(?<=[.!?])\\s+(\\p{Lower})");
        Matcher matcher = pattern.matcher(str);

        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            // Replace lowercase letter after sentence end with uppercase
            matcher.appendReplacement(sb, " " + matcher.group(1).toUpperCase());
        }
        matcher.appendTail(sb);

        return sb.toString();
    }

    private String capitalizeFirstLetter(String str) {
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }

}