package com.wiley.cms.cochrane.services;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import com.wiley.cms.cochrane.cmanager.CochraneCMSProperties;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.repository.RepositoryFactory;
import com.wiley.tes.util.Logger;

/**
 * @author <a href='mailto:gkhristich@wiley.ru'>Galina Khristich</a>
 *         Date: 22-Jun-2007
 */
public class CommonUIDataList {
    private static List<String> fileList;
    private static List<String> dirList;
    private static final Logger LOG = Logger.getLogger(CommonUIDataList.class);

    private CommonUIDataList() {

    }

    public static List<String> getCommonUIFileList() {
        if (fileList != null) {
            return fileList;
        }
        fileList = new ArrayList<String>();
        String place = RepositoryFactory.getRepository().getRepositoryPlace()
                + "/" + CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.COMMON_UI_FILES);

        try {
            for (File file : getFilesFromDir(place)) {
                if (!file.isDirectory()) {
                    fileList.add(file.getName());
                }
            }
        } catch (URISyntaxException e) {
            LOG.error(e, e);
        }
        return fileList;
    }


    public static List<String> getCommonUIDirList() {
        if (dirList != null) {
            return dirList;
        }
        dirList = new ArrayList<String>();
        String place = RepositoryFactory.getRepository().getRepositoryPlace()
                + "/" + CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.COMMON_UI_FILES);

        try {
            for (File file : getFilesFromDir(place)) {
                if (file.isDirectory()) {
                    dirList.add(file.getName());
                }
            }
        } catch (URISyntaxException e) {
            LOG.error(e, e);
        }
        return dirList;
    }

    public static String getCommonUIDataPath() {
        return CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.COMMON_UI_FILES);
    }


    private static File[] getFilesFromDir(String dir) throws URISyntaxException {
        File[] files = new File(new URI(dir)).listFiles();

        return (files == null ? new File[0] : files);
    }

}
