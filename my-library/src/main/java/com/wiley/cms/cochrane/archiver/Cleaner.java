package com.wiley.cms.cochrane.archiver;

import java.io.File;
import java.io.FileInputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.commons.io.FileUtils;

import com.wiley.cms.cochrane.cmanager.CachedPath;
import com.wiley.cms.cochrane.cmanager.CmsUtils;
import com.wiley.cms.cochrane.cmanager.CochraneCMSProperties;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.FilePathBuilder;
import com.wiley.cms.cochrane.cmanager.contentworker.WorkerFactory;
import com.wiley.cms.cochrane.cmanager.data.ClDbEntity;
import com.wiley.cms.cochrane.cmanager.data.IResultsStorage;
import com.wiley.cms.cochrane.cmanager.data.IssueEntity;
import com.wiley.cms.cochrane.cmanager.data.issue.IssueStorageFactory;
import com.wiley.cms.cochrane.cmanager.entitywrapper.ResultStorageFactory;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.cmanager.res.PathType;
import com.wiley.cms.cochrane.cmanager.res.PubType;
import com.wiley.cms.cochrane.repository.IRepository;
import com.wiley.cms.cochrane.repository.RepositoryFactory;
import com.wiley.cms.cochrane.utils.Constants;
import com.wiley.cms.process.IModelController;
import com.wiley.cms.process.task.IScheduledTask;
import com.wiley.cms.process.task.ITaskExecutor;
import com.wiley.cms.process.task.TaskVO;
import com.wiley.cms.cochrane.test.PackageChecker;
import com.wiley.tes.util.Extensions;
import com.wiley.tes.util.InputUtils;
import com.wiley.tes.util.Logger;
import com.wiley.tes.util.Now;
import com.wiley.tes.util.res.Property;
import com.wiley.tes.util.res.Res;


/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 5/6/2019
 */
public class Cleaner implements ITaskExecutor, IScheduledTask {
    private static final Logger LOG = Logger.getLogger(Cleaner.class);

    private static final Res<Property> CLEANER_START = Property.get("cochrane.clean.start-pattern");
    private static final Res<Property> ISSUES_TO_SKIP = Property.get("cochrane.clean.issues-to-skip", ""
            + Constants.LAST_ACTUAL_MONTH_AMOUNT);
    private static final Res<Property> EXPIRATION = Property.get("cochrane.clean.expiration", ""
            + Constants.DEFAULT_EXPIRATION_LIMIT);
    private static final Res<Property> DRY_RUN = Property.get("cochrane.clean.dry-run", "true");
    private static final Res<Property> DEBUG = Property.get("cochrane.clean.debug", "false");

    private static final ReentrantLock CLEANER_LOCK = new ReentrantLock();

    private final IResultsStorage rs = ResultStorageFactory.getFactory().getInstance();
    private final IRepository rp = RepositoryFactory.getRepository();

    @Override
    public String getScheduledTemplate() {
        return CLEANER_START.get().getValue();
    }

    @Override
    public boolean execute(TaskVO task) throws Exception {
        Stats stats = null;
        try {
            stats = new Stats();
            cleanOldData(stats);

        } catch (Throwable tr) {
            if (stats != null) {
                stats.clear();
            }
            LOG.error(tr);
        }
        updateSchedule(task);
        return true;
    }

    public void cleanOldData(Stats stats) throws Throwable {
        if (CLEANER_LOCK.tryLock()) {
            try {
                LOG.warn(String.format(
                    "cleaning is starting with an expiration months limit: %d, an expiration date: %s",
                        stats.getExpirationMonths(), stats.getExpirationDate()));
                List<IssueEntity> issuesToCheck = checkOldIssues(stats);
                stats.publishCleaner.cleanOldPublishEntities();
                for (IssueEntity ie: issuesToCheck) {
                    cleanIssue(ie, stats);
                }
                cleanEntireFolders(stats);
                cleanOtherFolders(stats);
                cleanDbTables(stats);

            } finally {
                CLEANER_LOCK.unlock();
            }
        } else {
            LOG.warn("cleaning is currently performing within another thread ...");
        }
    }

    private List<IssueEntity> checkOldIssues(Stats stats) {
        List<IssueEntity> list = rs.getIssueList();
        int actualIssueCount = stats.getLastIssuesLimit();
        if (actualIssueCount < 1) {
            // set default value
            actualIssueCount = Constants.LAST_ACTUAL_MONTH_AMOUNT;
            LOG.warn(String.format("no actual Issues amount are set, so a default value: %d is taken ...",
                    actualIssueCount));
        }
        if (list.size() <= actualIssueCount) {
            LOG.info(String.format("no Issues for cleaning found with actual Issues amount: %d", actualIssueCount));
            return Collections.emptyList();
        }
        LOG.debug(String.format("all Issues are going to be cleaned except the latest actual %d...", actualIssueCount));

        while (actualIssueCount > 0) {
            rs.getDbList(list.remove(--actualIssueCount).getId()).forEach(stats::saveDb);
        }
        return list;
    }

    private void cleanOtherFolders(Stats stats) throws Exception {
        File[] rootFiles = rp.getFilesFromDir(CochraneCMSProperties.getProperty(
                CochraneCMSPropertyNames.PREFIX_REPOSITORY, ""));
        if (rootFiles != null) {
            FLStats flStats = stats.createFLStats("temp folder", stats.isAllLog());
            for (File fl : rootFiles) {
                if (!fl.isDirectory()) {
                    continue;
                }
                String fileName = fl.getName();
                if ("temp".equals(fileName)) {
                    cleanFolder(fl, flStats, true);
                }
            }
            stats.upFLStats(flStats);
        }
        File render = getRootRenderingRepository();
        if (render.exists()) {
            FLStats flStats = stats.createFLStats("render", stats.isAllLog());
            cleanFolder(render, flStats, true);
            stats.upFLStats(flStats);
        }
    }

    private void cleanOtherFolders(File[] folders, FLStats stats) {
        if (folders == null || folders.length == 0) {
            return;
        }
        for (File fl: folders) {
            cleanFolder(fl, stats, true);
        }
    }

    private void cleanIssue(IssueEntity issue, Stats stats) {
        Integer issueId = issue.getId();
        if (CmsUtils.isSpecialIssue(issueId)) {
            return;
        }
        LOG.info(String.format("Issue [%d] is checking to clean", issueId));
        List<ClDbEntity> list = rs.getDbList(issueId);
        for (ClDbEntity clDb: list) {
            cleanDb(issueId, clDb.getTitle(), stats);
        }
    }

    private File getRootRenderingRepository() throws URISyntaxException {
        String rndRepository = CochraneCMSPropertyNames.getRenderingRepository();
        return new File(new URI(rndRepository));
    }

    private void cleanDb(Integer issueId, String dbName, Stats stats) {

        String issueDbPath = FilePathBuilder.getPathToIssueDb(issueId, dbName);

        FLStats flStats = stats.createFLStats(String.format("%s[%d]", dbName, issueId),
                stats.issueExportExpirationTime, true);
        cleanFolder(FilePathBuilder.getPathToIssueExport(issueId, dbName), flStats, false);

        flStats.setExpiration(stats.issueCopyExpirationTime).setLog(stats.isAllLog());
        //cleanFolder(FilePathBuilder.getPathToBackup(issueId, dbName), flStats, true);

        flStats.setExpiration(stats.getExpiration()).setLog(stats.isAllLog());
        cleanFolder(issueDbPath + "xmlurls", flStats, true);

        flStats.setLog(true);
        File[] files = new File(rp.getRealFilePath(issueDbPath)).listFiles(File::isFile);
        if (files != null && files.length > 0) {
            for (File fl: files) {
                String fileName = fl.getName();
                if (fileName.endsWith(Extensions.ZIP) && (fileName.contains(PackageChecker.AUT_SUFFIX))
                        || WorkerFactory.matches(fileName)) {
                    flStats.deleteFile(fl);
                }
            }
        }
        stats.upFLStats(flStats);

        cleanPublishFolders(issueId, dbName, flStats, stats);
    }

    private void cleanEntireFolders(Stats stats) {
        FLStats flStats = new FLStats("entire", stats.isDryRun()) {
                @Override
                boolean isNotExpired(File fl) {
                    return super.isNotExpired(fl) || stats.publishCleaner.isPublishNameSaved(fl.getName());
                }
            }.setExpiration(stats.getExpiration()).setLog(true);

        Collection<Res<BaseType>> list = BaseType.getAll();
        for (Res<BaseType> bt: list) {
            String dbName = bt.get().getId();
            cleanPublishFolders(FilePathBuilder.getPathToEntirePublish(dbName), flStats,
                    stats.entirePublishExpirationTime);
            flStats.setExpiration(stats.entireExportExpirationTime);
            cleanFolder(FilePathBuilder.getPathToEntireExport(dbName), flStats, false);
        }
        stats.upFLStats(flStats);
    }

    private void cleanPublishFolders(Integer issueId, String dbName, FLStats flStats, Stats stats) {
        //String publishPath = FilePathBuilder.getPathToIssuePublish(issueId, dbName);
        String publishPathWR = FilePathBuilder.getPathToIssuePublishWhenReady(issueId, dbName);
        //cleanPublishFolders(publishPath, flStats, stats.issuePublishExpirationTime);
        cleanPublishFolders(publishPathWR, flStats, stats.issuePublishWRExpirationTime);
        stats.upFLStats(flStats);
    }

    private void cleanPublishFolders(String publishPath, FLStats flStats, long defaultExpirationTime) {
        File[] files = rp.getFilesFromDir(publishPath);
        if (files != null) {
            for (File fl : files) {
                if (fl.isDirectory()) {
                    cleanPublishFolder(fl, flStats, defaultExpirationTime);
                }
            }
        }
    }

    private void cleanPublishFolder(File folder, FLStats flStats, long defaultExpirationTime) {
        String folderName = folder.getName();
        if (Constants.WHEN_READY.equals(folderName)) {
            return;
        }
        Res<PubType> pt = PubType.find(folderName);
        int expiration = !Res.valid(pt) ? Constants.NO_EXPIRATION_LIMIT : pt.get().getExpiration();
        flStats.setExpiration(expiration <= Constants.NO_EXPIRATION_LIMIT ? defaultExpirationTime
                : Now.getNowMinusMonths(expiration));
        cleanFolder(folder, flStats, false);
    }

    private void cleanFolder(String folderPath, FLStats flStats, boolean removeSubFolders) {
        try {
            if (flStats.expirationTime == 0 || !rp.isFileExists(folderPath)) {
                return;
            }
            String path = rp.getRealFilePath(folderPath);
            File folder = new File(path);
            cleanFolder(folder, flStats, removeSubFolders);

        } catch (Exception e) {
            LOG.error(e);
        }
    }

    private void cleanFolder(File folder, FLStats flStats, boolean removeSubFolders) {
        try {
            Files.walk(folder.toPath()).forEach(flStats::cleanFiles);
            if (removeSubFolders) {
                flStats.cleanFolders(folder);
            }
        } catch (Exception e) {
            LOG.error(e);
        }
    }

    static void updateDB(String[] queries, Map<String, String> parameters, boolean dryRun, IModelController mc) {
        if (dryRun) {
            int size = queries.length;
            for (int i = 0; i < size; i++) {
                String sql = queries[i];
                queries[i] = sql.replace("DELETE", "SELECT");
            }
        }
        Archiver.updateDB(queries, parameters, mc);
    }

    private void cleanDbTables(Stats stats) {
        try {
            String[] cleanQueries = initCleanQueries();
            Map<String, String> params = new HashMap<>();
            StringBuilder sb = new StringBuilder();
            Iterator<Integer> it = stats.savedDbIds.iterator();
            sb.append(it.next());
            while (it.hasNext()) {
                Integer id = it.next();
                sb.append(",").append(id);
            }
            params.put("<db_ids>", sb.toString());
            params.put("<expired_date>", Now.DATE_FORMATTER.format(
                stats.getExpirationDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate()));
            //params.put("<export_issue_date>", Now.DATE_FORMAT.b)
            //params.put("<export_entire_date>", )
            updateDB(cleanQueries, params, stats.isDryRun(), IssueStorageFactory.getFactory().getInstance());

        } catch (Exception e) {
            LOG.warn(e.getMessage());
        }
    }

    private String[] initCleanQueries() throws Exception {
        return InputUtils.readStreamToString(new FileInputStream(new File(new URI(
            CochraneCMSPropertyNames.getCochraneResourcesRoot() + "archive/cochrane_clean.sql")))).split(";");
    }

    /**
     *
     */
    public static class Stats {

        private final Date expirationDate;

        private final long issueExportExpirationTime = calculateExpiration(CachedPath.ISSUE_EXPORT.getExpired());
        private final long issueCopyExpirationTime = calculateExpiration(CachedPath.ISSUE_COPY.getExpired());
        private final long issuePublishExpirationTime = calculateExpiration(CachedPath.ISSUE_PUBLISH.getExpired());
        private final long issuePublishWRExpirationTime =
                calculateExpiration(CachedPath.ISSUE_PUBLISH_WHENREADY.getExpired());
        private final long entirePublishExpirationTime = calculateExpiration(CachedPath.ENTIRE_PUBLISH.getExpired());
        private final long entireExportExpirationTime = calculateExpiration(CachedPath.ENTIRE_EXPORT.getExpired());

        private final Set<Integer> savedDbIds = new HashSet<>();

        private final PublishCleaner publishCleaner;

        private final FLStats flStats;

        public Stats() throws Exception {
            int expirationMonths = getExpirationMonths();
            expirationDate = new Date(calculateExpiration(expirationMonths));
            flStats = new FLStats("ALL", DRY_RUN.get().asBoolean()).setExpiration(
                    calculateExpiration(expirationMonths)).setLog(DEBUG.get().asBoolean());
            publishCleaner = new PublishCleaner(this);
        }

        public int getLastIssuesLimit() {
            return ISSUES_TO_SKIP.get().asInteger();
        }

        public int getExpirationMonths() {
            return EXPIRATION.get().asInteger();
        }

        final boolean isDryRun() {
            return flStats.dryRun;
        }

        final boolean isAllLog() {
            return flStats.totalLog;
        }

        final long getExpiration() {
            return flStats.expirationTime;
        }

        final Date getExpirationDate() {
            return expirationDate;
        }

        final void saveDb(ClDbEntity clDb) {
            savedDbIds.add(clDb.getId());
        }

        final boolean isDbSaved(Integer dbId) {
            return savedDbIds.contains(dbId);
        }

        public boolean isPublishEntityExcluded(int id) {
            return false;
        }

        public boolean isPublishRecordEntityExcluded(int id) {
            return false;
        }

        public void clear() {
            savedDbIds.clear();
            publishCleaner.clear();
            PathType.freshWeakPaths();
        }

        FLStats createFLStats(String name, boolean totalLog) {
            return createFLStats(name, getExpiration(), totalLog);
        }

        FLStats createFLStats(String name, long expirationTime, boolean totalLog) {
            return new FLStats(name, isDryRun()).setExpiration(expirationTime).setLog(totalLog);
        }

        void upFLStats(FLStats addStats) {
            if (addStats.isEmpty()) {
                return;
            }
            flStats.add(addStats);
            LOG.info(addStats);
            addStats.reset();
            LOG.info(flStats);
        }

        private int validateExpiration(int expirationMonths) {
            // try to override with a base expiration
            int ret = expirationMonths <= Constants.NO_EXPIRATION_LIMIT ? getExpirationMonths() : expirationMonths;
            if (ret <= 0) {
                ret = 1;
                LOG.warn("no limit months for cleaning are set, so 1 month will be taken ...");
            }
            return ret;
        }

        private long calculateExpiration(int expirationMonths) {
            return Now.getNowMinusMonths(validateExpiration(expirationMonths));
        }

        @Override
        public String toString() {
            return flStats.toString();
        }
    }

    /**
     *
     */
    private static class FLStats {

        private final String name;
        private long expirationTime;
        private boolean totalLog;

        private long allLength;
        private long fileCount;
        private long dirCount;

        private final boolean dryRun;

        FLStats(String name, boolean dryRun) {
            this.name = name;
            this.dryRun = dryRun;
            reset();
        }

        FLStats setExpiration(long expirationTime) {
            this.expirationTime = expirationTime;
            return this;
        }

        FLStats setLog(boolean totalLog) {
            this.totalLog = totalLog;
            return this;
        }

        void cleanFolders(File fl) {
            cleanFolders(fl.listFiles());
        }

        void cleanFiles(Path path) {
            File fl = path.toFile();
            if (!fl.isFile()) {
                return;
            }
            deleteFile(fl);
        }

        private boolean cleanFolders(File[] files) {
            if (files == null || files.length == 0) {
                return true;
            }
            boolean ret = true;
            for (File fl : files) {
                if (!fl.isDirectory() || !cleanFolders(fl.listFiles()) || !deleteFolder(fl)) {
                    ret = false;
                }
            }
            return ret;
        }

        private boolean deleteFolder(File folder) {
            if (folder.exists()) {
                if (!dryRun && !folder.delete()) {
                    return false;
                }
                dirCount++;
                log(folder);
            }
            return true;
        }

        boolean isNotExpired(File fl) {
            return fl.lastModified() > expirationTime;
        }

        void deleteFile(File fl) {
            if (!fl.isFile() || isNotExpired(fl)) {
                return;
            }
            long length = fl.length();
            if (!dryRun && !fl.delete()) {
                return;
            }
            fileCount++;
            allLength += length;
            log(fl);
        }

        void add(FLStats stats) {
            fileCount += stats.fileCount;
            dirCount += stats.dirCount;
            allLength += stats.allLength;
        }

        void reset() {
            allLength = 0;
            fileCount = 0;
            dirCount = 0;
        }

        boolean isEmpty() {
            return allLength == 0 && fileCount == 0 && dirCount == 0;
        }

        private void log(File fl) {
            if (totalLog || fl.length() > Constants.MB) {
                LOG.debug(String.format("[%s] %s", !dryRun, fl.getAbsolutePath()));
            }
        }

        @Override
        public String toString() {
            return String.format("[%s] %s: %s in %d files / %d folders", !dryRun, name,
                    FileUtils.byteCountToDisplaySize(allLength), fileCount, dirCount);
        }
    }
}
