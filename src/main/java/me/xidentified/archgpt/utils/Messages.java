package me.xidentified.archgpt.utils;

import de.cubbossa.translations.Message;
import de.cubbossa.translations.MessageBuilder;

public class Messages {

    // General
    public static final Message GENERAL_CMD_PLAYER_ONLY = new MessageBuilder("general.must_be_player")
            .withDefault("<negative>Only players can use this command.")
            .build();
    public static final Message GENERAL_CHAT_COOLDOWN = new MessageBuilder("general.chat_cooldown")
            .withDefault("<negative>Please wait before sending another message.")
            .build();
    public static final Message GENERAL_CMD_NO_PERM = new MessageBuilder("general.cmd.no_perm")
            .withDefault("<negative>You don't have permission to use this command.")
            .build();
    public static final Message MSG_TOO_SHORT = new MessageBuilder("general.msg_too_short")
            .withDefault("<negative>Your message is too short, use at least <size> characters.")
            .withPlaceholder("size")
            .build();
    public static final Message GENERAL_PLAYER_MESSAGE = new MessageBuilder("general.player_message")
            .withDefault("<player_name_color><player_name>: <message_color><message>")
            .withPlaceholder("player_name")
            .withPlaceholder("player_name_color")
            .withPlaceholder("message")
            .withPlaceholder("message_color")
            .build();
    public static final Message GENERAL_NPC_MESSAGE = new MessageBuilder("general.npc_message")
            .withDefault("<npc_name_color><npc_name>: <hover:show_text:'<red>Click to report'><click:run_command:/reportnpcmessage><message_color><message></click></hover>")
            .withPlaceholder("npc_name")
            .withPlaceholder("npc_name_color")
            .withPlaceholder("message")
            .withPlaceholder("message_color")
            .build();

    // Conversation
    public static final Message CONVERSATION_STARTED = new MessageBuilder("conversation.started")
            .withDefault("<yellow>Conversation started with <npc>. Type '<cancel>' to exit.")
            .withPlaceholder("npc")
            .withPlaceholder("cancel")
            .build();
    public static final Message CONVERSATION_ENDED = new MessageBuilder("conversation.started")
            .withDefault("<yellow>Conversation ended.")
            .build();
    public static final Message CONVERSATION_ENDED_INACTIVITY = new MessageBuilder("conversation.ended_inactivity")
            .withDefault("<yellow>Conversation ended due to inactivity.")
            .build();
    public static final Message CONVERSATION_ENDED_CHANGED_WORLDS = new MessageBuilder("conversation.ended_changed_worlds")
            .withDefault("<yellow>Conversation ended because you changed worlds.")
            .build();
    public static final Message CONVERSATION_ENDED_WALKED_AWAY = new MessageBuilder("conversation.ended_walked_away")
            .withDefault("<yellow>Conversation ended because you walked away.")
            .build();

    // Reports
    public static final Message REPORT_NONE_TO_DISPLAY = new MessageBuilder("report.none_to_display")
            .withDefault("<negative>There are no reports to display.")
            .build();
    public static final Message REPORT_TYPE_SELECTED = new MessageBuilder("report.type_selected")
            .withDefault("<positive>You've selected <type>. Enter your feedback about the last NPC message.")
            .withPlaceholder("type")
            .build();
    public static final Message REPORT_SELECT_TYPE = new MessageBuilder("report.select_type")
            .withDefault("<negative>Please click one of the report types above to continue.")
            .build();
    public static final Message REPORT_SUBMITTED = new MessageBuilder("report.submitted")
            .withDefault("<positive>Thank you, your report has been submitted. Type '<cancel>' to end the conversation.")
            .withPlaceholder("cancel")
            .build();
    public static final Message REPORT_DELETED = new MessageBuilder("report.deleted")
            .withDefault("<positive>Report successfully deleted.")
            .build();

    // Admin
    public static final Message CMD_USAGE = new MessageBuilder("archgpt.usage")
            .withDefault("<positive>Usage: /archgpt <argument>")
            .build();
    public static final Message BROADCAST_CMD_USAGE = new MessageBuilder("archgpt.usage")
            .withDefault("<yellow>Usage: /archgpt broadcast <npcName> <message>")
            .build();
    public static final Message SETNPC_CMD_USAGE = new MessageBuilder("archgpt.setnpc-usage")
            .withDefault("<yellow>Usage: /archgpt setnpc <npcname> <prompt>")
            .build();
    public static final Message RESETMEMORY_CMD_USAGE = new MessageBuilder("archgpt.resetnpc-usage")
            .withDefault("<yellow>Usage: /archgpt resetnpcmemory <npcName>")
            .build();

    public static final Message RELOAD_SUCCESS = new MessageBuilder("archgpt.reload")
            .withDefault("<positive>ArchGPT successfully reloaded!")
            .build();
    public static final Message DEBUG_MODE = new MessageBuilder("archgpt.debugmode")
            .withDefault("<yellow>Debug mode is now <toggle>")
            .withPlaceholder("toggle")
            .build();
    public static final Message VERSION_INFO = new MessageBuilder("archgpt.version")
            .withDefault("<yellow>Server version: <gray><server-ver>\n<yellow>ArchGPT version: <gray><plugin-ver>\n<yellow>Java version: <gray><java-ver>")
            .withPlaceholder("server-ver")
            .withPlaceholder("plugin-ver")
            .withPlaceholder("java-ver")
            .build();
    public static final Message CLEAR_STORAGE_SUCCESS = new MessageBuilder("archgpt.clearconversations.success")
            .withDefault("<positive>All conversation history successfully deleted.")
            .build();
    public static final Message CLEAR_STORAGE_ERROR = new MessageBuilder("archgpt.clearconversations.error")
            .withDefault("<negative>There was an error clearing conversation history.")
            .build();

    // NPC stuff
    public static final Message NPC_NOT_FOUND = new MessageBuilder("npc.not-found")
            .withDefault("<negative>NPC '<name>' not found.")
            .withPlaceholder("name")
            .build();
    public static final Message NPC_MEMORY_RESET = new MessageBuilder("npc.memory-reset")
            .withDefault("<positive>Memory for NPC '<name>' has been reset.")
            .withPlaceholder("name")
            .build();
    public static final Message NPC_PROMPT_UPDATED = new MessageBuilder("npc.prompt-updated")
            .withDefault("<positive>Prompt for <name> updated successfully.")
            .withPlaceholder("name")
            .build();
}
