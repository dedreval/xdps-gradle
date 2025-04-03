package com.wiley.cms.cochrane.cmanager.publish.generate.ml3g;

import com.wiley.cms.cochrane.activitylog.ILogEvent;
import com.wiley.cms.cochrane.cmanager.CmsUtils;
import com.wiley.cms.cochrane.cmanager.FilePathBuilder;
import com.wiley.cms.cochrane.cmanager.FilePathCreator;
import com.wiley.cms.cochrane.cmanager.data.PrevVO;
import com.wiley.cms.cochrane.cmanager.data.db.ClDbVO;
import com.wiley.cms.cochrane.cmanager.entitywrapper.RecordWrapper;
import com.wiley.cms.cochrane.cmanager.publish.PublishHelper;
import com.wiley.cms.cochrane.cmanager.publish.generate.ArchiveEntry;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.cmanager.res.SPDManifest;
import com.wiley.cms.cochrane.converter.ml3g.Ml3gXmlAssets;
import com.wiley.cms.cochrane.converter.ml3g.Wml3gConverterPrevious;
import com.wiley.cms.cochrane.repository.IRepository;
import com.wiley.cms.cochrane.utils.Constants;
import com.wiley.cms.cochrane.utils.ContentLocation;
import com.wiley.cms.converter.services.RevmanMetadataHelper;
import com.wiley.tes.util.Extensions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author <a href='mailto:ikirov@wiley.com'>Igor Kirov</a>
 * @version 01.11.11
 */
public class ML3GCDSRGenerator extends ML3GGenerator {

    protected final String archiveRootDir;
    private Set<Integer> previousVersions;
    private SPDManifest spdManifest;

    protected ML3GCDSRGenerator(ClDbVO db, String generateName, String exportTypeName) {
        super(db, generateName, exportTypeName);
        archiveRootDir = getArchiveRootDirectory();
    }

    private String getArchiveRootDirectory() {
        return BaseType.find(getDb().getTitle()).get().getPubInfo(getExportTypeName()).getSBNDir();
    }

    @Override
    protected List<RecordWrapper> getRecordList(int startIndex, int count) {
        return byDeliveryPacket() ? RecordWrapper.getDbRecordWrapperList(getDb().getId(), getDeliveryFileId(),
                startIndex, count) : RecordWrapper.getDbRecordWrapperList(getDb().getId(), startIndex, count);
    }

    @Override
    protected List<ArchiveEntry> processRecordList(List<RecordWrapper> recordList) throws Exception {
        List<ArchiveEntry> archEntries = super.processRecordList(recordList);
        if (addPreviousVersion()) {
            processPreviousVersion(recordList, archEntries);
        }
        return archEntries;
    }

    @Override
    protected List<String> getAssetsUris(int issueId, RecordWrapper record, boolean outdated) throws Exception {
        StringBuilder errs = new StringBuilder();
        List<String> assetsUris = null;
        if (!record.isPublishingCanceled()) {
            assetsUris = PublishHelper.getPublishContentUris(this, Constants.UNDEF, record.getName(),
                    outdated, true, ContentLocation.ISSUE, errs);
            if (assetsUris == null) {
                throw new Exception(errs.toString());
            }
        }
        addToSPDManifest(record);
        return assetsUris;
    }

    protected final void addToSPDManifest(RecordWrapper record) {
        if (CmsUtils.isScheduledIssue(record.getIssueId())) {
            if (spdManifest == null) {
                spdManifest = new SPDManifest(getExportFileName());
            }
            spdManifest.addSubmission(RevmanMetadataHelper.buildDoi(record.getName(), record.getPubNumber()),
                    record.getPublishedOnlineFinalForm(), record.isPublishingCanceled());
        }
    }

    protected final List<ArchiveEntry> addSPDManifest(List<ArchiveEntry> list) {
        List<ArchiveEntry> ret = list;
        if (spdManifest != null) {
            if (ret == null) {
                ret = new ArrayList<>();
            }
            ret.add(new ArchiveEntry(addToRoot(Constants.CONTROL_FILE_MANIFEST), null, spdManifest.asXmlString()));
        }
        return ret == null ? Collections.emptyList() : ret;
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

    protected boolean addPreviousVersion() {
        return true;
    }

    private void processPreviousVersion(Iterable<RecordWrapper> recWrappers, List<ArchiveEntry> entries)
            throws Exception {
        Map<Integer, Map<String, String>> map = getRecordWrappersByVersions(recWrappers);
        for (Map.Entry<Integer, Map<String, String>> entry : map.entrySet()) {

            Integer version = entry.getKey();
            Map<String, String> records = entry.getValue();
            List<String> unconvertedNames = new ArrayList<>();
            Map<String, Ml3gXmlAssets> assetsByRecName = getPreviousMl3gAssets(records.keySet(), unconvertedNames,
                    version);
            convertMissedRecords(assetsByRecName, unconvertedNames, version);

            for (Map.Entry<String, String> record: records.entrySet()) {
                String doi = record.getValue();
                String cdNumber = record.getKey();
                String pubName = RevmanMetadataHelper.parsePubName(doi);
                String ml3gPathInArch = getPreviousPath(cdNumber, version);
                String prefix = getPreviousPrefix(cdNumber, version);
                Ml3gXmlAssets assets = assetsByRecName.get(cdNumber);
                if (assets == null || isRecordWithNoFirstOnlineDate(pubName, assets::getXmlUri)) {
                    continue;
                }
                addArchiveEntries(cdNumber, ml3gPathInArch, prefix, assets, entries);
                onRecordArchive(cdNumber, version, pubName);
            }
        }
        previousVersions = map.keySet();
    }

    private static Map<Integer, Map<String, String>> getRecordWrappersByVersions(Iterable<RecordWrapper> wrappers) {
        Map<Integer, Map<String, String>> map = new HashMap<>();
        for (RecordWrapper wrapper : wrappers) {
            if (!wrapper.getVersions().isPreviousVersionExist() || wrapper.isPublishingCanceled()) {
                continue;
            }
            List<PrevVO> list = wrapper.getVersions().getPreviousVersionsVO();
            for (PrevVO prev : list) {
                Map<String, String> records = map.computeIfAbsent(prev.version, f -> new HashMap<>());
                records.put(prev.name, prev.buildDoi());
            }
        }
        return map;
    }

    private Map<String, Ml3gXmlAssets> getPreviousMl3gAssets(Collection<String> recNames,
                                                             Collection<String> unconvRecNames, int version) {
        Map<String, Ml3gXmlAssets> assetsByRecName = new HashMap<>(recNames.size());
        for (String recName : recNames) {
            StringBuilder errs = new StringBuilder();
            List<String> assetsUris = PublishHelper.getPublishContentUris(this, version, recName, false, true,
                    ContentLocation.PREVIOUS, errs);

            Ml3gXmlAssets assets = new Ml3gXmlAssets();
            assets.setXmlUri(getWml3gXmlUriPrevious(version, recName, special, rps));
            assets.setAssetsUris(assetsUris);
            assetsByRecName.put(recName, assets);

            if (errs.length() > 0) {
                unconvRecNames.add(recName);
            }
        }
        return assetsByRecName;
    }

    static String getWml3gXmlUriPrevious(int version, String recName, BaseType.PubInfo special, IRepository rp) {

        String xmlUri = null;
        if (special != null) {
            //xmlUri = FilePathBuilder.ML3G.getPathToPreviousMl3gNoTARecord(version, recName);
            xmlUri = FilePathBuilder.ML3G.getPathToPreviousMl3gRecordByPostfix(version, recName,
                    special.getSourcePostfix());
            try {
                if (!rp.isFileExists(xmlUri)) {
                    xmlUri = null;
                }
            } catch (Exception e) {
                xmlUri = null;
                LOG.error(e);
            }
        }

        if (xmlUri == null) {
            xmlUri = FilePathCreator.getPreviousMl3gXmlPath(recName, version);
        }
        return xmlUri;
    }

    private void convertMissedRecords(Map<String, Ml3gXmlAssets> assetsByRecName,
                                      List<String> unconvRecNames,
                                      int version) throws Exception {
        if (unconvRecNames.isEmpty()) {
            return;
        }

        new Wml3gConverterPrevious(unconvRecNames, version).execute();
        for (String recName : unconvRecNames) {
            StringBuilder errs = new StringBuilder();
            List<String> assetsUris = PublishHelper.getPublishContentUris(this, version, recName, false, true,
                    ContentLocation.PREVIOUS, errs);
            assetsByRecName.get(recName).setAssetsUris(assetsUris);

            if (errs.length() > 0) {
                String message = String.format("Failed to get WML3G assets for record [%s] historical number %d, %s",
                        recName, version, errs);
                flowLogger.onProductPackageError(ILogEvent.PRODUCT_ERROR, getExportFileName(), recName, message,
                        true, true, CmsUtils.isScheduledIssue(getDb().getIssue().getId()));
                throw new Exception(message);
            }
        }
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

    /*public static void excludeRawData(RecordWrapper record, List<String> assetsUris) {
        int i = 0;
        boolean find = false;
        if (record.isRawDataExists()) {
            for (String uri: assetsUris) {
                if (FilePathBuilder.containsRawData(uri)) {
                    find = true;
                    break;
                }
                i++;
            }
            if (!find)  {
                LOG.warn(String.format("record %s has raw data, but its assets have no raw data link",
                        record.getRecordPath()));
            } else {
                assetsUris.remove(i);
            }
        }
    }*/

    private void addControlFile(Collection<ArchiveEntry> ret) throws IOException {

        String controlFileContent = PublishHelper.getControlFileContent();
        ret.add(new ArchiveEntry(archiveRootDir + "/" +  Constants.CONTROL_FILE, null, controlFileContent));
        for (Integer version : previousVersions) {
            ret.add(new ArchiveEntry(archiveRootDir + "/" + FilePathBuilder.buildHistoryDir(version)
                    + "/" +  Constants.CONTROL_FILE, null, controlFileContent));
        }
    }
}
