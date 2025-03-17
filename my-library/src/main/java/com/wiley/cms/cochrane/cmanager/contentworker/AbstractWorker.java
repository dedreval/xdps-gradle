package com.wiley.cms.cochrane.cmanager.contentworker;

import java.io.File;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import javax.naming.NamingException;

import com.wiley.cms.cochrane.activitylog.ActivityLogEntity;
import com.wiley.cms.cochrane.activitylog.IActivityLogService;
import com.wiley.cms.cochrane.activitylog.ILogEvent;
import com.wiley.cms.cochrane.cmanager.CmsUtils;
import com.wiley.cms.cochrane.cmanager.CochraneCMSProperties;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.ContentManager;
import com.wiley.cms.cochrane.cmanager.DeliveryPackageException;
import com.wiley.cms.cochrane.cmanager.DeliveryPackageInfo;
import com.wiley.cms.cochrane.cmanager.IDeliveryFileStatus;
import com.wiley.cms.cochrane.cmanager.MessageSender;
import com.wiley.cms.cochrane.cmanager.data.IResultsStorage;
import com.wiley.cms.cochrane.cmanager.entitymanager.AbstractManager;
import com.wiley.cms.cochrane.cmanager.entitywrapper.ResultStorageFactory;
import com.wiley.cms.cochrane.repository.IRepository;
import com.wiley.cms.cochrane.repository.RepositoryFactory;
import com.wiley.tes.util.Logger;
import com.wiley.tes.util.URIWrapper;
import com.wiley.tes.util.ftp.FTPConnection;

/**
 * @author Sergey Trofimov
 */
public abstract class AbstractWorker implements Runnable {
    protected static final Logger LOG = Logger.getLogger(ContentManager.class);
    protected String theLogUser = CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.ACTIVITY_LOG_SYSTEM_NAME);
    protected String extractFailedMessageDestination = "data_extraction_failed";
    protected URI packageUri;
    protected IActivityLogService logService;
    protected IResultsStorage rs;
    protected int deliveryFileId = -1;

    protected IRepository rps = RepositoryFactory.getRepository();

    public abstract void work(final URI packageUri);

    public abstract String getLibName();

    protected abstract boolean parsePackage(URI packageUri);

    public void run() {

        if (hasPackageUri()) {
            work(packageUri);
        } else {
            work(deliveryFileId);
        }
    }

    public void work(int deliveryFileId) {
    }

    public URI getPackageUri() {
        return packageUri;
    }

    public boolean setPackageUri(URI packageUri) {
        this.packageUri = packageUri;
        return parsePackage(packageUri);
    }

    public void setPackageId(int packageId) {
        deliveryFileId = packageId;
    }

    protected boolean hasPackageUri() {
        return packageUri != null;
    }

    protected void deletePackageOnFtp(URIWrapper uri, int deliveryFileId) {
        if (FTPConnection.deletePackageOnFtp(uri) && deliveryFileId != -1) {
            rs.setDeliveryFileStatus(deliveryFileId, IDeliveryFileStatus.STATUS_PACKAGE_DELETED, true);
        }
    }

    protected void deletePackageOnRepository(URI uri, int deliveryFileId) {
        File file = new File(uri);
        if (file.exists() && file.isFile()) {
            if (file.delete() && deliveryFileId != -1) {
                rs.setDeliveryFileStatus(deliveryFileId, IDeliveryFileStatus.STATUS_PACKAGE_DELETED, true);
            }
        }
    }

    protected void lookupServices() throws NamingException {

        logService = AbstractManager.getActivityLogService();
        rs = ResultStorageFactory.getFactory().getInstance();
    }

    protected Map<String, String> getNotifyMessage(URI packageUri) {

        String path = packageUri.getPath();
        String packageFileName = path.substring(path.lastIndexOf("/") + 1);
        return getNotifyMessage(packageFileName);
    }

    protected Map<String, String> getNotifyMessage(String packageFileName) {

        Map<String, String> notifyMessage = new HashMap<>();
        notifyMessage.put("deliveryFileName", packageFileName);
        return notifyMessage;
    }

    protected void doOnWorkMethodException(Exception e, URI packageUri, DeliveryPackageInfo packageInfo) {

        String path = packageUri.getPath();
        String packageFileName = path.substring(path.lastIndexOf("/") + 1);
        doOnWorkMethodException(e, packageFileName, packageInfo);
    }

    protected void doOnWorkMethodException(Exception e, String packageFileName, DeliveryPackageInfo packageInfo) {
        if (e instanceof DeliveryPackageException) {
            doOnWorkMethodException((DeliveryPackageException) e, packageFileName, packageInfo);

        } else {
            cleanRepository(packageInfo);
            if (deliveryFileId != -1) {
                rs.setDeliveryFileStatus(deliveryFileId, IDeliveryFileStatus.STATUS_PICKUP_FAILED, false);
            }
        }
    }

    protected void doOnWorkMethodException(DeliveryPackageException e, String packageFileName,
                                           DeliveryPackageInfo packageInfo) {
        if (!e.isSilently())  {
            logWorkMethodException(e, packageFileName, packageInfo.getDbName());
        }
        cleanRepository(packageInfo);
        int status = e.getStatus();
        if (deliveryFileId != -1) {
            rs.setDeliveryFileStatus(deliveryFileId, status, false);
        }
    }

    protected void logWorkMethodException(Exception e, String packageFileName, String dbName) {
        ContentManager.LOG.error(e, e);
        Map<String, String> map = getNotifyMessage(packageFileName);
        map.put(MessageSender.MSG_PARAM_DATABASE, CmsUtils.getOrDefault(dbName));
        map.put(MessageSender.MSG_PARAM_ERROR, e.getMessage());
        String identifiers = MessageSender.getCDnumbersFromMessageByPattern(e.getMessage(), map);
        map.put(MessageSender.MSG_PARAM_RECORD_ID, identifiers);
        if (extractFailedMessageDestination != null) {
            MessageSender.sendMessage(extractFailedMessageDestination, map);
        }
        MessageSender.sendMessage("loading_failed", map);
        if (deliveryFileId != -1 && logService != null) {
            logService.error(ActivityLogEntity.EntityLevel.FILE, ILogEvent.DATA_EXTRACTION_FAILED,
                    deliveryFileId, packageFileName, theLogUser, e.getMessage());
        }
    }

    protected void cleanRepository(DeliveryPackageInfo packageInfo) {

        if (packageInfo != null) {
            packageInfo.cleanInRepository(rps);
        }
    }
}
