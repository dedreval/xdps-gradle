package com.wiley.cms.cochrane.cmanager.specrender.xlsfiles;

import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.QuoteMode;
import org.apache.commons.io.IOUtils;

import com.wiley.cms.cochrane.cmanager.CmsUtils;
import com.wiley.cms.cochrane.cmanager.CochraneCMSBeans;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.FilePathBuilder;
import com.wiley.cms.cochrane.cmanager.FilePathCreator;
import com.wiley.cms.cochrane.cmanager.contentworker.JatsHelper;
import com.wiley.cms.cochrane.cmanager.data.DeliveryFileVO;
import com.wiley.cms.cochrane.cmanager.data.IResultsStorage;
import com.wiley.cms.cochrane.cmanager.data.PrevVO;
import com.wiley.cms.cochrane.cmanager.data.entire.EntireDBStorageFactory;
import com.wiley.cms.cochrane.cmanager.data.record.RecordEntity;
import com.wiley.cms.cochrane.cmanager.entitymanager.DbRecordVO;
import com.wiley.cms.cochrane.cmanager.entitymanager.ICDSRMeta;
import com.wiley.cms.cochrane.cmanager.entitymanager.IRecordManager;
import com.wiley.cms.cochrane.cmanager.entitymanager.IVersionManager;
import com.wiley.cms.cochrane.cmanager.entitymanager.RecordHelper;
import com.wiley.cms.cochrane.cmanager.entitywrapper.EntireDbWrapper;
import com.wiley.cms.cochrane.cmanager.entitywrapper.ResultStorageFactory;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.cmanager.translated.TranslatedAbstractVO;
import com.wiley.cms.cochrane.cmanager.translated.TranslatedAbstractsCorrector;
import com.wiley.cms.cochrane.process.IRecordCache;
import com.wiley.cms.cochrane.repository.IRepository;
import com.wiley.cms.cochrane.repository.RepositoryFactory;
import com.wiley.cms.cochrane.utils.Constants;
import com.wiley.cms.cochrane.utils.ContentLocation;
import com.wiley.cms.cochrane.utils.ErrorInfo;
import com.wiley.cms.cochrane.utils.XLSHelper;
import com.wiley.cms.converter.services.RevmanMetadataHelper;
import com.wiley.tes.util.DbConstants;
import com.wiley.tes.util.Extensions;
import com.wiley.tes.util.Logger;
import com.wiley.tes.util.Now;
import jxl.format.Alignment;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 4/21/2015
 */
public class CochraneReportCreator {
    private static final Logger LOG = Logger.getLogger(CochraneReportCreator.class);
    private static final Logger LOG_TST = Logger.getLogger("Tester");

    private static final String SHEET_1 = "Articles";
    private static final String SHEET_2 = "Translations";

    private static final String DOI = "DOI";
    private static final String STAGE = "Stage";
    private static final String RM_ID = "RevMan-ID";
    private static final String RM_VERSION = "RevMan-Version";
    private static final String JATS = "JATS";
    private static final String RM_EXISTS = "RevMan-Exists";
    private static final String WML21_EXISTS = "WML21-Exists";
    private static final String LANG = "Language";
    private static final String LANG_COCHRANE = "";
    private static final String TA_ID = "Translation-ID";
    private static final String TA_VERSION = "Translation-Version";

    private static final int SHEET_COUNT = 3;

    private CochraneReportCreator() {
    }

    public static synchronized void createCochraneReport() {
        try {
            createCDSREntireReport(findCDSRStatistic(), EntireDbWrapper.getCochraneReportPath());
        } catch (Exception e) {
            LOG.error(e, e);
        }
    }

    private static File getManifestFile() {
        try {
            return File.createTempFile(Constants.TEMP, Extensions.CSV);
        } catch (Exception e) {
            LOG.error(e.getMessage());
        }
        return null;
    }

    public static void createCDSREntireReport(Statistic<CochraneItem> stats, String path) {
        File manifest = getManifestFile();
        if (manifest == null) {
            return;
        }
        try (CSVPrinter csvWriter = createManifestWriter(manifest)) {
            File tempFile = File.createTempFile(Constants.TEMP, "");
            XLSHelper xlsHelper = new XLSHelper(tempFile, "Overall", true, SHEET_COUNT);

            int centerFormat = xlsHelper.registerFormat(Alignment.CENTRE);
            int rightFormat = xlsHelper.registerFormat(Alignment.RIGHT);

            xlsHelper.setCurrentSheet(0);
            int col = 0;
            xlsHelper.registerTitleColumn(col++, XLSHelper.WIDE_FACTOR_2, "Content", 0, SHEET_1, SHEET_2);
            xlsHelper.registerTitleColumn(col++, XLSHelper.WIDE_FACTOR_2, "Total", 0,
                    stats.getEntireCount() + stats.getPreviousCount(),
                    stats.getLastTranslationsCount() + stats.getPreviousTranslationsCount());
            xlsHelper.registerTitleColumn(col++, XLSHelper.WIDE_FACTOR_2, "Latest", 0,
                    stats.getEntireCount(), stats.getLastTranslationsCount());
            xlsHelper.registerTitleColumn(col++, XLSHelper.WIDE_FACTOR_2, "Total History", 0,
                    stats.getPreviousCount(), stats.getPreviousTranslationsCount());
            xlsHelper.registerTitleColumn(col++, XLSHelper.WIDE_FACTOR_2, "JATS Latest", 0,
                    stats.getJatsCount(), stats.getJatsTranslationsCount());
            xlsHelper.registerTitleColumn(col++, XLSHelper.WIDE_FACTOR_2, "JATS History", 0,
                    stats.getPreviousJatsCount(), stats.getPreviousJatsTranslationsCount());
            xlsHelper.registerTitleColumn(col++, XLSHelper.WIDE_FACTOR_2, "Legacy Latest", 0,
                    stats.getNonJatsCount(), stats.getNonJatsTranslationsCount());
            xlsHelper.registerTitleColumn(col, XLSHelper.WIDE_FACTOR_2, "Legacy History", 0,
                    stats.getPreviousNonJatsCount(), stats.getPreviousNonJatsTranslationsCount());

            xlsHelper.addCurrentSheet(SHEET_1);
            col = 0;
            xlsHelper.registerTitleColumn(col++, XLSHelper.WIDE_FACTOR_3, DOI, 1);
            xlsHelper.registerTitleColumn(col++, 1, centerFormat, STAGE, 1);
            xlsHelper.registerTitleColumn(col++, XLSHelper.WIDE_FACTOR_2, centerFormat, RM_ID, 1);
            xlsHelper.registerTitleColumn(col++, XLSHelper.WIDE_FACTOR_2, rightFormat, RM_VERSION, 1);
            xlsHelper.registerTitleColumn(col++, 1, JATS, 1);
            xlsHelper.registerTitleColumn(col++, XLSHelper.WIDE_FACTOR_2, RM_EXISTS, 1);
            xlsHelper.registerTitleColumn(col, XLSHelper.WIDE_FACTOR_2, WML21_EXISTS, 1);

            xlsHelper.addCurrentSheet(SHEET_2);
            col = 0;
            xlsHelper.registerTitleColumn(col++, XLSHelper.WIDE_FACTOR_3, DOI, 2);
            xlsHelper.registerTitleColumn(col++, 1, LANG, 2);
            xlsHelper.registerTitleColumn(col++, 1, LANG_COCHRANE, 2);
            xlsHelper.registerTitleColumn(col++, 1, JATS, 2);
            xlsHelper.registerTitleColumn(col++, XLSHelper.WIDE_FACTOR_4, TA_ID, 2);
            xlsHelper.registerTitleColumn(col++, XLSHelper.WIDE_FACTOR_2, TA_VERSION, 2);
            xlsHelper.registerTitleColumn(col++, XLSHelper.WIDE_FACTOR_3, RM_ID, 2);
            xlsHelper.registerTitleColumn(col, XLSHelper.WIDE_FACTOR_2, RM_VERSION, 2);
            //xlsHelper.registerTitleColumn(col, XLSHelper.WIDE_FACTOR_2, TA_LAST_UPDATE, 2);

            xlsHelper.setCurrentSheet(1);
            int row = 1;
            int taRow = 1;
            Iterator<CochraneItem> it = stats.getItemIterator();
            while (it.hasNext()) {
                CochraneItem item = it.next();
                xlsHelper.addValue(item.getDoi(), DOI, row, 1);
                xlsHelper.addValue(item.getStage(), STAGE, row, 1);
                xlsHelper.addValue(item.getSid(), RM_ID, row, 1);
                xlsHelper.addValue(item.getVersion(), RM_VERSION, row, 1);
                xlsHelper.addValue(item.hasJats(), JATS, row, 1);
                xlsHelper.addValue(item.hasRM(), RM_EXISTS, row, 1);
                xlsHelper.addValue(item.hasWML21(), WML21_EXISTS, row++, 1);

                writeManifestRecord(item, csvWriter);

                taRow = addTranslationRows(item, taRow, csvWriter, xlsHelper);
            }

            csvWriter.flush();
            csvWriter.close();

            xlsHelper.closeAndSaveToRepository(tempFile, manifest, path, true, RepositoryFactory.getRepository());

        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
        }
    }

    private static int addTranslationRows(CochraneItem item, int startRow, CSVPrinter csvWriter, XLSHelper xlsHelper)
            throws Exception {
        if (item.translations == null || item.translations.isEmpty()) {
            return startRow;
        }
        int row = startRow;
        int curSheet = 2;
        xlsHelper.setCurrentSheet(curSheet);
        for (CochraneItem ta: item.translations) {
            
            xlsHelper.addValue(ta.doi, DOI, row, curSheet);
            xlsHelper.addValue(ta.getLanguage(), LANG, row, curSheet);
            xlsHelper.addValue(ta.getOriginalLanguage(), LANG_COCHRANE, row, curSheet);
            xlsHelper.addValue(ta.hasJats(), JATS, row, curSheet);
            xlsHelper.addValue(ta.getCochraneTaSid(), TA_ID, row, curSheet);
            xlsHelper.addValue(ta.getCochraneTaVersion(), TA_VERSION, row, curSheet);
            xlsHelper.addValue(ta.getSid(), RM_ID, row, curSheet);
            xlsHelper.addValue(ta.getVersion(), RM_VERSION, row++, curSheet);

            writeManifestRecord(ta, csvWriter);
        }
        xlsHelper.setCurrentSheet(1);
        return row;
    }

    public static Statistic<CochraneItem> findCDSRStatistic() {
        Context context = new Context();
        Statistic<CochraneItem> ret = new Statistic<>(CochraneCMSPropertyNames.getCDSRDbName());
        IVersionManager vm = CochraneCMSBeans.getVersionManager();

        List<String> cdNumbers = EntireDBStorageFactory.getFactory().getInstance().findSysrevRecordNames();
        LOG.info(String.format("getting latest and previous articles for %d cdNumbers started  ...", cdNumbers.size()));
        cdNumbers.forEach(cdNumber -> addPrevious(cdNumber, vm.getVersions(cdNumber), ret));

        collectMetadata(ret, context);
        collectTranslations(ret, context);

        LOG_TST.warn(ret.getErrors());

        Iterator<CochraneItem> it = ret.getItemIterator();
        while (it.hasNext()) {
            CochraneItem ci = it.next();
            if (ci.meta == null) {
                LOG.error("missing metadata: " + ci);
            }
        }

        return ret;
    }

    private static void collectMetadata(Statistic<CochraneItem> stats, Context context) {
        int start = 0;
        List<ICDSRMeta> list = context.rs.findAllMetadata(context.bt.getId(), start, DbConstants.DB_PACK_SIZE);
        while (!list.isEmpty()) {
            LOG.info(String.format("getting metadata (%d, %d) started", start, list.size()));
            for (ICDSRMeta meta : list) {
                if (!CmsUtils.isSpecialIssueNumber(meta.getIssue())) {
                    checkMetadata(meta.getCdNumber(), meta, meta.getHistoryNumber() > RecordEntity.VERSION_LAST
                            ? ContentLocation.PREVIOUS : ContentLocation.ENTIRE, stats, context);
                }
            }
            start += DbConstants.DB_PACK_SIZE;
            list = context.rs.findAllMetadata(context.bt.getId(), start, DbConstants.DB_PACK_SIZE);
        }
    }

    private static void collectTranslations(Statistic<CochraneItem> stats, Context context) {
        collectTranslations(context.rm::getLastTranslations, ContentLocation.ENTIRE, stats, context);
        collectTranslations(context.rm::getAllTranslationHistory, ContentLocation.PREVIOUS, stats, context);
    }

    private static void collectTranslations(BiFunction<Integer, Integer, List<DbRecordVO>> getter,
                                            ContentLocation cl, Statistic<CochraneItem> stats, Context context) {
        int start = 0;
        List<DbRecordVO> list = getter.apply(start, DbConstants.DB_PACK_SIZE);
        while (!list.isEmpty()) {
            LOG.info(String.format("getting %s translations (%d, %d) started", cl, start, list.size()));
            list.forEach(ta -> checkTranslation(ta, cl, stats, context));
            start += DbConstants.DB_PACK_SIZE;
            list = getter.apply(start, DbConstants.DB_PACK_SIZE);
        }
    }

    private static void checkTranslation(DbRecordVO vo, ContentLocation cl,
                                         Statistic<CochraneItem> stats, Context context) {
        if (!vo.isDeleted()) {
            String cdNumber = RecordHelper.buildCdNumber(vo.getNumber());
            int pub = RevmanMetadataHelper.parsePubNumber(vo.getLabel());
            CochraneItem item = stats.getHistoryMap(cdNumber).get(pub);
            if (item == null) {
                LOG_TST.warn(String.format("there is no article for %s", vo));
                return;
            }
            if (item.historyVersion != vo.getVersion()) {
                stats.addError(item, String.format("different history: translation %s %s vs %s",
                        vo.getLanguage(), vo.getVersion(), item.historyVersion));
                return;
            }
            addTranslation(cdNumber, item, vo, cl, stats, context);
        }
    }

    private static void checkMetadata(String cdNumber, ICDSRMeta meta, ContentLocation cl,
                                      Statistic<CochraneItem> stats, Context context) {
        CochraneItem item = stats.getHistoryMap(cdNumber).get(meta.getPubNumber());
        if (item == null || item.meta != null) {
            return;
        }
        if (!item.historyVersion.equals(meta.getHistoryNumber())) {
            stats.addError(item, String.format("different history: metadata %s vs %s",
                    meta.getHistoryNumber(), item.historyVersion));
            return;
        }
        item.meta = meta;
        item.jats = meta.isJats();
        item.setUpdate(meta.getPublishedDate());
        ICDSRMeta contentMeta = null;
        if (item.jats) {
            String path = cl.getPathToJatsSrcDir(null, stats.dbName, null,
                    item.historyVersion, cdNumber) + FilePathCreator.SEPARATOR + cdNumber + Extensions.XML;
            if (checkFile(item, path, stats, context)) {
                item.metadataPath = path;
                contentMeta = extractJatsMetadata(cdNumber, item, path, meta, stats, context);
            }
        } else {
            String wml21path = cl.getPathToMl21SrcDir(null, stats.dbName, null, item.historyVersion, cdNumber)
                    + Extensions.XML;
            if (checkFile(item, wml21path, stats, context)) {
                item.wml21Path = wml21path;
            }
            String rmPath = cl.getPathToRevmanSrc(null, item.historyVersion, item.group, cdNumber);
            String rmMetadataPath = cl.getPathToRevmanMetadata(null, item.historyVersion, item.group, cdNumber);
            if (checkFile(item, rmPath, stats, context) && checkFile(item, rmMetadataPath, stats, context)) {
                item.metadataPath = rmMetadataPath;
                contentMeta = extractRevmanMetadata(item, rmMetadataPath, meta, stats, context);
            }
        }
        stats.addStats(!item.isHistorical(), item.jats, false, 1);
        if (contentMeta != null) {
            item.setSid(contentMeta.getRevmanId());
            item.setVersion(contentMeta.getCochraneVersion());
        }
    }

    private static ICDSRMeta extractJatsMetadata(String cdNumber, CochraneItem item, String path, ICDSRMeta dbMeta,
                                                 Statistic<CochraneItem> stats, Context context) {
        try {
            return context.jatsHelper.extractMetadata(cdNumber, item.pub, path, dbMeta.getIssue(), context.bt,
                    context.rp, context.cache);
        } catch (Exception e) {
            stats.addError(item, e.getMessage());
        }
        return null;
    }

    private static boolean extractJatsTranslation(CochraneItem item, String language, TranslatedAbstractVO tvo,
        String[] rmIds, ContentLocation cl, Statistic<CochraneItem> stats, Context context) {
        String path = null;
        try {
            String jatsTaFolderPath = cl.getPathToJatsTA(null, null, item.historyVersion, language, item.cdNumber);
            //path = RecordHelper.findPathToJatsTAOriginalRecord(jatsTaFolder, item.cdNumber, context.rp);
            path = jatsTaFolderPath + FilePathBuilder.buildTAFileName(language, item.cdNumber);
            if (!checkFile(item, path, stats, context)) {
                return false;
            }
            tvo.setLanguage(null);
            context.jatsHelper.extractMetadata(tvo, path, null, rmIds, context.rp);

        } catch (Exception e) {
            stats.addError(item, tvo.getLanguage(), e.getMessage());
        }
        return path != null;
    }

    private static ICDSRMeta extractRevmanMetadata(CochraneItem item, String path, ICDSRMeta dbMeta,
                                                   Statistic<CochraneItem> stats, Context context) {
        try {
            return context.rmHelper.extractMetadata(path, dbMeta, context.rp);
        } catch (Exception e) {
            stats.addError(item, e.getMessage());
        }
        return null;
    }

    private static boolean extractLegacyTranslation(CochraneItem item, String language, TranslatedAbstractVO tvo,
        String[] rmIds, ContentLocation cl, Statistic<CochraneItem> stats, Context context) {
        try {
            String path = cl.getPathToTA(null, item.historyVersion, language, item.cdNumber);
            if (!checkFile(item, path, stats, context)) {
                return false;
            }
            InputStream is = context.rp.getFile(path);
            TranslatedAbstractsCorrector.parseAbstract(is, tvo, rmIds, context.failedLegacyTa);
            IOUtils.closeQuietly(is);
            tvo.setPath(path);

        } catch (Exception e) {
            stats.addError(item, tvo.getLanguage(), e.getMessage());

        } finally {
            if (!context.failedLegacyTa.isEmpty()) {
                stats.addError(item, context.failedLegacyTa.get(0).getErrorDetail());
                context.failedLegacyTa.clear();
            }
        }
        return true;
    }

    private static boolean checkFile(CochraneItem ci, String path, Statistic<CochraneItem> stats, Context context) {
        if (!context.rp.isFileExistsQuiet(path)) {
            stats.addError(ci, String.format("no content found in %s", path));
            return false;
        }
        return true;
    }

    private static void addTranslation(String cdNumber, CochraneItem item, DbRecordVO vo, ContentLocation cl,
                                       Statistic<CochraneItem> stats, Context context) {
        String lang = vo.getLanguage();
        TranslatedAbstractVO tvo = new TranslatedAbstractVO(cdNumber, lang);
        tvo.setPubNumber(item.pub);
        String[] rmIds = {null, null};

        if (vo.isJats()) {
            if (!extractJatsTranslation(item,  lang, tvo, rmIds, cl, stats, context)) {
                return;
            }
        } else if (!extractLegacyTranslation(item,  lang, tvo, rmIds, cl, stats, context)) {
            return;
        }
        CochraneItem ta = item.addTranslation(tvo, vo.isJats(), vo.getLastUpdate());
        ta.setSid(rmIds[0]);
        ta.setVersion(rmIds[1]);
        stats.addStats(!ta.isHistorical(), ta.jats, true, 1);

        checkVersion(item, ta, vo, stats, context);
    }

    private static void checkVersion(CochraneItem item, CochraneItem ta, DbRecordVO vo,
                                     Statistic<CochraneItem> stats, Context context) {
        String rmVersion = item.getVersion();
        String taVersion = ta.getVersion();
        if (rmVersion.equals(taVersion) || Constants.NA.equals(rmVersion) || Constants.NA.equals(taVersion)) {
            return;
        }
        if (vo.getDfId() != null) {
            DeliveryFileVO df = context.rs.getDeliveryFileVO(vo.getDfId());
            if (df != null) {
                ta.setUpdate(df.getDate());
            }
        }
        stats.addError(item, String.format("%s version is %s", ta, taVersion));
    }
    
    private static boolean addPrevious(String cdNumber, List<PrevVO> list, Statistic<CochraneItem> stats) {
        if (list.isEmpty()) {
            stats.addError(cdNumber, "has no previous");
            return false;
        }
        PrevVO last = list.remove(0);
        Integer curVersion = last.version;
        addPrevious(cdNumber, last, RecordEntity.VERSION_LAST, stats);
        for (PrevVO prev: list) {
            if (!prev.version.equals(curVersion)) {
                addPrevious(cdNumber, prev, prev.version, stats);
            }
            curVersion = prev.version;
        }
        return true;
    }

    private static void addPrevious(String cdNumber, PrevVO prev, Integer v, Statistic<CochraneItem> stats) {
        Map<Integer, CochraneItem> map = stats.getHistoryMap(cdNumber);
        if (map != null) {
            map.computeIfAbsent(prev.pub, f -> new CochraneItem(cdNumber, prev.pub, prev.buildDoi(), prev.group, v));
        }
    }

    private static CSVPrinter createManifestWriter(File manifest) throws Exception{
        return new CSVPrinter(new FileWriter(manifest), CSVFormat.EXCEL.withNullString(
            Constants.NA).withEscape('\\').withQuoteMode(QuoteMode.NONE).withDelimiter('\t').withHeader(
                padValue24("UNIT"), padValue24("RM-ID"), padValue8("RM-VERSION"), padValue8(JATS),
                padValue32("TA-ID"), padValue8("TA-VERSION"), "UPDATE"));
    }

    private static void writeManifestRecord(CochraneItem item, CSVPrinter csvWriter) throws Exception {
        csvWriter.printRecord(padValue24(item.getPubNameAndLanguage()), padValue24(item.getSid()),
                padValue8(item.getVersion()), padValue8(item.hasJats()), padValue32(item.getCochraneTaSid()),
                padValue8(item.getCochraneTaVersion()), item.getUpdate());
    }

    private static String padValue8(String value) {
        return String.format("%-8s", value);
    }

    private static String padValue16(String value) {
        return String.format("%-16s", value);
    }

    private static String padValue32(String value) {
        return String.format("%-32s", value);
    }

    private static String padValue24(String value) {
        return String.format("%-24s", value);
    }

    private static class Context {
        IRepository rp = RepositoryFactory.getRepository();
        IRecordCache cache = CochraneCMSBeans.getRecordCache();
        IRecordManager rm = CochraneCMSBeans.getRecordManager();
        IResultsStorage rs = ResultStorageFactory.getFactory().getInstance();
        JatsHelper jatsHelper = new JatsHelper();
        RevmanMetadataHelper rmHelper = new RevmanMetadataHelper(rm, rs);
        BaseType bt = BaseType.getCDSR().get();

        List<ErrorInfo<TranslatedAbstractVO>> failedLegacyTa = new ArrayList<>();
    }

    /**
     * An item for Cochrane CDSR report
     */
    public static class CochraneItem extends Statistic.Item {

        public final String group;

        String cochraneLanguage = null;
        String cochraneTaSid;
        String cochraneTaVersion = null;

        boolean jats = false;

        String metadataPath;
        String wml21Path;

        ICDSRMeta meta;
        List<CochraneItem> translations;
        Date update;

        private CochraneItem(String cdNumber, int pub, String doi, String group, Integer historyVersion) {
            super(cdNumber, pub, doi, historyVersion);
            this.group = group;
        }

        private CochraneItem addTranslation(TranslatedAbstractVO tvo, boolean jats, Date date) {
            CochraneItem ai = new CochraneItem(tvo.getName(), tvo.getPubNumber(), tvo.getDoi(), group, historyVersion);
            ai.language = tvo.getLanguage();
            ai.cochraneLanguage = tvo.getOriginalLanguage();
            ai.meta = meta;
            ai.jats = jats;
            ai.cochraneTaSid = tvo.getSid();
            ai.cochraneTaVersion = tvo.getCochraneVersion();
            ai.metadataPath = tvo.getPath();
            ai.update = date;

            if (translations == null) {
                translations = new ArrayList<>();
            }
            translations.add(ai);
            return ai;
        }

        public String getCochraneTaSid() {
            return getValue(cochraneTaSid);
        }

        public String getCochraneTaVersion() {
            return getVersionValue(cochraneTaVersion);
        }

        public String getCochraneVersionDb() {
            return meta.getCochraneVersion();
        }

        public String getStage() {
            return meta == null ? Constants.NA : meta.getStage();
        }

        public String getOriginalLanguage() {
            return getValue(cochraneLanguage);
        }

        public String hasWML21() {
            return getYesOrNo(wml21Path != null);
        }

        public String hasRM() {
            return getYesOrNo(!jats && metadataPath != null);
        }

        public String hasJats() {
            return getYesOrNo(jats && metadataPath != null);
        }

        public boolean isJats() {
            return jats;
        }

        public void setUpdate(Date date) {
            update = date;
        }

        public String getUpdate() {
            return update == null ? Constants.NA : Now.SHORT_DATE_FORMATTER.format(update);
        }

        public ICDSRMeta getMeta() {
            return meta;
        }
    }
}
