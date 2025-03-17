package com.wiley.cms.cochrane.services;

import com.wiley.cms.cochrane.cmanager.CochraneCMSProperties;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.FilePathBuilder;
import com.wiley.cms.cochrane.cmanager.FilePathCreator;
import com.wiley.cms.cochrane.cmanager.data.record.RecordEntity;
import com.wiley.cms.cochrane.cmanager.data.record.RecordManifest;
import com.wiley.cms.cochrane.cmanager.entitymanager.AbstractManager;
import com.wiley.cms.cochrane.repository.IRepository;
import com.wiley.cms.cochrane.repository.RepositoryFactory;
import com.wiley.cms.cochrane.utils.Constants;
import com.wiley.tes.util.Logger;
import org.apache.commons.lang.StringUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Type comments here.
 *
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @version 1.0
 */
public class ImageLinksHelper {
    private static final Logger LOG = Logger.getLogger(ImageLinksHelper.class);
    private static final String FIGURE_GROUP_OPEN_TAG = "<figureGroup>";
    private static final String FIGURE_GROUP_CLOSE_TAG = "</figureGroup>";
    private static final String XML_EXT = ".xml";
    private static final String PNG_EXT = ".png";
    private static final String IMAGE_T_DIR = "/image_t";
    private static final String IMAGE_N_DIR = "/image_n";
    private static final Integer ISSUE_ID_POS = 0;
    private static final Integer DB_NAME_POS = 1;
    private static final Integer REC_NAME_POS = 2;

    IRepository rps = RepositoryFactory.getRepository();

    private String filePath;

    public ImageLinksHelper(String filePath) {
        this.filePath = filePath;

        if ("/".equals(filePath.substring(0, 1))) {
            this.filePath = filePath.replaceFirst("/", "");
        }
    }

    public String checkImageLinks(String data) {
        String str = data.replaceAll("image_a/a", "image_n/n");
        str = str.replaceAll("\\.tif", PNG_EXT);

        if (str.indexOf(FIGURE_GROUP_OPEN_TAG) == -1) {
            return str;
        }
        String header = str.substring(0, str.indexOf(FIGURE_GROUP_OPEN_TAG));
        String figureGroup = str.substring(str.indexOf(FIGURE_GROUP_OPEN_TAG),
                str.indexOf(FIGURE_GROUP_CLOSE_TAG) + FIGURE_GROUP_CLOSE_TAG.length());
        String footer = str.substring(str.indexOf(FIGURE_GROUP_CLOSE_TAG) + FIGURE_GROUP_CLOSE_TAG.length(),
                str.length());

        List<String> uris = getImageList();

        //second replace abstract files link to real
        for (String uri : uris) {
            String file = uri.substring(uri.lastIndexOf("/") + 1, uri.length());
            String fileName = file.substring(0, file.lastIndexOf("."));

            figureGroup = figureGroup.replaceFirst(fileName + PNG_EXT, file);
        }

        str = header + figureGroup + footer;
        return str;
    }

    private void walkDirs(String dir, String path, List<String> uris) {
        File[] files = rps.getFilesFromDir(dir);
        if (files == null) {
            return;
        }
        for (File file : files) {
            String newName = path + "/" + file.getName();
            if (file.isDirectory()) {
                walkDirs(file.getAbsolutePath(), newName, uris);
                continue;
            }

            if (newName.contains(IMAGE_T_DIR) || newName.contains(IMAGE_N_DIR)
                        || newName.contains(Constants.JATS_FIG_DIR_SUFFIX)) {
                uris.add(newName);
            }
        }
    }

    public List<String> getImageListTemporary() {
        List<String> uris = new ArrayList<String>();
        try {
            String path =
                    CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.PREFIX_REPOSITORY)
                            + StringUtils.substringAfter(filePath,
                                CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.PREFIX_REPOSITORY))
                                .replace(XML_EXT, "");
            walkDirs(path, path, uris);
        } catch (Exception e) {
            LOG.error(e, e);
        }

        return uris;
    }

    public List<String> getImageListEntire() {
        List<String> uris = new ArrayList<String>();
        try {
            String dbName = StringUtils.substringBetween(filePath,
                    CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.PREFIX_REPOSITORY) + "/", "/entire");
            String recordName = StringUtils.substringAfterLast(filePath, "/").replace(XML_EXT, "");
            String path = FilePathCreator.getFilePathForEnclosureEntire(dbName, recordName, "");
            walkDirs(path, path, uris);
        } catch (Exception e) {
            LOG.error(e, e);
        }

        return uris;
    }

    public List<String> getImageListPrevious() {
        List<String> uris = new ArrayList<String>();
        try {
            String recordName = StringUtils.substringAfterLast(filePath, "/").replace(XML_EXT, "");

            //String prev = CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.PREV_CDSR_REPOSITORY);
            //int versionIdx = filePath.indexOf(prev) + prev.length();
            //int version = Integer.decode(filePath.substring(versionIdx, filePath.indexOf("/", versionIdx)));

            Integer version = FilePathBuilder.cutOffPreviousVersion(filePath, false);

            String path = FilePathCreator.getPreviousFilePathForEnclosure(version, recordName, "");
            walkDirs(path, path, uris);
        } catch (Exception e) {
            LOG.error(e, e);
        }

        return uris;
    }

    public List<String> getImageList() {
        List<String> retval;
        if (filePath.contains("entire")) {
            retval = getImageListEntire();
        } else if (filePath.contains("previous")) {
            retval = getImageListPrevious();
        } else if (filePath.contains("temp")) {
            retval = getImageListTemporary();
        } else {
            String[] parts = FilePathCreator.getSplitedUri(filePath);
            int issueId = Integer.parseInt(parts[ISSUE_ID_POS]);
            String dbName = parts[DB_NAME_POS];
            String recName = parts[REC_NAME_POS];
            retval = new ArrayList<String>();

            RecordEntity entity = AbstractManager.getResultStorage().getRecord(issueId, dbName, recName);
            if (entity == null) {
                LOG.error("Failed to get image list. RecordEntity wasn't found by attributes {"
                        + issueId + "; " + dbName + "; " + recName + "}");
                return retval;
            }

            List<String> uris = new RecordManifest(issueId, dbName, recName,
                    entity.getRecordPath()).getUris(Constants.SOURCE);
            for (String uri : uris) {
                if (uri.contains(IMAGE_N_DIR) || uris.contains(IMAGE_T_DIR)) {
                    retval.add(uri);
                }
            }
        }
        return retval;
    }
}
