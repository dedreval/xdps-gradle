package com.wiley.tes.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import com.wiley.cms.cochrane.cmanager.CochraneCMSProperties;

/**
 * @author <a href='mailto:ikirov@wiley.com'>Igor Kirov</a>
 * @version 24.09.2009
 */
public class EntitiesDepository {
    private static final Logger LOG = Logger.getLogger(EntitiesDepository.class);
    private static Map<String, String> entToNumber = null;

    private EntitiesDepository() {
    }

    private static synchronized void loadEntToNum() {
        String dir = CochraneCMSProperties.getProperty("cms.resources.default.ent2num.location");
        String[] fileNames = CochraneCMSProperties.getProperty("cms.resources.default.ent2num.filelist").split(",");
        entToNumber = new HashMap<String, String>();
        for (String fileName : fileNames) {
            try {
                URL url = new URI(dir + fileName).toURL();
                InputStream is = url.openStream();
                loadEntToNumber(is);
                is.close();
            } catch (Exception e) {
                LOG.error("Failed to load entities from " + dir + fileName);
            }
        }
    }

    private static synchronized void loadEntToNumber(InputStream istream) throws IOException {
        BufferedReader br = null;
        final String entityTag = "<!ENTITY ";
        final String planeMarker = "%plane1D;";

        try {
            br = new BufferedReader(new InputStreamReader(istream));
            String str;
            while ((str = br.readLine()) != null) {
                if (!str.startsWith(entityTag)) {
                    continue;
                }
                int start = str.indexOf(" ", entityTag.length());
                if (start == -1) {
                    continue;
                }
                String key = str.substring("<!ENTITY".length(), start).trim();

                int plane = str.indexOf(planeMarker);
                String value;
                if (plane != -1) {
                    plane = plane + planeMarker.length();
                    value = "&#x1D" + str.substring(plane, str.indexOf("\"", plane + 1));
                } else {
                    start = str.indexOf("\"", key.length() + 1);
                    if (start == -1) {
                        continue;
                    }
                    int end = str.indexOf("\"", start + 1);
                    value = str.substring(start + 1, end);
                }
                entToNumber.put(key, value);
            }
        } finally {
            if (br != null) {
                br.close();
            }
        }
    }

    public static synchronized String getEntNumber(String entity) {
        if (entToNumber == null) {
            loadEntToNum();
        }
        return entToNumber.get(entity);
    }
}
