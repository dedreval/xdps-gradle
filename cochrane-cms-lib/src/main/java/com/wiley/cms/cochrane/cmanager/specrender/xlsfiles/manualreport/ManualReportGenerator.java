package com.wiley.cms.cochrane.cmanager.specrender.xlsfiles.manualreport;

import com.wiley.cms.cochrane.cmanager.entitywrapper.DbWrapper;
import com.wiley.cms.cochrane.cmanager.specrender.xlsfiles.manualreport.ManualReportDataCollector.ErrorsStrategy;
import com.wiley.cms.cochrane.utils.Constants;
import com.wiley.cms.cochrane.utils.XLSHelper;
import com.wiley.tes.util.Logger;
import com.wiley.tes.util.res.Property;
import com.wiley.tes.util.res.Res;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author <a href='mailto:aasadulin@wiley.com'>Alexandr Asadulin</a>
 * @version 11.05.2017
 */
public class ManualReportGenerator {
    private static final Logger LOG = Logger.getLogger(ManualReportGenerator.class);

    private static final Res<Property> ROW_LIMIT = Property.get("cms.cochrane.report.page.maxrow", "60000");

    private final Set<Header> activeHeaders;
    private final IncrementalRecordIdCollector recordIdCollector;
    private final ManualReportDataCollector reportDataCollector;
    private final XlsHelperCreator xlsHelperCreator;

    public ManualReportGenerator(Set<Header> activeHeaders,
                                 IncrementalRecordIdCollector recordIdCollector,
                                 ManualReportDataCollector reportDataCollector,
                                 XlsHelperCreator xlsHelperCreator) {
        this.activeHeaders = activeHeaders;
        this.recordIdCollector = recordIdCollector;
        this.reportDataCollector = reportDataCollector;
        this.xlsHelperCreator = xlsHelperCreator;
    }

    public ManualReportGenerator(int dbId, boolean entire, String[] recordNames, Set<Header> activeHeaders) {
        this.activeHeaders = activeHeaders;
        this.recordIdCollector = new IncrementalRecordIdCollector(dbId, entire, recordNames);
        this.reportDataCollector = createManualReportDataCollector(dbId, entire);
        this.xlsHelperCreator = new XlsHelperCreator();
    }

    private ManualReportDataCollector createManualReportDataCollector(int dbId, boolean entire) {
        int fullIssueNumb = getFullIssueNumber(dbId, entire);
        ErrorsStrategy errorsStrategy = (activeHeaders.contains(Header.ERRORS)
                        ? ErrorsStrategy.WITH_ERRORS_ONLY
                        : ErrorsStrategy.NO_ERRORS);
        return new ManualReportDataCollector(fullIssueNumb, errorsStrategy);
    }

    private int getFullIssueNumber(int dbId, boolean entire) {
        if (entire) {
            return Constants.NO_ISSUE;
        } else {
            DbWrapper dbWrapper = new DbWrapper(dbId);
            return dbWrapper.getIssue().getFullNumber();
        }
    }

    public void generate(String xlsFilePath) {
        XLSHelper xlsHelper = xlsHelperCreator.create(xlsFilePath);
        writeDataToXls(xlsHelper);
    }

    private void writeDataToXls(XLSHelper xlsHelper) {

        RowData rowData = new RowData();
        do  {
            writeHeader(xlsHelper);
            writeBody(xlsHelper, rowData);

        } while (!rowData.end);

        completeXlsWriting(xlsHelper);
    }

    private void writeHeader(XLSHelper xlsHelper) {
        int col = 0;
        for (Header header : Header.values()) {
            if (activeHeaders.contains(header)) {
                appendHeader(header.getName(), col++, xlsHelper);
            }
        }
    }

    private void appendHeader(String name, int col, XLSHelper xlsHelper) {
        try {
            xlsHelper.addTitle(name, col);
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private void writeBody(XLSHelper xlsHelper, RowData rowData) {
        int limit = ROW_LIMIT.get().asInteger() + 1;

        List<Integer> recordIds = recordIdCollector.getData(rowData.offset);
        while (!recordIds.isEmpty()) {

            List<ManualReportDataCollector.RecordData> recordDataList = reportDataCollector.getData(recordIds);

            int size = recordDataList.size();
            if (rowData.startingRow + size  > limit) {

                LOG.info(String.format("report: %s; next_chunk=%d", rowData, size));

                if (rowData.startingRow == 1) {
                    LOG.error(String.format("report stopped: %s; next_chunk=%d > LIMIT=%d!", rowData, size, limit));
                    rowData.end = true;  // stop paging as recordDataList.size() > LIMIT

                } else {
                    rowData.startingRow = 1;
                    xlsHelper.addCurrentSheet("" + ++rowData.page);
                }

                return;
            }

            appendBodyRows(recordDataList, xlsHelper, rowData.startingRow);
            rowData.allRows += size;
            rowData.offset += recordIds.size();
            rowData.startingRow += size;
            recordIds = recordIdCollector.getData(rowData.offset);
        }

        rowData.end = true;
    }

    private void appendBodyRows(List<ManualReportDataCollector.RecordData> recordDataList,
                                XLSHelper xlsHelper,
                                int startingRow) {
        int row = startingRow;
        for (ManualReportDataCollector.RecordData recordData : recordDataList) {
            List<String> values = getValuesForActiveHeaders(recordData);
            appendValuesToBodyRow(values, row++, xlsHelper);
        }
    }

    private List<String> getValuesForActiveHeaders(ManualReportDataCollector.RecordData recordData) {
        RecordMetadataCollector.RecordMetadata recordMetadata = recordData.getRecordMetadata();
        List<String> values = new ArrayList<String>(activeHeaders.size());
        values.add(recordMetadata.getName());
        addValueIfHeaderActive(recordMetadata.getTitle(), Header.TITLE, values);
        addValueIfHeaderActive(recordMetadata.getStatus(), Header.STATUS, values);
        addValueIfHeaderActive(recordMetadata.getSubtitle(), Header.SUBTITLE, values);
        addValueIfHeaderActive(recordMetadata.getGroup(), Header.GROUP, values);
        addValueIfHeaderActive(recordData.getErrors(), Header.ERRORS, values);
        return values;
    }

    private void addValueIfHeaderActive(String value, Header header, List<String> values) {
        if (activeHeaders.contains(header)) {
            values.add(value);
        }
    }

    private void appendValuesToBodyRow(List<String> values, int row, XLSHelper xlsHelper) {
        for (int col = 0; col < values.size(); col++) {
            appendValueToBodyCell(values.get(col), col, row, xlsHelper);
        }
    }

    private void appendValueToBodyCell(String value, int col, int row, XLSHelper xlsHelper) {
        try {
            xlsHelper.addValue(value, col, row);
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private void completeXlsWriting(XLSHelper xlsHelper) {
        try {
            xlsHelper.close();
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    /**
     *
     */
    public static class XlsHelperCreator {

        public XLSHelper create(String xlsFilePath) {
            File xlsFile = new File(xlsFilePath);
            return createXlsHelper(xlsFile);
        }

        private XLSHelper createXlsHelper(File xlsFile) {
            try {
                return new XLSHelper(xlsFile);
            } catch (Exception e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }
    }

    private static class RowData {
        int allRows = 0;
        int offset = 0;
        int startingRow = 1;
        int page = 1;

        boolean end = false;

        @Override
        public String toString() {
            return String.format("page=%d, current_row=%d, all_rows=%d, offset=%d", page, startingRow, allRows, offset);
        }
    }

    /**
     *
     */
    public enum Header {

        RECORD_ID("Record ID"),
        TITLE("Title"),
        STATUS("Status"),
        SUBTITLE("Product subtitle"),
        GROUP("Group name"),
        ERRORS("Errors");

        private final String name;

        Header(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }
}
