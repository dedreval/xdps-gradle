package com.wiley.cms.cochrane.cmanager.publish.generate.aries;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.wiley.cms.cochrane.cmanager.data.IResultsStorage;
import com.wiley.cms.cochrane.cmanager.data.PublishEntity;
import com.wiley.cms.cochrane.cmanager.publish.IPublishStorage;
import com.wiley.cms.cochrane.cmanager.publish.generate.ArchiveEntry;
import com.wiley.cms.cochrane.cmanager.publish.generate.ArchiveHolder;
import com.wiley.cms.cochrane.cmanager.publish.util.GenerationErrorCollector;
import com.wiley.cms.cochrane.repository.IRepository;
import com.wiley.cms.cochrane.utils.zip.ZipOutput;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 10/12/2018
 */
public class AriesArchiveHolder extends ArchiveHolder {

    private final GenerationErrorCollector errCollector;
    private final String pubType;

    private List<ArchiveEntry> entries = new ArrayList<>();

    public AriesArchiveHolder(GenerationErrorCollector errCollector, String pubType) {
        this.pubType = pubType;
        this.errCollector = errCollector;
    }

    @Override
    protected void initArchive(IRepository rps) {
        archive = null;
    }

    public void resetEntries() {
        entries.clear();
    }

    public List<ArchiveEntry> getEntries() {
        return entries;
    }

    private void closeArchive(GenerationErrorCollector errCollector, IRepository rps, IResultsStorage rs)
            throws Exception {
        addToArchive(getEntries(), errCollector, rps);
        super.onGeneration(new Date(), rps, rs);
        super.closeArchive();
        resetEntries();
    }

    @Override
    public void addToArchive(List<ArchiveEntry> entryList, GenerationErrorCollector errCollector, IRepository rps)
            throws IOException {
        if (!entryList.isEmpty()) {
            if (archive == null) {
                createArchive(rps);
            }
            super.addToArchive(entryList, errCollector, rps);
        }
    }

    @Override
    protected void clearArchiveFolder(IRepository rps) {
    }

    @Override
    protected void clearArchive(IRepository rps) {
    }

    private void createArchive(IRepository rps) throws IOException {
        setArchive(new ZipOutput(getFile(getParentFile(rps), rps)));
    }

    protected void checkNext(String fileName, IRepository rp, IResultsStorage rs, IPublishStorage ps) throws Exception {
        if (curRecordCount == 0) {
            setExportFileName(fileName);

        } else {
            closeArchive(errCollector, rp, rs);
            curRecordCount = 0;
            addNewExport(fileName, export, getFolder(), archiveTemplate, rp, ps);
        }
        onRecordAdded();
    }

    private void addNewExport(String newName, PublishEntity oldExport, String path, String template,
                              IRepository rp, IPublishStorage ps) {
        init(newName, ps.createPublish(oldExport.getDb().getId(), oldExport.getPublishType(),
                oldExport.hasRelation() ? oldExport.getParentId() : oldExport.getId()), path, template, pubType);
        clearArchiveFile(rp);
        initArchive(rp);
    }

    private void onEmptyContent(IRepository rps, IResultsStorage rs) {
        clearArchiveFile(rps);
        export = super.onGeneration(null, rps, rs);
    }

    private void clearArchiveFile(IRepository rps) {
        File fl = getFile(getParentFile(rps), rps);
        if (fl.exists()) {
            fl.delete();
        }
    }
}
