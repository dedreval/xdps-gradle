package com.wiley.cms.cochrane.cmanager;

import com.wiley.cms.cochrane.cmanager.res.PathType;
import com.wiley.cms.cochrane.utils.Constants;
import com.wiley.tes.util.Extensions;
import com.wiley.tes.util.res.Res;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 4/1/2015
 */
public final class FilePathBuilder {

    private static final String RAW_DATA = "RawData";
    private static final String RAW_DATA_XML = "RawData.xml";
    private static final String STATS_DATA_RM5 = "StatsDataOnly.rm5";
    private static final String TABLE_DATA_PATH = "/table_n/";
    private static final String KEY_ISSUE   = "%issue%";
    private static final String KEY_DB      = "%database%";
    private static final String KEY_PACKAGE = "%package%";
    private static final String KEY_VERSION = "%version%";
    private static final String KEY_LANG    = "%lang%";
    private static final String KEY_RECORD  = "%record%";
    private static final String KEY_GROUP   = "%group%";
    private static final String[] KEYS = {KEY_ISSUE, KEY_DB, KEY_PACKAGE, KEY_GROUP};
    private static final String[] KEYS_CDSR = {KEY_ISSUE, KEY_GROUP};
    private static final String[] KEYS_PREV = {KEY_VERSION, KEY_GROUP, KEY_LANG};
    private static final String[] KEYS_ISSUE_PREV = {KEY_ISSUE, KEY_DB, KEY_VERSION, KEY_GROUP, KEY_LANG};
    private static final String[] KEYS_ENTIRE = {KEY_DB, KEY_LANG};
    private static final String PATH_DELIMITER = "/|\\\\";

    private FilePathBuilder() {
    }

    public static boolean isEntirePath(String path) {
        return path.contains(CachedPath.ENTIRE_ROOT.getLastPathKeyword());
    }

    public static boolean isPreviousPath(String path) {
        return path.contains(CachedPath.PREVIOUS_CDSR_ROOT.getLastPathKeyword());
    }

    public static Integer extractPreviousNumber(String version) {
        return Integer.valueOf(version.replaceAll("[^0-9]", ""));
    }

    public static String[] splitPath(String path) {
        return path.split(PATH_DELIMITER);
    }

    public static String buildHistoryDir(Integer version) {
        return String.format(Constants.REL, version);
    }

    public static String buildRawDataPathByUri(String recPath, String recName) {
        return replaceXmlExtension(recPath, TABLE_DATA_PATH + recName + RAW_DATA_XML);
    }

    public static String buildStatsDataPathByUri(String recPath, String recName) {
        return replaceXmlExtension(recPath, TABLE_DATA_PATH + recName + STATS_DATA_RM5);
    }

    public static String buildRawDataPathByDir(String dir, String recName) {
        return dir + FilePathCreator.SEPARATOR + recName + RAW_DATA_XML;
    }

    public static String replaceXmlExtension(String recPath, String replace) {
        return recPath.replaceFirst("\\.xml", replace);
    }

    public static String buildMetadataRecordName(String recName) {
        return recName + Constants.METADATA_SOURCE_SUFFIX + Extensions.XML;
    }

    public static boolean containsRawData(String path) {
        return path.contains(RAW_DATA);
    }
    
    /**
     * @param sourcePath  a full path to the source record
     * @return   a string array [0, <db name>, <record name>, ""]
     */
    public static String[] getEntireUriParts(String sourcePath) {
        int dbPos = CachedPath.ENTIRE_RECORD.getPathPosition(KEY_DB);
        int recordPos = CachedPath.ENTIRE_RECORD.getPathPosition(KEY_RECORD);
        String[] parts = splitPath(sourcePath);
        return new String[]{"0", parts[dbPos], parts[recordPos].replace(Extensions.XML, ""), ""};
    }

    /**
     * @param sourcePath  a full path to the source record
     * @return  a string array [<version>, <db name>, <record name>, ""]
     */
    public static String[] getPreviousUriParts(String sourcePath) {
        return getPreviousUriParts(sourcePath, CachedPath.PREVIOUS_RECORD);
    }

    private static String[] getPreviousUriParts(String sourcePath, CachedPath path) {
        int recordPos = path.getPathPosition(KEY_RECORD);
        String[] parts = splitPath(sourcePath);
        return new String[]{cutOffPreviousVersion(sourcePath, false).toString(),
                CochraneCMSPropertyNames.getCDSRDbName(), parts[recordPos].replace(Extensions.XML, ""), ""};
    }

    public static String[] getPreviousUriIssueParts(String sourcePath, CachedPath path) {
        String[] parts = splitPath(sourcePath);
        return new String[]{parts[path.getPathPosition(KEY_ISSUE)], CochraneCMSPropertyNames.getCDSRDbName(),
                parts[path.getPathPosition(KEY_RECORD)].replace(Extensions.XML, ""), ""};
    }

    /**
     * @param sourcePath a full path to the source record
     * @return  a string array [<issue>, <db name>, <record name>, <package name>]
     */
    public static String[] getUriParts(String sourcePath) {
        int issuePos = CachedPath.ISSUE_RECORD.getPathPosition(KEY_ISSUE);
        int dbPos = CachedPath.ISSUE_RECORD.getPathPosition(KEY_DB);
        int packPos = CachedPath.ISSUE_RECORD.getPathPosition(KEY_PACKAGE);
        int recPos = CachedPath.ISSUE_RECORD.getPathPosition(KEY_RECORD);

        String[] parts = splitPath(sourcePath);
        if (parts[dbPos].equals(CochraneCMSPropertyNames.getCentralDbName())) {
            recPos++;
        }
        return new String[]{parts[issuePos], parts[dbPos], parts[recPos].replace(Extensions.XML, ""), parts[packPos]};
    }

    /**
     * Trims the record path till the version directory (inclusive).
     * @param recordPath  a full path to the source record
     * @return   the "<repository root>/clsysrev/previous/<version>/ string
     */
    public static String cutOffPreviousVersionPath(String recordPath) {
        int startInd = getPathToPrevious().length();
        return recordPath.substring(0, recordPath.indexOf(FilePathCreator.SEPARATOR, startInd) + 1);
    }

    /**
     * @param recordPath  a full path to the source record
     * @return   the previous version folder's name
     */
    public static String cutOffPreviousVersionDir(String recordPath) {
        int startInd = getPathToPrevious().length();
        return recordPath.substring(startInd, recordPath.indexOf(FilePathCreator.SEPARATOR, startInd));
    }

    /**
     * Trims the version number from from the record path.
     * @param recordPath  a full path to the source record
     * @return   the truncated version number
     */
    public static Integer cutOffPreviousVersion(String recordPath, boolean ta) {
        String base = ta ? TR.getPathToPreviousTA() : getPathToPrevious();
        int versionIdx = base.length();
        int endInd = recordPath.indexOf(FilePathCreator.SEPARATOR, versionIdx);
        return extractPreviousNumber(recordPath.substring(versionIdx, endInd));
    }

    public static String getPathToEntire(String db) {
        return CachedPath.ENTIRE_ROOT.getPath(KEYS_ENTIRE, db);
    }

    public static String getPathToEntireExport(String db) {
        return CachedPath.ENTIRE_EXPORT.getPath(KEYS_ENTIRE, db);
    }

    public static String getPathToEntireReport(String db) {
        return CachedPath.ENTIRE_REPORT.getPath(KEYS_ENTIRE, db);
    }

    public static String getPathToEntirePublish(String db) {
        return CachedPath.ENTIRE_PUBLISH.getPath(KEYS_ENTIRE, db);
    }

    public static String getPathToEntireSrc(String db) {
        return CachedPath.ENTIRE_SRC.getPath(KEYS_ENTIRE, db);
    }

    public static String getPathToEntireSrcRecord(String db, String recName, boolean central) {
        return getPathToEntireSrc(db) + buildFileName(recName + Extensions.XML, recName, central);
    }

    public static String getPathToEntireSrcDir(String db, String recName, boolean central) {
        return getPathToEntireSrc(db) + buildFileName(recName, recName, central);
    }

    public static String getPathToSrcRecord(String basePath, String recName, boolean central) {
        return basePath + buildFileName(recName + Extensions.XML, recName, central);
    }

    public static String getPathToSrcDir(String basePath, String recName) {
        return basePath + recName;
    }

    public static String getPathToReconvertedEntireSrc(String dbName) {
        String specDir = CochraneCMSPropertyNames.getReconvertedRevmanDir();
        return !specDir.isEmpty() ? getPathToEntire(dbName) + specDir : getPathToEntireSrc(dbName);
    }

    public static String getPathToEntireHtml(String db) {
        return CachedPath.ENTIRE_HTML.getPath(KEYS_ENTIRE, db);
    }

    public static String getPathToEntireHtml(String db, String recName, boolean central) {
        return getPathToEntireHtml(db) + buildFolderPath(recName, central);
    }

    public static String getPathToEntireRevman() {
        return CachedPath.ENTIRE_REVMAN.getPath(KEYS_ENTIRE);
    }

    public static String getPathToEntireRevmanGroup(String group) {
        return CachedPath.ENTIRE_REVMAN_GROUP.getPath(KEYS_CDSR, null, group);
    }

    public static String getPathToEntireRevmanSrc(String group) {
        return CachedPath.ENTIRE_REVMAN_SRC.getPath(KEYS_CDSR, null, group);
    }

    public static String getPathToEntireRevmanRecord(String group, String recName) {
        return getPathToEntireRevmanSrc(group) + recName + Extensions.XML;
    }

    public static String getPathToEntireRevmanMetadata(String group, String recName) {
        return getPathToEntireRevmanSrc(group) + buildMetadataRecordName(recName);
    }

    public static String getPathToTopics(String group) {
        return CachedPath.ENTIRE_REVMAN_TOPIC.getPath(KEYS_CDSR, null, group);
    }

    public static String getPathToEntireSrcRecord(String db, String recName) {
        return getPathToEntireSrcRecordDir(db, recName) + Extensions.XML;
    }

    public static String getPathToEntireSrcRecordDir(String db, String recName) {
        return getPathToEntireSrc(db) + recName;
    }

    public static String getPathToEntireRawData(String db, String recName) {
        return getPathToEntireSrcRecordDir(db, recName) + TABLE_DATA_PATH + recName + RAW_DATA_XML;
    }

    public static String getPathToPrevious() {
        return CachedPath.PREVIOUS_CDSR_ROOT.getPath(KEYS_PREV);
    }

    public static String getPathToPrevious(Integer version) {
        return CachedPath.PREVIOUS_CDSR.getPath(KEYS_PREV, buildHistoryDir(version));
    }

    public static String getPathToPreviousSrc(Integer version) {
        return CachedPath.PREVIOUS_CDSR_SRC.getPath(KEYS_PREV, buildHistoryDir(version));
    }

    public static String getPathToReconvertedPreviousSrc(String versionFolder) {
        String specDir = CochraneCMSPropertyNames.getReconvertedRevmanDir();
        Integer version = extractPreviousNumber(versionFolder);
        return !specDir.isEmpty() ? getPathToPrevious(version) + specDir : getPathToPreviousSrc(version);
    }

    public static String getPathToPreviousSrcRecordDir(Integer version, String recName) {
        return getPathToPreviousSrc(version) + recName;
    }

    public static String getPathToPreviousSrcRecord(Integer version, String recName) {
        return getPathToPreviousSrcRecordDir(version, recName) + Extensions.XML;
    }

    public static String getPathToPreviousRawData(Integer version, String recName) {
        return getPathToPreviousSrcRecordDir(version, recName) + TABLE_DATA_PATH + recName + RAW_DATA_XML;
    }

    public static String getPathToPreviousStatsData(Integer version, String recName) {
        return getPathToPreviousSrcRecordDir(version, recName) + TABLE_DATA_PATH + recName + STATS_DATA_RM5;
    }

    public static String getPathToPreviousRevman(String versionFolder) {
        return getPathToPreviousRevman(extractPreviousNumber(versionFolder));
    }

    public static String getPathToPreviousRevman(Integer version) {
        return CachedPath.PREVIOUS_REVMAN.getPath(KEYS_PREV, buildHistoryDir(version));
    }

    public static String getPathToPreviousRevmanSrc(Integer version, String group) {
        return CachedPath.PREVIOUS_REVMAN_SRC.getPath(KEYS_PREV, buildHistoryDir(version), group);
    }

    public static String getPathToPreviousRevmanSrc(Integer issueId, Integer version, String group) {
        return CachedPath.ISSUE_PREVIOUS_REVMAN_SRC.getPath(KEYS_ISSUE_PREV, issueId, null,
                buildHistoryDir(version), group);
    }

    public static String getPathToPreviousRevmanRecord(Integer issueId, Integer version, String group, String name) {
        return getPathToPreviousRevmanSrc(issueId, version, group) + name + Extensions.XML;
    }

    public static String getPathToPreviousRevmanRecord(Integer version, String group, String name) {
        return getPathToPreviousRevmanSrc(version, group) + name + Extensions.XML;
    }

    public static String getPathToPreviousRevmanMetadata(Integer version, String group, String name) {
        return getPathToPreviousRevmanSrc(version, group) + buildMetadataRecordName(name);
    }

    public static String getPathToPreviousHtml(Integer version) {
        return CachedPath.PREVIOUS_CDSR_HTML.getPath(KEYS_PREV, buildHistoryDir(version));
    }

    public static String getPathToPreviousHtml(Integer version, String name) {
        return getPathToPreviousHtml(version) + name + FilePathCreator.SEPARATOR;
    }

    public static String getPathToPreviousHtml(Integer issueId, Integer version) {
        return CachedPath.ISSUE_PREVIOUS_HTML.getPath(KEYS_ISSUE_PREV, issueId, null, buildHistoryDir(version));
    }

    public static String getPathToPreviousHtml(Integer issueId, Integer version, String recName) {
        return getPathToPreviousHtml(issueId, version) + recName + FilePathCreator.SEPARATOR;
    }

    public static String getPathToIssueDb(Integer issueId, String db) {
        return CachedPath.ISSUE_DB.getPath(KEYS, issueId, db);
    }

    public static String getPathToIssueExport(Integer issueId, String db) {
        return CachedPath.ISSUE_EXPORT.getPath(KEYS, issueId, db);
    }

    public static String getPathToIssuePublish(Integer issueId, String db) {
        return CachedPath.ISSUE_PUBLISH.getPath(KEYS, issueId, db);
    }

    public static String getPathToIssuePublishWhenReady(Integer issueId, String db) {
        return CachedPath.ISSUE_PUBLISH_WHENREADY.getPath(KEYS, issueId, db);
    }

    public static String getPathToIssuePublish(Integer issueId, String db, String pubType, String fileName,
                                               boolean wr) {
        return  (wr ? getPathToIssuePublishWhenReady(issueId, db) : getPathToIssuePublish(issueId, db)) + pubType
                + FilePathCreator.SEPARATOR + fileName;
    }

    public static String getPathToReconvertedIssueDb(Integer issueId, String db) {
        return getPathToIssueDb(issueId, db) + CochraneCMSPropertyNames.getReconvertedRevmanDir();
    }

    public static String getPathToIssue(Integer issueId) {
        return CachedPath.ISSUE_ROOT.getPath(KEYS, issueId);
    }

    public static String getPathToIssueArchive(Integer issueId) {
        return CachedPath.ISSUE_ARCHIVE.getPath(KEYS, issueId);
    }

    public static String getPathToHtml(Integer issueId, String db) {
        return CachedPath.ISSUE_HTML.getPath(KEYS, issueId, db);
    }

    public static String getPathToHtml(Integer issueId, String db, String recName, boolean central) {
        return getPathToHtml(issueId, db) + buildFolderPath(recName, central);
    }

    public static String getPathToWML21RecordDir(Integer issueId, String db, String pckTimestamp, String recName) {
        return getPathToWML21(issueId, db, pckTimestamp) + recName;
    }

    public static String getPathToWML21Record(Integer issueId, String db, String pckTimestamp, String recName) {
        return getPathToWML21RecordDir(issueId, db, pckTimestamp, recName) + Extensions.XML;
    }

    public static String getPathToWML21(Integer issueId, String db, String pckTimestamp) {
        return CachedPath.ISSUE_ML21.getPath(KEYS, issueId, db, pckTimestamp);
    }

    public static String getPathToBackup(Integer issueId, String db) {
        return CachedPath.ISSUE_COPY.getPath(KEYS, issueId, db);
    }

    public static String getPathToBackupPrevious(Integer issueId, String db, Integer version) {
        return CachedPath.ISSUE_COPY_PREVIOUS.getPath(KEYS_ISSUE_PREV, issueId, db, buildHistoryDir(version));
    }

    public static String getPathToBackupSrc(Integer issueId, String db) {
        return CachedPath.ISSUE_COPY_SRC.getPath(KEYS, issueId, db);
    }

    public static String getPathToBackupSrcRecord(Integer issueId, String db, String recName, boolean central) {
        return getPathToBackupSrc(issueId, db) + buildFileName(recName + Extensions.XML, recName, central);
    }

    public static String getPathToBackupSrcRecordDir(Integer issueId, String db, String recName) {
        return getPathToBackupSrc(issueId, db) + recName;
    }

    public static String getPathToBackupStatsDir(Integer issueId, String db, String recName) {
        return getPathToBackupSrcRecordDir(issueId, db, recName) + FilePathCreator.SEPARATOR + Constants.TABLE_N_DIR;
    }

    public static String getPathToBackupFiguresDir(Integer issueId, String db, String recName) {
        return getPathToBackupSrcRecordDir(issueId, db, recName) + FilePathCreator.SEPARATOR + Constants.IMAGE_N_DIR;
    }

    public static String getPathToBackupThumbnailsDir(Integer issueId, String db, String recName) {
        return getPathToBackupSrcRecordDir(issueId, db, recName) + FilePathCreator.SEPARATOR + Constants.IMAGE_T_DIR;
    }

    public static String getPathToRevmanBackup(Integer issueId) {
        return CachedPath.ISSUE_REVMAN_COPY.getPath(KEYS_CDSR, issueId);
    }

    public static String getPathToRevmanBackupSrc(Integer issueId, String group) {
        return CachedPath.ISSUE_REVMAN_COPY_SRC.getPath(KEYS_CDSR, issueId, group);
    }

    public static String getPathToRevmanBackupRecord(Integer issueId, String group, String recName) {
        return getPathToRevmanBackupSrc(issueId, group) + recName + Extensions.XML;
    }

    public static String getPathToRevmanPackage(Integer issueId, String packageSid) {
        return CachedPath.ISSUE_REVMAN.getPath(KEYS, issueId, null, packageSid);
    }

    public static String getPathToIssuePackage(Integer issueId, String db, String packageSid) {
        return CachedPath.ISSUE_PACKAGE.getPath(KEYS, issueId, db, packageSid);
    }

    public static String getPathToIssuePackage(Integer issueId, String db, Integer packageId) {
        return CachedPath.ISSUE_PACKAGE.getPath(KEYS, issueId, db, packageId);
    }

    public static String getPathToBackupHtml(Integer issueId, String db, String recName, boolean central) {
        return CachedPath.ISSUE_COPY_HTML.getPath(KEYS, issueId, db) + buildFolderPath(recName, central);
    }

    public static String getPathToBackupHtmlFiguresDir(Integer issueId, String db, String recName) {
        return getPathToBackupHtml(issueId, db, recName, false) + Constants.IMAGE_N_DIR;
    }

    public static String getPathToBackupHtmlThumbnailsDir(Integer issueId, String db, String recName) {
        return getPathToBackupHtml(issueId, db, recName, false) + Constants.IMAGE_T_DIR;
    }

    public static String getPathToTopics(Integer issueId, String group) {
        return CachedPath.ISSUE_INPUT_GROUP.getPath(KEYS_CDSR, issueId, group) + Constants.TOPICS_SOURCE;
    }

    public static String buildTAName(String lang, String recName) {
        return recName + "." + lang;
    }

    public static String buildTAFileName(String lang, String recName) {
        return buildTAName(lang, recName) + Extensions.XML;
    }

    private static String getCachedPath(String pathKey, String[] keySet, Object... elements) {
        Res<PathType> path = PathType.get(pathKey);
        if (!Res.valid(path)) {
            throw new RuntimeException(String.format("no path by %s", pathKey));
        }
        return path.get().getCachedPath(keySet, elements);
    }

    private static String getCachedPath(CachedPath path, String postfix, String[] keySet, Object... elements) {
        return getCachedPath(path.path.get().getId() + postfix, keySet, elements);
    }

    private static String buildFileName(String fileName, String recName, boolean central) {
        return !central ? fileName : (FilePathCreator.buildSubFolder(recName) + fileName);
    }

    private static String buildFolderPath(String recName, boolean central) {
        return (!central ? recName : (FilePathCreator.buildSubFolder(recName) + recName)) + FilePathCreator.SEPARATOR;
    }

    /** Just Translation filter */
    public static final class TR {
        private TR() {
        }

        public static String getPathToEntireSrcTranslated(String db) {
            return CachedPath.ENTIRE_SRC_TR.getPath(KEYS_ENTIRE, db);
        }

        public static String getPathToWML21Translated(Integer issueId) {
            return CachedPath.ISSUE_SRC_TR.getPath(KEYS_CDSR, issueId);
        }

        public static String getPathToWML21TranslatedRecord(Integer issueId, String recName) {
            return getPathToWML21Translated(issueId) + recName  + Extensions.XML;
        }

        public static String getPathToTA() {
            return CachedPath.ENTIRE_TA_ROOT.getPath(KEYS_ENTIRE);
        }

        public static String getPathToTA(String lang) {
            return CachedPath.ENTIRE_TA_LANG.getPath(KEYS_ENTIRE, null, lang);
        }

        public static String getPathToJatsTA(String lang) {
            return CachedPath.ENTIRE_TA_JATS.getPath(KEYS_ENTIRE, null, lang);
        }

        public static String getPathToJatsTARecord(String lang, String recName) {
            return getPathToJatsTA(lang) + buildTAFileName(lang, recName);
        }

        /**
         * @param lang language code
         * @param recName record name
         * @return the path to translation of the specified record
         */
        public static String getPathToTARecord(String lang, String recName) {
            return getPathToTA(lang) + recName + Extensions.XML;
        }

        public static String getPathToWML21TA(String lang) {
            return CachedPath.ENTIRE_TA_ML21.getPath(KEYS_ENTIRE, null, lang);
        }

        public static String getPathToWML21TARecord(String lang, String recName) {
            return getPathToWML21TA(lang) + buildTAFileName(lang, recName);
        }

        public static String getPathToRevmanTranslations(Integer issueId, String packageSid, String group) {
            return CachedPath.ISSUE_REVMAN_TA.getPath(KEYS, issueId, null, packageSid, group);
        }

        public static String getPathToRevmanTranslation(Integer issueId, int packageId, String group,
                                                        String lang, String pubName) {
            return getPathToRevmanTranslations(issueId, "" + packageId, group) + buildTAFileName(lang, pubName);
        }

        public static String getPathToIssueTA(Integer issueId, int packageId) {
            return CachedPath.ISSUE_PACKAGE_TA.getPath(KEYS, issueId, null, "" + packageId);
        }

        public static String getPathToIssueTARecord(Integer issueId, int packageId, String lang, String record) {
            return getPathToIssueTA(issueId, packageId) + buildTAFileName(lang, record);
        }

        public static String getPathToBackupTA(Integer issueId) {
            return CachedPath.ISSUE_TA_COPY.getPath(KEYS_CDSR, issueId);
        }

        public static String getPathToBackupJatsTA(Integer issueId, String record) {
            return CachedPath.ISSUE_TA_JATS_COPY.getPath(KEYS_CDSR, issueId) + record + FilePathCreator.SEPARATOR;
        }

        public static String getPathToBackupWML3GTA(Integer issueId, String record) {
            return CachedPath.ISSUE_TA_ML3G_COPY.getPath(KEYS_CDSR, issueId) + record + FilePathCreator.SEPARATOR;
        }

        public static String getPathToBackupTA(Integer issueId, String record) {
            return getPathToBackupTA(issueId) + record + FilePathCreator.SEPARATOR;
        }

        public static String getPathToBackupTARecord(Integer issueId, String lang, String record) {
            return getPathToBackupTA(issueId, record) + lang + FilePathCreator.SEPARATOR + record + Extensions.XML;
        }

        public static String getPathToBackupWML21TARecord(Integer issueId, String lang, String record) {
            return getPathToBackupTARecord(getPathToBackupTA(issueId, record), lang, record);
        }

        public static String getPathToBackupWML3GTARecord(Integer issueId, String lang, String record) {
            return getPathToBackupTARecord(getPathToBackupWML3GTA(issueId, record), lang, record);
        }

        public static String getPathToBackupJatsTARecord(Integer issueId, String lang, String record) {
            return getPathToBackupTARecord(getPathToBackupJatsTA(issueId, record), lang, record);
        }

        public static String getPathToBackupTARecord(String basePath, String lang, String record) {
            return basePath + lang + FilePathCreator.SEPARATOR + buildTAFileName(lang, record);
        }

        public static String getPathToPreviousTA() {
            return CachedPath.PREVIOUS_TA_ROOT.getPath(KEYS_PREV);
        }

        public static String getPathToPreviousTA(Integer version) {
            return CachedPath.PREVIOUS_TA.getPath(KEYS_PREV, buildHistoryDir(version));
        }

        public static String getPathToPreviousTA(String versionFolder) {
            return getPathToPreviousTA(extractPreviousNumber(versionFolder));
        }

        public static String getPathToPreviousTA(Integer version, String lang) {
            return CachedPath.PREVIOUS_TA_LANG.getPath(KEYS_PREV, buildHistoryDir(version), null, lang);
        }

        public static String getPathToPreviousWML21TA(Integer version, String lang) {
            return CachedPath.PREVIOUS_TA_ML21.getPath(KEYS_PREV, buildHistoryDir(version), null, lang);
        }

        /**
         * Returns path to the record translation of the specified version.
         * @param version version number
         * @param lang language code
         * @param record record name
         * @return path to previous version of the translation of the record
         */
        public static String getPathToPreviousTARecord(Integer version, String lang, String record) {
            return getPathToPreviousTA(version, lang) + record + Extensions.XML;
        }

        public static String getPathToJatsTA(Integer issueId, Integer packageId) {
            return CachedPath.ISSUE_JATS_TA.getPath(KEYS, issueId, null, packageId);
        }

        public static String getPathToJatsTADir(Integer issueId, Integer packageId, String lang, String record) {
            return getPathToJatsTA(issueId, packageId) + record + FilePathCreator.SEPARATOR + lang;
        }

        public static String getPathToPreviousJatsTA(Integer version, String lang) {
            return CachedPath.PREVIOUS_TA_JATS.getPath(KEYS_PREV, buildHistoryDir(version), null, lang);
        }

        public static String getPathToPreviousJatsTARecord(Integer version, String lang, String record) {
            return getPathToPreviousJatsTA(version, lang) + buildTAFileName(lang, record);
        }

        public static String getPathToPreviousWML3GTA(Integer version, String lang) {
            return CachedPath.PREVIOUS_TA_ML3G.getPath(KEYS_PREV, buildHistoryDir(version), null, lang);
        }

        public static String getPathToPreviousWML3GTARecord(Integer version, String lang, String record) {
            return getPathToPreviousWML3GTA(version, lang) + buildTAFileName(lang, record);
        }

        public static String getPathToPreviousWML21TARecord(Integer version, String lang, String record) {
            return getPathToPreviousWML21TA(version, lang) + buildTAFileName(lang, record);
        }

        public static String getPathToWML3GTARecord(String lang, String record) {
            return CachedPath.ENTIRE_TA_ML3G.getPath(KEYS_ENTIRE, null, lang) + buildTAFileName(lang, record);
        }
    }

    /** Just JATS filter */
    public static final class JATS {
        private JATS() {
        }

        public static String getPathToTopics(Integer issueId, Integer packageId, String group) {
            return getPathToGroup(issueId, packageId, group) + Constants.TOPICS_SOURCE;
        }

        public static String getPathToGroup(Integer issueId, Integer packageId, String group) {
            return CachedPath.ISSUE_JATS_GROUP.getPath(KEYS, issueId, null, packageId, group);
        }

        public static String getPathToSrcDir(Integer issueId, Integer packageId, String record) {
            return getPathToPackage(issueId, packageId) + record;
        }

        public static String getPathToEntireDir(String dbName, String record) {
            return CachedPath.ENTIRE_JATS.getPath(KEYS_ENTIRE, dbName) + record;
        }

        public static String getPathToEntireRecord(String dbName, String record) {
            return getPathToEntireDir(dbName, record) + FilePathCreator.SEPARATOR + record + Extensions.XML;
        }

        public static String getPathToEntireRecord(String dbName, String record, String fileName) {
            return getPathToEntireDir(dbName, record) + FilePathCreator.SEPARATOR + fileName;
        }
        
        public static String getPathToPrevious(Integer version) {
            return CachedPath.PREVIOUS_JATS.getPath(KEYS_PREV, buildHistoryDir(version));
        }

        public static String getPathToPreviousDir(Integer version, String record) {
            return getPathToPrevious(version) + record;
        }

        public static String getPathToPreviousRecord(Integer version, String record, String fileName) {
            return getPathToPreviousDir(version, record) + FilePathCreator.SEPARATOR + fileName;
        }

        public static String getPathToPackage(Integer issueId, Integer packageId) {
            return CachedPath.ISSUE_JATS.getPath(KEYS, issueId, null, packageId);
        }

        public static String getPathToBackupDir(Integer issueId, String db, String record) {
            return CachedPath.ISSUE_COPY_JATS.getPath(KEYS, issueId, db) + record;
        }

        public static String getPathToBackupStatsDir(Integer issueId, String db, String record) {
            return getPathToBackupDir(issueId, db, record) + FilePathCreator.SEPARATOR + record
                    + Constants.JATS_STATS_DIR_SUFFIX;
        }

        public static String getPathToBackupFiguresDir(Integer issueId, String db, String record) {
            return getPathToBackupDir(issueId, db, record) + FilePathCreator.SEPARATOR + record
                    + Constants.JATS_FIG_DIR_SUFFIX;
        }

        public static String getPathToBackupThumbnailsDir(Integer issueId, String db, String record) {
            return getPathToBackupDir(issueId, db, record) + FilePathCreator.SEPARATOR + record
                    + Constants.JATS_TMBNLS_DIR_SUFFIX;
        }
    }

    /** Just RevMan filter */
    public static final class RM {
        private RM() {
        }

        public static String getPathToRevmanSrc(Integer issueId, String group) {
            return CachedPath.ISSUE_INPUT_SRC.getPath(KEYS_CDSR, issueId, group);
        }

        public static String getPathToRevmanRecord(Integer issueId, String group, String record) {
            return getPathToRevmanSrc(issueId, group) + record + Extensions.XML;
        }

        public static String getPathToRevmanMetadata(Integer issueId, String group, String record) {
            return getPathToRevmanSrc(issueId, group) + buildMetadataRecordName(record);
        }

        public static String getPathToRevmanRecord(Integer issueId, int packageId, String group, String pubName) {
            return getPathToRevmanSrc(issueId, "" + packageId, group) + pubName + Extensions.XML;
        }

        public static String getPathToRevmanMetadata(Integer issueId, String packageSid, String group, String record) {
            return getPathToRevmanSrc(issueId, packageSid, group) + buildMetadataRecordName(record);
        }

        public static String getPathToRevmanSrc(Integer issueId, String packageSid, String group) {
            return CachedPath.ISSUE_REVMAN_SRC.getPath(KEYS, issueId, null, packageSid, group);
        }

        public static String getPathToRevmanGroup(Integer issueId, int packageId, String group) {
            return CachedPath.ISSUE_REVMAN_GROUP.getPath(KEYS, issueId, null, "" + packageId, group);
        }
    }

    /** Just ML3G filter */
    public static final class ML3G {
        private ML3G() {
        }

        public static String getPathToEntireMl3g(String db) {
            return CachedPath.ENTIRE_ML3G.getPath(KEYS_ENTIRE, db);
        }

        public static String getPathToEntireMl3gTmp(String db) {
            return CachedPath.ENTIRE_ML3G_TMP.getPath(KEYS_ENTIRE, db);
        }

        public static String getPathToEntireMl3gTmpDir(String db, String record) {
            return getPathToEntireMl3gTmp(db) + record;
        }

        public static String getPathToEntireMl3gRecordByPostfix(String db, String record, String postfix) {
            return getCachedPath(CachedPath.ENTIRE_ML3G, postfix, KEYS_ENTIRE, db) + record + Extensions.XML;
        }

        public static String getPathToEntireMl3gRecordAssets(String db, String record) {
            return getPathToEntireMl3g(db) + record + Extensions.ASSETS;
        }

        public static String getPathToEntireMl3gRecord(String db, String record, boolean central) {
            return getPathToEntireMl3g(db) + buildFileName(record + Extensions.XML, record, central);
        }

        public static String getPathToMl3g(Integer issueId, String db) {
            return CachedPath.ISSUE_ML3G.getPath(KEYS, issueId, db);
        }

        public static String getPathToMl3gWOL(Integer issueId, String db) {
            return CachedPath.ISSUE_ML3G_WOL.getPath(KEYS, issueId, db);
        }

        public static String getPathToMl3gTmp(Integer issueId, String db) {
            return CachedPath.ISSUE_ML3G_TMP.getPath(KEYS, issueId, db);
        }

        public static String getPathToPreviousMl3gTmp(Integer issueId, Integer version) {
            return CachedPath.ISSUE_PREVIOUS_ML3G_TMP.getPath(KEYS_ISSUE_PREV, issueId, null, buildHistoryDir(version));
        }

        public static String getPathToMl3gTmpDir(Integer issueId, String db, String record) {
            return getPathToMl3gTmp(issueId, db) + record;
        }

        public static String getPathToPreviousMl3gTmpDir(Integer issueId, Integer version, String record) {
            return getPathToPreviousMl3gTmp(issueId, version) + record;
        }

        public static String getPathToMl3gRecordWOL(Integer issueId, String db, String record, boolean central) {
            return getPathToMl3gWOL(issueId, db) + buildFileName(record + Extensions.XML, record, central);
        }

        public static String getPathToMl3gRecord(Integer issueId, String db, String record) {
            return getPathToMl3g(issueId, db) + record + Extensions.XML;
        }

        public static String getPathToMl3gRecord(Integer issueId, String db, String record, boolean central) {
            return getPathToMl3g(issueId, db) + buildFileName(record + Extensions.XML, record, central);
        }

        public static String getPathToMl3gRecordAssets(Integer issueId, String db, String record) {
            return getPathToMl3g(issueId, db) + record + Extensions.ASSETS;
        }

        public static String getPathToMl3gRecordByPostfix(Integer issueId, String db, String record, String postfix) {
            return getCachedPath(CachedPath.ISSUE_ML3G, postfix, KEYS, issueId, db) + record + Extensions.XML;
        }

        public static String getPathToPreviousMl3g(Integer version) {
            return CachedPath.PREVIOUS_CDSR_ML3G.getPath(KEYS_PREV, buildHistoryDir(version));
        }

        public static String getPathToPreviousMl3g(Integer issueId, Integer version) {
            return CachedPath.ISSUE_PREVIOUS_ML3G.getPath(KEYS_ISSUE_PREV, issueId, null, buildHistoryDir(version));
        }

        public static String getPathToPreviousMl3gTmp(Integer version) {
            return CachedPath.PREVIOUS_CDSR_ML3G_TMP.getPath(KEYS_PREV, buildHistoryDir(version));
        }

        public static String getPathToPreviousMl3gTmpDir(Integer version, String record) {
            return getPathToPreviousMl3gTmp(version) + record;
        }

        public static String getPathToPreviousMl3gByPostfix(Integer version, String postfix) {
            return getCachedPath(CachedPath.PREVIOUS_CDSR_ML3G, postfix, KEYS_PREV, buildHistoryDir(version));
        }

        public static String getPathToPreviousMl3gRecordByPostfix(Integer version, String record, String postfix) {
            return getPathToPreviousMl3gByPostfix(version, postfix) + record + Extensions.XML;
        }

        public static String getPathToPreviousMl3gRecord(Integer version, String record) {
            return getPathToPreviousMl3g(version) + record + Extensions.XML;
        }

        public static String getPathToPreviousMl3gRecordAssets(Integer version, String record) {
            return getPathToPreviousMl3g(version) + record + Extensions.ASSETS;
        }

        public static String getPathToPreviousMl3gRecord(Integer issueId, Integer version, String record) {
            return getPathToPreviousMl3g(issueId, version) + record + Extensions.XML;
        }

        public static String getPathToPreviousMl3gRecordAssets(Integer issueId, Integer version, String record) {
            return getPathToPreviousMl3g(issueId, version) + record + Extensions.ASSETS;
        }

        public static String getPathToBackupMl3gRecordByPostfix(Integer issueId, String db, String record,
                                                                String postfix) {
            return getCachedPath(CachedPath.ISSUE_COPY_ML3G, postfix, KEYS, issueId, db) + record + Extensions.XML;
        }

        public static String getPathToBackupMl3gRecord(Integer issueId, String db, String record, boolean central) {
            return CachedPath.ISSUE_COPY_ML3G.getPath(KEYS, issueId, db)
                    + buildFileName(record + Extensions.XML, record, central);
        }

        public static String getPathToBackupMl3gRecordAssets(Integer issueId, String db, String record) {
            return CachedPath.ISSUE_COPY_ML3G.getPath(KEYS, issueId, db) + record + Extensions.ASSETS;
        }
    }

    /** Just PDF filter */
    public static final class PDF {
        private PDF() {
        }

        public static String getPathToEntirePdf(String db) {
            return CachedPath.ENTIRE_PDF.getPath(KEYS_ENTIRE, db);
        }

        public static String getPathToEntirePdf(String db, String record) {
            return getPathToEntirePdf(db) + record + FilePathCreator.SEPARATOR;
        }

        public static String getPathToEntirePdfRecord(String db, String record) {
            return getPathToEntirePdf(db, record) + record + Extensions.PDF;
        }

        public static String buildPdfTranslatedRecordName(String record, String language) {
            return record + "_" + language + Constants.PDF_ABSTRACT_SUFFIX + Extensions.PDF;
        }

        public static String getPathToPreviousPdf(Integer version) {
            return CachedPath.PREVIOUS_CDSR_PDF.getPath(KEYS_PREV, buildHistoryDir(version));
        }

        public static String getPathToPreviousPdf(Integer version, String record) {
            return getPathToPreviousPdf(version) + record + FilePathCreator.SEPARATOR;
        }

        public static String getPathToPreviousPdf(Integer issueId, Integer version) {
            return CachedPath.ISSUE_PREVIOUS_PDF.getPath(KEYS_ISSUE_PREV, issueId, null, buildHistoryDir(version));
        }

        public static String getPathToPreviousPdf(Integer issueId, Integer version, String record) {
            return getPathToPreviousPdf(issueId, version) + record + FilePathCreator.SEPARATOR;
        }

        public static String getPathToPdf(Integer issueId, String db) {
            return CachedPath.ISSUE_PDF.getPath(KEYS, issueId, db);
        }

        public static String getPathToPdf(Integer issueId, String db, String record) {
            return getPathToPdf(issueId, db) + record + FilePathCreator.SEPARATOR;
        }

        public static String getPathToBackupPdf(Integer issueId, String db, String record) {
            return CachedPath.ISSUE_COPY_PDF.getPath(KEYS, issueId, db) + record + FilePathCreator.SEPARATOR;
        }

        public static String getPathToEntirePdfFop(String db) {
            return CachedPath.ENTIRE_PDF_FOP.getPath(KEYS_ENTIRE, db);
        }

        public static String getPathToEntirePdfFop(String db, String record) {
            return getPathToEntirePdfFop(db) + record + FilePathCreator.SEPARATOR;
        }

        public static String getPathToEntirePdfFopRecord(String db, String record) {
            return getPathToEntirePdfFop(db, record) + record + Extensions.PDF;
        }

        public static String getPathToIssuePdfFopWithSuffix(Integer issueId, String db, String record, String suffix) {
            return getPathToPdfFop(issueId, db, record) + record + suffix + Extensions.PDF;
        }

        public static String getPathToPreviousPdfFop(Integer issueId, Integer version, String record, String suffix) {
            return getPathToPreviousPdfFop(issueId, version, record) + record + suffix + Extensions.PDF;
        }

        public static String getPathToIssuePdfFopTAWithSuffix(Integer issueId, String db, String record, String lang,
                                                              String suffix) {
            return getPathToPdfFop(issueId, db, record) + record + "_" + lang + suffix + Extensions.PDF;
        }

        public static String getPathToPreviousPdfFopTA(Integer issueId, Integer version, String record, String lang,
                                                       String suffix) {
            return getPathToPreviousPdfFop(issueId, version, record) + record + "_" + lang + suffix + Extensions.PDF;
        }

        public static String getPathToPreviousPdfFopWithSuffix(Integer version, String record, String suffix) {
            return getPathToPreviousPdfFop(version, record) + record + suffix + Extensions.PDF;
        }

        public static String getPathToPreviousPdfFopTAWithSuffix(Integer version, String record, String lang,
                                                              String suffix) {
            return getPathToPreviousPdfFop(version, record) + record + "_" + lang + suffix + Extensions.PDF;
        }

        public static String getPathToEntirePdfFopWithSuffix(String db, String record, String suffix) {
            return getPathToEntirePdfFop(db, record) + record + suffix + Extensions.PDF;
        }

        public static String getPathToEntirePdfFopTAWithSuffix(String db, String record, String lang, String suffix) {
            return getPathToEntirePdfFop(db, record) + record + "_" + lang + suffix + Extensions.PDF;
        }

        public static String getPathToPdfFop(Integer issueId, String db) {
            return CachedPath.ISSUE_PDF_FOP.getPath(KEYS, issueId, db);
        }

        public static String getPathToPreviousPdfFop(Integer issueId, Integer version) {
            return CachedPath.ISSUE_PREVIOUS_PDF_FOP.getPath(KEYS_ISSUE_PREV, issueId, null, buildHistoryDir(version));
        }

        public static String getPathToPdfFop(Integer issueId, String db, String record) {
            return getPathToPdfFop(issueId, db) + record + FilePathCreator.SEPARATOR;
        }

        public static String getPathToPreviousPdfFop(Integer issueId, Integer version, String record) {
            return getPathToPreviousPdfFop(issueId, version) + record + FilePathCreator.SEPARATOR;
        }

        public static String getPathToPreviousPdfFop(Integer version) {
            return CachedPath.PREVIOUS_CDSR_PDF_FOP.getPath(KEYS_PREV, buildHistoryDir(version));
        }

        public static String getPathToPreviousPdfFop(Integer version, String record) {
            return getPathToPreviousPdfFop(version) + record + FilePathCreator.SEPARATOR;
        }

        public static String getPathToBackupPdfFop(Integer issueId, String db, String record) {
            return CachedPath.ISSUE_COPY_PDF_FOP.getPath(KEYS, issueId, db) + record + FilePathCreator.SEPARATOR;
        }
    }
}
