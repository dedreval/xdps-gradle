package com.wiley.cms.cochrane.process;

import com.wiley.cms.cochrane.activitylog.ActivityLogEntity;
import com.wiley.cms.cochrane.activitylog.ILogEvent;
import com.wiley.cms.cochrane.cmanager.CmsUtils;
import com.wiley.cms.cochrane.cmanager.CochraneCMSProperties;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.FilePathBuilder;
import com.wiley.cms.cochrane.cmanager.FilePathCreator;
import com.wiley.cms.cochrane.cmanager.MessageSender;
import com.wiley.cms.cochrane.cmanager.data.RecordPublishEntity;
import com.wiley.cms.cochrane.cmanager.data.db.DbVO;
import com.wiley.cms.cochrane.cmanager.data.entire.IEntireDBStorage;
import com.wiley.cms.cochrane.cmanager.data.issue.IssueVO;
import com.wiley.cms.cochrane.cmanager.data.record.IRecordStorage;
import com.wiley.cms.cochrane.cmanager.entitymanager.AbstractManager;
import com.wiley.cms.cochrane.cmanager.entitywrapper.DbWrapper;
import com.wiley.cms.cochrane.cmanager.entitywrapper.SearchEntireRecordOrder;
import com.wiley.cms.cochrane.cmanager.entitywrapper.SearchRecordStatus;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.converter.ml3g.Wml3gConversionProcessPartParameters;
import com.wiley.cms.cochrane.converter.ml3g.Wml3gConversionQueue;
import com.wiley.cms.cochrane.process.handler.Wml3gConversionHandler;
import com.wiley.cms.cochrane.repository.IRepository;
import com.wiley.cms.cochrane.utils.Constants;
import com.wiley.cms.cochrane.utils.ContentLocation;
import com.wiley.cms.process.AbstractBeanFactory;
import com.wiley.cms.process.IProcessManager;
import com.wiley.cms.process.ProcessException;
import com.wiley.cms.process.ProcessHandler;
import com.wiley.cms.process.ProcessHelper;
import com.wiley.cms.process.ProcessState;
import com.wiley.cms.process.ProcessVO;
import com.wiley.cms.process.entity.DbEntity;
import com.wiley.cms.process.res.ProcessType;
import com.wiley.tes.util.Logger;
import org.apache.commons.lang.StringUtils;

import javax.annotation.Resource;
import javax.ejb.EJB;
import javax.ejb.Local;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.jms.Queue;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @author <a href='mailto:aasadulin@wiley.com'>Alexander Asadulin</a>
 * @version 01.09.2014
 */
@Stateless
@Local(IWml3gConversionManager.class)
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class Wml3gConversionManager extends Wml3gManager implements IWml3gConversionManager {

    private static final Logger LOG = Logger.getLogger(Wml3gConversionManager.class);
    private static final String REC_EMPTY_MSG = "no successfully rendered records available for the conversion";
    private static final String PROC_ID_MSG = "; process id ";

    @EJB
    private IRecordStorage recStorage;

    @EJB
    private IEntireDBStorage entStorage;

    @Resource(mappedName = Wml3gConversionQueue.DESTINATION_QUEUE)
    private Queue destQueue;

    public ProcessVO createProcess(int parentId,
                              int dbId,
                              List<Integer> recIds,
                              boolean previous,
                              String logName) throws ProcessException {
        String dbName = BaseType.getDbName(dbId);
        Wml3gConversionHandler handler = Wml3gConversionHandler.createWml3gConversionHandler(dbId, dbName, logName,
                recIds != null, previous);
        ProcessVO pvo = ps.createProcess(parentId, handler, ProcessType.find(
            ICMSProcessManager.PROC_TYPE_ML21_TO_ML3G_SELECTIVE).get(), IProcessManager.USUAL_PRIORITY, logName);
        try {
            createProcessPartsEntire(pvo, dbId, dbName, recIds);
            if (previous && dbName.equals(CochraneCMSPropertyNames.getCDSRDbName())) {
                createProcessPartsPrevious(pvo, recIds);
            }
        } catch (ProcessException e) {
            String err = String.format("Failed to create process parts of process %s, %s", pvo, e);
            LOG.error(err, e);
            throw new ProcessException(err);
        }
        return pvo;
    }

    public void startConversion(IssueVO issueVO, int dbId, String logName) {
        String qualifier = issueVO + "; " + new DbVO(new DbWrapper(dbId).getEntity()).getTitle();
        String tmpLogName = getLoginName(logName);
        ActivityLogEntity.EntityLevel entLvl = ActivityLogEntity.EntityLevel.DB;
        ProcessVO pvo = createProcess(dbId, qualifier, tmpLogName, entLvl);
        int batchSize = getBatchSize();
        int offset = 0;
        boolean empty;

        List<Integer> recIds =
                recStorage.getDbRecordIdList(dbId, null, SearchRecordStatus.RENDER_PASSED, offset, batchSize);
        empty = recIds.isEmpty();
        try {
            while (!recIds.isEmpty()) {
                recStorage.updateRecordPublishStateByRecordIds(new Date(), RecordPublishEntity.CONVERSION, recIds);
                createProcessPart(pvo.getId(), issueVO, ContentLocation.ISSUE, Constants.UNDEF, recIds);

                offset += batchSize;
                recIds = recStorage.getDbRecordIdList(dbId, null, SearchRecordStatus.RENDER_PASSED, offset, batchSize);
            }
        } catch (ProcessException e) {
            handleError(pvo, dbId, qualifier, tmpLogName, entLvl, "failed to create process part " + e);
            return;
        }

        if (empty) {
            handleError(pvo, dbId, qualifier, tmpLogName, entLvl, REC_EMPTY_MSG);
        } else {
            startProcess(pvo, dbId, qualifier, tmpLogName, entLvl);
        }
    }

    public void startConversion(int dbId, String logName) {
        startConversion(dbId, null, false, logName);
    }

    public ProcessVO prepareConversion(ProcessType type, int dbId, String dbName, List<Integer> recs, boolean previous,
                                       String logName, ProcessVO nextProcess) {
        Wml3gConversionHandler handler = Wml3gConversionHandler.createWml3gConversionHandler(dbId, dbName, logName,
                    recs != null, previous);
        String qualifier = handler.getQualifier();
        ProcessVO pvo = ps.createProcess(DbEntity.NOT_EXIST_ID, handler, type, IProcessManager.USUAL_PRIORITY, logName);

        try {
            if (nextProcess != null) {
                pvo.setNextId(nextProcess.getId());
                ps.setNextProcess(pvo);
            }

            createProcessPartsEntire(pvo, dbId, dbName, recs);
            if (previous && dbName.equals(CochraneCMSPropertyNames.getCDSRDbName())) {
                createProcessPartsPrevious(pvo, recs);
            }
        } catch (ProcessException e) {
            handleError(pvo, dbId, qualifier, handler.getLogName(), handler.getEntityLevel(), e.toString());
            return null;
        }
        return pvo;
    }

    public void startConversion(int dbId, List<Integer> recIds, boolean previous, String logName) {
        String dbName = BaseType.getDbName(dbId);
        Wml3gConversionHandler handler = Wml3gConversionHandler.createWml3gConversionHandler(dbId, dbName, logName,
                recIds != null, previous);
        String tmpLogName = handler.getLogName();
        String qualifier = handler.getQualifier();
        ProcessVO pvo = ps.createProcess(DbEntity.NOT_EXIST_ID, handler, ProcessType.empty(),
                IProcessManager.USUAL_PRIORITY, logName);
        try {
            createProcessPartsEntire(pvo, dbId, dbName, recIds);
            if (previous && dbName.equals(CochraneCMSPropertyNames.getCDSRDbName())) {
                createProcessPartsPrevious(pvo, recIds);
            }
        } catch (ProcessException e) {
            handleError(pvo, dbId, qualifier, tmpLogName, handler.getEntityLevel(), e.toString());
            return;
        }
        startProcess(pvo, dbId, qualifier, tmpLogName, handler.getEntityLevel());
    }

    private ProcessVO createProcess(int dbId,
                                    String qualifier,
                                    String logName,
                                    ActivityLogEntity.EntityLevel entLvl) {
        ProcessHandler handler = new Wml3gConversionHandler(dbId, qualifier, logName, entLvl);
        return ps.createProcess(DbEntity.NOT_EXIST_ID, handler, ProcessType.empty(),
                IProcessManager.USUAL_PRIORITY, logName);
    }

    private void logConversionStarted(int dbId,
                                      int procId,
                                      String qualifier,
                                      String logName,
                                      ActivityLogEntity.EntityLevel entLvl) {
        LOG.debug("Conversion to WML3G started; " + qualifier +  PROC_ID_MSG + procId);
        logService.info(entLvl, ILogEvent.CONVERSION_TO_3G_STARTED, dbId, qualifier, logName,
                ProcessHelper.buildProcessMsg(procId));
    }

    private void createProcessPartsEntire(ProcessVO pvo,
                                          int dbId,
                                          String dbName,
                                          List<Integer> recIds) throws ProcessException {
        int batchSize = getBatchSize();
        int offset;
        boolean empty = true;

        List<Integer> issues = getIssues(dbId, dbName);
        for (Integer issue : issues) {
            offset = 0;

            int numb = CmsUtils.getIssueByIssueNumber(issue);
            int year = CmsUtils.getYearByIssueNumber(issue);
            IssueVO issueVO = new IssueVO(year, numb, null);

            List<Integer> recIdsBelongIssue = getRecordIdsBelongsToIssue(issue, recIds);
            List<Integer> tmpRecIds = getSubRecordIds(batchSize, offset, recIdsBelongIssue, dbName, issue);

            empty = empty && tmpRecIds.isEmpty();
            while (!tmpRecIds.isEmpty()) {
                entStorage.updateRecordPublishStateByRecordIds(new Date(), RecordPublishEntity.CONVERSION, tmpRecIds);
                createProcessPart(pvo.getId(), issueVO, ContentLocation.ENTIRE, Constants.UNDEF, tmpRecIds);

                offset += batchSize;
                tmpRecIds = getSubRecordIds(batchSize, offset, recIdsBelongIssue, dbName, issue);
            }
        }
        if (empty) {
            throw new ProcessException(REC_EMPTY_MSG);
        }
    }

    private List<Integer> getIssues(int dbId, String dbName) {
        List<Integer> issues;
        if (dbName.equals(CochraneCMSPropertyNames.getCentralDbName())) {
            issues = entStorage.getLastIssuePublishedList(dbId);
        } else {
            issues = new ArrayList<Integer>(1);
            issues.add(Constants.UNDEF);
        }

        return issues;
    }

    private List<Integer> getRecordIdsBelongsToIssue(int issue, List<Integer> recIds) {
        if (issue == Constants.UNDEF || recIds == null) {
            return recIds;
        }

        int batchSize = CochraneCMSPropertyNames.getDbRecordBatchSize();
        int offset = 0;
        List<Integer> recIdsBelongIssue = new ArrayList<Integer>();

        List<Integer> tmpRecIds = getSubList(batchSize, offset, recIds);
        while (!tmpRecIds.isEmpty()) {
            recIdsBelongIssue.addAll(entStorage.getIdsBelongToIssue(issue, tmpRecIds));

            offset += batchSize;
            tmpRecIds = getSubList(batchSize, offset, recIds);
        }

        return recIdsBelongIssue;
    }

    private List<Integer> getSubRecordIds(int batchSize, int offset, List<Integer> recIds, String dbName, int issue) {
        if (recIds == null) {
            return entStorage.getRecordIds(dbName, offset, batchSize, null, null, SearchEntireRecordOrder.NONE, false,
                    null, issue);
        } else {
            return getSubList(batchSize, offset, recIds);
        }
    }

    private void createProcessPartsPrevious(ProcessVO pvo, List<Integer> recIds) throws ProcessException {
        IssueVO issueVO = new IssueVO();
        IRepository rep = AbstractManager.getRepository();
        File[] verDirs = rep.getFilesFromDir(FilePathBuilder.getPathToPrevious());
        int batchSize = getBatchSize();
        int offset = 0;

        if (verDirs == null || verDirs.length == 0) {
            return;
        }

        List<String> entRecNames = getRecordNames(batchSize, offset, recIds);
        while (!entRecNames.isEmpty()) {
            for (File versionDir : verDirs) {
                if (versionDir.isFile()) {
                    continue;
                }

                int version = Integer.parseInt(versionDir.getName().replaceFirst("\\D+", ""));
                List<String> prevRecNames = getExistingPreviousRecordNames(entRecNames, version, rep);
                if (!prevRecNames.isEmpty()) {
                    createProcessPart(pvo.getId(), issueVO, ContentLocation.PREVIOUS, version, prevRecNames);
                }
            }

            offset += batchSize;
            entRecNames = getRecordNames(batchSize, offset, recIds);
        }
    }

    private List<String> getRecordNames(int batchSize, int offset, List<Integer> recIds) {
        List<Integer> subRecIds = getSubList(batchSize, offset, recIds);
        if (subRecIds.isEmpty()) {
            return new ArrayList<String>(0);
        } else {
            return entStorage.findRecordNames(subRecIds);
        }
    }

    private List<Integer> getSubList(int batchSize, int offset, List<Integer> recIds) {
        if (recIds == null || recIds.size() <= offset) {
            return new ArrayList<Integer>(0);
        } else {
            return recIds.subList(offset, Math.min(recIds.size(), offset + batchSize));
        }
    }

    private List<String> getExistingPreviousRecordNames(List<String> entRecNames, int version, IRepository rep) {
        List<String> prevRecNames = new ArrayList<String>();
        for (String entRecName : entRecNames) {
            try {
                if (rep.isFileExists(FilePathCreator.getPreviousSrcPath(entRecName, version))) {
                    prevRecNames.add(entRecName);
                }
            } catch (IOException e) {
            }
        }

        return prevRecNames;
    }

    private void createProcessPart(int procId,
                                   IssueVO issueVO,
                                   ContentLocation contLoc,
                                   int version,
                                   List<?> procEntities) throws ProcessException {
        Wml3gConversionProcessPartParameters procPartParams =
                new Wml3gConversionProcessPartParameters(issueVO, contLoc, version, procEntities);
        String params = procPartParams.getParameters();
        try {
            ps.createProcessPart(procId, StringUtils.EMPTY, params, ProcessState.NONE);
        } catch (ProcessException e) {
            throw new ProcessException(String.format("Failed to create process part for process %s; %s.", procId, e));
        }
    }

    private void startProcess(ProcessVO pvo,
                              int dbId,
                              String qualifier,
                              String logName,
                              ActivityLogEntity.EntityLevel entLvl) {
        startProcess(pvo.getHandler(), pvo);
        if (pvo.getState() == ProcessState.FAILED) {
            handleError(pvo, dbId, qualifier, logName, entLvl, "Failed to start process " + pvo.getId());
        }
    }

    private void handleError(ProcessVO pvo,
                             int dbId,
                             String qualifier,
                             String logName,
                             ActivityLogEntity.EntityLevel entLvl,
                             String errCause) {
        String err = getErrorMessage(pvo.getId(), qualifier, errCause);
        LOG.error(err);
        logService.error(entLvl, ILogEvent.CONVERSION_TO_3G_FAILED, dbId, qualifier, logName, err);
        MessageSender.sendWml3gConversion(qualifier, err);
        ps.deleteProcess(pvo.getId());
    }

    private String getErrorMessage(int procId, String qualifier, String errCause) {
        return getErrorMessage(procId, qualifier, "Conversion to WML3G failed", errCause);
    }

    @Override
    protected void onStart(ProcessHandler handler, ProcessVO pvo) throws ProcessException {
        Wml3gConversionHandler wh = ProcessHandler.castProcessHandler(pvo.getHandler(), Wml3gConversionHandler.class);
        startConversion(wh, pvo);
    }


    public void startConversion(Wml3gConversionHandler wh, ProcessVO pvo) throws ProcessException {
        logConversionStarted(wh.getDbId(), pvo.getId(), wh.getQualifier(), wh.getLogName(), wh.getEntityLevel());
        processNextPart(pvo, getThreadCount());
    }

    @Override
    protected void onNextPart(ProcessVO pvo, ArrayList<Integer> partIds) throws ProcessException {
        onNextPart(pvo, partIds, destQueue);
    }

    @Override
    protected void onFail(ProcessVO pvo, Throwable th) {
        super.onFail(pvo, th);

        String err = "Failed to end process, " + th;
        LOG.error("Conversion to WML3G failed; " + err);
        MessageSender.sendWml3gConversion("Process id " + pvo.getId(), err);
    }

    @Override
    protected void onEnd(ProcessVO pvo) throws ProcessException {
        Wml3gConversionHandler handler =
                ProcessHandler.castProcessHandler(pvo.getHandler(), Wml3gConversionHandler.class);
        String stat = getStatistic(pvo.getId(), "Records converted").toString();
        String qualifierPlusStat = handler.getQualifier() + "; " + stat;

        LOG.debug("Conversion to WML3G completed; " + qualifierPlusStat +  PROC_ID_MSG + pvo.getId());
        logService.info(handler.getEntityLevel(), ILogEvent.CONVERSION_TO_3G_COMPLETED, handler.getDbId(),
                handler.getQualifier(), handler.getLogName(), ProcessHelper.buildProcessMsg(pvo.getId()) + "; " + stat);
        MessageSender.sendWml3gConversion(qualifierPlusStat, null);

        super.onEnd(pvo);
        if (pvo.hasCreator()) {
            stopCreator(getProcess(pvo.getCreatorId()), true);
        }
    }

    public static int getBatchSize() {
        return Integer.parseInt(CochraneCMSProperties.getProperty("cms.cochrane.wml3g_conversion.batch_size"));
    }

    public static int getThreadCount() {
        return Integer.parseInt(
                CochraneCMSProperties.getProperty("cms.cochrane.wml3g_conversion.thread_count_by_process", "10"));
    }

    @Override
    protected String getRecordNamesMessage(List<String> recNames) {
        return "; following records were not converted because they have no proper source format " + recNames;
    }

    /**
     *
     */
    public static class Factory extends AbstractBeanFactory<IWml3gConversionManager> {

        public static final String LOOKUP_NAME = CochraneCMSPropertyNames.buildLookupName(
                        "Wml3gConversionManager", IWml3gConversionManager.class);
        private static final Factory FACTORY_INSTANCE = new Factory();

        private Factory() {
            super(LOOKUP_NAME);
        }

        public static IWml3gConversionManager getBeanInstance() {
            return FACTORY_INSTANCE.getInstance();
        }
    }
}
