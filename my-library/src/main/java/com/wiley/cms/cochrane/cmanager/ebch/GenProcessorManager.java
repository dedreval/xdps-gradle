package com.wiley.cms.cochrane.cmanager.ebch;

import com.wiley.cms.cochrane.activitylog.ILogEvent;
import com.wiley.cms.cochrane.activitylog.LogEntity;
import com.wiley.cms.cochrane.activitylog.UUIDEntity;
import com.wiley.cms.cochrane.cmanager.CmsException;
import com.wiley.cms.cochrane.cmanager.CmsUtils;
import com.wiley.cms.cochrane.cmanager.CochraneCMSBeans;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.FilePathBuilder;
import com.wiley.cms.cochrane.cmanager.MessageSender;
import com.wiley.cms.cochrane.cmanager.contentworker.AriesHelper;
import com.wiley.cms.cochrane.cmanager.contentworker.RevmanPackage;
import com.wiley.cms.cochrane.cmanager.data.DeliveryFileEntity;
import com.wiley.cms.cochrane.cmanager.data.IResultsStorage;
import com.wiley.cms.cochrane.cmanager.data.db.ClDbVO;
import com.wiley.cms.cochrane.cmanager.data.record.RecordEntity;
import com.wiley.cms.cochrane.cmanager.data.rendering.RenderingPlan;
import com.wiley.cms.cochrane.cmanager.data.translation.PublishedAbstractEntity;
import com.wiley.cms.cochrane.cmanager.ebch.process.GenProcessor;
import com.wiley.cms.cochrane.cmanager.ebch.process.GenProcessorParameters;
import com.wiley.cms.cochrane.cmanager.entitymanager.AbstractManager;
import com.wiley.cms.cochrane.cmanager.entitymanager.IRecordManager;
import com.wiley.cms.cochrane.cmanager.entitymanager.RecordHelper;
import com.wiley.cms.cochrane.cmanager.entitywrapper.EntireRecordWrapper;
import com.wiley.cms.cochrane.cmanager.entitywrapper.PublishWrapper;
import com.wiley.cms.cochrane.cmanager.entitywrapper.RecordWrapper;
import com.wiley.cms.cochrane.cmanager.entitywrapper.ResultStorageFactory;
import com.wiley.cms.cochrane.cmanager.publish.PublishHelper;
import com.wiley.cms.cochrane.cmanager.publish.PublishServiceFactory;
import com.wiley.cms.cochrane.cmanager.publish.generate.semantico.HWFreq;
import com.wiley.cms.cochrane.cmanager.publish.send.cochrane.CochraneSender;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.cmanager.res.PubType;
import com.wiley.cms.cochrane.cmanager.res.PublishProfile;
import com.wiley.cms.cochrane.process.EntireRenderingManager;
import com.wiley.cms.cochrane.process.ICMSProcessManager;
import com.wiley.cms.cochrane.process.IEntireRenderingManager;
import com.wiley.cms.cochrane.process.IRecordCache;
import com.wiley.cms.cochrane.process.IWml3gConversionManager;
import com.wiley.cms.cochrane.process.RecordCache;
import com.wiley.cms.cochrane.process.RenderingHelper;
import com.wiley.cms.cochrane.process.Wml3gConversionManager;
import com.wiley.cms.cochrane.process.handler.ConversionRecordHandler;
import com.wiley.cms.cochrane.process.handler.DbHandler;
import com.wiley.cms.cochrane.process.handler.JatsConversionHandler;
import com.wiley.cms.cochrane.process.handler.Wml3gValidationHandler;
import com.wiley.cms.cochrane.repository.RepositoryFactory;
import com.wiley.cms.process.ProcessHelper;
import com.wiley.cms.process.ProcessVO;
import com.wiley.cms.process.entity.DbEntity;
import com.wiley.cms.process.res.ProcessType;
import com.wiley.tes.util.DbUtils;
import com.wiley.tes.util.Logger;
import com.wiley.tes.util.res.Res;
import org.apache.commons.lang.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.wiley.cms.cochrane.cmanager.entitywrapper.AbstractWrapper.getResultStorage;
/**
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @version 1.0
 */
public final class GenProcessorManager {
    private static final Logger LOG = Logger.getLogger(GenProcessorManager.class);

    private GenProcessorManager() {
    }

    public static void startProcess(GenProcessorParameters params, Map<String, String[]> requestParametersMap,
                                    Action action, IBasketHolder holder, boolean admin) throws CmsException {

        GenProcessor processor = getProcessor(params, requestParametersMap, action, admin);
        if (processor != null) {
            startProcess(processor, action, holder);
        }
    }

    public static void startProcess(GenProcessor processor, Action action, IBasketHolder holder) {
        LOG.info("Process starting - " + action.name);

        processor.setRecordIds(holder);
        new Thread(processor).start();

        if (holder != null) {
            holder.clearProcessBasket();
        }
    }

    private static GenProcessor getProcessor(GenProcessorParameters params, Map<String, String[]> requestParametersMap,
                                             Action action, boolean admin)  throws CmsException {

        GenProcessor ret = null;
        switch (action) {

            case PUBLISHING:
            case PUBLISHING_WR:
            case PUBLISHING_ENTIRE:
            case ACKNOWLEDGEMENT_WR:
                ret = new PublishProcessor(params, requestParametersMap, action, admin);
                break;
            case CANCELING_PROCESS:
                ret = new CancelProcessor(params, requestParametersMap);
                break;
            case RE_PROCESS:
                ret = new WhenReadyProcessor(params, requestParametersMap);
                break;
            case RENDERING_HTML:
                ret = new RenderProcessor(params, requestParametersMap, RenderingPlan.HTML);
                break;
            case RENDERING_PDF_FOP:
                ret = new RenderProcessor(params, requestParametersMap, RenderingPlan.PDF_FOP);
                break;
            case WML3G_CONVERSION:
                ret = new Wml3gConversionProcessor(params, requestParametersMap);
                break;
            case PROCESS_CONTENT:
                ret = new ContentProcessor(params, requestParametersMap);
                break;
            case IMPORT:
                ret = new ImportProcessor(params, requestParametersMap, false);
                break;
            case RESTORE:
                ret = new ImportProcessor(params, requestParametersMap, true);
                break;

            default:
                LOG.error("action is undefined: " + action);
                break;
        }
        return ret;
    }

    private static IWml3gConversionManager getWml3gConversionManager() {
        return Wml3gConversionManager.Factory.getBeanInstance();
    }

    /**
     * Actions
     * Do not change action ordering without editing:
     *       EBCHFormPage.html, EntireEBCHFormPage.html, ClDbPage.html, EntireDbPage.html
     */
    public enum Action {
        // 0
        UNDEFINED("Undefined action"),
        // 1
        @Deprecated
        RENDERING("EBCH rendering"),
        // 2
        @Deprecated
        PUBLISHING_WOL("WOL publishing"),
        // 3
        @Deprecated
        PUBLISHING_CCH("CCH publishing", PubType.MAJOR_TYPE_CCH),
        // 4
        PUBLISHING("Publishing", PubType.MAJOR_TYPE_S, PubType.MAJOR_TYPE_LIT, PubType.MAJOR_TYPE_DS,
                PubType.MAJOR_TYPE_ARIES, PubType.MAJOR_TYPE_COCHRANE),
        // 5
        PUBLISHING_WR("Publishing [When Ready]", PubType.MAJOR_TYPE_S, PubType.MAJOR_TYPE_LIT, PubType.MAJOR_TYPE_DS,
                PubType.MAJOR_TYPE_ARIES) {
            @Override
            public boolean isMandatory(String pubType) {
                return PublishProfile.PUB_PROFILE.get().getDestination().isMandatoryPubType(pubType);
            }

            @Override
            public boolean isDisabled(String pubType) {
                return PubType.TYPE_ARIES_ACK_P.equals(pubType);
            }

            @Override
            public boolean isGenerationEnabled() {
                return false;
            }
        },
        // 6
        CANCELING_PROCESS("Cancel process"),
        // 7
        RENDERING_HTML("HTML rendering") {
            @Override
            public boolean canRender() {
                return true;
            }
        },
        // 8
        @Deprecated
        RENDERING_PDF("PDF rendering") {
            @Override
            public boolean canRender() {
                return true;
            }
        },
        // 9
        EXPORT("Export") {
            @Override
            public boolean canExport() {
                return true;
            }
        },
        // 10
        WML3G_CONVERSION("WML3G conversion") {
            @Override
            public boolean canWml3gConversion() {
                return true;
            }
        },
        // 11
        CONVERT_REVMAN("Convert RevMan") {
            @Override
            public boolean canRevmanConversion() {
                return true;
            }
        },
        // 12
        PROCESS_CONTENT("Content processing") {
            @Override
            public boolean isContentProcessing() {
                return true;
            }
        },
        // 13 Not in use anymore
        @Deprecated
        COPY_TO_ML("Copy to Marklogic") {
            @Override
            public boolean isContentProcessing() {
                return true;
            }
        },
        // 14
        CONVERT_REVMAN_TA("Convert Translations 3.0") {
            @Override
            public boolean isContentProcessing() {
                return true;
            }
        },
        // 15 Not in use anymore
        @Deprecated
        REFRESH_LM_CACHE("Refresh a LM cache"),
        // 16
        RE_PROCESS("Re-process [When Ready]"),
        // 17 Not in use anymore
        @Deprecated
        REFRESH_LM_ISSUE("Update old references with Link Master"),
        // 18
        RENDERING_PDF_FOP("PDF [FOP] rendering") {
            @Override
            public boolean canRender() {
                return true;
            }
        },
        // 19
        CONVERT_JATS("Convert JATS") {
            @Override
            public boolean canJatsConversion() {
                return true;
            }
        },
        // 20
        CONVERT_JATS_TA("Convert JATS Translations") {
            @Override
            public boolean canJatsConversion() {
                return true;
            }
        },
        // 21
        IMPORT("Import articles"),
        // 22
        RESTORE("Restore imported articles from backup"),
        // 23
        UPDATE_MESH_WML3G("Update WML3G MeSH") {
            @Override
            public boolean canWml3gMeshUpdate() {
                return true;
            }
        },
        // 24
        ACKNOWLEDGEMENT_WR("Acknowledge [When Ready]", PubType.MAJOR_TYPE_ARIES) {
            @Override
            public boolean isMandatory(String pubType) {
                return PubType.TYPE_ARIES_ACK_P.equals(pubType);
            }

            @Override
            public boolean isDisabled(String pubType) {
                return PubType.TYPE_ARIES_ACK_D.equals(pubType);
            }

            @Override
            public boolean isGenerationEnabled() {
                return false;
            }
        },
        // 25
        PUBLISHING_ENTIRE("Publishing ", PubType.MAJOR_TYPE_S, PubType.MAJOR_TYPE_LIT, PubType.MAJOR_TYPE_DS);

        public final String name;
        private final Set<String> majorPubTypes;

        Action(String name) {
            this.name = name;
            majorPubTypes = null;
        }

        Action(String name, String... majorTypes) {
            this.name = name;

            majorPubTypes = new LinkedHashSet<>();
            majorPubTypes.addAll(Arrays.asList(majorTypes));
        }

        public Set<String> getMajorPubTypes() {
            return majorPubTypes;
        }

        public boolean canRender() {
            return false;
        }

        public boolean canPublish() {
            return  majorPubTypes != null;
        }

        public boolean canExport() {
            return false;
        }

        public boolean canWml3gConversion() {
            return false;
        }

        public boolean canRevmanConversion() {
            return false;
        }

        public boolean canJatsConversion() {
            return false;
        }

        public boolean canWml3gMeshUpdate() {
            return false;
        }

        public boolean isMandatory(String pubType) {
            return false;
        }

        public boolean isDisabled(String pubType) {
            return false;
        }

        public boolean isGenerationEnabled() {
            return true;
        }

        public boolean isContentProcessing() {
            return false;
        }

        public static Action getByOrdinal(int ordinal) {
            try {
                return Action.values()[ordinal];
            } catch (Exception e) {
                LOG.error(e.getMessage(), e);
                return UNDEFINED;
            }
        }
    }

    /**
     * When Ready records re-processing
     */
    private static class WhenReadyProcessor extends CancelProcessor {

        WhenReadyProcessor(GenProcessorParameters params, Map<String, String[]> requestParametersMap)
                throws CmsException {
            super(params, requestParametersMap);
        }

        public void run() {

            super.run();

            Integer[] ids = getRecordsIds();
            if (ids == null || ids.length == 0) {
                return;
            }

            Set<Integer> set = new HashSet<>(Arrays.asList(ids));
            try {
                String ret = CochraneCMSBeans.getPackageDownloader().uploadCDSRWhenReady(params.getDbId(), set);
                if (ret != null && !ret.isEmpty()) {

                    MessageSender.sendReport(MessageSender.MSG_TITLE_SYSTEM_WARN, "Re-processing errors:\n" + ret);
                    LOG.warn(ret);
                }

            } catch (Exception e) {
                LOG.error(e);
            }
        }

        @Override
        protected void logActivity(int entityId, String entityName) {
            logActivity(LogEntity.EntityLevel.FLOW, ILogEvent.REPROCESS_WR, entityId, entityName);
        }

        protected boolean resetCacheOnly() {
            return true;
        }
    }

    /**
     * Cancel records processing
     */
    private static class CancelProcessor extends GenProcessor<GenProcessorParameters> {

        CancelProcessor(GenProcessorParameters params, Map<String, String[]> requestParametersMap) throws CmsException {
            super(params, requestParametersMap);
        }

        @Override
        public void run() {

            Integer[] ids = getRecordsIds();
            if (ids == null || ids.length == 0) {
                return;
            }

            IRecordCache cache = CochraneCMSPropertyNames.getRecordCacheOrNull();
            if (cache == null) {
                return;
            }

            IRecordManager rm = CochraneCMSBeans.getRecordManager();
            for (int id: ids) {

                RecordWrapper record = new RecordWrapper(id);
                boolean spd = CmsUtils.isScheduledIssue(record.getIssueId());
                if (!resetCacheOnly()) {
                    rm.cancelWhenReady(id, record.getName(), record.getNumber(), record.getDeliveryFile().getId());
                    cache.removeRecord(record.getName(), spd);

                } else if (!spd) {
                    cache.removeRecord(record.getName(), false);
                }
                logActivity(record.getId(), record.getName());
            }
        }

        protected void logActivity(int entityId, String entityName) {
            logActivity(LogEntity.EntityLevel.FLOW, ILogEvent.CANCEL_PROCESS, entityId, entityName);
        }

        protected boolean resetCacheOnly() {
            return false;
        }
    }

    /**
     *  Render processing
     */
    private static class RenderProcessor extends GenProcessor<GenProcessorParameters> {

        private final RenderingPlan renderPlan;

        RenderProcessor(GenProcessorParameters params, Map<String, String[]> requestParametersMap, RenderingPlan plan)
            throws CmsException {

            super(params, requestParametersMap);
            renderPlan = plan;
        }

        public void run() {

            Integer[] ids = getRecordsIds();
            if (ids == null || ids.length == 0) {
                return;
            }

            IEntireRenderingManager manager = EntireRenderingManager.Factory.getFactory().getInstance();
            if (manager == null) {
                return;
            }

            String dbTitle = params.getDbName();
            try {
                manager.startRendering(renderPlan, ids, dbTitle, isIncludePrevious());

            } catch (Exception e) {
                LOG.error(e);
            }
        }
    }

    /**
     * Process publish actions
     */
    private static class PublishProcessor extends GenProcessor<GenProcessorParameters> {

        private final Action action;
        private final boolean isAdmin;

        PublishProcessor(GenProcessorParameters params, Map<String, String[]> requestMap, Action action, boolean admin)
            throws CmsException {
            super(params, requestMap);

            this.action = action;
            isAdmin = admin;
        }

        public void run() {

            String[] df = requestParametersMap.get("df");
            int dfId = df == null ? 0 : Integer.parseInt(df[0]);

            if (dfId == 0) {
                publishRecords();
            } else if (action == Action.PUBLISHING_WR) {
                publishWhenReadyPackage(dfId);
            } else if (action == Action.ACKNOWLEDGEMENT_WR) {
                acknowledgeWhenReadyPackage(dfId);
            }
        }

        private boolean isAdmin() {
            return isAdmin;
        }

        private void acknowledgeWhenReadyPackage(int dfId) {
            DeliveryFileEntity de = ResultStorageFactory.getFactory().getInstance().getDeliveryFileEntity(dfId);
            String srcFolder = FilePathBuilder.JATS.getPathToPackage(de.getIssue().getId(), dfId);
            try {
                String importName = RecordHelper.findAriesImportSource(srcFolder, RepositoryFactory.getRepository());
                String manuscriptNumber = AriesHelper.getManuscriptNumberByImportName(importName);
                IRecordCache cache = CochraneCMSBeans.getRecordCache();
                RecordCache.ManuscriptValue value = cache.checkAriesRecordOnPublished(manuscriptNumber, null,
                        DbEntity.NOT_EXIST_ID, de.getId());
                Integer wrId = value.whenReadyId();
                if (wrId == null) {
                    PublishedAbstractEntity pe = CochraneCMSBeans.getPublishStorage().findWhenReadyByManuscriptNumber(
                        manuscriptNumber, de.getId(), cache.getLastDatabases().keySet());
                    wrId = pe == null ? null : pe.getId();
                }
                if (wrId != null) {
                    logActivity(LogEntity.EntityLevel.FILE, ILogEvent.PUBLISH, dfId, de.getName(), "ackId=" + dfId);
                    CochraneCMSBeans.getPublishService().sendAcknowledgementAriesOnPublish(de.getDb().getTitle(),
                            de.getDb().getId(), de.getId(), manuscriptNumber, wrId, true);
                } else {
                    LOG.warn("cannot find When Ready record by %s", manuscriptNumber);
                }

            } catch (Exception e) {
                LOG.error(e.getMessage());
            }
        }

        private void publishWhenReadyPackage(int dfId) {

            boolean realTime = requestParametersMap.containsKey("realTime");
            int dbId = params.getDbId();
            String dbTitle = getDbTitle();
            Res<BaseType> baseType = BaseType.find(dbTitle);
            if (Res.valid(baseType)) {
                try {
                    DeliveryFileEntity de = ResultStorageFactory.getFactory().getInstance().getDeliveryFileEntity(dfId);
                    logActivity(LogEntity.EntityLevel.FILE, ILogEvent.PUBLISH, dfId, de.getName(), "packageId=" + dfId);
                    PublishServiceFactory.getFactory().getInstance().publishWhenReady(
                        baseType.get(), dbId, dfId, generatePublishTypeList(baseType),
                            getHWFrequency(PubType.MAJOR_TYPE_S, PubType.TYPE_SEMANTICO, realTime), !realTime);
                } catch (Exception e) {
                    LOG.debug(e.getMessage());
                }
            }
        }

        private void publishRecords() {
            Integer[] ids = getRecordsIds();
            if (ids == null || ids.length == 0) {
                return;
            }

            int dbId = params.getDbId();
            boolean entire = !DbUtils.exists(dbId);

            RecordWrapper record = entire ? new EntireRecordWrapper() : new RecordWrapper();
            record.askEntity(ids[0], false);
            String dbTitle = record.getDbName();

            Res<BaseType> baseType = BaseType.find(dbTitle);
            dbId = entire ? baseType.get().getDbId() : dbId;
            try {
                List<PublishWrapper> list = new ArrayList<>();
                boolean onlyGenerate = generatePublishList(baseType, dbId, entire, list);
                if (!list.isEmpty()) {
                    publishList(list, dbId, dbTitle, entire, "initial count=" + ids.length, onlyGenerate);
                }
            } catch (Exception e) {
                LOG.error(e);
            }
        }

        private void publishList(List<PublishWrapper> list, int dbId, String dbName, boolean entire, String comment,
                                 boolean onlyGenerate) {
            int event = onlyGenerate ? ILogEvent.START_GENERATING : ILogEvent.PUBLISH;
            try {
                if (entire) {
                    logActivity(LogEntity.EntityLevel.ENTIREDB, event, dbId, dbName, comment);
                    PublishServiceFactory.getFactory().getInstance().publishEntireDb(dbName, list);
                } else {
                    list.forEach(pw -> pw.setTransactionId(UUIDEntity.UUID));
                    logActivity(LogEntity.EntityLevel.DB, event, dbId, dbName, comment);
                    PublishServiceFactory.getFactory().getInstance().publishDb(dbId, list);
                }
            } catch (Exception e) {
                LOG.debug(e.getMessage());
            }
        }

        private Set<String> generatePublishTypeList(Res<BaseType> baseType) {

            Set<String> majorTypes = action.getMajorPubTypes();
            Set<String> list = new HashSet<>();

            for (BaseType.PubInfo pi: baseType.get().getPubInfo()) {
                PubType pt = pi.getType();
                if (!pt.canSelective()) {
                    continue;
                }
                String majorType = pt.getMajorType();
                if (!majorTypes.contains(majorType)) {
                    continue;
                }
                String type = pt.getId();
                if (!containsSend(majorType, type)) {
                    continue;
                }
                list.add(type);
            }
            return list;
        }

        private boolean generatePublishList(Res<BaseType> baseType, int dbId, boolean entire,
                                            Collection<PublishWrapper> ret) throws Exception {
            Set<String> majorTypes = action.getMajorPubTypes();
            boolean canUnpack = baseType.get().canUnpack();
            Date startDate = new Date();

            PublishWrapper prev = null;

            boolean skipHW = skipPublishHW();
            PublishWrapper pwLit4HW = null;
            PublishWrapper pwHW = null;
            boolean onlyGenerate = true;
            boolean[] options = {false, true, false};
            for (BaseType.PubInfo pi: baseType.get().getPubInfo()) {

                PubType pt = pi.getType();
                if (!pt.canSelective()) {
                    continue;
                }

                String majorType = pt.getMajorType();
                if (!majorTypes.contains(majorType)) {
                    continue;
                }
                String type = pt.getId();

                boolean canGen = pt.canGenerate();
                boolean del = containsDelete(majorType, type);
                boolean gen = containsGenerate(majorType, type);
                boolean send = containsSend(majorType, type);
                String freq = getHWFrequency(majorType, type, false);

                if (PubType.MAJOR_TYPE_COCHRANE.equalsIgnoreCase(majorType)
                        && PubType.TYPE_COCHRANE_P.equalsIgnoreCase(type)) {
                    if (send) {
                        moveCochraneNotificatonToRetryFolder(dbId);
                        continue;
                    }
                    if (gen) {
                        continue;
                    }
                }
                if (!isToPublish(gen, send, del, canGen)) {
                    continue;
                }

                onlyGenerate = onlyGenerate && !send && !del;

                PublishWrapper pw = entire ? PublishWrapper.createEntirePublishWrapper(
                        pt.getId(), majorType, baseType.get().getId(), true, startDate)
                    : PublishWrapper.createIssuePublishWrapperEx(
                        pt.getId(), baseType.get().getId(), dbId, DbEntity.NOT_EXIST_ID, startDate, options);

                pw.setDelete(del);
                pw.setGenerate(canGen && gen);
                pw.setSend(send);
                pw.setHWFrequency(freq);

                pw.setStaticContentDisabled(containsStaticContentDisabled(majorType, type));
                if (canGen) {
                    pw.setNewPackageName(getNewPackageName(majorType, type));
                }

                boolean isLit = pt.isLiteratum();
                boolean isHw = PubType.TYPE_SEMANTICO.equals(type);

                if (skipHW) {
                    if (isLit) {
                        pwLit4HW = pw;
                    } else if (isHw) {
                        pwHW = pw;
                        continue;   // don't include HW feed to the list for publishing
                    }
                }
                addToPublishList(pw, type, prev, ret);
                prev = pw;
            }

            addHWPublish(baseType.get().isCentral() && !entire ? dbId : null, pwHW, pwLit4HW, prev, ret);

            return onlyGenerate;
        }

        private void moveCochraneNotificatonToRetryFolder(int dbId) {
            List<Integer> recordIds = new ArrayList<>(Arrays.asList(getRecordsIds()));
            for (Integer recordId : recordIds) {
                RecordEntity entity = getResultStorage().getRecord(recordId);
                String recordName = entity.getName();
                PublishedAbstractEntity pae =
                        CochraneCMSBeans.getPublishStorage().findArticleByDbAndName(entity.getDb().getId(), recordName);
                String notificationFileName = pae.getCochraneNotificationFileName();
                if (notificationFileName != null) {
                    IResultsStorage rs = ResultStorageFactory.getFactory().getInstance();
                    ClDbVO dbVo = new ClDbVO(rs.getDb(dbId));
                    String localPath = RevmanPackage.getRealFilePath(notificationFileName, dbVo);
                    CochraneSender.saveNotificationForResend(pae, localPath);
                } else {
                    String language = pae.getLanguage() != null ? "." + pae.getLanguage() : "";
                    String warnMessage = String.format("There is no content to process. "
                                    + "The Cochrane notification file does not exist for record %s.pub%s in DB",
                            pae.getRecordName(), pae.getPubNumber() + language);
                    LOG.warn(warnMessage);
                    MessageSender.sendWarnForMissingNotificationFileInDB(pae, warnMessage);
                }
            }
        }

        private void addHWPublish(Integer centralDbId, PublishWrapper pwHW, PublishWrapper pwLit4HW,
                                  PublishWrapper prev, Collection<PublishWrapper> list) throws Exception {
            if (pwHW != null) {
                if (pwLit4HW == null || !pwLit4HW.setPublishToAwait(pwHW)) {
                    addToPublishList(pwHW, PubType.TYPE_SEMANTICO, prev, list);
                }
                if (pwLit4HW != null) {
                    pwLit4HW.setHWFrequency(pwHW.getHWFrequency());
                }
                if (centralDbId != null && pwHW.getPublishToAwait() == null) {
                    pwHW.setPublishToAwait(PublishWrapper.createIssuePublishWrapper(PubType.TYPE_DS,
                        BaseType.getCentral().get().getId(), centralDbId, false, true, pwHW.getStartDate()), true);
                }
            }
        }

        private void addToPublishList(PublishWrapper pw, String type, PublishWrapper prev,
                                      Collection<PublishWrapper> list) throws Exception {
            pw.setRecordsProcessId(PublishHelper.createRecordPublishProcess(type, getRecordsIds()).getId());
            if (prev == null) {
                list.add(pw);
            } else {
                prev.setNext(pw);
            }
        }

        private static boolean isToPublish(boolean gen, boolean send, boolean del, boolean canGen) {
            return canGen ? gen : (send || del);
        }

        private boolean containsDelete(String majorType, String pubType) {
            return containsPublishOperation(majorType , pubType, "_delete_");
        }

        private boolean containsGenerate(String majorType, String pubType) {
            return containsPublishOperation(majorType , pubType, "_generate_");
        }

        private boolean containsSend(String majorType, String pubType) {
            return containsPublishOperation(majorType , pubType, "_send_");
        }

        private boolean containsStaticContentDisabled(String majorType, String pubType) {
            return containsPublishOperation(majorType , pubType, "_static_off_");
        }

        private boolean containsPublishOperation(String majorType, String pubType, String operation) {
            return requestParametersMap.containsKey(majorType + operation + pubType);
        }

        private String getHWFrequency(String majorType, String pubType, boolean realTime) {
            String[] strs =  requestParametersMap.get("PropertySelection");
            return strs == null ? (realTime ? HWFreq.REAL_TIME.getValue() : HWFreq.BULK.getValue())
                    : HWFreq.valueOf(strs[0]).getValue();
        }

        private boolean skipPublishHW() {
            return CochraneCMSPropertyNames.isPublishToSemanticoAfterLiteratum()
                    && containsSend(PubType.MAJOR_TYPE_LIT, PubType.TYPE_LITERATUM)
                    && containsSend(PubType.MAJOR_TYPE_S, PubType.TYPE_SEMANTICO);
        }

        private String getNewPackageName(String majorType, String pubType) {
            String paramKey = majorType + "_packageName_" + pubType;
            return requestParametersMap.containsKey(paramKey)
                    ? requestParametersMap.get(paramKey)[0]
                    : null;
        }
    }

    /** WML3G conversion */
    private static class Wml3gConversionProcessor extends GenProcessor<GenProcessorParameters> {

        Wml3gConversionProcessor(GenProcessorParameters params, Map<String, String[]> requestParametersMap)
                throws CmsException {
            super(params, requestParametersMap);
        }

        public void run() {
            Integer[] tmpIds = getRecordsIds();
            if (tmpIds == null || tmpIds.length == 0) {
                return;
            }
            List<Integer> recIds = Arrays.asList(tmpIds);

            IWml3gConversionManager convManager = getWml3gConversionManager();
            if (convManager == null) {
                return;
            }

            int dbId = AbstractManager.getResultStorage().getDatabaseEntity(params.getDbName()).getId();
            convManager.startConversion(dbId, recIds, isIncludePrevious(), params.getUserName());
        }
    }

    private static class ImportProcessor extends GenProcessor<GenProcessorParameters> {
        private final boolean restore;

        ImportProcessor(GenProcessorParameters params, Map<String, String[]> requestParametersMap, boolean restore)
                throws CmsException {
            super(params, requestParametersMap);
            this.restore = restore;
        }

        @Override
        public void run() {
            if (restore) {
                CochraneCMSBeans.getOperationManager().restoreRecords(params.getDbId(), getRecordsIds(),
                        params.getUserName());
            } else {
                CochraneCMSBeans.getOperationManager().importRecords(params.getDbId(), getRecordsIds(),
                        params.getUserName());
            }
        }
    }

    private static class ContentProcessor extends GenProcessor<GenProcessorParameters> {

        ContentProcessor(GenProcessorParameters params, Map<String, String[]> requestParametersMap)
                throws CmsException {
            super(params, requestParametersMap);
        }

        @Override
        public void run() {

            if (!hasRecordsIds()) {
                return;
            }

            ProcessingPlan processingPlan = new ProcessingPlan(requestParametersMap);
            boolean withPrevious = isIncludePrevious();
            if (processingPlan.hasOnly(Action.WML3G_CONVERSION)) {
                List<Integer> recIds = Arrays.asList(getRecordsIds());
                int dbId = AbstractManager.getResultStorage().getDatabaseEntity(params.getDbName()).getId();
                getWml3gConversionManager().startConversion(dbId, recIds, withPrevious, params.getUserName());
            } else {
                startContentProcessing(processingPlan, withPrevious);
            }
        }

        private void startContentProcessing(ProcessingPlan processingPlan, boolean withPrevious) {
            try {
                String dbName = params.getDbName();
                ProcessVO first = null;
                BaseType bt = BaseType.find(dbName).get();

                if (processingPlan.has(Action.RENDERING_PDF_FOP)) {
                    first = RenderingHelper.createSelectiveRenderingProcess(dbName, RenderingPlan.PDF_FOP, withPrevious,
                            getRecordsIds(), getUserName(), null);
                }

                if (processingPlan.has(Action.WML3G_CONVERSION)) {
                    int dbId = AbstractManager.getResultStorage().getDatabaseEntity(params.getDbName()).getId();
                    List<Integer> recIds = Arrays.asList(getRecordsIds());
                    first = getWml3gConversionManager().prepareConversion(ProcessType.find(
                            ICMSProcessManager.PROC_TYPE_ML21_TO_ML3G_SELECTIVE).get(), dbId, dbName,
                            recIds, withPrevious, getUserName(), first);
                }

                if (processingPlan.has(Action.RENDERING_HTML)) {
                    first = RenderingHelper.createSelectiveRenderingProcess(dbName, RenderingPlan.HTML, withPrevious,
                            getRecordsIds(), getUserName(), first);
                }

                if (processingPlan.has(Action.UPDATE_MESH_WML3G)) {
                    first = createWml3gMeshUpdateProcess(bt, withPrevious, first);
                }

                first = checkJatsProcess(bt, withPrevious, first, processingPlan);

                boolean hasRevmanTaConv = processingPlan.has(Action.CONVERT_REVMAN_TA);
                boolean hasRevmanConv = processingPlan.has(Action.CONVERT_REVMAN);
                if (hasRevmanConv || hasRevmanTaConv) {
                    int taMode = hasRevmanConv && hasRevmanTaConv ? ConversionRecordHandler.WITH_SPECIAL
                            : (hasRevmanTaConv ? ConversionRecordHandler.ONLY_SPECIAL : 0);
                    first = createConversionProcess(bt, taMode, withPrevious, first);
                } 
                if (first != null) {
                    CochraneCMSBeans.getCMSProcessManager().startProcess(first);
                } else {
                    LOG.warn("nothing to process...");
                }

            } catch (Exception e) {
                LOG.error(e);
            }
        }

        private ProcessVO createWml3gMeshUpdateProcess(BaseType bt, boolean withPrevious, ProcessVO first)
                throws Exception {
            String dbName = bt.getId();
            DbHandler dbHandler = new DbHandler(0, dbName, bt.getDbId(), withPrevious);
            Wml3gValidationHandler ml3gh = new Wml3gValidationHandler(dbHandler);
            ProcessType pt = ProcessType.find(ICMSProcessManager.PROC_TYPE_ML3G_MESH_UPDATE_SELECTIVE).get();

            return ProcessHelper.createIdPartsProcess(ml3gh, pt, pt.getPriority(), params.getUserName(),
                    getRecordsIds(), first != null ? first.getId() : DbEntity.NOT_EXIST_ID, pt.batch());
        }

        private ProcessVO createConversionProcess(BaseType bt, int ta, boolean withPrevious, ProcessVO first)
                throws Exception {
            ProcessType pt = ProcessType.find(ICMSProcessManager.PROC_TYPE_RM_TO_WML21_SELECTIVE).get();
            ConversionRecordHandler ch = new ConversionRecordHandler(pt.getName(), bt.getId(), withPrevious, ta);

            return ProcessHelper.createIdPartsProcess(ch, pt, pt.getPriority(), params.getUserName(),
                    getRecordsIds(), first != null ? first.getId() : DbEntity.NOT_EXIST_ID, pt.batch());
        }

        private ProcessVO checkJatsProcess(BaseType bt, boolean withPrevious, ProcessVO first, ProcessingPlan plan)
                throws Exception {
            boolean hasJatsConv = plan.has(Action.CONVERT_JATS);
            boolean hasJatsTaConv = plan.has(Action.CONVERT_JATS_TA);
            if (!hasJatsConv && !hasJatsTaConv) {
                return first;
            }
            int taMode = hasJatsConv && hasJatsTaConv ? ConversionRecordHandler.WITH_SPECIAL
                    : (hasJatsTaConv ? ConversionRecordHandler.ONLY_SPECIAL : 0);
            String dbName = bt.getId();
            DbHandler dbHandler = new DbHandler(0, dbName, bt.getDbId(), withPrevious);
            JatsConversionHandler jh = new JatsConversionHandler(dbHandler, taMode);
            ProcessType pt = ProcessType.find(ICMSProcessManager.PROC_TYPE_CONVERT_JATS).get();

            return ProcessHelper.createIdPartsProcess(jh, pt, pt.getPriority(), params.getUserName(),
                    getRecordsIds(), first != null ? first.getId() : DbEntity.NOT_EXIST_ID, pt.batch());
        }
    }

    /**
     *
     */
    static class ProcessingPlan {
        static final String OPERATION_PREFIX = "action_";
        final Set<Action> operations;

        ProcessingPlan(Map<String, String[]> requestParameters) {
            this.operations = requestParameters.keySet().stream()
                    .filter(it -> it.startsWith(OPERATION_PREFIX))
                    .map(it -> StringUtils.substringAfter(it, OPERATION_PREFIX))
                    .map(Integer::parseInt)
                    .map(Action::getByOrdinal)
                    .collect(Collectors.toSet());
        }

        boolean hasOnly(Action operation) {
            return has(operation) && operations.size() == 1;
        }

        boolean has(Action operation) {
            return operations.contains(operation);
        }
    }
}