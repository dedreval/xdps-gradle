package com.wiley.cms.cochrane.cmanager.entitymanager;

import java.io.File;
import java.util.HashMap;;
import java.util.Map;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 11/28/2017
 */
public class RevmanSource {
    public File revmanFile = null;
    public final Map<String, File> taFiles = new HashMap<>();
    public String group = null;
    public String groupPath = null;
    public String abcRevmanDirPath;

    public RevmanSource() {
    }

    public RevmanSource(String abcRevmanDirPath) {
        this.abcRevmanDirPath = abcRevmanDirPath;
    }

    public void setGroup(String group, String groupPath) {
        this.group = group;
        this.groupPath = groupPath;
    }

    public boolean isExist() {
        return group != null;
    }
}
