package com.wiley.cms.cochrane.cmanager.specrender.xlsfiles;

import java.awt.Color;
import java.io.File;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.wiley.cms.cochrane.activitylog.ActivityLogEntity;
import com.wiley.cms.cochrane.activitylog.ActivityLogFactory;
import com.wiley.cms.cochrane.activitylog.IActivityLogService;
import com.wiley.cms.cochrane.activitylog.ILogEvent;
import com.wiley.cms.cochrane.cmanager.CmsUtils;
import com.wiley.cms.cochrane.cmanager.CochraneCMSBeans;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.DeliveryPackage;
import com.wiley.cms.cochrane.cmanager.FilePathBuilder;
import com.wiley.cms.cochrane.cmanager.contentworker.ArchiePackage;
import com.wiley.cms.cochrane.cmanager.contentworker.TranslatedAbstractsPackage;
import com.wiley.cms.cochrane.cmanager.data.ClDbEntity;
import com.wiley.cms.cochrane.cmanager.data.DeliveryFileEntity;
import com.wiley.cms.cochrane.cmanager.data.DeliveryFileVO;
import com.wiley.cms.cochrane.cmanager.data.IResultsStorage;
import com.wiley.cms.cochrane.cmanager.data.IssueEntity;
import com.wiley.cms.cochrane.cmanager.data.PrevVO;
import com.wiley.cms.cochrane.cmanager.data.PublishEntity;
import com.wiley.cms.cochrane.cmanager.data.PublishRecordEntity;
import com.wiley.cms.cochrane.cmanager.data.record.ProductSubtitleEntity;
import com.wiley.cms.cochrane.cmanager.data.record.RecordEntity;
import com.wiley.cms.cochrane.cmanager.data.record.RecordMetadataEntity;
import com.wiley.cms.cochrane.cmanager.data.translation.PublishedAbstractEntity;
import com.wiley.cms.cochrane.cmanager.entitymanager.AbstractManager;
import com.wiley.cms.cochrane.cmanager.entitymanager.RecordHelper;
import com.wiley.cms.cochrane.cmanager.entitymanager.RevmanSource;
import com.wiley.cms.cochrane.cmanager.entitywrapper.ResultStorageFactory;
import com.wiley.cms.cochrane.cmanager.publish.IPublishStorage;
import com.wiley.cms.cochrane.cmanager.publish.PublishDestination;
import com.wiley.cms.cochrane.cmanager.res.PackageType;
import com.wiley.cms.cochrane.cmanager.res.PathType;
import com.wiley.cms.cochrane.cmanager.res.PubType;
import com.wiley.cms.cochrane.cmanager.res.PublishProfile;
import com.wiley.cms.cochrane.cmanager.translated.TranslatedAbstractVO;
import com.wiley.cms.cochrane.repository.IRepository;
import com.wiley.cms.cochrane.repository.RepositoryFactory;
import com.wiley.cms.cochrane.repository.RepositoryUtils;
import com.wiley.cms.cochrane.utils.Constants;
import com.wiley.cms.process.entity.DbEntity;
import com.wiley.tes.util.Pair;
import com.wiley.cms.cochrane.utils.XLSHelper;
import com.wiley.cms.converter.services.RevmanMetadataHelper;
import com.wiley.tes.util.DbConstants;
import com.wiley.tes.util.Logger;
import com.wiley.tes.util.Now;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 4/21/2015
 */
public final class WhenReadyReportCreator {
    private static final Logger LOG = Logger.getLogger(WhenReadyReportCreator.class);

    private static final Color GREEN = new Color(196, 215, 155);
    private static final Color LIGHT_GREEN_1 = new Color(216, 226, 188);
    private static final Color LIGHT_GREEN_2 = new Color(235, 241, 222);
    private static final Color BLUE_1 = new Color(48, 87, 130);
    private static final Color BLUE_2 = new Color(107, 133, 165);
    private static final Color LIGHT_BLUE_1 = new Color(212, 224, 237);
    private static final Color LIGHT_BLUE_2 = new Color(149, 179, 215);
    private static final int ROW_MAIN_CONTEXT = 4;
    private static final int TABLE_1_X_SIZE = 3;
    private static final int TABLE_2_X_SIZE = 15;
    private static final int HIGH_FACTOR_3 = 3;
    private static final String MT = "MT";
    private static final String TA = "TA";
    private static final int TIME_BETWEEN_CRG_TA_AND_WML21 = Now.calculateMillisInMinute() * 3;
    private static final PackageType PT = PackageType.findCDSRJats().get();

    private WhenReadyReportCreator() {
    }

    public static synchronized void createXlsWhenReady(ClDbEntity clDbId, String path) {
        try {
            createXlsWR(findStatistic(clDbId), path);
        } catch (Exception e) {
            LOG.error(e, e);
        }
    }

    private static void createXlsWR(WRInfo st, String path) throws Exception {
        List<ArticleInfo> all = st.sortAll();
        File tempFile = File.createTempFile(Constants.TEMP, "");
        XLSHelper xlsHelper = new XLSHelper(tempFile, "Overall", true, 2);
        int greenBigTitleFormat = xlsHelper.registerFormat(null, GREEN, true, true, true);
        int lGreenFormat1 = xlsHelper.registerFormat(null, LIGHT_GREEN_1, false, false, true);
        int lGreenFormat2 = xlsHelper.registerFormat(null, LIGHT_GREEN_2, false, false, true);

        xlsHelper.addValue(String.format("Overall for Issue %d, %d", st.month, st.year), 0, 0, greenBigTitleFormat);
        xlsHelper.mergeCells(0, 0, TABLE_1_X_SIZE - 1, 0);
        xlsHelper.addValue(String.format("%d articles were published", st.countPublished),
                0, 1, XLSHelper.WIDE_FACTOR_3, lGreenFormat1);
        xlsHelper.mergeCells(0, 1, TABLE_1_X_SIZE - 1, HIGH_FACTOR_3);
        int row = ROW_MAIN_CONTEXT;
        addColorLines(row++, "main context update\n(only last updating counted)",
                format(st.pro.count, st.rev.count), "", lGreenFormat2, xlsHelper);
        xlsHelper.mergeCells(1, ROW_MAIN_CONTEXT, 2, ROW_MAIN_CONTEXT);
        addColorLines(row++, "", "NEW", format(st.pro.getNew(), st.rev.getNew()), lGreenFormat2, xlsHelper);
        addColorLines(row++, "", "WITHDRAWN", format(st.pro.getWithdrawn(), st.rev.getWithdrawn()),
                lGreenFormat2, xlsHelper);
        addColorLines(row, "", "major & minor update", format(st.pro.getUpdated(), st.rev.getUpdated()),
                lGreenFormat2, xlsHelper);
        xlsHelper.mergeCells(0, ROW_MAIN_CONTEXT, 0, row);
        int start = ++row;
        addColorLines(row++, "update with translations", format(st.countOnlyTA), "", lGreenFormat2,
                xlsHelper);
        addColorLines(row++, "update with monthly MeSH terms", format(st.countMT), "", lGreenFormat2, xlsHelper);
        addColorLines(row, "update with historical versions", format(st.pro.historyCount, st.rev.historyCount), "",
                lGreenFormat2, xlsHelper);
        xlsHelper.resetFormat(TABLE_1_X_SIZE);
        xlsHelper.mergeCells(2, start, 2, row);

        xlsHelper.addCurrentSheet("XDPS-PWR"); // second sheet
        int blueBigTitleFormat1 = xlsHelper.registerFormat(Color.WHITE, BLUE_1, true, true, false);
        int blueBigTitleFormat2 = xlsHelper.registerFormat(Color.WHITE, BLUE_2, true, true, false);
        xlsHelper.addValue(" XDPS - Process When Ready - Summary", 0, 0, blueBigTitleFormat1);
        xlsHelper.mergeRow(0, TABLE_2_X_SIZE);
        xlsHelper.addValue(String.format(" Issue %d, %d  UTC", st.month, st.year), 0, 1, blueBigTitleFormat2);
        xlsHelper.mergeRow(1, TABLE_2_X_SIZE);
        row = 2;
        xlsHelper.mergeRow(row++, TABLE_2_X_SIZE);
        int lBlueTitleFormat1 = xlsHelper.registerFormat(null, LIGHT_BLUE_1, false, true, true);
        int col = 0;
        String wol = PublishProfile.PUB_PROFILE.get().getDestination().isWhenReadyType(PubType.TYPE_LITERATUM)
                ? "LIT" : "WOL";
        xlsHelper.addValue("CD Number", col++, row, XLSHelper.WIDE_FACTOR_2, lBlueTitleFormat1);
        xlsHelper.addValue("Type", col++, row, lBlueTitleFormat1);
        xlsHelper.addValue("Status", col++, row, XLSHelper.WIDE_FACTOR_1_5, lBlueTitleFormat1);
        xlsHelper.addValue("Quantity in package", col++, row, XLSHelper.WIDE_FACTOR_1_5, lBlueTitleFormat1);
        xlsHelper.addValue("Start processing", col++, row, XLSHelper.WIDE_FACTOR_2, lBlueTitleFormat1);
        xlsHelper.addValue("XDPS processing time", col++, row, XLSHelper.WIDE_FACTOR_2, lBlueTitleFormat1);
        xlsHelper.addValue("Send to " + wol, col++, row, XLSHelper.WIDE_FACTOR_2, lBlueTitleFormat1);
        xlsHelper.addValue("Published in " + wol, col++, row, XLSHelper.WIDE_FACTOR_2, lBlueTitleFormat1);
        xlsHelper.addValue(wol + " processing time", col++, row, XLSHelper.WIDE_FACTOR_2, lBlueTitleFormat1);
        xlsHelper.addValue("Send to HW", col++, row, XLSHelper.WIDE_FACTOR_2, lBlueTitleFormat1);
        xlsHelper.addValue("Published in HW", col++, row, XLSHelper.WIDE_FACTOR_2, lBlueTitleFormat1);
        xlsHelper.addValue("HW processing time", col++, row, XLSHelper.WIDE_FACTOR_2, lBlueTitleFormat1);
        xlsHelper.addValue("Notified Archie", col++, row, XLSHelper.WIDE_FACTOR_2, lBlueTitleFormat1);
        xlsHelper.addValue("All processing time", col++, row, XLSHelper.WIDE_FACTOR_2, lBlueTitleFormat1);
        xlsHelper.addValue("Initial Package", col++, row, XLSHelper.WIDE_FACTOR_7, lBlueTitleFormat1);
        xlsHelper.addValue("Acknowledgement Package", col++, row, XLSHelper.WIDE_FACTOR_7, lBlueTitleFormat1);
        xlsHelper.addValue("Article Title", col, row++, XLSHelper.WIDE_FACTOR_15, lBlueTitleFormat1);

        if (!all.isEmpty()) {
            row = addArticles(all, row, prepareLineFormat(true, xlsHelper),
                    prepareLineFormat(false, xlsHelper), xlsHelper);
            xlsHelper.mergeRow(row, TABLE_2_X_SIZE);
        }
        xlsHelper.resetFormat(TABLE_2_X_SIZE);
        xlsHelper.closeAndSaveToRepository(tempFile, path, false, RepositoryFactory.getRepository());
    }

    private static int[][] prepareLineFormat(boolean centre, XLSHelper xlsHelper) throws Exception {
        int lineFormat1 = xlsHelper.registerFormat(null, Color.WHITE, false, false, centre);
        int oldLineFormat1 = xlsHelper.registerFormat(Color.GRAY, Color.WHITE, false, false, centre);
        int oldLineDelayedFormat1 = xlsHelper.registerFormat(Color.GRAY, Color.YELLOW, false, false, centre);
        int lineDelayedFormat1 = xlsHelper.registerFormat(null, Color.YELLOW, false, false, centre);
        int[][] ret = new int[2][2];
        ret[0][0] = lineFormat1;
        ret[1][0] = oldLineFormat1;
        ret[0][1] = lineDelayedFormat1;
        ret[1][1] = oldLineDelayedFormat1;
        return ret;
    }

    private static String buildDate(Date date) {
        return (date == null) ? Constants.NA : Now.buildDate(date);
    }

    private static String buildDifference(Date startDt, Date endDt) {
        return (startDt == null || endDt == null) ? Constants.NA
                : endDt.getTime() >= startDt.getTime() ? Now.buildFullTime(endDt.getTime() - startDt.getTime()) : "?";
    }

    private static int addArticles(Iterable<ArticleInfo> recs, int row, int[][] f1, int[][] f2, XLSHelper xls)
            throws Exception {
        int ret = row;
        for (ArticleInfo ai: recs) {
            if (!ai.hasNoPackage() && ai.pack.countIn > 0) {
                int fi0 = ai.old ? 1 : 0;
                int fi1 = ai.delayed ? 1 : 0;
                int col = 0;
                xls.addValue(ai.toString(), col++, ret, f2[fi0][fi1]);
                xls.addValue(ai.type, col++, ret, f1[fi0][fi1]);
                xls.addValue(ai.status, col++, ret, f1[fi0][fi1]);
                xls.addValue("" + ai.pack.countIn, col++, ret, f1[fi0][fi1]);
                xls.addValue(buildDate(ai.pack.start), col++, ret, f1[fi0][fi1]);
                xls.addValue(buildDifference(ai.pack.start, ai.pack.end), col++, ret, f1[fi0][fi1]);
                xls.addValue(buildDate(ai.sentWOL), col++, ret, f1[fi0][fi1]);
                xls.addValue(buildDate(ai.publishedWOL), col++, ret, f1[fi0][fi1]);
                xls.addValue(buildDifference(ai.sentWOL, ai.publishedWOL), col++, ret, f1[fi0][fi1]);
                xls.addValue(buildDate(ai.sentHW), col++, ret, f1[fi0][fi1]);
                xls.addValue(buildDate(ai.publishedHW), col++, ret, f1[fi0][fi1]);
                xls.addValue(buildDifference(ai.sentHW, ai.publishedHW), col++, ret, f1[fi0][fi1]);
                xls.addValue(buildDate(ai.archieNotified), col++, ret, f1[fi0][fi1]);
                xls.addValue(buildDifference(ai.initialPack.start, ai.archieNotified), col++, ret, f1[fi0][fi1]); 
                xls.addValue(ai.pack.toString(), col++, ret, f2[fi0][fi1]);
                xls.addValue(ai.ackPack == null ? "" : ai.ackPack.toString(), col++, ret, f2[fi0][fi1]);
                xls.addValue(ai.title, col, ret, f2[fi0][fi1]);
                ret++;
            }
        }
        return ret;
    }

    private static String format(int count) {
        return count == 0 ? "-" : String.format("%d %s", count, "articles");
    }

    private static String format(int pCount, int rCount) {
        String ret = "-";
        if (pCount != 0 && rCount != 0) {
            ret = String.format("%d protocols, %d reviews", pCount, rCount);
        } else if (pCount != 0) {
            ret = String.format("%d protocols", pCount);
        } else if (rCount != 0) {
            ret = String.format("%d reviews", rCount);
        }
        return ret;
    }

    private static void addColorLines(int row, String val1, String val2, String val3, int colorFormat, XLSHelper xls)
        throws Exception {
        xls.addValue(val1, 0, row, XLSHelper.WIDE_FACTOR_3, colorFormat);
        xls.addValue(val2, 1, row, XLSHelper.WIDE_FACTOR_3, colorFormat);
        xls.addValue(val3, 2, row, XLSHelper.WIDE_FACTOR_3, colorFormat);
    }

    private static WRInfo findStatistic(ClDbEntity clDb) throws Exception {
        WRInfo ret = new WRInfo();
        IssueEntity issue = clDb.getIssue();
        int issueNumber = issue.getFullNumber();
        ret.year = issue.getYear();
        ret.month = issue.getNumber();
        IResultsStorage rs = AbstractManager.getResultStorage();
        Map<Integer, PackageInfo> dfInfo = new HashMap<>();
        // CD number -> Article : Language -> Translation
        Map<String, Pair<ArticleInfo, Map<String, ArticleInfo>>> recordMap = new HashMap<>();
        Map<Integer, PackageInfo> taPackagesMap = new HashMap<>();
        // arrange all the packages and translations from the file system
        getPackageInfo(clDb, rs.getDeliveryFiles(clDb.getId()), dfInfo, recordMap, taPackagesMap);
        List<RecordMetadataEntity> list = rs.findRecordsMetadata(issue.getId(), clDb.getTitle());
        for (RecordMetadataEntity rm: list) {
            boolean isP = rm.isStageP();
            Pair<ArticleInfo, Map<String, ArticleInfo>> metaInfo = recordMap.get(rm.getCdNumber());
            if (metaInfo != null) {
                metaInfo.first.updateProperties(rm, isP ? ret.pro : ret.rev, isP, new ArticleInfo[]{null});
            }
        }
        List<RecordEntity> records = rs.getRecords(clDb.getId());
        Collection<String> articlesWithoutPubNumbers = new ArrayList<>();
        for (RecordEntity re: records) {
            String cdNumber = re.getName();
            Pair<ArticleInfo, Map<String, ArticleInfo>> metadata = updateProperties(cdNumber, re, recordMap, dfInfo,
                    taPackagesMap, ret);
            if (metadata.first.hasNoPub()) {
                articlesWithoutPubNumbers.add(cdNumber);
                if (articlesWithoutPubNumbers.size() == DbConstants.DB_QUERY_VARIABLE_COUNT) {
                    updatePubNumbers(issueNumber, articlesWithoutPubNumbers, recordMap);
                }
            }
        }
        updatePubNumbers(issueNumber, articlesWithoutPubNumbers, recordMap);
        updatePublishDates(clDb.getId(), dfInfo, recordMap, rs);
        updateUploadDates(dfInfo);
        return ret;
    }

    private static boolean isOldTranslatedAbstract(String packageName) {
        return packageName.contains(TranslatedAbstractsPackage.PACKAGE_NAME_PREFIX);
    }

    private static Pair<ArticleInfo, Map<String, ArticleInfo>> updateProperties(String cdNumber, RecordEntity re,
        Map<String, Pair<ArticleInfo, Map<String, ArticleInfo>>> recordMap, Map<Integer, PackageInfo> dfInfo,
            Map<Integer, PackageInfo> taPackagesMap, WRInfo ret) {

        Boolean isP = re.getProductSubtitle() == null ? null
                : (ProductSubtitleEntity.ProductSubtitle.PROTOCOLS == re.getProductSubtitle().getId()
                || ProductSubtitleEntity.ProductSubtitle.UPDATE_PROTOCOLS == re.getProductSubtitle().getId());
        boolean ta = DeliveryPackage.isTranslatedAbstract(re.getDeliveryFile().getType())
                || isOldTranslatedAbstract(re.getDeliveryFile().getName())
                || (re.getUnitStatus() != null && re.getUnitStatus().isTranslationUpdated());
        WRInfo.StatCounter stat = (isP == null) ? null : (isP ? ret.pro : ret.rev);
        if (stat != null) {
            stat.addCount(1);
        }
        if (RecordEntity.isPublished(re.getState())) {
            ret.countPublished++;
        }
        Pair<ArticleInfo, Map<String, ArticleInfo>> metaInfo = recordMap.get(cdNumber);
        if (metaInfo == null) {                  // this is meshterms or old clsysrev packages
            ArticleInfo ai = new ArticleInfo(re, isP);
            ai.setPackage(registerArticleInPackage(re.getDeliveryFile(), dfInfo));
            metaInfo = new Pair<>(ai, new HashMap<>());
            recordMap.put(cdNumber, metaInfo);
            if (re.getUnitStatus() != null && re.getUnitStatus().isMeshtermUpdated()) {
                ai.status = MT;
                ret.countMT++;
            } else {
                LOG.warn(String.format("%s: some data may be missed; package [%d], status is %s, ", ai,
                    re.getDeliveryFile().getId(), re.getUnitStatus()));
            }
            if (!ta) {
                ret.articles.add(ai);
            } else {
                LOG.warn(String.format("%s: unregister translation, package %s", ai, ai.pack.name));
            }  

        } else if (metaInfo.first.hasNoPackage()) { // there are only ta packages or old clsysrev packages
            if (!ta) {
                metaInfo.first.setPackage(registerArticleInPackage(re.getDeliveryFile(), dfInfo));
                ret.articles.add(metaInfo.first);
            }
            updateTranslations(ret, re, isP, metaInfo, taPackagesMap);
        } else {
            ret.articles.add(metaInfo.first);
            updateTranslations(ret, re, isP, metaInfo, taPackagesMap);
        }
        return metaInfo;
    }

    private static void updateTranslations(WRInfo stats, RecordEntity re, Boolean isP,
        Pair<ArticleInfo, Map<String, ArticleInfo>> metaInfo, Map<Integer, PackageInfo> taPackagesMap) {

        for (ArticleInfo ai: metaInfo.second.values()) {
            ai.updateProperties(re.getUnitTitleNormalized(), isP);
            PackageInfo taPackage = taPackagesMap.get(ai.pack.id);
            if (taPackage == null) {
                LOG.warn(String.format("%s: cannot find a translation package", ai));
            } else {
                ai.setPackage(taPackage);
            }
            stats.translations.add(ai);
        }
        stats.countTA += metaInfo.second.size();
        if (metaInfo.first.hasNoPackage()) {
            stats.countOnlyTA++;
        }
    }

    private static boolean getJatsTranslationsFromRepository(Integer issueId, PackageInfo pi,
        Collection<String> uniqueNames, Map<String, Pair<ArticleInfo, Map<String, ArticleInfo>>> map, IRepository rp) {
        String taPath = FilePathBuilder.TR.getPathToJatsTA(issueId, pi.id);
        File[] dirs = rp.getFilesFromDir(taPath);
        if (dirs == null || dirs.length == 0) {
            return false;
        }
        for (File fl : dirs) {
            if (!fl.isDirectory()) {
                continue;
            }
            File[] langs = fl.listFiles();
            if (langs == null || langs.length == 0) {
                continue;
            }
            for (File lang: langs) {
                addTranslations(fl.getName(), lang.getName(), pi, map.computeIfAbsent(fl.getName(),
                        m -> new Pair<>(new ArticleInfo(fl.getName(), null, null), new HashMap<>())));
                countIn(fl.getName() + lang.getName(), pi, uniqueNames);
            }
        }
        return true;
    }

    private static boolean getJatsArticlesFromRepository(ClDbEntity clDb, PackageInfo pi, int dfType,
            Map<String, Pair<ArticleInfo, Map<String, ArticleInfo>>> recordMap, IRepository rp) {
        Pattern template = PT.getArticlePattern();
        if (template == null) {
            return false;
        }
        Integer issueId = clDb.getIssue().getId();
        Collection<String> uniqueNames = new HashSet<>();
        boolean hasTranslations = DeliveryFileEntity.hasJatsTranslation(dfType)
                && getJatsTranslationsFromRepository(issueId, pi, uniqueNames, recordMap, rp);
        String path = FilePathBuilder.JATS.getPathToPackage(issueId, pi.id);
        File[] dirs = rp.getFilesFromDir(path);
        if (dirs != null && dirs.length > 0) {
            for (File fl : dirs) {
                if (template.matcher(fl.getName()).matches()) {
                    String name = fl.getName();
                    addArticle(name, pi, false, recordMap);
                    countIn(name, pi, uniqueNames); 
                }
            }
        }
        return hasTranslations;
    }

    private static void countIn(String name, PackageInfo pi, Collection<String> uniqueNames) {
        if (!uniqueNames.contains(name)) {
            pi.countIn++;
            uniqueNames.add(name);
        }
    }

    private static void getArticlesFromRepository(ClDbEntity clDb, PackageInfo pi,
        Map<String, Pair<ArticleInfo, Map<String, ArticleInfo>>> recordMap, IRepository rp) {

        String path = FilePathBuilder.getPathToWML21(clDb.getIssue().getId(), clDb.getTitle(), pi.sid);
        File[] dirs = rp.getFilesFromDir(path);
        if (dirs == null) {
            return;
        }
        Collection<String> uniqueNames = new HashSet<>();
        for (File fl : dirs) {
            if (fl.isDirectory()) {
                continue;
            }
            String name = RepositoryUtils.getFirstNameByFileName(fl.getName());
            addArticle(name, pi, true, recordMap);
            countIn(name, pi, uniqueNames);
        }
    }

    private static boolean getArticlesFromRepository(ClDbEntity clDb, String revmanSid, PackageInfo pi,
        Map<String, Pair<ArticleInfo, Map<String, ArticleInfo>>> recordMap, boolean onlyTa, IRepository rp)
            throws Exception {
        String path = FilePathBuilder.getPathToWML21(clDb.getIssue().getId(), clDb.getTitle(), revmanSid);
        String taPath = path + ArchiePackage.TRANSLATION_FOLDER;
        if (onlyTa) {
            path = taPath;
        }
        File[] dirs = rp.getFilesFromDir(path);
        if (dirs == null) {
            return false;
        }
        String revmanPath = FilePathBuilder.getPathToRevmanPackage(clDb.getIssue().getId(), revmanSid);
        boolean hasTranslations = rp.isFileExists(taPath);
        Collection<String> uniqueNames = new HashSet<>();
        for (File fl: dirs) {
            if (fl.isDirectory()) {
                continue;
            }
            String name = RepositoryUtils.getFirstNameByFileName(fl.getName());
            RevmanSource rs = RecordHelper.findInitialSourcesForRevmanPackage(name, revmanPath, rp);
            if (rs == null) {
                continue;
            }
            if (rs.revmanFile != null) {
                if (onlyTa) {
                    continue;
                }
                addArticle(name, pi, false, recordMap);
            }
            if (!rs.taFiles.isEmpty()) {
                addTranslations(name, rs.taFiles, pi, recordMap.computeIfAbsent(
                        name, m -> new Pair<>(new ArticleInfo(name, null, null), new HashMap<>())));
            } else if (rs.revmanFile == null) {
                addArticle(name, pi, false, recordMap);
            }
            countIn(name, pi, uniqueNames);
        }
        return hasTranslations;
    }

    private static void addArticle(String cdNumber, PackageInfo pi, boolean reloaded,
                                   Map<String, Pair<ArticleInfo, Map<String, ArticleInfo>>> recordMap) {
        Pair<ArticleInfo, Map<String, ArticleInfo>> recordScope = recordMap.get(cdNumber);
        ArticleInfo ai = new ArticleInfo(cdNumber, pi, null);
        if (recordScope == null) {
            recordScope = new Pair<>(ai, new HashMap<>());
        } else if (reloaded) {
            recordScope.first.reloaded = ai;

        } else {
            ai.moveToOthers(recordScope.first);
            recordScope = new Pair<>(ai, recordScope.second);
        }
        recordMap.put(cdNumber, recordScope);
    }

    private static void addTranslations(String cdNumber, String language, PackageInfo pi,
                                        Pair<ArticleInfo, Map<String, ArticleInfo>> recordScope) {
        ArticleInfo ai = new ArticleInfo(cdNumber, pi, language);
        ArticleInfo exist = recordScope.second.get(ai.language);
        if (exist != null) {
            if (exist.hasNoPackage() || exist.pack.id > pi.id) {
                exist.addOther(ai);
                return;
            } else {
                ai.moveToOthers(exist);
            }
        }
        recordScope.second.put(ai.language, ai);
    }

    private static void addTranslations(String cdNumber, Map<String, File> langs, PackageInfo pi,
                                        Pair<ArticleInfo, Map<String, ArticleInfo>> recordScope) {
        for (Map.Entry<String, File> lang: langs.entrySet()) {
            ArticleInfo ai = new ArticleInfo(cdNumber, pi, lang.getKey());
            recordScope.second.put(ai.language, ai);
            try {
                TranslatedAbstractVO record = TranslatedAbstractsPackage.parseFileName(lang.getValue().getName());
                ai.pub = record.getPubNumber();
            } catch (Exception e) {
                LOG.error(e.getMessage());
            }
        }
    }

    private static void getPackageInfo(ClDbEntity clDb, Iterable<DeliveryFileVO> dfs, Map<Integer, PackageInfo> dfMap,
        Map<String, Pair<ArticleInfo, Map<String, ArticleInfo>>> recordMap, Map<Integer, PackageInfo> taPackagesMap)
            throws Exception {
        IRepository rp = RepositoryFactory.getRepository();
        PackageInfo crgWithTranslations = null;

        for (DeliveryFileVO dvo: dfs) {
            PackageInfo pi = new PackageInfo(dvo);
            dfMap.put(dvo.getId(), pi);
            String name = dvo.getName();
            boolean jats = DeliveryFileEntity.isJats(dvo.getType());
            boolean ml3g = !jats && DeliveryFileEntity.isWml3g(dvo.getType());
            if (DeliveryPackage.isArchie(name) || jats) {
                if ((jats && getJatsArticlesFromRepository(clDb, pi, dvo.getType(), recordMap, rp))
                        || getArticlesFromRepository(clDb, pi.sid, pi, recordMap, false, rp)) {
                    crgWithTranslations = pi;
                    taPackagesMap.put(crgWithTranslations.id, pi);
                }
            } else if (isOldTranslatedAbstract(name)) {
                if (crgWithTranslations != null
                        && crgWithTranslations.start.getTime() - pi.start.getTime() < TIME_BETWEEN_CRG_TA_AND_WML21) {
                    taPackagesMap.put(pi.id, pi);
                    getArticlesFromRepository(clDb, crgWithTranslations.sid, pi, recordMap, true, rp);
                    crgWithTranslations = null;
                }
            } else if (ml3g) {
                getArticlesFromRepository(clDb, pi, recordMap, rp);
            }
        }
        PathType.freshWeakPaths();
    }

    private static void updatePubNumbers(int issue, Collection<String> recs,
        Map<String, Pair<ArticleInfo, Map<String, ArticleInfo>>> recordMap) {

        if (!recs.isEmpty()) {
            List<PrevVO> list = CochraneCMSBeans.getVersionManager().getLastVersions(issue, recs);
            for (PrevVO pvo : list) {
                Pair<ArticleInfo, Map<String, ArticleInfo>> metadata = recordMap.get(pvo.name);
                if (metadata != null && metadata.first.pub == 0) {
                    metadata.first.pub = pvo.pub;
                }
            }
        }
    }

    private static void updatePublishDates(Integer dbId, Map<Integer, PackageInfo> dfInfo,
        Map<String, Pair<ArticleInfo, Map<String, ArticleInfo>>> articleMap, IResultsStorage rs) {

        Map<String, Map<Integer, PublishRecordEntity>> mapWOL = new HashMap<>();
        Map<String, Map<Integer, PublishRecordEntity>> mapHW = new HashMap<>();
        PublishDestination dest = PublishProfile.PUB_PROFILE.get().getDestination();
        Collection<String> types = dest.getMainTypes();

        types.forEach(t -> makeSendingMap(dbId, PubType.TYPE_SEMANTICO.equals(t)
                ? mapHW : mapWOL, dest.getWhenReadyTypeId(t), CochraneCMSBeans.getPublishStorage(), rs));
        if (!CmsUtils.isScheduledDb(dbId)) {
            types.forEach(t -> makeSendingMap(Constants.SPD_DB_CDSR_ID, PubType.TYPE_SEMANTICO.equals(t)
                ? mapHW : mapWOL, dest.getWhenReadyTypeId(t), CochraneCMSBeans.getPublishStorage(), rs));
        }
        List<PublishedAbstractEntity> paList = CochraneCMSBeans.getPublishStorage().getWhenReady(dbId);
        updatePublishEventDates(paList, dfInfo, articleMap, mapWOL, mapHW);
    }

    private static void makeSendingMap(Integer dbId, Map<String, Map<Integer, PublishRecordEntity>> ret,
                                       Integer type, IPublishStorage ps, IResultsStorage rs) {
        List<PublishEntity> list = rs.findPublishesByDbAndType(dbId, type);
        Collection<Integer> ids = new ArrayList<>();
        for (PublishEntity pe: list) {
            ids.add(pe.getId());
            if (ids.size() == DbConstants.DB_QUERY_VARIABLE_COUNT) {
                makeSendingMap(ids, ret, ps);
            }
        }
        if (!ids.isEmpty()) {
            makeSendingMap(ids, ret, ps);
        }
    }

    private static void makeSendingMap(Collection<Integer> ids, Map<String, Map<Integer, PublishRecordEntity>> ret,
                                       IPublishStorage ps) {
        List<PublishRecordEntity> entities = ps.getPublishRecords(ids);
        for (PublishRecordEntity pe: entities) {
            Map<Integer, PublishRecordEntity> map = ret.computeIfAbsent(pe.getPubName(), v -> new HashMap<>());
            Integer dfId = pe.getDeliveryId();
            PublishRecordEntity prev = map.get(dfId);
            if (prev == null || isBeforeFirst(prev, pe)) {
                map.put(dfId, pe);
            } 
        }
        ids.clear();
    }

    private static boolean isBeforeFirst(PublishRecordEntity first, PublishRecordEntity second) {
        boolean ret = false;
        PublishRecordEntity updated = first;
        Date start1 = first.getPublishPacket().getStartSendingDate();
        Date start2 = second.getPublishPacket().getStartSendingDate();
        if (start1 == null || (start2 != null && start1.after(start2))) {
            updated = second;
            ret = true;
        }
        Date published1 = first.getHandledDate();
        Date published2 = second.getHandledDate();
        Date published = updated.getHandledDate();
        if (published == null) {
            if (published1 != null) {
                published = published2 != null && published2.before(published1) ? published2 : published1;

            } else if (published2 != null) {
                published = published2;
            }
        }
        updated.setHandledDate(published);
        return ret;
    }

    private static PublishRecordEntity getPublishEntity(PublishedAbstractEntity pa,
            Map<String, Map<Integer, PublishRecordEntity>> wrPubMap) {
        Map<Integer, PublishRecordEntity> map = wrPubMap.get(pa.getPubName());
        return map == null ? null : map.get(pa.getDeliveryId());
    }

    private static void updatePublishEventDates(Iterable<PublishedAbstractEntity> list, Map<Integer, PackageInfo> dfMap,
                                                Map<String, Pair<ArticleInfo, Map<String, ArticleInfo>>> articleMap,
                                                Map<String, Map<Integer, PublishRecordEntity>> mapWOL,
                                                Map<String, Map<Integer, PublishRecordEntity>> mapHW) {
        PublishDestination dest = PublishProfile.PUB_PROFILE.get().getDestination();
        PublishDestination wol = dest.isMandatoryPubType(PubType.TYPE_LITERATUM) ? PublishDestination.WOLLIT
                : PublishDestination.WOL;
        Date currentDate = new Date();
        for (PublishedAbstractEntity pa: list) {
            if (PublishedAbstractEntity.isCanceled(pa.getNotified()) || pa.sPD().off()) {
                continue;
            }
            Pair<ArticleInfo, Map<String, ArticleInfo>> metadata = articleMap.get(pa.getRecordName());
            if (metadata == null) {
                LOG.warn(String.format("cannot find basic PWR data for %s", pa));
                continue;
            }
            ArticleInfo ai = findArticleInfo(pa, metadata, dfMap);
            if (ai == null) {
                continue;
            }
            checkInitialFile(pa, ai, dfMap);
            if (pa.sPD().on() && ai.canBeSpdDate != null) {
                ai.spdDate = Date.from(OffsetDateTime.parse(
                        ai.canBeSpdDate, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant());
            }
            PublishRecordEntity sending = getPublishEntity(pa, mapWOL);
            int pubNotified = pa.getPubNotified();
            if (sending != null && wol.getWhenReadyTypeId() == sending.getPublishPacket().getPublishType()) {
                ai.sentWOL = sending.getPublishPacket().getSendingDate();
                if (pubNotified == dest.ordinal() || pubNotified == wol.ordinal()) {
                    ai.publishedWOL = sending.getDate();
                }
            }
            sending = getPublishEntity(pa, mapHW);
            if (sending != null
                && PublishDestination.SEMANTICO.getWhenReadyTypeId() == sending.getPublishPacket().getPublishType()) {
                ai.sentHW = sending.getPublishPacket().getSendingDate();
                if (pubNotified == PublishDestination.SEMANTICO.ordinal() || pubNotified == dest.ordinal()) {
                    ai.publishedHW = sending.getDate();
                    ai.archieNotified = sending.getHandledDate();
                }
            }
            checkNotified(ai, pa, dfMap);
            checkDelayed(ai, currentDate);
        }
    }

    private static void checkNotified(ArticleInfo ai, PublishedAbstractEntity pa, Map<Integer, PackageInfo> dfInfo) {
        if (PublishedAbstractEntity.isNotifiedOnPublished(pa.getNotified()) && ai.archieNotified == null) {
            ai.archieNotified = pa.getPublishedDate();
        }
        if (pa.getAcknowledgementId() != null) {
            ai.ackPack = dfInfo.get(pa.getAcknowledgementId());
        }
    }

    private static void checkDelayed(ArticleInfo ai, Date currentDate) {
        Date endDate = ai.archieNotified != null ? ai.archieNotified : currentDate;
        //long diff = endDate.getTime() - ai.getInitialStart().getTime();
        long diff = endDate.getTime() - (ai.spdDate != null ? ai.spdDate.getTime() : ai.getInitialStart().getTime());
        if (diff >= CochraneCMSPropertyNames.getAwaitingPublicationInterval().get().asInteger()) {
            ai.delayed = true;
        }
    }

    private static void checkInitialFile(PublishedAbstractEntity pa, ArticleInfo ai, Map<Integer, PackageInfo> dfInfo) {
        if (pa.getInitialDeliveryId() == null || pa.getDeliveryId().equals(pa.getInitialDeliveryId())) {
            return;
        }
        PackageInfo pi = dfInfo.get(pa.getInitialDeliveryId());
        if (pi == null) {
            DeliveryFileVO dfVo = ResultStorageFactory.getFactory().getInstance().getDeliveryFileVO(
                    pa.getInitialDeliveryId());
            if (dfVo != null) {
                pi = new PackageInfo(dfVo);
            }
        }
        if (pi != null) {
            ai.initialPack = pi;
        } else {
            LOG.warn(String.format("can't find a first initial package by [%d] for %s", pa.getInitialDeliveryId(), ai));
        }
    }

    private static ArticleInfo findArticleInfo(PublishedAbstractEntity pa, Pair<ArticleInfo,
                                               Map<String, ArticleInfo>> metadata, Map<Integer, PackageInfo> dfInfo) {
        ArticleInfo ret = null;
        if (pa.hasLanguage()) {
            for (Map.Entry<String, ArticleInfo> entry: metadata.second.entrySet()) {
                ret = findArticleInfo4WhenReady(pa, entry.getValue());
                if (ret != null) {
                    return ret;
                }
            }
        } else {
            ret = findArticleInfo4WhenReady(pa, metadata.first);
        }
        if (ret == null) {
            ret = addOtherArticleInfo(pa, metadata.first, dfInfo);
        }
        return ret;
    }

    private static ArticleInfo findArticleInfo4WhenReady(PublishedAbstractEntity pa, ArticleInfo ai) {
        if (ai.equalArticleInfo(pa)) {
            ai.setWhenReady(pa.getId(), pa.getPubNumber());
            return ai;
        }
        ArticleInfo ret = null;
        if (ai.others != null) {
            for (ArticleInfo old: ai.others) {
                ret = findArticleInfo4WhenReady(pa, old);
                if (ret != null) {
                    break;
                }
            }
        }
        return ret;
    }

    private static ArticleInfo addOtherArticleInfo(PublishedAbstractEntity pa, ArticleInfo parent,
                                                   Map<Integer, PackageInfo> dfInfo) {
        PackageInfo pack = dfInfo.get(pa.getDeliveryId());
        if (pack == null || !PublishedAbstractEntity.isNotifiedOnPublished(pa.getNotified())) {
            return null;
        }
        ArticleInfo ret = new ArticleInfo(parent.cdNumber, pack, pa.getLanguage());
        ret.setWhenReady(pa.getId(), pa.getPubNumber());
        ret.type = Constants.NA;
        parent.addOther(ret);
        return ret;
    }

    private static void updateUploadDates(Map<Integer, PackageInfo> dfMap) {
        IActivityLogService as = ActivityLogFactory.getFactory().getInstance();
        Collection<Integer> events = new ArrayList<>();
        events.add(ILogEvent.LOADING_COMPLETED);
        events.add(ILogEvent.PUBLISH_COMPLETED);
        events.add(ILogEvent.PUBLISH_SUCCESSFUL);
        events.add(ILogEvent.RECORDS_CREATED);
        int i = 0;
        List<ActivityLogEntity> list = as.getActivities(events, dfMap.keySet(), i, DbConstants.DB_QUERY_VARIABLE_COUNT);
        while (!list.isEmpty()) {
            for (ActivityLogEntity ae: list) {
                PackageInfo pi = dfMap.get(ae.getEntityId());
                if (pi == null) {
                    LOG.warn(String.format("unknown entityId: %d of activity[%d]" + ae.getEntityId(), ae.getId()));
                    continue;
                }
                int eventId = ae.getEvent().getId();
                if (eventId == ILogEvent.LOADING_COMPLETED && pi.end == null) {
                    pi.end = ae.getDate();

                } else if (eventId == ILogEvent.RECORDS_CREATED) {
                    updateCountIn(ae, pi);

                } else if (pi.pub == null) {
                    pi.pub = ae.getDate();
                }
            }
            i += DbConstants.DB_QUERY_VARIABLE_COUNT;
            list = as.getActivities(events, dfMap.keySet(), i, DbConstants.DB_QUERY_VARIABLE_COUNT);
        }
    }

    private static void updateCountIn(ActivityLogEntity ae, PackageInfo pi) {
        if (ae.getComments() != null) {
            String countRecs = ae.getComments().replace("records", "").trim();
            try {
                pi.countIn = Integer.valueOf(countRecs);
            } catch (NumberFormatException ne) {
                LOG.warn(ne.getMessage());
            }
        }
    }

    private static PackageInfo registerArticleInPackage(DeliveryFileEntity de, Map<Integer, PackageInfo> dfInfo) {
        return dfInfo.computeIfAbsent(de.getId(), f -> new PackageInfo(de));
    }

    private static class PackageInfo {
        final Integer id;
        final Date start;
        final String name;
        String sid;
        Date end;
        Date pub;
        int countIn;
        
        PackageInfo(DeliveryFileVO dvo) {
            id = dvo.getId();
            sid = id.toString();
            start = dvo.getDate();
            name = dvo.getName();
        }

        PackageInfo(DeliveryFileEntity de) {
            id = de.getId();
            sid = id.toString();
            start = de.getDate();
            name = de.getName();
        }

        @Override
        public String toString() {
            return String.format("%s [%d]", name, id);
        }
    }

    static final class ArticleInfo {
        int wrId = DbEntity.NOT_EXIST_ID;
        final String cdNumber;
        String status = Constants.NA;
        String type;
        String title = Constants.NA;
        int pub;
        String language;
        PackageInfo pack;
        PackageInfo initialPack;
        Date sentWOL;
        Date sentHW;
        Date publishedWOL;
        Date publishedHW;
        Date archieNotified;
        boolean old;
        Date spdDate;
        String canBeSpdDate;
        boolean delayed;
        List<ArticleInfo> others;
        boolean propsUpdated;
        PackageInfo ackPack;
        ArticleInfo reloaded;

        private ArticleInfo(String cdNumber, PackageInfo pack, String lang) {
            this.cdNumber = cdNumber;
            setPackage(pack);
            if (lang != null) {
                setTranslation(lang);
            }
        }

        private ArticleInfo(RecordEntity re, Boolean isP) {
            cdNumber = re.getName();
            type = getTypeString(isP);
            title = re.getUnitTitleNormalized();
        }

        Date getStart() {
            return pack.start;
        }

        void setPackage(PackageInfo pack) {
            this.pack = pack;
            initialPack = pack;
        }

        private Date getInitialStart() {
            return initialPack.start;
        }

        private boolean equalArticleInfo(PublishedAbstractEntity pa) {
            return !hasNoPackage() && !isNotExisted() && !MT.equals(status) && (pack.id.equals(pa.getDeliveryId())
                    || (pa.getDeliveryId() == null && pub == pa.getPubNumber() && wrId < pa.getId()))
                    && (pa.hasLanguage() ? pa.getLanguage().equals(language) : language == null);
        }

        private boolean updateProperties(RecordMetadataEntity rm, WRInfo.StatCounter counter, boolean isP,
                                         ArticleInfo[] prev) {
            if (propsUpdated) {
                prev[0] = this;
                return others != null && others.stream().anyMatch(f -> f.updateProperties(rm, counter, isP, prev));
            }
            int statusId = rm.getStatus();
            if (!counter.statuses.containsKey(statusId)) {
                statusId = RecordMetadataEntity.RevmanStatus.UPDATED.dbKey;
            }
            if (prev[0] == null) {
                counter.addCountByStatus(statusId, 1);
            } else if (prev[0].pub > rm.getPubNumber()) {
                counter.addHistoryCount(1);
                prev[0] = this;
            }
            status = RecordMetadataEntity.RevmanStatus.getStatusName(statusId);
            updateProperties(rm.getTitle(), rm.getPubNumber(), getTypeString(isP), rm.getPublishedOnlineCitation());
            propsUpdated = true;
            return true;
        }

        private void updateProperties(String title, int pub, String type, String spdDate) {
            updateProperties(title, type);
            this.pub = pub;
            this.canBeSpdDate = spdDate;
        }

        private void updateProperties(String title, Boolean isP) {
            updateProperties(title, getTypeString(isP));
        }

        private void updateProperties(String title, String type) {
            this.title = title;
            this.type = type;
        }

        private void addOthers(Iterable<ArticleInfo> others) {
            if (others != null) {
                others.forEach(this::addOther);
            }
        }

        private void addOther(ArticleInfo ai) {
            if (others == null) {
                others = new ArrayList<>();
            }
            others.add(ai);
            ai.old = true;
            LOG.info(String.format("%s [%d] was added to previous PWR records", ai, ai.wrId));
        }

        private void moveToOthers(ArticleInfo moved) {
            addOthers(moved.others);
            moved.others = null;
            addOther(moved);
        }

        private boolean hasNoPub() {
            return pub == 0;
        }

        boolean hasNoPackage() {
            return pack == null;
        }

        private boolean isNotExisted() {
            return hasNoPub() && hasNoPackage();
        }

        private void setWhenReady(int wrId, int pub) {
            this.wrId = wrId;
            this.pub = pub;
        }

        private void setTranslation(String language) {
            this.language = language;
            status = TA;
        }

        boolean isTranslation() {
            return language != null;
        }

        private static String getTypeString(Boolean isP) {
            return isP == null ? Constants.NA : (isP ? "P" : "R");
        }

        @Override
        public String toString() {
            return RevmanMetadataHelper.buildPubName(cdNumber, pub) + (isTranslation() ? ("." + language) : "");
        }
    }
}
