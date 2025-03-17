package com.wiley.cms.cochrane.utils;

import java.util.ArrayList;
import java.util.List;

import com.wiley.cms.cochrane.cmanager.MessageSender;
import com.wiley.tes.util.Logger;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 10/20/2018
 */
@SuppressWarnings("ALL")
public class MessageBuilder implements java.io.Serializable {
    public static final int MAX_MESSAGES_LINES = 100;

    private static final Logger LOG = Logger.getLogger(MessageBuilder.class);
    private static final long serialVersionUID = 1L;

    private static final String SUB_MESSAGES_DIVIDER = "_QQ_";

    private final List<String> messages = new ArrayList<>();

    public void addMessage(String message) {
        messages.add(message);
    }

    public void addSubMessages(String mainMessage, String... messages) {
        addMessage(mainMessage);
        for (String msg: messages) {
            addSubMessage(msg);
        }
    }

    public void addSubMessage(String message) {
        setLastMessage(getLastMessage() + SUB_MESSAGES_DIVIDER + message);
    }

    public void sendMessages(String dbName, String pubType) {

        int size = messages.size();
        if (messages.size() == 1) {
            LOG.warn(String.format("message container has only title: %s for %s.%s", getTitle(), dbName, pubType));
            return;
        }

        String title = getTitle();
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < size; i++) {

            String msg = messages.get(i);
            String[] subMessages = msg.split(SUB_MESSAGES_DIVIDER);
            if (subMessages.length > 1) {
                appendSubMessages(dbName, title, subMessages, sb);
            } else {
                sb.append(msg).append("\n\n");
            }
        }
        if (sb.length() > 0) {
            MessageSender.sendReport(title, dbName, sb.toString());
        }

        messages.clear();
    }

    private String getTitle() {
        return messages.get(0);
    }

    private String getLastMessage() {
        return messages.get(messages.size() - 1);
    }

    private void setLastMessage(String message) {
        messages.set(messages.size() - 1, message);
    }

    public boolean isEmpty() {
        return messages.isEmpty();
    }

    private static void appendSubMessages(String dbName, String title, String[] subMessages, StringBuilder main) {
        int size = subMessages.length;

        if (size <= MAX_MESSAGES_LINES) {
            for (String msg: subMessages) {
                main.append(msg).append("\n");
            }
            main.append("\n");

        } else {
            String firstMessage = subMessages[0] + "\n";
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i < size; i++) {
                sb.append(subMessages[i]).append("\n");
                if (i % MAX_MESSAGES_LINES == 0) {
                    MessageSender.sendReport(title, dbName, firstMessage + sb);
                    sb = new StringBuilder();
                }
            }
            if (sb.length() > 0) {
                MessageSender.sendReport(title, dbName, firstMessage + sb);
            }
        }
    }
}
