package com.wiley.cms.cochrane.cmanager.specrender.xlsfiles.manualreport;

import com.wiley.cms.cochrane.cmanager.data.record.IRecordStorage;
import com.wiley.cms.cochrane.cmanager.entitymanager.AbstractManager;
import com.wiley.cms.process.ProcessHelper;
import org.apache.commons.lang.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author <a href='mailto:aasadulin@wiley.com'>Alexandr Asadulin</a>
 * @version 28.06.2017
 */
public class QaErrorsCollector extends RecordProcessingErrorsCollector {

    public QaErrorsCollector() {
        this(new QaErrorsSource());
    }

    public QaErrorsCollector(QaErrorsSource dataSource) {
        super(dataSource);
    }

    /**
     *
     */
    public static class QaErrorsSource implements ProcessingErrorsDataSource {

        private final IRecordStorage recordStorage = AbstractManager.getRecordStorage();

        public Map<Integer, String> getData(List<Integer> recordIds) {
            Map<Integer, String> processingResults = requestProcessingResults(recordIds);
            return extractErrors(processingResults);
        }

        private Map<Integer, String> requestProcessingResults(List<Integer> recordIds) {
            Map<Integer, String> processingResults = new HashMap<>();
            recordStorage.getLatestQasResultsByRecordsIds(recordIds, processingResults);
            return processingResults;
        }

        private Map<Integer, String> extractErrors(Map<Integer, String> processingResults) {
            Map<Integer, String> errors = new HashMap<>();
            for (Map.Entry<Integer, String> entry: processingResults.entrySet()) {
                String error = ProcessHelper.parseErrorReportMessageXml(entry.getValue(), false);
                if (StringUtils.isNotEmpty(error)) {
                    errors.put(entry.getKey(), error);
                } else if (StringUtils.isNotEmpty(entry.getValue())
                        && !entry.getValue().endsWith(ProcessHelper.REPORT_MSG_FINAL_TAG)) {
                    errors.put(entry.getKey(), entry.getValue());
                }
            }
            return errors;
        }
    }
}
