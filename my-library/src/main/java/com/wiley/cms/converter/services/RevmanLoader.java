package com.wiley.cms.converter.services;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Resource;
import javax.ejb.EJB;
import javax.ejb.Local;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.Queue;
import javax.jms.QueueConnectionFactory;
import javax.jms.Session;

import com.wiley.cms.cochrane.activitylog.ActivityLogEntity;
import com.wiley.cms.cochrane.activitylog.IActivityLogService;
import com.wiley.cms.cochrane.activitylog.ILogEvent;
import com.wiley.cms.cochrane.cmanager.CochraneCMSProperties;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.ContentManagerFactory;
import com.wiley.cms.cochrane.cmanager.DeliveryPackageInfo;
import com.wiley.cms.cochrane.cmanager.FilePathBuilder;
import com.wiley.cms.cochrane.cmanager.FilePathCreator;
import com.wiley.cms.cochrane.cmanager.IDeliveryFileStatus;
import com.wiley.cms.cochrane.cmanager.MessageSender;
import com.wiley.cms.cochrane.cmanager.contentworker.ErrorResults;
import com.wiley.cms.cochrane.cmanager.contentworker.ArchieEntry;
import com.wiley.cms.cochrane.cmanager.contentworker.ArchieResponseBuilder;
import com.wiley.cms.cochrane.cmanager.contentworker.RevmanPackage;
import com.wiley.cms.cochrane.cmanager.data.IResultsStorage;
import com.wiley.cms.cochrane.cmanager.data.issue.IssueVO;
import com.wiley.cms.cochrane.cmanager.data.record.RecordEntity;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.cmanager.translated.TranslatedAbstractVO;
import com.wiley.cms.cochrane.process.IRecordCache;
import com.wiley.cms.cochrane.repository.IRepository;
import com.wiley.cms.cochrane.repository.RepositoryFactory;
import com.wiley.cms.cochrane.repository.RepositoryUtils;
import com.wiley.cms.cochrane.services.IContentManager;
import com.wiley.cms.cochrane.utils.Constants;
import com.wiley.cms.cochrane.utils.ErrorInfo;
import com.wiley.cms.process.jms.JMSSender;
import com.wiley.tes.util.Logger;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 11.07.12
 */
@Stateless
@Local
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class RevmanLoader implements IRevmanLoader {
    private static final Logger LOG = Logger.getLogger(RevmanLoader.class);

    @Resource(mappedName = JMSSender.CONNECTION_LOOKUP)
    private QueueConnectionFactory connectionFactory;

    @Resource(mappedName = "java:jboss/exported/jms/queue/conversion_revman")
    private Queue conversionQueue;

    @EJB
    private IActivityLogService logService;

    @EJB(beanName = "ResultsStorage")
    private IResultsStorage rs;

    @EJB(beanName = "ConversionProcessImpl")
    private IConversionProcess cp;

    @EJB(beanName = "RecordCache")
    private IRecordCache cache;

    @EJB
    private IContentManager cm;

    private IRepository rp = RepositoryFactory.getRepository();

    private String logUser = CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.ACTIVITY_LOG_SYSTEM_NAME);

    public void loadRevmanPackage(DeliveryPackageInfo packageInfo, String packageName, int deliveryFileId,
        IssueVO issue) throws Exception {
        createMessage(packageInfo, packageName, deliveryFileId, issue, null);
    }

    public void loadRevmanPackage(DeliveryPackageInfo packageInfo, String packageName, int deliveryFileId,
        IssueVO issue, Set<String> includedNames) throws Exception {
        createMessage(packageInfo, packageName, deliveryFileId, issue, includedNames);
    }

    public void convertRevmanPackage(DeliveryPackageInfo sources, String packName, int dfId, IssueVO issue,
        Set<String> includedNames) throws Exception {
        LOG.debug(String.format("revman conversion started, packageName:%s, packageId=%d", packName, dfId));

        DeliveryPackageInfo result = new DeliveryPackageInfo(issue.getId(), sources.getDbName(), dfId, packName);
        result.setDbId(sources.getDbId());

        Map<String, String> recs = sources.getRecordPaths();

        String inputDir = FilePathCreator.getInputDir(issue.getId(), sources.getDbName());
        String loadDir = inputDir.replaceFirst(FilePathCreator.INPUT_DIR, FilePathCreator.SEPARATOR + dfId);

        boolean isAut = RevmanPackage.canAut(packName);

        LoadInfo inf = new LoadInfo(result);

        if (isAut) {
            inf.setRevmanElementBuilder(new ArchieResponseBuilder(true, false,
                    packName.replace(FilePathCreator.ZIP_EXT, "1"), dfId));
        }

        int issueId = issue.getId();

        if (sources.hasTranslations()) {
            inf.setTranslatedAbstractsPackage(issueId);
        }

        ErrorResults errResults = new ErrorResults();

        for (String group: recs.keySet()) {

            String revmanGroupDir = recs.get(group);

            List<ErrorInfo> errors = convertRevmanGroup(group, revmanGroupDir, result, issue, packName, includedNames);
            checkEmptyResult(errors, inf);

            if (!RevmanPackage.setWhenReadyRecords(errors, errResults, includedNames, null, group, inf.reb, cache)) {
                handleGoodRecords(revmanGroupDir, inputDir + FilePathCreator.SEPARATOR + group, loadDir,
                        errResults.curr.keySet(), includedNames, inf);
            }

            Map<String, TranslatedAbstractVO> translations = sources.removeTranslations(group);
            if (translations != null) {

                setRevmanElementBuilder(inf.reb, true);

                if (!errResults.curr.isEmpty()) {
                    checkErrors(errResults, translations, group, inf.reb, dfId, issue.getFullNumber());
                    logCurrentErrResults(errResults, dfId, issue.getFullNumber());
                }

                convertRevmanGroup(group, inf.trDestDir, translations, inf, issue, errResults, includedNames);
                setRevmanElementBuilder(inf.reb, false);
            }

            // avoid clean
            sources.removeRecord(group);
            logCurrentErrResults(errResults, dfId, issue.getFullNumber());
        }

        setRevmanElementBuilder(inf.reb, true);
        if (sources.hasTranslations()) {
            for (String trGroup : sources.getTranslationGroups()) {
                convertRevmanGroup(trGroup, inf.trDestDir, sources.getTranslations(trGroup), inf, issue, errResults,
                        includedNames);
                logCurrentErrResults(errResults, dfId, issue.getFullNumber());
            }
        }

        LOG.debug(String.format("revman conversion finishing ... packageName: %s packageId=%d", packName, dfId));

        afterConversion(sources, inf, packName, dfId, errResults.err);

        LOG.debug(String.format("revman conversion finished, packageName: %s packageId=%d", packName, dfId));
    }

    private void setRevmanElementBuilder(ArchieResponseBuilder reb, boolean tr) {
        if (reb == null) {
            return;
        }
        reb.setForTranslations(tr);
    }

    public void onConversionFailed(String packageName, int deliveryFileId, String msg) {

        MessageSender.sendFailedConversionPackageMessage(packageName, null, msg);
        rs.setDeliveryFileStatus(deliveryFileId, IDeliveryFileStatus.STATUS_REVMAN_CONVERTED, true);
        rs.setDeliveryFileStatus(deliveryFileId, IDeliveryFileStatus.STATUS_INVALID_CONTENT, false);

        rs.setWhenReadyPublishStateByDeliveryFile(RecordEntity.STATE_PROCESSING,
                RecordEntity.STATE_UNDEFINED, deliveryFileId);
    }

    public void onConversionSuccessful(String packageName, int deliveryFileId, String errMsg) {

        MessageSender.sendFinishedConversionPackageMessage(packageName, errMsg);
        rs.setDeliveryFileStatus(deliveryFileId, IDeliveryFileStatus.STATUS_REVMAN_CONVERTED, true);
        rs.setDeliveryFileStatus(deliveryFileId, IDeliveryFileStatus.STATUS_REVMAN_CONVERTED, false);
    }

    private void checkErrors(List<ErrorInfo> errs, Map<String, TranslatedAbstractVO> taMap) {
        for (ErrorInfo ei: errs) {
            Object entity = ei.getErrorEntity();
            ArchieEntry rel = (entity instanceof ArchieEntry) ? (ArchieEntry) entity : null;
            if (rel != null) {
                String fileName = RepositoryUtils.getLastNameByPath(rel.getPath());
                TranslatedAbstractVO tvo = taMap.get(fileName);
                if (tvo != null) {
                    tvo.setWasNotified(false);
                    ei.setErrorEntity(tvo);
                    taMap.put(fileName, null);
                }
            }
        }
    }

    private void checkErrors(ErrorResults errResults, Map<String, TranslatedAbstractVO> translations, String group,
                             ArchieResponseBuilder reb, int dfId, int issue) {

        List<ErrorInfo> trErrors = new ArrayList<>();
        for (String key: translations.keySet()) {

            TranslatedAbstractVO tvo = translations.get(key);
            if (tvo != null) {
                tvo.setWasNotified(false);
            }
            String name = tvo.getName();
            if (!errResults.curr.containsKey(name)) {
                continue;
            }
            translations.put(key, null);  // reset it for further conversion
            ErrorInfo ei = errResults.curr.get(name);
            if (ei == null) {
                ei = new ErrorInfo<>(tvo, ErrorInfo.Type.SYSTEM,
                    "the english article of this translation couldn't be processed");
            } else {
                ei.setErrorEntity(tvo);
            }
            trErrors.add(ei);
        }

        if (trErrors.isEmpty()) {
            RevmanPackage.setWhenReadyRecords(trErrors, errResults, null, null, group, reb, cache);
            logCurrentErrResults(errResults, dfId, issue);
        }
    }

    private void logCurrentErrResults(ErrorResults errResults, int dfId, int issue) {
        if (errResults.curr.isEmpty()) {
            return;
        }

        int baseType = BaseType.getCDSR().get().getDbId();
        for (String name: errResults.curr.keySet()) {

            ErrorInfo ei = errResults.curr.get(name);
            logService.logRecordError(ILogEvent.CONVERSION_REVMAN_FAILED, dfId, name, baseType, issue,
                    ei.getErrorDetail());
        }
        errResults.curr.clear();
    }

    private void onContentEmpty(String packageName, int deliveryFileId) {

        MessageSender.sendFailedLoadPackageMessage(packageName,
                "no any workable records in the package, content is invalid");
        rs.setDeliveryFileStatus(deliveryFileId, IDeliveryFileStatus.STATUS_REVMAN_CONVERTED, true);
        rs.setDeliveryFileStatus(deliveryFileId, IDeliveryFileStatus.STATUS_INVALID_CONTENT, false);
    }

    private void checkEmptyResult(List<ErrorInfo> errors, LoadInfo info) {
        if (errors.size() == 1) {
            ErrorInfo.Type errType = errors.get(0).getErrorType();
            if (errType == ErrorInfo.Type.EMPTY_CONTENT) {
                errors.clear();
                return;
            }
        }
        if (info.isEmpty && !info.result.isEmpty()) {
            info.isEmpty = false;
        }
    }

    private List<ErrorInfo> convertRevmanGroup(String groupName, String revmanGroupDir,
        DeliveryPackageInfo result, IssueVO issue, String packageName, Set<String> includedNames) {
        try {
            result.setCurrentGroup(groupName);
            return cp.convert(revmanGroupDir, issue, result.getDfId(), packageName, result, includedNames);

        } catch (Exception e) {
            LOG.error(e.getMessage());
            List<ErrorInfo> ret = new ArrayList<>();
            ret.add(new ErrorInfo<>(groupName, ErrorInfo.Type.CRG_GROUP, e.getMessage(), null));
            return ret;
        }
    }

    private void convertRevmanGroup(String group, String destDir, Map<String, TranslatedAbstractVO> translations,
        LoadInfo inf, IssueVO issue, ErrorResults errResults, Set<String> includedNames) {

        int issueId = issue.getId();
        String trDir = FilePathBuilder.TR.getPathToRevmanTranslations(issueId, inf.packageSid, group);
        List<ErrorInfo> errors;

        try {
            inf.result.setCurrentGroup(group);
            errors = cp.convertTranslations(trDir, destDir, translations, issue, inf.result, includedNames);

        } catch (Exception e) {
            LOG.error(e.getMessage());
            errors = new ArrayList<>();
            errors.add(new ErrorInfo<>(group, ErrorInfo.Type.CRG_GROUP, e.getMessage(), null));
        }

        checkEmptyResult(errors, inf);

        if (!errors.isEmpty()) {
            checkErrors(errors, translations);
            RevmanPackage.setWhenReadyRecords(errors, errResults, null, inf.result.getRecords().keySet(),
                    group, inf.reb, cache);
        }
    }

    private void handleGoodRecords(String srcPath, String destPath, String loadDir, Set<String> noMoves,
        Set<String> includedNames, LoadInfo inf) throws Exception {

        String topicsPath = FilePathCreator.getRevmanTopicSource(srcPath);
        synchronized (RevmanLoader.class) {
            if (rp.isFileExists(topicsPath)) {

                InputStream isTopics = rp.getFile(topicsPath);
                rp.putFile(destPath + FilePathCreator.SEPARATOR + Constants.TOPICS_SOURCE, isTopics);
                inf.hasTop = true;
                //rp.deleteFile(topicsPath);
            }
        }

        String reviewPath = FilePathCreator.getDirForRevmanReviews(destPath);
        String reviewPathSrc = FilePathCreator.getDirForRevmanReviews(srcPath);
        File[] reviews = rp.getFilesFromDir(reviewPathSrc);
        if (reviews == null) {
            return;
        }

        for (File review: reviews) {

            if (review.isDirectory()) {
                continue;
            }

            String fName = review.getName();
            String name = RevmanPackage.getRecordNameByFileName(fName);

            if (noMoves != null && noMoves.contains(name)) {

                rp.deleteFile(loadDir + FilePathCreator.SEPARATOR + name + FilePathCreator.XML_EXT);
                rp.deleteFile(loadDir + FilePathCreator.SEPARATOR + name);
                continue;
            }

            if (includedNames != null && !isIncluded(name, includedNames)) {
                continue;
            }

            InputStream is = new BufferedInputStream(new FileInputStream(review));
            synchronized (RevmanLoader.class) {
                rp.putFile(reviewPath + FilePathCreator.SEPARATOR + name + FilePathCreator.XML_EXT, is);
            }
        }
    }

    private boolean isIncluded(String name, Set<String> includes) {
        return includes.contains(name) || (name.contains(Constants.METADATA_SOURCE_SUFFIX)
            && includes.contains(name.substring(0, name.length()
                - Constants.METADATA_SOURCE_SUFFIX.length())));
    }

    private void afterConversion(DeliveryPackageInfo sources, LoadInfo inf, String packName,
        int deliveryFileId, StringBuilder fails) throws Exception {

        sources.cleanInRepository(rp);

        if (fails.length() > 0) {
            fails.delete(fails.length() - 2, fails.length());
        }

        if (!inf.isEmpty && (inf.reb != null || fails.length() > 0)) {
            // if auto packet -> set converted any case
            onConversionSuccessful(packName, deliveryFileId, fails.toString());
            logService.info(ActivityLogEntity.EntityLevel.FILE, ILogEvent.REVMAN_DATA_CONVERTED, deliveryFileId,
                    packName, logUser, null);

        } else if (fails.length() > 0) {
            // if not auto packet -> stop by any error
            onConversionFailed(packName, deliveryFileId, fails.toString());
            logService.error(ActivityLogEntity.EntityLevel.FILE, ILogEvent.CONVERSION_REVMAN_FAILED, deliveryFileId,
                    packName, logUser, null);
            inf.upload = false;

        } else {
            if (!inf.isEmpty || inf.hasTop) {
                onConversionSuccessful(packName, deliveryFileId, null);

            } else {
                onContentEmpty(packName, deliveryFileId);
                LOG.warn(String.format("%s [%s] is probably empty ...", packName, deliveryFileId));
                inf.upload = false;
            }
        }

        RevmanPackage.notifyPublished(inf.reb, null);

        if (inf.upload) {
            ContentManagerFactory.getFactory().getInstance().resumeOldPackage(deliveryFileId, packName);
        }
    }

    private void createMessage(DeliveryPackageInfo sources, String packageName, int deliveryFileId,
        IssueVO issue, Set<String> includedNames) throws JMSException {

        Connection connection = null;
        Session session = null;
        MessageProducer messageProducer = null;

        try {
            connection = connectionFactory.createConnection();
            connection.start();
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            messageProducer = session.createProducer(conversionQueue);

            ObjectMessage msg = session.createObjectMessage();
            Object[] parameters = {
                sources, packageName, deliveryFileId, issue, includedNames != null ? includedNames.toArray() : null
            };
            msg.setObject(parameters);
            messageProducer.send(msg);

        } finally {
            closeMessageProducer(messageProducer);
            closeSession(session);
            closeConnection(connection);
        }
    }

    private void closeMessageProducer(MessageProducer messageProducer) throws JMSException {
        if (messageProducer != null) {
            messageProducer.close();
        }
    }

    private void closeSession(Session session) throws JMSException {
        if (session != null) {
            session.close();
        }
    }

    private void closeConnection(Connection connection) throws JMSException {
        if (connection != null) {
            connection.close();
        }
    }

    private static class LoadInfo {

        private final String packageSid;
        private DeliveryPackageInfo result;
        private String trDestDir = null;
        private boolean upload = true;
        private boolean isEmpty = true;
        private boolean hasTop = false;
        private ArchieResponseBuilder reb = null;

        private LoadInfo(DeliveryPackageInfo packageInfo) {
            setDeliveryPackageInfo(packageInfo);
            packageSid = "" + packageInfo.getDfId();
        }

        private void setRevmanElementBuilder(ArchieResponseBuilder reb) {
            this.reb = reb;
        }

        private void setTranslatedAbstractsPackage(Integer issueId) {
            trDestDir = FilePathBuilder.TR.getPathToIssueTA(issueId, result.getDfId());
        }

        private void setDeliveryPackageInfo(DeliveryPackageInfo packageInfo) {
            result = packageInfo;
        }
    }
}