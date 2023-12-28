package me.xidentified.archgpt;


import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.xidentified.archgpt.utils.LocaleUtils;
import me.xidentified.archgpt.utils.TranslationService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ChatRequestHandler {

    private final ArchGPT plugin;
    private final Logger logger;

    public ChatRequestHandler(ArchGPT plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    public enum RequestType {
        GREETING,
        CONVERSATION
    }

    public CompletableFuture<Object> processChatGPTRequest(JsonObject requestBody, RequestType requestType, Component playerMessageComponent, List<Component> conversationState) {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost httpPost = getHttpPost();
            plugin.debugLog("Setting Content-Type to application/json");

            // Convert the JsonObject to a JSON string
            String jsonRequest = requestBody.toString();
            plugin.debugLog("Request Body to ChatGPT API: " + jsonRequest);

            // Set the request body
            httpPost.setEntity(new StringEntity(jsonRequest));

            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                if (response.getStatusLine().getStatusCode() == 200) {
                    HttpEntity responseEntity = response.getEntity();
                    if (responseEntity != null) {
                        String jsonResponse = EntityUtils.toString(responseEntity);

                        // Parse the response JSON using the updated method
                        JsonObject responseObject = JsonParser.parseString(jsonResponse).getAsJsonObject();
                        // Extract the assistant's response from the "choices" array
                        String assistantResponseText = responseObject.getAsJsonArray("choices")
                                .get(0)
                                .getAsJsonObject()
                                .get("text")
                                .getAsString().trim();

                        Component responseComponent = Component.text(assistantResponseText.trim());

                        if (requestType == RequestType.GREETING) {
                            return CompletableFuture.completedFuture(responseComponent);
                        } else {
                            // Sanitize the ChatGPT response
                            Component sanitizedResponse = sanitizeAPIResponse(responseComponent);

                            // Sanitize the player's message
                            String sanitizedPlayerMessage = StringEscapeUtils.escapeJson(PlainTextComponentSerializer.plainText().serialize(playerMessageComponent));

                            // Limit the conversation state to a certain number of messages to avoid excessive token usage
                            if (conversationState.size() > ArchGPTConstants.MAX_CONVERSATION_STATE_SIZE * 2) {
                                conversationState.subList(0, 2).clear();
                            }

                            // Update the conversation state with the player's message and the sanitized ChatGPT response
                            conversationState.add(Component.text("user: " + sanitizedPlayerMessage));
                            conversationState.add(Component.text("assistant: ").append(sanitizedResponse));

                            return CompletableFuture.completedFuture(Pair.of(sanitizedResponse, conversationState));
                        }
                    } else {
                        // Handle case where responseEntity is null
                        logger.warning("ChatGPT API returned an empty response body.");
                        return handleAPIErrorResponse(requestType);
                    }
                } else {
                    int statusCode = response.getStatusLine().getStatusCode();
                    String errorMessage = EntityUtils.toString(response.getEntity());
                    logger.warning("ChatGPT API Error (Status Code: " + statusCode + "): " + errorMessage);

                    return handleAPIErrorResponse(requestType);
                }
            }
        } catch (IOException e) {
            if (plugin.getConfigHandler().isDebugMode()) {
                e.printStackTrace();
            }
            return handleAPIErrorResponse(requestType);
        }
    }

    @NotNull
    private HttpPost getHttpPost() {
        String apiKey = plugin.getConfigHandler().getApiKey();
        String chatGptEngine = plugin.getConfigHandler().getChatGptEngine();
        String chatGptEndpoint = "https://api.openai.com/v1/engines/" + chatGptEngine + "/completions";
        HttpPost httpPost = new HttpPost(chatGptEndpoint);

        // Set the request headers
        httpPost.setHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
        httpPost.setHeader(HttpHeaders.CONTENT_TYPE, "application/json");
        return httpPost;
    }

    private Component sanitizeAPIResponse(Component chatGptResponseComponent) {
        String response = PlainTextComponentSerializer.plainText().serialize(chatGptResponseComponent);

        // Remove any "white: assistant:" prefix from the response
        response = response.replaceFirst("(?i)^white: assistant:", "").trim();

        // Convert item/biome names like SNOWY_TAIGA to "snowy taiga"
        response = Arrays.stream(response.split(" "))
                .map(word -> {
                    if (word.contains("_")) {
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
        response = truncateResponse(response);
        return Component.text(response);
    }

    public String truncateResponse(String response) {
        if (response.length() <= ArchGPTConstants.MAX_CHAT_LENGTH) {
            return response;
        }
        int endPoint = response.lastIndexOf('.', ArchGPTConstants.MAX_CHAT_LENGTH);
        if (endPoint == -1) {
            endPoint = response.lastIndexOf(' ', ArchGPTConstants.MAX_CHAT_LENGTH);
        }
        return response.substring(0, endPoint + 1) + "..."; //
    }


    private String capitalizeFirstLetter(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }

    private String capitalizeSentences(String str) {
        Matcher m = Pattern.compile("(^|[.!?]\\s*)([a-z])").matcher(str);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(sb, m.group(1) + m.group(2).toUpperCase());
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private CompletableFuture<Object> handleAPIErrorResponse(RequestType requestType) {
        if (requestType == RequestType.GREETING) {
            return CompletableFuture.completedFuture(Component.text("Hello there!"));
        } else {
            return CompletableFuture.completedFuture(Pair.of(Component.text("Sorry, I was lost in thought... Could you repeat that?"), new ArrayList<Component>()));
        }
    }
}