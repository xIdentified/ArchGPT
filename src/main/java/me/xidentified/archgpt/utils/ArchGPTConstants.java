package me.xidentified.archgpt.utils;

public class ArchGPTConstants {

    public static final int CACHE_EXPIRATION_MINUTES = 30;

    public static final long MAX_HOLOGRAM_LIFETIME = 1200L;  // 60 seconds in ticks

    public static final double MAX_DISTANCE_FROM_NPC = 14.0;

    public static final double MAX_DISTANCE_LINE_OF_SIGHT = 4.0;

    public static final int MAX_CONVERSATION_STATE_SIZE = 8;

    public static final long GREETING_COOLDOWN_MS = 300000; // 5 min default

    public static final int GREETING_RADIUS = 3;

    public static final long CHAT_COOLDOWN_MS = 3000;

    public static final int MINIMUM_SAVED_SENTENCE_LENGTH = 30;
}