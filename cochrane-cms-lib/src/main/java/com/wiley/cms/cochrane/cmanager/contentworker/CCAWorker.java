package com.wiley.cms.cochrane.cmanager.contentworker;

import com.wiley.cms.cochrane.activitylog.ActivityLogEntity;
import com.wiley.cms.cochrane.activitylog.FlowProduct;
import com.wiley.cms.cochrane.activitylog.IFlowLogger;
import com.wiley.cms.cochrane.activitylog.ILogEvent;
import com.wiley.cms.cochrane.cmanager.CmsException;
import com.wiley.cms.cochrane.cmanager.CmsJTException;
import com.wiley.cms.cochrane.cmanager.CmsUtils;
import com.wiley.cms.cochrane.cmanager.CochraneCMSBeans;
import com.wiley.cms.cochrane.cmanager.CochraneCMSProperties;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.DeliveryPackage;
import com.wiley.cms.cochrane.cmanager.DeliveryPackageException;
import com.wiley.cms.cochrane.cmanager.DeliveryPackageInfo;
import com.wiley.cms.cochrane.cmanager.FilePathBuilder;
import com.wiley.cms.cochrane.cmanager.IDeliveryFileStatus;
import com.wiley.cms.cochrane.cmanager.IQaService;
import com.wiley.cms.cochrane.cmanager.MessageSender;
import com.wiley.cms.cochrane.cmanager.data.IResultsStorage;
import com.wiley.cms.cochrane.cmanager.data.PrevVO;
import com.wiley.cms.cochrane.cmanager.data.StatsUpdater;
import com.wiley.cms.cochrane.cmanager.data.cca.CcaEntity;
import com.wiley.cms.cochrane.cmanager.data.db.ClDbVO;
import com.wiley.cms.cochrane.cmanager.data.db.DbStorageFactory;
import com.wiley.cms.cochrane.cmanager.data.df.DfStorageFactory;
import com.wiley.cms.cochrane.cmanager.data.df.IDfStorage;
import com.wiley.cms.cochrane.cmanager.data.entire.EntireDBStorageFactory;
import com.wiley.cms.cochrane.cmanager.data.entire.IEntireDBStorage;
import com.wiley.cms.cochrane.cmanager.data.record.GroupVO;
import com.wiley.cms.cochrane.cmanager.data.record.IKibanaRecord;
import com.wiley.cms.cochrane.cmanager.data.record.IRecordStorage;
import com.wiley.cms.cochrane.cmanager.data.record.RecordEntity;
import com.wiley.cms.cochrane.cmanager.data.record.RecordStorageFactory;
import com.wiley.cms.cochrane.cmanager.data.record.UnitStatusEntity;
import com.wiley.cms.cochrane.cmanager.data.record.UnitStatusVO;
import com.wiley.cms.cochrane.cmanager.entitymanager.CDSRMetaVO;
import com.wiley.cms.cochrane.cmanager.entitymanager.ICDSRMeta;
import com.wiley.cms.cochrane.cmanager.entitymanager.IRecordManager;
import com.wiley.cms.cochrane.cmanager.entitymanager.IVersionManager;
import com.wiley.cms.cochrane.cmanager.entitywrapper.PublishWrapper;
import com.wiley.cms.cochrane.cmanager.publish.IPublish;
import com.wiley.cms.cochrane.cmanager.publish.IPublishStorage;
import com.wiley.cms.cochrane.cmanager.publish.PublishHelper;
import com.wiley.cms.cochrane.cmanager.publish.PublishOperation;
import com.wiley.cms.cochrane.cmanager.publish.generate.AbstractGeneratorWhenReady;
import com.wiley.cms.cochrane.cmanager.publish.generate.ds.DSCCAGenerator;
import com.wiley.cms.cochrane.cmanager.publish.generate.literatum.LiteratumCCAGenerator;
import com.wiley.cms.cochrane.cmanager.publish.generate.semantico.SemanticoCCAGenerator;
import com.wiley.cms.cochrane.cmanager.publish.send.ds.DSCCASender;
import com.wiley.cms.cochrane.cmanager.publish.send.literatum.LiteratumSender;
import com.wiley.cms.cochrane.cmanager.publish.send.semantico.SemanticoCCASender;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.cmanager.res.PubType;
import com.wiley.cms.cochrane.converter.ml3g.JatsMl3gAssetsManager;
import com.wiley.cms.cochrane.process.ICMSProcessManager;
import com.wiley.cms.cochrane.process.IRecordCache;
import com.wiley.cms.cochrane.process.handler.ContentHandler;
import com.wiley.cms.cochrane.repository.IRepository;
import com.wiley.cms.cochrane.repository.RepositoryFactory;
import com.wiley.cms.cochrane.repository.RepositoryUtils;
import com.wiley.cms.cochrane.test.ContentChecker;
import com.wiley.cms.cochrane.test.PackageChecker;
import com.wiley.cms.cochrane.utils.Constants;
import com.wiley.cms.cochrane.utils.ErrorInfo;
import com.wiley.cms.cochrane.utils.xml.EntityDecoder;
import com.wiley.cms.qaservice.services.IProvideQa;
import com.wiley.cms.qaservice.services.WebServiceUtils;
import com.wiley.tes.util.Extensions;
import com.wiley.tes.util.Logger;
import com.wiley.tes.util.Pair;
import com.wiley.tes.util.URIWrapper;
import com.wiley.tes.util.XmlUtils;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.SystemUtils;
import org.jdom.Attribute;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.Namespace;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import org.jdom.xpath.XPath;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author <a href='mailto:ikirov@wiley.com'>Igor Kirov</a>
 * @author <a href='mailto:sgulin@wiley.com'>Svyatoslav Gulin</a>
 * @version 16.02.2012
 */
public class CCAWorker extends PackageWorker<DeliveryPackage> {
    public static final String CCA_DOI_TEMPLATE = "^10\\.1002/(cca\\.[1-9][0-9]*)$";
    public static final String CCA_DOI_ACCESSION_ID_TEMPLATE = "^info:doi/" + CCA_DOI_TEMPLATE.substring(1);
    public static final Pattern CCA_DOI_PATTERN = Pattern.compile(CCA_DOI_TEMPLATE);
    public static final Pattern CCA_DOI_ACCESSION_ID_PATTERN = Pattern.compile(CCA_DOI_ACCESSION_ID_TEMPLATE);
    private static final Logger LOG = Logger.getLogger(CCAWorker.class);
    private static final String MESSAGES_OPEN_TAG = "<messages";
    private static final String MESSAGES_CLOSE_TAG = "</messages>";
    private static final String A = "a";
    private static final Pattern CDSR_DOI_PATTERN = Pattern.compile("^10\\.1002/14651858\\.([^.]*)(\\..*)?$");
    private static final String CDSR_DOI_PATH = "//a:bibliography/a:bib/a:citation/a:accessionId";
    private static final String TITLE_PATH = "//a:contentMeta/a:titleGroup/a:title";
    private static final String PUB_META_UNIT_PATH = "//a:publicationMeta[@level='unit']";
    private static final String EVENT_PATH = PUB_META_UNIT_PATH + "[@type='article']/a:eventGroup";    
    private static final String FIRST_ONLINE_PATH = "//a:event[@type='firstOnline']";
    private static final String PUBLISH_ONLINE_PATH = "//a:event[@type='publishedOnlineFinalForm']";
    private static final String ONLINE_CITATION_ISSUE_PATH = "//a:event[@type='publishedOnlineCitationIssue']";
    private static final String FAILED_TO_COMPILE_X_PATH = "Failed to compile xPath";
    private static final String DETAILS = "details";
    private static final String RECORD_NAME = "record_name";
    private static final String RECORD_STATUS = "record_status";
    private static final String AVAILABLE_STATUSES = "available_statuses";
    private static final String FIRST_DOI = "doi1";
    private static final String SECOND_DOI = "doi2";
    private static final String CCA_DOI = "cca_doi";
    private static final String CCA_ACSN_ID_DOI = "cca_accessionid_doi";
    private static final String CCA_ACSN_ID_REF = "cca_accessionid_ref";
    private static final String CDSR = "cdsr";
    private static final String CDSR_OLD = "cdsr_old";
    private static final String TYPE_STR = "type";
    private static final String DATE_STR = "date";
    private static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");
    private static final List<Integer> DOI_CHECK_DISABLED_4_STATUSES = Arrays.asList(
            UnitStatusEntity.UnitStatus.ARCHIVED, UnitStatusEntity.UnitStatus.UNDER_REVIEW);
    private static final String MESSAGE_KEY_EXCEPTION = "cca_validation_failed_exception";
    private static final String MESSAGE_KEY_XML_SOURCE_INVALID = "cca_validation_failed_xml_source_invalid";
    private static final String MESSAGE_KEY_NO_STATUS = "cca_validation_failed_no_status";
    private static final String MESSAGE_KEY_UNSUPPORTED_STATUS = "cca_validation_failed_unsupported_status";
    private static final String MESSAGE_KEY_INCORRECT_STATUS_NEW = "cca_validation_failed_incorrect_status_new";
    private static final String MESSAGE_KEY_CDSR_DIFFERENT_VERSIONS = "cca_validation_failed_cdsr_different_versions";
    private static final String MESSAGE_KEY_CCA_DIFFERENT_DOIS = "cca_validation_failed_doi_differs_from_recordname";
    private static final String MESSAGE_KEY_CCA_WRONG_DOI = "cca_validation_failed_doi";
    private static final String MESSAGE_KEY_CDSR_NOT_FOUND = "cca_validation_failed_cdsr_not_found";
    private static final String MESSAGE_KEY_OLD_CDSR_REFERENCE = "cca_validation_failed_old_cdsr_reference";
    private static final String MESSAGE_KEY_QA = "cca_validation_failed_qa";

    private IRepository rps;
    private IQaService qaService;
    private IRecordManager rm;
    private IDfStorage df;
    private IRecordStorage recs;
    private IPublishStorage ps;
    private IFlowLogger flLogger;
    private final ContentHelper helper = new ContentHelper();

    @Override
    public void work(URI packageUri) {
        LOG.debug("loading started, packageUri: " + packageUri);
        init();
        DeliveryPackageInfo packageInfo = unpackPackage(packageUri);
        if (packageInfo == null) {
            return;
        } else if (packageInfo.getRecordPaths().isEmpty()) {
            helper.addMessageTag(DETAILS, "Invalid structure delivered package [" + pck.getPackageFileName() + "]");
            sendFailedValidationReport(packageInfo.getDfId(), "", "", MESSAGE_KEY_QA);
        }
        BaseType bt = BaseType.getCCA().get();
        int issue = CmsUtils.getIssueNumber(pck.getYear(), pck.getIssueNumber());
        QasResult qasResult = startQas(packageInfo, issue, bt);
        boolean qaSuccessful = qasResult.ccaRecords.size() == packageInfo.getRecordNames().size();
        PublishResult publishResult = PublishResult.FAIL;
        if (qasResult.result && !qasResult.ccaRecords.isEmpty()) {
            saveToEntire(bt, packageInfo, qasResult);
            publishResult = publish(packageInfo, bt, qasResult, false);
            rs.setDbApproved(packageInfo.getDbId(), true);
        }
        setDfStatus(qasResult.result, qaSuccessful, publishResult.result, qasResult.ccaRecords.size());
        DbStorageFactory.getFactory().getInstance().updateRenderedRecordCount(packageInfo.getDbId());
        logService.info(ActivityLogEntity.EntityLevel.FILE, ILogEvent.LOADING_COMPLETED, deliveryFileId,
            getPackageName(), CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.ACTIVITY_LOG_SYSTEM_NAME), "");
        flLogger.onFlowCompleted(qasResult.toComplete.keySet());
        LOG.debug("loading finished, packageName " + getPackageName());
    }

    @Override
    protected boolean check4NewFormats(BaseType bt, String dfName, int type, int issueId, int dbId)
            throws CmsException {
        super.check4NewFormats(bt, dfName, type, issueId, dbId);
        if (DeliveryPackage.isPropertyUpdate(dfName)) {
            startUploadProcess(dfName, packageUri, bt.getId(), dbId, issueId, CmsUtils.getIssueNumber(
                    pck.getYear(), pck.getIssueNumber()), ICMSProcessManager.PROC_TYPE_UPLOAD_CCA);
            return true;
        }
        return false;
    }

    private void setDfStatus(boolean noFatalQaErrors, boolean qaSuccessful, boolean publishSuccessful, int size) {
        if (!noFatalQaErrors || size == 0) {
            df.changeStatus(deliveryFileId, IDeliveryFileStatus.STATUS_VALIDATION_FAILED);
        } else if (qaSuccessful && publishSuccessful) {
            df.changeStatus(deliveryFileId, IDeliveryFileStatus.STATUS_PUBLISHING_FINISHED_SUCCESS);
        } else if (!publishSuccessful) {
            df.changeStatus(deliveryFileId, IDeliveryFileStatus.STATUS_PUBLISHING_FAILED);
        } else {
            df.changeStatus(deliveryFileId, IDeliveryFileStatus.STATUS_VALIDATION_SOME_FAILED);
        }
    }

    private void init() {
        try {
            extractFailedMessageDestination = "cca_data_extraction_failed";
            qaService = CochraneCMSPropertyNames.lookup("QaService", IQaService.class);
            rps = RepositoryFactory.getRepository();
            rm = CochraneCMSBeans.getRecordManager();
            df = DfStorageFactory.getFactory().getInstance();
            recs = RecordStorageFactory.getFactory().getInstance();
            ps = CochraneCMSBeans.getPublishStorage();
            flLogger = CochraneCMSPropertyNames.lookupFlowLogger();
            helper.initMessageTags();
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
        }
    }

    private void saveToEntire(BaseType bt, DeliveryPackageInfo info, QasResult qasResult) {
        IEntireDBStorage dbStorage = EntireDBStorageFactory.getFactory().getInstance();
        String dbName = info.getDbName();
        boolean dashboard = bt.hasSFLogging();
        Map<String, String> fails = new HashMap<>();
        for (String recordName: qasResult.ccaRecords.keySet()) {
            try {
                moveSourcesToEntire(info.getRecordPaths().get(recordName), dbName, recordName);
                dbStorage.updateRecord(info.getDbId(), recordName, true);
                Boolean newDoi = qasResult.toComplete.get(recordName);
                if (newDoi != null) {
                    flLogger.onProductSaved(recordName, newDoi, dashboard, false);
                }
            } catch (Exception e) {
                LOG.error(e.getMessage(), e);
                fails.put(recordName, e.getMessage());
                flLogger.onProductError(ILogEvent.PRODUCT_ERROR, info.getDfName(), recordName, Constants.FIRST_PUB,
                        e.getMessage(), bt.hasSFLogging());
            }
        }
        StatsUpdater.onUpdate(dbName);
        StringBuilder errMsg = new StringBuilder();
        for (String recordName: fails.keySet()) {
            qasResult.ccaRecords.remove(recordName);
            errMsg.append(recordName).append(" - ").append(fails.get(recordName)).append("\n");
        }
        if (errMsg.length() > 0) {
            MessageSender.sendFailed(pck.getPackageId(), pck.getPackageFileName(), errMsg.toString(), true, dbName);
        }
    }

    private void moveSourcesToEntire(String filePath, String dbName, String recordName) throws Exception {
        InputStream is = null;
        try {
            is = rps.getFile(filePath);
            String pathToSourceEntire = FilePathBuilder.getPathToEntireSrcRecordDir(dbName, recordName);
            rps.putFile(pathToSourceEntire + Extensions.XML, is);
            String fileFolder = RepositoryUtils.getRecordNameByFileName(filePath);
            File[] files = rps.getFilesFromDir(fileFolder);
            if (files != null && files.length != 0) {
                rps.deleteDir(pathToSourceEntire);
                CmsUtils.writeDir(fileFolder, pathToSourceEntire);
            }
        } finally {
            IOUtils.closeQuietly(is);
        }
    }

    public static Pair<String, CcaEntity> extractCDSRDoi(String dfName, Integer dfId, ICDSRMeta meta, String recordPath,
            Date currentDate, ContentHelper helper, IResultsStorage rs) {
        IFlowLogger flowLogger = !helper.initMessageTags() ? CochraneCMSBeans.getFlowLogger() : null;
        IRepository rp = RepositoryFactory.getRepository();
        XPath xPath;
        try {
            xPath = XPath.newInstance(CDSR_DOI_PATH);
            xPath.addNamespace(A, Constants.WILEY_NAMESPACE_URI);
        } catch (Exception e) {
            LOG.error(FAILED_TO_COMPILE_X_PATH, e);
            return null;
        }
        boolean failed = false;
        Map<String, String> dois = new HashMap<>();
        String messageKey = MESSAGE_KEY_CDSR_DIFFERENT_VERSIONS;
        String recordName = meta.getCdNumber();
        helper.addMessageTag(RECORD_NAME, recordName);
        try {
            Document document = helper.getDocumentLoader().load(rp.getFile(recordPath));
            List nodes = xPath.selectNodes(document);
            for (Object node : nodes) {
                Element element = (Element) node;
                Matcher m = CDSR_DOI_PATTERN.matcher(element.getText());
                if (m.matches()) {
                    String doi = element.getText();
                    String name = m.group(1);
                    if (dois.containsKey(name) && !dois.get(name).equals(doi)) {
                        failed = true;
                        helper.addMessageTag(FIRST_DOI, doi);
                        helper.addMessageTag(SECOND_DOI, dois.get(name));
                    } else {
                        dois.put(name, doi);
                    }
                } else {
                    LOG.warn("accessionId unrecognized: [" + element.getText() + "]");
                }
            }
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
            failed = true;
            messageKey = MESSAGE_KEY_EXCEPTION;
            helper.addMessageTag(DETAILS, e.getMessage());
        }
        if (failed) {
            reportFailedValidation(dfName, dfId, recordName, meta.getTitle(), messageKey, helper, flowLogger);
        }
        StringBuilder notFoundCdsrRecords = new StringBuilder();
        StringBuilder oldReferences2Cdsr = new StringBuilder();
        failed = failed || isCheckDoisEnabled(meta.getUnitStatusId())
                && !checkCDSRDois(dois, notFoundCdsrRecords, oldReferences2Cdsr, helper);
        Pair<String, CcaEntity> ret;
        if (failed) {
            if (notFoundCdsrRecords.length() != 0) {
                helper.addMessageTag(CDSR, notFoundCdsrRecords.substring(0, notFoundCdsrRecords.length() - 2));
                reportFailedValidation(dfName, dfId, recordName, meta.getTitle(), MESSAGE_KEY_CDSR_NOT_FOUND, helper,
                        flowLogger);
            }
            if (oldReferences2Cdsr.length() != 0) {
                helper.addMessageTag(CDSR_OLD, oldReferences2Cdsr.substring(0, oldReferences2Cdsr.length() - 2));
                reportFailedValidation(dfName, dfId, recordName, meta.getTitle(), MESSAGE_KEY_OLD_CDSR_REFERENCE,
                        helper, flowLogger);
            }
            ret = null;
        } else if (currentDate != null) {
            CcaEntity cca = rs.saveCcaEntity(recordName, currentDate);
            dois.forEach((name, doi) -> rs.saveDoiEntity(cca, doi, name));
            ret = new Pair<>(recordName, cca);
        } else {
            ret = new Pair<>(recordName, null);
        }
        return ret;
    }

    private static boolean checkCDSRDois(Map<String, String> dois, StringBuilder notFoundCdsr,
                                         StringBuilder oldReferences2Cdsr, ContentHelper helper) {
        boolean ret = true;
        LOG.debug("try to validate CDSR dois");
        IVersionManager vm = CochraneCMSBeans.getVersionManager();
        for (String recordName : dois.keySet()) {
            PrevVO pvo = vm.getLastVersion(recordName);
            if (pvo == null) {
                LOG.debug("Prev entity for " + recordName + " is null");
                notFoundCdsr.append(recordName).append(", ");
                ret = false;
            } else  {
                String doi = pvo.buildDoi();
                if (!doi.equals(dois.get(recordName))) {
                    helper.addMessageTag(CDSR, doi);
                    LOG.debug("Reference " + dois.get(recordName) + " found");
                    LOG.debug("entity.getDoi() = " + doi);
                    oldReferences2Cdsr.append(dois.get(recordName)).append(", ");
                    ret = false;
                }
            }
        }
        return ret;
    }

    private PublishResult publish(DeliveryPackageInfo dpi, BaseType bt, QasResult qasResult, boolean pu) {
        boolean result;
        if (!qasResult.ccaRecords.isEmpty()) {
            if (bt.hasSFLogging()) {
                flLogger.onProductsPublishingStarted(qasResult.ccaRecords.keySet(), true, false);
            }
            qasResult.determineRecordsToFlowCompleted();
            BaseType baseType = BaseType.find(dpi.getDbName()).get();
            ClDbVO dbVo = new ClDbVO(rs.getDb(dpi.getDbId()));
            PublishWrapper pwHW =  PublishWrapper.createIssuePublishWrapper(PubType.TYPE_SEMANTICO, dpi.getDbName(),
                dpi.getDbId(), true, false, new Date());
            pwHW.initWorkflow(true, true, false, false);
            PublishWrapper pwLIT = publishLiteratum(dpi, dbVo, baseType, pwHW);
            result = pwLIT != null && publishSemantico(dpi, dbVo, pwHW, pwLIT);
            if (qasResult.existingCcaRecords == null) {
                boolean skipDs = bt.isActualPublicationDateSupported() && !pu;
                result = result && (skipDs || publishDS(dpi, baseType, null));
            } else {
                result = result && publishDS(dpi, baseType, qasResult.existingCcaRecords.keySet());
            }
        } else {
            LOG.debug("There are no records available for publishing. Package won't be generated");
            result = false;
        }
        return new PublishResult(result, result ? qasResult.ccaRecords.keySet() : Collections.emptyList());
    }

    private PublishWrapper publishLiteratum(DeliveryPackageInfo info, ClDbVO dbVo, BaseType bt, PublishWrapper pwHW) {
        PubType pubType = bt.getPubInfo(PubType.TYPE_LITERATUM).getType();
        PublishWrapper publishWrapper = PublishWrapper.createIssuePublishWrapper(pubType.getId(), info.getDbName(),
                info.getDbId(), deliveryFileId, new Date(), true, false);
        publishWrapper.initWorkflow(true, true, false, false);
        if (pwHW != null) {
            publishWrapper.setPublishToAwait(pwHW);
        }
        LiteratumCCAGenerator ccaGenerator = new LiteratumCCAGenerator(dbVo);
        ccaGenerator.setUseCommonWrSupport(false);
        IPublish generator = ccaGenerator.setContext(rs, ps, flLogger);
        generator.start(publishWrapper, PublishOperation.GENERATE);
        if (publishWrapper.hasPublishToAwait()) {
            publishWrapper.checkPublishAwaitCompleted();
        }
        IPublish sender = new LiteratumSender(dbVo).setContext(rs, ps, flLogger);
        boolean ret = publishWrapper.isScopeSkipped() || sendPublishPackage(sender, publishWrapper);
        publishWrapper.sendMessages();
        return ret ? publishWrapper : null;
    }

    private boolean publishSemantico(DeliveryPackageInfo info, ClDbVO dbVo, PublishWrapper pw, PublishWrapper pwLIT) {
        if (pwLIT == null) {
            pw.setDeliveryFileId(deliveryFileId);
        } else if (!pwLIT.hasNext()) {
            return true; // there is only scope awaiting LIT
        }
        IPublish generator = new SemanticoCCAGenerator(false, info.getDbId());
        generator.start(pw, PublishOperation.GENERATE);
        IPublish sender = new SemanticoCCASender(dbVo).setContext(rs, ps, flLogger);
        return sendPublishPackage(sender, pw);
    }

    private boolean publishDS(DeliveryPackageInfo info, BaseType baseType, Set<String> names) {
        IRecordCache cache = flLogger.getRecordCache();
        String uuid = null;
        if (names != null) {
            for (String cdNumber : names) {
                IKibanaRecord kr = cache.getKibanaRecord(cdNumber, false);
                if (kr != null) {
                    kr.getFlowProduct().setFlowAndProductState(FlowProduct.State.PUBLISHED, null);
                    uuid = kr.getFlowProduct().getTransactionId();
                }
            }
        }
        return isPublishSuccessful(info, baseType, PubType.TYPE_DS, names, uuid, new DSCCASender(info.getDbId()),
                new DSCCAGenerator(false, info.getDbId()));
    }

    private boolean isPublishSuccessful(DeliveryPackageInfo info, BaseType baseType, String type, Set<String> names,
                                        String uuid, IPublish sender, AbstractGeneratorWhenReady generator) {
        PubType pubType = baseType.getPubInfo(type).getType();
        PublishWrapper publishWrapper = PublishWrapper.createIssuePublishWrapper(pubType.getId(), info.getDbName(),
                info.getDbId(), true, false, new Date());
        if (names != null) {
            publishWrapper.setCdNumbers(names);
        } else {
            publishWrapper.setDeliveryFileId(deliveryFileId);
        }
        publishWrapper.setTransactionId(uuid);
        generator.start(publishWrapper, PublishOperation.GENERATE);
        return generator.isArchiveEmpty() || sendPublishPackage(sender, publishWrapper);
    }

    private boolean sendPublishPackage(IPublish sender, PublishWrapper pw) {
        String exportType = pw.getType();
        sender.start(pw, PublishOperation.SEND);
        if (rs.findPublish(pw.getId()).sent()) {
            rs.createWhenReadyPublishSuccess(deliveryFileId, exportType);
            return true;
        }
        String err = String.format("Publishing %s failed [%s]", exportType, pw.getPublishEntity().getId());
        LOG.error(err);
        rs.createWhenReadyPublishFailure(deliveryFileId, exportType, err);
        return false;
    }

    private DeliveryPackageInfo unpackPackage(URI packageUri) {
        DeliveryPackageInfo packageInfo = null;
        boolean success = false;
        try {
            Map<String, String> notifyMessage = getNotifyMessage(packageUri);
            lookupServices();
            MessageSender.sendMessage("loading_started", notifyMessage);
            String packageFileName = getPackageName();
            logService.info(ActivityLogEntity.EntityLevel.FILE, ILogEvent.NEW_PACKAGE_RECEIVED,
                deliveryFileId, packageFileName, theLogUser, null);
            int issueId = rs.findOpenIssue(pck.getYear(), pck.getIssueNumber());
            if (CmsUtils.isScheduledIssue(issueId)) {
                throw CmsException.createForScheduledIssue(deliveryFileId);
            }
            int dbId = rs.findOpenDb(issueId, pck.getLibName());
            int type = rs.updateDeliveryFile(deliveryFileId, issueId, pck.getVendor(), dbId).getType();
            if (!check4NewFormats(BaseType.getCCA().get(), packageFileName, type, issueId, dbId)) {
                packageInfo = pck.extractData(issueId);
                MessageSender.sendMessage("data_extracted", notifyMessage);
                logService.info(ActivityLogEntity.EntityLevel.FILE, ILogEvent.PACKAGE_UNZIPPED,
                        deliveryFileId, packageFileName, theLogUser, null);
                packageInfo.setDbId(dbId);
                updateRepository(packageInfo, dbId, deliveryFileId, packageFileName);
                success = true;
            }
        } catch (Exception e) {
            LOG.error(e.getMessage() + ", packageName: " + getPackageName());
            doOnWorkMethodException(e, packageUri, packageInfo);
            success = false;
            return null;
        } finally {
            String uri = packageUri.toString();
            if (uri.contains("ftp")) {
                deletePackageOnFtp(new URIWrapper(packageUri), deliveryFileId);
            } else if (success
                    && uri.contains(CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.PREFIX_REPOSITORY))) {
                deletePackageOnRepository(packageUri, deliveryFileId);
            }
            LOG.debug("loading finished, packageName: " + getPackageName());
        }
        return packageInfo;
    }

    private QasResult startQas(DeliveryPackageInfo dpi, int fullIssueNumber, BaseType bt) {
        QasResult qasResult = new QasResult(new HashMap<>(), true);
        IProvideQa qa = null;
        Date dt = new Date();
        boolean dashboard = bt.hasSFLogging();
        try {
            qa = WebServiceUtils.getProvideQa();
            for (Map.Entry<String, String> entry: dpi.getRecordPaths().entrySet()) {
                String sourceXmlWithAssets = "";
                boolean successful = true;
                String recordName = entry.getKey();
                flLogger.onProductReceived(dpi.getDfName(), dpi.getDfId(), recordName, Constants.FIRST_PUB,
                        PackageChecker.APTARA, null, dashboard);
                flLogger.getRecordCache().addRecord(recordName, false);
                qasResult.toComplete.put(recordName, null);
                String recordPath = entry.getValue();
                try {
                    sourceXmlWithAssets = getSourceXmlWithAssets(recordPath);

                } catch (StringIndexOutOfBoundsException e) {
                    successful = false; // xml source is invalid
                    helper.addMessageTag(RECORD_NAME, recordName);
                    sendFailedValidationReport(dpi.getDfId(), recordName, "", MESSAGE_KEY_XML_SOURCE_INVALID);
                }
                String resultStr = qa.check(sourceXmlWithAssets, "cochrane_xml3g", bt.getId());
                CDSRMetaVO meta = extractMetadata(bt, dpi.getDfId(), recordName, recordPath, fullIssueNumber);
                String title = meta != null ? meta.getTitle() : null;
                successful = successful && meta != null && parseQaResult(dpi.getDfId(), resultStr, recordName, title);
                Date[] prevDates = rs.getLastPublishedCCADate(recordName);
                successful = checkPreviousDate(dpi.getDfId(), recordName, title, prevDates, successful)
                    && checkRecordUnitStatus(getPackageName(), dpi.getDfId(), meta, dt, prevDates, helper, flLogger);
                Pair<String, CcaEntity> en = !successful ? null
                        : extractCDSRDoi(getPackageName(), dpi.getDfId(), meta, recordPath, dt, helper, rs);
                boolean newPubDateSupport = isActualPublicationDateSupportedAndNew(bt, prevDates);
                successful = (en != null) && checkDate(bt, dpi.getDfId(), meta, recordPath,
                        prevDates != null ? prevDates : new Date[] {dt, null}, newPubDateSupport, false);
                successful = updateMetadata(bt, dpi.getDfId(), meta, newPubDateSupport, successful);
                if (meta != null) {
                    updateQaResult(dpi, meta, successful ? en : null, meta.getUnitStatusId(),
                            resultStr, bt.isActualPublicationDateSupported() && !newPubDateSupport, qasResult);
                }
                if (successful) {
                    flLogger.updateProduct(meta, meta.getStage(), meta.getStatus(), null, meta.getWMLPublicationType(),
                            null, 0);
                    flLogger.onProductUnpacked(getPackageName(), null, recordName, null, dashboard, false);
                    flLogger.onProductValidated(meta, dashboard, false);
                }
            }
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
            qasResult.result = false;
        } finally {
            WebServiceUtils.releaseServiceProxy(qa, IProvideQa.class);
        }
        return qasResult;
    }

    private CDSRMetaVO extractMetadata(BaseType bt, Integer dfId, String name, String recordPath, int fullIssueNumber) {
        try {
            return helper.extractMetadata(name, Constants.FIRST_PUB, recordPath, fullIssueNumber, bt,
                    rps, flLogger.getRecordCache());
        } catch (CmsException ce) {
            reportFailedValidation(getPackageName(), dfId, name, ce.getMessage(), ce.getErrorInfo(), helper, flLogger);
        } catch (Exception e) {
            reportFailedValidation(getPackageName(), dfId, name, null, e.getMessage(), helper, flLogger);
        }
        return null;
    }

    public static String reportFailedValidation(String dfName, Integer dfId, String name, String msg, ErrorInfo ei,
                                                ContentHelper helper, IFlowLogger flLogger1) {
        IRecordStorage rs = RecordStorageFactory.getFactory().getInstance();
        String messageKey = MESSAGE_KEY_EXCEPTION;
        String title = null;
        if (ei == null || !(ei.getErrorEntity() instanceof ArchieEntry)) {
            setFailedValidationReport(dfName, name, msg, helper);
        } else {
            title = ((ArchieEntry) ei.getErrorEntity()).getTitle();
            switch (ei.getErrorType()) {
                case NO_UNIT_STATUS:
                    LOG.warn(String.format("%s does not contains status information", name));
                    helper.addMessageTag(RECORD_NAME, name);
                    helper.addMessageTag(AVAILABLE_STATUSES, getAvailableStatuses(rs));
                    messageKey = MESSAGE_KEY_NO_STATUS;
                    break;
                case WRONG_UNIT_STATUS:
                    String status = ei.getErrorDetail();
                    LOG.warn(String.format("%s contains unsupported status \"%s\"", name, status.toUpperCase()));
                    helper.addMessageTag(RECORD_NAME, name);
                    helper.addMessageTag(RECORD_STATUS, status);
                    helper.addMessageTag(AVAILABLE_STATUSES, getAvailableStatuses(rs));
                    messageKey = MESSAGE_KEY_UNSUPPORTED_STATUS;
                    break;
                case WRONG_DOI:
                    setFailedValidationReport(name, ei, CCA_DOI, "invalid doi", msg, helper);
                    messageKey = MESSAGE_KEY_CCA_DIFFERENT_DOIS;
                    break;
                case WRONG_DOI_PATTERN:
                    setFailedValidationReport(name, ei, CCA_DOI, "unrecognized doi", msg, helper);
                    messageKey = MESSAGE_KEY_CCA_WRONG_DOI;
                    break;
                case WRONG_ACCESSION_ID_DOI:
                    setFailedValidationReport(name, ei, CCA_ACSN_ID_DOI, "invalid 'accession id' doi", msg, helper);
                    messageKey = MESSAGE_KEY_CCA_DIFFERENT_DOIS;
                    break;
                case WRONG_ACCESSION_ID_REF:
                    setFailedValidationReport(name, ei, CCA_ACSN_ID_REF, "invalid 'accession id' ref", msg, helper);
                    messageKey = MESSAGE_KEY_CCA_DIFFERENT_DOIS;
                    break;
                default:
                    setFailedValidationReport(dfName, name, msg, helper);
            }
        }
        return reportFailedValidation(dfName, dfId, name, title, messageKey, helper, flLogger1);
    }

    private static void setFailedValidationReport(String name, ErrorInfo ei, String msgTag, String logMsg, String msg,
                                                  ContentHelper helper) {
        LOG.warn(String.format("%s %s: [%s] - %s", name, logMsg, ei.getErrorDetail(), msg));
        helper.addMessageTag(RECORD_NAME, name);
        helper.addMessageTag(msgTag, ei.getErrorDetail());
    }

    private static void setFailedValidationReport(String dfName, String name,  String msg, ContentHelper helper) {
        LOG.warn(String.format("%s - %s", name, msg));
        helper.addMessageTag(RECORD_NAME, name);
        helper.addMessageTag(DETAILS, msg);
    }

    private static boolean isActualPublicationDateSupportedAndNew(BaseType bt, Date[] prevDates) {
        return bt.isActualPublicationDateSupported() && prevDates == null;
    }

    private boolean checkPreviousDate(Integer dfId, String name, String title, Date[] prevDates, boolean successful) {
        if (prevDates != null && prevDates[0] == null) {
            if (successful) {
                helper.addMessageTag(RECORD_NAME, name);
                helper.addMessageTag(DETAILS, String.format(
                    "%s was already uploaded, is being sent for publication, and cannot be re-loaded right now", name));
                reportFailedValidation(getPackageName(), dfId, name, title, MESSAGE_KEY_EXCEPTION, helper, flLogger);
            }
            return false;
        }
        return successful;
    }

    private boolean checkDate(BaseType bt, Integer dfId, ICDSRMeta meta, String recordPath, Date[] dates,
                              boolean pubDateSupport, boolean pu) {
        InputStream is = null;
        try {
            is = rps.getFile(recordPath);
            String ml3gSource;
            if (pubDateSupport) {
                String pubDates = JatsMl3gAssetsManager.createPublishDateDescriptor(pu ? meta.getPublishedIssue()
                        : meta.getIssue(), bt, meta);
                ml3gSource = ContentHandler.updateWML3G(meta, ContentHandler.formatSource(is,
                    helper.getDocumentLoader()), null, ContentHandler.getMl3gToMl3gExtraXmls(pubDates), true,
                        CochraneCMSBeans.getConverter());
            } else {
                if (bt.isActualPublicationDateSupported() && !PublishHelper.checkInputOnlineDates(meta)) {
                    throw new Exception("an article cannot have pre-defined publication dates");
                }
                String dateStr = DATE_FORMAT.format(dates[0]);
                ml3gSource = replaceDate(IOUtils.toString(is, StandardCharsets.UTF_8), dateStr,
                    bt.isActualPublicationDateSupported() && dates[1] != null ? DATE_FORMAT.format(dates[1]) : null);
                LOG.debug("Dates are being updated in source XML [" + recordPath + "], new value is [" + dateStr + "]");
            }
            rps.putFile(recordPath, new ByteArrayInputStream(ml3gSource.getBytes()));
            return true;

        } catch (Exception e) {
            helper.addMessageTag(RECORD_NAME, meta.getCdNumber());
            helper.addMessageTag(DETAILS, "Updating date in source XML [" + recordPath + "] failed, " + e.getMessage());
            sendFailedValidationReport(dfId, meta.getCdNumber(), meta.getTitle(), MESSAGE_KEY_EXCEPTION);
        } finally {
            IOUtils.closeQuietly(is);
        }
        return false;
    }

    public static boolean checkRecordUnitStatus(String dfName, Integer dfId, CDSRMetaVO meta, Date curDate,
                                                Date[] prevDates, ContentHelper helper, IFlowLogger flLogger) {
        if (!isRecordPassNewStatusCorrectnessCheck(
                meta.getUnitStatusId(), curDate, prevDates == null ? null : prevDates[0])) {
            helper.addMessageTag(RECORD_NAME, meta.getCdNumber());
            reportFailedValidation(dfName, dfId, meta.getCdNumber(), meta.getTitle(),
                    MESSAGE_KEY_INCORRECT_STATUS_NEW, helper, flLogger);
            return false;
        }
        return true;
    }

    private boolean updateMetadata(BaseType bt, Integer dfId, CDSRMetaVO meta, boolean pubDateSupport,
                                   boolean successful) {
        if (pubDateSupport && meta != null) {
            try {
                rm.createRecord(bt, meta, flLogger.getRecordCache().getCRGGroup(GroupVO.SID_CCA),
                        deliveryFileId, false, false, 0);
                if (successful) {
                    CochraneCMSBeans.getVersionManager().populateMetadataVersion(meta);
                }
            } catch (CmsJTException ce) {
                reportFailedValidation(getPackageName(), dfId, meta.getCdNumber(), ce.getMessage(), ce.getErrorInfo(),
                        helper, flLogger);
                return false;
            }
        }
        return successful;
    }

    private RecordEntity getRecord(BaseType bt, String cdNumber, String recordPath) throws CmsJTException {
        ArchieEntry ae = new ArchieEntry(cdNumber, 0);
        ae.setPath(recordPath);
        int recordId  = rm.createRecord(bt, ae, deliveryFileId, null, false, false);
        return recs.getRecordEntityById(recordId);
    }

    private void updateQaResult(DeliveryPackageInfo dpi, ICDSRMeta meta, Pair<String, CcaEntity> en,
                                Integer unitStatusId, String result, boolean existing, QasResult qasResult) {
        String messages = result;
        if (result != null && result.contains(MESSAGES_OPEN_TAG)) {
            messages = result.substring(result.indexOf(MESSAGES_OPEN_TAG),
                    result.indexOf(MESSAGES_CLOSE_TAG) + MESSAGES_CLOSE_TAG.length());
        }
        RecordEntity re = qaService.updateCCARecord(meta.getName(), dpi.getDbName(), dpi.getIssueId(), en != null,
                messages, meta.getTitle(), unitStatusId);
        qasResult.toComplete.put(meta.getName(), re.getMetadata() != null && re.getMetadata().getVersion().isNewDoi());
        if (en != null) {
            qasResult.addCcaRecord(meta.getName(), en.second, existing);
        }
    }

    private boolean parseQaResult(Integer dfId, String result, String recordName, String title)
            throws JDOMException, IOException {
        StringBuilder errors = new StringBuilder();
        boolean successful = CmsUtils.getErrorsFromQaResults(result, errors, helper.getDocumentLoader());
        if (!successful) {
            helper.addMessageTag(DETAILS, errors.toString());
            sendFailedValidationReport(dfId, recordName, title, MESSAGE_KEY_QA);
        }
        return successful;
    }

    private String getSourceXmlWithAssets(String path) throws Exception {
        String directory = rps.getRealFilePath(path.replace(Extensions.XML, ""));
        StringBuilder javaFiles = new StringBuilder();
        javaFiles.append("<java_files>").append(SystemUtils.LINE_SEPARATOR);
        readDir(directory, javaFiles, "");
        javaFiles.append("</java_files>").append(SystemUtils.LINE_SEPARATOR);
        InputStream is = rps.getFile(path);
        String source = IOUtils.toString(is, XmlUtils.getEncoding(is));
        int index = source.indexOf("</component>");
        return source.substring(0, index) + javaFiles + source.substring(index);
    }

    private static void readDir(String dirPath, StringBuilder javaFiles, String basePath) {
        File[] files = new File(dirPath).listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    javaFiles.append("<file href=\"").append(basePath).append(file.getName()).append("\"/>").append(
                            SystemUtils.LINE_SEPARATOR);
                } else {
                    readDir(file.getAbsolutePath(), javaFiles, basePath + file.getName() + "/");
                }
            }
        }
    }

    private void updateRepository(DeliveryPackageInfo packageInfo, int dbId, int dfId, String packageFileName) {
        SortedMap<String, List<String>> records = packageInfo.getRecords();
        SortedMap<String, String> recPaths = packageInfo.getRecordPaths();
        LOG.debug("Number records=" + records.size());
        rs.createRecordsWithoutManifests(recPaths, dbId, dfId);
        DbStorageFactory.getFactory().getInstance().updateAllRecordCount(dbId);
        rs.setDeliveryFileStatus(dfId, IDeliveryFileStatus.STATUS_RECORDS_CREATED, true);
        logService.info(ActivityLogEntity.EntityLevel.FILE, ILogEvent.RECORDS_CREATED, dfId, packageFileName,
            theLogUser, packageInfo.getRecords().size() + " records");
    }

    @Override
    protected boolean parsePackage(URI packageUri) {
        try {
            initPackage(new DeliveryPackage(packageUri));
            return true;
        } catch (DeliveryPackageException de) {
            doOnWorkMethodException(de, packageUri, null);
            if (isFtpPackage(packageUri.toString())) {
                deletePackageOnFtp(new URIWrapper(packageUri), -1);
            }
        }
        return false;
    }

    private void sendFailedValidationReport(Integer dfId, String name, String title, String messageKey) {
        reportFailedValidation(getPackageName(), dfId, name, title, messageKey, helper, flLogger);
    }

    private static String reportFailedValidation(String dfName, Integer packageId, String name, String title,
                                                 String messageKey, ContentHelper helper, IFlowLogger flLogger) {
        String report = helper.setMessageTagReport(messageKey);
        if (flLogger != null) {
            sendFailedValidationReport(BaseType.getCCA().get(), dfName, packageId, name, title == null ? "" : title,
                    report, flLogger);
        }
        return report;
    }

    private static void sendFailedValidationReport(BaseType bt, String dfName, Integer packageId, String name,
                                                   String title, String report, IFlowLogger flLogger) {
        LOG.error(String.format("Sending failed validation report: CCA id = [%s]\nCCA title = [%s]\nDetails: %s",
                        name, title, report));
        flLogger.onProductUnpacked(dfName, packageId, name, null, bt.hasSFLogging(), false);
        flLogger.onProductError(ILogEvent.PRODUCT_VALIDATED, packageId, name, null, report,
                        true, bt.hasSFLogging(), false);

        MessageSender.sendRecordFailedValidationReport(bt, dfName, name, title, report, null);
    }

    private static boolean isCheckDoisEnabled(int unitStatusId) {
        return (!DOI_CHECK_DISABLED_4_STATUSES.contains(unitStatusId));
    }

    private static String getAvailableStatuses(IRecordStorage rs) {
        List<UnitStatusVO> usVOs = rs.getUnitStatusList(UnitStatusEntity.CCA_AVAILABLE_STATUSES);
        StringBuilder statuses = new StringBuilder();
        usVOs.forEach(usVO -> statuses.append(usVO.getName()).append(", "));
        return statuses.substring(0, statuses.length() - 2);
    }

    private static boolean isRecordPassNewStatusCorrectnessCheck(int unitStatusId, Date currentDate, Date prevDate) {
        return unitStatusId != UnitStatusEntity.UnitStatus.NEW || !CochraneCMSProperties.getBoolProperty(
                "cms.cochrane.cca.status.new.check", true) || isStatusNewCorrect(currentDate, prevDate);
    }

    private static boolean isStatusNewCorrect(Date currentDate, Date prevDate) {
        if (prevDate != null) {
            Calendar cl = Calendar.getInstance();
            cl.setTime(currentDate);
            cl.add(Calendar.MONTH, -1);
            Date monthBefore = cl.getTime();
            return monthBefore.before(prevDate);
        }
        return true;
    }

    private String replaceDate(String source, String date, String firstOnline) throws Exception {
        String newSource = EntityDecoder.encodeEntities(source);
        Document document = helper.getDocumentLoader().load(newSource);
        XPath xPath = XPath.newInstance(EVENT_PATH);
        xPath.addNamespace(A, Constants.WILEY_NAMESPACE_URI);
        Element evGroup = (Element) xPath.selectSingleNode(document);
        if (firstOnline != null) {
            updateEventDate(evGroup, FIRST_ONLINE_PATH, ContentChecker.TAG_FIRST_ONLINE, firstOnline);
            updateEventDate(evGroup, ONLINE_CITATION_ISSUE_PATH, ContentChecker.TAG_ONLINE_CITATION_ISSUE, firstOnline);
        } else {
            updateEventDate(evGroup, FIRST_ONLINE_PATH, ContentChecker.TAG_FIRST_ONLINE, date);
        }
        updateEventDate(evGroup, PUBLISH_ONLINE_PATH, ContentChecker.TAG_ONLINE_FINAL_FORM, date);
        XMLOutputter xmlOutputter = new XMLOutputter(Format.getRawFormat());
        return EntityDecoder.decodeEntities(xmlOutputter.outputString(document));
    }

    private static void updateEventDate(Element context, String path, String type, String date) throws JDOMException {
        XPath xPath = XPath.newInstance(path);
        xPath.addNamespace(A, Constants.WILEY_NAMESPACE_URI);
        Element el = (Element) xPath.selectSingleNode(context);
        if (el == null) {
            el = new Element("event", Namespace.getNamespace(Constants.WILEY_NAMESPACE_URI));
            el.setAttribute(new Attribute(TYPE_STR, type));
            el.setAttribute(new Attribute(DATE_STR, date));
            context.addContent(el);
        } else {
            el.getAttribute(DATE_STR).setValue(date);
        }
    }

    /**
     * CCA QAS Result
     * @author <a href='mailto:sgulin@wiley.com'>Svyatoslav Gulin</a>
     * @version 29.12.11
     */
    private static final class QasResult {
        private boolean result; // global qas result
        private final Map<String, CcaEntity> ccaRecords; // valid records
        private Map<String, CcaEntity> existingCcaRecords;
        private final Map<String, Boolean> toComplete = new HashMap<>(); // records to remove from caching at the end

        private QasResult(Map<String, CcaEntity> ccaRecords, boolean success) {
            this.ccaRecords = ccaRecords;
            result = success;
        }

        private void addCcaRecord(String recordName, CcaEntity ccaEntity, boolean existing) {
            ccaRecords.put(recordName, ccaEntity);
            if (existing) {
                if (existingCcaRecords == null) {
                    existingCcaRecords = new HashMap<>();
                }
                existingCcaRecords.put(recordName, ccaEntity);
            }
        }

        private void determineRecordsToFlowCompleted() {
            if (existingCcaRecords != null) {
                ccaRecords.entrySet().stream().filter((e) -> !existingCcaRecords.containsKey(e.getKey())).forEach(
                        e -> toComplete.remove(e.getKey()));
            } else {
                ccaRecords.forEach((k, v) -> toComplete.remove(k));
            }
        }
    }

    /**
     * CCA Publish Result
     * @author <a href='mailto:sgulin@wiley.com'>Svyatoslav Gulin</a>
     * @version 14.02.12
     */
    private static final class PublishResult {
        private static final PublishResult FAIL = new PublishResult(false, null);
        private final boolean result; // global publish result
        private final Collection<String> successPublishedRecords; // published records

        private PublishResult(boolean result, Collection<String> successPublishedRecords) {
            this.result = result;
            this.successPublishedRecords = successPublishedRecords;
        }
    }
}
