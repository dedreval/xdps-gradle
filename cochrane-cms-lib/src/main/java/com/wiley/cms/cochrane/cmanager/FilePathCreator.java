package com.wiley.cms.cochrane.cmanager;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;

import com.wiley.cms.cochrane.cmanager.contentworker.ArchiePackage;
import com.wiley.cms.cochrane.cmanager.contentworker.RevmanPackage;
import com.wiley.cms.cochrane.cmanager.data.rendering.RenderingPlan;
import com.wiley.cms.cochrane.utils.Constants;
import com.wiley.cms.process.entity.DbEntity;
import com.wiley.tes.util.Extensions;

/**
 * @author <a href='mailto:gkhristich@wiley.ru'>Galina Khristich</a>
 *         Date: 23-Jan-2007
 */
public class FilePathCreator {

    public static final String XML_EXT = ".xml";
    public static final String PDF_EXT = ".pdf";
    public static final String ZIP_EXT = ".zip";
    public static final String SEPARATOR = "/";
    public static final String INPUT_DIR = "/input";
    //public static final String TMP_PATH = "tmp/";
    public static final String REVMAN_DIR = "revman/";
    public static final String ML3G_DIR = "/ml3g";
    public static final String PREVIOUS_DIR = "/previous";
    public static final String ABSTRACTS_TMP_DIR = "/abstracts-tmp";

    public static final String SRC_PATH = "/src/";

    //private static final int ISSUE_POS = 1;
    private static final int DB_POS = 2;
    //private static final int PACKAGE_POS = 3;
    //private static final int RECORD_POS = 4;
    private static final int THREE = 3;

    //private static final String COPY_DIR = "/copy";
    //private static final String ABSTRACTS_COPY_DIR = "abstracts_copy/";

    //private static final String PREFIX_FOR_PREVIOUS = CochraneCMSProperties
    //        .getProperty(CochraneCMSPropertyNames.PREFIX_REPOSITORY) + "/" + CochraneCMSProperties
    //        .getProperty(CochraneCMSPropertyNames.PREV_CDSR_REPOSITORY);
    //private static final int ENTIRE_RECORD_POS = 4;
    //private static final int PREVIOUS_RECORD_POS = 5;
    //private static final String TABLE_N_DIR = "/table_n/";
    private static final String ENTIRE_DIR = "/entire";
    private static final String PROTOCOLS_DELETED_DIR = "/protocols_deleted";
    private static final String CLSYSREV_DIR = "/clsysrev";
    //private static final String PREVIOUS_ABSTRACTS_DIR = "/previous_abstracts";
    private static final String PUBLISH_DIR = "/publish/";
    private static final String PUBLISH_AUTO_DIR = "whenready/";

    private FilePathCreator() {
    }

    public static String buildPackagePath(String timestamp, String dbName) {
        return SEPARATOR + timestamp + SEPARATOR + dbName + SEPARATOR;
    }

    public static String buildSBNPackagePath(String path) {
        return SEPARATOR + Constants.SBN + SEPARATOR + path + SEPARATOR;
    }

    public static String buildPackagePathRecord(String packagePath, String recordName) {
        return packagePath + recordName + SEPARATOR + recordName + Extensions.XML;
    }

    public static String buildPackagePathRecordFile(String packagePath, String recordName, String fileName) {
        return packagePath + fileName.substring(fileName.indexOf(recordName));
    }

    /**
     * @return String by template /folder_name/
     */
    public static String getFixedManifestFolder() {
        return "/manifests/";
    }

    public static String getFilePathToIssue(int issueId, String dbName) {
        return CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.PREFIX_REPOSITORY) + SEPARATOR + issueId
                + SEPARATOR + dbName;
    }

    /*public static String getFilePathToEntireDb(String dbName) {
        String ret = null;
        if (dbName.equals(CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.CLSYSREV))) {
            ret = CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.PREFIX_REPOSITORY) + SEPARATOR
                    + CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.ENTIRE_CLSYSREV);
        } else if (dbName.equals(CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.CLEDITORIAL))) {
            ret = CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.PREFIX_REPOSITORY) + SEPARATOR
                    + CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.ENTIRE_CLEDITORIAL);
        } else if (dbName.equals(CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.CLCENTRAL))) {
            ret = CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.PREFIX_REPOSITORY) + SEPARATOR
                    + CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.ENTIRE_CLCENTRAL);
        }

        return ret;
    }*/

    public static String getFilePathToSourceCopy(int issueId, String dbName, String name) {
        final String copyPath = "/copy/src/";
        if (dbName.equals(CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.CLCENTRAL))) {
            return CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.PREFIX_REPOSITORY) + "/" + issueId + "/"
                    + dbName + copyPath + name.substring(name.length() - THREE) + "/" + name + XML_EXT;
        }

        return CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.PREFIX_REPOSITORY) + "/" + issueId + "/"
                + dbName + copyPath + name + XML_EXT;
    }

    /**
     * Returns path to source file in entire DB
     *
     * @param db   - db name
     * @param name - record name
     * @return path to source file
     */
    public static String getFilePathToSourceEntire(String db, String name) {
        String ret = FilePathBuilder.getPathToEntireSrc(db);
        if (db.equals(CochraneCMSPropertyNames.getCentralDbName())) {
            ret = ret + name.substring(name.length() - THREE) + "/" + name + Extensions.XML;
        }  else {
            ret = ret + name + Extensions.XML;
        }
        return ret;
    }

    public static String getFilePathToJatsEntireRecord(String db, String recName) {
        return FilePathBuilder.JATS.getPathToEntireRecord(db, recName, recName + Extensions.XML);
    }

    public static String getFilePathToJatsPreviousRecord(Integer version, String recName) {
        return FilePathBuilder.JATS.getPathToPreviousRecord(version, recName, recName + Extensions.XML);
    }

    public static String getFilePathToIssue(int issueId) {
        return CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.PREFIX_REPOSITORY) + SEPARATOR + issueId;
    }

    public static String getFilePathToIssue(String pckTimestamp, int issueId, String db) {
        return CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.PREFIX_REPOSITORY) + SEPARATOR
                + issueId + SEPARATOR + db + SEPARATOR + pckTimestamp + SEPARATOR;
    }

    public static String buildSubFolder(String name) {
        return name.substring(name.length() - THREE) + SEPARATOR;
    }

    public static String getFilePathToSource(String pckTimestamp, String issueId, String db, String name) {
        if (db.equals(CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.CLCENTRAL))) {
            return CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.PREFIX_REPOSITORY) + "/"
                    + issueId + "/" + db + "/"
                    + pckTimestamp + "/" + buildSubFolder(name) + name + XML_EXT;
        } else {
            return CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.PREFIX_REPOSITORY) + "/"
                    + issueId + "/" + db + "/"
                    + pckTimestamp + "/" + name + XML_EXT;
        }
    }

    public static String getFilePathToUrlsCentral(String issueId, String db,
                                                  String timestamp, int i) {
        return CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.PREFIX_REPOSITORY) + "/" + issueId + "/" + db
                + "/xmlurls/" + timestamp + "/" + i + XML_EXT;
    }

    public static String getFilePathToUrlsCentralEntire(String db, String timestamp, int i) {
        return CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.PREFIX_REPOSITORY) + "/" + db
                + "/entire/xmlurls/" + timestamp + "/" + i + XML_EXT;
    }

    //public static String getManifestPath(String issueId, String db, String name) {
    //    if (db.equals(CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.CLCENTRAL))) {
    //        return CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.PREFIX_REPOSITORY) + "/"
    //                + issueId + MANIFESTS_DIR
    //                + db + "/" + name.substring(name.length() - THREE) + "/" + name + XML_EXT;
    //    } else {

    //        return CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.PREFIX_REPOSITORY) + "/"
    //                + issueId + MANIFESTS_DIR
    //                + db + "/" + MANIFEST_PREFIX + name + XML_EXT;
    //    }
    //}

    //public static String getFilePathForCDSRRawSourceEntire(String db, String name) {
        //String ret = getFilePathToEntireDb(db);
    //    String ret = FilePathBuilder.getPathToEntireSrc(db);
    //    return getPreviousRawFilePath(name, ret + name);
    //}

    public static String getFilePathForEnclosureEntire(String db, String name, String tail) {
        //String ret = getFilePathToEntireDb(db);

        String ret = FilePathBuilder.getPathToEntireSrc(db);
        if (db.equals(CochraneCMSPropertyNames.getCDSRDbName())
                    || db.equals(CochraneCMSPropertyNames.getCentralDbName())) {
            ret = tail.equals("") ? ret + name : ret + name + "/" + tail;
        } else if (db.equals(CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.CLEDITORIAL))) {
            ret = tail.equals("") ? ret + name : ret +  "/" + tail;
        }
        return ret;
    }

    public static String getRenderedFilePathEntireByPlan(String planPath, String db, String name, String url) {
        String path = null;
        if (db.equals(CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.CLCENTRAL))) {
            path = planPath + getClearFilePath(url);
        } else {
            path = planPath + name + SEPARATOR + getClearFilePath(url);
        }
        return path;
    }

    public static String getRenderedFilePathPrevious(int version, String name, RenderingPlan rndPlan, String url) {
        return getRenderedDirPathPrevious(version, name, rndPlan) + getClearFilePath(url);
    }

    //public static String getDirPathForTranslatedAbstracts() {
    //    return CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.PREFIX_REPOSITORY)
    //            + "/" + CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.CLSYSREV)
    //            + ABSTRACTS_DIR + "/";
    //}

    //public static String getDirPathForTranslatedAbstract(String basedir, String language) {
    //    return basedir + "/" + language + "/";
    //}

    //public static String getFilePathForTranslatedAbstract(String record, String language) {
    //    return CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.PREFIX_REPOSITORY)
    //            + "/" + CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.CLSYSREV)
    //            + ABSTRACTS_DIR + "/" + language + "/" + record + XML_EXT;
    //}

    //todo replace with FilePathBuilder.getPathToSrs(WML21)TranslatedRecord
    public static String getFilePathForTranslatedAbstract(String basedir, String language, String record,
                                                          boolean dirByRecord) {
        return basedir + (dirByRecord ? record + SEPARATOR + language : language) + SEPARATOR + record + XML_EXT;
    }

    //public static String getDirPathForUnhandledTranslatedAbstract() {
    //    return CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.PREFIX_REPOSITORY)
    //            + "/" + CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.CLSYSREV)
    //            + "/translated_unhandled/";
    //}

    //public static String getDirPathForDeletedAbstracts(int issueId, String dbName) {
    //    return CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.PREFIX_REPOSITORY) + "/" + issueId + "/"
    //            + dbName + ABSTRACTS_COPY_DIR;
    //}

    public static String getRevmanMetadataSource(String srcPath) {
        return srcPath + SEPARATOR + RevmanPackage.METADATA_SOURCE;
    }

    public static String getRevmanPackageInputDirPath(String srcPath) {
        return srcPath + INPUT_DIR + SEPARATOR;
    }

    public static String getRevmanTopicSource(String groupName) {
        return groupName + FilePathCreator.SEPARATOR + Constants.TOPICS_SOURCE;
    }

    public static String getFilePathForEnclosure(String pckTimestamp, String issueId, String db, String name,
                                                 String tail) {
        return CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.PREFIX_REPOSITORY)
                + SEPARATOR + issueId + SEPARATOR + db + SEPARATOR + pckTimestamp + SEPARATOR + name + SEPARATOR
                + tail;
    }

    public static String getFilePathForEnclosure(String pckTimestamp, int issueId, String db, String[] tails) {

        StringBuilder sb = new StringBuilder();

        sb.append(CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.PREFIX_REPOSITORY)).append(SEPARATOR).
                append(issueId).append(SEPARATOR).append(db).append(SEPARATOR).append(pckTimestamp);

        if (tails != null) {
            for (String tail : tails) {

                sb.append(SEPARATOR);
                sb.append(tail);
            }
        }

        return sb.toString();
    }

    public static String getRenderedFilePath(String issueId,
                                             String db,
                                             String name,
                                             RenderingPlan rndPlan,
                                             String url) {
        return db.equals(CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.CLCENTRAL))
                ? getRenderedDirPath(issueId, db, rndPlan) + "/" + getClearFilePath(url)
                : getRenderedDirPath(issueId, db, name, rndPlan) + getClearFilePath(url);
    }

    public static String getRenderedDirPath(String issueId, String db, String name, RenderingPlan rndPlan) {
        String baseDirPath = getRenderedDirPath(issueId, db, rndPlan);
        String articleDir = (db.equals(CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.CLCENTRAL)))
                ? name.substring(name.length() - THREE) + "/" + name
                : name;
        return baseDirPath + "/" + articleDir + "/";
    }

    public static String getRenderedDirPath(String issueId, String db, RenderingPlan rndPlan) {
        String planDirName = getRenderingPlanDirName(rndPlan, true);
        return CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.PREFIX_REPOSITORY)
                + "/" + issueId
                + "/" + db
                + "/" + planDirName;
    }

    //public static String getCopyDirPath(String sourceUri) {
    //    String ret = null;
    //    String[] parts = getSplitedUri(sourceUri);
    //    if (parts[1].equals(CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.CLSYSREV))) {
    //        ret = CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.PREFIX_REPOSITORY) + "/" + parts[0] + "/"
    //                + parts[1] + COPY_DIR;
    //    } else if (parts[1].equals(CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.CLEDITORIAL))) {
    //        ret = CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.PREFIX_REPOSITORY) + "/" + parts[0] + "/"
    //                + parts[1] + COPY_DIR;
    //    } else if (parts[1].equals(CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.CLCENTRAL))) {
    //        ret = CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.PREFIX_REPOSITORY) + "/" + parts[0] + "/"
    //                + parts[1] + COPY_DIR;
    //    }
    //    return ret;
    //}

    public static String getEntireDirPath(String dbName) {
        return CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.PREFIX_REPOSITORY) + "/" + dbName
                + ENTIRE_DIR;
    }

    public static String getRenderedDirPath(String sourceUri, RenderingPlan rndPlan) {
        String[] parts = getSplitedUri(sourceUri);
        String planDirName = getRenderingPlanDirName(rndPlan, true);
        if (parts[DB_POS - 1].equals(CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.CLCENTRAL))) {
            return CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.PREFIX_REPOSITORY)
                    + "/" + parts[0]
                    + "/" + parts[1]
                    + "/" + planDirName
                    + "/" + parts[2].substring(parts[2].length() - THREE)
                    + "/" + parts[2] + "/";
        } else {
            return CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.PREFIX_REPOSITORY)
                    + "/" + parts[0]
                    + "/" + parts[1]
                    + "/" + planDirName
                    + "/" + parts[2] + "/";
        }
    }

    public static String getRenderingPlanDirName(RenderingPlan rndPlan, boolean inIssue) {
        String name;
        if (inIssue) {
            name = "rendered-" + rndPlan.planName;
        } else {
            if (rndPlan == RenderingPlan.HTML) {
                name = "rnd_html";
            } else if (rndPlan == RenderingPlan.PDF_TEX) {
                name = "rnd_pdf";
            } else {
                name = "rnd_pdf_fop";
            }
        }
        return name;
    }

    public static String getClearFilePath(String path) {

        String clearPath = path.substring(path.indexOf("/") + 1, path.length());
        clearPath = clearPath.substring(clearPath.indexOf("/") + 1, clearPath.length());
        clearPath = clearPath.substring(clearPath.indexOf("/") + 1, clearPath.length());

        return clearPath;
    }

    public static URI getUri(String path) throws URISyntaxException {
        return new URI(CochraneCMSPropertyNames.getWebLoadingUrl() + "DataTransferer/" + path);
    }

    //public static String getManifestPrefix(int issueId, String db) {
    //    if (db.equals(CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.CLCENTRAL))) {
    //        return CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.PREFIX_REPOSITORY) + "/" + issueId
    //            + MANIFESTS_DIR + db + "/";
    //    } else {
    //        return CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.PREFIX_REPOSITORY) + "/" + issueId
    //            + MANIFESTS_DIR + db + "/" + MANIFEST_PREFIX;
    //    }
    //}

    //public static String getManifestPostfix() {
    //    return XML_EXT;
    //}

    /**
     * It splits a file uri for details required.
     * @param filePath
     * @return a string array [<version | issue>, <db name>, <record name>, <package name>]
     */
    public static String[] getSplitedUri(String filePath) {
        String[] res;
        if (FilePathBuilder.isEntirePath(filePath)) {
            res = FilePathBuilder.getEntireUriParts(filePath);
        } else if (FilePathBuilder.isPreviousPath(filePath)) {
            res = FilePathBuilder.getPreviousUriParts(filePath);
        } else {
            res = FilePathBuilder.getUriParts(filePath);
        }
        return res;
    }

    /*public static String[] getSplitedUri(String filePath) {
        String[] res;
        String[] parts = filePath.split("/");
        if (filePath.contains("entire/")) {
            res = new String[]{"0", parts[DB_POS - 1], parts[ENTIRE_RECORD_POS].replace(XML_EXT, ""), ""};
        } else if (filePath.contains("previous/")) {
            String prev = "clsysrev/previous/rel000";
            int versionIdx = filePath.indexOf(prev) + prev.length();
            res = new String[]{filePath.substring(versionIdx, filePath.indexOf("/", versionIdx)), "clsysrev",
                    parts[PREVIOUS_RECORD_POS].replace(XML_EXT, ""), ""};
        } else {
            if (parts[DB_POS].equals(CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.CLCENTRAL))) {
                res = new String[]{parts[ISSUE_POS], parts[DB_POS],
                    parts[RECORD_POS + 1].replace(XML_EXT, ""), parts[PACKAGE_POS]};
            } else {
                res = new String[]{parts[ISSUE_POS], parts[DB_POS],
                    parts[RECORD_POS].replace(XML_EXT, ""), parts[PACKAGE_POS]};
            }
        }
        return res;
    }*/

    //public static String getPackageDir(String filePath) {
    //    String[] parts = filePath.split("/");
    //    return filePath.substring(0, filePath.indexOf(parts[PACKAGE_POS]) + parts[PACKAGE_POS].length());
    //}

    public static String getPreviousSrcPath(String name, int version) {
        return FilePathBuilder.getPathToPreviousSrc(version) + name + Extensions.XML;
    }

    public static String getPreviousFilePathForEnclosure(int version, String name, String tail) {
        return FilePathBuilder.getPathToPreviousSrc(version) + name + SEPARATOR + tail;
    }

    public static String getPreviousHtmlPath(String name, int version) {
        return FilePathBuilder.getPathToPreviousHtml(version) + name;
    }

    public static String getPreviousPdfPath(String name, int version) {
        return FilePathBuilder.PDF.getPathToPreviousPdf(version) + name;
    }

    public static String getPreviousPdfFopPath(String name, int version) {
        return FilePathBuilder.PDF.getPathToPreviousPdfFop(version) + name;
    }

    public static String getPreviousRenderedDirPath(String recName, int version, RenderingPlan rndPlan) {
        String rndDirName = getRenderingPlanDirName(rndPlan, false);
        return (FilePathBuilder.getPathToPrevious(version) + rndDirName + SEPARATOR + recName);
    }

    public static String getPreviousRecordDir(String name, String versionDir) {
        return versionDir + SRC_PATH + name;
    }

    //public static String getPreviousRawFilePath(String recordName, String srcPath) {
    //    return srcPath + TABLE_N_DIR + recordName + "RawData.xml";
    //}

    //public static String getPreviousRevManStatsDataPath(String name, String srcPath) {
    //    return srcPath + TABLE_N_DIR + name + "StatsDataOnly.rm5";
    //}

    public static String getPreviousMl3gXmlPath(String name, int version) {
        return FilePathBuilder.ML3G.getPathToPreviousMl3g(version) + name + Extensions.XML;
    }

    public static String getPreviousMl3gAssetsPath(String name, int version) {
        return FilePathBuilder.ML3G.getPathToPreviousMl3g(version) + name + Extensions.ASSETS;
    }

    public static String getRenderedDirPathEntire(String dbName, String recordName, RenderingPlan rndPlan) {
        String planDirName = getRenderingPlanDirName(rndPlan, false);
        String ret = FilePathBuilder.getPathToEntire(dbName) + planDirName + SEPARATOR;

        if (recordName != null && recordName.length() > 1) {
            ret = addRecordNameToPath(ret, recordName, dbName) + SEPARATOR;
        }
        return ret;
    }

    public static String getRenderedDirPathPrevious(int version, String name, RenderingPlan plan) {
        String base;
        if (plan == RenderingPlan.HTML) {
            base = FilePathBuilder.getPathToPreviousHtml(version);
        } else if (plan == RenderingPlan.PDF_FOP) {
            base = FilePathBuilder.PDF.getPathToPreviousPdfFop(version);
        } else {
            base = FilePathBuilder.PDF.getPathToPreviousPdf(version);
        }
        return base + name + SEPARATOR;
        //return CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.PREFIX_REPOSITORY) + "/"
        //        + CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.PREV_CDSR_REPOSITORY) + version + "/"
        //        + (plan.contains(HTML) ? IRenderingService.RND_HTML : IRenderingService.RND_PDF) + "/" + name + "/";
    }

    public static String getRenderedDirPathPrevious(String dbName, String recordPath, String recordName,
                                                    RenderingPlan rndPlan) {

        String planDirName = getRenderingPlanDirName(rndPlan, false);
        String ret = FilePathBuilder.cutOffPreviousVersionPath(recordPath) + planDirName + SEPARATOR;

        if (recordName != null && recordName.length() > 1) {
            ret = addRecordNameToPath(ret, recordName, dbName) + SEPARATOR;
        }
        return ret;
    }

    public static String getRenderedDirPathEntire(String recordPath, RenderingPlan rndPlan) {
        String planDirName = getRenderingPlanDirName(rndPlan, false);
        String[] records = recordPath.split("/");
        return records[0] + "/" + records[1] + "/" + records[2] + "/" + planDirName;
    }

    public static String getRenderedFilePathEntire(String recordPath,
                                                   RenderingPlan rndPlan,
                                                   String dbName,
                                                   String recordName) {
        String result = getRenderedDirPathEntire(recordPath, rndPlan);

        return addRecordNameToPath(result, recordName, dbName);
    }

    public static String getFilePathForMl3gXml(int issueId, String dbName, String name) {
        return getFilePathForMl3g(issueId, dbName, name, Extensions.XML);
    }

    public static String getFilePathForMl3gAssets(int issueId, String dbName, String name) {
        return getFilePathForMl3g(issueId, dbName, name, Extensions.ASSETS);
    }

    private static String getFilePathForMl3g(int issueId, String dbName, String name, String extension) {
        String basePath = CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.PREFIX_REPOSITORY)
                + "/" + issueId + "/" + dbName + ML3G_DIR;
        return addRecordNameToPath(basePath, name, dbName) + extension;
    }

    public static String getFilePathForMl3gXmlCopy(int issueId, String dbName, String name) {
        return getFilePathForMl3gCopy(issueId, dbName, name, Extensions.XML);
    }

    public static String getFilePathForMl3gAssetsCopy(int issueId, String dbName, String name) {
        return getFilePathForMl3gCopy(issueId, dbName, name, Extensions.ASSETS);
    }

    private static String getFilePathForMl3gCopy(int issueId, String dbName, String name, String extension) {
        String basePath = FilePathBuilder.getPathToBackup(issueId, dbName) + "ml3g";
        return addRecordNameToPath(basePath, name, dbName) + extension;
    }

    public static String getFilePathForEntireMl3gXml(String dbName, String name) {
        return getFilePathForEntireMl3g(dbName, name, Extensions.XML);
    }

    public static String getFilePathForEntireMl3gAssets(String dbName, String name) {
        return getFilePathForEntireMl3g(dbName, name, Extensions.ASSETS);
    }

    private static String getFilePathForEntireMl3g(String dbName, String name, String extension) {
        String basePath = getEntireDirPath(dbName) + ML3G_DIR;
        return addRecordNameToPath(basePath, name, dbName) + extension;
    }

    public static String addRecordNameToPath(String basePath, String recordName, String dbName) {
        if (dbName.equals(CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.CLCENTRAL))) {
            return basePath + SEPARATOR + recordName.substring(recordName.length() - THREE) + SEPARATOR + recordName;
        } else {
            return basePath + SEPARATOR + recordName;
        }
    }

    public static String mergeRecordNameToPath(String basePath, String recordName, String dbName) {
        if (dbName.equals(CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.CLCENTRAL))) {
            return basePath + recordName.substring(recordName.length() - THREE) + SEPARATOR + recordName;
        } else {
            return basePath + recordName;
        }
    }

    public static String getRenderedDirPathCopy(int issueId, String dbName, RenderingPlan rndPlan) {
        String rndDirName = getRenderingPlanDirName(rndPlan, false);
        return CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.PREFIX_REPOSITORY)
                + "/" + issueId
                + "/" + dbName
                + "/copy/" + rndDirName;
    }

    //public static String getFilePathToDeletedProtocolsEntire() {
    //    return CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.PREFIX_REPOSITORY)
    //        + CLSYSREV_DIR
    //        + PROTOCOLS_DELETED_DIR
    //        + ENTIRE_DIR;
    //}

    public static String getFilePathToDeletedRecordsEntire(String dbName) {
        return CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.PREFIX_REPOSITORY)
            + "/" + dbName
            + PROTOCOLS_DELETED_DIR
            + ENTIRE_DIR;
    }

    public static String getFilePathToDeletedProtocolsPrevious() {
        return CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.PREFIX_REPOSITORY)
            + CLSYSREV_DIR
            + PROTOCOLS_DELETED_DIR
            + PREVIOUS_DIR;
    }

    //public static String getDirPathForDeletedAbstractsPrevious() {
    //    return CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.PREFIX_REPOSITORY)
    //        + CLSYSREV_DIR
    //        + PREVIOUS_ABSTRACTS_DIR;
    //}

    public static String getFilePathToRevmanReviewEntire(String recName, String groupName) {
        return getFilePathToRevmanReview(getDirPathForRevmanEntire(), recName, groupName);
    }

    public static String getFilePathToRevmanReview(String basedir, String recName, String groupName) {
        return getDirForRevmanReviews(basedir + groupName) + SEPARATOR + recName + Extensions.XML;
    }

    public static String getPathToPreviousRevmanReview(String recName, String groupName, int version) {
        return FilePathBuilder.getPathToPreviousRevmanSrc(version, groupName) + recName + Extensions.XML;
    }

    public static String getDirForRevmanReviews(String basedir) {
        return basedir + SEPARATOR + ArchiePackage.REVIEW_FOLDER;
    }

    public static String getInputDir(int issueId, String dbName) {
        return CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.PREFIX_REPOSITORY) + SEPARATOR + issueId
                + SEPARATOR + dbName + INPUT_DIR;
    }

    public static String getDirPathForRevmanEntire() {
        //return FilePathBuilder.getPathToEntire(CochraneCMSPropertyNames.getCDSRDbName()) +  REVMAN_DIR;
        return FilePathBuilder.getPathToEntireRevman();
    }

    //public static String getFilePathToReconvertedRevman(String basePath) {
    //    return basePath + CochraneCMSPropertyNames.getReconvertedRevmanDir();
    //}

    public static String getDirPathForPublish(String db, int issueId, boolean isAut) {

        if (issueId == DbEntity.NOT_EXIST_ID) {
                // this is entire path
            return CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.PREFIX_REPOSITORY) + SEPARATOR + db
                        + PUBLISH_DIR;
        }
        return CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.PREFIX_REPOSITORY) + SEPARATOR
                + issueId + SEPARATOR + db + PUBLISH_DIR + (isAut ? PUBLISH_AUTO_DIR : "");
    }

    public static String getDirPathForPublish(String db, int issueId, String exportTypeName, boolean isAut) {
        return getDirPathForPublish(db, issueId, isAut) + exportTypeName + SEPARATOR;
    }

    public static String getTerm2NumFilePath(String fileName) {
        String path = CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.RESOURCES_TERM2NUM_OUTPUT);
        path = path.replace("file:///", "");

        if (!path.contains(":")) { // UNIX SYSTEM
            path = File.separator + path;
        }

        return path + File.separator + fileName;
    }

    public static String getMeshtermRecordUpdatedFilePath(String dbName) {
        return getEntireDirPath(dbName) + SEPARATOR + CochraneCMSPropertyNames.getMeshtermRecordUpdateFileName();
    }

    public static String addWithSeparator(String base, String... adds) {
        StringBuilder sb = new StringBuilder();
        sb.append(base);
        for (String add: adds) {
            sb.append(SEPARATOR).append(add);
        }
        return sb.toString();
    }
}