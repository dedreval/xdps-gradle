package com.wiley.cms.cochrane.cmanager.publish.generate;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import com.wiley.cms.cochrane.cmanager.CochraneCMSProperties;
import org.apache.commons.io.IOUtils;

import com.wiley.cms.cochrane.cmanager.CmsException;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.FilePathCreator;
import com.wiley.cms.cochrane.cmanager.data.IContentRoom;
import com.wiley.cms.cochrane.cmanager.data.PrevVO;
import com.wiley.cms.cochrane.cmanager.data.record.RecordEntity;
import com.wiley.cms.cochrane.cmanager.entitywrapper.PublishWrapper;
import com.wiley.cms.cochrane.cmanager.entitywrapper.RecordVersions;
import com.wiley.cms.cochrane.cmanager.entitywrapper.RecordWrapper;
import com.wiley.cms.cochrane.cmanager.publish.AbstractPublisher;
import com.wiley.cms.cochrane.cmanager.publish.PublishHelper;
import com.wiley.cms.cochrane.cmanager.publish.util.DoiXmlCreator;
import com.wiley.cms.cochrane.cmanager.publish.util.GenerationErrorCollector;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.cmanager.res.PublishProfile;
import com.wiley.cms.cochrane.cmanager.translated.ITranslatedAbstractsInserter;
import com.wiley.cms.cochrane.converter.ml3g.Ml3gXmlAssets;
import com.wiley.cms.cochrane.repository.IRepository;
import com.wiley.cms.cochrane.services.LiteratumEvent;
import com.wiley.cms.cochrane.utils.Constants;
import com.wiley.tes.util.BitValue;
import com.wiley.tes.util.InputUtils;
import com.wiley.tes.util.Logger;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 10/12/2018
 *
 * @param <H> A holder for an arhive related to the given feed
 */
public abstract class ArchiveGenerator<H extends ArchiveHolder> extends AbstractPublisher implements IContentRoom {
    protected static final Logger LOG = Logger.getLogger(AbstractGenerator.class);

    protected static final String MSG_PARAM_ML3G_ERR = "wml3g_conversion_error";
    protected static final String NOT_EXIST_MSG = "[%s] doesn't exist and can't be added into archive.\n";

    private static final String MSG_PARAM_DISABLED_WARN = "record_disabled_warn";
    private static final String MSG_PARAM_VALIDATION_WARN = "record_validation_warn";
    private static final String MSG_PARAM_RENDERING_WARN = "record_rendering_warn";
    private static final String MSG_PARAM_NO_PUB_DATE_WARN = "record_no_publication_date_warn";
    private static final String MSG_PARAM_NO_HW_PUBLICATION_WARN = "record_no_hw_publication_warn";
    private static final String MSG_PARAM_HW_PUBLICATION_ERR = "record_hw_publication_error";

    protected final GenerationErrorCollector errorCollector;

    protected H archive;

    protected final String sbn;
    protected final String doiPrefix;

    protected ITranslatedAbstractsInserter taInserter;
    protected Set<String> includedNames;
    protected Set<String> processedNames;
    protected Date generateStartDate;

    private final Set<String> archivedRecords = new HashSet<>();

    private int recordsProcessId;
    private boolean trackRecord;
    private boolean checkRecordWithNoOnlineDate;

    ArchiveGenerator(String dbName, String generateName, String exportTypeName) {
        super(buildTag(dbName, generateName, exportTypeName), exportTypeName);

        errorCollector = new GenerationErrorCollector(generateName);

        doiPrefix = getDoiPrefix(dbName);

        BaseType bt = BaseType.find(dbName).get();
        BaseType.PubInfo pi = bt.getPubInfo(exportTypeName);
        if (pi != null) {
            setTrackByRecord(pi.getTrackRecord());
            sbn = pi.getSBN();

        } else {
            sbn = null;
        }
    }

    protected String getDoiPrefix(String dbName) {
        return PublishHelper.defineDsDoiPrefixByDatabase(dbName);
    }

    protected void init(PublishWrapper publish) throws Exception {

        archive = createArchiveHolder();
        includedNames = publish.takeCdNumbers();
        taInserter = CochraneCMSPropertyNames.lookup("TranslatedAbstractsInserter", ITranslatedAbstractsInserter.class);
        recordsProcessId = publish.getRecordsProcessId();
    }

    protected H createArchiveHolder() {
        return (H) (new ArchiveHolder());
    }

    protected final ArchiveHolder getHolder() {
        return archive;
    }

    void addToArchive(List<ArchiveEntry> archiveEntryList) throws IOException {
        archive.addToArchive(archiveEntryList, errorCollector, rps);
    }

    private void setTrackByRecord(int value) {
        trackRecord = BitValue.getBit(0, value);
        if (BitValue.getBit(1, value)) {
            processedNames = new HashSet<>();
        }
    }

    protected boolean isTrackByRecord() {
        return trackRecord;
    }

    protected int getBatchSize() {
        int ret = archive.getBatch();
        return ret > 0 ? ret : PublishProfile.PUB_PROFILE.get().getBatch();
    }

    protected int getRecordsProcessId() {
        return recordsProcessId;
    }

    protected boolean byRecords() {
        return recordsProcessId > 0;
    }

    protected boolean hasIncludedNames() {
        return includedNames != null;
    }

    protected String[] getItemsFromIncludedNames(int count) {
        int size = includedNames.size();
        if (size > count) {
            size = count;
        }
        if (size == 0) {
            return null;
        }
        String[] items = new String[size];
        Iterator<String> it = includedNames.iterator();
        int i = 0;
        while (i < size) {
            items[i++] = it.next();
            it.remove();
        }
        return items;
    }

    void onRecordsBatchArchived(List<? extends RecordWrapper> records) {
        addArchivedRecordsToTrack(records);
        archivedRecords.clear();
    }

    protected void addArchivedRecordsToTrack(List<? extends RecordWrapper> records) {
        if (!isTrackByRecord()) {
            return;
        }

        for (RecordWrapper rec : records) {
            int recordIdHash = rec.getNumber();
            RecordVersions recVersions = rec.getVersions();
            int publishId = archive.getExport().getId();

            for (PrevVO prevVO : recVersions.getPreviousVersionsVO()) {
                if (archivedRecords.contains(rec.getName() + prevVO.version)) {
                    createPublishRecord(rec, prevVO.pub, recordIdHash, publishId);
                }
            }
            if (archivedRecords.contains(rec.getName() + RecordEntity.VERSION_LAST) || rec.isPublishingCanceled()) {
                createPublishRecord(rec, rec.getPubNumber(), recordIdHash, publishId);
            }
        }
    }

    protected void createPublishRecord(RecordWrapper rec, int pubNumb, int recordIdHash, int publishId) {
        ps.createPublishRecord(recordIdHash, pubNumb, rec.getDeliveryFileId(), publishId, rec.getId());
    }

    protected void createPublishRecord(Integer recordId, Integer dfId, int pubNumb, int recordIdHash, int publishId) {
        ps.createPublishRecord(recordIdHash, pubNumb, dfId, publishId, recordId);
    }

    protected void onRecordArchive(RecordWrapper record) {
        onRecordArchive(record.getName(), RecordEntity.VERSION_LAST, record.getPubName());
    }

    protected void onRecordArchive(String name, int version, String pubName) {
        archivedRecords.add(name + version);

        if (processedNames != null && pubName != null) {
            processedNames.add(pubName);
        }
    }

    protected void onRecordArchiveFailed(RecordWrapper record) {
        archivedRecords.remove(record.getName() + record.getVersions().getVersion());
    }

    protected List<ArchiveEntry> createParticularFiles() throws Exception {
        return Collections.emptyList();
    }

    void logStartGenerate(Logger log) {
        log.debug("> Start Generate [" + tag + "]");
    }

    void logEndGenerate(Logger log) {
        log.debug("< Finish Generate [" + tag + "]");
    }

    void logEndGenerate(Logger log, Exception e) {
        log.error(String.format("< Finish Generate [%s] Failed with Exception: %s", tag, e.getMessage()));
    }

    protected String getExportFileName() {
        return archive.exportFileName;
    }

    protected String getPubName(String cdNumber, int pubNumber) {
        return cdNumber;
    }

    final void collectError4MainGeneration(Exception e) {
        errorCollector.addError(GenerationErrorCollector.NotificationLevel.ERROR,
            String.format("Generation failed with: %s.\n", e.getMessage()));
    }

    final void collectMessage(Exception e) {
        errorCollector.addError(GenerationErrorCollector.NotificationLevel.INFO, e.getMessage() + "\n");
    }

    final void collectWarn(Exception e) {
        errorCollector.addError(GenerationErrorCollector.NotificationLevel.WARN, e.getMessage() + "\n");
    }

    protected final void collectWarn4NotExisted(String prefix, String path) {
        errorCollector.addError(GenerationErrorCollector.NotificationLevel.WARN, prefix
                + String.format(NOT_EXIST_MSG, path));
    }

    protected final void collectError(Exception e) {
        errorCollector.addError(GenerationErrorCollector.NotificationLevel.ERROR, e.getMessage());
    }

    protected static void setBaseAssetsUri(String uri, Ml3gXmlAssets assets, IRepository rep) throws Exception {
        if (!rep.isFileExists(uri)) {
            throw new CmsException(generateNotExistsStr(uri));
        }
        assets.setXmlUri(uri);
    }

    static String generateNotExistsStr(String uri) {
        return "File or directory " + String.format(NOT_EXIST_MSG, uri);
    }

    protected Date getGenerateStartDate() {
        return generateStartDate;
    }

    protected void addDoiXml(List<ArchiveEntry> ret, String archiveRoot, String meshUpdate,
                             Function<String, String> buildDoi) throws Exception {
        addDoiXml(ret, archiveRoot, processedNames, meshUpdate, buildDoi);
    }

    protected void addDoiXml(List<ArchiveEntry> ret, String archiveRoot, Collection<String> pubNames, String meshUpdate,
                             Function<String, String> buildDoi) throws Exception {
        InputStream is = null;
        try {
            if (pubNames != null && !pubNames.isEmpty()) {
                is = DoiXmlCreator.getDoiXml4DS(getDbName(), generateStartDate, pubNames, doiPrefix,
                        meshUpdate, buildDoi);
                ret.add(new ArchiveEntry(archiveRoot + FilePathCreator.SEPARATOR + Constants.DOIURL_FILE,
                        null, IOUtils.toString(is, StandardCharsets.UTF_8)));
            }
        } finally {
            IOUtils.closeQuietly(is);
        }
    }

    protected void excludeDisabledRecords(List<? extends RecordWrapper> recLst, boolean checkDates) {
        recLst.removeIf(rec -> isRecordNotIncluded(rec, checkDates ? ((RecordWrapper) rec)::getWML3GPath : null));
    }

    protected final boolean checkRecordWithNoOnlineDate() {
        return checkRecordWithNoOnlineDate; // a check is disabled by default
    }

    protected final void setCheckRecordWithNoOnlineDate(boolean value) {
        checkRecordWithNoOnlineDate = value;
    }

    private boolean hasPublicationDate(Supplier<String> path, String firstOnlineTag, String finalOnlineFormTag) {
        //if (CochraneCMSPropertyNames.checkEmptyPublicationDateInWML3G4DS()) {
        try {
            String content = InputUtils.readStreamToString(rps.getFile(path.get()));
            return content.contains(firstOnlineTag) && content.contains(finalOnlineFormTag);
        } catch (Exception e) {
            LOG.error(e.getMessage());
        }
        //}
        return false;
    }

    private boolean isRecordDisabled(RecordWrapper record) {
        if (record.isDisabled()) {
            String message = CochraneCMSProperties.getProperty(MSG_PARAM_DISABLED_WARN);
            errorCollector.addErrorWithGroupingEntries(GenerationErrorCollector.NotificationLevel.WARN, message,
                                                       record.getName());
            return true;
        }
        return false;
    }

    private boolean isRecordNotValid(RecordWrapper record) {
        if (!record.isQasSuccessful()) {
            errorCollector.addErrorWithGroupingEntries(GenerationErrorCollector.NotificationLevel.WARN,
                    CochraneCMSProperties.getProperty(MSG_PARAM_VALIDATION_WARN), record.getName());
            return true;
        }
        return false;
    }

    private boolean isRecordNotRendered(RecordWrapper record) {
        if (!record.isRenderingSuccessful()) {
            errorCollector.addErrorWithGroupingEntries(GenerationErrorCollector.NotificationLevel.WARN,
                    CochraneCMSProperties.getProperty(MSG_PARAM_RENDERING_WARN), record.getName());
            return true;
        }
        return false;
    }

    protected boolean isRecordWithNoFirstOnlineDate(String name, Supplier<String> pathSupplier) {
        if (checkRecordWithNoOnlineDate() //&& CochraneCMSPropertyNames.checkEmptyPublicationDateInWML3G4DS()
                && !hasPublicationDate(pathSupplier, LiteratumEvent.WRK_EVENT_FIRST_ONLINE,
                        LiteratumEvent.WRK_EVENT_ONLINE_FINAL_FORM)) {

            errorCollector.addErrorWithGroupingEntries(GenerationErrorCollector.NotificationLevel.WARN,
                    CochraneCMSProperties.getProperty(MSG_PARAM_NO_PUB_DATE_WARN), name);
            return true;
        }
        return false;
    }

    protected boolean isRecordNotPublished(RecordWrapper record) {
        if (!record.isPublished()) {
            errorCollector.addErrorWithGroupingEntries(GenerationErrorCollector.NotificationLevel.WARN,
                    CochraneCMSProperties.getProperty(MSG_PARAM_NO_HW_PUBLICATION_WARN), record.getName());
            return true;
        }
        return false;
    }

    protected boolean isRecordHWError(RecordWrapper record) {
        if (record.hasHWError()) {
            errorCollector.addErrorWithGroupingEntries(GenerationErrorCollector.NotificationLevel.WARN,
                    CochraneCMSProperties.getProperty(MSG_PARAM_HW_PUBLICATION_ERR), record.getName());
            return true;
        }
        return false;
    }

    protected boolean isRecordNotIncluded(RecordWrapper record, Supplier<String> pathSupplier) {
        return isRecordDisabled(record) || isRecordNotValid(record) || isRecordNotRendered(record)
                || (pathSupplier != null && isRecordWithNoFirstOnlineDate(record.getPubName(), pathSupplier));
    }

    protected boolean checkEmpty() {
        return false;
    }
}
