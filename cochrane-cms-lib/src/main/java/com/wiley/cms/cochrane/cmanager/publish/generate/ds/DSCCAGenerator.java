package com.wiley.cms.cochrane.cmanager.publish.generate.ds;

import com.wiley.cms.cochrane.cmanager.CmsException;
import com.wiley.cms.cochrane.cmanager.CochraneCMSProperties;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.FilePathCreator;
import com.wiley.cms.cochrane.cmanager.MessageSender;
import com.wiley.cms.cochrane.cmanager.data.db.ClDbVO;
import com.wiley.cms.cochrane.cmanager.data.record.IRecordStorage;
import com.wiley.cms.cochrane.cmanager.data.record.RecordStorageFactory;
import com.wiley.cms.cochrane.cmanager.data.record.UnitStatusEntity;
import com.wiley.cms.cochrane.cmanager.data.record.UnitStatusVO;
import com.wiley.cms.cochrane.cmanager.entitymanager.RecordHelper;
import com.wiley.cms.cochrane.cmanager.entitywrapper.EntireDbWrapper;
import com.wiley.cms.cochrane.cmanager.entitywrapper.PublishWrapper;
import com.wiley.cms.cochrane.cmanager.entitywrapper.RecordWrapper;
import com.wiley.cms.cochrane.cmanager.publish.IPublishChecker;
import com.wiley.cms.cochrane.cmanager.publish.PublishChecker;
import com.wiley.cms.cochrane.cmanager.publish.generate.AbstractGeneratorWhenReady;
import com.wiley.cms.cochrane.cmanager.publish.generate.ArchiveEntry;
import com.wiley.cms.cochrane.cmanager.res.PubType;
import com.wiley.cms.cochrane.utils.ErrorInfo;
import com.wiley.tes.util.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * @author <a href='mailto:atolkachev@wiley.com'>Andrey Tolkachev</a>
 * @version 03.07.2019
 */

public class DSCCAGenerator extends AbstractGeneratorWhenReady {

    private static final Logger LOG = Logger.getLogger(DSCCAGenerator.class);
    private final boolean unitStatusFilterEnabled =
            CochraneCMSProperties.getBoolProperty("cms.cochrane.cca.publish.ds.records_filter.enabled", true);
    private final List<String> archivedRecordsSrcPaths = new ArrayList<>();
    private List<String> excludedByFilterRecordNames = Collections.EMPTY_LIST;
    private List<String> excludedByPubDatesRecordNames = Collections.EMPTY_LIST;
    private String newUnitStatus;
    private String underReviewUnitStatus;
    private int internBeginIndex;

    public DSCCAGenerator(ClDbVO dbVO) {
        super(dbVO, PubType.TYPE_DS);
    }

    public DSCCAGenerator(EntireDbWrapper db) {
        super(db, PubType.TYPE_DS);
    }

    public DSCCAGenerator(boolean fromEntire, int dbId) {
        super(fromEntire, dbId, CochraneCMSPropertyNames.getCcaDbName(), PubType.TYPE_DS);
    }

    @Override
    protected void init(PublishWrapper publish) {
        super.init(publish);

        archiveName = publish.getPublishEntity().getFileName();
        setUnitStatuses(publish.getId());
        setCheckRecordWithNoOnlineDate(CochraneCMSPropertyNames.checkEmptyPublicationDateInWML3G4DS());
    }

    @Override
    protected List<RecordWrapper> getRecords(int beginIndex, int limit) {
        List<RecordWrapper> records;
        boolean emptyResult = false;
        do {
            records = super.getRecords(internBeginIndex, limit);
            internBeginIndex += limit;
            if (!records.isEmpty()) {
                filterOutRecords(records);
            } else {
                emptyResult = true;
            }
        } while (records.isEmpty() && !emptyResult);

        return records;
    }

    private void filterOutRecords(List<RecordWrapper> records) {
        for (Iterator<RecordWrapper> it = records.iterator(); it.hasNext();) {
            RecordWrapper record = it.next();
            String unitStatus = record.getUnitStatus();
            if (unitStatusFilterEnabled && (underReviewUnitStatus.equals(unitStatus)
                    || newUnitStatus.equals(unitStatus) && isPublishedBefore(record))) {
                if (excludedByFilterRecordNames.isEmpty()) {
                    excludedByFilterRecordNames = new ArrayList<>();
                }
                excludedByFilterRecordNames.add(record.getName());
                it.remove();

            } else if (isRecordWithNoFirstOnlineDate(record.getName(), record::getRecordPath)) {
                if (excludedByPubDatesRecordNames.isEmpty()) {
                    excludedByPubDatesRecordNames = new ArrayList<>();
                }
                excludedByPubDatesRecordNames.add(record.getName());
                it.remove();
            }
        }
    }

    private void setUnitStatuses(int publishId) {
        if (!unitStatusFilterEnabled) {
            LOG.debug("Records filter by unit statuses is disabled for publish with id " + publishId
                    + ". All articles will be in archive.");
            return;
        }

        List<Integer> statusIds = Arrays.asList(
                UnitStatusEntity.UnitStatus.NEW,
                UnitStatusEntity.UnitStatus.UNDER_REVIEW);
        IRecordStorage recordStorage = RecordStorageFactory.getFactory().getInstance();

        List<UnitStatusVO> unitStatuses = recordStorage.getUnitStatusList(statusIds);
        for (UnitStatusVO unitStatus : unitStatuses) {
            if (unitStatus.getId() == UnitStatusEntity.UnitStatus.NEW) {
                newUnitStatus = unitStatus.getName();
            } else {
                underReviewUnitStatus = unitStatus.getName();
            }
        }
    }

    //@Override
    //protected boolean checkRecordWithNoOnlineDate() {
    //    return CochraneCMSPropertyNames.checkEmptyPublicationDateInWML3G4DS();
    //}

    @Override
    protected void past() throws Exception {
        if (!excludedByFilterRecordNames.isEmpty() || !excludedByPubDatesRecordNames.isEmpty()) {
            notifyAboutExcludedRecords();
            if (isArchiveEmpty()) {
                throw new CmsException(new ErrorInfo(ErrorInfo.Type.NO_GENERATING_RECORD_FOUND));
            }
        }
        super.past();
    }

    private void notifyAboutExcludedRecords() {
        Map<String, String> params = new HashMap<>();
        String report = null;

        if (!excludedByFilterRecordNames.isEmpty()) {
            params.put(MessageSender.MSG_PARAM_DELIVERY_FILE, archiveName);
            params.put(MessageSender.MSG_PARAM_LIST, excludedByFilterRecordNames.toString());
            report = CochraneCMSProperties.getProperty("cca.generation.ds.excluded_records", params);

        }
        if (!excludedByPubDatesRecordNames.isEmpty()) {
            StringBuilder sb = report != null ? new StringBuilder(report).append("\n\n") : new StringBuilder();
            sb.append(CochraneCMSProperties.getProperty("record_no_publication_date_warn")).append(":\n").append(
                    excludedByPubDatesRecordNames.toString());
            report = sb.toString();

        }
        if (report != null) {
            params.clear();
            params.put(MessageSender.MSG_PARAM_REPORT, report);

            LOG.warn(report);
            MessageSender.sendMessage("cca_ds_generation_warning", params);
        }
    }

    @Override
    public boolean isArchiveEmpty() {
        return archivedRecordsSrcPaths.isEmpty();
    }

    private boolean isPublishedBefore(RecordWrapper record) {
        //return rs.getLastPublishedCCADate(recordName, false, true) != null;
        IPublishChecker checker =  PublishChecker.getDSDelivered(Collections.singletonList(record),
                getExportId(), true, false, null, ps);
        return checker.isDelivered(checker.getPublishCheckerItem(record.getNumber(), record.getName()));
    }

    @Override
    protected String getPrefix() {
        return sbn + FilePathCreator.SEPARATOR;
    }

    @Override
    protected void updateArchivedRecordList(RecordWrapper record) {
        super.updateArchivedRecordList(record);
        archivedRecordsSrcPaths.add(record.getRecordPath());
    }

    @Override
    protected List<ArchiveEntry> createParticularFiles() throws Exception {
        List<ArchiveEntry> ret = new ArrayList<>();
        addDoiXml(ret, sbn, archivedRecords, null, RecordHelper::buildDoiCCA);
        return ret;
    }
}
