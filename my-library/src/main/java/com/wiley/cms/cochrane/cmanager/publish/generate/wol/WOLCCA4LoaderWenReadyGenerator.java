package com.wiley.cms.cochrane.cmanager.publish.generate.wol;

import com.wiley.cms.cochrane.cmanager.data.db.ClDbVO;
import com.wiley.cms.cochrane.cmanager.entitywrapper.EntireDbWrapper;
import com.wiley.cms.cochrane.cmanager.entitywrapper.RecordWrapper;
import com.wiley.cms.cochrane.cmanager.publish.PublishHelper;
import com.wiley.cms.cochrane.cmanager.publish.generate.ArchiveEntry;
import com.wiley.cms.cochrane.cmanager.publish.generate.semantico.SemanticoCCAGenerator;
import com.wiley.cms.cochrane.cmanager.res.PubType;
import com.wiley.tes.util.Extensions;
import org.apache.commons.collections.CollectionUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @author <a href='mailto:sgulin@wiley.com'>Svyatoslav Gulin</a>
 * @version 30.01.2012
 */
public class WOLCCA4LoaderWenReadyGenerator extends SemanticoCCAGenerator {

    public static final String PART_STR = "_part";
    private static final int OL_LOADER_MAX_PACKAGE_SIZE = 50;
    private boolean entriesAdded;
    private int packagePart = 1;

    public WOLCCA4LoaderWenReadyGenerator(ClDbVO dbVO) {
        super(dbVO, PubType.TYPE_WOL);
    }

    public WOLCCA4LoaderWenReadyGenerator(EntireDbWrapper db) {
        super(db, PubType.TYPE_WOL);
    }

    public WOLCCA4LoaderWenReadyGenerator(boolean fromEntire, int dbId) {
        super(fromEntire, dbId, PubType.TYPE_WOL);
    }

    @Override
    protected void generateArchive() throws Exception {
        do {
            super.generateArchive();
        } while (entriesAdded);
        if (packagePart != 1) {
            archiveName = getPreviousPackagePartName(archiveName);
        }
    }

    @Override
    protected void defineArchiveName() {
        if (archiveName == null) {
            super.defineArchiveName();
        } else {
            archiveName = getNextPackagePartName();
            packagePart += 1;
        }
    }

    @Override
    protected String buildArchiveName(Date timeStamp) {
        String tmpName = super.buildArchiveName(timeStamp);
        String ext = Extensions.TAR_GZ;
        if (!tmpName.endsWith(ext)) {
            ext = tmpName.substring(tmpName.lastIndexOf("."));
        }

        return tmpName.replace(ext, PART_STR + String.valueOf(packagePart) + Extensions.ZIP);
    }

    private String getNextPackagePartName() {
        String curPart = PART_STR + packagePart;
        String nextPart = PART_STR + (packagePart + 1);
        return archiveName.replace(curPart, nextPart);
    }

    @Override
    protected void createArchive() throws Exception {
        int beginIndex = (packagePart - 1) * OL_LOADER_MAX_PACKAGE_SIZE;
        int maxRecordsNumber = OL_LOADER_MAX_PACKAGE_SIZE;
        List<RecordWrapper> recordList = getRecords(beginIndex, maxRecordsNumber);

        if (CollectionUtils.isNotEmpty(recordList)) {
            List<ArchiveEntry> archiveEntryList = processRecordList(recordList);
            addToArchiveLegacyImpl(archiveEntryList);
            List<ArchiveEntry> particularFilesList = createParticularFiles();
            addToArchiveLegacyImpl(particularFilesList);

            entriesAdded = true;
        } else {
            entriesAdded = false;
        }
    }

    @Override
    protected List<ArchiveEntry> createParticularFiles() throws Exception {
        List<ArchiveEntry> ret = new ArrayList<ArchiveEntry>();
        addControlFile(ret);
        return ret;
    }

    private void addControlFile(List<ArchiveEntry> ret) throws IOException {
        ret.add(new ArchiveEntry(getPrefix() + "control_file.xml", null, PublishHelper.getControlFileContent()));
    }

    private String getPreviousPackagePartName(String archiveName) {
        return archiveName.replace(PART_STR + packagePart, PART_STR + (packagePart - 1));
    }
}
