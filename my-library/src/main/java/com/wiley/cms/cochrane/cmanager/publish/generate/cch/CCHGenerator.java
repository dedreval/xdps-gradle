package com.wiley.cms.cochrane.cmanager.publish.generate.cch;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.wiley.cms.cochrane.cmanager.data.db.ClDbVO;
import com.wiley.cms.cochrane.cmanager.publish.generate.ArchiveHolder;

import com.wiley.cms.cochrane.cmanager.entitywrapper.RecordWrapper;
import com.wiley.cms.cochrane.cmanager.publish.generate.AbstractGenerator;
import com.wiley.cms.cochrane.cmanager.publish.generate.ArchiveEntry;
import com.wiley.cms.cochrane.cmanager.res.PubType;

/**
 * @author <a href='mailto:ikirov@wiley.com'>Igor Kirov</a>
 * @version 02.11.11
 */
public class CCHGenerator extends AbstractGenerator<ArchiveHolder> {

    public CCHGenerator(ClDbVO db) {
        super(db, "CCH:MAIN:" + db.getTitle(), PubType.TYPE_CCH);
    }

    protected CCHGenerator(ClDbVO db, String generateName, String exportTypeName) {
        super(db, generateName, exportTypeName);
    }

    @Override
    protected List<RecordWrapper> getRecordList(int startIndex, int count) {
        return RecordWrapper.getDbRecordWrapperList(getDb().getId(), startIndex, count);
    }

    protected boolean skipTranslation(RecordWrapper record) {

        boolean ret = record.isTranslationUpdated();

        if (!ret && isWhenReady()) {
            ret = record.isLastTranslationUpdated();
        }
        return ret && (isSkipTranslationUpdatedByDefault());
    }

    protected boolean isSkipTranslationUpdatedByDefault() {
        return !byRecords();
    }

    @Override
    protected List<ArchiveEntry> processRecordList(List<RecordWrapper> recordList) throws Exception {

        List<ArchiveEntry> ret = new ArrayList<ArchiveEntry>();
        for (RecordWrapper record : recordList) {

            if (skipTranslation(record)) {
                continue;
            }
            add(ret, getPathPrefix(record), new File(rps.getRealFilePath(record.getRecordPath())));

            onRecordArchive(record);
        }
        return ret;
    }

    @Override
    protected List<ArchiveEntry> createParticularFiles() throws Exception {
        List<ArchiveEntry> ret = new ArrayList<ArchiveEntry>();
        createLinksXml(ret);
        return ret;
    }

    public static void createLinksXml(List<ArchiveEntry> list) {
        String xml = "<emrw:targets xmlns:xlink=\"http://www.w3.org/1999/xlink\" "
                + "xmlns:emrw=\"http://wiley.com/interscience/emrw\"/>";
        list.add(new ArchiveEntry("links.xml", null, xml));
    }

    protected String getPathPrefix(RecordWrapper record) {
        return record.getName();
    }
}
