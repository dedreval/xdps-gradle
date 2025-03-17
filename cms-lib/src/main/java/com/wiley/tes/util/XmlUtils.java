package com.wiley.tes.util;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.Normalizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Type comments here.
 *
 * @author <a href='mailto:ademidov@wiley.ru'>Andrey Demidov</a>
 * @version 1.0
 */
public class XmlUtils {

    public static final String XML_HEAD = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";
    public static final String XML_HEAD2 = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>";
    public static final String UTF_8 = "UTF-8";
    public static final String US_ASCII = "US-ASCII";
    public static final String CONTAINER = "CONTAINER";

    private static final int COUNT_OF_BYTES_REQUIRED_TO_READ_XML_DECL = 128;
    private static final Pattern XML_ENCODING_DECLARATION_PTR =
            Pattern.compile("<\\?xml\\s+version.+encoding=\"(.+)\"[^</>]*\\?>");
    private static final Pattern XML_ENCODING_END_PTR = Pattern.compile("\"");

    private static final int MAGIC_130 = 130;
    private static final int MAGIC_20 = 20;
    private static final int AMP = 38;

    private XmlUtils() {
    }

    public static String normalize(String input) {
        if (input != null) {
            return Normalizer.normalize(input, Normalizer.Form.NFD);
        }
        return null;
    }

    public static String cutXMLHead(String xml) {
        return xml.replace(XML_HEAD, "");
    }

    public static String cutStandaloneXMLHead(String xml) {
        return xml.replace(XML_HEAD2, "");
    }

    public static String cutDocTypeDeclaration(String xml) {
        String ret = xml;
        int posDTD = xml.indexOf("<!DOCTYPE");
        if (posDTD > 0) {
            int posEnd = xml.indexOf('>', posDTD) + 1;
            ret = xml.substring(0, posDTD) + xml.substring(posEnd).trim();
        }
        return ret;
    }

    public static String unescapeXmlEntities(String str) {
        return str != null && str.indexOf(AMP) >= 0 ? StringEscapeUtils.unescapeXml(str) : str;
    }

    public static String unescapeHtmlEntities(String str) {
        return str != null && str.indexOf(AMP) >= 0  ? unescapeXmlEntities(StringEscapeUtils.unescapeHtml(str)) : str;
    }

    public static String escapeElementEntities(String str) {
        // Process HTML and XML entities.
        StringBuilder buffer;
        char ch;
        int ich;
        String entity;

        buffer = null;
        for (int i = 0; i < str.length(); i++) {
            ch = str.charAt(i);

            switch (ch) {
                case '<':
                    entity = "&lt;";
                    break;
                case '>':
                    entity = "&gt;";
                    break;
                case '&':
                    entity = "&amp;";
                    break;
                default:
                    entity = defaultCh(ch);
                    break;
            }

            if (buffer == null) {
                if (entity != null) {
                    // An entity occurred, so we'll have to use StringBuilder
                    // (allocate room for it plus a few more entities).
                    buffer = new StringBuilder(str.length() + MAGIC_20);
                    // Copy previous skipped characters and fall through
                    // to pickup current character
                    buffer.append(str.substring(0, i));
                    buffer.append(entity);
                }
            } else {
                if (entity == null) {
                    buffer.append(ch);
                } else {
                    buffer.append(entity);
                }
            }
        }

        // If there were any entities, return the escaped characters
        // that we put in the StringBuilder. Otherwise, just return
        // the unmodified input string.
        return (buffer == null) ? str : buffer.toString();
    }

    private static String defaultCh(char ch) {
        String entity;
        int ich = (int) ch;
        if (ich > MAGIC_130) {
            entity = "&#" + ich + ';';
        } else {
            entity = null;
        }
        return entity;
    }

    /**
     * Retrieves content of xml file using encoding extracted from xml declaration or autodetected
     * @param xmlPath absolute path to the file
     * @return string content of xml file
     * @throws IOException
     */
    public static String getXmlContentBasedOnEstimatedEncoding(String xmlPath) throws IOException {
        String content;
        BufferedInputStream bis = null;
        try {
            bis = new BufferedInputStream(new FileInputStream(xmlPath));
            content = IOUtils.toString(bis, getEncoding(bis));
        } finally {
            IOUtils.closeQuietly(bis);
        }

        return content;
    }

    /**
     * Returns encoding retrieved from xml declaration or autodetected using method described
     * in following document http://www.w3.org/TR/2000/REC-xml-20001006#sec-guessing if external
     * encoding information is not present
     * @param bis InputStream which encoding should be defined
     * @return canonical name of encoding of xml document
     * @throws IOException
     */
    public static String getEncoding(InputStream bis) throws IOException {
        byte[] buf = new byte[COUNT_OF_BYTES_REQUIRED_TO_READ_XML_DECL];
        bis.mark(0);
        bis.read(buf);
        bis.reset();

        String encoding = getEstimatedEncoding(buf);
        Matcher m = XML_ENCODING_DECLARATION_PTR.matcher(new String(buf, encoding));
        if (!m.find()) {
            return encoding;
        }

        String tmp = m.group(m.groupCount());
        if (StringUtils.isNotEmpty(tmp)) {

            encoding = tmp;
            Matcher m2 = XML_ENCODING_END_PTR.matcher(tmp);
            if (m2.find()) {
                encoding = tmp.substring(0, m2.end() - 1);
            }
        }

        return encoding;
    }

    public static String asHTML(String xml) {
        return "<textarea>" + xml + "</textarea>";
    }

    //CHECKSTYLE:OFF MagicNumber
    private static String getEstimatedEncoding(byte[] buf) {
        int encMark = 0xFF000000 & (buf[0] << 24)
                | 0x00FF0000 & (buf[1] << 16)
                | 0x0000FF00 & (buf[2] << 8)
                | 0x000000FF & buf[3];
        String estimatedEnc;
        if ((encMark & 0xFFFF0000) == 0xFEFF0000) {
            estimatedEnc = "UnicodeBig";
        } else if ((encMark & 0xFFFF0000) == 0xFFFE0000) {
            estimatedEnc = "UnicodeLittle";
        } else if (encMark == 0x003C003F) {
            estimatedEnc = "UTF-16BE";
        } else if (encMark == 0x3C003F00) {
            estimatedEnc = "UTF-16LE";
        } else if (encMark == 0x4C6FA794) {
            estimatedEnc = "IBM1047";
        } else {
            estimatedEnc = UTF_8;
        }

        return estimatedEnc;
    }
    //CHECKSTYLE:ON MagicNumber
}
