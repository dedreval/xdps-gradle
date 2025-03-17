package com.wiley.cms.cochrane.cmanager.specrender.xlsfiles;

import com.wiley.cms.cochrane.activitylog.ActivityLogEntity;
import com.wiley.cms.cochrane.activitylog.ActivityLogFactory;
import com.wiley.cms.cochrane.activitylog.ErrorLogEntity;
import com.wiley.cms.cochrane.activitylog.IActivityLogService;
import com.wiley.cms.cochrane.activitylog.ILogEvent;
import com.wiley.cms.cochrane.cmanager.CochraneCMSBeans;
import com.wiley.cms.cochrane.cmanager.CochraneCMSProperties;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.FilePathCreator;
import com.wiley.cms.cochrane.cmanager.MessageSender;
import com.wiley.cms.cochrane.cmanager.data.DatabaseEntity;
import com.wiley.cms.cochrane.cmanager.data.IResultsStorage;
import com.wiley.cms.cochrane.cmanager.data.cca.CcaCdsrDoiViewEntity;
import com.wiley.cms.cochrane.cmanager.data.record.IRecordStorage;
import com.wiley.cms.cochrane.cmanager.data.record.RecordEntity;
import com.wiley.cms.cochrane.cmanager.data.record.RecordMetadataEntity;
import com.wiley.cms.cochrane.cmanager.data.record.RecordStorageFactory;
import com.wiley.cms.cochrane.cmanager.data.record.UnitStatusEntity;
import com.wiley.cms.cochrane.cmanager.data.record.UnitStatusVO;
import com.wiley.cms.cochrane.cmanager.data.rendering.RenderingPlan;
import com.wiley.cms.cochrane.cmanager.entitymanager.AbstractManager;
import com.wiley.cms.cochrane.cmanager.entitymanager.DbRecordVO;
import com.wiley.cms.cochrane.cmanager.entitymanager.IRecordManager;
import com.wiley.cms.cochrane.cmanager.entitywrapper.EntireDbWrapper;
import com.wiley.cms.cochrane.cmanager.entitywrapper.SearchRecordOrder;
import com.wiley.cms.cochrane.cmanager.translated.TranslatedAbstractsHelper;
import com.wiley.cms.cochrane.repository.IRepository;
import com.wiley.cms.cochrane.repository.RepositoryFactory;
import com.wiley.cms.cochrane.utils.Constants;
import com.wiley.cms.cochrane.utils.XLSHelper;
import com.wiley.cms.converter.services.RevmanMetadataHelper;
import com.wiley.tes.util.DbUtils;
import com.wiley.tes.util.EntitiesDepository;
import com.wiley.tes.util.InputUtils;
import com.wiley.tes.util.Logger;
import jxl.CellView;
import jxl.Workbook;
import jxl.write.Label;
import jxl.write.WritableCellFormat;
import jxl.write.WritableFont;
import jxl.write.WritableSheet;
import jxl.write.WritableWorkbook;
import jxl.write.WriteException;
import org.apache.commons.lang.StringUtils;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;

/**
 * @author <a href='mailto:gkhristich@wiley.ru'>Galina Khristich</a>
 *         Date: 31-Jan-2008
 */
public class CreatorXlsWithNewOrUpdated {
    private static final Logger LOG = Logger.getLogger(CreatorXlsWithNewOrUpdated.class);

    private static WritableWorkbook workbook;
    private static WritableSheet sheet;
    private static final int POZ_RECORDID = 0;
    private static final int POZ_TITLE = 1;
    private static final int POZ_AUTHOTS = 2;
    private static final int POZ_GROUP = 3;
    private static final int POZ_ABSTRINFO = 4;
    private static final int POZ_IMPL = 5;
    private static final int POZ_MSG = 3;

    private static final int POZ_CIT = 6;
    private static final int WIDE_WIDTH = 40;
    private static final int WIDTH = 20;
    private static final int NUMBER256 = 256;
    private static final int NARROW_WIDTH = 10;
    private static final int MAX_ENTITY_LENGTH = 40;

    private static final String FILESYSTEM_ROOT = CochraneCMSProperties.getProperty("filesystem.root");
    private static final String CLSYSREV_BLOCK = "Updated CDSR";
    private static final String CCA_BLOCK = "Related CCA";

    private static final String ID = "ID";
    private static final String DATE = "Date";
    private static final String ACTION = "Action";

    private static final String RECORD_ID = "Record ID";
    private static final String TITLE = "Title";
    private static final String STATUS = "Status";
    private static final String DOI = "DOI";

    private static final String AUTHORS = "Authors";
    private static final String REVIEW_GROUP = "Review Group";
    private static final String ABSTRACT = "Abstract";
    private static final String IMPLICATIONS = "Implications";
    private static final String WHAT_S_NEW = "What's New";
    private static final String CITATION = "Citation";

    private static final String[] CCA_TITLES = {ID, TITLE, DATE, ACTION, STATUS, ID, DATE, ACTION};
    private static final int CCA_REPORT_CDRS_ATTRS_COUNT = 5;

    private static HashMap<String, String> uiNameStatuses;
    private static final String FORMAT = "#[0-9]+";
    private static final SimpleDateFormat CCA_REPORT_SDF = new SimpleDateFormat("dd.MMM.yyyy", Locale.US);

    private static final int CD_NUMBER_COLUMN_WIDTH_FACTOR = 2;
    private static final int DOI_COLUMN_WIDTH_FACTOR = 3;
    private static final int TITLE_COLUMN_WIDTH_FACTOR = 6;

    private CreatorXlsWithNewOrUpdated() {
    }

    public static synchronized void createXlsEntireTA() {
        createXlsTA(EntireDbWrapper.getTranslationReportPath());
    }

    public static synchronized void createXlsRevmanNihMandate(int issueId, String dbName, String path) {
        createXlsRevmanNihFunded(issueId, dbName, path);
    }

    private static void closeAndSaveToRepository(XLSHelper xlsHelper, File tempFile, String path) throws Exception {
        xlsHelper.closeAndSaveToRepository(tempFile, path, false, RepositoryFactory.getRepository());
    }

    private static void createXlsRevmanNihFunded(int issueId, String dbName, String path) {
        try {
            File tempFile = File.createTempFile(Constants.TEMP, "");
            XLSHelper xlsHelper = new XLSHelper(tempFile);
            initCDSRHead(xlsHelper, true, false);
            xlsHelper.setCurrentSheet(0);
            IResultsStorage rs = AbstractManager.getResultStorage();
            List<RecordMetadataEntity> list = rs.findRecordsMetadata(issueId, dbName);
            int row = 0;
            for (RecordMetadataEntity re: list) {
                if (re.isNihFunded())  {
                    xlsHelper.addValue(re.getCdNumber(), 0, ++row);
                    xlsHelper.addValue(RevmanMetadataHelper.buildDoi(re.getCdNumber(), re.getPubNumber()), 1, row);
                    xlsHelper.addValue(re.getTitle(), 2, row);
                }
            }
            closeAndSaveToRepository(xlsHelper, tempFile, path);
        } catch (Exception e) {
            LOG.error(e, e);
        }
    }

    public static synchronized void createXlsImport(int dbId, String path) {
        try {
            File tempFile = File.createTempFile(Constants.TEMP, "");
            XLSHelper xlsHelper = new XLSHelper(tempFile);

            initCDSRHead(xlsHelper, false, true);
            //xlsHelper.setCurrentSheet(0);
            xlsHelper.addCurrentSheet("articles", 0);

            Map<String, ErrorLogEntity> errs = ActivityLogFactory.getFactory().getInstance().getLatestErrorLogs(
                    DatabaseEntity.CDSR_KEY, Constants.IMPORT_JATS_ISSUE_NUMBER);

            //IResultsStorage rs = AbstractManager.getResultStorage();
            IRecordStorage rs = RecordStorageFactory.getFactory().getInstance();
            List<RecordEntity> records = rs.getDbRecordList(dbId, 0, 0, null, 0, null, SearchRecordOrder.NAME, false);
            //List<RecordMetadataEntity> list = rs.findRecordsMetadata(issueId);
            int row = 0;
            for (RecordEntity re: records) {

                RecordMetadataEntity rme = re.getMetadata();
                int metaType = rme.getMetaType();
                String cdNumber = re.getName();
                String pubName = RevmanMetadataHelper.buildPubName(cdNumber, rme.getPubNumber());
                boolean imported = metaType == RecordMetadataEntity.IMPORTED;
                boolean onlyTa = rme.getIssue() != Constants.IMPORT_JATS_ISSUE_NUMBER;
                ErrorLogEntity err = findLatest(errs.remove(pubName), errs.remove(re.getId().toString()));
                String errMsg = null;
                if (err != null && err.getDate().after(re.getDeliveryFile().getDate())) {
                    errMsg = err.getComments();
                }

                xlsHelper.addValue(pubName, 0, ++row);
                xlsHelper.addValue(RevmanMetadataHelper.buildDoi(rme.getCdNumber(), rme.getPubNumber()), 1, row);
                xlsHelper.addValue(imported ? "YES" : "NO", 2, row);
                if (errMsg != null) {
                    xlsHelper.addValue(errMsg, POZ_MSG, row);
                }
                if (onlyTa) {
                    xlsHelper.addValue("only translations present", POZ_MSG + 1, row);
                }

                if (row % Constants.CHUNK_SIZE_4_DEBUG == 0) {
                    LOG.debug(String.format("%d from %d were handled", row, records.size()));
                }
            }

            for (Map.Entry<String, ErrorLogEntity> entry: errs.entrySet()) {
                ErrorLogEntity err = entry.getValue();
                if (DbUtils.exists(err.getEntityId()) || err.getComments() == null || err.getComments().length() == 0) {
                    continue;
                }
                xlsHelper.addValue(err.getEntityName(), 0, ++row);
                xlsHelper.addValue(RevmanMetadataHelper.addDoiPrefix(err.getEntityName()), 1, row);
                xlsHelper.addValue("NO", 2, row);
                xlsHelper.addValue(err.getComments(), POZ_MSG, row);
            }

            closeAndSaveToRepository(xlsHelper, tempFile, path);

        } catch (Exception e) {
            LOG.error(e, e);
        }
    }

    private static ErrorLogEntity findLatest(ErrorLogEntity errByPub, ErrorLogEntity errById) {
        ErrorLogEntity ret = errByPub;
        if (errByPub == null || (errById != null && errById.getId() > errByPub.getId())) {
            ret = errById;
        }
        return ret;
    }

    private static void createXlsTA(String path) {
        try {
            TranslatedAbstractsHelper taHelper = new TranslatedAbstractsHelper();
            File[] langs = taHelper.getLanguages();

            File tempFile = File.createTempFile(Constants.TEMP, "");
            XLSHelper xlsHelper = new XLSHelper(tempFile);

            xlsHelper.setColumn(0, 2);
            xlsHelper.setColumn(1, 1);
            xlsHelper.setColumn(2, 2);
            xlsHelper.addTitle("Language Code", 0);
            xlsHelper.addTitle("Count", 1);
            xlsHelper.addTitle("History count", 2);

            IRecordManager rm = CochraneCMSBeans.getRecordManager();

            int row = 0;
            int[] sum = {0, 0};
            for (File fl: langs) {
                if (!fl.isFile()) {
                    int[] ret = prepareLanguageSheet(fl.getName(), ++row, xlsHelper, rm);
                    sum[0] += ret[0];
                    sum[1] += ret[1];
                }
            }

            xlsHelper.setCurrentSheet(0);

            xlsHelper.addValue(String.valueOf(sum[0]), 1, ++row);
            xlsHelper.addValue(String.valueOf(sum[1]), 2, row);
            xlsHelper.addValue("Unique reviews", 0, row + 2);
            int uniqueCount = rm.getTranslationUniqueCount();
            xlsHelper.addValue(String.valueOf(uniqueCount), 1, row + 2);

            closeAndSaveToRepository(xlsHelper, tempFile, path);

        } catch (Exception e) {
            LOG.error(e, e);
        }
    }

    private static void initCDSRHead(XLSHelper xlsHelper, boolean title, boolean toImport) throws Exception {
        xlsHelper.setColumn(0, CD_NUMBER_COLUMN_WIDTH_FACTOR);
        xlsHelper.setColumn(1, DOI_COLUMN_WIDTH_FACTOR);
        xlsHelper.addTitle(DOI, 1);
        if (toImport) {
            xlsHelper.addTitle("SID", 0);
            xlsHelper.setColumn(2, CD_NUMBER_COLUMN_WIDTH_FACTOR);
            xlsHelper.setColumn(POZ_MSG, TITLE_COLUMN_WIDTH_FACTOR);
            xlsHelper.setColumn(POZ_MSG + 1, TITLE_COLUMN_WIDTH_FACTOR);
            xlsHelper.addTitle("Imported", 2);
            xlsHelper.addTitle("Messages", POZ_MSG);
            xlsHelper.addTitle("Comment", POZ_MSG + 1);

        } else {
            xlsHelper.addTitle(RECORD_ID, 0);
            if (title) {
                xlsHelper.setColumn(2, TITLE_COLUMN_WIDTH_FACTOR);
                xlsHelper.addTitle(TITLE, 2);
            }
        }
    }

    private static int[] prepareLanguageSheet(String lang, int row, XLSHelper xlsHelper, IRecordManager rm)
        throws Exception {

        int count = rm.getTranslationCount(lang);
        int batch = CochraneCMSPropertyNames.getDbRecordBatchSize();

        List<DbRecordVO> previous = rm.getTranslationHistory(lang, 0, batch);
        int previousCount = previous.size();

        xlsHelper.setCurrentSheet(0);
        xlsHelper.addValue(lang, 0, row);
        xlsHelper.addValue(String.valueOf(count), 1, row);

        if (count == 0 && previousCount == 0) {
            xlsHelper.addValue(String.valueOf(previousCount), 2, row);
            return new int[2];
        }

        xlsHelper.addCurrentSheet(lang);
        initCDSRHead(xlsHelper, false, false);

        int j = 1;
        for (int i = 0; i < count; i += batch) {
            List<DbRecordVO> list = rm.getTranslations(lang, i, batch);
            for (DbRecordVO ta: list) {
                j = addTaRecord(ta, j, xlsHelper);
            }
        }

        xlsHelper.addValue("", 0, j);
        xlsHelper.addValue("Historical versions:", 1, j++);
        
        while (!previous.isEmpty()) {
            for (DbRecordVO ta: previous) {
                j = addTaRecord(ta, j, xlsHelper);
            }

            previous = rm.getTranslationHistory(lang, previousCount, batch);
            previousCount += previous.size();
        }

        int[] ret = {count, previousCount};
        xlsHelper.setCurrentSheet(0);
        xlsHelper.addValue(String.valueOf(previousCount), 2, row);
        return ret;
    }

    private static int addTaRecord(DbRecordVO ta, int j, XLSHelper xlsHelper) throws Exception {
        int ret = j;
        String name = RevmanMetadataHelper.parseCdNumber(ta.getLabel());
        String doi = RevmanMetadataHelper.addDoiPrefix(ta.getLabel());
        xlsHelper.addValue(name, 0, ret);
        xlsHelper.addValue(doi, 1, ret++);
        return ret;
    }

    private static void createReport(List<RecordEntity> recs, String fileUri, int dbId, String dbName) {
        List<String[]> fields = new ArrayList<String[]>();
        IResultsStorage rs = AbstractManager.getResultStorage();
        StringBuilder errors = new StringBuilder();
        try {
            for (RecordEntity cdsr : recs) {
                List<CcaCdsrDoiViewEntity> ccas = rs.getCcaCdsrDoiViewByCdsrName(cdsr.getName());
                for (int i = 0; i < ccas.size(); i++) {
                    if (i == 0) {
                        fields.add(getFields(cdsr, ccas.get(i)));
                    } else {
                        fields.add(getFields(null, ccas.get(i)));
                    }
                }
            }

            if (!fields.isEmpty()) {
                writeToXls(fields, fileUri);
            }
        } catch (Exception e) {
            errors.append(e).append(".\n");
            LOG.error("CCA report generation failed", e);
        } finally {
            sendMessage(fileUri, fields, errors.toString(), dbId, dbName);
        }
    }

    private static String[] getFields(RecordEntity cdsr, CcaCdsrDoiViewEntity cca) {
        int i = 0;
        String[] result = new String[CCA_TITLES.length];
        Arrays.fill(result, "");

        if (cdsr != null) {
            result[i++] = cdsr.getName();
            result[i++] = cdsr.getUnitTitleNormalized();
            result[i++] = CCA_REPORT_SDF.format(cdsr.getDeliveryFile().getDate());
            result[i++] = "Updated";
            result[i++] = cdsr.getUnitStatus().getName();
        } else {
            i = CCA_REPORT_CDRS_ATTRS_COUNT;
        }
        result[i++] = cca.getCcaName();
        result[i++] = CCA_REPORT_SDF.format(cca.getDate());
        result[i] = "Published";

        return result;
    }

    public static synchronized void createXlsWithNewOrUpdated(int dbId, int issueId, String dbName) {
        LOG.debug("createXlsWithNewOrUpdated started");
        FileWriter csvFile = null;
        IRecordStorage recordStorage = AbstractManager.getRecordStorage();
        try {
            fillStatusesMap(dbName);
            String filePath = generateFolderPath(issueId, dbName);
            csvFile = initCsv(filePath);

            IRepository rs = RepositoryFactory.getRepository();
            String path = FilePathCreator.getRenderedDirPath(
                    String.valueOf(issueId), dbName, "", RenderingPlan.HTML);

            List<String> recs = recordStorage.getNewReviews(dbId);
            if (recs != null && recs.size() != 0) {
                LOG.debug("Count of New Reviews " + recs.size());
                writeToXls(recs, rs, filePath + "CL_new_reviews.xls", csvFile,
                        path.substring(0, path.length() - 1), false, true);
            }

            //recs = null;
            recs = recordStorage.getUpdatedReviews(dbId);
            if (recs != null && recs.size() != 0) {
                LOG.debug("Count of Updated Reviews " + recs.size());
                writeToXls(recs, rs, filePath + "CL_updated_reviews.xls", csvFile,
                        path.substring(0, path.length() - 1), true, true);
            }

            // protocols, recs = null;
            recs = recordStorage.getNewProtocols(dbId);
            if (recs != null && recs.size() != 0) {
                LOG.debug("Count of New Protocols " + recs.size());
                writeToXls(recs, rs, filePath + "CL_new_protocols.xls", csvFile,
                        path.substring(0, path.length() - 1), false, false);
            }

            recs = recordStorage.getUpdatedProtocols(dbId);
            if (recs != null && recs.size() != 0) {
                LOG.debug("Count of Updated Protocols " + recs.size());
                writeToXls(recs, rs, filePath + "CL_updated_protocols.xls", csvFile,
                        path.substring(0, path.length() - 1), true, false);
            }

            //CCA report generation
            List<RecordEntity> recordEntities =
                    recordStorage.getCdsrRecords4Report(dbId, IgnoredStatuses4CcaReport.getStatuses());
            LOG.debug("CCA report generation started");
            createReport(recordEntities, filePath + "Updated_CDSR_records_connected_with_CCA.xls", dbId, dbName);

            csvFile.flush();
            csvFile.close();
        } catch (Exception e) {
            LOG.debug(e, e);
            return;
        }
        LOG.debug("createXlsWithNewOrUpdated finished");
    }

    private static FileWriter initCsv(String filePath) throws Exception {
        FileWriter csv = new FileWriter(new File(new URI(filePath + "manifest.csv")));
        csv.write("\"Record Id\",\"Status\",\"Title\",\"Authors\","
                + "\"Review Group\",\"Abstract\",\"Implications\"\n");
        return csv;
    }

    private static void writeToXls(List<String> recs, IRepository rs, String filename, FileWriter csvFile, String path,
        boolean isUpd, boolean isReview) {
        try {
            initJexcel(filename, isUpd, isReview);
        } catch (Exception e) {
            LOG.debug(e, e);
            return;
        }

        int i = 1;
        for (String recName : recs) {
            try {
                String uri = path + recName + "/meta.html";
                if (rs.isFileExistsQuiet(uri)) {
                    FieldsForXls fields = parse(rs.getFile(uri));
                    writeLine(i++, fields, isUpd, isReview);
                    if (isReview) {
                        csvFile.write("\"" + fields.getRecId() + "\","
                                + "\"" + fields.getStatus() + "\","
                                + "\"" + fields.getTitle() + "\","
                                + "\"" + fields.getAuthors() + "\","
                                + "\"" + fields.getReviewGroup() + "\","
                                + "\"" + fields.getAbstr() + "\","
                                + "\"" + fields.getImplication() + "\"\n");
                    }
                }
            } catch (Exception e) {
                LOG.debug(e, e);
            }
        }
        finishJexcel();
    }

    private static void writeToXls(List<String[]> fields, String fileUri) throws Exception {
        int i = 2;
        initJexcel(new File(new URI(fileUri)), CCA_TITLES);
        for (String[] line : fields) {
            writeLine(line, i++);
        }
        finishJexcel();
    }

    private static void finishJexcel() {
        try {
            workbook.write();
            workbook.close();
        } catch (Exception e) {
            LOG.debug(e, e);
        }
    }

    private static void initJexcel(File file, String[] titles) throws Exception {
        WritableCellFormat arialBoldFormat = initBoldFont(file);
        WritableCellFormat format = initDefaultFormat();

        CellView view = new CellView();
        view.setSize(WIDTH * NUMBER256);
        view.setFormat(format);
        for (int i = 0; i < CCA_TITLES.length; i++) {
            sheet.setColumnView(i, view);
        }

        Label label = new Label(0, 0, CLSYSREV_BLOCK, arialBoldFormat);
        sheet.addCell(label);
        sheet.mergeCells(0, 0, CCA_REPORT_CDRS_ATTRS_COUNT - 1, 0);
        sheet.mergeCells(CCA_REPORT_CDRS_ATTRS_COUNT, 0, CCA_TITLES.length - 1, 0);

        Label label1 = new Label(CCA_REPORT_CDRS_ATTRS_COUNT, 0, CCA_BLOCK, arialBoldFormat);
        sheet.addCell(label1);

        boolean[] params = new boolean[CCA_TITLES.length];
        Arrays.fill(params, true);
        addTitle(params, arialBoldFormat, titles, 1);
    }

    private static WritableCellFormat initDefaultFormat() throws WriteException {
        WritableFont arial = new WritableFont(WritableFont.ARIAL, WritableFont.DEFAULT_POINT_SIZE,
                WritableFont.NO_BOLD, false);
        WritableCellFormat format = new WritableCellFormat(arial);
        format.setWrap(true);
        format.setVerticalAlignment(jxl.format.VerticalAlignment.TOP);
        return format;
    }

    private static WritableCellFormat initBoldFont(File file) throws IOException {
        workbook = Workbook.createWorkbook(file);
        sheet = workbook.createSheet("Sheet1", 0);
        WritableFont arialBold = new WritableFont(WritableFont.ARIAL, WritableFont.DEFAULT_POINT_SIZE,
                WritableFont.BOLD, false);
        return new WritableCellFormat(arialBold);
    }

    private static void initJexcel(String filename, boolean upd, boolean isReview)
        throws IOException, WriteException, URISyntaxException {

        WritableCellFormat arialBoldFormat = initBoldFont(new File(new URI(filename)));
        WritableCellFormat format = initDefaultFormat();

        CellView view = new CellView();
        view.setSize(WIDE_WIDTH * NUMBER256);
        view.setFormat(format);
        sheet.setColumnView(POZ_ABSTRINFO, view);
        sheet.setColumnView(POZ_IMPL, view);

        CellView view1 = new CellView();
        view1.setSize(WIDTH * NUMBER256);
        view1.setFormat(format);
        sheet.setColumnView(POZ_CIT, view1);
        sheet.setColumnView(POZ_TITLE, view1);
        sheet.setColumnView(POZ_GROUP, view1);
        sheet.setColumnView(POZ_AUTHOTS, view1);
        sheet.setColumnView(POZ_RECORDID, view1);

        CellView view2 = new CellView();
        view2.setFormat(format);
        view2.setSize(NARROW_WIDTH * NUMBER256);
        sheet.setColumnView(POZ_RECORDID, view2);

        addTitle(upd, isReview, arialBoldFormat);
    }

    private static void addTitle(boolean[] params, WritableCellFormat arialBoldFormat, String[] titles, int row)
        throws Exception {

        int poz = 0;
        int idx = 0;
        for (boolean param : params) {
            if (param) {
                Label label = new Label(poz, row, titles[idx], arialBoldFormat);
                sheet.addCell(label);
                poz++;
            }
            idx++;
        }
    }

    private static void addTitle(boolean upd, boolean isReview, WritableCellFormat arialBoldFormat)
        throws WriteException {

        int poz = 0;
        Label label = new Label(poz++, 0, RECORD_ID, arialBoldFormat);
        sheet.addCell(label);

        if (upd) {
            Label label7 = new Label(poz++, 0, STATUS, arialBoldFormat);
            sheet.addCell(label7);

        }
        Label label1 = new Label(poz++, 0, TITLE, arialBoldFormat);
        sheet.addCell(label1);

        Label label3 = new Label(poz++, 0, AUTHORS, arialBoldFormat);
        sheet.addCell(label3);

        Label label2 = new Label(poz++, 0, REVIEW_GROUP, arialBoldFormat);
        sheet.addCell(label2);

        if (isReview) {

            Label label4 = new Label(poz++, 0, ABSTRACT, arialBoldFormat);
            sheet.addCell(label4);

            Label label5 = new Label(poz++, 0, IMPLICATIONS, arialBoldFormat);
            sheet.addCell(label5);
        }
        if (upd) {
            Label label7 = new Label(poz++, 0, WHAT_S_NEW, arialBoldFormat);
            sheet.addCell(label7);
        }

        Label label6 = new Label(poz, 0, CITATION, arialBoldFormat);
        sheet.addCell(label6);
    }

    private static void writeLine(String[] fields, int i) throws Exception {
        int poz = 0;
        for (String field : fields) {
            if (field != null) {
                Label label = new Label(poz++, i, field);
                sheet.addCell(label);
            }
        }
    }

    private static void writeLine(int i, FieldsForXls fields, boolean upd, boolean isReview) throws WriteException {
        int poz = 0;
        Label label = new Label(poz++, i, fields.getRecId());
        sheet.addCell(label);
        if (upd) {
            Label label7 = new Label(poz++, i, uiNameStatuses.get(fields.getStatus()));
            sheet.addCell(label7);
        }
        Label label1 = new Label(poz++, i, fields.getTitle());
        sheet.addCell(label1);
        Label label3 = new Label(poz++, i, fields.getAuthors());
        sheet.addCell(label3);
        Label label2 = new Label(poz++, i, fields.getReviewGroup());
        sheet.addCell(label2);
        if (isReview) {
            Label label4 = new Label(poz++, i, fields.getAbstr());
            sheet.addCell(label4);
            Label label5 = new Label(poz++, i, fields.getImplication());
            sheet.addCell(label5);
        }
        if (upd) {
            Label label7 = new Label(poz++, i, fields.getWhatsNew());
            sheet.addCell(label7);
        }
        Label label6 = new Label(poz, i, fields.getCitation());
        sheet.addCell(label6);

    }

    private static FieldsForXls parse(InputStream stream)
        throws IOException, SAXException, IllegalAccessException, InstantiationException, ClassNotFoundException {

        String fileHtml = InputUtils.readStreamToString(stream);
        FieldsForXls fields = new FieldsForXls();
        fields.setRecId(find("RecordId", fileHtml));
        fields.setTitle(find(TITLE, fileHtml));
        fields.setReviewGroup(find(REVIEW_GROUP, fileHtml));
        fields.setStatus(find("unitStatus", fileHtml));
        fields.setAuthors(find(AUTHORS, fileHtml));
        fields.setAbstr(find(ABSTRACT, fileHtml));
        fields.setImplication(find(IMPLICATIONS, fileHtml));
        fields.setWhatsNew(find(WHAT_S_NEW, fileHtml));
        fields.setCitation(find(CITATION, fileHtml));
        return fields;
    }

    private static String find(String name, String fileHtml) {
        String str = "<meta name=\"" + name + "\" content=\"";
        int start = fileHtml.indexOf(str);
        if (start == -1) {
            LOG.debug("not found " + name + " in meta.html");
            return "";
        }
        return replaceEntities(fileHtml.substring(start + str.length(), fileHtml.indexOf("\">", start)));
    }

    private static char getUnicode(String entity) {
        String entNumb;
        if (entity.matches(FORMAT)) {
            entNumb = entity;
        } else if (entity.equals("copy")) {
            entNumb = "&#x000A9;";
        } else if (entity.equals("nbsp")) {
            entNumb = "&#x000A0;";
        } else if (entity.equals("amp")) {
            entNumb = "&#38;";
        } else {
            try {
                entNumb = EntitiesDepository.getEntNumber(entity);
                if (entNumb == null) {
                    LOG.error("Entity not found: " + entity);
                    return '&';
                }
            } catch (Exception e) {
                entNumb = entity;
            }
        }
        char chr;
        String code;
        if (entNumb.matches(FORMAT)) {
            code = entNumb.substring(1);
            chr = (char) Integer.decode(code).intValue();
        } else {
            code = entNumb.substring(entNumb.lastIndexOf("#") + 1,
                    entNumb.lastIndexOf(";")).replaceAll("x", "");
            chr = (char) Integer.decode("0x" + code).intValue();
        }
        return chr;
    }

    public static String replaceEntities(String str) {
        int curPos = 0;
        int entStart = str.indexOf("&", curPos);
        if (entStart < 0) {
            return str;
        }
        int entEnd;
        StringBuilder buf = new StringBuilder(str.length());
        while (entStart >= 0) {
            int substrLength = str.length() - entStart > MAX_ENTITY_LENGTH ? MAX_ENTITY_LENGTH : str.length()
                    - entStart;
            entEnd = str.substring(entStart, entStart + substrLength).indexOf(";");
            if (entEnd != -1) {
                buf.append(str, curPos, entStart);
                char chr = getUnicode(str.substring(entStart + 1, entStart + entEnd));
                buf.append(chr);
                if (chr != '&') {
                    curPos = entStart + entEnd + 1;
                } else {
                    curPos = entStart + 1;
                }
            } else {
                buf.append(str, curPos, entStart + 1);
                curPos = entStart + 1;
            }
            entStart = str.indexOf("&", curPos);
        }
        buf.append(str.substring(curPos));
        return buf.toString();
    }

    private static String generateFolderPath(int issueId, String dbTitle) {
        return FILESYSTEM_ROOT
                + "/"
                + CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.PREFIX_REPOSITORY)
                + "/" + issueId
                + "/" + dbTitle
                + "/";
    }

    private static void fillStatusesMap(String dbName) {
        Iterator<UnitStatusVO> it = CochraneCMSBeans.getRecordCache().getUnitStatuses(
                CochraneCMSPropertyNames.getCDSRDbName().equals(dbName));
        uiNameStatuses = new HashMap<>();
        while (it.hasNext()) {
            UnitStatusVO statusVO = it.next();
            uiNameStatuses.put(statusVO.getName(), statusVO.getUiName());
        }
    }

    private static void sendMessage(String fileUri,
                                    List<String[]> fields,
                                    String errors,
                                    int dbId,
                                    String dbName) {
        boolean reportUpdated;
        MessageSender.NotificationMessage notificationMsg = createNotificationMessage(fileUri, fields, errors);
        try {
            IActivityLogService als = AbstractManager.getActivityLogService();
            String message = notificationMsg.getParam(MessageSender.MSG_PARAM_LIST);
            reportUpdated = (StringUtils.isNotEmpty(errors)
                    || !notificationMsg.isEmpty() && isReportUpdated(message, als));
            als.info(ActivityLogEntity.EntityLevel.DB, ILogEvent.CDSR_CONNECTED_WITH_CCA_REPORT,
                    dbId, dbName, null, message);
        } catch (Exception e) {
            reportUpdated = false;
        }
        if (reportUpdated) {
            MessageSender.sendMessage(notificationMsg.getMsgId(), notificationMsg.getParams());
        }
    }

    private static MessageSender.NotificationMessage createNotificationMessage(String fileUri, List<String[]> fields,
                                                                               String errors) {
        MessageSender.NotificationMessage notificationMsg = new MessageSender.NotificationMessage();
        StringBuilder link2File = new StringBuilder();
        StringBuilder recordList = new StringBuilder();

        if (StringUtils.isNotEmpty(errors)) {
            notificationMsg.setMsgId(MessageSender.MSG_TITLE_CCA_REPORT_GENERATION_FAILED);

            link2File.append(CochraneCMSPropertyNames.getNotAvailableMsg());

            Map<String, String> map = new HashMap<String, String>();
            map.put(MessageSender.MSG_PARAM_REPORT, errors.toString());
            recordList.append(Matcher.quoteReplacement(CochraneCMSProperties.getProperty(
                    MessageSender.MSG_TITLE_CCA_REPORT_GENERATION_EXCEPTION, map)));
        } else {
            notificationMsg.setMsgId(MessageSender.MSG_TITLE_CCA_REPORT_GENERATION_SUCCESS);

            if (fields.isEmpty()) {
                link2File.append(CochraneCMSPropertyNames.getNotAvailableMsg());
                recordList.append("Report is empty");
                notificationMsg.setEmpty(true);
            } else {
                link2File.append(CochraneCMSPropertyNames.getWebLoadingUrl())
                        .append("PreviewServlet/")
                        .append(fileUri.replaceFirst(FILESYSTEM_ROOT + "/", ""));
                fillRecordList(fields, recordList);
            }
        }
        notificationMsg.addParam(MessageSender.MSG_PARAM_LINK, link2File.toString());
        notificationMsg.addParam(MessageSender.MSG_PARAM_LIST, recordList.toString());

        return notificationMsg;
    }

    private static void fillRecordList(List<String[]> fields, StringBuilder recordList) {
        for (String[] line : fields) {
            for (int i = 0; i < CCA_TITLES.length; i++) {
                if (i == 0) {
                    recordList.append(CLSYSREV_BLOCK + "\n");
                } else if (i == CCA_REPORT_CDRS_ATTRS_COUNT) {
                    recordList.append(CCA_BLOCK + "\n");
                }
                recordList.append(String.format("%1$-10s", CCA_TITLES[i] + ":") + line[i] + "\n");
            }
            recordList.append("\n");
        }
    }

    private static boolean isReportUpdated(String newReport, IActivityLogService als) {
        boolean updated;
        if (StringUtils.isEmpty(newReport)) {
            updated = false;
        } else {
            String prevReport = als.getLastCommentsByEvent(ILogEvent.CDSR_CONNECTED_WITH_CCA_REPORT);
            updated = !StringUtils.equals(newReport, prevReport);
        }

        return updated;
    }

    private static class IgnoredStatuses4CcaReport {

        private static final List<Integer> STASUSES = new ArrayList<Integer>();
        static {
            STASUSES.add(UnitStatusEntity.UnitStatus.NEW);
            STASUSES.add(UnitStatusEntity.UnitStatus.NEW1);
            STASUSES.add(UnitStatusEntity.UnitStatus.EDITED);
            STASUSES.add(UnitStatusEntity.UnitStatus.UNCHANGED);
            STASUSES.add(UnitStatusEntity.UnitStatus.STABLE);
            STASUSES.add(UnitStatusEntity.UnitStatus.NEW_COMMENTED);
            STASUSES.add(UnitStatusEntity.UnitStatus.EDITED_COMMENTED);
            STASUSES.add(UnitStatusEntity.UnitStatus.UNCHANGED_COMMENTED);
            STASUSES.add(UnitStatusEntity.UnitStatus.STABLE_COMMENTED);
            STASUSES.add(UnitStatusEntity.UnitStatus.MESHTERMS_UPDATED);
            STASUSES.add(UnitStatusEntity.UnitStatus.TRANSLATED_ABSTRACTS);
            STASUSES.add(UnitStatusEntity.UnitStatus.EDITED1);
        }

        public static List<Integer> getStatuses() {
            return STASUSES;
        }
    }

    static class FieldsForXls {
        String recId;
        String title;
        String authors;
        String reviewGroup;
        String abstr;
        String implication;
        String citation;
        String status;
        String whatsNew;


        public String getStatus() {
            return status;
        }

        public String getRecId() {
            return recId;
        }

        public String getTitle() {
            return title;
        }

        public String getAuthors() {
            return authors;
        }

        public String getReviewGroup() {
            return reviewGroup;
        }

        public String getAbstr() {
            return abstr;
        }

        public String getImplication() {
            return implication;
        }

        public String getCitation() {
            return citation;
        }

        public String getWhatsNew() {
            return whatsNew;
        }

        public void setRecId(String recId) {
            this.recId = recId;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public void setAuthors(String authors) {
            this.authors = authors;
        }

        public void setReviewGroup(String reviewGroup) {
            this.reviewGroup = reviewGroup;
        }

        public void setAbstr(String abstr) {
            this.abstr = abstr;
        }

        public void setImplication(String implication) {
            this.implication = implication;
        }

        public void setCitation(String citation) {
            this.citation = citation;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public void setWhatsNew(String s) {
            whatsNew = s;
        }
    }
}
