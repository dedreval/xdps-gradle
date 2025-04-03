package com.wiley.cms.cochrane.cmanager.entitywrapper;

import com.wiley.cms.cochrane.activitylog.ActivityLogEntity;
import com.wiley.cms.cochrane.activitylog.ILogEvent;
import com.wiley.cms.cochrane.authentication.IVisit;
import com.wiley.cms.cochrane.cmanager.CochraneCMSBeans;
import com.wiley.cms.cochrane.cmanager.CochraneCMSProperties;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.FilePathBuilder;
import com.wiley.cms.cochrane.cmanager.MessageSender;
import com.wiley.cms.cochrane.cmanager.data.DatabaseEntity;
import com.wiley.cms.cochrane.cmanager.data.entire.EntireDBStorageFactory;
import com.wiley.cms.cochrane.cmanager.data.entire.IEntireDBStorage;
import com.wiley.cms.cochrane.cmanager.data.rendering.RenderingPlan;
import com.wiley.cms.cochrane.cmanager.entitymanager.AbstractManager;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.process.ICMSProcessManager;
import com.wiley.cms.cochrane.process.RenderingHelper;
import com.wiley.cms.cochrane.process.Wml3gConversionManager;
import com.wiley.cms.cochrane.process.handler.DbHandler;
import com.wiley.cms.cochrane.process.handler.JatsConversionHandler;
import com.wiley.cms.cochrane.utils.ContentLocation;
import com.wiley.cms.cochrane.utils.ErrorInfo;
import com.wiley.cms.converter.services.IConversionProcess;
import com.wiley.cms.process.ProcessHelper;
import com.wiley.cms.process.ProcessVO;
import com.wiley.cms.process.entity.DbEntity;
import com.wiley.cms.process.res.ProcessType;
import com.wiley.tes.util.DbConstants;
import com.wiley.tes.util.Logger;
import com.wiley.tes.util.res.Res;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.wiley.cms.cochrane.cmanager.entitywrapper.SearchRecordOrder.ID;
import static com.wiley.cms.cochrane.utils.Constants.UNDEF;

/**
 * @author <a href='mailto:ikirov@wiley.com'>Igor Kirov</a>
 * @version 30.11.2009
 */
public class EntireDbWrapper extends AbstractWrapper implements java.io.Serializable {
    private static final Logger LOG = Logger.getLogger(EntireDbWrapper.class);

    private String dbName;
    private int recordsCount = -1;

    private ArrayList<PublishWrapper> publishList;

    public EntireDbWrapper(String dbName) {
        this.dbName = dbName;
    }

    public String getManualReportPath() {
        return CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.PREFIX_REPOSITORY) + "/" + getDbName()
            + "/reports/ManualReport.xls";
    }

    public AddonFile getManualReport() {
        List<AddonFile> list = new ArrayList<>();

        tryAddAddonFile(getManualReportPath(), "Manual Report", list);

        return list.size() > 0 ? list.get(0) : null;
    }

    public AddonFile getCochraneReport() {
        List<AddonFile> list = new ArrayList<>();
        tryAddAddonFile(getCochraneReportPath(), "Cochrane Report", list);
        return list.size() > 0 ? list.get(0) : null;
    }

    public AddonFile getTranslationReport() {

        List<AddonFile> list = new ArrayList<AddonFile>();
        tryAddAddonFile(getTranslationReportPath(), "Translation Report", list);
        return list.size() > 0 ? list.get(0) : null;
    }

    @Override
    protected void tryAddAddonFile(String path, String name, List<AddonFile> list) {
        try {
            super.tryAddAddonFile(path, name, list);
        } catch (IOException e) {
            LOG.debug(e, e);
        }
    }

    public static List<EntireDbWrapper> getEntireDbWrapperList() {
        List<EntireDbWrapper> list = new ArrayList<EntireDbWrapper>();
        list.add(new EntireDbWrapper(CochraneCMSPropertyNames.getCDSRDbName()));
        list.add(new EntireDbWrapper(CochraneCMSPropertyNames.getEditorialDbName()));
        list.add(new EntireDbWrapper(CochraneCMSPropertyNames.getCentralDbName()));
        list.add(new EntireDbWrapper(CochraneCMSPropertyNames.getCcaDbName()));

        //list.add(new EntireDbWrapper(CLABOUT)); not supported yet
        return list;
    }

    public void setPublishList(ArrayList<PublishWrapper> publishList) {
        this.publishList = publishList;
    }

    public ArrayList<PublishWrapper> getPublishList() {
        return publishList;
    }

    public String getDbName() {
        return dbName;
    }

    public int getDbId() {
        Res<BaseType> bt = BaseType.find(dbName);
        return Res.valid(bt) ? bt.get().getDbId() : DbEntity.NOT_EXIST_ID;
    }

    public void setDbName(String dbName) {
        this.dbName = dbName;
    }

    public String getShortName() {
        try {
            return BaseType.find(getDbName()).get().getShortName();
        } catch (Exception e) {
            LOG.error(e.getMessage());
            return "";
        }
    }

    public String getFullName() {
        try {
            return BaseType.find(getDbName()).get().getFullName();
        } catch (Exception e) {
            LOG.error(e.getMessage());
            return "";
        }
    }

    public int getRecordsCount() {
        calcRecordCount();
        return recordsCount;
    }

    public int getRecordsCount4UI() {
        return recordsCount;
    }

    public void calcRecordCount() {
        recordsCount = CochraneCMSBeans.getPageManager().getRecordListCount(getDbName());
    }

    public void publish(IVisit visit) {
        new PublishAction().perform(visit);
    }

    public void render(IVisit visit, int action) {
        new RenderAction(action).perform(visit);
    }

    public void convertRevman(IVisit visit) {
        new ConvertRevmanAction().perform(visit);
    }

    public void convertJats(IVisit visit) {
        new ConvertJatsAction().perform(visit);
    }

    public void convertTo3g(IVisit visit) {
        new ConvertTo3gAction().perform(visit);
    }

    public static String getTranslationReportPath() {
        return FilePathBuilder.getPathToEntireReport(BaseType.getCDSR().get().getId()) + "TranslationReport.xls";
    }

    public static String getCochraneReportPath() {
        return FilePathBuilder.getPathToEntireReport(BaseType.getCDSR().get().getId()) + "CochraneReportCDSR.xls";
    }

    private class PublishAction extends AbstractAction {

        public int getId() {
            return Action.PUBLISH_ACTION;
        }

        public String getDisplayName() {
            return CochraneCMSProperties.getProperty("db.action.publish.name");
        }

        public void perform(IVisit visit) {
            try {
                getPublishService().publishEntireDb(EntireDbWrapper.this.getDbName(),
                        EntireDbWrapper.this.getPublishList());

                logAction(visit, ActivityLogEntity.EntityLevel.ENTIREDB, ILogEvent.PUBLISH,
                        EntireDbWrapper.this.getDbId(), EntireDbWrapper.this.getDbName());
            } catch (Exception e) {
                LOG.debug(e, e);
            }
        }
    }

    private class RenderAction extends AbstractRenderAction {

        public RenderAction(int action) {
            this.action = action;
        }

        public void perform(IVisit visit) {
            try {
                List<Integer> recordIds = EntireDBStorageFactory.getFactory().getInstance().getRecordIds(
                        dbName, 0, UNDEF, null, null, ID, false, null, UNDEF);
                ProcessVO pvo = RenderingHelper.createSelectiveRenderingProcess(dbName, RenderingPlan.PDF_FOP, false,
                        recordIds.toArray(new Integer[recordIds.size()]), visit.getLogin(), null);
                CochraneCMSBeans.getCMSProcessManager().startProcess(pvo);

            } catch (Exception e) {
                LOG.error(e, e);
            }
        }
    }


    private class ConvertTo3gAction extends AbstractAction {

        public int getId() {
            return Action.CONVERT_TO_3G_ACTION;
        }

        public String getDisplayName() {
            return "WML3G conversion";
        }

        public void perform(IVisit visit) {
            int dbId = AbstractWrapper.getResultStorage().getDatabaseEntity(dbName).getId();
            Wml3gConversionManager.Factory.getBeanInstance().startConversion(dbId, visit.getLogin());

        }
    }

    private class ConvertJatsAction extends AbstractAction {

        public int getId() {
            return Action.CONVERT_JATS_ACTION;
        }

        public String getDisplayName() {
            return "JATS to WML3G";
        }

        public void perform(IVisit visit) {
            IEntireDBStorage edbs = getEntireDbStorage();
            int limit = DbConstants.DB_PACK_SIZE;
            Map<Integer, String> map = edbs.getRecordIdsAndNames(dbName, 0, limit, null, null, 0, false, null);
            if (map.isEmpty()) {
                LOG.info(String.format("no records for re-conversion found in  %s", dbName));
                return;
            }
            BaseType bt = BaseType.find(dbName).get();
            ProcessType pt = ProcessType.find(ICMSProcessManager.PROC_TYPE_CONVERT_JATS).get();
            DbHandler dbHandler = new DbHandler(0, dbName, bt.getDbId(), false);
            JatsConversionHandler jh = new JatsConversionHandler(dbHandler, 0);
            try {
                int start = limit;
                ProcessVO pvo = ProcessHelper.createIdPartsProcess(jh, pt, pt.getPriority(), visit.getLogin(),
                        getIds(map), DbEntity.NOT_EXIST_ID, pt.batch());
                map = edbs.getRecordIdsAndNames(dbName, start, limit, null, null, 0, false, null);
                while (!map.isEmpty()) {
                    ProcessHelper.createIdPartsProcess(pvo, getIds(map), pt.batch(), getProcessStorage());
                    start += limit;
                    map = edbs.getRecordIdsAndNames(dbName, start, limit, null, null, 0, false, null);
                }
                CochraneCMSBeans.getCMSProcessManager().startProcess(pvo);

            } catch (Exception e) {
                MessageSender.sendWml3gConversion(ContentLocation.ENTIRE.getShortString(0, dbName, null),
                        e.getMessage());
                LOG.error("Jats conversion failed, " + e);
            }
        }

        private Integer[] getIds(Map<Integer, String> map) {
            Integer[] ids = new Integer[map.size()];
            int i = 0;
            for (Map.Entry<Integer, String> entry: map.entrySet()) {
                ids[i++] = entry.getKey();
            }
            return ids;
        }
    }

    private class ConvertRevmanAction extends AbstractConvertRevmanAction {

        public void perform(IVisit visit) {
            try {
                IConversionProcess cp = AbstractManager.getConversionProcess();
               
                String revmanDir = FilePathBuilder.getPathToEntireRevman();
                String destination = FilePathBuilder.getPathToReconvertedEntireSrc(dbName);

                List<ErrorInfo> errors;

                logAction(visit, ActivityLogEntity.EntityLevel.ENTIREDB, ILogEvent.CONVERSION_REVMAN_STARTED,
                        DatabaseEntity.CDSR_KEY, dbName);
                CochraneCMSBeans.getRecordCache().addSingleProcess(dbName, ILogEvent.CONVERSION_REVMAN_STARTED);

                errors = cp.convert(revmanDir, destination, null);
                if (!errors.isEmpty()) {
                    throw new Exception(getReport(errors, DbEntity.NOT_EXIST_ID, getActivityLog()));
                }

                logAction(visit, ActivityLogEntity.EntityLevel.ENTIREDB, ILogEvent.REVMAN_DATA_CONVERTED,
                        DatabaseEntity.CDSR_KEY, dbName);
            } catch (Exception e) {
                Map<String, String> message = new HashMap<String, String>();
                message.put(MessageSender.MSG_PARAM_LIST, e.getMessage());
                MessageSender.sendMessage(MessageSender.MSG_TITLE_ENTIRE_REVMAN_CONVERSION_FAILED, message);

                LOG.error("Revman conversion failed, " + e);
                logAction(visit, ActivityLogEntity.EntityLevel.ENTIREDB, ILogEvent.CONVERSION_REVMAN_FAILED,
                        DatabaseEntity.CDSR_KEY, dbName);
            }
        }
    }

}
