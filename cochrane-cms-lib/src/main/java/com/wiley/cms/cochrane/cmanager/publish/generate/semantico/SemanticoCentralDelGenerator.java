package com.wiley.cms.cochrane.cmanager.publish.generate.semantico;

import java.util.List;

import com.wiley.cms.cochrane.cmanager.CENTRALRecordsDeleter;
import com.wiley.cms.cochrane.cmanager.data.db.ClDbVO;
import com.wiley.cms.cochrane.cmanager.entitywrapper.RecordWrapper;
import com.wiley.cms.cochrane.cmanager.publish.generate.AbstractGenerator;
import com.wiley.cms.cochrane.cmanager.publish.generate.ArchiveEntry;
import com.wiley.cms.cochrane.cmanager.publish.generate.ArchiveHolder;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.cmanager.res.PubType;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 8/2/2016
 */
public class SemanticoCentralDelGenerator extends AbstractGenerator<ArchiveHolder> {

    public SemanticoCentralDelGenerator(ClDbVO db) {
        super(db, "SEMANTICO:DEL:" + db.getTitle(), PubType.TYPE_SEMANTICO_DELETE);
    }

    @Override
    protected void createArchive() throws Exception {
        rps.putFile(archive.getExportFilePath(), byDeliveryPacket() ? CENTRALRecordsDeleter.getDeletedRecordsBody(
            rs.getDeletedRecordNames(BaseType.getCentral().get(), getDeliveryFileId()))
                : CENTRALRecordsDeleter.getDeletedRecordsBody(getDb().getIssue().getId()));
    }

    @Override
    protected List<ArchiveEntry> processRecordList(List<RecordWrapper> recordList) throws Exception {
        return null;
    }

    @Override
    protected  List<RecordWrapper> getRecordList(int startIndex, int count) throws Exception {
        return null;
    }
}
