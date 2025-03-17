package com.wiley.cms.cochrane.cmanager.entitymanager;

import com.wiley.cms.cochrane.cmanager.CmsUtils;
import com.wiley.cms.cochrane.cmanager.DeliveryPackageInfo;
import com.wiley.cms.cochrane.cmanager.FilePathBuilder;
import com.wiley.cms.cochrane.cmanager.FilePathCreator;
import com.wiley.cms.cochrane.cmanager.PreviousVersionException;
import com.wiley.cms.cochrane.cmanager.contentworker.ArchieEntry;
import com.wiley.cms.cochrane.cmanager.contentworker.ArchiePackage;
import com.wiley.cms.cochrane.cmanager.contentworker.TranslatedAbstractsPackage;
import com.wiley.cms.cochrane.cmanager.data.record.IRecord;
import com.wiley.cms.cochrane.cmanager.data.record.RecordEntity;
import com.wiley.cms.cochrane.cmanager.data.record.RecordManifest;
import com.wiley.cms.cochrane.cmanager.data.record.RecordVO;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.cmanager.res.CmsResourceInitializer;
import com.wiley.cms.cochrane.cmanager.translated.TranslatedAbstractVO;
import com.wiley.cms.cochrane.cmanager.translated.TranslatedAbstractsHelper;
import com.wiley.cms.cochrane.converter.ml3g.JatsMl3gAssetsManager;
import com.wiley.cms.cochrane.converter.ml3g.Ml3gAssetsManager;
import com.wiley.cms.cochrane.repository.IRepository;
import com.wiley.cms.cochrane.repository.RepositoryFactory;
import com.wiley.cms.cochrane.repository.RepositoryUtils;
import com.wiley.cms.cochrane.utils.Constants;
import com.wiley.cms.cochrane.utils.ContentLocation;
import com.wiley.cms.cochrane.utils.ErrorInfo;
import com.wiley.cms.converter.services.RevmanMetadataHelper;
import com.wiley.tes.util.Extensions;
import com.wiley.tes.util.InputUtils;
import com.wiley.tes.util.Logger;
import com.wiley.tes.util.res.Res;
import com.wiley.tes.util.res.Settings;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import javax.validation.constraints.NotNull;

import org.apache.commons.lang.StringUtils;

/**
 * Type comments here.
 *
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @version 1.0
 */
public final class RecordHelper {
    public static final String FAILED_COPY_WML3G_MSG_TEMP = "Failed to copy WML3G %s from [%s] to [%s], %s.\n";

    private static final Logger LOG = Logger.getLogger(RecordHelper.class);

    private static final int RECORD_NUMBER_PREFIX_CDSR_EDITORIAL_SIZE = 2;
    private static final int RECORD_NUMBER_PREFIX_CENTRAL_CCA_SIZE = 3;
    private static final int RECORD_NUMBER_MIN_FORMATTED_SIZE = 8;
    private static final int BASE_RECORD_NUMBER = 100000000;

    private static final List<String> RENDERED_GROUPS = new ArrayList<>();

    private static final Res<Settings> PREFIX_MAP = CmsResourceInitializer.getCDNumberPrefixMapping();
    private static final Res<Settings> DB_RECORD_NUMBER_MAP = CmsResourceInitializer.getDatabaseNumberMapping();

    static {
        RENDERED_GROUPS.add(Constants.RENDERED_HTML_DIAMOND);
        RENDERED_GROUPS.add(Constants.RENDERED_PDF_TEX);
        RENDERED_GROUPS.add(Constants.RENDERED_PDF_FOP);
    }

    private RecordHelper() {
    }

    public static IRecord createIRecord() {
        return new IRecord() {
            private String recordName;

            @Override
            public String getName() {
                return recordName;
            }

            @Override
            public  void setName(String name) {
                recordName =  name;
            }
        };
    }

    public static void getRecords(List<RecordVO> records, HashMap<String, Integer> map) {
        for (RecordVO rvo: records) {
            map.put(rvo.getName(), rvo.getId());
        }
    }

    public static String[] getRecordAttrsByFileName(String filename) {
        return RepositoryUtils.getRecordNameByFileName(filename).split(Constants.NAME_SPLITTER);
    }

    public static String buildDoiCDSRAndEditorial(String pubName) {
        return Constants.DOI_PREFIX_CDSR + pubName;
    }

    public static String buildDoiCCA(String name) {
        return Constants.DOI_PREFIX + name.replaceAll("(cca)(\\d)", "$1.$2");
    }

    public static String buildDoiCentral(String cdNumber) {
        return Constants.DOI_PREFIX_CENTRAL + cdNumber;
    }

    public static String buildPubName(String cdNumber, int pubNumber) {
        return pubNumber > Constants.FIRST_PUB ? cdNumber + Constants.PUB_PREFIX_POINT + pubNumber : cdNumber;
    }

    public static String buildCdNumber(int recordNumber) {

        String directValue =  PREFIX_MAP.get().getStrSetting("" + recordNumber);
        if (directValue != null) {
            return directValue;
        }

        int realNumber = recordNumber % BASE_RECORD_NUMBER;
        String baseNumberStr = "" + recordNumber / BASE_RECORD_NUMBER;
        String template =  PREFIX_MAP.get().getStrSetting(baseNumberStr);

        if (template.length() >= RECORD_NUMBER_MIN_FORMATTED_SIZE) {
            String realNumberStr = "" + realNumber;
            template = template.substring(0, template.length() - realNumberStr.length());
        }
        return template + realNumber;
    }

    public static int buildRecordNumberCca(String cdNumber) {
        return buildRecordNumber(cdNumber, RECORD_NUMBER_PREFIX_CENTRAL_CCA_SIZE);
    }

    public static int buildRecordNumberCentral(String cdNumber) {
        return buildRecordNumber(cdNumber, RECORD_NUMBER_PREFIX_CENTRAL_CCA_SIZE);
    }

    public static int buildRecordNumberCdsr(String cdNumber) {
        return buildRecordNumber(cdNumber, RECORD_NUMBER_PREFIX_CDSR_EDITORIAL_SIZE);
    }

    public static int buildRecordNumber(String cdNumber) {
        String str = cdNumber.replaceAll("[^\\d]", "");
        int numberSize = str.length();
        int prefixSize = cdNumber.length() - numberSize;
        return buildRecordNumber(cdNumber, prefixSize, str);
    }

    public static int buildRecordNumber(String cdNumber, int prefixSize) {
        return buildRecordNumber(cdNumber, prefixSize, cdNumber.substring(prefixSize));
    }

    public static String getDbNameByRecordNumber(int recordNumber) {
        return DB_RECORD_NUMBER_MAP.get().getStrSetting("" + (recordNumber / BASE_RECORD_NUMBER));
    }

    public static int[] getRecordNumbersRange(String dbName) {
        String[] parts = DB_RECORD_NUMBER_MAP.get().getArraySetting(dbName);
        int[] range = {0, 0};
        if (parts != null) {
            try {
                int start = Integer.parseInt(parts[0]);
                int end = parts.length > 1 ? Integer.parseInt(parts[1]) : start;
                if (start > end || end - start > 1) {
                    LOG.warn(String.format("such a record numbers range is currently not supported for %s: %d - %d",
                            dbName, start, end));
                    end = start;
                }
                range[0] = start * BASE_RECORD_NUMBER;
                range[1] = end * BASE_RECORD_NUMBER + BASE_RECORD_NUMBER - 1;

            } catch (Exception e) {
                LOG.warn(String.format("cannot calculate number range for %s - %s", dbName, e.getMessage()));
            }
        }
        return range;
    }

    public static int getMaxRecordNumber(int recordNumber) {
        return recordNumber / BASE_RECORD_NUMBER * BASE_RECORD_NUMBER + BASE_RECORD_NUMBER - 1;
    }

    public static void handleErrorMessage(ErrorInfo ei, List<ErrorInfo> err) {
        handleErrorMessage(ei, err, true);
    }

    public static void handleErrorMessage(ErrorInfo ei, List<ErrorInfo> err, boolean addToEnd) {
        if (addToEnd || err.isEmpty()) {
            err.add(ei);
        } else {
            err.add(0, ei);
        }
        LOG.error(String.format("failed to handle %s: %s", ei.getErrorEntity(), ei.getErrorDetail()));
    }

    private static int buildRecordNumber(String cdNumber, int prefixSize, String numberStr) {
        String prefix = cdNumber.substring(0, prefixSize);
        Integer prefixValue =  PREFIX_MAP.get().getIntSetting(prefix);
        if (prefixValue == null) {
            prefixValue = buildRecordNumberWithoutPrefix(cdNumber);
            if (prefixValue != null) {
                return prefixValue;
            }
            LOG.warn(String.format("unknown DB prefix %s", prefix));
            prefixValue = 0;
        }
        int ret = 0;
        try {
            ret = prefixValue + Integer.parseInt(numberStr);

        } catch (NumberFormatException ne) {
            Integer directValue = buildRecordNumberWithoutPrefix(cdNumber);
            if (directValue != null) {
                ret = directValue;

            } else {
                LOG.warn(ne.getMessage());
            }
        }
        return ret;
    }

    private static Integer buildRecordNumberWithoutPrefix(String cdNumber) {
        return PREFIX_MAP.get().getIntSetting(cdNumber);
    }

    public static void copyPdfFOPToEntire(int issueId, String dbName, String recName, IRepository rp) throws Exception {

        String pathTo = FilePathBuilder.PDF.getPathToEntirePdfFop(dbName, recName);
        String pathFrom = FilePathBuilder.PDF.getPathToPdfFop(issueId, dbName, recName);
        rp.deleteDir(pathTo);
        CmsUtils.writeDir(pathFrom, pathTo);
    }

    public static void copyWML3GToEntire(int issueId, String dbName, String recName, BaseType bt,
        boolean fullUpload, IRepository rp) throws Exception {

        String srcXmlUri = FilePathCreator.getFilePathForMl3gXml(issueId, dbName, recName);
        String destXmlUri = FilePathCreator.getFilePathForEntireMl3gXml(dbName, recName);
        StringBuilder errs = new StringBuilder();

        try {
            InputStream is = rp.getFile(srcXmlUri);
            rp.putFile(destXmlUri, is);
        } catch (IOException e) {
            errs.append(String.format(FAILED_COPY_WML3G_MSG_TEMP, Constants.XML_STR, srcXmlUri, destXmlUri, e));
        }

        if (bt.hasStandaloneWml3g()) {
            Ml3gAssetsManager.copyAssetsFromOneLocation2Another(dbName, issueId, recName, Constants.UNDEF,
                    ContentLocation.ISSUE, ContentLocation.ENTIRE, errs);
        }
        if (errs.length() > 0) {
            throw new Exception(errs.toString());
        }
    }

    public static void copyWML21(String recordName, String pathTo, String pathFrom, DeliveryPackageInfo di)
            throws Exception {
        IRepository rp = RepositoryFactory.getRepository();

        String recordPathTo = FilePathBuilder.getPathToSrcRecord(pathTo, recordName, false);
        if (rp.isFileExists(recordPathTo)) {
            return;
        }

        String recordDirTo = FilePathBuilder.getPathToSrcDir(pathTo, recordName);
        rp.deleteDir(recordDirTo);

        InputStream is = rp.getFile(FilePathBuilder.getPathToSrcRecord(pathFrom, recordName, false));
        rp.putFile(recordPathTo, is);
        di.addFile(recordName, recordPathTo);
        di.addRecordPath(recordName, recordPathTo);

        RepositoryUtils.copyDir(recordName, recordDirTo, FilePathBuilder.getPathToSrcDir(pathFrom, recordName), di, rp);
    }

    public static boolean hasOnlyNotJatsTranslations(int issueId, int dfId, String recName) {

        String revmanPath = FilePathBuilder.getPathToRevmanPackage(issueId, "" + dfId);

        RevmanSource rs = findInitialSourcesForRevmanPackage(recName, revmanPath, RepositoryFactory.getRepository());

        return rs != null && rs.revmanFile == null && !rs.taFiles.isEmpty();
    }

    public static RevmanSource findInitialSourcesForRevmanPackage(String recName, String revmanPath, IRepository rp) {

        File revmanSrc = new File(rp.getRealFilePath(revmanPath));
        File[] groups = revmanSrc.exists() ? revmanSrc.listFiles() : null;
        if (groups == null) {
            return null;
        }
        RevmanSource ret = new RevmanSource(revmanSrc.getAbsolutePath());
        for (File group : groups) {
            if (!group.isDirectory()) {
                continue;
            }
            if (findRevmanSource(recName, group, ret)) {
                break;
            }
        }
        return ret;
    }

    public static RevmanSource findInitialSourcesForRevmanPackage(String recName, File group) {
        RevmanSource ret = new RevmanSource();
        if (group.exists()) {
            findRevmanSource(recName, group, ret);
        }
        return ret;
    }

    private static boolean findRevmanSource(String recName, File group, RevmanSource ret) {

        String groupPath = group.getAbsolutePath();
        String groupName = group.getName();
        ret.revmanFile = RevmanMetadataHelper.findRevmanRecord(groupPath, recName);
        findRevmanTranslations(groupPath, recName, ret.taFiles);
        if (ret.revmanFile != null || !ret.taFiles.isEmpty()) {
            ret.setGroup(groupName, groupPath);
            return true;
        }
        return false;
    }

    private static void findRevmanTranslations(String groupPath, String recName, Map<String, File> ret) {
        File taDir = new File(groupPath + FilePathCreator.SEPARATOR + ArchiePackage.TRANSLATION_FOLDER);
        File[] files = taDir.listFiles();
        if (files != null) {
            for (File fl : files) {
                String fileName = fl.getName();
                if (!fl.isFile() || !fileName.startsWith(recName)) {
                    continue;
                }
                String lang = TranslatedAbstractsPackage.parseLanguage(fileName).second;
                if (lang == null) {
                    LOG.warn(String.format("cannot parse a translation file name: %s", fl.getAbsolutePath()));
                    continue;
                }
                String mappedLanguage = TranslatedAbstractVO.getMappedLanguage(lang);
                if (mappedLanguage == null) {
                    mappedLanguage = lang;
                }
                ret.put(mappedLanguage, fl);
            }
        }
    }

    public static void putFile(String source, String path, IRepository rp) throws IOException {
        InputStream is = new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8));
        rp.putFile(path, is);
    }

    public static String findJatsSource(String srcFolder, String recName, IRepository rp)
            throws PreviousVersionException {
        try {
            return findJatsSource(srcFolder, recName, Extensions.XML, rp);
        } catch (Exception e) {
            throw new PreviousVersionException(e.getMessage());
        }
    }

    public static String findAriesImportSource(String srcFolder, IRepository rp) throws Exception {
        return findJatsSource(srcFolder, null, Constants.ARIES_IMPORT_EXTENSION, rp);
    }

    public static String findPathToJatsTAOriginalRecord(String taFolderPath, String cdNumber, IRepository rp)
            throws Exception {
        return taFolderPath + findJatsSource(taFolderPath, cdNumber, Extensions.XML, rp);
    }

    private static String findJatsSource(String srcFolder, String recName, @NotNull String postfix, IRepository rp)
            throws Exception {
        File[] files = rp.getFilesFromDir(srcFolder);
        if (files != null) {
            for (File fl : files) {
                if (fl.isDirectory()) {
                    continue;
                }
                if ((recName == null || fl.getName().startsWith(recName)) && fl.getName().endsWith(postfix)) {
                    return fl.getName();
                }
            }
        }
        throw new Exception(recName == null ? String.format("cannot find *%s in JATS folder: %s", postfix, srcFolder)
                : String.format("cannot find %s*%s in JATS folder: %s", recName, postfix, srcFolder));
    }

    public static boolean setExistingCDSRSrcPath(String dbName, String cdNumber, ArchieEntry entry, Integer version,
                                                 boolean jats, IRepository rp) throws PreviousVersionException {
        ContentLocation cl = version == RecordEntity.VERSION_LAST ? ContentLocation.ENTIRE : ContentLocation.PREVIOUS;
        if (jats) {
            String recordDir = cl.getPathToJatsSrcDir(null, dbName, null, version, cdNumber);
            if (!rp.isFileExistsQuiet(recordDir)) {
                throw new PreviousVersionException(new ErrorInfo<>(entry, ErrorInfo.Type.CONTENT,
                        "no JATS article uploaded before"));
            }
            entry.setPath(recordDir + FilePathCreator.SEPARATOR + findJatsSource(recordDir, cdNumber, rp));
            return JatsMl3gAssetsManager.hasStatsData(recordDir, rp);

        } else {
            String recordPath = cl.getPathToMl21SrcRecord(null, dbName, null, version, cdNumber);
            if (!rp.isFileExistsQuiet(recordPath)) {
                throw new PreviousVersionException(new ErrorInfo<>(entry, ErrorInfo.Type.CONTENT,
                        "no Wiley ML21 article uploaded before"));
            }
            entry.setPath(recordPath);
            String statsDataUriString = FilePathBuilder.buildStatsDataPathByUri(recordPath, cdNumber);
            return RepositoryFactory.getRepository().isFileExistsQuiet(statsDataUriString);
        }
    }

    public static List<String> getRecordSourceUris(int issueId, String dbName, List<Object[]> recordNames) {
        List<String> ret = new ArrayList<>();
        for (Object[] rec: recordNames) {
            ret.addAll(new RecordManifest(issueId, dbName,
                    rec[0].toString(), rec[1].toString()).getUris(Constants.SOURCE));
        }
        return ret;
    }

    public static void removeRecordRenderedFolders(RecordManifest manifest) {
        removeRecordFolders(manifest, RENDERED_GROUPS);
    }

    public static void removeRecordFolders(RecordManifest manifest, List<String> groups) {
        if (manifest == null) {
            LOG.error("cannot get resources uris because of record uris are not initialized");
            return;
        }
        List<String> uris = groups == null ? manifest.getUris() : manifest.getUris(groups);
        if (uris.isEmpty()) {
            LOG.error("cannot get resources uris by record " + manifest.getRecord() + " and groups " + groups);
            return;
        }
        CmsUtils.deleteSourceWithImages(uris);
    }

    public static boolean isNotLatest(String dbName, String recName, long controlTime, IRepository rp) {
        String destXmlUri = FilePathCreator.getFilePathForEntireMl3gXml(dbName, recName);
        File fl = new File(rp.getRealFilePath(destXmlUri));
        return (!fl.exists() || fl.lastModified() >= controlTime);
    }

    public static void copyJatsToPrevious(String dbName, Integer version, String recName, IRepository rp)
            throws Exception {

        copyDir(recName, version, FilePathBuilder.JATS.getPathToEntireDir(dbName, recName),
                FilePathBuilder.JATS::getPathToPrevious, rp);
    }

    public static void copyPdfsToPrevious(String dbName, Integer version, String recName, IRepository rp)
            throws Exception {

        copyDir(recName, version, FilePathBuilder.PDF.getPathToEntirePdf(dbName, recName),
                FilePathBuilder.PDF::getPathToPreviousPdf, rp);
        copyDir(recName, version, FilePathBuilder.PDF.getPathToEntirePdfFop(dbName, recName),
                        FilePathBuilder.PDF::getPathToPreviousPdfFop, rp);
    }

    private static void copyDir(String recName, Integer version, String pathFrom, Function<Integer, String> getPathTo,
                                IRepository rp) throws Exception {
        if (rp.isFileExists(pathFrom)) {
            String pathTo = getPathTo.apply(version) + recName;
            CmsUtils.writeDir(pathFrom, pathTo);
        }
    }

    public static void clearBackUpSrc(Integer issueId, String dbName, String cdNumber, boolean central, IRepository rp)
            throws Exception {
        if (central) {
            RepositoryUtils.deleteFile(FilePathBuilder.getPathToBackupSrcRecord(
                    issueId, dbName, cdNumber, central), rp);
        } else {
            String srcDir = ContentLocation.ISSUE_COPY.getPathToMl21SrcDir(issueId, dbName, null, null, cdNumber);
            RepositoryUtils.deleteDir(srcDir, rp);
            String srcPath = srcDir + Extensions.XML;
            RepositoryUtils.deleteFile(srcPath, rp);
        }
        RepositoryUtils.deleteFile(ContentLocation.ISSUE_COPY.getPathToMl3g(
                issueId, dbName, null, cdNumber, central), rp);
        RepositoryUtils.deleteFile(ContentLocation.ISSUE_COPY.getPathToMl3gAssets(issueId, dbName, null, cdNumber), rp);
    }

    public static void makeBackUp(BaseType bt, Integer issueId, String cdNumber, IRepository rp) {
        try {
            String dbName = bt.getId();
            boolean central = bt.isCentral();

            String srcPathTo = FilePathBuilder.getPathToBackupSrcRecord(issueId, dbName, cdNumber, central);
            if (rp.isFileExists(srcPathTo)) {
                // a backup was already done
                return;
            }

            String ml3gPathTo = FilePathBuilder.ML3G.getPathToBackupMl3gRecord(issueId, dbName, cdNumber, central);
            if (rp.isFileExists(ml3gPathTo)) {
                // a backup was already done
                return;
            }

            String prefixForCopy = FilePathBuilder.getPathToBackup(issueId, dbName);
            String srcPath = FilePathBuilder.getPathToEntireSrcRecord(dbName, cdNumber, central);
            if (rp.isFileExists(srcPath)) {
                rp.putFile(srcPathTo, rp.getFile(srcPath));
            }

            CmsUtils.writeDir(srcPath.replace(Extensions.XML, ""), srcPathTo.replace(Extensions.XML, ""), true, rp);

            String ml3gPath = FilePathBuilder.ML3G.getPathToEntireMl3gRecord(dbName, cdNumber, central);
            boolean exists = RepositoryUtils.copyFile(ml3gPath, ml3gPathTo, false, rp);
            if (bt.hasStandaloneWml3g()) {
                ml3gPath = FilePathBuilder.ML3G.getPathToEntireMl3gRecordAssets(dbName, cdNumber);
                ml3gPathTo = ml3gPathTo.replace(Extensions.XML, Extensions.ASSETS);
                RepositoryUtils.copyFile(ml3gPath, ml3gPathTo, exists, rp);
            }

            String pathTo;
            String pathFrom;

            if (bt.canPdfFopConvert()) {
                pathTo = FilePathBuilder.PDF.getPathToBackupPdfFop(issueId, dbName, cdNumber);
                pathFrom = FilePathBuilder.PDF.getPathToEntirePdfFop(dbName, cdNumber);
                CmsUtils.writeDir(pathFrom, pathTo, true, rp);
            }

            if (bt.canHtmlConvert()) {
                pathTo = FilePathBuilder.getPathToBackupHtml(issueId, dbName, cdNumber, central);
                pathFrom = FilePathBuilder.getPathToEntireHtml(dbName, cdNumber, central);
                CmsUtils.writeDir(pathFrom, pathTo, true, rp);
            }
        } catch (Exception e) {
            LOG.warn(e);
        }
    }

    public static void removeRecordFolders(BaseType bt, Integer issueId, IRecord record) {
        String recordPath = record.getRecordPath();
        if (recordPath == null) {
            return;
        }

        String cdNumber = record.getName();
        String dbName = bt.getId();
        boolean central = bt.isCentral();
        boolean cdsr = !central && bt.isCDSR();
        IRepository rp = RepositoryFactory.getRepository();
        try {
            String group = record.getGroupSid();
            if (cdsr && group != null) {
                rp.deleteDir(FilePathBuilder.RM.getPathToRevmanRecord(issueId, group, cdNumber));
                rp.deleteDir(FilePathBuilder.RM.getPathToRevmanMetadata(issueId, group, cdNumber));
                rp.deleteDir(FilePathBuilder.TR.getPathToWML21TranslatedRecord(issueId, cdNumber));

                rp.deleteDir(FilePathBuilder.getPathToReconvertedIssueDb(issueId, dbName) + cdNumber);
                rp.deleteDir(FilePathBuilder.getPathToReconvertedIssueDb(issueId, dbName) + cdNumber + Extensions.XML);
            }
            
            rp.deleteDir(FilePathBuilder.getPathToHtml(issueId, dbName, cdNumber, central));

            if (!central) {
                rp.deleteDir(FilePathBuilder.PDF.getPathToPdf(issueId, dbName, cdNumber));
                rp.deleteDir(FilePathBuilder.PDF.getPathToPdfFop(issueId, dbName, cdNumber));
            }
            rp.deleteDir(FilePathBuilder.ML3G.getPathToMl3gRecord(issueId, dbName, cdNumber, central));
            rp.deleteDir(FilePathBuilder.ML3G.getPathToMl3gRecordAssets(issueId, dbName, cdNumber));
            rp.deleteDir(FilePathBuilder.ML3G.getPathToMl3gRecordWOL(issueId, dbName, cdNumber, central));

        } catch (Exception e) {
            LOG.error(e.getMessage());
        }
    }

    public static void deletePreviousDir(String cdNumber, Integer version, String group, IRepository rp) {
        try {
            String filePath = FilePathCreator.getPreviousSrcPath(cdNumber, version);
            RepositoryUtils.deleteFile(filePath, true, rp);

            filePath = StringUtils.substringBefore(filePath, Extensions.XML);
            RepositoryUtils.deleteFile(filePath, false, rp);

            filePath = FilePathCreator.getPreviousHtmlPath(cdNumber, version);
            RepositoryUtils.deleteFile(filePath, false, rp);

            filePath = FilePathCreator.getPreviousPdfPath(cdNumber, version);
            RepositoryUtils.deleteFile(filePath, false, rp);

            filePath = FilePathCreator.getPreviousPdfFopPath(cdNumber, version);
            RepositoryUtils.deleteFile(filePath, false, rp);

            RepositoryUtils.deleteFile(FilePathCreator.getPreviousMl3gXmlPath(cdNumber, version), true, rp);
            RepositoryUtils.deleteFile(FilePathCreator.getPreviousMl3gAssetsPath(cdNumber, version), true, rp);
            RepositoryUtils.deleteFile(FilePathBuilder.JATS.getPathToPreviousDir(version, cdNumber), false, rp);

            filePath = FilePathBuilder.getPathToPreviousRevmanSrc(version, group);
            RepositoryUtils.deleteFile(filePath + cdNumber + Extensions.XML, true, rp);
            RepositoryUtils.deleteFile(filePath + FilePathBuilder.buildMetadataRecordName(cdNumber), true, rp);

            TranslatedAbstractsHelper.deleteTranslations(cdNumber, version, false);
            TranslatedAbstractsHelper.deleteTranslations(cdNumber, version, true);

        } catch (Exception e) {
            LOG.error(e);
        }
    }

    public static void deleteContentFromIssue(String dbName, Integer srcIssueId, RecordEntity re, IRepository rp) {
        String cdNumber = re.getName();
        Integer dfId = re.getDeliveryFile().getId();
        try {
            rp.deleteDir(FilePathBuilder.getPathToIssuePackage(srcIssueId, dbName, dfId));
            rp.deleteDir(ContentLocation.ISSUE.getPathToPdf(srcIssueId, dbName, null, cdNumber));
            rp.deleteDir(ContentLocation.ISSUE.getPathToMl3gTmpDir(srcIssueId, dbName, null, cdNumber));

            String srcMl3gPath = ContentLocation.ISSUE.getPathToMl3g(srcIssueId, dbName, null, cdNumber, false);
            if (rp.isFileExistsQuiet(srcMl3gPath)) {
                rp.deleteFile(srcMl3gPath);
            }
            String srcAssetPath = srcMl3gPath.replace(Extensions.XML, Extensions.ASSETS);
            if (rp.isFileExistsQuiet(srcAssetPath)) {
                rp.deleteFile(srcAssetPath);
            }
        } catch (Exception e)  {
            LOG.warn(e.getMessage());
        }
    }

    public static void copyContentFromIssueToIssue(String dbName, Integer srcIssueId, Integer destIssueId,
                                                   RecordEntity re, IRepository rp) throws Exception {
        String cdNumber = re.getName();
        Integer dfId = re.getDeliveryFile().getId();

        CmsUtils.replaceDir(FilePathBuilder.getPathToIssuePackage(srcIssueId, dbName, dfId),
                FilePathBuilder.getPathToIssuePackage(destIssueId, dbName, dfId), null, rp);
        CmsUtils.replaceDir(ContentLocation.ISSUE.getPathToPdf(srcIssueId, dbName, null, cdNumber),
                ContentLocation.ISSUE.getPathToPdf(destIssueId, dbName, null, cdNumber), null, rp);
        CmsUtils.replaceDir(ContentLocation.ISSUE.getPathToMl3gTmpDir(srcIssueId, dbName, null, cdNumber),
                ContentLocation.ISSUE.getPathToMl3gTmpDir(destIssueId, dbName, null, cdNumber), null, rp);

        String srcMl3gPath = ContentLocation.ISSUE.getPathToMl3g(srcIssueId, dbName, null, cdNumber, false);
        String destMl3gPath = ContentLocation.ISSUE.getPathToMl3g(destIssueId, dbName, null, cdNumber, false);
        String destAssetPath = destMl3gPath.replace(Extensions.XML, Extensions.ASSETS);
        Exception ex = CmsUtils.replaceFile(srcMl3gPath, destMl3gPath, null, rp);
        if (ex != null) {
            throw  ex;
        }
        ex = CmsUtils.replaceFile(srcMl3gPath.replace(Extensions.XML, Extensions.ASSETS), destAssetPath, null, rp);
        if (ex != null) {
            throw ex;
        }
        String assets = InputUtils.readStreamToString(rp.getFile(destAssetPath));
        String partPathSrc = FilePathBuilder.getPathToIssueDb(srcIssueId, dbName);
        String partPathDest = FilePathBuilder.getPathToIssueDb(destIssueId, dbName);
        putFile(assets.replace(partPathSrc, partPathDest), destAssetPath, rp);

        re.setRecordPath(re.getRecordPath().replace(partPathSrc, partPathDest));
    }
}
