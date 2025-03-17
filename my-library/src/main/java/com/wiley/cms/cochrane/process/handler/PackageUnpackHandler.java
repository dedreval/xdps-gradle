package com.wiley.cms.cochrane.process.handler;

import java.io.File;
import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.wiley.cms.cochrane.activitylog.ActivityLogFactory;
import com.wiley.cms.cochrane.activitylog.IActivityLog;
import com.wiley.cms.cochrane.activitylog.IFlowLogger;
import com.wiley.cms.cochrane.activitylog.ILogEvent;
import com.wiley.cms.cochrane.cmanager.CmsUtils;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.DeliveryPackage;
import com.wiley.cms.cochrane.cmanager.DeliveryPackageException;
import com.wiley.cms.cochrane.cmanager.DeliveryPackageInfo;
import com.wiley.cms.cochrane.cmanager.IDeliveryFileStatus;
import com.wiley.cms.cochrane.cmanager.Record;
import com.wiley.cms.cochrane.cmanager.contentworker.AriesMl3gPackage;
import com.wiley.cms.cochrane.cmanager.data.DeliveryFileEntity;
import com.wiley.cms.cochrane.cmanager.data.IResultsStorage;
import com.wiley.cms.cochrane.cmanager.data.record.IRecord;
import com.wiley.cms.cochrane.cmanager.entitywrapper.ResultStorageFactory;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.cmanager.res.PackageType;
import com.wiley.cms.cochrane.process.BaseAcceptQueue;
import com.wiley.cms.cochrane.process.CMSProcessManager;
import com.wiley.cms.cochrane.test.PackageChecker;
import com.wiley.cms.process.ProcessException;
import com.wiley.cms.process.ProcessManager;
import com.wiley.cms.process.ProcessPartVO;
import com.wiley.cms.process.ProcessVO;
import com.wiley.cms.cochrane.test.Hooks;
import com.wiley.tes.util.URIWrapper;
import com.wiley.tes.util.ftp.FTPConnection;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 7/7/2019
 *
 */
public class PackageUnpackHandler extends ContentHandler<DbHandler, BaseAcceptQueue, Map<String, IRecord>> {
    private static final long serialVersionUID = 1L;

    protected String packageName;
    private String packageUri;

    public PackageUnpackHandler() {
    }

    public PackageUnpackHandler(DbHandler handler, String packageUri) {
        super(handler);
        setPackageUri(packageUri);
    }

    @Override
    protected Class<DbHandler> getTClass() {
        return DbHandler.class;
    }

    @Override
    public void onExternalMessage(ProcessVO pvo, BaseAcceptQueue queue) throws ProcessException {
        pvo.setOutput(unpack(packageUri, pvo, queue.getFlowLogger()));
    }

    @Override
    public void onMessage(ProcessVO pvo) throws Exception  {
        pvo.setOutput(unpack(packageUri, pvo, CochraneCMSPropertyNames.lookupFlowLogger()));
    }

    @Override
    public void passResult(ProcessVO pvo, IContentResultAcceptor to) {
        to.acceptResult(this, pvo);
    }

    @Override
    protected void onStartSync(ProcessVO pvo, List<ProcessPartVO> inputData, CMSProcessManager manager)
            throws ProcessException {

        super.onStartSync(pvo, inputData, manager);
        pvo.setOutput(unpack(packageUri, pvo, manager.getFlowLogger()));
    }

    @Override
    protected void validate(ProcessVO pvo) throws ProcessException {
        if (packageUri == null) {
            throw new ProcessException("no package URI to unpack", pvo.getId());
        }
    }

    @Override
    protected void onEnd(ProcessVO pvo, CMSProcessManager manager) {
        if (packageUri == null) {
            return;
        }
        try {
            if (isFtpPackage(packageUri)
                    && FTPConnection.deletePackageOnFtp(new URIWrapper(new URI(packageUri)))) {
                setDeliveryFileStatus(IDeliveryFileStatus.STATUS_PACKAGE_DELETED, true);
                packageUri = null;

            } else if (DeliveryPackage.isPropertyUpdate(packageName)) {
                File file = new File(packageUri);
                if (file.exists() && file.isFile()) {
                    file.delete();
                }
                packageUri = null;
            }
        } catch (Exception e) {
            LOG.warn(e.getMessage());
        }
    }

    @Override
    public void logOnEnd(ProcessVO pvo, ProcessManager manager) {
        super.logOnEnd(pvo, manager);
        log4Package(packageName, "unpacked.");
    }

    @Override
    public void logOnFail(ProcessVO pvo, String msg, ProcessManager manager) {
        super.logOnFail(pvo, msg, manager);

        setDeliveryFileStatus(IDeliveryFileStatus.STATUS_PICKUP_FAILED, false);
        if (packageName != null) {
            log4PackageFail(packageName, msg);
            ActivityLogFactory.getFactory().getInstance().logDeliveryFileError(ILogEvent.DATA_EXTRACTION_FAILED,
                    getContentHandler().getDfId(), packageName, pvo.getOwner(), msg);
            packageName = null;
        }
    }

    @Override
    public void logOnStart(ProcessVO pvo, ProcessManager manager) {
        super.logOnStart(pvo, manager);
        log4Package(packageName, "unpacking ...");
        setDeliveryFileStatus(IDeliveryFileStatus.STATUS_PICKED_UP, true);
    }

    public final void setPackageUri(String packageUri) {
        this.packageUri = packageUri;
    }

    private Map<String, IRecord> unpack(String packagePath, ProcessVO pvo, IFlowLogger logger) throws ProcessException {
        String owner = pvo.getOwner();
        try {
            URI uri = new URI(packagePath);
            Integer issueId = getContentHandler().getIssueId();
            int dfId = getContentHandler().getDfId();
            BaseType bt = BaseType.find(getContentHandler().getDbName()).get();

            Map<String, IRecord> ret = DeliveryPackage.isMl3g(packagePath) || bt.isCentral()
                ? unpackCommon(bt, uri, issueId, dfId, owner, logger) : (DeliveryPackage.isAries(packagePath)
                    ? unpackAries(bt, uri, issueId, dfId, owner, logger) : (DeliveryPackage.isJats(packagePath)
                        ? unpackJats(bt, uri, issueId, dfId, owner, logger)
                            : unpackMl3gFlat(bt, uri, issueId, dfId, owner, logger)));    //  EDI QA

            Hooks.captureRecords(getContentHandler().getDbId(), ret.keySet(), Hooks.UPLOAD_START);
            return ret;

        } catch (DeliveryPackageException de) {

            if (de.getStatus() == IDeliveryFileStatus.STATUS_RND_NOT_STARTED
                    || de.getStatus() == IDeliveryFileStatus.STATUS_PACKAGE_LOADED) {
                // stop processing
                updateDeliveryFileStatus(de.getStatus());
                pvo.setFreeToCompletedWithParent();
                return Collections.emptyMap();
            }
            throw new ProcessException(de.getMessage(), pvo.getId());

        } catch (Exception e) {
            throw new ProcessException(e.getMessage(), pvo.getId());
        }
    }

    protected Map<String, IRecord> unpackAries(BaseType bt, URI uri, Integer issueId, int dfId, String owner,
                                               IFlowLogger logger) throws Exception {
        int issue = getContentHandler().getIssue();

        if (bt.isEditorial() && ResultStorageFactory.getFactory().getInstance().getDeliveryFileEntity(dfId).isWml3g()) {
            return unpackMl3gFlat(bt, uri, issueId, dfId, owner, logger);
        }

        AriesMl3gPackage ap = new AriesMl3gPackage(uri, bt.getId(), dfId, issue);
        packageName = ap.getPackageFileName();

        Map<String, IRecord> results;
        try {
            results = ap.extractData(issueId, getContentHandler().getDbId(),
                bt.isCCA() ? PackageType.findCcaMl3gAries().get() : PackageType.findEditorialMl3gAries().get(), logger);

        } catch (DeliveryPackageException de) {
            if (de.getStatus() == IDeliveryFileStatus.STATUS_PACKAGE_LOADED
                    || de.getStatus() == IDeliveryFileStatus.STATUS_PACKAGE_DELETED) {
                removeAriesPackage();
            }
            throw de;
        }

        if (results.isEmpty()) {
            setUnzipped(dfId, owner, logger.getActivityLog());
        } else {
            removeAriesPackage();
            getContentHandler().updateDeliveryFileOnRecordCreate(packageName, getTypeUpdatedForAries(ap),
                    owner, results.size(), ResultStorageFactory.getFactory().getInstance(), logger.getActivityLog());
        }
        return results;
    }

    private Map<String, IRecord> unpackMl3gFlat(BaseType bt, URI uri, Integer issueId, Integer dfId, String owner,
                                                IFlowLogger logger) throws Exception {
        Map<String, IRecord> results = Collections.emptyMap();
        if (bt.isEditorial()) {
            DeliveryPackage dp = new DeliveryPackage(uri, bt.getId(), dfId, getContentHandler().getIssue());
            packageName = dp.getPackageFileName();

            results = dp.extractData(issueId, getContentHandler().getDbId(),
                    PackageType.findEditorialMl3gFlat().get(), logger);
            logger.getActivityLog().logDeliveryFile(ILogEvent.PACKAGE_UNZIPPED, dfId, packageName, owner);

            if (!results.isEmpty()) {
                removeAriesPackage();
                getContentHandler().updateDeliveryFileOnRecordCreate(packageName, DeliveryFileEntity.TYPE_WML3G, owner,
                        results.size(), ResultStorageFactory.getFactory().getInstance(), logger.getActivityLog());
            }
        }
        return results;
    }

    protected Map<String, IRecord> unpackCommon(BaseType bt, URI uri, Integer issueId, Integer dfId, String owner,
                                                IFlowLogger logger) throws Exception {
        int dbId = getContentHandler().getDbId();
        DeliveryPackage dp = new DeliveryPackage(uri, bt.getId(), dfId, getContentHandler().getIssue());
        packageName = dp.getPackageFileName();
        Map<String, IRecord> results = Collections.emptyMap();
        DeliveryPackageInfo dfInfo;
        IResultsStorage rs = ResultStorageFactory.getFactory().getInstance();
        int type = bt.isCentral() ? DeliveryFileEntity.TYPE_DEFAULT : DeliveryFileEntity.TYPE_WML3G;

        if (bt.isEditorial()) {
            results = dp.extractData(issueId, dbId, PackageType.findEditorialMl3g().get(), logger);
            logger.getActivityLog().logDeliveryFile(ILogEvent.PACKAGE_UNZIPPED, dfId, packageName, owner);

        } else if (bt.isCCA()) {
            results = dp.extractData(issueId, dbId, PackageType.findCcaMl3g().get(), logger);
            logger.getActivityLog().logDeliveryFile(ILogEvent.PACKAGE_UNZIPPED, dfId, packageName, owner);

        } else if (bt.isCentral()) {
            // deprecated part currently used for CENTRAL only
            logger.onPackageReceived(bt, packageName, dfId, PackageChecker.METAXIS, null);
            try {
                dfInfo = dp.extractData(issueId, dbId);

            } catch (Exception e) {
                logger.onPackageUnpacked(bt, packageName, dfId, PackageChecker.METAXIS,
                    CmsUtils.buildIssuePackageTitle(getContentHandler().getIssue()), 0, e.getMessage());
                throw e;
            }
            logger.onPackageUnpacked(bt, packageName, dfId, PackageChecker.METAXIS,
                CmsUtils.buildIssuePackageTitle(getContentHandler().getIssue()), dfInfo.getRecordNames().size(), null);
            logger.getActivityLog().logDeliveryFile(ILogEvent.PACKAGE_UNZIPPED, dfId, packageName, owner);

            List<String> list = dfInfo.getRecordNames();
            if (!list.isEmpty()) {
                results = new HashMap<>();
                for (String cdNumber : list) {
                    Record record = new Record(cdNumber, false);
                    record.setRecordSourceUri(dfInfo.getRecordPath(cdNumber));
                    record.setDeliveryFileId(dfId);
                    results.put(cdNumber, record);
                }
            }
            rs.createRecords(results, dbId, dfId);
        } else {
            DeliveryPackageException.throwNoWorkableRecordsError("unpacking is not supported");
        }
        getContentHandler().updateDeliveryFileOnRecordCreate(packageName, type, owner, results.size(), rs,
                logger.getActivityLog());
        return results;
    }

    protected Map<String, IRecord> unpackJats(BaseType bt, URI uri, Integer issueId, int dfId,
                                              String owner, IFlowLogger logger) throws Exception {
        return Collections.emptyMap();
    }

    void setUnzipped(int dfId, String owner, IActivityLog logger) {
        setDeliveryFileStatus(IDeliveryFileStatus.STATUS_UNZIPPED, true);
        logger.logDeliveryFile(ILogEvent.PACKAGE_UNZIPPED, dfId, packageName, owner);
    }

    void removeAriesPackage() {
        //AriesConnectionManager.removePackage(packageName);
        //setDeliveryFileStatus(IDeliveryFileStatus.STATUS_PACKAGE_DELETED, true);
    }

    private static boolean isFtpPackage(String packagePath) {
        return packagePath.contains("ftp");
    }

    private static int getTypeUpdatedForAries(AriesMl3gPackage ap) {
        int typeUpdated = DeliveryFileEntity.TYPE_ARIES;
        if (ap.getArticleCount() > 0) {
            typeUpdated = DeliveryFileEntity.setWml3g(typeUpdated);
        }
        return typeUpdated;
    }
}
