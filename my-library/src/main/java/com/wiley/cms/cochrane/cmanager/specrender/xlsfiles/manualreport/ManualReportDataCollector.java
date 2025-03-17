package com.wiley.cms.cochrane.cmanager.specrender.xlsfiles.manualreport;

import com.wiley.cms.cochrane.utils.Constants;
import org.apache.commons.lang.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author <a href='mailto:aasadulin@wiley.com'>Alexandr Asadulin</a>
 * @version 28.04.2017
 */
public class ManualReportDataCollector
        implements ReportDataCollector<List<ManualReportDataCollector.RecordData>, List<Integer>> {

    private final ErrorsStrategy errorsStrategy;
    private final RecordMetadataCollector metadataCollector;
    private final List<RecordProcessingErrorsCollector> errorsCollectors;

    public ManualReportDataCollector(ErrorsStrategy errorsStrategy,
                                     RecordMetadataCollector metadataCollector,
                                     List<RecordProcessingErrorsCollector> errorsCollectors) {
        this.errorsStrategy = errorsStrategy;
        this.metadataCollector = metadataCollector;
        this.errorsCollectors = errorsCollectors;
    }

    public ManualReportDataCollector(int fullIssueNumb, ErrorsStrategy errorsStrategy) {
        this.errorsStrategy = errorsStrategy;
        this.metadataCollector = createMetadataCollector(fullIssueNumb);
        this.errorsCollectors = createErrorsCollectors(fullIssueNumb);
    }

    private RecordMetadataCollector createMetadataCollector(int fullIssueNumb) {
        boolean entire = (fullIssueNumb == Constants.NO_ISSUE);
        return new RecordMetadataCollector(entire);
    }

    private List<RecordProcessingErrorsCollector> createErrorsCollectors(int fullIssueNumb) {
        if (errorsStrategy == ErrorsStrategy.WITH_ERRORS_ONLY) {
            List<RecordProcessingErrorsCollector> collectors = new ArrayList<RecordProcessingErrorsCollector>();
            if (fullIssueNumb != Constants.NO_ISSUE) {
                collectors.add(new QaErrorsCollector());
                collectors.add(new HWFailureErrorsCollector(fullIssueNumb));
            }
            collectors.add(new Wml3gConversionErrorsCollector(fullIssueNumb));
            return collectors;
        } else {
            return Collections.emptyList();
        }
    }

    public List<RecordData> getData(List<Integer> recordIds) {
        Map<Integer, String> processingErrors = getProcessingErrors(recordIds);
        List<RecordMetadataCollector.RecordMetadata> recordMetadataLst = getRecordMetadata(recordIds, processingErrors);
        return createRecordData(recordMetadataLst, processingErrors);
    }

    private Map<Integer, String> getProcessingErrors(List<Integer> recordIds) {
        Map<Integer, String> errors = new HashMap<Integer, String>();
        for (RecordProcessingErrorsCollector errorsCollector : errorsCollectors) {
            Map<Integer, String> collectedErrors = errorsCollector.getData(recordIds);
            addCollectedErrors(collectedErrors, errors);
        }
        return errors;
    }

    private void addCollectedErrors(Map<Integer, String> collectedErrors, Map<Integer, String> errors) {
        for (Integer id : collectedErrors.keySet()) {
            String error = collectedErrors.get(id);
            if (errors.containsKey(id)) {
                error = errors.get(id) + "\n" + error;
            }
            errors.put(id, error);
        }
    }

    private List<RecordMetadataCollector.RecordMetadata> getRecordMetadata(List<Integer> recordIds,
                                                                           Map<Integer, String> processingErrors) {
        List<Integer> requestingRecordIds = chooseRequestingRecordIds(recordIds, processingErrors);
        return metadataCollector.getData(requestingRecordIds);
    }

    private List<Integer> chooseRequestingRecordIds(List<Integer> recordIds, Map<Integer, String> processingErrors) {
        if (errorsStrategy == ErrorsStrategy.WITH_ERRORS_ONLY) {
            return new ArrayList<Integer>(processingErrors.keySet());
        } else {
            return recordIds;
        }
    }

    private List<RecordData> createRecordData(List<RecordMetadataCollector.RecordMetadata> recordMetadataLst,
                                              Map<Integer, String> processingErrors) {
        List<RecordData> recordDataLst = new ArrayList<RecordData>(recordMetadataLst.size());
        for (RecordMetadataCollector.RecordMetadata recordMetadata : recordMetadataLst) {
            String errors = processingErrors.get(recordMetadata.getId());
            RecordData recordData = new RecordData(recordMetadata, errors);
            recordDataLst.add(recordData);
        }
        return recordDataLst;
    }

    /**
     *
     */
    public static class RecordData {

        private final RecordMetadataCollector.RecordMetadata recordMetadata;
        private final String errors;

        public RecordData(RecordMetadataCollector.RecordMetadata recordMetadata, String errors) {
            this.recordMetadata = recordMetadata;
            this.errors = StringUtils.isEmpty(errors) ? StringUtils.EMPTY : errors;
        }

        public RecordMetadataCollector.RecordMetadata getRecordMetadata() {
            return recordMetadata;
        }

        public String getErrors() {
            return errors;
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof RecordData) {
                RecordData that = (RecordData) o;
                return recordMetadata.equals(that.recordMetadata) && errors.equals(that.errors);
            }
            return false;

        }

        //CHECKSTYLE:OFF MagicNumber
        @Override
        public int hashCode() {
            int result = recordMetadata.hashCode();
            result = 31 * result + errors.hashCode();
            return result;
        }
        //CHECKSTYLE:ON MagicNumber
    }

    /**
     *
     */
    public enum ErrorsStrategy {

        NO_ERRORS, WITH_ERRORS_ONLY
    }
}
