package com.wiley.cms.cochrane.cmanager.publish.util;

import com.wiley.cms.cochrane.cmanager.MessageSender;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author <a href='mailto:aasadulin@wiley.com'>Alexandr Asadulin</a>
 * @version 07.11.2012
 */
public class GenerationErrorCollector {

    private final NotificationSender notifSender;
    private final Map<String, Message> messages = new LinkedHashMap<>();


    public GenerationErrorCollector(String generateName) {
        this(new MessageSenderWrapper(generateName));
    }

    public GenerationErrorCollector(NotificationSender notifSender) {
        this.notifSender = notifSender;
    }

    public void addError(NotificationLevel level, String message) {
        addErrorWithGroupingEntries(level, message, Collections.emptyList());
    }

    public void addErrorWithGroupingEntries(NotificationLevel level, String messageBase, List<String> entries) {
        addMessageEntries(level, messageBase, entries);
    }

    public void addErrorWithGroupingEntries(NotificationLevel level, String messageBase, String entry) {
        addMessageEntry(level, messageBase, entry);
    }

    private void addMessageEntry(NotificationLevel level, String messageBase, String entry) {
        messages.computeIfAbsent(messageBase, f -> new Message(level, messageBase)).entries.add(entry);
    }

    private void addMessageEntries(NotificationLevel level, String messageBase, List<String> entries) {
        messages.computeIfAbsent(messageBase, f -> new Message(level, messageBase)).entries.addAll(entries);
    }

    public void sendErrors() {
        if (messages.isEmpty()) {
            return;
        }

        Map<NotificationLevel, StringBuilder> notificationMessages = fillNotificationMessages();
        sendNotificationMessages(notificationMessages);
    }

    private Map<NotificationLevel, StringBuilder> fillNotificationMessages() {
        Map<NotificationLevel, StringBuilder> msgHolders = new HashMap<>();
        for (Message message : messages.values()) {
            addMessageToHolder(message, msgHolders);
        }
        return msgHolders;
    }

    private void addMessageToHolder(Message message, Map<NotificationLevel, StringBuilder> msgHolders) {
        StringBuilder msgHolder = chooseMessageHolder(msgHolders, message.severity);
        String msgText = message.getText();
        msgHolder.append(msgText).append(";\n");
    }

    private StringBuilder chooseMessageHolder(Map<NotificationLevel, StringBuilder> msgHolders,
                                              NotificationLevel severity) {
        if (!msgHolders.containsKey(severity)) {
            msgHolders.put(severity, new StringBuilder());
        }
        return msgHolders.get(severity);
    }

    private void sendNotificationMessages(Map<NotificationLevel, StringBuilder> notificationMessages) {
        for (NotificationLevel level : notificationMessages.keySet()) {
            StringBuilder message = notificationMessages.get(level);
            sendMessage(message, level);
        }
    }

    private void sendMessage(StringBuilder message, NotificationLevel level) {
        notifSender.sendMessage(message.toString(), level);
    }

    /**
     *
     */
    public enum NotificationLevel {

        WARN(MessageSender.MSG_TITLE_GENERATION_WARNINGS),
        ERROR(MessageSender.MSG_TITLE_GENERATION_ERRORS),
        INFO(MessageSender.MSG_TITLE_GENERATION_INFO);

        private final String messageId;

        NotificationLevel(String messageId) {
            this.messageId = messageId;
        }

        public String getMessageId() {
            return messageId;
        }
    }

    /**
     *
     */
    private class Message {

        private final NotificationLevel severity;
        private final String messageBase;
        private final List<String> entries;

        Message(NotificationLevel severity, String messageBase) {
            this.severity = severity;
            this.messageBase = messageBase;
            this.entries = new ArrayList<>();
        }

        String getText() {
            StringBuilder text = messageBase != null ? new StringBuilder(messageBase) : new StringBuilder();
            if (!entries.isEmpty()) {
                addEntries(text);
            }
            return text.toString();
        }

        private void addEntries(StringBuilder text) {
            text.append(": ").append(entries);
        }
    }

    /**
     *
     */
    public interface NotificationSender {

        void sendMessage(String message, NotificationLevel level);
    }

    /**
     *
     */
    private static class MessageSenderWrapper implements NotificationSender {

        private final String type;

        private MessageSenderWrapper(String type) {
            this.type = type;
        }

        public void sendMessage(String message, NotificationLevel level) {
            Map<String, String> msgParams = new HashMap<>();
            msgParams.put(MessageSender.MSG_PARAM_DATABASE, type);
            msgParams.put(MessageSender.MSG_PARAM_REPORT, message);
            MessageSender.sendMessage(level.getMessageId(), msgParams);
        }
    }
}
