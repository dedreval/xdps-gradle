package com.wiley.cms.cochrane.cmanager.publish.generate.semantico;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.entitywrapper.EntireDbWrapper;
import com.wiley.cms.cochrane.cmanager.entitywrapper.PublishWrapper;
import com.wiley.cms.cochrane.cmanager.publish.generate.ArchiveEntry;
import com.wiley.cms.cochrane.cmanager.publish.generate.ml3g.ML3GCDSRGeneratorEntire;
import com.wiley.cms.cochrane.cmanager.res.PubType;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 5/27/2016
 */
public class SemanticoEditorialGeneratorEntire extends ML3GCDSRGeneratorEntire {

    public SemanticoEditorialGeneratorEntire(EntireDbWrapper db) {
        super(db, "SEMANTICO:ENTIRE:ML3G:" + db.getDbName(), PubType.TYPE_SEMANTICO);
    }

    @Override
    protected void init(PublishWrapper publish) throws Exception{
        super.init(publish);
        setCheckRecordWithNoOnlineDate(CochraneCMSPropertyNames.checkEmptyPublicationDateInWML3G4HW());
    }

    @Override
    protected String getArchivePrefix(String recName) {
        return addToRoot(recName);
    }

    @Override
    protected List<ArchiveEntry> createParticularFiles() throws IOException {
        return Collections.emptyList();
    }

    @Override
    protected boolean addPreviousVersion() {
        return false;
    }
}
