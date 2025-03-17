package com.wiley.cms.cochrane.cmanager.publish.generate.ds;

import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.FilePathBuilder;
import com.wiley.cms.cochrane.cmanager.data.PrevVO;
import com.wiley.cms.cochrane.cmanager.entitymanager.RecordHelper;
import com.wiley.cms.cochrane.cmanager.entitywrapper.EntireDbWrapper;
import com.wiley.cms.cochrane.cmanager.entitywrapper.PublishWrapper;
import com.wiley.cms.cochrane.cmanager.entitywrapper.RecordWrapper;
import com.wiley.cms.cochrane.cmanager.publish.generate.ArchiveEntry;
import com.wiley.cms.cochrane.cmanager.publish.generate.ml3g.ML3GCDSRGeneratorEntire;
import com.wiley.cms.cochrane.cmanager.publish.generate.semantico.SemanticoCDSRGeneratorEntire;
import com.wiley.cms.cochrane.cmanager.res.PubType;
import com.wiley.tes.util.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * @author <a href='mailto:atolkachev@wiley.com'>Andrey Tolkachev</a>
 * @version 03.07.2019
 */

public class DSCDSRGeneratorEntire extends ML3GCDSRGeneratorEntire {
    private static final Logger LOG = Logger.getLogger(SemanticoCDSRGeneratorEntire.class);

    private boolean addPreviousVersion = CochraneCMSPropertyNames.addPreviousVersionForDs();
    private boolean checkRevmanMissed = CochraneCMSPropertyNames.isCheckRevmanMissed();
    private StringBuilder meshNames = null;

    public DSCDSRGeneratorEntire(EntireDbWrapper db) {
        super(db, "DS:ENTIRE:ML3G:" + db.getDbName(), PubType.TYPE_DS);
    }

    @Override
    protected void init(PublishWrapper publish) throws Exception {
        super.init(publish);
        setCheckRecordWithNoOnlineDate(CochraneCMSPropertyNames.checkEmptyPublicationDateInWML3G4DS());
    }

    @Override
    protected List<ArchiveEntry> createParticularFiles() throws Exception {
        List<ArchiveEntry> ret = new ArrayList<>();
        addDoiXml(ret, archiveRootDir, getMeshtermUpdated(), RecordHelper::buildDoiCDSRAndEditorial);
        return ret;
    }

    private String getMeshtermUpdated() {
        return meshNames != null ? meshNames.toString() : null;
    }

    @Override
    protected void onRecordArchive(RecordWrapper record) {
        super.onRecordArchive(record);

        if (byRecords() && record.isMeshtermUpdated()) {
            if (meshNames == null) {
                meshNames = new StringBuilder();
            } else {
                meshNames.append(", ");
            }
            meshNames.append(record.getName());
        }
    }

    @Override
    protected boolean addPreviousVersion() {
        return addPreviousVersion;
    }

    //@Override
    //protected boolean checkRecordWithNoOnlineDate() {
    //    return true;
    //}

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

    @Override
    protected boolean checkEmpty() {
        return true;
    }
}