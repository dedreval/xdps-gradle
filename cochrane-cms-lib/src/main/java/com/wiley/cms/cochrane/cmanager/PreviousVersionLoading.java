package com.wiley.cms.cochrane.cmanager;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import javax.ejb.EJB;
import javax.ejb.Local;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;

import com.wiley.cms.cochrane.activitylog.IFlowLogger;
import com.wiley.cms.cochrane.activitylog.ILogEvent;
import com.wiley.cms.cochrane.cmanager.data.record.IRecord;
import com.wiley.cms.cochrane.cmanager.data.record.RecordEntity;
import com.wiley.cms.cochrane.cmanager.entitymanager.ICDSRMeta;
import com.wiley.cms.cochrane.cmanager.entitymanager.IVersionManager;
import com.wiley.cms.cochrane.cmanager.entitymanager.RecordHelper;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.converter.ml3g.Ml3gAssetsManager;
import com.wiley.cms.cochrane.utils.Constants;
import com.wiley.cms.cochrane.utils.ContentLocation;

import com.wiley.cms.cochrane.cmanager.data.IResultsStorage;
import com.wiley.cms.cochrane.cmanager.translated.TranslatedAbstractsHelper;
import com.wiley.cms.cochrane.repository.IRepository;
import com.wiley.cms.cochrane.repository.RepositoryFactory;
import com.wiley.tes.util.Extensions;
import com.wiley.tes.util.Logger;

/**
 * Type comments here.
 *
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @version 1.0
 */
@Stateless
@Local(IPreviousVersionLoading.class)
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class PreviousVersionLoading implements IPreviousVersionLoading {
    private static final Logger LOG = Logger.getLogger(PreviousVersionLoading.class);

    private static final String RND_HTML_DIR = "/rnd_html/";

    @EJB(beanName = "ResultsStorage")
    private IResultsStorage resultsStorage;

    @EJB(beanName = "VersionManager")
    private IVersionManager vm;

    @EJB(beanName = "FlowLogger")
    private IFlowLogger flowLogger;

    private final IRepository rp = RepositoryFactory.getRepository();

    private void copyHtml(int version, String name, String recordPath, String fromPath) throws IOException {

        String pathTo = FilePathBuilder.getPathToPreviousHtml(version) + name;
        String[] rpaths = recordPath.split("/");

        String pathFrom = rpaths[0] + "/" + rpaths[1]
                + "/" + rpaths[2] + fromPath + name;
        CmsUtils.writeDir(pathFrom, pathTo, true, rp);
    }

    private void copySrc(int version, String name, String recordPath) throws IOException {
        if (!rp.isFileExists(recordPath)) {
            return;
        }
        String pathToSrc = FilePathBuilder.getPathToPreviousSrc(version);
        String pathTo = pathToSrc + recordPath.substring(recordPath.lastIndexOf("/"));
        rp.putFile(pathTo, rp.getFile(recordPath));

        String dirPath = recordPath.replace(".xml", "");
        File[] files = rp.getFilesFromDir(dirPath);

        if (files != null && files.length != 0) {
            pathTo = pathToSrc + name;
            CmsUtils.writeDir(dirPath, pathTo, true, rp);
        }
    }

    private void copyMl3gContent(String dbName, int version, IRecord record)  {
        String recName = record.getName();
        String srcUri = FilePathCreator.getFilePathForEntireMl3gXml(dbName, recName);
        String destUri = FilePathCreator.getPreviousMl3gXmlPath(recName, version);
        StringBuilder errs = new StringBuilder();
        try {
            InputStream is = rp.getFile(srcUri);
            rp.putFile(destUri, is);

        } catch (IOException e) {
            errs.append(String.format("Failed to copy WML3G xml from [%s] to [%s], %s.\n", srcUri, destUri, e));
        }
        Ml3gAssetsManager.copyAssetsFromOneLocation2Another(dbName, Constants.UNDEF, recName, version,
                ContentLocation.ENTIRE, ContentLocation.PREVIOUS, errs);
        if (errs.length() > 0) {
            LOG.error(errs);
            record.setSuccessful(false, errs.toString());
        }
    }

    private void deleteRevman(IRecord rec, String oldGroup) {
        try {
            String path = FilePathBuilder.getPathToEntireRevmanSrc(oldGroup) + rec.getName();
            String pathMeta = FilePathBuilder.buildMetadataRecordName(path);
            path += Extensions.XML;

            if (rp.isFileExists(path)) {
                rp.deleteFile(path);
            }
            if (rp.isFileExists(pathMeta)) {
                rp.deleteFile(pathMeta);
            }
        } catch (Exception e) {

            LOG.error("Error during delete revman from old group: " + oldGroup, e);
            rec.setSuccessful(false, e.getMessage());
        }
    }

    private void copyRevman(int version, IRecord rec, String group) {

        if (group == null || group.isEmpty()) {

            LOG.error("Revman group is null, " + rec.getName());
            rec.setSuccessful(false, "revman group is null");
            return;
        }

        String recPath = rec.getName() + Extensions.XML;
        String metadataPath = FilePathBuilder.buildMetadataRecordName(rec.getName());
        String pathRevmanFrom = FilePathBuilder.getPathToEntireRevmanSrc(group);
        String pathRecordFrom = pathRevmanFrom + recPath;
        String pathMetadataFrom = pathRevmanFrom + metadataPath;

        try {
            String pathRevmanTo = null;
            if (rp.isFileExists(pathRecordFrom)) {

                pathRevmanTo = FilePathBuilder.getPathToPreviousRevmanSrc(version, group);
                String pathRecordTo = pathRevmanTo + recPath;
                if (rp.isFileExists(pathRecordTo)) {
                    rp.deleteFile(pathRecordTo);
                }
                rp.putFile(pathRecordTo, rp.getFile(pathRecordFrom), true);
            }
            if (rp.isFileExists(pathMetadataFrom)) {
                if (pathRevmanTo == null) {
                    pathRevmanTo = FilePathBuilder.getPathToPreviousRevmanSrc(version, group);
                }
                String pathMetadataTo = pathRevmanTo + metadataPath;
                if (rp.isFileExists(pathMetadataTo)) {
                    rp.deleteFile(pathMetadataTo);
                }
                rp.putFile(pathMetadataTo, rp.getFile(pathMetadataFrom), true);
            }

        } catch (Exception e) {

            LOG.error("Error during copy revman to previous", e);
            rec.setSuccessful(false, e.getMessage());
        }
    }

    public void handlePreviousVersions(BaseType baseType, IRecord record, boolean dashboard) {
        ICDSRMeta meta = resultsStorage.getMetadata(record.getId());
        record.setJats(meta.isJats());
        updatePrevious(record, meta, baseType.getId(), dashboard);
    }

    public Map<String, String> handlePreviousVersions(BaseType baseType, Collection<? extends IRecord> recs,
                                                      boolean dashboard) {
        LOG.debug("Handle previous versions started");
        Map<String, String> wrongRecs = new HashMap<>();
        for (IRecord rec : recs) {
            if (!rec.isCompleted() || !rec.isSuccessful()) {
                continue;
            }
            ICDSRMeta meta = resultsStorage.getMetadata(rec.getId());
            if (meta == null) {
                // just to support an old approach
                meta = resultsStorage.findLatestMetadata(rec.getName(), true);
            }

            if (meta == null) {
                addError(rec, "cannot find metadata", dashboard);
            } else {
                rec.setJats(meta.isJats());
                updatePrevious(rec, meta, baseType.getId(), dashboard);
            }
            if (!rec.isSuccessful()) {
                wrongRecs.put(rec.getName(), rec.getMessages());
                rec.setSuccessful(true, "");
            }
        }
        LOG.debug("Handle previous versions finished, failed records: " + wrongRecs.size());
        return wrongRecs;
    }

    private void updatePrevious(IRecord rec, ICDSRMeta meta, String dbName, boolean dashboard) {
        try {
            rec.setGroupSid(meta.getGroupSid());

            if (meta.getHistoryNumber() != null && meta.getHistoryNumber() == RecordEntity.VERSION_SHADOW) {
                ICDSRMeta prev = vm.populateMetadataVersion(meta);
                if (prev == null) {
                    return;
                }
                if (prev.getPubNumber() != meta.getPubNumber()) {
                    Integer oldVersion = prev.getHistoryNumber();
                    copyRevman(oldVersion, rec, prev.getGroupSid());
                    copyPrevious(rec, oldVersion, dbName);
                    rec.setHistoryNumber(oldVersion);
                }
                checkGroup(rec, prev.getGroupSid(), meta.getGroupSid());
            }

        } catch (Exception e) {
            LOG.error(e);
            addError(rec, e.getMessage(), dashboard);
        }
    }

    private void addError(IRecord rec, String msg, boolean dashboard) {
        rec.setSuccessful(false, msg);
        flowLogger.onProductPackageError(ILogEvent.PRODUCT_SAVED, null, rec.getName(), "Saving in DB: " + msg,
                true, dashboard, false);
    }

    private void checkGroup(IRecord rec, String oldGroup, String newGroup) {
        if (oldGroup != null && !oldGroup.equals(newGroup))  {
            deleteRevman(rec, oldGroup);
        }
    }

    private void copyPrevious(IRecord rec, Integer version, String dbName) throws Exception {
        String recName = rec.getName();

        RecordHelper.copyJatsToPrevious(dbName, version, recName, rp);

        String recordPath = FilePathBuilder.getPathToEntireSrcRecord(dbName, recName);
        copySrc(version, recName, recordPath);
        copyHtml(version, recName, recordPath, RND_HTML_DIR);

        RecordHelper.copyPdfsToPrevious(dbName, version, recName, rp);
        copyMl3gContent(dbName, version, rec);
        TranslatedAbstractsHelper.copyAbstractsToPrevious(recName, version, rp);
    }
}