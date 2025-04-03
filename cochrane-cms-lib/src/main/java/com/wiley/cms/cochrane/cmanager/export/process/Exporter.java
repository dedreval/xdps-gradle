package com.wiley.cms.cochrane.cmanager.export.process;

import com.wiley.cms.cochrane.cmanager.CmsException;
import com.wiley.cms.cochrane.cmanager.CmsUtils;
import com.wiley.cms.cochrane.cmanager.CochraneCMSBeans;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.FilePathBuilder;
import com.wiley.cms.cochrane.cmanager.FilePathCreator;
import com.wiley.cms.cochrane.cmanager.MessageSender;
import com.wiley.cms.cochrane.cmanager.contentworker.ArchiePackage;
import com.wiley.cms.cochrane.cmanager.contentworker.JatsHelper;
import com.wiley.cms.cochrane.cmanager.contentworker.JatsPackage;
import com.wiley.cms.cochrane.cmanager.data.ClDbEntity;
import com.wiley.cms.cochrane.cmanager.data.IContentRoom;
import com.wiley.cms.cochrane.cmanager.data.IResultsStorage;
import com.wiley.cms.cochrane.cmanager.data.entire.EntireDBEntity;
import com.wiley.cms.cochrane.cmanager.data.issue.IssueVO;
import com.wiley.cms.cochrane.cmanager.data.record.RecordEntity;
import com.wiley.cms.cochrane.cmanager.data.record.RecordManifest;
import com.wiley.cms.cochrane.cmanager.data.translation.PublishedAbstractEntity;
import com.wiley.cms.cochrane.cmanager.entitymanager.AbstractManager;
import com.wiley.cms.cochrane.cmanager.entitymanager.DbRecordVO;
import com.wiley.cms.cochrane.cmanager.entitymanager.ICDSRMeta;
import com.wiley.cms.cochrane.cmanager.entitymanager.RecordHelper;
import com.wiley.cms.cochrane.cmanager.entitymanager.RevmanSource;
import com.wiley.cms.cochrane.cmanager.entitywrapper.DbWrapper;
import com.wiley.cms.cochrane.cmanager.entitywrapper.IssueWrapper;
import com.wiley.cms.cochrane.cmanager.export.data.ExportEntity;
import com.wiley.cms.cochrane.cmanager.export.data.ExportVO;
import com.wiley.cms.cochrane.cmanager.publish.PublishHelper;
import com.wiley.cms.cochrane.cmanager.publish.generate.ArchiveEntry;
import com.wiley.cms.cochrane.cmanager.translated.TranslatedAbstractVO;
import com.wiley.cms.cochrane.cmanager.translated.TranslatedAbstractsHelper;
import com.wiley.cms.cochrane.converter.ml3g.JatsMl3gAssetsManager;
import com.wiley.cms.cochrane.converter.ml3g.Ml3gXmlAssets;
import com.wiley.cms.cochrane.repository.IRepository;
import com.wiley.cms.cochrane.repository.RepositoryUtils;
import com.wiley.cms.cochrane.utils.Constants;
import com.wiley.cms.cochrane.utils.ContentLocation;
import com.wiley.cms.cochrane.utils.IssueDate;
import com.wiley.cms.cochrane.test.PackageChecker;
import com.wiley.tes.util.DbConstants;
import com.wiley.tes.util.FileUtils;
import com.wiley.tes.util.Pair;
import com.wiley.cms.cochrane.utils.zip.IZipOutput;
import com.wiley.cms.cochrane.utils.zip.ZipOutput;
import com.wiley.cms.converter.services.RevmanMetadataHelper;
import com.wiley.tes.util.Extensions;
import com.wiley.tes.util.Logger;
import com.wiley.tes.util.Now;
import com.wiley.tes.util.jdom.DocumentLoader;
import com.wiley.tes.util.res.Property;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import org.jdom.xpath.XPath;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @version 1.0
 */
public class Exporter {
    private static final Logger LOG = Logger.getLogger(Exporter.class);
    private static final String BRACKET_AND_COMMA_MSG = "], ";
    private static final String ENTIRE = "entire";
    protected final IRepository rps = AbstractManager.getRepository();
    protected final Map<String, StringBuilder> errsByNotifId = new HashMap<>();
    protected final List<ArchiveEntry> entries = new ArrayList<>();
    protected final Set<Integer> items;
    protected List<IContentExporter> contents;
    protected List<PackageContent> packageContents;
    protected Logger log;
    protected IZipOutput output;
    private final IResultsStorage rs = AbstractManager.getResultStorage();
    private final String mainPackageName;
    private final String mainArchivePath;
    private String lastArchiveCreated;
    private int fileCnt = 0;
    private boolean hasTa = false;

    public Exporter(ExportParameters params, Set<Integer> items, String filePath) throws CmsException {
        init(params);
        this.items = items;
        mainPackageName = RepositoryUtils.getLastNameByPath(filePath);
        mainArchivePath = filePath;
    }

    private IZipOutput createOutput(String filePath) throws CmsException {
        try {
            lastArchiveCreated = filePath;
            return new ZipOutput(filePath);
        } catch (FileNotFoundException e) {
            throw new CmsException(e);
        }
    }

    protected void init() {
        log = Logger.getLogger(Exporter.class);
        errsByNotifId.put(StringUtils.EMPTY, new StringBuilder());
        contents = new ArrayList<>();
        packageContents = new ArrayList<>();
    }

    protected void init(ExportParameters params) {
        init();
        if (params.isRenderedText()) {
            contents.add(new RenderedText());
        }
        if (params.isRenderedGraphics()) {
            contents.add(new RenderedGraphics());
        }
        if (params.isSourceGraphics()) {
            contents.add(new SourceGraphics());
        }
        if (params.isXmlSource()) {
            contents.add(new XmlSource());
        }
        if (params.isRenderedPdf()) {
            contents.add(new RenderedPdf());
        }
        DbWrapper db = null;
        String dbName = params.getDbName();
        if (params.isMl3gForIpad()) {
            db = new DbWrapper(params.getDbId());
            contents.add(new Ml3gForIpadIssue(db.getIssue().getId(), dbName, db.getIssue().getFullNumber()));
        }
        if (params.isRevManForIPackage()) {
            if (db == null) {
                db = new DbWrapper(params.getDbId());
            }
            packageContents.add(new RevmanPackageContent(db.getIssue(), dbName));
        }
        if (params.isWml21ForIPackage()) {
            if (db == null) {
                db = new DbWrapper(params.getDbId());
            }
            packageContents.add(new WML21PackageContent(db.getIssue(), dbName));
        }
        if (params.isTaForIPackage()) {
            if (db == null) {
                db = new DbWrapper(params.getDbId());
            }
            packageContents.add(new TranslationPackageContent(db.getIssue(), dbName, false));
            packageContents.add(new TranslationPackageContent(db.getIssue(), dbName, true));
        }
        if (params.isWRIPackage()) {
            if (db == null) {
                db = new DbWrapper(params.getDbId());
            }
            packageContents.add(params.isJatsForIPackage() ? new WRJatsPackageContent(db.getIssue(), dbName)
                    : new WRPackageContent(db.getIssue(), dbName));

        } else if (params.isJatsForIPackage()) {
            if (db == null) {
                db = new DbWrapper(params.getDbId());
            }
            packageContents.add(new JatsPackageContent(db.getIssue(), dbName));
        }
    }

    public String getLastArchiveCreated() {
        return lastArchiveCreated;
    }

    public List<ExportVO> getCompletedExports(ExportVO first) {
        StringBuilder commonErrs = errsByNotifId.get(StringUtils.EMPTY);
        boolean hasErrs = commonErrs != null && commonErrs.length() > 0;
        List<ExportVO> ret = new ArrayList<>();
        if (!contents.isEmpty() || (hasErrs && packageContents.isEmpty())) {
            ret.add(first);
            first.setState(getCompletedState(fileCnt, hasErrs || errsByNotifId.containsKey(mainArchivePath)));
        }
        for (PackageContent pc: packageContents) {
            if (pc.isOmitted() && pc.packageEntries.isEmpty()) {
                continue;
            }
            String packagePath = buildExtraPackagePath(pc);
            ExportVO vo = new ExportVO(first, packagePath, getCompletedState(pc.packageEntries.size(),
                    (hasErrs && contents.isEmpty()) || errsByNotifId.containsKey(packagePath)));
            if (ret.isEmpty()) {
                vo.setId(first.getId());
            }
            ret.add(vo);
        }
        return ret;
    }

    private int getCompletedState(int count, boolean hasErr) {
        return count == 0 ? ExportEntity.FAILED : (hasErr ? ExportEntity.COMPLETED_WITH_ERRS : ExportEntity.COMPLETED);
    }

    public void run() throws CmsException {
        int count = 0;
        for (int id : items) {
            try {
                export(id);
                if (++count % DbConstants.DB_PACK_SIZE == 0) {
                    LOG.info(String.format("%d records were exported ...", count));
                }
            } catch (Exception e) {
                appendError(mainArchivePath, String.format("Can't perform export for review [%s], %s", id, e));
            }
        }
        if (!entries.isEmpty()) {
            output = createOutput(mainArchivePath);
            fileCnt = storeArchive(entries, output, mainArchivePath, true);
        }
        for (PackageContent pc : packageContents) {
            if (!pc.packageEntries.isEmpty()) {
                String path = buildExtraPackagePath(pc);
                IZipOutput out = createOutput(path);
                pc.store(out);
                storeArchive(pc.packageEntries, out, path, false);
            }
        }
    }

    private String buildExtraPackagePath(PackageContent pc) {
        if (pc.getArchivePath() == null) {
            String storeFolder = mainArchivePath.substring(0, mainArchivePath.lastIndexOf(FilePathCreator.SEPARATOR));
            pc.setArchivePath(storeFolder + FilePathCreator.SEPARATOR + pc.getPackageName(mainPackageName));
        }
        return pc.getArchivePath();
    }

    private int storeArchive(List<ArchiveEntry> entries, IZipOutput output, String zipName, boolean createReport) {
        int ret = 0;
        Set<String> paths = new HashSet<>();
        for (ArchiveEntry entry : entries) {
            if (!paths.add(entry.getPathInArchive())) {
                continue;
            }
            InputStream is = null;
            try {
                is = StringUtils.isEmpty(entry.getContent())  ? new FileInputStream(new File(entry.getPathToContent()))
                        : new ByteArrayInputStream(entry.getContent().getBytes(StandardCharsets.UTF_8));
                output.put(entry.getPathInArchive(), is);
                ret++;
                
            } catch (IOException e) {
                appendError(zipName, String.format("Failed to add file {%s} to archive: [%s], %s\n",
                        entry.getPathToContent(), entry.getPathInArchive(), e));
            } finally {
                IOUtils.closeQuietly(is);
            }
        }
        if (entries.isEmpty()) {
            return ret;
        }
        if (createReport) {
            putReport(output, "Files in archive: " + fileCnt, "report.txt");
        }
        try {
            output.close();
        } catch (IOException e) {
            appendError(zipName, String.format("Failed to create %s, %s\n", zipName, e));
            log.error("Can't close archive " + zipName);
        }
        return ret;
    }

    protected void export(int id) throws Exception {
        RecordEntity entity = rs.getRecord(id);
        if (entity == null) {
            throw new Exception("record has not found");
        }
        if (!contents.isEmpty()) {
            ClDbEntity db = entity.getDb();
            RecordManifest manifest = new RecordManifest(db.getIssue().getId(), db.getTitle(), entity.getName(),
                    entity.getRecordPath());
            manifest.setJats(entity.getRecordPath().contains(JatsPackage.JATS_FOLDER));
            for (IContentExporter content : contents) {
                entries.addAll(content.process(manifest));
            }
        }
        for (PackageContent packageContent : packageContents) {
            packageContent.export(packageContent.process(entity));
        }
    }

    protected void appendError(PackageContent pc, String err) {
        appendError(buildExtraPackagePath(pc), err);
    }

    private void appendError(String key, String err) {
        StringBuilder sb = errsByNotifId.computeIfAbsent(key, f -> new StringBuilder(err.length()));
        sb.append(err);
        sb.append(";\n");
        log.warn(err);
    }

    public Map<String, StringBuilder> getErrors() {
        return errsByNotifId;
    }

    public int getErrorCount() {
        int errCount = 0;
        for (Map.Entry<String, StringBuilder> entry: errsByNotifId.entrySet()) {
            if (!MessageSender.EXPORT_COMPLETED_ID.equals(entry.getKey())
                    && entry.getValue().toString().trim().length() > 0) {
                errCount++;
            }
        }
        return errCount;
    }

    public String buildExportPaths() {
        String prefix = CochraneCMSPropertyNames.getWebLoadingUrl() + "DataTransferer/";
        String mainPath = fileCnt > 0 ? prefix + mainArchivePath : null;

        StringBuilder paths = new StringBuilder();
        if (mainPath != null) {
            paths.append("\n").append(mainPath);
        }
        for (PackageContent packageContent : packageContents) {
            if (!packageContent.packageEntries.isEmpty() && packageContent.getArchivePath() != null) {
                paths.append("\n").append(prefix).append(packageContent.getArchivePath());
            }
        }
        return paths.length() == 0 ? CochraneCMSPropertyNames.getNotAvailableMsg() : paths.toString();
    }

    private void putReport(IZipOutput output, String report, String reportName) {
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(report.getBytes());
            output.put(reportName, bais);
            bais.close();

        } catch (IOException e) {
            errsByNotifId.get(StringUtils.EMPTY).append("Failed to create ").append(
                    reportName).append(" ").append(e).append("\n");
        }
    }

    protected interface IContentExporter {
        List<ArchiveEntry> process(RecordManifest manifest);
        List<ArchiveEntry> process(EntireDBEntity entity);
    }

    /**  */
    private abstract class IssueContent implements IContentExporter {
        private static final String EXPORT_ERR_TEMPL = "Can't perform export {%s} for review [%s]. %s\n";
        protected String type;
        protected StringBuilder errs;

        protected List<ArchiveEntry> checkAssets(List<ArchiveEntry> entries, String recName, boolean checkEmpty,
                                                 PackageContent pc) {
            StringBuilder tmpErrs = new StringBuilder();
            boolean success;
            if (checkEmpty && entries.isEmpty()) {
                tmpErrs.append("Content for this type of export is not available. ");
            }
            for (ArchiveEntry entry : entries) {
                try {
                    success = rps.isFileExists(entry.getPathToContent());
                } catch (Exception e) {
                    success = false;
                }
                if (!success) {
                    tmpErrs.append("File [").append(entry.getPathToContent()).append("] doesn't exist. ");
                }
            }
            if (tmpErrs.length() == 0) {
                return entries;
            }
            appendError(pc != null ? buildExtraPackagePath(pc) : StringUtils.EMPTY,
                        String.format(EXPORT_ERR_TEMPL, type, recName, tmpErrs));
            return Collections.emptyList();
        }

        public List<ArchiveEntry> process(EntireDBEntity entity) {
            return new ArrayList<>();
        }
    }

    private abstract class TraditionalContent extends IssueContent {
        protected final List<String> groups = new ArrayList<>();

        public TraditionalContent() {
            super();
            if (!errsByNotifId.containsKey(MessageSender.EXPORT_COMPLETED_ID)) {
                errsByNotifId.put(MessageSender.EXPORT_COMPLETED_ID, new StringBuilder());
            }
            errs = errsByNotifId.get(MessageSender.EXPORT_COMPLETED_ID);
        }

        public List<ArchiveEntry> process(RecordManifest manifest) {
            String recName = manifest.getRecord();
            List<ArchiveEntry> tmpEntries = new ArrayList<>();
            for (String group : groups) {
                List<String> uris = manifest.getUris(group);
                for (String uri : uris) {
                    if (isValid(uri, recName)) {
                        String pathInArch = getPathInArch(uri, recName, group);
                        tmpEntries.add(new ArchiveEntry(pathInArch, rps.getRealFilePath(uri)));
                    }
                }
            }
            return checkAssets(tmpEntries, recName, true, null);
        }

        protected abstract boolean isValid(String uri, String recName);

        protected String getPathInArch(String uri, String recName, String group) {
            return (recName + "/" + group + "/" + uri.substring(uri.indexOf(recName) + recName.length() + 1));
        }

        protected boolean isPicture(String uri) {
            return FileUtils.isGraphicExtension("." + FilenameUtils.getExtension(uri));
        }
    }

    protected class WML21PackageContent extends PackageContent {
        private static final String TYPE = "Initial Package (WileyML21)";
        private final String packagePath;

        WML21PackageContent(int issueYear, int issueNumber, String dbName) {
            super(issueYear, issueNumber, dbName);
            packagePath = FilePathCreator.buildSBNPackagePath(dbName);
            type = TYPE;
        }

        WML21PackageContent(IssueWrapper issue, String dbName) {
            super(issue, dbName);
            packagePath = FilePathCreator.buildSBNPackagePath(dbName);
            type = TYPE;
        }

        @Override
        protected List<ArchiveEntry> process(String recordName, String relPath) {
            List<ArchiveEntry> tmpEntries = new ArrayList<>();
            if (!relPath.contains(JatsPackage.JATS_FOLDER) && !relPath.contains(FilePathCreator.ML3G_DIR)) {
                String sourcePath = editorial    // editorial should be always exported for 3G package
                    ? (entire ? FilePathBuilder.ML3G.getPathToEntireMl3gRecord(dbName, recordName, false)
                        : FilePathBuilder.ML3G.getPathToMl3gRecord(issue.getId(), dbName, recordName)) : relPath;
                String archivePath = FilePathCreator.buildPackagePathRecord(packagePath, recordName);
                tmpEntries.add(new ArchiveEntry(archivePath, rps.getRealFilePath(sourcePath)));
                String srcDirPath = relPath.substring(0, relPath.length() - Extensions.XML.length());
                processDir(rps.getFilesFromDir(srcDirPath), packagePath + recordName, tmpEntries);
            }
            return checkAssets(tmpEntries, recordName, true, this);
        }

        @Override
        protected String getPackageName(String postfix) {
            return super.getPackageName(editorial
                    ? postfix.replace(Extensions.ZIP, PackageChecker.WML3G_POSTFIX + Extensions.ZIP) : postfix);
        }

        private void processDir(File[] files, String baseArchivePath, List<ArchiveEntry> ret) {
            if (files == null) {
                return;
            }
            for (File fl : files) {
                if (!fl.isFile()) {
                    processDir(fl.listFiles(), baseArchivePath + FilePathCreator.SEPARATOR + fl.getName(), ret);
                } else {
                    String realFilePath = fl.getAbsolutePath();
                    ret.add(new ArchiveEntry(baseArchivePath + FilePathCreator.SEPARATOR + fl.getName(), realFilePath));
                }
            }
        }
    }

    protected class JatsPackageContent extends TranslationPackageContent {
        private static final String TYPE = "Initial Package (JATS)";

        JatsPackageContent(int issueYear, int issueNumber, String dbName) {
            super(issueYear, issueNumber, dbName, true);
            type = TYPE;
        }

        JatsPackageContent(IssueWrapper issue, String dbName) {
            super(issue, dbName, true);
            type = TYPE;
        }

        protected boolean isScheduled() {
            return CmsUtils.isScheduledIssueNumber(issue.getFullNumber());
        }

        @Override
        protected List<ArchiveEntry> process(String recName, String relPath) {
            return processJats(recName, relPath);
        }

        protected List<ArchiveEntry> processJats(String recName, String relPath) {
            List<ArchiveEntry> ret = new ArrayList<>();
            if (entire || relPath.contains(ENTIRE)) {
                buildJatsRecordPathEntire(recName, ret);
            } else {
                buildJatsRecordPath(recName, relPath, ret);
            }
            return checkAssets(ret, recName, true, this);
        }

        private void buildJatsRecordPathEntire(String recName, List<ArchiveEntry> entries) {
            ICDSRMeta re = getRecordMetadataEntity(recName, false);
            if (re == null || !re.isJats()) {
                return;
            }
            String groupName = re.getGroupSid();
            String pubName = RevmanMetadataHelper.buildPubName(recName, re.getPubNumber());
            String pathToJatsEntire = rps.getRealFilePath(FilePathBuilder.JATS.getPathToEntireDir(dbName, recName));

            File tempDar = RepositoryUtils.createDarPackageWithReview(pubName, pathToJatsEntire, 0);
            if (tempDar != null) {
                entries.add(new ArchiveEntry(getGroupArchivePath(groupName, ArchiePackage.REVIEW_FOLDER)
                                                     + tempDar.getName(), tempDar.getAbsolutePath()));
            } else {
                appendError(this, String.format("no entire JATS sources from %s [%s],  (%s)",
                        pathToJatsEntire, recName, getPackageName(mainPackageName)));
                return;
            }

            String pathToTopicsEntire = rps.getRealFilePath(FilePathBuilder.getPathToTopics(groupName));
            if (pathToTopicsEntire != null) {
                entries.add(new ArchiveEntry(groupName + FilePathCreator.SEPARATOR + Constants.TOPICS_SOURCE,
                                             pathToTopicsEntire));
            } else {
                appendError(this, String.format("no topics found in entire db for [%s]", recName));
            }
        }

        private void buildJatsRecordPath(String recName, String relPath, List<ArchiveEntry> entries) {
            ICDSRMeta re = getRecordMetadataEntity(recName, false);
            if (re == null || !re.isJats()) {
                return;
            }
            String[] relPathParts = relPath.split("[/\\\\]");
            int packageId = Integer.parseInt(relPathParts[3]);
            String groupName = re.getGroupSid();
            String pubName = RevmanMetadataHelper.buildPubName(recName, re.getPubNumber());
            String pathToJatsIssue = rps.getRealFilePath(
                    FilePathBuilder.JATS.getPathToSrcDir(issue.getId(), packageId, recName));

            File tempDar = RepositoryUtils.createDarPackageWithReview(pubName, pathToJatsIssue, issue.getId());
            if (tempDar != null) {
                entries.add(new ArchiveEntry(getGroupArchivePath(groupName, ArchiePackage.REVIEW_FOLDER)
                                                     + tempDar.getName(), tempDar.getAbsolutePath()));
            } else {
                appendError(this, String.format("no JATS sources from %s [%s], (%s)",
                        pathToJatsIssue, recName, getPackageName(mainPackageName)));
                return;
            }
            if (!CmsUtils.isScheduledIssue(issue.getId())) {
                String pathToTopicsIssue = rps.getRealFilePath(
                        FilePathBuilder.getPathToTopics(issue.getId(), groupName));
                if (pathToTopicsIssue != null) {
                    entries.add(new ArchiveEntry(groupName + FilePathCreator.SEPARATOR + Constants.TOPICS_SOURCE,
                            pathToTopicsIssue));
                } else {
                    appendError(this, String.format("no topics found for [%s]", recName));
                }
            }
        }

        @Override
        protected String getPackageName(String postfix) {
            return ArchiePackage.buildPackageName(issue.getYear(), issue.getNumber(), addJatsPostfix(postfix), "");
        }

        @Override
        protected void store(IZipOutput out) {
            putReport(out, "", ArchiePackage.SUCCESS_LOG);
        }
    }

    protected class TranslationPackageContent extends PackageContent {
        private static final String TYPE = "Initial Package (Translation)";

        private boolean jats;
        private boolean omitted = false;

        TranslationPackageContent(IssueWrapper issue, String dbName, boolean fromJats) {
            super(issue, dbName);
            init(fromJats);
        }

        TranslationPackageContent(int issueYear, int issueNumber, String dbName, boolean fromJats) {
            super(issueYear, issueNumber, dbName);
            init(fromJats);
        }

        private void init(boolean fromJats) {
            jats = fromJats;
            type = TYPE;
        }

        @Override
        public boolean isOmitted() {
            return omitted;
        }

        @Override
        protected List<ArchiveEntry> process(String recName, String relPath) {
            ICDSRMeta re = getRecordMetadataEntity(recName, false);
            if (re == null) {
                return Collections.emptyList();
            }
            List<ArchiveEntry> ret = new ArrayList<>();
            List<DbRecordVO> list = CochraneCMSBeans.getRecordManager().getLastTranslations(
                    RecordHelper.buildRecordNumberCdsr(recName));
            TranslatedAbstractsHelper helper = new TranslatedAbstractsHelper();
            list.forEach(t -> helper.makeSourceArchiveEntry(recName, RevmanMetadataHelper.parsePubNumber(t.getLabel()),
                t.getLanguage(), getGroupArchivePath(re.getGroupSid(), ArchiePackage.TRANSLATION_FOLDER), ret, jats));
            if (!ret.isEmpty()) {
                hasTa = true;
            } else if (!jats || hasTa) {
                omitted = true;    // this package is empty and can be excluded
            } else {
                appendError(this, String.format("no translation sources for [%s],  (%s)", recName,
                        getPackageName(mainPackageName)));
            }
            return checkAssets(ret, recName, !omitted, this);
        }

        @Override
        protected String getPackageName(String postfix) {
            return ArchiePackage.buildPackageName(issue.getYear(), issue.getNumber(),
                    PackageChecker.TA_SUFFIX + addJatsPostfix(postfix), "");
        }

        protected String addJatsPostfix(String postfix) {
            return jats ? postfix.replace(Extensions.ZIP, PackageChecker.JATS_POSTFIX + Extensions.ZIP) : postfix;
        }

        protected String getGroupArchivePath(String groupName, String groupFolder) {
            return groupName + FilePathCreator.SEPARATOR + groupFolder + FilePathCreator.SEPARATOR;
        }

        protected ICDSRMeta getRecordMetadataEntity(String cdNumber, boolean checkIssue) {
            ICDSRMeta re = rs.findLatestMetadata(cdNumber, false);
            if (re == null) {
                appendError(this, String.format("no metadata [%s]  (%s)", cdNumber, getPackageName(mainPackageName)));

            } else if (checkIssue && re.getIssue() > issue.getFullNumber()) {
                appendError(this, String.format("%s - is not the latest version, it was already uploaded in Issue %d\n",
                        cdNumber, issue.getFullNumber()));
                return null;
            }
            return re;
        }
    }

    protected class WRJatsPackageContent extends JatsPackageContent {
        private static final String TYPE = "WR Jats Package";

        private JatsHelper jatsHelper = new JatsHelper();

        WRJatsPackageContent(IssueWrapper issue, String dbName) {
            super(issue, dbName);
            type = TYPE;
        }

        @Override
        public List<ArchiveEntry> process(RecordEntity entity) {
            List<ArchiveEntry> ret = new ArrayList<>();
            String recName =  entity.getName();
            List<PublishedAbstractEntity> list = CochraneCMSBeans.getRecordManager().getWhenReadyByDeliveryPackage(
                    recName, entity.getDeliveryFile().getId());
            if (list.isEmpty()) {
                appendError(this,
                    String.format("%s - can't find When Ready record registered in Issue %d to re-process\n",
                        recName, issue.getFullNumber()));
            } else {
                list.forEach(r -> processWR(entity.getName(), entity.getMetadata(), r, ret));
            }
            return ret;
        }

        protected void processWR(String recName, ICDSRMeta rme, PublishedAbstractEntity pae, List<ArchiveEntry> ret) {
            if (!rme.isScheduled() && getRecordMetadataEntity(recName, true) == null) {
                return;
            }

            String groupName = rme.getGroupSid();
            String pubName = RevmanMetadataHelper.buildPubName(recName, rme.getPubNumber());
            Integer dfId = pae.getInitialDeliveryId();

            if (pae.hasLanguage()) {
                String path = ContentLocation.ISSUE.getPathToJatsTA(issue.getId(), dfId,
                        null, pae.getLanguage(), recName);
                TranslatedAbstractVO tvo = new TranslatedAbstractVO(recName);
                tvo.setPubNumber(pae.getPubNumber());
                String err = jatsHelper.extractMetadata(tvo, path + recName + Extensions.XML, rps);
                if (err == null) {
                    buildRecordPath(recName, FilePathBuilder.buildTAName(tvo.getOriginalLanguage(), pubName),
                            groupName, path, ArchiePackage.TRANSLATION_FOLDER, ret);
                } else {
                    appendError(this, err);
                }
            } else {
                String path = ContentLocation.ISSUE.getPathToJatsSrcDir(issue.getId(), dbName, dfId, null, recName);
                buildRecordPath(recName, pubName, groupName, path, ArchiePackage.REVIEW_FOLDER, ret);
            }
            if (!rme.isScheduled()) {
                String pathToTopics = rps.getRealFilePath(FilePathBuilder.getPathToTopics(issue.getId(), groupName));
                if (pathToTopics != null) {
                    ret.add(new ArchiveEntry(groupName + FilePathCreator.SEPARATOR + Constants.TOPICS_SOURCE,
                            pathToTopics));
                }
            }
        }

        private void buildRecordPath(String recName, String pubName, String groupName, String path,
                                     String reviewFolder, List<ArchiveEntry> entries) {
            File tmpDar = RepositoryUtils.createDarPackageWithReview(pubName, rps.getRealFilePath(path), issue.getId());
            if (tmpDar != null) {
                entries.add(new ArchiveEntry(getGroupArchivePath(groupName, reviewFolder)
                        + tmpDar.getName(), tmpDar.getAbsolutePath()));
            } else {
                appendError(this, String.format("no JATS sources from %s [%s] found", path, recName));
            }
        }

        protected String getPackageName(String postfix) {
            String autPostfix = PackageChecker.AUT_REPROCESS_SUFFIX + "_" + postfix;
            if (!isScheduled() && (CochraneCMSPropertyNames.getUploadCDSRIssue() == Constants.NO_ISSUE
                    || CochraneCMSPropertyNames.getUploadCDSRIssue() == Constants.UNDEF)) {
                IssueDate iDate = new IssueDate(CmsUtils.getCochraneDownloaderDateTime());
                return ArchiePackage.buildPackageName(iDate.issueYear, iDate.issueMonth, autPostfix, "");
            }
            return super.getPackageName(autPostfix);
        }
    }

    protected class WRPackageContent extends RevmanPackageContent {
        private static final String TYPE = "WR Package";

        WRPackageContent(IssueWrapper issue, String dbName) {
            super(issue, dbName);
            convertOldRevman = false;
            type = TYPE;
        }

        @Override
        public List<ArchiveEntry> process(RecordEntity entity) {
            List<ArchiveEntry> ret = new ArrayList<>();
            String recName =  entity.getName();
            List<PublishedAbstractEntity> list = CochraneCMSBeans.getRecordManager().getWhenReadyByDeliveryPackage(
                    recName, entity.getDeliveryFile().getId());
            if (list.isEmpty()) {
                appendError(this,
                    String.format("%s - cannot find When Ready record registered in Issue %d to re-process\n",
                            recName, issue.getFullNumber()));
            } else if (getRecordMetadataEntity(recName, true) != null) {
                processWR(entity.getName(), list.get(0), ret);
            }
            return ret;
        }

        protected String getPackageName(String postfix) {
            String autPostfix = PackageChecker.AUT_REPROCESS_SUFFIX + "_" + postfix;
            if (CochraneCMSPropertyNames.getUploadCDSRIssue() == Constants.NO_ISSUE
                    || CochraneCMSPropertyNames.getUploadCDSRIssue() == Constants.UNDEF) {
                IssueDate iDate = new IssueDate(CmsUtils.getCochraneDownloaderDateTime());
                return ArchiePackage.buildPackageName(iDate.issueYear, iDate.issueMonth, autPostfix, "");
            }
            return super.getPackageName(autPostfix);
        }
    }

    protected class RevmanPackageContent extends TranslationPackageContent {
        private static final String TYPE = "Initial Package (RevMan)";
        protected boolean convertOldRevman = Property.getBooleanProperty("cochrane.export.convert-old-revman", true);
        private DocumentLoader dl = new DocumentLoader();
        private Map<String, Document> docMetadata = new HashMap<>();
        private Map<String, Pair<Document, List<Element>>> resultMetadata = new HashMap<>();
        private RevmanMetadataHelper rh;

        RevmanPackageContent(int issueYear, int issueNumber, String dbName) {
            super(issueYear, issueNumber, dbName, false);
            type = TYPE;
            init();
        }

        RevmanPackageContent(IssueWrapper issue, String dbName) {
            super(issue, dbName, false);
            type = TYPE;
            init();
        }

        @Override
        protected List<ArchiveEntry> process(String recName, String relPath) {
            return processRevman(recName, relPath);
        }

        protected List<ArchiveEntry> processRevman(String recName, String relPath) {
            List<ArchiveEntry> ret = new ArrayList<>();
            if (entire) {
                buildRevmanRecordPathEntire(recName, ret);
            } else {
                String srcDirPath = relPath.substring(0, relPath.length() - recName.length() - Extensions.XML.length());
                String abcRevmanDirPath = rps.getRealFilePath(srcDirPath + FilePathCreator.REVMAN_DIR);
                String abcInputDirPath = getIssueInputDir(abcRevmanDirPath);
                buildRevmanRecordPath(new File(abcRevmanDirPath), abcInputDirPath, recName, ret);
            }
            return checkAssets(ret, recName, true, this);
        }

        protected void processWR(String recName, PublishedAbstractEntity pae, List<ArchiveEntry> ret) {

            String revmanPath = FilePathBuilder.getPathToRevmanPackage(issue.getId(), "" + pae.getInitialDeliveryId());
            RevmanSource source = RecordHelper.findInitialSourcesForRevmanPackage(recName, revmanPath, rps);

            if (source == null || !source.isExist()) {
                appendError(this, String.format("no revman source from %s [%s]", revmanPath, recName));
                return;
            }

            File topic = new File(FilePathCreator.getRevmanTopicSource(source.groupPath));
            addMetadataAndTopic(source.group, source.groupPath, recName,
                source.revmanFile != null ? source.revmanFile.getAbsolutePath() : null,
                rps.getRealFilePath(FilePathCreator.getDirPathForRevmanEntire()), ret, topic);

            String pathTa = source.group + FilePathCreator.SEPARATOR + ArchiePackage.TRANSLATION_FOLDER
                + FilePathCreator.SEPARATOR;
            for (Map.Entry<String, File> ta : source.taFiles.entrySet()) {
                File fl = ta.getValue();
                ret.add(new ArchiveEntry(pathTa + fl.getName(), fl.getAbsolutePath()));
            }
        }

        @Override
        protected String getPackageName(String postfix) {
            return ArchiePackage.buildPackageName(issue.getYear(), issue.getNumber(), postfix, "");
        }

        @Override
        protected void store(IZipOutput out) {
            putReport(out, "", ArchiePackage.SUCCESS_LOG);
            if (resultMetadata.isEmpty()) {
                return;
            }
            XMLOutputter xout = new XMLOutputter(Format.getPrettyFormat());
            for (String groupName : resultMetadata.keySet()) {
                Pair<Document, List<Element>> pair = resultMetadata.get(groupName);
                if (pair.second.isEmpty() || pair.second.contains(null)) {
                    continue;
                }
                Element root = pair.first.getRootElement();
                root.removeContent();
                for (Element el : pair.second) {
                    Element parent = el.getParentElement();
                    if (parent != null) {
                        parent.removeContent();
                    }
                    root.addContent(el);
                }
                putReport(out, xout.outputString(pair.first), FilePathCreator.getRevmanMetadataSource(groupName));
            }
        }

        private void init() {
            if (convertOldRevman) {
                rh = new RevmanMetadataHelper(rs, dl);
            }
        }

        private void buildRevmanRecordPathEntire(String recName, List<ArchiveEntry> entries) {
            ICDSRMeta re = getRecordMetadataEntity(recName, false);
            if (re == null || re.isJats()) {
                return;
            }
            String abcRevmanPath = rps.getRealFilePath(FilePathCreator.getDirPathForRevmanEntire());
            String groupName = re.getGroupSid();
            String abcGrPath = abcRevmanPath + FilePathCreator.SEPARATOR + groupName;
            Document doc = docMetadata.get(abcGrPath);
            if (doc == null) {
                doc = new Document();
                Element group = RevmanMetadataHelper.createMetadataGroupElement(groupName, re.getGroupTitle(), issue);
                doc.addContent(group);
                docMetadata.put(abcGrPath, doc);
            }
            Pair<Document, List<Element>> pair = resultMetadata.get(groupName);
            if (pair == null) {
                pair = new Pair<>(doc, new ArrayList<>());
                resultMetadata.put(groupName, pair);
                addTopic(entries, abcRevmanPath, groupName, null);
            }
            pair.second.add(rh.getMetadataElement4Export(re, null));
            entries.add(new ArchiveEntry(getGroupArchivePath(groupName, ArchiePackage.REVIEW_FOLDER)
                + RevmanMetadataHelper.buildPubName(recName, re.getPubNumber()) + Extensions.XML,
                    getGroupArchivePath(abcGrPath, ArchiePackage.REVIEW_FOLDER) + recName + Extensions.XML));
        }

        private void buildRevmanRecordPath(File abcRevmanDir, String abcIssueInputDir, String recName,
                                           List<ArchiveEntry> list) {
            ICDSRMeta re = getRecordMetadataEntity(recName, false);
            if (re == null || re.isJats()) {
                return;
            }
            File[] groups = abcRevmanDir.listFiles();
            File record = null;
            if (groups != null) {
                for (File group : groups) {
                    if (!group.isDirectory()) {
                        continue;
                    }
                    String groupPath = group.getAbsolutePath();
                    record = RevmanMetadataHelper.findRevmanRecord(groupPath, recName);
                    if (record != null) {
                        addMetadataAndTopic(group.getName(), groupPath, recName, record.getAbsolutePath(),
                                abcIssueInputDir, list, null);
                        return;
                    }
                }
            }
            log.warn(String.format("no metadata %s from %s, it will be taken from entire", recName, abcRevmanDir));
            buildRevmanRecordPathEntire(recName, list);
        }

        private void addMetadataAndTopic(String groupName, String groupPath, String recName, String recordPath,
                                         String inputDir, List<ArchiveEntry> tmpEntries, File localTopic) {
            Pair<Document, List<Element>> pair = readMetadata(groupName, groupPath, inputDir, tmpEntries, localTopic,
                    recordPath != null);
            if (pair == null || pair.first == null || recordPath == null) {
                return;
            }
            try {
                for (Object obj : XPath.selectNodes(pair.first, RevmanMetadataHelper.REVIEW_XPATH)) {
                    Element el = ((Element) obj);
                    String id = el.getAttributeValue(RevmanMetadataHelper.CD_NUMBER_ATTR);
                    int pubNum = RevmanMetadataHelper.parsePubNumber(RevmanMetadataHelper.parseDoi(id, pair.first));
                    if (recName.equals(id)) {
                        if (convertOldRevman) {
                            rh.updateMetadataElement4Export(el);
                        }
                        pair.second.add(el);
                        tmpEntries.add(new ArchiveEntry(getGroupArchivePath(groupName, ArchiePackage.REVIEW_FOLDER)
                            + RevmanMetadataHelper.buildPubName(recName, pubNum) + Extensions.XML, recordPath));
                        return;
                    }
                }
            } catch (Exception e) {
                log.error(e.getMessage());
            }
            appendError(this, String.format("no metadata from %s [%s] (%s)",
                    groupPath, recName, getPackageName(mainPackageName)));
        }

        private String getIssueInputDir(String path) {
            int ind = path.indexOf(dbName);
            if (ind == -1) {
                log.warn(String.format("no root db %s directory from %s", dbName, path));
                return null;
            }
            return FilePathCreator.getRevmanPackageInputDirPath(path.substring(0, ind + dbName.length()));
        }

        private void addTopic(List<ArchiveEntry> tmpEntries, String basePath, String groupName, File localTopic) {
            File fl = new File(FilePathCreator.getRevmanTopicSource(basePath + FilePathCreator.SEPARATOR + groupName));
            if (fl.exists() && localTopic == null) {
                // add from entire or issue input
                tmpEntries.add(new ArchiveEntry(FilePathCreator.getRevmanTopicSource(groupName), fl.getAbsolutePath()));

            } else if (localTopic != null && localTopic.exists()
                    && (!fl.exists() || localTopic.lastModified() > fl.lastModified())) {
                // add from local because local topic has been updated
                tmpEntries.add(new ArchiveEntry(FilePathCreator.getRevmanTopicSource(groupName),
                        localTopic.getAbsolutePath()));
            }
        }

        private Pair<Document, List<Element>> readMetadata(String groupName, String groupPath, String inputDir,
            List<ArchiveEntry> tmpEntries, File localTopic, boolean metadataExist) {

            Pair<Document, List<Element>> ret = null;
            Document metadataXmlDoc = docMetadata.get(groupPath);
            if (metadataExist && metadataXmlDoc == null) {
                String metadataPath = FilePathCreator.getRevmanMetadataSource(groupPath);
                File fl = new File(metadataPath);
                if (!fl.exists()) {
                    appendError(this, String.format("no metadata from %s (%s)",
                            metadataPath, getPackageName(mainPackageName)));
                    return null;
                }
                try {
                    metadataXmlDoc = dl.load(fl);
                    docMetadata.put(groupPath, metadataXmlDoc);
                } catch (Exception e) {
                    appendError(this, String.format("no metadata.xml from %s (%s)",
                            metadataPath, getPackageName(mainPackageName)));
                }
            }
            Pair<Document, List<Element>> pair = resultMetadata.get(groupName);
            if (pair == null) {
                if (metadataXmlDoc != null) {
                    pair = new Pair<>(metadataXmlDoc, new ArrayList<>());
                    resultMetadata.put(groupName, pair);
                    addTopic(tmpEntries, inputDir, groupName, localTopic);
                    ret = pair;
                }
            } else {
                ret = new Pair<>(metadataXmlDoc, pair.second);
            }
            return ret;
        }
    }

    protected abstract class PackageContent extends IssueContent {
        protected final IssueVO issue;
        protected final String dbName;
        protected String archivePath;
        protected final List<ArchiveEntry> packageEntries = new ArrayList<>();
        protected final boolean editorial;
        protected final boolean entire;

        PackageContent(IssueWrapper iw, String dbName) {
            this(iw.getYear(), iw.getNumber(), dbName);
            issue.setId(iw.getId());
        }

        PackageContent(int issueYear, int issueNumber, String dbName) {
            entire = isEntire(issueYear, issueNumber);
            if (entire) {
                Calendar cl = Now.getNowUTC();
                issue = new IssueVO(cl.get(Calendar.YEAR), Now.getCalendarMonth(cl), null);
            } else {
                issue = new IssueVO(issueYear, issueNumber, null);
            }
            this.dbName = dbName;
            editorial =  CochraneCMSPropertyNames.getEditorialDbName().equals(dbName);
            init();
        }

        protected abstract List<ArchiveEntry> process(String recordName, String relPath);

        private boolean isEntire(int issueYear, int issueNumber) {
            return issueYear == 0 || issueNumber == 0;
        }

        protected void export(List<ArchiveEntry> list) {
            packageEntries.addAll(list);
        }

        protected void store(IZipOutput out) {
        }

        protected String getPackageName(String postfix) {
            return PackageChecker.buildCommonPrefix(dbName, issue.getYear(), CmsUtils.getIssueMonth(issue.getNumber()))
                    + postfix;
        }

        protected void setArchivePath(String path) {
            archivePath = path;
        }

        public String getArchivePath() {
            return archivePath;
        }

        public List<ArchiveEntry> process(RecordEntity entity) {
            return process(entity.getName(), entity.getRecordPath());
        }

        @Override
        public List<ArchiveEntry> process(EntireDBEntity entity) {
            String recordName = entity.getName();
            return process(recordName, FilePathCreator.getFilePathToSourceEntire(dbName, recordName));
        }

        public List<ArchiveEntry> process(RecordManifest recResWrapper) {
            String recordName = recResWrapper.getRecord();
            return process(recordName, FilePathCreator.getFilePathToSourceEntire(dbName, recordName));
        }

        public boolean isOmitted() {
            return false;
        }

        private void init() {
            errsByNotifId.put(MessageSender.EXPORT_COMPLETED_ID, new StringBuilder());
            errs = errsByNotifId.get(MessageSender.EXPORT_COMPLETED_ID);
        }
    }

    protected class Ml3gForIpadIssue extends IssueContent implements IContentRoom {
        private static final String TYPE = "3G Package for iPad App";

        private final int issueId;
        private final int fullIssueNumber;
        private final String dbName;
        private final boolean outdatedIssue;

        public Ml3gForIpadIssue(int issueId, String dbName, int fullIssueNumber) {
            this(issueId, dbName, fullIssueNumber, PublishHelper.isIssueOutdated(issueId, dbName,
                    CochraneCMSBeans.getRecordCache().getLastDatabases()));
        }

        public Ml3gForIpadIssue(int issueId, String dbName) {
            this(issueId, dbName, 0, false);
        }

        private Ml3gForIpadIssue(int issueId, String dbName, int fullIssueNumber, boolean outdatedIssue) {
            type = TYPE;
            errs = errsByNotifId.computeIfAbsent(MessageSender.EXPORT_3G_COMPLETED_ID, f -> new StringBuilder());
            this.issueId = issueId;
            this.dbName = dbName;
            this.fullIssueNumber = fullIssueNumber;
            this.outdatedIssue = outdatedIssue;
        }

        @Override
        public String getDbName() {
            return dbName;
        }

        @Override
        public Integer getIssueId() {
            return issueId;
        }

        public List<ArchiveEntry> process(RecordManifest recResWrapper) {
            String recName = recResWrapper.getRecord();
            List<ArchiveEntry> tmpEntries;
            StringBuilder tmpErrs = new StringBuilder();
            Ml3gXmlAssets assets = new Ml3gXmlAssets();
            assets.setXmlUri(FilePathCreator.getFilePathForMl3gXml(issueId, dbName, recName));
            assets.setAssetsUris(PublishHelper.getPublishContentUris(this, Constants.UNDEF, recName,
                    isRecordOutdated(recName, recResWrapper.getRecordPath()), false, ContentLocation.ISSUE, tmpErrs));
            if (tmpErrs.length() > 0) {
                errs.append("Failed to perform export {").append(TYPE).append("} for review [").append(recName)
                        .append(BRACKET_AND_COMMA_MSG).append(tmpErrs).append("\n");
                tmpEntries = new ArrayList<>(0);
            } else {
                tmpEntries = getEntries(recName, assets);
            }
            return tmpEntries;
        }

        protected boolean isRecordOutdated(String recName, String recPath) {
            if (outdatedIssue && FilePathBuilder.isEntirePath(recPath)) {
                ICDSRMeta meta = rs.findLatestMetadata(recName, false);
                return meta != null && meta.getIssue() > fullIssueNumber;
            }
            return false;
        }

        protected List<ArchiveEntry> getEntries(String recName, Ml3gXmlAssets ass) {
            List<ArchiveEntry> ret = new ArrayList<>();
            ret.add(new ArchiveEntry(getPathInArchive(recName, ass.getXmlUri(), false),
                                     rps.getRealFilePath(ass.getXmlUri())));
            for (String assetUri : ass.getAssetsUris()) {
                String filePath;
                String assetPathInArch;
                boolean isJatsAsset = assetUri.contains(JatsPackage.JATS_FOLDER);
                if (isJatsAsset) {
                    filePath = rps.getRealFilePath(JatsMl3gAssetsManager.getRealAssetUri(assetUri));
                    assetPathInArch = getPathInArchive(recName, JatsMl3gAssetsManager.getMappedAsset(assetUri), true);
                } else {
                    filePath = rps.getRealFilePath(assetUri);
                    assetPathInArch = getPathInArchive(recName, assetUri, false);
                }
                ret.add(new ArchiveEntry(assetPathInArch, filePath));
            }
            return checkAssets(ret, recName, true, null);
        }

        protected String getPathInArchive(String recName, String uri, boolean isJatsAsset) {
            String pathToAsset = uri.substring(uri.indexOf(recName));
            return uri.endsWith(recName + Extensions.XML) ? recName + "/" + pathToAsset
                           : isJatsAsset ? recName + "/" + uri : pathToAsset;
        }
    }

    private class RenderedText extends TraditionalContent {
        private static final String TYPE = "Rendered text (HTML, Javascript, CSS and .xhtml)";

        public RenderedText() {
            type = TYPE;
            groups.add(Constants.RENDERED_HTML_DIAMOND);
            groups.add(Constants.RENDERED_PDF_TEX);
        }

        protected boolean isValid(String uri, String recName) {
            return (uri.endsWith(Extensions.XML) || uri.endsWith(Extensions.CSS) || uri.endsWith(Extensions.HTML)
                    || uri.endsWith(Extensions.HTM) || uri.endsWith(Extensions.PDF) || uri.endsWith(Extensions.JS));
        }
    }

    private class RenderedGraphics extends TraditionalContent {
        private static final String TYPE = "Rendered graphics (image_n, image_t)";

        public RenderedGraphics() {
            type = TYPE;
            groups.add(Constants.RENDERED_HTML_DIAMOND);
        }

        protected boolean isValid(String uri, String recName) {
            return isPicture(uri);
        }
    }

    private class RenderedPdf extends TraditionalContent {
        private static final String TYPE = "Rendered pdf";

        public RenderedPdf() {
            type = TYPE;
            groups.add(Constants.RENDERED_PDF_TEX);
            groups.add(Constants.RENDERED_PDF_FOP);
        }

        protected boolean isValid(String uri, String recName) {
            return uri.endsWith(Extensions.PDF);
        }
    }

    private class SourceGraphics extends TraditionalContent {
        private static final String TYPE = "Source graphics (image_n, image_t, CDXXXXXX-figures, CDXXXXXX-thumbnails)";

        public SourceGraphics() {
            type = TYPE;
            groups.add(Constants.SOURCE);
        }

        protected boolean isValid(String uri, String recName) {
            return isPicture(uri);
        }
    }

    private class XmlSource extends TraditionalContent {
        private static final String TYPE = "XML Source";

        public XmlSource() {
            type = TYPE;
            groups.add(Constants.SOURCE);
        }

        protected boolean isValid(String uri, String recName) {
            return uri.endsWith(Extensions.XML);
        }

        protected String getPathInArch(String uri, String recName, String group) {
            return uri.endsWith(recName + Extensions.XML) ? recName + "/" + group + "/"
                    + uri.substring(uri.indexOf(recName)) : super.getPathInArch(uri, recName, group);
        }
    }
}
