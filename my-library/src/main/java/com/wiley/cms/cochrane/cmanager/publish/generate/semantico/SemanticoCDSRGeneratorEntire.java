package com.wiley.cms.cochrane.cmanager.publish.generate.semantico;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.FilePathBuilder;
import com.wiley.cms.cochrane.cmanager.contentworker.RevmanPackage;
import com.wiley.cms.cochrane.cmanager.data.PrevVO;
import com.wiley.cms.cochrane.cmanager.entitywrapper.EntireDbWrapper;
import com.wiley.cms.cochrane.cmanager.entitywrapper.EntireRecordWrapper;
import com.wiley.cms.cochrane.cmanager.entitywrapper.PublishWrapper;
import com.wiley.cms.cochrane.cmanager.entitywrapper.RecordWrapper;
import com.wiley.cms.cochrane.cmanager.publish.generate.ArchiveEntry;
import com.wiley.cms.cochrane.cmanager.publish.generate.ml3g.ML3GCDSRGeneratorEntire;
import com.wiley.cms.cochrane.cmanager.res.PubType;
import com.wiley.tes.util.Logger;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 5/27/2016
 */
public class SemanticoCDSRGeneratorEntire extends ML3GCDSRGeneratorEntire {
    private static final Logger LOG = Logger.getLogger(SemanticoCDSRGeneratorEntire.class);

    private boolean addPreviousVersion = CochraneCMSPropertyNames.addPreviousVersionForSemantico();
    private boolean checkRevmanMissed = CochraneCMSPropertyNames.isCheckRevmanMissed();

    public SemanticoCDSRGeneratorEntire(EntireDbWrapper db) {
        super(db, "SEMANTICO:ENTIRE:ML3G:" + db.getDbName(), PubType.TYPE_SEMANTICO);
    }

    protected SemanticoCDSRGeneratorEntire(EntireDbWrapper db, String generateName, String exportTypeName) {
        super(db, generateName, exportTypeName);
    }

    @Override
    protected void init(PublishWrapper publish) throws Exception {
        super.init(publish);
        setCheckRecordWithNoOnlineDate(CochraneCMSPropertyNames.checkEmptyPublicationDateInWML3G4HW());
    }

    @Override
    protected List<ArchiveEntry> createParticularFiles() throws IOException {
        List<ArchiveEntry> ret = new ArrayList<>();
        if (!byRecords() || RevmanPackage.hasPreviousEditorial()) {
            SemanticoCDSRGenerator.addEditorialTopicXml(ret, archiveRootDir, rps);
        }
        return ret;
    }

    @Override
    protected boolean addPreviousVersion() {
        return addPreviousVersion;
    }

    @Override
    protected boolean checkEmpty() {
        return true;
    }

    @Override
    protected boolean validContent(RecordWrapper rw) {
        if (checkRevmanMissed) {
            String path = FilePathBuilder.getPathToEntireRevmanRecord(rw.getGroupName(), rw.getName());
            boolean ret = rps.isFileExistsQuiet(path);
            if (!ret) {
                LOG.warn(String.format("cannot find revman of %s - last version!", path));
            }
        }
        return true;
    }

    @Override
    protected boolean validContentPrevious(PrevVO prev) {
        return !checkRevmanMissed || rps.isFileExistsQuiet(
                    FilePathBuilder.getPathToPreviousRevmanRecord(prev.version, prev.group, prev.name));
    }

    protected boolean isOnlyTopic() {
        return false;
    }

    @Override
    protected List<ArchiveEntry> processRecordList(List<EntireRecordWrapper> recordList) throws Exception  {
        return isOnlyTopic() ? Collections.emptyList() : super.processRecordList(recordList);
    }
}
