package com.wiley.cms.cochrane.cmanager.publish.generate.ml3g;

import com.wiley.cms.cochrane.cmanager.CochraneCMSProperties;
import com.wiley.cms.cochrane.cmanager.FilePathBuilder;
import com.wiley.cms.cochrane.cmanager.FilePathCreator;
import com.wiley.cms.cochrane.cmanager.contentworker.JatsPackage;
import com.wiley.cms.cochrane.cmanager.data.PrevVO;
import com.wiley.cms.cochrane.cmanager.data.RecordPublishEntity;
import com.wiley.cms.cochrane.cmanager.data.entire.IEntireDBStorage;
import com.wiley.cms.cochrane.cmanager.entitymanager.AbstractManager;
import com.wiley.cms.cochrane.cmanager.entitywrapper.EntireDbWrapper;
import com.wiley.cms.cochrane.cmanager.entitywrapper.EntireRecordWrapper;
import com.wiley.cms.cochrane.cmanager.entitywrapper.RecordWrapper;
import com.wiley.cms.cochrane.cmanager.entitywrapper.SearchRecordOrder;
import com.wiley.cms.cochrane.cmanager.publish.PublishHelper;
import com.wiley.cms.cochrane.cmanager.publish.generate.AbstractGeneratorEntire;
import com.wiley.cms.cochrane.cmanager.publish.generate.ArchiveEntry;
import com.wiley.cms.cochrane.cmanager.publish.generate.ArchiveHolder;
import com.wiley.cms.cochrane.cmanager.publish.util.GenerationErrorCollector;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.converter.ml3g.JatsMl3gAssetsManager;
import com.wiley.cms.cochrane.converter.ml3g.Ml3gXmlAssets;
import com.wiley.cms.cochrane.utils.Constants;
import com.wiley.cms.cochrane.utils.ContentLocation;
import com.wiley.tes.util.Extensions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author <a href='mailto:ikirov@wiley.com'>Igor Kirov</a>
 * @version 14.11.11
 */
public class ML3GCDSRGeneratorEntire extends AbstractGeneratorEntire<ArchiveHolder> {
    private static final IEntireDBStorage DB_STORAGE = AbstractManager.getEntireDBStorage();

    protected final String archiveRootDir;
    private Set<Integer> previousVersions;
    private BaseType.PubInfo special;

    public ML3GCDSRGeneratorEntire(EntireDbWrapper db, String generateName, String exportTypeName) {
        super(db, generateName, exportTypeName);
        previousVersions = Collections.emptySet();
        archiveRootDir = BaseType.find(db.getDbName()).get().getPubInfo(getExportTypeName()).getSBNDir();
    }

    @Override
    protected List<EntireRecordWrapper> getRecordList(int startIndex, int count) {
        return hasIncludedNames() ? getRecordListFromIncludedNames(count, SearchRecordOrder.NONE, false)
                : EntireRecordWrapper.getRecordWrapperList(getDb().getDbName(), startIndex, count);
    }

    @Override
    protected List<ArchiveEntry> processRecordList(List<EntireRecordWrapper> recordList) throws Exception {
        List<ArchiveEntry> archEntries = new ArrayList<>();
        excludeDisabledRecords(recordList, checkRecordWithNoOnlineDate());
        excludeUnconvertedRecords(recordList, errorCollector);
        if (!recordList.isEmpty()) {
            processCurrentVersion(recordList, archEntries);
            if (addPreviousVersion()) {
                processPreviousVersions(recordList, archEntries);
            }
        }
        return archEntries;
    }

    protected void setSpecial(BaseType.PubInfo value) {
        special = value;
    }

    protected boolean hasSpecial() {
        return special != null;
    }

    protected boolean validContent(RecordWrapper rw) {
        return true;
    }

    protected boolean validContentPrevious(PrevVO prev) {
        return true;
    }

    public static void excludeUnconvertedRecords(List<? extends RecordWrapper> recWrappers,
                                                 GenerationErrorCollector errCollector) {
        if (recWrappers.isEmpty()) {
            return;
        }

        List<Integer> recIds = new ArrayList<>(recWrappers.size());
        for (RecordWrapper recWrapper : recWrappers) {
            recIds.add(recWrapper.getId());
        }

        Set<Integer> unconvRecIds = new HashSet<>();
        unconvRecIds.addAll(DB_STORAGE.getRecordIds(recIds, RecordPublishEntity.CONVERSION_FAILED));
        if (unconvRecIds.isEmpty()) {
            return;
        }

        StringBuilder msg = new StringBuilder();
        msg.append(CochraneCMSProperties.getProperty(MSG_PARAM_ML3G_ERR)).append(":");
        Iterator<? extends RecordWrapper> it = recWrappers.iterator();
        while (it.hasNext()) {
            RecordWrapper recWrapper = it.next();
            if (unconvRecIds.contains(recWrapper.getId())) {
                msg.append(" ").append(recWrapper.getName()).append(",");
                it.remove();
            }
        }
        msg.replace(msg.length() - 1, msg.length(), ".\n");

        errCollector.addError(GenerationErrorCollector.NotificationLevel.WARN, msg.toString());
    }

    private void processCurrentVersion(List<EntireRecordWrapper> recWrappers, List<ArchiveEntry> archEntries) {
        for (EntireRecordWrapper recWrapper : recWrappers) {

            String recName = recWrapper.getName();

            if (!validContent(recWrapper)) {
                continue;
            }

            StringBuilder errs = new StringBuilder();

            Ml3gXmlAssets assets = new Ml3gXmlAssets();
            try {
                assets.setXmlUri(getWml3gXmlUri(getDb().getDbName(), recName));
                assets.setAssetsUris(getAssets(recWrapper, errs));

                if (assets.getAssetsUris() == null) {
                    throw new Exception(errs.toString());
                }
                addArchiveEntries(recName, assets, archEntries, Constants.UNDEF, ContentLocation.ENTIRE);

                onRecordArchive(recWrapper);
            } catch (Exception e) {
                errorCollector.addError(GenerationErrorCollector.NotificationLevel.WARN, e.getMessage() + "\n");
            }
        }
    }

    private String getWml3gXmlUri(String dbName, String recName) throws Exception {

        String xmlUri = null;
        if (hasSpecial()) {
            //xmlUri = FilePathBuilder.ML3G.getPathToEntireMl3gNoTARecord(dbName, recName);
            xmlUri = FilePathBuilder.ML3G.getPathToEntireMl3gRecordByPostfix(dbName, recName,
                    special.getSourcePostfix());
            if (!rps.isFileExists(xmlUri)) {
                xmlUri = null;
            }
        }

        if (xmlUri == null) {
            xmlUri = FilePathCreator.getFilePathForEntireMl3gXml(dbName, recName);
        }

        return xmlUri;
    }

    protected List<String> getAssets(RecordWrapper record, StringBuilder errs) {
        return PublishHelper.getPublishContentUris(this, Constants.UNDEF, record.getName(), false, true,
            ContentLocation.ENTIRE, errs);
    }

    private void processPreviousVersions(List<EntireRecordWrapper> recWrappers, List<ArchiveEntry> archEntries) {

        Map<Integer, List<PrevVO>> recWrappersByVer = getRecordsByVersions(recWrappers);

        for (int version : recWrappersByVer.keySet()) {
            List<PrevVO> tmpRecWrappers = recWrappersByVer.get(version);
            for (PrevVO prev : tmpRecWrappers) {
                String cdNumber = prev.name;

                if (!validContentPrevious(prev)) {
                    continue;
                }

                StringBuilder errs = new StringBuilder();
                Ml3gXmlAssets assets = new Ml3gXmlAssets();
                try {
                    assets.setXmlUri(ML3GCDSRGenerator.getWml3gXmlUriPrevious(version, cdNumber, special, rps));
                    assets.setAssetsUris(PublishHelper.getPublishContentUris(this, version, cdNumber, false, false,
                            ContentLocation.PREVIOUS, errs));

                    if (assets.getAssetsUris() == null) {
                        throw new Exception(errs.toString());
                    }
                    if (isRecordWithNoFirstOnlineDate(prev.buildPubName(), assets::getXmlUri)) {
                        continue;
                    }

                    addArchiveEntries(cdNumber, assets, archEntries, version, ContentLocation.PREVIOUS);
                    onRecordArchive(cdNumber, prev.version, prev.buildPubName());

                } catch (Exception e) {
                    errorCollector.addError(GenerationErrorCollector.NotificationLevel.WARN, e.getMessage() + "\n");
                }
            }
        }
        previousVersions = recWrappersByVer.keySet();
    }

    private Map<Integer, List<PrevVO>> getRecordsByVersions(List<EntireRecordWrapper> recWrappers) {
        Map<Integer, List<PrevVO>> recWrappersByVer = new HashMap<Integer, List<PrevVO>>();
        for (EntireRecordWrapper recWrapper : recWrappers) {
            for (PrevVO prev : recWrapper.getVersions().getPreviousVersionsVO()) {
                if (!recWrappersByVer.containsKey(prev.version)) {
                    recWrappersByVer.put(prev.version, new ArrayList<PrevVO>());
                }
                recWrappersByVer.get(prev.version).add(prev);
            }
        }

        return recWrappersByVer;
    }

    private void addArchiveEntries(String recName,
                                   Ml3gXmlAssets assets,
                                   List<ArchiveEntry> archEntries,
                                   int version,
                                   ContentLocation contentLocation) {
        String ml3gPathInArch;
        String prefix;
        if (contentLocation == ContentLocation.ENTIRE) {
            ml3gPathInArch = getArchivePath(recName);
            prefix = getArchivePrefix(recName);
        } else {
            ml3gPathInArch = getPreviousPath(recName, version);
            prefix = getPreviousPrefix(recName, version);
        }

        archEntries.add(new ArchiveEntry(ml3gPathInArch, rps.getRealFilePath(assets.getXmlUri())));
        for (String assetUri : assets.getAssetsUris()) {
            String filePath;
            String assetPathInArch;
            if (assetUri.contains(JatsPackage.JATS_FOLDER)) {
                filePath = rps.getRealFilePath(JatsMl3gAssetsManager.getRealAssetUri(assetUri));
                assetPathInArch = JatsMl3gAssetsManager.getMappedAsset(assetUri);
            } else {
                filePath = rps.getRealFilePath(assetUri);
                assetPathInArch = assetUri.substring(assetUri.indexOf(recName) + recName.length() + 1);
            }

            archEntries.add(new ArchiveEntry(prefix + "/" + assetPathInArch, filePath));
        }
    }

    private String getArchivePath(String recName) {
        return getArchivePrefix(recName) + "/" + recName + Extensions.XML;
    }

    protected boolean addPreviousVersion() {
        return true;
    }

    @Override
    protected String getArchivePrefix(String recName) {
        return archiveRootDir + "/" + recName;
    }

    protected String addToRoot(String toAdd) {
        if (archiveRootDir != null) {
            String root = archiveRootDir;
            if (archiveRootDir.startsWith(FilePathCreator.SEPARATOR)) {
                root = archiveRootDir.substring(FilePathCreator.SEPARATOR.length());
            }
            if (!root.isEmpty() && !root.startsWith(FilePathCreator.SEPARATOR)) {
                return root + FilePathCreator.SEPARATOR + toAdd;
            }
        }
        return toAdd;
    }

    private String getPreviousPath(String recName, int version) {
        return getPreviousPrefix(recName, version) + "/" + recName + Extensions.XML;
    }

    private String getPreviousPrefix(String recName, int version) {
        return archiveRootDir + "/" + FilePathBuilder.buildHistoryDir(version) + "/" + recName;
    }

    @Override
    protected List<ArchiveEntry> createParticularFiles() throws Exception {
        List<ArchiveEntry> ret = new ArrayList<>();
        addControlFile(ret);
        return ret;
    }

    private void addControlFile(List<ArchiveEntry> ret) throws IOException {

        String controlFile = PublishHelper.getControlFileContent();
        ret.add(new ArchiveEntry(archiveRootDir + "/" + Constants.CONTROL_FILE, null, controlFile));
        for (Integer version : previousVersions) {
            ret.add(new ArchiveEntry(archiveRootDir + "/" + FilePathBuilder.buildHistoryDir(version)
                    + "/" + Constants.CONTROL_FILE, null, controlFile));
        }
    }
}