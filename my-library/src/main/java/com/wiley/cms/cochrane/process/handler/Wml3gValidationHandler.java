package com.wiley.cms.cochrane.process.handler;

import java.io.IOException;
import java.io.InputStream;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import com.wiley.cms.cochrane.activitylog.IFlowLogger;
import com.wiley.cms.cochrane.activitylog.ILogEvent;
import com.wiley.cms.cochrane.cmanager.CmsException;
import com.wiley.cms.cochrane.cmanager.CmsUtils;
import com.wiley.cms.cochrane.cmanager.CochraneCMSBeans;
import com.wiley.cms.cochrane.cmanager.DeliveryPackage;
import com.wiley.cms.cochrane.cmanager.FilePathCreator;
import com.wiley.cms.cochrane.cmanager.IDeliveryFileStatus;
import com.wiley.cms.cochrane.cmanager.contentworker.ContentHelper;
import com.wiley.cms.cochrane.cmanager.data.meshterm.MeshtermStorageFactory;
import com.wiley.cms.cochrane.cmanager.data.record.IRecord;
import com.wiley.cms.cochrane.cmanager.data.record.RecordStorageFactory;
import com.wiley.cms.cochrane.cmanager.entitymanager.ICDSRMeta;
import com.wiley.cms.cochrane.cmanager.entitymanager.RecordHelper;
import com.wiley.cms.cochrane.cmanager.parser.Wml3gParser;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.converter.IConverterAdapter;
import com.wiley.cms.cochrane.converter.ml3g.JatsMl3gAssetsManager;
import com.wiley.cms.cochrane.converter.ml3g.Ml3gAssetsManager;
import com.wiley.cms.cochrane.meshtermmanager.JatsMeshtermManager;
import com.wiley.cms.cochrane.process.BaseAcceptQueue;
import com.wiley.cms.cochrane.process.CMSProcessManager;
import com.wiley.cms.cochrane.repository.IRepository;
import com.wiley.cms.cochrane.repository.RepositoryFactory;
import com.wiley.cms.cochrane.utils.ContentLocation;
import com.wiley.cms.converter.services.RevmanMetadataHelper;
import com.wiley.cms.process.ProcessException;
import com.wiley.cms.process.ProcessManager;
import com.wiley.cms.process.ProcessPartVO;
import com.wiley.cms.process.ProcessVO;
import com.wiley.tes.util.InputUtils;
import com.wiley.tes.util.Logger;
import com.wiley.tes.util.jdom.DocumentLoader;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.CharEncoding;
import org.apache.commons.lang.StringUtils;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 7/7/2019
 *
 */
public class Wml3gValidationHandler extends ContentHandler<DbHandler, BaseAcceptQueue, Map<String, IRecord>> {
    private static final long serialVersionUID = 1L;
    private static final Logger LOG = Logger.getLogger(Wml3gValidationHandler.class);
    private static final String N_A = "N/A";

    private Map<String, IRecord> initialRecords;

    public Wml3gValidationHandler() {
    }

    public Wml3gValidationHandler(DbHandler handler) {
        super(handler);
    }

    @Override
    protected Class<DbHandler> getTClass() {
        return DbHandler.class;
    }

    @Override
    public void onMessage(ProcessVO pvo, ProcessPartVO processPart) throws ProcessException {
        List<Integer> ids = getIdsParam(processPart);

        ErrorHolder errCollector = new ErrorHolder();
        ContentLocation location = getContentHandler().getContentLocation();
        String dbName = getContentHandler().getDbName();
        Map<ContentLocation, Collection<? extends IRecord>> collectedRecs = checkEntireMetadata(dbName, ids, location,
                false, false, errCollector);

        if (collectedRecs.isEmpty()) {
            LOG.warn("process part[%d] [%d] - no records found", processPart.getId(), processPart.parentId);
            processPart.setOutput(null);
            return;
        }

        processPart.setOutput(validateResults(processPart, errCollector, collectedRecs,
                CochraneCMSBeans.getFlowLogger()));
        String packageName = StringUtils.isNotBlank(getContentHandler().getDfName())
                ? getContentHandler().getDfName() : N_A;
        errCollector.sendConversionErrors(dbName, getContentHandler().getIssue(), location, packageName);
    }

    @Override
    protected boolean isMetaNotJats(ICDSRMeta meta, boolean isOnlyConvertTA) {
        return false;
    }

    private Map<String, IRecord> validateResults(ProcessPartVO processPart, ErrorHolder errCollector,
        Map<ContentLocation, Collection<? extends IRecord>> collectedRecs, IFlowLogger flLogger)
            throws ProcessException {

        Map<String, IRecord> results = new HashMap<>();
        for (Map.Entry<ContentLocation, Collection<? extends IRecord>> entry: collectedRecs.entrySet()) {
            results.putAll(validate(entry.getValue(), entry.getKey(), errCollector, processPart.getId(), flLogger));
        }
        return results;
    }

    @Override
    public void acceptResult(Map<String, IRecord> records) {
        initialRecords = records;
    }

    @Override
    public void acceptResult(PackageUnpackHandler fromHandler, ProcessVO from) {
        initialRecords = fromHandler.takeResult(from);
    }

    @Override
    public void passResult(ProcessVO pvo, IContentResultAcceptor to) {
        to.acceptResult(this, pvo);
    }

    @Override
    protected void onStartSync(ProcessVO pvo, List<ProcessPartVO> inputData, CMSProcessManager manager)
            throws ProcessException {

        super.onStartSync(pvo, inputData, manager);

        pvo.setOutput(validate(initialRecords.values(), getContentHandler().getContentLocation(), new ErrorHolder(),
                               pvo.getId(), manager.getFlowLogger()));
    }

    @Override
    public void logOnStart(ProcessVO pvo, ProcessManager manager) {
        super.logOnStart(pvo, manager);
        setDeliveryFileStatus(IDeliveryFileStatus.STATUS_QAS_STARTED, true);
    }

    @Override
    public void logOnEnd(ProcessVO pvo, ProcessManager manager) {
        super.logOnEnd(pvo, manager);
        setDeliveryFileStatus(IDeliveryFileStatus.STATUS_QAS_ACCEPTED, true);
    }

    @Override
    public void logOnFail(ProcessVO pvo, String msg, ProcessManager manager) {
        super.logOnFail(pvo, msg, manager);
        setDeliveryFileStatus(IDeliveryFileStatus.STATUS_QAS_FAILED, false);
    }

    private Map<String, IRecord> validate(Collection<? extends IRecord> initialData, ContentLocation cl,
        ErrorHolder errCollector, int processId, IFlowLogger flLogger) throws ProcessException {

        BaseType bt = BaseType.find(getContentHandler().getDbName()).get();
        boolean cdsr = bt.isCDSR();
        SAXParser saxParser = cdsr ? null : createParser(processId);
        Map<String, IRecord> results = new HashMap<>();
        IConverterAdapter conv = CochraneCMSBeans.getConverter();
        if (cdsr && hasDeliveryFile()) {
            setDeliveryFileStatus(IDeliveryFileStatus.STATUS_MESHTERM_UPDATING_STARTED, true);
        }

        for (IRecord record: initialData) {
            IRecord result = checkRecord(record, cl, saxParser, bt, errCollector, conv, flLogger);
            if (result != null) {
                if (cdsr && !hasDeliveryFile()) {
                    results.put(RevmanMetadataHelper.buildPubName(result.getName(), result.getPubNumber()), result);
                } else {
                    results.put(result.getName(), result);
                }
            }
        }

        if (hasDeliveryFile()) {
            boolean cca = !cdsr && bt.isCCA();
            boolean skipRender = cca || DeliveryPackage.isPropertyUpdateMl3g(getContentHandler().getDfName());
            if (cdsr) {
                setDeliveryFileStatus(results.isEmpty() ? IDeliveryFileStatus.STATUS_MESHTERM_UPDATING_FAILED
                        : IDeliveryFileStatus.STATUS_MESHTERM_UPDATING_ACCEPTED, !results.isEmpty());
            }
            RecordStorageFactory.getFactory().getInstance().flushQAResults(initialData, getContentHandler().getDbId(),
                    cdsr && DeliveryPackage.isAut(getContentHandler().getDfName()), false, true, skipRender);
            if (!skipRender) {
                CochraneCMSBeans.getRenderManager().resetRendering(results.values());
                
            } else {
                CochraneCMSBeans.getDeliveringService().reloadContent(
                        getContentHandler().getDfId(), results.values(), cdsr, processId);
            }
            String dbName = StringUtils.isNotBlank(getContentHandler().getDbName())
                    ? getContentHandler().getDbName() : N_A;
            errCollector.sendLoadPackageError(getContentHandler().getDfName(), dbName);
        }
        return results;
    }

    private static SAXParser createParser(int processId) throws ProcessException {
        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            return factory.newSAXParser();
        } catch (Exception e) {
            throw new ProcessException(e.getMessage(), processId);
        }
    }

    private IRecord checkRecord(IRecord record, ContentLocation cl, SAXParser parser, BaseType bt,
                                ErrorHolder errCollector, IConverterAdapter conv, IFlowLogger flLogger) {
        String cdNumber = record.getName();
        Integer issueId = getContentHandler().getIssueId();
        String dbName = getContentHandler().getDbName();
        Integer version = record.getHistoryNumber();
        boolean cdsr = bt.isCDSR();
        String pubDates = null;
        String descriptor = null;
        String ml3gSource = null;
        IRepository rp = RepositoryFactory.getRepository();
        boolean toDashboard = !bt.isCDSR() && hasDeliveryFile() && !DeliveryPackage.isPropertyUpdate(
                getContentHandler().getDfName());
        try {
            String recordPath = hasDeliveryFile() ? record.getRecordPath()
                    : cl.getPathToMl3g(issueId, dbName, version, cdNumber, false);
            if (cdsr)  {
                pubDates = JatsMl3gAssetsManager.createPublishDateDescriptor(record.getPublishedIssue(), bt, record);
                ml3gSource = updateWML3G(record, InputUtils.readStreamToString(rp.getFile(recordPath)),
                    JatsMeshtermManager.generateMeshTerms(record, MeshtermStorageFactory.getFactory().getInstance()),
                        getMl3gToMl3gExtraXmls(pubDates), true, conv);

            } else if (bt.isActualPublicationDateSupported()) {
                int flowIssue = hasDeliveryFile() && !DeliveryPackage.isPropertyUpdate(getContentHandler().getDfName())
                        ? getContentHandler().getIssue() : record.getPublishedIssue();
                pubDates = JatsMl3gAssetsManager.createPublishDateDescriptor(flowIssue, bt, record);                 
                ml3gSource = formatSource(rp.getFile(recordPath), new DocumentLoader());

                if (isAriesMl3g()) {  // make sure that flat structured resources will be mapped to standard folders
                    descriptor = JatsMl3gAssetsManager.generateAssetsAndPackageDescriptor(bt, record, issueId,
                        cl, true, new ContentHelper(), rp);
                    ml3gSource = updateWML3G(record, ml3gSource, null,
                        getMl3gToMl3gExtraXmls(descriptor, null, pubDates), true, conv);
                } else {              // legacy or already normalized format
                    ml3gSource = updateWML3G(record, ml3gSource, null, getMl3gToMl3gExtraXmls(pubDates), true, conv);
                }
            } else {
                ml3gSource = InputUtils.readStreamToString(rp.getFile(recordPath));
                InputStream is = IOUtils.toInputStream(ml3gSource, CharEncoding.UTF_8);
                parser.parse(is, new Wml3gParser().init(record));
            }
            String err = conv.validate(ml3gSource, IConverterAdapter.WILEY_ML3GV2_GRAMMAR);
            if (err != null && !err.isEmpty()) {
                throw new CmsException(err);
            }
            List<String> assetsUris = getAssetsUris(record, cl, issueId, dbName, cdsr, errCollector, rp);
            RecordHelper.putFile(ml3gSource, cl.getPathToMl3g(issueId, dbName, version, cdNumber, false), rp);
            RecordHelper.putFile(Ml3gAssetsManager.getAssetsFileContent(assetsUris),
                    cl.getPathToMl3gAssets(issueId, dbName, version, cdNumber), rp);
            if (bt.isCCA()) {
                RecordHelper.putFile(ml3gSource,
                    cl.getPathToMl21SrcRecord(issueId, dbName, getContentHandler().getDfId(), version, cdNumber), rp);
            }
            if (ContentHelper.saveTmpResults()) {
                saveTmpResults(cdNumber, null, null, null, descriptor, pubDates,
                    cl.getPathToMl3gTmpDir(issueId, dbName, version, cdNumber) + FilePathCreator.SEPARATOR, rp);
            }
            record.setSuccessful(true);
            if (toDashboard) {
                flLogger.onProductValidated(cdNumber, bt.hasSFLogging(), CmsUtils.isScheduledIssue(issueId));
            }
            return record;

        } catch (Throwable tr) {

            record.setSuccessful(false);
            errCollector.addError(cdNumber, tr.getMessage());
            logWml3gError(BaseType.find(dbName).get().getDbId(), getContentHandler().getIssue(), record.getId(),
                    cdNumber, tr.getMessage(), flLogger.getActivityLog());
            if (toDashboard) {
                flLogger.onProductError(ILogEvent.PRODUCT_VALIDATED, getContentHandler().getDfId(), cdNumber, null,
                        tr.getMessage(), true, bt.hasSFLogging(), CmsUtils.isScheduledIssue(issueId));
            }
            flLogger.getRecordCache().removeRecord(cdNumber);
            LOG.error(tr.getMessage());
            saveTmpResults(cdNumber, null, null, ml3gSource, descriptor, pubDates,
                cl.getPathToMl3gTmpDir(issueId, dbName, version, cdNumber) + FilePathCreator.SEPARATOR, rp);
        }
        return null;
    }

    private boolean isAriesMl3g() {
        return hasDeliveryFile() && !DeliveryPackage.isMl3g(getContentHandler().getDfName());
    }

    private List<String> getAssetsUris(IRecord recVO, ContentLocation cl, int issueId, String dbName, boolean cdsr,
                                       ErrorHolder errCollector, IRepository rp) throws IOException {
        if (cdsr) {
            String pathToAssets = hasDeliveryFile()
                                 ? ContentLocation.ENTIRE.getPathToMl3gAssets(issueId, dbName, recVO.getHistoryNumber(),
                                                                              recVO.getName())
                                 : cl.getPathToMl3gAssets(issueId, dbName, recVO.getHistoryNumber(), recVO.getName());
            return IOUtils.readLines(rp.getFile(pathToAssets), CharEncoding.UTF_8);
        } else {
            return Ml3gAssetsManager.collectIssueAssets(issueId, dbName, recVO, errCollector.errs());
        }
    }
}
