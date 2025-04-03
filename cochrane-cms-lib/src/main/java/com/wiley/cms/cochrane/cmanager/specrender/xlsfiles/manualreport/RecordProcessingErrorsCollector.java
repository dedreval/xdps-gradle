package com.wiley.cms.cochrane.cmanager.specrender.xlsfiles.manualreport;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author <a href='mailto:aasadulin@wiley.com'>Alexandr Asadulin</a>
 * @version 27.04.2017
 */
public abstract class RecordProcessingErrorsCollector
        implements ReportDataCollector<Map<Integer, String>, List<Integer>> {

    private final ProcessingErrorsDataSource dataSource;

    public RecordProcessingErrorsCollector(ProcessingErrorsDataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Map<Integer, String> getData(List<Integer> recordIds) {
        if (recordIds.isEmpty()) {
            return Collections.emptyMap();
        } else {
            return dataSource.getData(recordIds);
        }
    }

    /**
     *
     */
    public interface ProcessingErrorsDataSource extends ReportDataSource<Map<Integer, String>, List<Integer>> {
    }
}
