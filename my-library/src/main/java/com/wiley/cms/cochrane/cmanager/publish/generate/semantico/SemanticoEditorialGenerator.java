package com.wiley.cms.cochrane.cmanager.publish.generate.semantico;

import com.wiley.cms.cochrane.cmanager.data.db.ClDbVO;
import com.wiley.cms.cochrane.cmanager.entitywrapper.RecordWrapper;
import com.wiley.cms.cochrane.cmanager.publish.generate.ArchiveEntry;
import com.wiley.cms.cochrane.cmanager.publish.generate.ml3g.ML3GCDSRGenerator;
import com.wiley.cms.cochrane.cmanager.res.PubType;

import java.util.List;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 8/5/2016
 */
public class SemanticoEditorialGenerator extends ML3GCDSRGenerator {

    public SemanticoEditorialGenerator(ClDbVO db) {
        super(db, "SEMANTICO:ML3G:" + db.getTitle(), PubType.TYPE_SEMANTICO);
    }

    @Override
    protected String getArchivePrefix(String recName) {
        return addToRoot(recName);
    }

    @Override
    protected boolean addPreviousVersion() {
        return false;
    }

    @Override
    protected List<RecordWrapper> getRecordList(int startIndex, int count) {
        return (hasIncludedNames() ? getRecordListFromIncludedNames(count, false)
                : super.getRecordList(startIndex, count));
    }

    @Override
    protected List<ArchiveEntry> createParticularFiles() throws Exception {
        return addSPDManifest(null);
    }
}
