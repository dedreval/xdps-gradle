package com.wiley.cms.cochrane.cmanager.contentworker;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;

import javax.naming.NamingException;

import com.wiley.cms.cochrane.activitylog.ActivityLogEntity;
import com.wiley.cms.cochrane.activitylog.IActivityLogService;
import com.wiley.cms.cochrane.activitylog.ILogEvent;
import com.wiley.cms.cochrane.cmanager.CmsException;
import com.wiley.cms.cochrane.cmanager.CmsUtils;
import com.wiley.cms.cochrane.cmanager.CochraneCMSBeans;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.DeliveryPackage;
import com.wiley.cms.cochrane.cmanager.DeliveryPackageException;
import com.wiley.cms.cochrane.cmanager.DeliveryPackageInfo;
import com.wiley.cms.cochrane.cmanager.IDeliveringService;
import com.wiley.cms.cochrane.cmanager.IDeliveryFileStatus;
import com.wiley.cms.cochrane.cmanager.MessageSender;
import com.wiley.cms.cochrane.cmanager.data.DeliveryFileEntity;
import com.wiley.cms.cochrane.cmanager.data.IResultsStorage;
import com.wiley.cms.cochrane.cmanager.data.db.DbStorageFactory;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.meshtermmanager.IMeshtermUpdater;
import com.wiley.cms.cochrane.meshtermmanager.MeshtermUpdater;
import com.wiley.cms.cochrane.process.ICMSProcessManager;
import com.wiley.cms.cochrane.process.IQaManager;
import com.wiley.cms.cochrane.test.Hooks;
import com.wiley.cms.cochrane.test.PackageChecker;
import com.wiley.tes.util.Logger;

/**
 * @author Sergey Trofimov
 */
public class IssueWorker extends PackageWorker<DeliveryPackage> implements Runnable {
    public static final Logger LOG = Logger.getLogger(IssueWorker.class);

    public void work(URI packageUri) {
        LOG.debug("loading started, packageUri: " + packageUri);

        DeliveryPackageInfo packageInfo = null;
        boolean removeOnFTP = true;
        boolean isCDSR;
        BaseType bt = null;
        try {
            Map<String, String> notifyMessage = getNotifyMessage(packageUri);

            lookupServices();

            MessageSender.sendMessage(MessageSender.MSG_TITLE_LOAD_STARTED, notifyMessage);

            String packageFileName = getPackageName();

            logService.info(ActivityLogEntity.EntityLevel.FILE, ILogEvent.NEW_PACKAGE_RECEIVED,
                    deliveryFileId, packageFileName, theLogUser, null);

            int issueId = rs.findOpenIssue(pck.getYear(), pck.getIssueNumber());
            String libName = pck.getLibName();
            bt = BaseType.find(libName).get();
            int dbId = rs.findOpenDb(issueId, libName);
            int type = rs.updateDeliveryFile(deliveryFileId, issueId, pck.getVendor(), dbId).getType();

            if (check4NewFormats(bt, packageFileName, type, issueId, dbId)) {
                removeOnFTP = false;
                return;
            }
            packageInfo = bt.isCDSR() ? pck.extractData(issueId, dbId, type, getRecordCache())
                    : pck.extractData(issueId);

            MessageSender.sendMessage(MessageSender.MSG_TITLE_DATA_EXTRACTED, notifyMessage);

            logService.info(ActivityLogEntity.EntityLevel.FILE, ILogEvent.PACKAGE_UNZIPPED,
                    deliveryFileId, packageFileName, theLogUser, null);

            updateRepository(packageInfo, dbId, packageFileName, deliveryFileId, type, bt.isCDSR(), false);

        } catch (Exception e) {
            if (bt != null && bt.isCentral()) {
                CochraneCMSBeans.getFlowLogger().onPackageFlowEventError(ILogEvent.PRODUCT_RECEIVED, bt,
                    pck.getPackageFileName(), deliveryFileId, PackageChecker.METAXIS, null, e.getMessage(), null, null);
            }
            LOG.error(e.getMessage() + ", packageName: " + getPackageName());
            doOnWorkMethodException(e, packageUri, packageInfo);
            return;

        } finally {
            onFinal(packageUri, removeOnFTP);
            LOG.debug("loading finished, packageName: " + getPackageName());
        }

        qasRequest(packageInfo, pck.getPackageId(), pck.getPackageFileName(), pck.getLibName(), pck.getYear(),
                   pck.getIssueNumber());
    }

    @Override
    protected boolean check4NewFormats(BaseType bt, String dfName, int type, int issueId, int dbId)
            throws CmsException {
        super.check4NewFormats(bt, dfName, type, issueId, dbId);
        if ((DeliveryFileEntity.isWml3g(type) && (bt.isCDSR() || bt.isEditorial()))
                || (bt.isCentral() && !bt.canHtmlConvert())) {
            startUploadProcess(dfName, packageUri, bt.getId(), dbId, issueId, CmsUtils.getIssueNumber(
                pck.getYear(), pck.getIssueNumber()), (bt.isCDSR() || DeliveryPackage.isPropertyUpdate(dfName))
                    ? defineCDSRMl3gPackageUploadProcess(bt, dfName) : bt.getProductType().getProcessTypeOnUpload());
            return true;
        }
        return false;
    }

    private static int defineCDSRMl3gPackageUploadProcess(BaseType bt, String dfName) {
        if (!bt.isCDSR() && !bt.isEditorial()) {
            return bt.getProductType().getProcessTypeOnUpload();
        }
        return DeliveryPackage.isPropertyUpdateMl3g(dfName) ? ICMSProcessManager.PROC_TYPE_UPLOAD_CDSR_MLG
                : ICMSProcessManager.PROC_TYPE_UPLOAD_CDSR_MESH;
    }

    static boolean isTestPackage(String packageUri) {
        return packageUri.contains("test-data");
    }

    @Override
    protected boolean parsePackage(URI packageUri)  {
        boolean ret = true;
        try {
            initPackage(new DeliveryPackage(packageUri));
        } catch (DeliveryPackageException de) {
            ret = false;
            doOnWorkMethodException(de, packageUri, null);
            onFinal(packageUri);
        }
        return ret;
    }

    void qasRequest(DeliveryPackageInfo packageInfo, int pckId, String pckName, String libName,
        int issueYear, int issueNumber) {

        if (packageInfo.isEmpty()) {

            rs.setDeliveryFileStatus(deliveryFileId, IDeliveryFileStatus.STATUS_RND_SOME_FAILED, false);
            try {
                getUploadingService().finishUpload(packageInfo.getIssueId(), pckId, new ArrayList<>());
            } catch (NamingException ne) {
                LOG.error(ne);
            }
            return;
        }

        if (libName.equals(CochraneCMSPropertyNames.getCDSRDbName())
                && !updateMeshterms(packageInfo, pckId, pckName, issueYear, issueNumber)) {
            return;
        }

        qasRequest(packageInfo, pckId, pckName, libName);
    }

    static IDeliveringService getUploadingService() throws NamingException {
        return CochraneCMSPropertyNames.lookup("DeliveringService", IDeliveringService.class);
    }

    public static IQaManager getQaManager() throws NamingException {
        return CochraneCMSPropertyNames.lookup("QaManager", IQaManager.class);
    }

    private boolean updateMeshterms(DeliveryPackageInfo packageInfo, int pckId, String pckName,
        int issueYear, int issueNumber) {
        try {
            IMeshtermUpdater meshtermUpdater = MeshtermUpdater.Factory.getFactory().getInstance();
            rs.setDeliveryFileStatus(pckId, IDeliveryFileStatus.STATUS_MESHTERM_UPDATING_STARTED, true);
            if (packageInfo.getRecordNames().size() < 1) {
                throw new Exception(
                        "Nothing to do for Meshterm Updater. No records in the package or all are hanging out.");
            }
            Map<String, String> sourcePaths = packageInfo.getRecordPaths();
            meshtermUpdater.updateMeshterms(sourcePaths,
                    Integer.parseInt(String.format("%d%02d", issueYear, issueNumber)), pckId, pckName);
            rs.setDeliveryFileStatus(pckId, IDeliveryFileStatus.STATUS_MESHTERM_UPDATING_ACCEPTED, true);

        } catch (Exception e) {
            LOG.error(e);
            logService.error(ActivityLogEntity.EntityLevel.FILE, ILogEvent.MESHTERM_UPDATER_FAILED, pckId, pckName,
                    theLogUser, e.getMessage());
            rs.setDeliveryFileStatus(pckId, IDeliveryFileStatus.STATUS_MESHTERM_UPDATING_FAILED, false);
            return false;
        }
        return true;
    }

    void updateRepository(DeliveryPackageInfo packageInfo, int dbId, String dfName,
        int dfId, int dfType, boolean isCDSR, boolean upload) {

        packageInfo.setDbId(dbId);
        SortedMap<String, List<String>> recs = packageInfo.getRecords();
        SortedMap<String, String> recPaths = packageInfo.getRecordPaths();
        if (!CochraneCMSPropertyNames.getCentralDbName().equals(packageInfo.getDbName())) {
            Hooks.captureRecords(dbId, recs.keySet(), Hooks.UPLOAD_START);
        }
        if (isCDSR) {
            if (!upload) {
                rs.createRecords(recPaths, dfId, packageInfo.getRecordsWithRawData(), recs.keySet(), null,
                        DeliveryPackage.isTranslatedAbstract(dfType));
            }
        } else {
            rs.createRecords(recPaths, dbId, dfId, packageInfo.getRecordsWithRawData());
        }
        updateDeliveryFileState(dbId, dfId, dfName, packageInfo.getRecords().size(), theLogUser, logService, rs);
    }

    private static void updateDeliveryFileState(int dbId, int dfId, String dfName, int recordCount, String user,
                                                IActivityLogService logService, IResultsStorage rs) {
        DbStorageFactory.getFactory().getInstance().updateAllRecordCount(dbId);
        LOG.debug(String.format("%s [%d] amount of records: %d", dfName, dfId, recordCount));
        logService.info(ActivityLogEntity.EntityLevel.FILE, ILogEvent.RECORDS_CREATED,
                dfId, dfName, user, recordCount + " records");

        rs.setDeliveryFileStatus(dfId, IDeliveryFileStatus.STATUS_RECORDS_CREATED, true);
    }

    private static void qasRequest(DeliveryPackageInfo manifest, int pckId, String pckName, String libName) {
        try {
            IQaManager manager = getQaManager();
            manager.startQa(manifest, pckId, pckName, libName);

        } catch (Exception e) {
            LOG.error(e);
        }
    }
}
