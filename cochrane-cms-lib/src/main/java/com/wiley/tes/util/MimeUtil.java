package com.wiley.tes.util;

import java.util.HashMap;
import java.util.Map;


/**
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @version 1.0
 */

public class MimeUtil {

    static Map<String, String> mime;

    private static final String MIME_FORMAT_XML = "text/xml";
    private static final String MIME_FORMAT_HTML = "text/html";
    private static final String MIME_FORMAT_EXCEL = "application/excel";


    private MimeUtil() {

    }

    static {
        mime = new HashMap<String, String>();
        mime.put("xml", MIME_FORMAT_XML);
        mime.put("rm5", MIME_FORMAT_XML);
        mime.put("htm", MIME_FORMAT_HTML);
        mime.put("html", MIME_FORMAT_HTML);
        mime.put("js", "application/javascript");
        mime.put("pdf", "application/pdf");
        mime.put("png", "image/png");
        mime.put("jpg", "image/jpeg");
        mime.put("gif", "image/gif");
        mime.put("svg", "image/svg");
        mime.put("xsl", MIME_FORMAT_EXCEL);
        mime.put("csv", MIME_FORMAT_EXCEL);
    }

    public static String getMimeType(String file) {
        String result = mime.get(file.substring(file.lastIndexOf(".") + 1, file.length()));
        if (result == null) {
            result = "application/octet-stream";
        }
        return result;
    }
}
