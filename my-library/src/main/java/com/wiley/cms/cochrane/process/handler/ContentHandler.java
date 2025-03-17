package com.wiley.cms.cochrane.process.handler;

import com.wiley.cms.cochrane.activitylog.IActivityLog;
import com.wiley.cms.cochrane.activitylog.ILogEvent;
import com.wiley.cms.cochrane.cmanager.FilePathCreator;
import com.wiley.cms.cochrane.cmanager.MessageSender;
import com.wiley.cms.cochrane.cmanager.data.DeliveryFileVO;
import com.wiley.cms.cochrane.cmanager.data.entire.EntireDBStorageFactory;
import com.wiley.cms.cochrane.cmanager.data.record.IRecord;
import com.wiley.cms.cochrane.cmanager.entitymanager.ICDSRMeta;
import com.wiley.cms.cochrane.cmanager.entitymanager.RecordHelper;
import com.wiley.cms.cochrane.cmanager.entitywrapper.ResultStorageFactory;
import com.wiley.cms.cochrane.converter.IConverterAdapter;
import com.wiley.cms.cochrane.process.CMSProcessManager;
import com.wiley.cms.cochrane.repository.IRepository;
import com.wiley.cms.cochrane.utils.Constants;
import com.wiley.cms.cochrane.utils.ContentLocation;
import com.wiley.cms.converter.services.RevmanMetadataHelper;
import com.wiley.cms.process.FunctionalHandler;
import com.wiley.cms.process.ProcessException;
import com.wiley.cms.process.ProcessHandler;
import com.wiley.cms.process.ProcessVO;
import com.wiley.tes.util.DbUtils;
import com.wiley.tes.util.Extensions;
import com.wiley.tes.util.Logger;
import com.wiley.tes.util.XmlUtils;
import com.wiley.tes.util.jdom.DocumentLoader;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.validation.constraints.NotNull;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 7/7/2019
 *
 * @param <H>
 * @param <Q>
 * @param <R>
 */
public abstract class ContentHandler <H extends DbHandler, Q, R> extends FunctionalHandler<H, CMSProcessManager, Q>
        implements IContentResultAcceptor<R> {
    protected static final Logger LOG = Logger.getLogger(ContentHandler.class);

    static final String NO_JATS_META_FOUND = "no JATS metadata found";

    private static final long serialVersionUID = 1L;

    public ContentHandler() {
    }

    public ContentHandler(H handler) {
        super(handler);
    }

    @Override
    public void pass(ProcessVO pvo, ProcessHandler to) {
        super.pass(pvo, to);

        if (to instanceof IContentResultAcceptor) {
            passResult(pvo, (IContentResultAcceptor) to);
        }
    }

    protected void setDeliveryFileStatus(int status, boolean interim) {
        if (hasDeliveryFile()) {
            ResultStorageFactory.getFactory().getInstance().setDeliveryFileStatus(
                    getContentHandler().getDfId(), status, interim);
        }
    }

    protected void updateDeliveryFileStatus(int status) {
        if (hasDeliveryFile()) {
            ResultStorageFactory.getFactory().getInstance().updateDeliveryFileStatus(
                    getContentHandler().getDfId(), status, DeliveryFileVO.FINAL_STATES);
        }
    }

    protected boolean checkInitialRecords(ProcessVO pvo, Map<String, IRecord> startRecords, boolean throwErr,
                                          int dfStatus) throws ProcessException {
        if (startRecords == null || startRecords.isEmpty()) {
            if (hasDeliveryFile()) {
                setDeliveryFileStatus(dfStatus, false);
            }
            if (throwErr) {
                throw new ProcessException("no initial records");
            }
            LOG.warn(String.format("no initial records, process %s", pvo));
            return false;
        }
        return true;
    }

    public boolean hasDeliveryFile() {
        return DbUtils.exists(getContentHandler().getDfId());
    }

    protected void log4Package(String packageName, String message) {
        if (packageName != null) {
            LOG.debug(String.format("%s [%d] -> %s", packageName, getContentHandler().getDfId(), message));
        }
    }

    protected void log4PackageFail(String packageName, String message) {
        if (packageName != null) {
            LOG.error(String.format("%s [%d] failed -> %s", packageName, getContentHandler().getDfId(), message));
        }
    }

    public static String updateWML3G(IRecord record, String source, String meshTerms, Map<String, String> extraXmls,
        boolean strictValidation, IConverterAdapter conv) throws Exception {
        try {
            Map<String, String> toMl3gExtraXmls = getMl3gToMl3gExtraXmls(meshTerms, extraXmls);
            return toMl3gExtraXmls == null ? source : conv.convertSource(source, IConverterAdapter.WILEY_ML3G,
                    strictValidation ? IConverterAdapter.WILEY_ML3G_CV : IConverterAdapter.WILEY_ML3G,
                                 getMl3gToMl3gParams(), toMl3gExtraXmls, strictValidation);
        } catch (Exception e) {
            throw new Exception(String.format("unable to update Wiley ML3G for %s - %s",
                    RevmanMetadataHelper.buildPubName(record.getName(), record.getPubNumber()), e.getMessage()));
        }
    }

    protected static Map<String, String> getMl3gToMl3gExtraXmls(String meshTerms, Map<String, String> extraXmls) {
        if (meshTerms != null) {
            Map<String, String> ret = extraXmls == null ? new HashMap<>(1) : extraXmls;
            ret.put("conv-meshterms", meshTerms);
            return ret;
        } else {
            return extraXmls;
        }
    }

    protected static Map<String, String> getMl3gToMl3gExtraXmls(@NotNull String packageDescriptor, String topics,
                                                                String pubDates) {
        Map<String, String> extraXmls = new HashMap<>();
        extraXmls.put("conv-package-descriptor", packageDescriptor);
        if (topics != null) {
            extraXmls.put("conv-topics", topics);
        }
        return addPubDatesToConversion(pubDates, extraXmls);
    }

    public static Map<String, String> getMl3gToMl3gParams() {
        Map<String, String> params = new HashMap<>(1);
        params.put("param-alternatives", "yes");
        return params;
    }

    public static Map<String, String> getMl3gToMl3gExtraXmls(String pubDates) {
        return pubDates == null ? null : addPubDatesToConversion(pubDates, new HashMap<>(1));
    }

    public static String formatSource(InputStream is, DocumentLoader loader) throws Exception {
        try {
            String encoding = XmlUtils.getEncoding(is);
            String source = IOUtils.toString(is, encoding);
            XMLOutputter xmlOutputter = new XMLOutputter(Format.getRawFormat());
            return xmlOutputter.outputString(loader.load(source.getBytes(StandardCharsets.UTF_8)));

        } finally {
            IOUtils.closeQuietly(is);
        }
    }

    private static Map<String, String> addPubDatesToConversion(String pubDates, Map<String, String> extraXmls) {
        if (pubDates != null) {
            extraXmls.put("conv-dates", pubDates);
        }
        return extraXmls;
    }

    protected static StringBuilder addError(String pubName, String error, StringBuilder sb) {
        StringBuilder ret = sb;
        ret = ret == null ? new StringBuilder(pubName) : ret.append("\n").append(pubName);
        if (error != null) {
            ret.append(" - ").append(error);
        }
        LOG.warn(String.format("%s for %s", error, pubName));
        return ret;
    }

    protected static void logWml3gError(int dbType, int issueNumber, int recordId, String cdNumber,
                                        String error, IActivityLog logService) {
        logError(dbType, issueNumber, recordId, cdNumber, ILogEvent.CONVERSION_TO_3G_FAILED, error, logService);
    }

    protected static void logError(int dbType, int issueNumber, int recordId, String cdNumber, int logEvent,
                                   String error, IActivityLog logService) {
        logService.logRecordError(logEvent, recordId, cdNumber, dbType, issueNumber, error);
    }

    protected Map<ContentLocation, Collection<? extends IRecord>> checkEntireMetadata(String dbName, List<Integer> ids,
            ContentLocation cl, boolean isOnlyConvertTA, boolean setJatsPath, IErrorCollector errCollector) {

        List<String> initialNames = EntireDBStorageFactory.getFactory().getInstance().findRecordNames(ids);
        if (initialNames.isEmpty()) {
            return Collections.emptyMap();
        }

        boolean withPrev = getContentHandler().hasPrevious();
        Map<String, ICDSRMeta> lastMetadata = initialNames.isEmpty() ? Collections.emptyMap()
                : ResultStorageFactory.getFactory().getInstance().findLatestMetadata(initialNames, withPrev);

        List<ICDSRMeta> prevList = withPrev ? new ArrayList<>() : null;

        for (String name: initialNames) {
            ICDSRMeta meta = lastMetadata.get(name);
            if (meta == null) {
                errCollector.addJatsError(name, "no proper metadata found");
                continue;

            } else if (isMetaNotJats(meta, isOnlyConvertTA)) {
                errCollector.addJatsError(meta, NO_JATS_META_FOUND);
                lastMetadata.remove(name);
                continue;
            }
            if (setJatsPath) {
                meta.setRecordPath(cl.getPathToJatsSrcDir(null, dbName, null,  null, name)
                        + FilePathCreator.SEPARATOR + name + Extensions.XML);
            }
            if (prevList == null) {
                continue;
            }
            ICDSRMeta history = meta.getHistory();
            while (history != null) {
                if (isMetaNotJats(history, isOnlyConvertTA)) {
                    errCollector.addJatsError(history, String.format("no JATS metadata found for a previous version %d",
                            history.getHistoryNumber()));
                } else if (setJatsPath){
                    history.setRecordPath(ContentLocation.PREVIOUS.getPathToJatsSrcDir(null, dbName, null,
                            history.getHistoryNumber(), name) + FilePathCreator.SEPARATOR + name + Extensions.XML);
                    prevList.add(history);
                } else {
                    prevList.add(history);
                }
                history = history.getHistory();
            }
        }
        return collectStartRecords(dbName, lastMetadata.values(), prevList, cl, ContentLocation.PREVIOUS, errCollector);
    }

    protected boolean isMetaNotJats(ICDSRMeta meta, boolean isOnlyConvertTA) {
        return !meta.isJats() && !isOnlyConvertTA;
    }

    protected Map<ContentLocation, Collection<? extends IRecord>> collectStartRecords(String dbName,
            Collection<? extends IRecord> lastRecords, Collection<? extends IRecord> prevRecords,
            ContentLocation lastLocation, ContentLocation prevLocation, IErrorCollector errorCollector) {

        Map<ContentLocation, Collection<? extends IRecord>> ret = new HashMap<>();
        if (!lastRecords.isEmpty()) {
            ret.put(lastLocation, lastRecords);
        }
        if (prevRecords != null && !prevRecords.isEmpty()) {
            ret.put(prevLocation, prevRecords);
        }
        String packageName = StringUtils.isNotBlank(getContentHandler().getDfName())
                ? getContentHandler().getDfName() : "N/A";
        errorCollector.sendConversionErrors(dbName, getContentHandler().getIssue(), lastLocation, packageName);
        return ret;
    }

    protected static void saveTmpResults(String recordName, String firstResult, String englishOnlyResult,
            String finalResult, String descriptor, String pubDates, String path, IRepository rp) {
        try {
            if (firstResult != null) {
                RecordHelper.putFile(firstResult, path + recordName + Extensions.XML, rp);
            }
            if (descriptor != null) {
                RecordHelper.putFile(descriptor, path + Constants.PACKAGE_DESCRIPTOR + Extensions.XML, rp);
            }
            if (englishOnlyResult != null) {
                RecordHelper.putFile(englishOnlyResult, path + recordName + "_en" + Extensions.XML, rp);
            }
            if (finalResult != null) {
                RecordHelper.putFile(finalResult, path + recordName + "_last" + Extensions.XML, rp);
            }
            if (pubDates != null) {
                RecordHelper.putFile(pubDates, path + Constants.PUB_DATES + Extensions.XML, rp);
            }
        } catch (Exception e) {
            LOG.warn(e.getMessage());
        }
    }

    protected interface IErrorCollector {

        void addJatsError(ICDSRMeta meta, String err);

        void addJatsError(String name, String err);

        void sendConversionErrors(String dbName, int issue, ContentLocation cl, String packageName);
    }

    protected static class ErrorHolder implements IErrorCollector {
        private StringBuilder errs;

        protected StringBuilder errs() {
            return errs == null ? new StringBuilder() : errs;
        }

        protected void sendLoadPackageError(String dfName, String dbName) {
            if (errs != null && errs.length() > 0) {
                MessageSender.sendFailedLoadPackageMessage(dfName, errs.toString(), dbName, null);
                errs = null;
            }
        }

        @Override
        public void sendConversionErrors(String dbName, int issue, ContentLocation cl, String packageName) {
            if (errs != null && errs.length() > 0) {
                MessageSender.sendWml3gConversion(packageName, cl.getShortString(issue, dbName, null), errs.toString());
                errs = null;
            }
        }

        @Override
        public void addJatsError(ICDSRMeta metaData, String err) {
            String pubName = RevmanMetadataHelper.buildPubName(metaData.getName(), metaData.getPubNumber());
            addError(pubName, err);
        }

        @Override
        public void addJatsError(String name, String err) {
            addError(name, err);
        }

        protected void addError(String pubName, String err) {
            errs = ContentHandler.addError(pubName, err, errs);
        }

        protected void addError(IRecord record, String error) {
            errs = ContentHandler.addError(
                    RevmanMetadataHelper.buildPubName(record.getName(), record.getPubNumber()), error, errs);
        }
    }
}


