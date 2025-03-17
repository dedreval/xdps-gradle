package com.wiley.tes.util;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 7/3/2020
 */
public class ExceptionParser {
    private static final Logger LOG = Logger.getLogger(ExceptionParser.class);

    private ExceptionParser() {
    }

    public static String buildMessage(Throwable ex) {
        return buildMessage(ex, null);
    }

    public static String buildMessage(Throwable ex, String postfix) {
        String lastMsg = formatMessage(ex);
        Throwable cause = getCause(ex);
        if (postfix != null) {
            lastMsg += postfix;
        }
        if (cause == null) {
            return lastMsg;
        }

        StringBuilder sb = new StringBuilder(lastMsg).append("\nInternal errors:");
        try {
            while (cause != null) {
                if (!lastMsg.contains(cause.getClass().getName()) || !lastMsg.contains(cause.getMessage())) {
                    lastMsg = formatMessage(cause);
                    sb.append("\n\n").append(lastMsg);
                }
                cause = getCause(cause);
            }
        } catch (Throwable tr) {
            LOG.error(tr);
        }
        return sb.toString();
    }

    private static String formatMessage(Throwable ex) {
        return String.format("[%s]  %s", ex.getClass().getName(), ex.getMessage());
    }

    private static Throwable getCause(Throwable ex) {
        Throwable cause = ex.getCause();
        return cause != null && cause != ex ? cause : null;
    }
}
