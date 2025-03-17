package com.wiley.cms.cochrane.process;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Resource;
import javax.ejb.EJB;
import javax.ejb.Local;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.jms.Queue;

import com.wiley.cms.cochrane.activitylog.ILogEvent;
import com.wiley.cms.cochrane.cmanager.CochraneCMSBeans;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.FilePathBuilder;
import com.wiley.cms.cochrane.cmanager.FilePathCreator;
import com.wiley.cms.cochrane.cmanager.MessageSender;
import com.wiley.cms.cochrane.cmanager.contentworker.TranslatedAbstractsPackage;
import com.wiley.cms.cochrane.cmanager.data.entire.IEntireDBStorage;
import com.wiley.cms.cochrane.cmanager.translated.TranslatedAbstractVO;
import com.wiley.cms.cochrane.cmanager.translated.TranslatedAbstractsHelper;
import com.wiley.cms.cochrane.converter.ml3g.Wml3gConversionQueue;
import com.wiley.cms.cochrane.process.handler.ConversionRecordHandler;
import com.wiley.cms.cochrane.repository.RepositoryFactory;
import com.wiley.cms.cochrane.repository.RepositoryUtils;
import com.wiley.cms.cochrane.utils.ErrorInfo;
import com.wiley.cms.process.res.ProcessType;
import com.wiley.tes.util.Pair;
import com.wiley.cms.converter.services.IConversionProcess;
import com.wiley.cms.converter.services.RevmanMetadataHelper;
import com.wiley.cms.process.IProcessManager;
import com.wiley.cms.process.ProcessException;
import com.wiley.cms.process.ProcessHandler;
import com.wiley.cms.process.ProcessHelper;
import com.wiley.cms.process.ProcessVO;
import com.wiley.cms.process.entity.DbEntity;
import com.wiley.tes.util.Extensions;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 10.09.13
 */
@Stateless
@Local(IConversionManager.class)
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class ConversionManager extends BaseManager implements IConversionManager {

    public static final String LOOKUP_NAME = CochraneCMSPropertyNames.buildLookupName(
                    "ConversionManager", IConversionManager.class);
    private static final int MIN_ASYNC_NUMBER = 10;

    @EJB(beanName = "EntireDBStorage")
    private IEntireDBStorage edbs;

    @EJB(beanName = "ConversionProcessImpl")
    private IConversionProcess cp;

    @Resource(mappedName = Wml3gConversionQueue.DESTINATION_QUEUE)
    private Queue convQueue;

    @Deprecated
    public void startRevmanConversion(Integer[] recordIds, int translations, boolean withPrevious,
                                      String logName, boolean async) {
        startRevmanConversion(recordIds, translations, withPrevious, logName, async, DbEntity.NOT_EXIST_ID);
    }

    @Deprecated
    public void startRevmanConversion(Integer[] recIds, int translations, boolean withPrevious,
                                      String logName, boolean async, int nextId) {
        String dbName = CochraneCMSPropertyNames.getCDSRDbName();
        ConversionRecordHandler ph = new ConversionRecordHandler(LABEL_CONV_RECORDS, dbName, withPrevious,
                translations);
        startConversion(ph, dbName, recIds, logName, async, nextId);
    }

    @Deprecated
    private void startConversion(ConversionRecordHandler ph, String dbName, Integer[] ids, String logName,
        boolean async, int nextId) {
        ProcessVO pvo = null;
        try {
            int batch = CochraneCMSPropertyNames.getRevmanConversionBatchSize();
            pvo = ProcessHelper.createIdPartsProcess(ph, ProcessType.empty(), IProcessManager.LOW_PRIORITY, logName,
                    ids, nextId, batch);
            if (async && ids.length >= MIN_ASYNC_NUMBER) {

                asynchronousStart(pvo, LABEL_CONV_RECORDS, LOOKUP_NAME,
                        CochraneCMSBeans.getQueueProvider().getTaskQueue());
                logStart(pvo.getId(), dbName, ids.length, false, ", async", logName,
                        ILogEvent.CONVERSION_REVMAN_STARTED);

            } else {
                logStart(pvo.getId(), dbName, ids.length, false, "", logName, ILogEvent.CONVERSION_REVMAN_STARTED);
                startProcess(ph, pvo);
            }
        } catch (Exception e) {
            logFail(pvo == null ? DbEntity.NOT_EXIST_ID : pvo.getId(), dbName, false, e.getMessage(), logName,
                    ILogEvent.CONVERSION_REVMAN_FAILED);
            LOG.error(e.getMessage(), e);
        }
    }

    @Deprecated
    @Override
    protected void onNextPart(ProcessVO pvo, ArrayList<Integer> partIds) throws ProcessException {
        sendProcessPart(pvo, partIds, convQueue);
    }

    @Deprecated
    @Override
    public void onStart(ProcessHandler handler, ProcessVO pvo) throws ProcessException {
        LOG.info(String.format("%s is starting", pvo));
        String dbName = null;
        int procId = pvo.getId();
        ConversionRecordHandler chandler = null;
        try {
            chandler = ProcessHandler.castProcessHandler(handler, ConversionRecordHandler.class);
            dbName = chandler.getDbName();
            startProcessing(pvo, CochraneCMSPropertyNames.getRevmanConversionThreadCount());

        } catch (ProcessException pe) {
            logErrorProcessEntire(procId, dbName, getErrorLogEvent(chandler), pe.getMessage(), true);
            throw pe;
            
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
            logErrorProcessEntire(procId, dbName, getErrorLogEvent(chandler), ErrorInfo.Type.SYSTEM.getMsg(),
                    true);
            throw new ProcessException(e.getMessage());
        }
    }

    @Deprecated
    private int startProcessing(ProcessVO pvo, int sessionCount) throws Exception {
        int all = ps.getUnFinishedProcessPartCount(pvo.getId());
        sendProcessParts(pvo, pvo.getType(), sessionCount, 1, convQueue);
        return all;
    }

    private void initPreviousDir(List<Pair<String, String>> prevDirs, List<Pair<String, String>> prevDirsTA) {

        String prevBaseDir = FilePathBuilder.getPathToPrevious();
        File[] versions = RepositoryFactory.getRepository().getFilesFromDir(prevBaseDir);
        if (versions == null) {
            return;
        }
        for (File fl: versions) {

            if (fl.isFile()) {
                continue;
            }

            String flName = fl.getName();
            if (prevDirs != null) {

                String src = FilePathBuilder.getPathToPreviousRevman(flName);
                String destPrev = FilePathBuilder.getPathToReconvertedPreviousSrc(flName);
                Pair<String, String> pair = new Pair<>(src, destPrev);
                prevDirs.add(pair);
            }

            if (prevDirsTA != null) {
                String src = FilePathBuilder.TR.getPathToPreviousTA(flName);
                Pair<String, String> pair = new Pair<>(src, null);
                prevDirsTA.add(pair);
            }
        }
    }

    public void performRevmanConversion(int processId, String dbName, List<Integer> recordIds, boolean withPrevious,
            boolean withTa, boolean onlyTa) throws Exception {

        String revmanDir = FilePathBuilder.getPathToEntireRevman();
        String destSrc = FilePathBuilder.getPathToReconvertedEntireSrc(dbName);

        List<Pair<String, String>> prevDirs = null;
        List<Pair<String, String>> prevDirsTA = null;
        if (withPrevious) {
            if (!onlyTa) {
                prevDirs = new ArrayList<>();
            }
            if (withTa) {
                prevDirsTA = new ArrayList<>();
            }
            initPreviousDir(prevDirs, prevDirsTA);
        }

        if (withTa) {
            performConvertingWithTranslations(processId, recordIds, prevDirsTA);
            LOG.info(String.format("ta re-conversion, [%d] - %d records passed", processId, recordIds.size()));
        }
        if (!onlyTa) {
            performConverting(processId, recordIds, revmanDir, destSrc, prevDirs);
            LOG.info(String.format("revman re-conversion, [%s] - %d records passed", processId, recordIds.size()));
        }
    }

    private int getErrorLogEvent(ConversionRecordHandler chandler) {
        return chandler == null ? ILogEvent.EXCEPTION : ILogEvent.CONVERSION_REVMAN_FAILED;
    }

    private void performConverting(int processId, List<Integer> ids, String revmanDir, String destination,
        List<Pair<String, String>> prevDirs) throws Exception {

        List<String> names = edbs.findRecordNames(ids);
        Set<String> includedNames = new HashSet<>(names.size());
        includedNames.addAll(names);

        checkErrors(cp.convert(revmanDir, destination, includedNames), processId);

        if (prevDirs == null) {
            return;
        }
        for (Pair<String, String> pair: prevDirs) {
            try {
                checkErrors(cp.convert(pair.first, pair.second, includedNames), processId);

            } catch (Exception e) {
                LOG.debug(e.getMessage());
            }
        }
    }

    private void performConvertingWithTranslations(int processId, List<Integer> ids, List<Pair<String,
            String>> prevDirs) throws Exception {

        List<String> names = edbs.findRecordNames(ids);
        Map<String, List<Pair<String, String>>> map = TranslatedAbstractsHelper.getAbstractPaths(
                FilePathBuilder.TR.getPathToTA(), names, null);
        if (map != null) {

            for (String lang : map.keySet()) {
                List<Pair<String, String>> list = map.get(lang);
                if (list == null || list.isEmpty()) {
                    continue;
                }

                String trDir = FilePathBuilder.TR.getPathToTA(lang);
                String destDir = FilePathBuilder.TR.getPathToWML21TA(lang);

                Map<String, TranslatedAbstractVO> taMap = prepareTranslations(lang, list);
                checkErrors(cp.convertTranslations(trDir, destDir, taMap, null, null, null), processId);
            }
        }

        if (prevDirs == null) {
            return;
        }
        for (Pair<String, String> pair: prevDirs) {
            String path = pair.first;
            if (path.endsWith(FilePathCreator.SEPARATOR)) {
                path = path.substring(0, path.length() - 1);
            }
            Integer version = FilePathBuilder.extractPreviousNumber(RepositoryUtils.getLastNameByPath(path));
            Map<String, List<Pair<String, String>>> mapPrev = TranslatedAbstractsHelper.getAbstractPaths(
                    pair.first, names, version);
            if (mapPrev == null) {
                continue;
            }

            for (String lang : mapPrev.keySet()) {
                List<Pair<String, String>> list = mapPrev.get(lang);
                if (list == null || list.isEmpty()) {
                    continue;
                }
                String trDir = FilePathBuilder.TR.getPathToPreviousTA(version, lang);
                String destDir = FilePathBuilder.TR.getPathToPreviousWML21TA(version, lang);
                Map<String, TranslatedAbstractVO> taMap = prepareTranslations(lang, list);
                checkErrors(cp.convertTranslations(trDir, destDir, taMap, null, null, null), processId);
            }
        }
    }

    private Map<String, TranslatedAbstractVO> prepareTranslations(String lang, List<Pair<String, String>> list) {
        Map<String, TranslatedAbstractVO> ret = new HashMap<>();
        for (Pair<String, String> ta: list) {
            String path = ta.second;
            if (path.endsWith(FilePathCreator.SEPARATOR)) {
                path = path.substring(0, path.length() - 1);
            }
            String fileName = RepositoryUtils.getLastNameByPath(path);
            TranslatedAbstractVO vo = new TranslatedAbstractVO(
                    TranslatedAbstractsPackage.parseLanguage(fileName).first, lang);
            vo.setPath(fileName);
            vo.setVersion3(true);
            ret.put(vo.getName() + Extensions.XML, vo);
        }
        return ret;
    }

    private void checkErrors(List<ErrorInfo> errors, int processId) {
        if (!errors.isEmpty()) {

            String err = RevmanMetadataHelper.getReport(errors, processId, DbEntity.NOT_EXIST_ID, logService);
            Map<String, String> message = new HashMap<>();
            message.put(MessageSender.MSG_PARAM_LIST, err);
            MessageSender.sendMessage(MessageSender.MSG_TITLE_ENTIRE_REVMAN_CONVERSION_FAILED, message);

            LOG.error("Revman conversion failed, " + err);
        }
    }

    private void logStart(int procId, String dbName, int count, boolean entire, String msg, String user, int eventId) {
        logProcessEntire(procId, dbName, eventId, "initial count=" + count + msg, !entire, user);
    }

    private void logEnd(int procId, String dbName, boolean entire, int eventId) {
        logProcessEntire(procId, dbName, eventId, "", !entire);
    }

    private void logFail(int procId, String dbName, boolean entire, String msg, String user, int eventId) {
        logProcessEntire(procId, dbName, eventId, msg, !entire, user);
    }
}
