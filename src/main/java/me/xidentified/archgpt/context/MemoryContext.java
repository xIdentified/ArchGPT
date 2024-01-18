package me.xidentified.archgpt.context;

import lombok.Getter;
import me.xidentified.archgpt.storage.model.Conversation;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Getter
public class MemoryContext {
    private final Queue<String> recentConversations; // Store recent conversations
    private final Pattern inquiryPattern;
    private static final Set<String> STOP_WORDS = Set.of( // Common words to be excluded
            "i", "me", "my", "myself", "we", "our", "ours", "ourselves", "you", "your", "yours",
            "yourself", "yourselves", "he", "him", "his", "himself", "she", "her", "hers", "herself",
            "it", "its", "itself", "they", "them", "their", "theirs", "themselves", "what", "which",
            "who", "whom", "this", "that", "these", "those", "am", "is", "are", "was", "were", "be",
            "been", "being", "have", "has", "had", "having", "do", "does", "did", "doing", "a", "an",
            "the", "and", "but", "if", "or", "because", "as", "until", "while", "of", "at", "by", "for",
            "with", "about", "against", "between", "into", "through", "during", "before", "after",
            "above", "below", "to", "from", "up", "down", "in", "out", "on", "off", "over", "under",
            "again", "further", "then", "once", "here", "there", "when", "where", "why", "how", "all",
            "any", "both", "each", "few", "more", "most", "other", "some", "such", "no", "nor", "not",
            "only", "own", "same", "so", "than", "too", "very", "s", "t", "can", "will", "just", "don",
            "should", "now", "ah"
    );
    public MemoryContext() {
        this.recentConversations = new LinkedList<>();
        String inquiryKeywords = "earlier|before|previously|past|you said";
        inquiryPattern = Pattern.compile("\\b(" + inquiryKeywords + ")\\b", Pattern.CASE_INSENSITIVE);
    }

    public boolean isAskingAboutPastConversation(Component playerMessage) {
        String normalizedMessage = PlainTextComponentSerializer.plainText().serialize(playerMessage).toLowerCase();
        return inquiryPattern.matcher(normalizedMessage).find();
    }

    public String summarizeConversations(List<Conversation> conversations) {
        // Convert Conversation objects to strings
        List<String> conversationTexts = conversations.stream()
                .map(Conversation::getMessage)
                .collect(Collectors.toList());

        Set<String> topKeywords = extractKeywords(conversationTexts);
        String keywordPattern = topKeywords.stream()
                .map(Pattern::quote)
                .collect(Collectors.joining("|"));
        Pattern sentencePattern = Pattern.compile("[^.!?]*\\b(" + keywordPattern + ")\\b[^.!?]*[.!?]");

        Set<String> summarySentences = new HashSet<>();
        for (String conversation : conversationTexts) {
            Matcher matcher = sentencePattern.matcher(conversation);
            while (matcher.find()) {
                summarySentences.add(matcher.group());
            }
        }

        return String.join(" ", summarySentences);
    }

    private Set<String> extractKeywords(List<String> conversationTexts) {
        Map<String, Integer> keywordFrequency = new HashMap<>();
        for (String conversation : conversationTexts) {
            String[] words = conversation.toLowerCase().split("\\s+");
            for (String word : words) {
                if (!STOP_WORDS.contains(word)) {
                    keywordFrequency.put(word, keywordFrequency.getOrDefault(word, 0) + 1);
                }
            }
        }

        return keywordFrequency.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .limit(5) // Adjust the limit as needed
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }
}

