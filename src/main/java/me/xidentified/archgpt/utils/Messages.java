package me.xidentified.archgpt.utils;

import de.cubbossa.tinytranslations.Message;
import de.cubbossa.tinytranslations.MessageBuilder;

public class Messages {

    // Just to be safe if someone clears the styles.yml lets add a prefix name. Formatting will be done by styles.
    public static final Message PREFIX = new MessageBuilder("prefix").withDefault("ArchGPT").build();

    // General
    public static final Message GENERAL_CMD_PLAYER_ONLY = new MessageBuilder("general.must_be_player")
            .withDefault("{msg:global:command.player_required}")
            .build();
    public static final Message GENERAL_CHAT_COOLDOWN = new MessageBuilder("general.chat_cooldown")
            .withDefault("<prefix_negative>Please wait before sending another message.")
            .build();
    public static final Message GENERAL_CMD_NO_PERM = new MessageBuilder("general.cmd.no_perm")
            .withDefault("{msg:global:command.missing_permissions}")
            .build();
    public static final Message MSG_TOO_SHORT = new MessageBuilder("general.msg_too_short")
            .withDefault("<prefix_negative>Your message is too short, use at least {size} characters.")
            .withPlaceholder("size")
            .build();
    public static final Message GENERAL_PLAYER_MESSAGE = new MessageBuilder("general.player_message")
            .withDefault("<player_name>You</player_name>: <player_text>{message}</player_text>")
            .withPlaceholder("player")
            .withPlaceholder("message")
            .build();
    public static final Message GENERAL_NPC_MESSAGE = new MessageBuilder("general.npc_message")
            .withDefault("<npc_name>{npc:name}:</npc_name> <hover:show_text:'<red>Click to report'><click:run_command:'/reportnpcmessage'><npc_text>{message}</npc_text></click></hover>")
            .withPlaceholder("npc")
            .withPlaceholder("message")
            .build();

    // Conversation
    public static final Message CONVERSATION_STARTED = new MessageBuilder("conversation.started")
            .withDefault("<yellow>Conversation started with {npc:name}. Type '{cancel}' to exit.")
            .withPlaceholder("npc")
            .withPlaceholder("cancel")
            .build();
    public static final Message CONVERSATION_ENDED = new MessageBuilder("conversation.started")
            .withDefault("<prefix_warning>Conversation ended.")
            .build();
    public static final Message CONVERSATION_ENDED_INACTIVITY = new MessageBuilder("conversation.ended_inactivity")
            .withDefault("<prefix_warning>Conversation ended due to inactivity.")
            .build();
    public static final Message CONVERSATION_ENDED_CHANGED_WORLDS = new MessageBuilder("conversation.ended_changed_worlds")
            .withDefault("<prefix_warning>Conversation ended because you changed worlds.")
            .build();
    public static final Message CONVERSATION_ENDED_WALKED_AWAY = new MessageBuilder("conversation.ended_walked_away")
            .withDefault("<prefix_warning>Conversation ended because you walked away.")
            .build();

    // Reports
    public static final Message REPORT_NONE_TO_DISPLAY = new MessageBuilder("report.none_to_display")
            .withDefault("<prefix_negative>There are no reports to display.")
            .build();
    public static final Message REPORT_TYPE_SELECTED = new MessageBuilder("report.type_selected")
            .withDefault("<prefix>You've selected {type}. Enter your feedback about the last NPC message.")
            .withPlaceholder("type")
            .build();
    public static final Message REPORT_SELECT_TYPE = new MessageBuilder("report.select_type")
            .withDefault("<prefix_negative>Please click one of the report types above to continue.")
            .build();
    public static final Message REPORT_SUBMITTED = new MessageBuilder("report.submitted")
            .withDefault("<prefix>Thank you, your report has been submitted. Type '{cancel}' to end the conversation.")
            .withPlaceholder("cancel")
            .build();
    public static final Message REPORT_DELETED = new MessageBuilder("report.deleted")
            .withDefault("<prefix>Report successfully deleted.")
            .build();

    // Admin
    public static final Message CMD_USAGE = new MessageBuilder("archgpt.usage")
            .withDefault("<prefix>Usage: <cmd_syntax>/archgpt <arg>argument</arg></cmd_syntax>")
            .build();
    public static final Message BROADCAST_CMD_USAGE = new MessageBuilder("archgpt.usage")
            .withDefault("<prefix_warning>Usage: <cmd_syntax>/archgpt broadcast <arg>npcName</arg> <arg>message</arg></cmd_syntax>")
            .build();
    public static final Message SETNPC_CMD_USAGE = new MessageBuilder("archgpt.setnpc-usage")
            .withDefault("<prefix_warning>Usage: <cmd_syntax>/archgpt setnpc <arg>npcname</arg> <arg>prompt</arg></cmd_syntax>")
            .build();
    public static final Message RESETMEMORY_CMD_USAGE = new MessageBuilder("archgpt.resetnpc-usage")
            .withDefault("<prefix_warning>Usage: <cmd_syntax>/archgpt reset-npc-memory <arg>npcName</arg></cmd_syntax>")
            .build();

    public static final Message CLOUD_NOT_CONFIGURED = new MessageBuilder("archgpt.cloud-not-configured")
            .withDefault("<prefix_warning>Google NLP is enabled, but google-cloud-key.json is not set up! Configure the file in your storage folder and restart the server.")
            .build();
    public static final Message RELOAD_SUCCESS = new MessageBuilder("archgpt.reload")
            .withDefault("<prefix>ArchGPT successfully reloaded!")
            .build();
    public static final Message DEBUG_MODE = new MessageBuilder("archgpt.debugmode")
            .withDefault("<prefix_warning>Debug mode is now {toggle ? 'enabled' : 'disabled'}")
            .withPlaceholder("toggle")
            .build();
    public static final Message VERSION_INFO = new MessageBuilder("archgpt.version")
            .withDefault("<prefix_warning>Server version: <gray>{server-ver}</gray>\nArchGPT version: <gray>{plugin-ver}</gray>\nJava version: <gray>{java-ver}</gray>")
            .withPlaceholder("server-ver")
            .withPlaceholder("plugin-ver")
            .withPlaceholder("java-ver")
            .build();
    public static final Message CLEAR_STORAGE_SUCCESS = new MessageBuilder("archgpt.clearconversations.success")
            .withDefault("<prefix>All conversation history successfully deleted.")
            .build();
    public static final Message CLEAR_STORAGE_ERROR = new MessageBuilder("archgpt.clearconversations.error")
            .withDefault("<prefix_negative>There was an error clearing conversation history.")
            .build();

    // NPC stuff
    public static final Message NPC_NOT_FOUND = new MessageBuilder("npc.not-found")
            .withDefault("<prefix_negative>NPC '{name}' not found.")
            .withPlaceholder("name")
            .build();
    public static final Message NPC_MEMORY_RESET = new MessageBuilder("npc.memory-reset")
            .withDefault("<prefix>Memory for NPC '{npc:name}' has been reset.")
            .withPlaceholder("npc")
            .build();
    public static final Message NPC_PROMPT_UPDATED = new MessageBuilder("npc.prompt-updated")
            .withDefault("<prefix>Prompt for {npc:name} updated successfully.")
            .withPlaceholder("npc")
            .build();
}
