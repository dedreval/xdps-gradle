package com.wiley.cms.cochrane.cmanager.contentworker;

import com.wiley.cms.cochrane.activitylog.ActivityLogEntity;
import com.wiley.cms.cochrane.activitylog.ILogEvent;
import com.wiley.cms.cochrane.cmanager.CmsUtils;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.DeliveryPackageException;
import com.wiley.cms.cochrane.cmanager.MessageSender;
import com.wiley.cms.cochrane.process.ICMSProcessManager;
import com.wiley.tes.util.URIWrapper;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

/**
 * @author <a href='mailto:atolkachev@wiley.ru'>Andrey Tolkachev</a>
 * Date: 11.12.20
 */
public class AriesWorker extends IssueWorker {

    public AriesWorker() throws URISyntaxException {
        super();
    }

    @Override
    public void work(final URI packageUri) {
        LOG.debug("aries worker loading started, packageUri: " + packageUri);
        boolean removeOnFTP = true;
        try {
            Map<String, String> notifyMessage = getNotifyMessage(packageUri);
            MessageSender.sendMessage(MessageSender.MSG_TITLE_LOAD_STARTED, notifyMessage);

            lookupServices();

            String packageName = getPackageName();
            AriesArchiePackage ap = (AriesArchiePackage) pck;

            logService.info(ActivityLogEntity.EntityLevel.FILE, ILogEvent.NEW_PACKAGE_RECEIVED, deliveryFileId,
                            packageName, theLogUser, null);

            int issueId = rs.findOpenIssueEntity(ap.getYear(), ap.getIssueNumber()).getId();
            String libName = ap.getLibName();

            int dbId = rs.findOpenDb(issueId, libName);
            int type = rs.updateDeliveryFile(deliveryFileId, issueId, ap.getVendor(), dbId).getType();

            //if (DeliveryFileEntity.isAries(type)) {
            startUploadProcess(packageName, packageUri, libName, dbId, issueId,
                CmsUtils.getIssueNumber(ap.getYear(), ap.getIssueNumber()), ICMSProcessManager.PROC_TYPE_UPLOAD_CDSR);
            removeOnFTP = false;
            //}
        } catch (Exception e) {
            LOG.error(e.getMessage() + ", packageName: " + getPackageName());
            doOnWorkMethodException(e, packageUri, null);

        } finally {
            onFinal(packageUri, removeOnFTP);
            LOG.debug("aries worker loading finished, packageName: " + getPackageName());
        }
    }

    @Override
    protected boolean parsePackage(URI packageUri) {
        boolean ret = true;
        try {
            initPackage(new AriesArchiePackage(packageUri));
        } catch (DeliveryPackageException de) {
            ret = false;
            doOnWorkMethodException(de, packageUri, null);
            onFinal(packageUri);
        }
        return ret;
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
}
