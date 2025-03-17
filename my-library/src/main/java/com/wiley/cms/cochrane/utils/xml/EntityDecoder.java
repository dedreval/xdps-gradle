package com.wiley.cms.cochrane.utils.xml;

/**
 * Type comments here.
 *
 * @author <a href='mailto:ademidov@wiley.ru'>Andrey Demidov</a>
 * @version 1.0
 */
public class EntityDecoder {

    private static final String AMP_ENTITY = "&amp;";
    private static final int AMP_ENTITY_LENGTH = AMP_ENTITY.length();

    private EntityDecoder() {
    }

    public static String encodeEntities(final String source) {
        StringBuilder buf = new StringBuilder(source.length());
        int curPos = 0;
        int tagPos = source.indexOf("&", curPos);
        while (tagPos >= 0) {
            buf.append(source.substring(curPos, tagPos));
            buf.append(AMP_ENTITY);
            curPos = tagPos + 1;
            tagPos = source.indexOf("&", curPos);
        }
        buf.append(source.substring(curPos));
        return buf.toString();
    }


    /**
     * Decode entities back.
     *
     * @param source the XML with encoded entities
     * @return the XML with original entities
     */
    public static String decodeEntities(final String source) {
        StringBuilder buf = new StringBuilder(source.length());
        int curPos = 0;
        int tagPos = source.indexOf("&", curPos);
        while (tagPos >= 0) {
            buf.append(source.substring(curPos, tagPos));
            buf.append("&");
            curPos = tagPos + AMP_ENTITY_LENGTH;
            tagPos = source.indexOf("&", curPos);
        }
        buf.append(source.substring(curPos));
        return buf.toString();
    }

}
