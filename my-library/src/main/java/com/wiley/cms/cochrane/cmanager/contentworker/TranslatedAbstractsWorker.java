package com.wiley.cms.cochrane.cmanager.contentworker;

import java.io.File;
import java.net.URI;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Map;

import com.wiley.cms.cochrane.activitylog.ActivityLogEntity;
import com.wiley.cms.cochrane.activitylog.ILogEvent;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.DeliveryPackageException;
import com.wiley.cms.cochrane.cmanager.DeliveryPackageInfo;
import com.wiley.cms.cochrane.cmanager.IDeliveryFileStatus;
import com.wiley.cms.cochrane.cmanager.MessageSender;
import com.wiley.cms.cochrane.cmanager.data.IssueEntity;
import com.wiley.cms.cochrane.cmanager.packagegenerator.TranslatedAbstractsPackageGenerator;
import com.wiley.cms.cochrane.services.IContentManager;
import com.wiley.tes.util.URIWrapper;
import org.apache.commons.lang.StringUtils;

/**
 * @author Sergey Trofimov
 */
@Deprecated
public class TranslatedAbstractsWorker extends PackageWorker<TranslatedAbstractsPackage> {

    private static final int PUBLISH_DELAY = -7;

    private IssueEntity lastIssue;
    private Calendar calendar = new GregorianCalendar();

    @Override
    public void work(URI packageUri) {
        LOG.debug("loading started, packageUri: " + packageUri);

        DeliveryPackageInfo packageInfo = null;
        try {
            Map<String, String> notifyMessage = getNotifyMessage(packageUri);

            lookupServices();

            MessageSender.sendMessage(MessageSender.MSG_TITLE_LOAD_STARTED, notifyMessage);

            String packageFileName = getPackageName();

            lastIssue = rs.getLastNonPublishedIssue();
            if (!isIssueNonPublished(lastIssue)) {
                extractFailedMessageDestination = null;
                throw new Exception("The package with translated abstracts will not be loaded to the current issue "
                    + "due to Publish Date has passed. Create next issue and repeat loading");
            }

            logService.info(ActivityLogEntity.EntityLevel.FILE, ILogEvent.NEW_PACKAGE_RECEIVED,
                    deliveryFileId, packageFileName, theLogUser, null);
            String libName = CochraneCMSPropertyNames.getCDSRDbName();
            int dbId = rs.findOpenDb(lastIssue.getId(), libName);
            rs.updateDeliveryFile(deliveryFileId, lastIssue.getId(), "", dbId);

            packageInfo = new DeliveryPackageInfo(lastIssue.getId(), getLibName(), dbId, deliveryFileId,
                    packageFileName);
            pck.extractData(packageInfo, logService, getRecordCache());
            extractFailedMessageDestination = null;
            MessageSender.sendMessage(MessageSender.MSG_TITLE_DATA_EXTRACTED, notifyMessage);

            logService.info(ActivityLogEntity.EntityLevel.FILE, ILogEvent.PACKAGE_UNZIPPED,
                    deliveryFileId, packageFileName, theLogUser, null);
            rs.setDeliveryFileStatus(deliveryFileId, IDeliveryFileStatus.STATUS_UNZIPPED, false);

            if (packageInfo.getRecordNames().isEmpty()) {
                String dbName = StringUtils.isNotBlank(packageInfo.getDbName()) ? packageInfo.getDbName() : "N/A";
                MessageSender.sendFailedLoadPackageMessage(packageFileName,
                        "The package with valid translated abstracts is empty.", dbName, null);
            } else {

                rs.setDeliveryFileStatus(deliveryFileId, IDeliveryFileStatus.STATUS_UNZIPPED, false);
                //updateRepository(packageInfo, packageFileName);
                File packet = new TranslatedAbstractsPackageGenerator().generateFile(pck.getRecordsToGenerate(),
                    lastIssue.getId(), pck.isAut() ? TranslatedAbstractsPackage.AUT_PACKAGE_NAME_PREFIX
                        : TranslatedAbstractsPackage.PACKAGE_NAME_PREFIX, pck.getResponse());

                IContentManager manager = CochraneCMSPropertyNames.lookup("ContentManager", IContentManager.class);
                manager.newPackageReceived(packet.toURI());
            }

        } catch (Exception e) {
            LOG.error(e.getMessage() + ", packageName: " + getPackageName());
            pck.clear(true);
            doOnWorkMethodException(e, packageUri, packageInfo);
        } finally {
            pck.clear(false);
            if (isFtpPackage(packageUri.toString())) {
                deletePackageOnFtp(new URIWrapper(packageUri), deliveryFileId);
            }
            LOG.debug("loading finished, packageName: " + getPackageName());
        }
    }

    @Override
    protected boolean parsePackage(URI packageUri) {
        boolean ret = true;
        try {
            super.initPackage(new TranslatedAbstractsPackage(packageUri));
        } catch (DeliveryPackageException de) {
            ret = false;
            doOnWorkMethodException(de, packageUri, null);
            if (isFtpPackage(packageUri.toString())) {
                deletePackageOnFtp(new URIWrapper(packageUri), -1);
            }
        }
        return ret;
    }

    private boolean isIssueNonPublished(IssueEntity issue) {
        calendar.setTime(new Date());
        calendar.add(Calendar.DAY_OF_YEAR, PUBLISH_DELAY);
        return issue.getPublishDate().after(calendar.getTime());

    }
}