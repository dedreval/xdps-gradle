package com.wiley.tes.util;

import com.wiley.cms.cochrane.cmanager.CochraneCMSProperties;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author <a href='mailto:aasadulin@wiley.com'>Alexander Asadulin</a>
 * @version 16.10.2013
 */
public class UserFriendlyMessageBuilder {

    private static final Logger LOG = Logger.getLogger(UserFriendlyMessageBuilder.class);

    private static String[] msgMappings;

    private UserFriendlyMessageBuilder() {}

    public static void load() {
        msgMappings = parse(CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.MESSAGE_MAPPING));
    }

    public static void load(String msgMappingsStr) {
        msgMappings = parse(msgMappingsStr);
    }

    private static String[] parse(String msgMappingsStr) {
        String[] msgMappingArr;
        if (StringUtils.isEmpty(msgMappingsStr)) {
            msgMappingArr = ArrayUtils.EMPTY_STRING_ARRAY;
        } else {
            msgMappingArr = msgMappingsStr.split("#");
            if (ArrayUtils.isEmpty(msgMappingArr) || msgMappingArr.length % 2 != 0) {
                msgMappingArr = ArrayUtils.EMPTY_STRING_ARRAY;
            }
        }

        return msgMappingArr;
    }

    public static String build(Exception e) {
        StringBuilder strb = new StringBuilder();
        Throwable cause = e;

        do {
            if (StringUtils.isEmpty(cause.getMessage())) {
                strb.append(cause.toString());
            } else {
                strb.append(cause.getMessage());
            }
            strb.append("; ");

            cause = cause.getCause();
        } while (cause != null);

        return build(strb.substring(0, strb.length() - 2));
    }

    public static String build(String msg) {
        if (msgMappings == null) {
            load();
        }

        String builtMsg = "";
        try {
            for (int i = 0; i < msgMappings.length; i += 2) {
                Pattern p = Pattern.compile(msgMappings[i]);
                Matcher m = p.matcher(msg);
                if (m.find()) {
                    builtMsg = m.replaceAll(msgMappings[i + 1]);

                    break;
                }
            }
            if (StringUtils.isEmpty(builtMsg)) {
                builtMsg = msg;
                LOG.trace("Corresponding user-friendly message didn't find for message {" + msg + "}");
            }
        } catch (Exception e) {
            builtMsg = msg;
            LOG.error("Failed to build user-friendly message from message {" + msg + "}", e);
        }

        return builtMsg;
    }

}
