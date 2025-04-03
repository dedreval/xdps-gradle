package com.wiley.cms.cochrane.cmanager.contentworker;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.naming.NamingException;

import com.wiley.cms.cochrane.activitylog.ActivityLogEntity;
import com.wiley.cms.cochrane.activitylog.ILogEvent;
import com.wiley.cms.cochrane.cmanager.CmsException;
import com.wiley.cms.cochrane.cmanager.CmsUtils;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.DeliveryPackage;
import com.wiley.cms.cochrane.cmanager.DeliveryPackageException;
import com.wiley.cms.cochrane.cmanager.DeliveryPackageInfo;
import com.wiley.cms.cochrane.cmanager.FilePathBuilder;
import com.wiley.cms.cochrane.cmanager.FilePathCreator;
import com.wiley.cms.cochrane.cmanager.IDeliveryFileStatus;
import com.wiley.cms.cochrane.cmanager.MessageSender;
import com.wiley.cms.cochrane.cmanager.Record;
import com.wiley.cms.cochrane.cmanager.data.ClDbEntity;
import com.wiley.cms.cochrane.cmanager.data.DeliveryFileEntity;
import com.wiley.cms.cochrane.cmanager.data.IssueEntity;
import com.wiley.cms.cochrane.cmanager.data.issue.IssueVO;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.process.ICMSProcessManager;
import com.wiley.cms.cochrane.process.IRecordCache;
import com.wiley.cms.cochrane.repository.IRepository;
import com.wiley.cms.converter.services.IRevmanLoader;
import com.wiley.tes.util.DbUtils;
import com.wiley.tes.util.Extensions;
import com.wiley.tes.util.FileUtils;
import com.wiley.tes.util.URIWrapper;
import com.wiley.tes.util.res.Property;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 06.06.12
 */
public class RevmanWorker extends IssueWorker  {

    private IRevmanLoader revmanLoader;

    @Override
    public void work(int deliveryFileId) {

        LOG.debug("worker loading started, packageId=" + deliveryFileId);

        DeliveryPackageInfo packageInfo = null;
        DeliveryFileEntity df =  null;
        String libName =  null;
        boolean qas = false;
        try {
            lookupServices();

            df = rs.getDeliveryFileEntity(deliveryFileId);
            if (df == null) {
                throw new CmsException("cannot find delivery file by id=" + deliveryFileId);
            }
            IssueEntity issue = df.getIssue();
            if (issue == null) {
                throw new CmsException("delivery file issue is not exist, id=" + deliveryFileId);
            }

            ClDbEntity clDb = df.getDb();
            if (clDb == null) {
                throw new CmsException("delivery file db is not exist, id=" + deliveryFileId);
            }

            libName = CochraneCMSPropertyNames.getCDSRDbName();

            if (df.getStatus().equalsById(IDeliveryFileStatus.STATUS_REVMAN_CONVERTED)) {
                // start uploading
                DeliveryPackageInfo delivery = collectPackage(df, libName);
                qas = startUpload(delivery, df.getName(), clDb.getId(), df.getType());
                packageInfo = delivery;

            } else if (df.getInterimStatus().equalsById(IDeliveryFileStatus.STATUS_REVMAN_CONVERTED)) {

                packageInfo = RevmanPackage.collectUnpackedData(rps, df, libName);

                if (packageInfo.hasRecordPaths()) {
                    // resume conversion
                    Map<String, String> notifyMessage = getNotifyMessage(df.getName());
                    MessageSender.sendMessage(MessageSender.MSG_TITLE_CONVERSION_STARTED, notifyMessage);
                    revmanLoader.loadRevmanPackage(packageInfo, df.getName(), deliveryFileId, new IssueVO(issue));
                } else {
                    // start uploading
                    LOG.warn("status is 'revman converting' but no revman records found, packageId=" + deliveryFileId);
                    DeliveryPackageInfo delivery = collectPackage(df, libName);
                    qas = startUpload(delivery, df.getName(), clDb.getId(), df.getType());
                    packageInfo = delivery;
                }
            } else {
                throw new CmsException(String.format(
                    "delivery file id=%d, status must be 'revman converted/converting' %d/%d", deliveryFileId,
                        IDeliveryFileStatus.STATUS_REVMAN_CONVERTED, IDeliveryFileStatus.STATUS_REVMAN_CONVERTING));
            }
        } catch (Exception e) {
            LOG.error(e.getMessage() + ", packageName " + getPackageName());
            if (df != null) {
                doOnWorkMethodException(e, df.getName(), packageInfo);
            }
            qas = false;
        } finally {
            LOG.debug("revman loading finished, packageName " + getPackageName());
        }
        if (qas) {
            qasRequest(packageInfo, df.getId(), df.getName(), libName, df.getIssue().getYear(),
                    df.getIssue().getNumber());
        }
    }

    @Override
    protected boolean check4NewFormats(BaseType bt, String dfName, int type, int issueId, int dbId)
            throws CmsException {
        boolean importIssue = CmsUtils.isImportIssue(issueId);
        boolean scheduledIssue = !importIssue && CmsUtils.isScheduledIssue(issueId);
        boolean jats = DeliveryFileEntity.isJats(type);
        if (scheduledIssue && (!DeliveryPackage.isAutReprocess(dfName) || !jats)) {
            throw CmsException.createForScheduledIssue(deliveryFileId);
        }
        if (jats) {
            if (importIssue && !Property.get("cms.cochrane.jats.import", "false").get().asBoolean()) {
                throw new CmsException(String.format(
                        "cannot upload delivery file [%d] to Issue for Import as it's disabled", deliveryFileId));
            }
            startUploadProcess(dfName, packageUri, bt.getId(), dbId, issueId,
                CmsUtils.getIssueNumber(pck.getYear(), pck.getIssueNumber()),
                    importIssue ? ICMSProcessManager.PROC_TYPE_IMPORT_JATS : ICMSProcessManager.PROC_TYPE_UPLOAD_CDSR);
            return true;
        }
        return false;
    }

    @Override
    public void work(final URI packageUri) {
        LOG.debug("worker loading started, packageUri: " + packageUri);
        DeliveryPackageInfo packageInfo = null;
        IssueEntity issue;
        boolean remove = true;
        try {
            Map<String, String> notifyMessage = getNotifyMessage(packageUri);
            MessageSender.sendMessage(MessageSender.MSG_TITLE_CONVERSION_STARTED, notifyMessage);

            lookupServices();

            String packageName = getPackageName();
            RevmanPackage rp = getRevmanPackage();

            logService.info(ActivityLogEntity.EntityLevel.FILE, ILogEvent.NEW_PACKAGE_RECEIVED, deliveryFileId,
                    packageName, theLogUser, null);

            issue = rs.findOpenIssueEntity(rp.getYear(), rp.getIssueNumber());
            int issueId = issue.getId();
            String libName = rp.getLibName();

            int dbId = rs.findOpenDb(issueId, libName);
            int type = rs.updateDeliveryFile(deliveryFileId, issueId, rp.getVendor(), dbId).getType();

            if (check4NewFormats(BaseType.getCDSR().get(), packageName, type, issueId, dbId)) {
                remove = false;
                return;
            }

            IRecordCache rCache = getRecordCache();
            IssueVO issueVO = new IssueVO(issue);
            if (rp.isAut()) {
                // to support old issues
                rCache.addLastCDSRDb(dbId, issueVO);
            }
            packageInfo = rp.extractData(issueId, dbId, DeliveryFileEntity.TYPE_DEFAULT, logService, rCache);
            MessageSender.sendMessage(MessageSender.MSG_TITLE_DATA_EXTRACTED, notifyMessage);
            logService.info(ActivityLogEntity.EntityLevel.FILE, ILogEvent.PACKAGE_UNZIPPED,
                    deliveryFileId, packageName, theLogUser, null);

            if ((packageInfo.hasRecordPaths() || packageInfo.hasTranslations() || !rp.hasTranslatedPackage())
                    && !rp.hasOnlyErrors()) {
                revmanLoader.loadRevmanPackage(packageInfo, packageName, deliveryFileId, issueVO,
                        rp.getIncludedNames());
            } else {
                rs.setDeliveryFileStatus(deliveryFileId, IDeliveryFileStatus.STATUS_UNZIPPED, false);
            }
            rs.setDeliveryFileStatus(deliveryFileId, IDeliveryFileStatus.STATUS_REVMAN_CONVERTING, true);
        } catch (Exception e) {
            LOG.error(e.getMessage() + ", packageName: " + getPackageName());
            doOnWorkMethodException(e, packageUri, packageInfo);
        } finally {
            onFinal(packageUri, remove);
            LOG.debug("worker loading finished, packageName: " + getPackageName());
        }
    }

    @Override
    public String getLibName() {
        return CochraneCMSPropertyNames.getCDSRDbName();
    }

    @Override
    protected void onFinal(URI packageUri) {
        String str = packageUri.toString();
        if (isFtpPackage(str)) {
            deletePackageOnFtp(new URIWrapper(packageUri), deliveryFileId);
        } else if (CochraneCMSPropertyNames.isArchieDownloadTestMode() && !isTestPackage(str)) {
            deletePackageOnRepository(packageUri, -1);
        }
    }

    @Override
    protected void doOnWorkMethodException(DeliveryPackageException e, String packageFileName,
                                           DeliveryPackageInfo packageInfo) {
        if (e.removeDeliveryPackage()) {
            DeliveryPackageInfo dfInfo = e.getDeliveryPackageInfoToRemove();
            int issueId = dfInfo.getIssueId();
            if (DbUtils.exists(issueId)) {
                cleanRevmanPackageFiles(issueId, dfInfo);
            }
            rs.deleteDeliveryFileEntity(deliveryFileId);
        } else {
            super.doOnWorkMethodException(e, packageFileName, packageInfo);
        }
    }

    private void cleanRevmanPackageFiles(int issueId, DeliveryPackageInfo dfInfo) {
        try {
            rps.deleteDir(FilePathBuilder.getPathToIssuePackage(issueId, dfInfo.getDbName(), dfInfo.getDfId()));
        } catch (Exception e) {
            LOG.error(e.getMessage());
        }
    }

    @Override
    protected boolean parsePackage(URI packageUri) {
        boolean ret = true;
        try {
            super.initPackage(new RevmanPackage(packageUri));
        } catch (DeliveryPackageException de) {
            ret = false;
            doOnWorkMethodException(de, packageUri, null);
            onFinal(packageUri);
        }
        return ret;
    }

    @Override
    protected void lookupServices() throws NamingException {
        super.lookupServices();
        revmanLoader = CochraneCMSPropertyNames.lookup("RevmanLoader", IRevmanLoader.class);
    }

    private DeliveryPackageInfo collectPackage(DeliveryFileEntity df, String libName) throws Exception {
        Set<String> skip = new HashSet<>();
        skip.add(RevmanPackage.REVMAN_FOLDER);
        return collectUnpackedData(rps, df, libName, skip);
    }

    private RevmanPackage getRevmanPackage() {
        return (RevmanPackage) pck;
    }

    private boolean startUpload(DeliveryPackageInfo packageInfo, String dfName, int clDbId, int type) throws Exception {

        if (packageInfo == null) {
            return false;
        }

        boolean qas = true;
        if (packageInfo.hasRecordPaths()) {

            Map<String, String> notifyMessage = getNotifyMessage(dfName);
            MessageSender.sendMessage(MessageSender.MSG_TITLE_LOAD_STARTED, notifyMessage);

            rs.setDeliveryFileStatus(deliveryFileId, IDeliveryFileStatus.STATUS_BEGIN, false);
            updateRepository(packageInfo, clDbId, dfName, deliveryFileId, type, true, true);
        } else {
            // finishing packet that maybe contains deleted records
            rs.setDeliveryFileStatus(deliveryFileId, IDeliveryFileStatus.STATUS_RND_FINISHED_SUCCESS, false);
            getUploadingService().finishUpload(packageInfo.getIssueId(), deliveryFileId, new ArrayList<Record>());
            qas = false;
        }

        return qas;
    }

    private static DeliveryPackageInfo collectUnpackedData(IRepository rp, DeliveryFileEntity df, String libName,
                                                           Set<String> skip) throws DeliveryPackageException {
        String dfId = String.valueOf(df.getId());
        LOG.debug("unpacked data are collecting, packageId=" + dfId);
        if (!DeliveryFileEntity.isRevman(df.getType())) {
            throw new DeliveryPackageException("Operation is supported only for RevMan", df.getStatus().getId());
        }
        DeliveryPackageInfo ret = new DeliveryPackageInfo(df.getIssue().getId(), libName, df.getId(), df.getName());
        int issueId = ret.getIssueId();
        String path = FilePathCreator.getFilePathForEnclosure(dfId, issueId, libName, null);
        File[] recs = rp.getFilesFromDir(path);
        if (recs == null) {
            return ret;
        }
        for (File rec: recs) {
            String fName = rec.getName();
            if (skip != null && skip.contains(fName)) {
                continue;
            }
            String recordName = fName;
            if (!rec.isDirectory()) {
                if (!fName.endsWith(Extensions.XML)) {
                    continue;
                }
                recordName = FileUtils.cutExtension(fName, Extensions.XML);
                path = FilePathCreator.getFilePathToSource(dfId, String.valueOf(issueId), libName, recordName);
                ret.addRecordPath(recordName, path);
                ret.addFile(recordName, path);
            } else {
                path = FilePathCreator.getFilePathForEnclosure(dfId, String.valueOf(issueId), libName, recordName, "");
                extractUnpackedRecord(recordName, ret, path, rec);
            }
        }
        return ret;
    }

    private static void extractUnpackedRecord(String recordName, DeliveryPackageInfo packageInfo, String rootPath,
                                              File recordDir) {
        File[] dirs = recordDir.listFiles();
        for (File dir : dirs) {
            if (!dir.isDirectory()) {
                continue;
            }
            String path = rootPath + dir.getName();
            File[] recs = dir.listFiles();
            for (File rec : recs) {
                if (!rec.isDirectory()) {
                    packageInfo.addFile(recordName, path + FilePathCreator.SEPARATOR + rec.getName());
                } else {
                    LOG.error("Unexpected directory: " + rec.getAbsolutePath());
                }
            }
        }
    }
}
