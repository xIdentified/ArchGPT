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
            .withDefault("<npc_name_color><npc_name>: <message_color><message>")
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

    // Reload
    public static final Message RELOAD_SUCCESS = new MessageBuilder("reload.success")
            .withDefault("<positive>ArchGPT successfully reloaded!")
            .build();
    public static final Message RELOAD_CMD_USAGE = new MessageBuilder("reload.usage")
            .withDefault("<positive>Usage: /archgpt reload")
            .build();
}
