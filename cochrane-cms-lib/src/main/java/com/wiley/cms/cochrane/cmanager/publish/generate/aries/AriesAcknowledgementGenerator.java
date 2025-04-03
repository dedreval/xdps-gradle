package com.wiley.cms.cochrane.cmanager.publish.generate.aries;

import java.util.Collections;
import java.util.List;

import javax.validation.constraints.NotNull;

import com.wiley.cms.cochrane.cmanager.CmsException;
import com.wiley.cms.cochrane.cmanager.CochraneCMSBeans;
import com.wiley.cms.cochrane.cmanager.FilePathBuilder;
import com.wiley.cms.cochrane.cmanager.contentworker.AriesHelper;
import com.wiley.cms.cochrane.cmanager.data.DeliveryFileEntity;
import com.wiley.cms.cochrane.cmanager.data.db.ClDbVO;
import com.wiley.cms.cochrane.cmanager.data.translation.PublishedAbstractEntity;
import com.wiley.cms.cochrane.cmanager.entitymanager.RecordHelper;
import com.wiley.cms.cochrane.cmanager.entitywrapper.PublishWrapper;
import com.wiley.cms.cochrane.cmanager.entitywrapper.RecordWrapper;
import com.wiley.cms.cochrane.cmanager.publish.generate.AbstractGenerator;
import com.wiley.cms.cochrane.cmanager.publish.generate.ArchiveEntry;
import com.wiley.cms.cochrane.cmanager.publish.util.GenerationErrorCollector;
import com.wiley.cms.cochrane.cmanager.res.BaseType;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 2/15/2021
 */
abstract class AriesAcknowledgementGenerator extends AbstractGenerator<AriesArchiveHolder> {

    protected AriesHelper helper;

    AriesAcknowledgementGenerator(ClDbVO db, String generateName, String exportTypeName) {
        super(db, generateName, exportTypeName);
    }

    protected abstract String buildImportXml(RecordWrapper record, String importPath, DeliveryFileEntity df)
            throws Exception;

    protected abstract DeliveryFileEntity findAriesPackage(RecordWrapper rw) throws CmsException;

    @Override
    protected void init(PublishWrapper publish) throws Exception {
        super.init(publish);
        helper = new AriesHelper();
    }

    @Override
    protected List<ArchiveEntry> processRecordList(List<RecordWrapper> recordList) {
        if (recordList.isEmpty()) {
            return Collections.emptyList();
        }
        archive.resetEntries();
        recordList.forEach(this::processRecord);
        return archive.getEntries();
    }

    void createAckGO(String srcFolder, String goManifestName) throws Exception {
        helper.createAckGO(srcFolder + goManifestName, archive.getFolder() + goManifestName, rps);
    }

    protected void onAcknowledgementStart(String dfName, Integer recordId, String cdNumber) {
    }

    private void processRecord(RecordWrapper rw) {
        try {
            DeliveryFileEntity df = checkAriesPackage(rw);

            String dfName = df.getName();
            Integer dfId = df.getId();

            String srcFolder = BaseType.find(getDbName()).get().isCDSR()
                    ? FilePathBuilder.JATS.getPathToPackage(df.getIssue().getId(), dfId)
                    : FilePathBuilder.getPathToIssuePackage(df.getIssue().getId(), getDbName(), dfId);
            String importFileName = RecordHelper.findAriesImportSource(srcFolder, rps);

            onAcknowledgementStart(dfName, rw.getId(), rw.getPubName());

            archive.checkNext(dfName, rps, rs, ps);

            archive.getEntries().add(new ArchiveEntry(importFileName, importFileName,
                    buildImportXml(rw, srcFolder + importFileName, df)));

            createAckGO(srcFolder, AriesHelper.buildGOFileName(dfName));

            onRecordArchive(rw);

        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
            errorCollector.addError(GenerationErrorCollector.NotificationLevel.ERROR, e.getMessage());
        }
    }

    static PublishedAbstractEntity findWhenReadyArticle(Integer dfId, String manuscriptNumber) {
        List<PublishedAbstractEntity> list = CochraneCMSBeans.getRecordManager().getWhenReadyByDeliveryPackage(dfId);
        for (PublishedAbstractEntity pe: list) {
            if (manuscriptNumber.equals(pe.getManuscriptNumber())) {
                return pe;
            }
        }
        return null;
    }

    PublishedAbstractEntity findWhenReadyArticle(RecordWrapper rw) {
        List<PublishedAbstractEntity> list =
                rs.getPublishedAbstracts(Collections.singletonList(rw.getNumber()), getDb().getId());
        int pub = rw.getPubNumber();
        for (PublishedAbstractEntity pe: list) {
            if (pub == pe.getPubNumber() && !pe.hasLanguage()) {
                return pe;
            }
        }
        return null;
    }

    DeliveryFileEntity checkAriesPackage(RecordWrapper rw) throws CmsException {
        DeliveryFileEntity df = findAriesPackage(rw);
        if (df == null) {
            throw new CmsException(String.format("cannot find Aries package for %s", rw.getPubName()));
        }
        String dfName = df.getName();
        if (!DeliveryFileEntity.isAriesSFTP(df.getType())) {
            throw new CmsException(String.format("%s belongs to %s that's not an Aries package",
                    rw.getPubName(), dfName));
        }
        return df;
    }

    protected List<RecordWrapper> getRecordListFromIncludedNames(String[] items) {
        if (items == null) {
            return Collections.emptyList();
        }
        List<RecordWrapper> ret = RecordWrapper.getDbRecordWrapperList(
                getDb().getId(), 0, 0, items, -1, null, 0, false, false);
        return ret.size() > 1 ? Collections.singletonList(ret.get(0)) : ret;
    }

    final DeliveryFileEntity getDeliveryPackage(@NotNull Integer dfId, RecordWrapper rw) {
        return rw != null && dfId.equals(rw.getDeliveryFileId()) ? rw.getDeliveryFile().getEntity()
                : rs.getDeliveryFileEntity(dfId);
    }
}
