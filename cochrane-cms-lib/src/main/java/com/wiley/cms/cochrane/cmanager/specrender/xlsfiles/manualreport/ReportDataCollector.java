package com.wiley.cms.cochrane.cmanager.specrender.xlsfiles.manualreport;

/**
 * @author <a href='mailto:aasadulin@wiley.com'>Alexandr Asadulin</a>
 * @version 26.04.2017
 *
 * @param <I> input data type
 * @param <R> output data type
 */
public interface ReportDataCollector<R, I> {

    R getData(I inputData);
}
