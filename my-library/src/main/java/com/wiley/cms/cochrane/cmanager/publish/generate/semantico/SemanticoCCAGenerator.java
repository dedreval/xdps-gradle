package com.wiley.cms.cochrane.cmanager.publish.generate.semantico;

import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.FilePathCreator;
import com.wiley.cms.cochrane.cmanager.data.db.ClDbVO;
import com.wiley.cms.cochrane.cmanager.entitywrapper.EntireDbWrapper;
import com.wiley.cms.cochrane.cmanager.entitywrapper.PublishWrapper;
import com.wiley.cms.cochrane.cmanager.publish.generate.AbstractGeneratorWhenReady;
import com.wiley.cms.cochrane.cmanager.publish.generate.ArchiveEntry;
import com.wiley.cms.cochrane.cmanager.res.PubType;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author <a href='mailto:sgulin@wiley.com'>Svyatoslav Gulin</a>
 * @version 30.01.2012
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 8/5/2016
 */
public class SemanticoCCAGenerator extends AbstractGeneratorWhenReady {

    public SemanticoCCAGenerator(ClDbVO dbVO) {
        super(dbVO, PubType.TYPE_SEMANTICO);
    }

    protected SemanticoCCAGenerator(ClDbVO dbVO, String exportTypeName) {
        super(dbVO, exportTypeName);
    }

    public SemanticoCCAGenerator(EntireDbWrapper db) {
        super(db, PubType.TYPE_SEMANTICO);
    }

    protected SemanticoCCAGenerator(EntireDbWrapper db, String exportTypeName) {
        super(db, exportTypeName);
    }

    public SemanticoCCAGenerator(boolean fromEntire, int dbId) {
        super(fromEntire, dbId, CochraneCMSPropertyNames.getCcaDbName(), PubType.TYPE_SEMANTICO);
    }

    protected SemanticoCCAGenerator(boolean fromEntire, int dbId, String exportTypeName) {
        super(fromEntire, dbId, CochraneCMSPropertyNames.getCcaDbName(), exportTypeName);
    }

    @Override
    protected void init(PublishWrapper publish) {
        super.init(publish);
        setCheckRecordWithNoOnlineDate(fromEntire() && CochraneCMSPropertyNames.checkEmptyPublicationDateInWML3G4HW());
    }

    @Override
    protected String getPrefix() {
        return sbn + FilePathCreator.SEPARATOR;
    }

    @Override
    protected List<ArchiveEntry> createParticularFiles() throws Exception {
        return new ArrayList<>();
    }
}
