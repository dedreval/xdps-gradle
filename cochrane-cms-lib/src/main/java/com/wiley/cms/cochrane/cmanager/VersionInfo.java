package com.wiley.cms.cochrane.cmanager;

import com.wiley.cms.cochrane.utils.Constants;
import org.apache.commons.lang.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * @author <a href='mailto:gkhristich@wiley.ru'>Galina Khristich</a>
 * Date: 06-Nov-2007
 */
public class VersionInfo {
    private static final String OPEN_STR = "Version: ";
    private static final String CLOSE_STR = ", Commit";

    private VersionInfo() {
    }

    public static String getVersion(boolean editorialApi) throws IOException {
        String[] versionParts = getVersionParts();
        return versionParts[0] + (editorialApi ? versionParts[2] : versionParts[1]) + versionParts[3];
    }

    private static String[] getVersionParts() throws IOException {
        String version = new BufferedReader(new InputStreamReader(
                VersionInfo.class.getResourceAsStream("/META-INF/version"))).readLine();
        String versionNumbers = StringUtils.substringBetween(version, OPEN_STR, CLOSE_STR);
        if (StringUtils.isEmpty(versionNumbers)) {
            return getPartsIfNoVersion(version);
        }

        return getParts(version, versionNumbers);
    }

    private static String[] getParts(String version, String versionNumbers) {
        String[] versionNumParts = versionNumbers.split("[;,]", 2);

        // cms version goes first, editorial api version always goes second
        String cmsVersion = versionNumParts[0].isEmpty() ? Constants.NA : versionNumParts[0];
        String editorialApiVersion;
        if (versionNumParts.length < 2) {
            editorialApiVersion = Constants.NA;
        } else {
            editorialApiVersion = versionNumParts[1].isEmpty() ? Constants.NA : versionNumParts[1];
        }

        return new String[]{
            StringUtils.substringBefore(version, versionNumbers),
            cmsVersion,
            editorialApiVersion,
            StringUtils.substringAfter(version, versionNumbers)
        };
    }

    private static String[] getPartsIfNoVersion(String version) {
        return new String[]{
            StringUtils.substring(version, 0, version.indexOf(OPEN_STR) + OPEN_STR.length()),
            Constants.NA,
            Constants.NA,
            StringUtils.substring(version, version.indexOf(CLOSE_STR))
        };
    }
}
