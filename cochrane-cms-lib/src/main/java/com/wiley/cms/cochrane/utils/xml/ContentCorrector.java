package com.wiley.cms.cochrane.utils.xml;

import java.text.MessageFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 27.11.12
 */
public class ContentCorrector {

    private static final String[] MARKUP_TAGS = {"B", "I", "A"};

    private static final String[] OPEN_TAGS = open(MARKUP_TAGS);
    private static final String[] CLOSE_TAGS = close(MARKUP_TAGS);
    private static final String[] EMPTY_TAGS = empty(MARKUP_TAGS);

    private static final String REGEXP = "{0}\\s*{1}";

    private ContentCorrector() {
    }

    /**
     * change "<B/>" to " "
     * change "<B></B>" to " "
     * @param xml  The content to repair
     * @return
     */
    public static String repairHtmlMarkups(String xml) {

        if (!checkHtmlMarkups(xml)) {
            return xml;
        }

        String result = xml;
        for (int i = 0; i < MARKUP_TAGS.length; i++) {
            result = result.replaceAll(MessageFormat.format(REGEXP, OPEN_TAGS[i], CLOSE_TAGS[i]), " ");
            result = result.replaceAll(EMPTY_TAGS[i], " ");
        }
        return result;
    }

    private static boolean checkHtmlMarkups(String xml) {

        boolean ret = false;
        for (int i = 0; i < MARKUP_TAGS.length; i++) {
            Pattern p = Pattern.compile(MessageFormat.format(REGEXP, OPEN_TAGS[i], CLOSE_TAGS[i]));
            Matcher m = p.matcher(xml);
            if (m.find()) {
                ret = true;
                break;
            }

            p = Pattern.compile(EMPTY_TAGS[i]);
            m = p.matcher(xml);
            if (m.find()) {
                ret = true;
                break;
            }
        }
        return ret;
    }

    private static String[] empty(String[] tags) {

        String[] result = new String[tags.length];
        for (int i = 0; i < tags.length; i++) {
            result[i] = "<" + tags[i] + "/>";
        }
        return result;
    }

    private static String[] open(String[] tags) {

        String[] result = new String[tags.length];
        for (int i = 0; i < tags.length; i++) {
            result[i] = "<" + tags[i] + ">";
        }
        return result;
    }

    private static String[] close(String[] tags) {

        String[] result = new String[tags.length];
        for (int i = 0; i < tags.length; i++) {
            result[i] = "</" + tags[i] + ">";
        }
        return result;
    }
}
