package com.wiley.cms.cochrane.cmanager.specrender.xlsfiles.manualreport;

/**
 * @author <a href='mailto:aasadulin@wiley.com'>Alexandr Asadulin</a>
 * @version 28.04.2017
 *
 * @param <I> input data type
 * @param <R> output data type
 */
public interface ReportDataSource<R, I> {

    R getData(I inputData);
}
