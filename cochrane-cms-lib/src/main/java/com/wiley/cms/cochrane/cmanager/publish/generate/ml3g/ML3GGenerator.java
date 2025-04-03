package com.wiley.cms.cochrane.cmanager.publish.generate.ml3g;

import com.wiley.cms.cochrane.activitylog.IFlowLogger;
import com.wiley.cms.cochrane.activitylog.ILogEvent;
import com.wiley.cms.cochrane.cmanager.CmsUtils;
import com.wiley.cms.cochrane.cmanager.CochraneCMSProperties;
import com.wiley.cms.cochrane.cmanager.FilePathBuilder;
import com.wiley.cms.cochrane.cmanager.FilePathCreator;
import com.wiley.cms.cochrane.cmanager.contentworker.JatsPackage;
import com.wiley.cms.cochrane.cmanager.data.RecordPublishEntity;
import com.wiley.cms.cochrane.cmanager.data.db.ClDbVO;
import com.wiley.cms.cochrane.cmanager.data.record.IRecordStorage;
import com.wiley.cms.cochrane.cmanager.entitywrapper.RecordWrapper;
import com.wiley.cms.cochrane.cmanager.publish.generate.AbstractGenerator;
import com.wiley.cms.cochrane.cmanager.publish.generate.ArchiveEntry;
import com.wiley.cms.cochrane.cmanager.publish.generate.ArchiveHolder;
import com.wiley.cms.cochrane.cmanager.publish.util.GenerationErrorCollector;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.converter.ml3g.JatsMl3gAssetsManager;
import com.wiley.cms.cochrane.converter.ml3g.Ml3gXmlAssets;
import com.wiley.tes.util.Extensions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;

/**
 * @author <a href='mailto:ikirov@wiley.com'>Igor Kirov</a>
 * @version 01.11.11
 */
public class ML3GGenerator extends AbstractGenerator<ArchiveHolder> {

    protected BaseType.PubInfo special;

    protected ML3GGenerator(ClDbVO db, String generateName, String exportTypeName) {
        super(db, generateName, exportTypeName);
    }

    @Override
    protected List<RecordWrapper> getRecordList(int startIndex, int count) {
        return hasIncludedNames() ? getRecordListFromIncludedNames(count, false)
                : RecordWrapper.getDbRecordWrapperList(getDb().getId(), startIndex, count);
    }

    @Override
    protected List<ArchiveEntry> processRecordList(List<RecordWrapper> recordList) throws Exception {
        excludeDisabledRecords(recordList, checkRecordWithNoOnlineDate());
        excludeNotConvertedToWml3gRecords(recordList, getExportFileName(), errorCollector, recs, flowLogger);
        Map<String, Ml3gXmlAssets> assetsByRecName = getMl3gAssets(recordList);

        List<ArchiveEntry> archEntries = new ArrayList<>();
        addArchiveEntries(assetsByRecName, archEntries);
        recordList.stream()
                .filter(rec -> assetsByRecName.containsKey(rec.getName()))
                .forEach(this::onRecordArchive);

        return archEntries;
    }

    public static void excludeNotConvertedToWml3gRecords(List<RecordWrapper> recLst, String exportFileName,
                                                         GenerationErrorCollector errCollector,
                                                         IRecordStorage rs, IFlowLogger flowLogger) {
        if (recLst.isEmpty()) {
            return;
        }
        List<Integer> notConvertedRecIds = getNotConvertedToWml3gRecordIds(recLst, rs);
        if (notConvertedRecIds.isEmpty()) {
            return;
        }

        List<String> notConvertedRecNames = new ArrayList<>();
        for (Iterator<RecordWrapper> it = recLst.iterator(); it.hasNext();) {
            RecordWrapper rec = it.next();
            if (notConvertedRecIds.contains(rec.getId())) {
                notConvertedRecNames.add(rec.getName());
                flowLogger.onProductPackageError(ILogEvent.PRODUCT_ERROR, exportFileName, rec.getName(),
                    "Record not converted to WML3G and excluded from the package", true,
                        true, CmsUtils.isScheduledIssue(rec.getIssueId()));
                it.remove();
            }
        }
        errCollector.addErrorWithGroupingEntries(
                GenerationErrorCollector.NotificationLevel.WARN,
                CochraneCMSProperties.getProperty(MSG_PARAM_ML3G_ERR),
                notConvertedRecNames);
    }

    private static List<Integer> getNotConvertedToWml3gRecordIds(List<RecordWrapper> recLst, IRecordStorage rs) {
        List<Integer> recIds = new ArrayList<>(recLst.size());
        for (RecordWrapper rec : recLst) {
            recIds.add(rec.getId());
        }
        return rs.getRecordIds(recIds, RecordPublishEntity.getNotConvertedStates());
    }

    protected void setSpecial(BaseType.PubInfo value) {
        special = value;
    }

    protected boolean hasSpecial() {
        return special != null;
    }

    private Map<String, Ml3gXmlAssets> getMl3gAssets(List<RecordWrapper> recLst) {
        int issueId = getDb().getIssue().getId();
        Map<String, Ml3gXmlAssets> assetsByRecName = new HashMap<>(recLst.size());
        for (RecordWrapper rec : recLst) {
            Ml3gXmlAssets ml3gAssets = getMl3gAssets(issueId, rec);
            if (ml3gAssets != null) {
                assetsByRecName.put(rec.getName(), ml3gAssets);
            }
        }
        return assetsByRecName;
    }

    private Ml3gXmlAssets getMl3gAssets(int issueId, RecordWrapper rec) {
        Ml3gXmlAssets assets = null;
        try {
            List<String> assetUris = getAssetsUris(issueId, rec, isRecordOutdated(rec));
            if (assetUris != null) {
                assets = new Ml3gXmlAssets();
                assets.setXmlUri(getWml3gXmlUri(issueId, rec.getName()));
                assets.setAssetsUris(assetUris);
            }
        } catch (Exception e) {
            assets = null;
            String reason = e.getMessage();
            if (StringUtils.isEmpty(reason)) {
                reason = "Undefined reason";
            }

            String message = String.format("Failed to obtain WML3G content for %s: %s", rec.getName(), reason);
            LOG.error(message, e);
            errorCollector.addError(GenerationErrorCollector.NotificationLevel.ERROR, message);
            flowLogger.onProductPackageError(ILogEvent.PRODUCT_ERROR, getExportFileName(),  rec.getName(), message,
                    true, true, CmsUtils.isScheduledIssue(getIssueId()));
        }
        return assets;
    }

    private String getWml3gXmlUri(int issueId, String recName) {
        String xmlUri = null;
        if (hasSpecial()) {
            //xmlUri = FilePathBuilder.ML3G.getPathToMl3gNoTARecord(issueId, getDb().getTitle(), recWrapper.getName());
            xmlUri = FilePathBuilder.ML3G.getPathToMl3gRecordByPostfix(issueId, getDb().getTitle(),
                    recName, special.getSourcePostfix());
            if (!rps.isFileExistsQuiet(xmlUri)) {
                xmlUri = null;
            }
        }

        if (xmlUri == null) {
            xmlUri = FilePathCreator.getFilePathForMl3gXml(issueId, getDb().getTitle(), recName);
        }

        return xmlUri;
    }

    protected List<String> getAssetsUris(int issueId, RecordWrapper record, boolean outdated) throws Exception {
        return Collections.emptyList();
    }

    protected void addArchiveEntries(Map<String, Ml3gXmlAssets> assetsByRecName, List<ArchiveEntry> archEntries) {
        for (String recName : assetsByRecName.keySet()) {
            String ml3gPathInArch = getArchivePath(recName);
            String prefix = getArchivePrefix(recName);
            addArchiveEntries(recName, ml3gPathInArch, prefix, assetsByRecName.get(recName), archEntries);
        }
    }

    private String getArchivePath(String recName) {
        return getArchivePrefix(recName) + "/" + recName + Extensions.XML;
    }

    protected String getArchivePrefix(String recName) {
        return recName;
    }

    protected void addArchiveEntries(String recName,
                                     String ml3gPathInArch,
                                     String prefix,
                                     Ml3gXmlAssets assets,
                                     List<ArchiveEntry> archEntries) {
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
}