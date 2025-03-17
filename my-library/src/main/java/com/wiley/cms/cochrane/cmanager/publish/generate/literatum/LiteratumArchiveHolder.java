package com.wiley.cms.cochrane.cmanager.publish.generate.literatum;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.wiley.cms.cochrane.cmanager.CmsException;
import com.wiley.cms.cochrane.cmanager.CochraneCMSBeans;
import com.wiley.cms.cochrane.cmanager.FilePathCreator;
import com.wiley.cms.cochrane.cmanager.data.IResultsStorage;
import com.wiley.cms.cochrane.cmanager.data.PublishEntity;
import com.wiley.cms.cochrane.cmanager.entitywrapper.PublishWrapper;
import com.wiley.cms.cochrane.cmanager.publish.IPublishChecker;
import com.wiley.cms.cochrane.cmanager.publish.PublishHelper;
import com.wiley.cms.cochrane.cmanager.publish.generate.ArchiveEntry;
import com.wiley.cms.cochrane.cmanager.publish.generate.ArchiveHolder;
import com.wiley.cms.cochrane.cmanager.publish.util.GenerationErrorCollector;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.cmanager.res.PubType;
import com.wiley.cms.cochrane.repository.IRepository;
import com.wiley.cms.cochrane.utils.Constants;
import com.wiley.cms.cochrane.utils.ErrorInfo;
import com.wiley.cms.cochrane.utils.zip.ZipOutput;
import com.wiley.tes.util.Extensions;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 10/12/2018
 */
public class LiteratumArchiveHolder extends ArchiveHolder {

    protected LiteratumArchiveHolder second = null;

    private int allRecordCount = 0;
    private boolean wasDelivered = true;
    private List<ArchiveEntry> entries = new ArrayList<>();

    private LiteratumPriorityStrategy priorityStrategy = LiteratumPriorityStrategy.HIGH4NEW_LOW4UPDATE;
                  
    @Override
    protected void init(PublishWrapper publish, PublishEntity entity, String path) {
        super.init(publish, entity, path);
        wasDelivered = !publish.isOnlyNewContent();
    }

    void setPriorityStrategy(LiteratumPriorityStrategy strategy) {
        priorityStrategy = strategy;
    }

    boolean wasDelivered() {
        return wasDelivered;
    }

    private boolean hasSecond() {
        return second != null;
    }

    public int getAllRecordCount() {
        return hasSecond() ? second.getAllRecordCount() + allRecordCount : allRecordCount;
    }

    public void resetEntries() {
        resetEntries41();

        if (hasSecond()) {
            second.resetEntries41();
        }
    }

    private void resetEntries41() {
        entries.clear();
    }

    public List<ArchiveEntry> createParticularlyFiles(String sbnName, IRepository rps, IResultsStorage rs)
            throws Exception {
        if (isEmpty41() && (!hasSecond() || second.isEmpty41())) {
            if (skippedRecordCount > 0) {
                throw new CmsException(new ErrorInfo(ErrorInfo.Type.NO_GENERATING_RECORD_FOUND));
            }
            throw new CmsException(new ErrorInfo(ErrorInfo.Type.NO_PUBLISHING_RECORD));
        }

        createParticularlyFiles41(sbnName, rps, rs);
        if (hasSecond()) {
            second.createParticularlyFiles41(sbnName, rps, rs);
        }

        return entries;
    }

    @Override
    public void closeArchive() {
        closeArchive41();
        if (hasSecond()) {
            second.closeArchive41();
        }
    }

    protected void closeArchive41(String sbnName, GenerationErrorCollector errCollector, IRepository rps,
                                  IResultsStorage rs) throws Exception {
        createParticularlyFiles41(sbnName);
        addToArchive41(getEntries(), errCollector, rps);
        super.onGeneration(new Date(), rps, rs);
        closeArchive41();
        resetEntries41();
    }

    protected final void closeArchive41() {
        super.closeArchive();
    }

    @Override
    protected void clearArchive(IRepository rps) {
        clearArchiveFile41(rps);
        if (hasSecond()) {
            second.clearArchive(rps);
        }
    }

    @Override
    public void addToArchive(List<ArchiveEntry> entryList, GenerationErrorCollector errCollector, IRepository rps)
            throws IOException {

        addToArchive41(entryList, errCollector, rps);
        if (hasSecond()) {
            second.addToArchive41(second.getEntries(), errCollector, rps);
        }
    }

    private void addToArchive41(List<ArchiveEntry> entryList, GenerationErrorCollector errCollector, IRepository rps)
            throws IOException {
        if (!entryList.isEmpty()) {
            if (archive == null) {
                createArchive41(rps);
            }
            super.addToArchive(entryList, errCollector, rps);
        }
    }

    @Override
    public void onRecordAdded() {
        super.onRecordAdded();
        allRecordCount++;
    }

    @Override
    public PublishEntity onGeneration(Date date, IRepository rps, IResultsStorage rs) {
        PublishEntity ret = super.onGeneration(date, rps, rs);
        if (hasSecond()) {
            second.onGeneration(date, rps, rs);
        }
        return ret;
    }

    @Override
    public void onWhenReadyFail(String generateName, String exportTypeName, Exception e) {
        super.onWhenReadyFail(generateName, exportTypeName, e);
        if (hasSecond()) {
            second.onWhenReadyFail(generateName, exportTypeName, e);
        }
    }

    @Override
    protected void initArchive(IRepository rps) {
        archive = null;
    }

    private void createArchive41(IRepository rps) throws IOException {
        setArchive(new ZipOutput(getFile(getParentFile(rps), rps)));
    }

    protected LiteratumArchiveHolder checkSecondArchive(boolean delivered, IRepository rps)  {
        if (!hasSecond())  {

            String newName = PublishHelper.renameLiteratumPackage(wasDelivered(), getExportFileName());

            if (isEmpty41()) {
                setExportFileName(newName);
                wasDelivered = delivered;
                return this;

            } else {
                second = newArchiveHolder();
                second.addNewExport(newName, export, getFolder(), archiveTemplate, rps);
                second.wasDelivered = delivered;
                second.setPriorityStrategy(priorityStrategy);
            }
        }
        return second;
    }

    protected LiteratumArchiveHolder newArchiveHolder() {
        return new LiteratumArchiveHolder();
    }

    protected LiteratumArchiveHolder checkNext(String sbnName, IRepository rps, IResultsStorage rs) throws Exception {
        onRecordAdded();
        return this;
    }

    protected void addNewExport(String newName, PublishEntity oldExport, String path, String template,
                                IRepository rps) {
        init(newName, CochraneCMSBeans.getPublishStorage().createPublish(oldExport.getDb().getId(),
            oldExport.getPublishType(), oldExport.hasRelation() ? oldExport.getParentId()
                        : oldExport.getId()), path, template, PubType.TYPE_LITERATUM);
        clearArchiveFile41(rps);
        initArchive(rps);
    }

    public LiteratumArchiveHolder checkRecord(Object checkItem, String sbnName, IPublishChecker checker,
        boolean excludeWasDelivered, IRepository rps, IResultsStorage rs) throws Exception {

        boolean delivered = checker.isDelivered(checkItem);
        if (delivered && excludeWasDelivered) {
            onRecordSkipped();
            return null;
        }
        return (!wasDelivered() && delivered || wasDelivered() && !delivered)
                ? checkSecondArchive(delivered, rps).checkNext(sbnName, rps, rs)
                : checkNext(sbnName, rps, rs);
    }

    public List<ArchiveEntry> getEntries() {
        return entries;
    }

    private void createParticularlyFiles41(String sbnName, IRepository rps, IResultsStorage rs) throws Exception {
        if (isEmpty41()) {
            onEmptyContent41(rps, rs);
        } else {
            resetEntries41();
            createParticularlyFiles41(sbnName);
        }
    }

    private boolean isEmpty41() {
        return super.isEmpty();
    }

    private void onEmptyContent41(IRepository rps, IResultsStorage rs) {
        clearArchiveFile41(rps);
        export = super.onGeneration(null, rps, rs);
    }

    private void clearArchiveFile41(IRepository rps) {
        super.clearArchive(rps);
    }

    private void createParticularlyFiles41(String sbn) {

        String dbName = export.getDb().getTitle();
        BaseType bt = BaseType.find(dbName).get();

        String sbnContent = bt.getResourceContent(PubType.TYPE_LITERATUM, sbn + Extensions.XML);
        if (sbnContent != null) {
            entries.add(new ArchiveEntry(FilePathCreator.SEPARATOR + sbn + FilePathCreator.SEPARATOR + sbn
                    + FilePathCreator.SEPARATOR + sbn + Extensions.XML, null, sbnContent));
        }

        String manifest = priorityStrategy.getManifest(bt, wasDelivered());
        entries.add(new ArchiveEntry(Constants.CONTROL_FILE_MANIFEST, null, manifest));
    }
}
