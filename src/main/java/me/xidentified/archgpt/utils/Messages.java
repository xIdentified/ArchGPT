package me.xidentified.archgpt.utils;

import de.cubbossa.translations.Message;
import de.cubbossa.translations.MessageBuilder;

public class Messages {

    // General
    public static final Message GENERAL_CMD_PLAYER_ONLY = new MessageBuilder("general.must_be_player")
            .withDefault("<negative>Only players can use this command.")
            .build();
    public static final Message GENERAL_NO_PERM = new MessageBuilder("general.no_perm")
            .withDefault("<negative>You don't have sufficient permission")
            .build();
    public static final Message GENERAL_CMD_NO_PERM = new MessageBuilder("general.cmd.no_perm")
            .withDefault("<negative>You don't have permission to use this command.")
            .build();
    public static final Message GENERAL_PLAYER_NOT_FOUND = new MessageBuilder("general.player_not_found")
            .withDefault("<negative>Player not found: <name>")
            .build();
    public static final Message RELOAD_SUCCESS = new MessageBuilder("general.reload_success")
            .withDefault("<positive>ArchGPT successfully reloaded!")
            .build();
    public static final Message MSG_TOO_SHORT = new MessageBuilder("general.msg_too_short")
            .withDefault("<negative>Your message is too short, use at least <size> characters.")
            .withPlaceholder("size")
            .build();

    // Conversation
    public static final Message CONVERSATION_STARTED = new MessageBuilder("conversation.started")
            .withDefault("<yellow>Conversation started with <npc>. Type 'cancel' to exit.")
            .withPlaceholder("npc")
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
            .withDefault("You've selected <type>. Enter your feedback about the last NPC message.")
            .withPlaceholder("type")
            .build();
    public static final Message REPORT_SUBMITTED = new MessageBuilder("report.submitted")
            .withDefault("<positive>Thank you, your report has been submitted. Type 'cancel' to end the conversation.")
            .build();
    public static final Message REPORT_DELETED = new MessageBuilder("report.deleted")
            .withDefault("<positive>Report successfully deleted.")
            .build();
}
