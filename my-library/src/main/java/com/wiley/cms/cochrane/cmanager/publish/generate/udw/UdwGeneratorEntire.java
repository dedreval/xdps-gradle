package com.wiley.cms.cochrane.cmanager.publish.generate.udw;

import com.wiley.cms.cochrane.cmanager.entitywrapper.EntireDbWrapper;
import com.wiley.cms.cochrane.cmanager.entitywrapper.EntireRecordWrapper;
import com.wiley.cms.cochrane.cmanager.entitywrapper.PublishWrapper;
import com.wiley.cms.cochrane.cmanager.publish.generate.AbstractGeneratorEntire;
import com.wiley.cms.cochrane.cmanager.publish.generate.ArchiveEntry;
import com.wiley.cms.cochrane.cmanager.publish.generate.ArchiveHolder;
import com.wiley.cms.cochrane.cmanager.publish.udw.UdwFeedDataExtractor;
import com.wiley.cms.cochrane.cmanager.res.PubType;
import com.wiley.tes.util.FileUtils;

import java.util.Collections;
import java.util.List;

import static com.wiley.cms.cochrane.cmanager.data.record.RecordEntity.VERSION_LAST;
import static com.wiley.cms.cochrane.cmanager.publish.util.GenerationErrorCollector.NotificationLevel.ERROR;

/**
 * @author <a href='mailto:aasadulin@wiley.com'>Alexandr Asadulin</a>
 * @version 24.01.2019
 */
public class UdwGeneratorEntire extends AbstractGeneratorEntire<ArchiveHolder> {
    private UdwFeedDataExtractor dataExtractor;
    private final StringBuilder udwData = new StringBuilder();

    public UdwGeneratorEntire(EntireDbWrapper db) {
        super(db, "UDW:ENTIRE:", PubType.TYPE_UDW);
    }

    @Override
    protected void init(PublishWrapper publish) throws Exception {
        super.init(publish);
        dataExtractor = new UdwFeedDataExtractor(getDbName());
    }

    @Override
    protected List<EntireRecordWrapper> getRecordList(int startIndex, int count) {
        return EntireRecordWrapper.getRecordWrapperList(getDb().getDbName(), startIndex, count);
    }

    @Override
    protected List<ArchiveEntry> processRecordList(List<EntireRecordWrapper> recordList) {
        for (EntireRecordWrapper rec : recordList) {
            if (!byRecords()) {
                for (int version : rec.getVersions().getPreviousVersions()) {
                    addArticleMd(rec.getName(), version);
                }
            }
            addArticleMd(rec.getName(), VERSION_LAST);
        }
        return Collections.emptyList();
    }

    private void addArticleMd(String name, int version) {
        try {
            String md = dataExtractor.extract(name, version);
            udwData.append(md).append('\n');

            onRecordArchive(name, version, null);
        } catch (Exception e) {
            String versionMsg = version == VERSION_LAST ? " (latest version)" : " (ver. " + version + ")";
            errorCollector.addError(ERROR, "Failed to extract udw feed data for " + name + versionMsg);
        }
    }

    @Override
    protected String getArchivePrefix(String recName) {
        return null;
    }

    @Override
    protected List<ArchiveEntry> createParticularFiles() {
        String mdFileName = FileUtils.cutExtension(archive.getExportFileName());
        return Collections.singletonList(new ArchiveEntry(mdFileName, null, udwData.toString()));
    }
}
