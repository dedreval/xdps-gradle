package com.wiley.cms.cochrane.process;

import java.util.ArrayList;
import java.util.List;

import com.wiley.cms.cochrane.activitylog.ActivityLogEntity;
import com.wiley.cms.cochrane.activitylog.ActivityLogFactory;
import com.wiley.cms.cochrane.activitylog.IActivityLogService;
import com.wiley.cms.cochrane.cmanager.CochraneCMSBeans;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.data.entire.EntireDBEntity;
import com.wiley.cms.cochrane.cmanager.data.entire.IEntireDBStorage;
import com.wiley.cms.cochrane.cmanager.entitywrapper.SearchRecordOrder;
import com.wiley.cms.cochrane.process.handler.PackageHandler;
import com.wiley.cms.process.IProcessManager;
import com.wiley.cms.process.ProcessException;
import com.wiley.cms.process.ProcessHelper;
import com.wiley.cms.process.ProcessManager;
import com.wiley.cms.process.ProcessVO;
import com.wiley.cms.process.entity.DbEntity;
import com.wiley.cms.process.jms.IQueueProvider;
import com.wiley.tes.util.DbConstants;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 24.09.13
 */
public abstract class BaseManager extends ProcessManager {

    public static final String LABEL_CONV_RECORDS = "ConversionRecords";

    public static final String LABEL_RENDERING_RECORDS = "RenderingRecords";

    protected static final String LABEL_MAIN_PACKAGE = "Package";
    protected static final String LABEL_RENDERING_PACKAGE = "RenderingPackage";
    protected static final String LABEL_RENDERING_ENTIRE = "RenderingEntire";

    protected IActivityLogService logService = ActivityLogFactory.getFactory().getInstance();

    @Override
    protected IQueueProvider getQueueProvider() {
        return CochraneCMSBeans.getQueueProvider();
    }

    /** This manager will deletes completed processes except the failed one. **/
    @Override
    protected void onEnd(ProcessVO pvo) throws ProcessException {

        super.onEnd(pvo);

        if (!pvo.getState().isFailed()) {
            deleteProcess(pvo.getId(), true);
        }
    }

    protected void logProcessEntire(int prId, String dbName, int event, String addMsg, boolean record) {
        logProcessEntire(prId, dbName, event, addMsg, record, CochraneCMSPropertyNames.getSystemUser());
    }

    protected void logErrorProcessEntire(int prId, String dbName, int event, String msg, boolean record) {
        logErrorProcessEntire(prId, dbName, event, msg, record, CochraneCMSPropertyNames.getSystemUser());
    }

    protected void logProcessEntire(int prId, String dbName, int event, String addMsg, boolean record, String user) {
        logService.info(record ? ActivityLogEntity.EntityLevel.RECORD : ActivityLogEntity.EntityLevel.ENTIREDB,
            event, 1, dbName, user, ProcessHelper.buildProcessMsg(prId, addMsg));
    }

    protected void logErrorProcessEntire(int prId, String dbName, int event, String msg, boolean record, String user) {
        logService.error(record ? ActivityLogEntity.EntityLevel.RECORD : ActivityLogEntity.EntityLevel.ENTIREDB,
                event, 1, dbName, user, ProcessHelper.buildProcessMsg(prId, msg));
    }

    protected void logProcessForDeliveryPackage(int prId, int pckId, String pckName, int event, String addMsg) {
        logService.info(ActivityLogEntity.EntityLevel.FILE, event, pckId, pckName,
                CochraneCMSPropertyNames.getSystemUser(), ProcessHelper.buildProcessMsg(prId, addMsg));
    }

    protected void logProcessForDeliveryPackage(int prId, int pckId, String pckName, int event) {
        String logUser = CochraneCMSPropertyNames.getSystemUser();
        logService.info(ActivityLogEntity.EntityLevel.FILE, event, pckId, pckName, logUser,
                ProcessHelper.buildProcessMsg(prId));
    }

    protected void logErrorProcessForDeliveryPackage(int prId, int pckId, String pckName, int event, String msg) {
        logService.error(ActivityLogEntity.EntityLevel.FILE, event, pckId, pckName,
                CochraneCMSPropertyNames.getSystemUser(), ProcessHelper.buildProcessMsg(prId, msg));
    }

    protected void logProcessDb(int prId, String dbName, int dbId, int event, String msg, boolean record, String user) {
        logService.info(record ? ActivityLogEntity.EntityLevel.RECORD : ActivityLogEntity.EntityLevel.DB,
                event, dbId, dbName, user, ProcessHelper.buildProcessMsg(prId, msg));
    }

    protected void logErrorProcessDb(int prId, String dbName, int dbId, int event, String msg, boolean record,
        String user) {
        logService.error(record ? ActivityLogEntity.EntityLevel.RECORD : ActivityLogEntity.EntityLevel.DB,
                event, dbId, dbName, user, ProcessHelper.buildProcessMsg(prId, msg));
    }

    protected void logProcess(int prId, int event, String msg) {
        logService.error(ActivityLogEntity.EntityLevel.SYSTEM, event, DbEntity.NOT_EXIST_ID, "",
                CochraneCMSPropertyNames.getSystemUser(), ProcessHelper.buildProcessMsg(prId, msg));
    }

    protected void logErrorProcess(int prId, int event, String msg) {
        logErrorProcess(prId, event, msg, DbEntity.NOT_EXIST_ID);
    }

    protected void logErrorProcess(int prId, int event, String msg, int entityId) {
        logService.error(ActivityLogEntity.EntityLevel.SYSTEM, event, entityId, "",
            CochraneCMSPropertyNames.getSystemUser(), ProcessHelper.buildProcessMsg(prId, msg));
    }

    protected int getPackagePriority(boolean cdsr) {
        int priority = IProcessManager.USUAL_PRIORITY;
        if (cdsr) {
            priority = CochraneCMSPropertyNames.canArchieAut() ? IProcessManager.HIGHEST_PRIORITY
                    : IProcessManager.HIGH_PRIORITY;
        }
        return priority;
    }

    protected int getPriority(boolean entire) {
        return entire ? IProcessManager.LOWEST_PRIORITY : IProcessManager.USUAL_PRIORITY;
    }

    protected ProcessVO startMainPackageProcess(int packageId, String packageName, String dbName) {
        int priority =  getPackagePriority(CochraneCMSPropertyNames.getCDSRDbName().equals(dbName));
        return startProcess(new PackageHandler(LABEL_MAIN_PACKAGE, packageId, packageName, dbName), priority);
    }

    protected List<String> findRecordNames(ProcessVO pvo, String dbName, IEntireDBStorage edbs) {

        int processId = pvo.getId();
        List<String> recs = new ArrayList<>();

        int beginIndex = 0;
        List<EntireDBEntity> recordList = edbs.getRecordList(dbName, beginIndex, DbConstants.DB_PACK_SIZE, null, null,
                    SearchRecordOrder.NONE, false,  processId);
        while (recordList != null && !recordList.isEmpty()) {

            addRecordNames(recs, recordList);
            beginIndex += DbConstants.DB_PACK_SIZE;
            recordList = edbs.getRecordList(dbName, beginIndex, DbConstants.DB_PACK_SIZE, null, null,
                SearchRecordOrder.NONE, false,  processId);
        }

        return recs;
    }

    private void addRecordNames(List<String> names, List<EntireDBEntity> recordList) {
        for (EntireDBEntity e: recordList) {
            names.add(e.getName());
        }
    }
}
