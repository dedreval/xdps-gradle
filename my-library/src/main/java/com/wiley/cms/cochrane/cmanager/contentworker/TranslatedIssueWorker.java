package com.wiley.cms.cochrane.cmanager.contentworker;

import java.io.File;
import java.net.URI;
import java.util.Map;

import com.wiley.cms.cochrane.activitylog.ActivityLogEntity;
import com.wiley.cms.cochrane.activitylog.ILogEvent;
import com.wiley.cms.cochrane.cmanager.CmsException;
import com.wiley.cms.cochrane.cmanager.DeliveryPackage;
import com.wiley.cms.cochrane.cmanager.DeliveryPackageInfo;
import com.wiley.cms.cochrane.cmanager.FilePathBuilder;
import com.wiley.cms.cochrane.cmanager.MessageSender;
import com.wiley.cms.cochrane.cmanager.data.ClDbEntity;
import com.wiley.cms.cochrane.cmanager.data.DeliveryFileEntity;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.tes.util.URIWrapper;

/**
 * @author Sergey Trofimov
 */
@Deprecated
public class TranslatedIssueWorker extends IssueWorker {
    //public TranslatedIssueWorker() throws URISyntaxException {
    //    callbackURI = new URI(CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.CMS_SERVICE_URL)
    //                    + "AcceptQaTranslatedIssueResults?wsdl");
    //}

    @Override
    public void work(int deliveryFileId) {

        LOG.debug("revman translated package loading started, packageId=" + deliveryFileId);

        DeliveryPackageInfo packageInfo = null;
        DeliveryFileEntity df;
        try {
            lookupServices();

            df = rs.getDeliveryFileEntity(deliveryFileId);
            if (df == null) {
                throw new CmsException("cannot find delivery file by id=" + deliveryFileId);
            }
            String packageFileName = df.getName();
            Map<String, String> notifyMessage = getNotifyMessage(packageFileName);
            MessageSender.sendMessage(MessageSender.MSG_TITLE_LOAD_STARTED, notifyMessage);

            logService.info(ActivityLogEntity.EntityLevel.FILE, ILogEvent.NEW_PACKAGE_RECEIVED,
                deliveryFileId, packageFileName, theLogUser, null);

            ClDbEntity clDbEntity = df.getDb();
            int issueId = clDbEntity.getIssue().getId();
            int dbId = clDbEntity.getId();

            packageUri = new File(rps.getRealFilePath(FilePathBuilder.getPathToIssueDb(
                    issueId, BaseType.getCDSR().get().getId()) + packageFileName)).toURI();
            initPackage(new DeliveryPackage(packageUri, deliveryFileId, packageFileName));
            packageInfo = pck.extractData(issueId, dbId, DeliveryFileEntity.TYPE_TA, getRecordCache());

            MessageSender.sendMessage(MessageSender.MSG_TITLE_DATA_EXTRACTED, notifyMessage);

            logService.info(ActivityLogEntity.EntityLevel.FILE, ILogEvent.PACKAGE_UNZIPPED,
                deliveryFileId, packageFileName, theLogUser, null);

            updateRepository(packageInfo, dbId, packageFileName, deliveryFileId, DeliveryFileEntity.TYPE_TA,
                    true, false);

        } catch (Exception e) {
            LOG.error(e.getMessage() + ", packageName: " + getPackageName());
            doOnWorkMethodException(e, packageUri, packageInfo);
            return;

        } finally {
            onFinal(packageUri);
            LOG.debug("revman translated package loading finished, packageName: " + getPackageName());
        }

        qasRequest(packageInfo, pck.getPackageId(), pck.getPackageFileName(), pck.getLibName(), pck.getYear(),
            pck.getIssueNumber());
    }

    @Override
    protected void onFinal(URI packageUri) {
        String str = packageUri.toString();
        if (isFtpPackage(str)) {
            deletePackageOnFtp(new URIWrapper(packageUri), deliveryFileId);
        } else if (!isTestPackage(str)) {
            deletePackageOnRepository(packageUri, -1);
        }
    }
}
