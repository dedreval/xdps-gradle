package com.wiley.cms.cochrane.archiver;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.zip.GZIPInputStream;

import org.apache.tools.tar.TarEntry;
import org.apache.tools.tar.TarInputStream;

import com.wiley.cms.cochrane.activitylog.ActivityLogEntity;
import com.wiley.cms.cochrane.activitylog.IActivityLogService;
import com.wiley.cms.cochrane.activitylog.ILogEvent;
import com.wiley.cms.cochrane.cmanager.CmsException;
import com.wiley.cms.cochrane.cmanager.CmsUtils;
import com.wiley.cms.cochrane.cmanager.CochraneCMSProperties;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.FilePathBuilder;
import com.wiley.cms.cochrane.cmanager.FilePathCreator;
import com.wiley.cms.cochrane.cmanager.data.ClDbEntity;
import com.wiley.cms.cochrane.cmanager.data.DatabaseEntity;
import com.wiley.cms.cochrane.cmanager.data.IResultsStorage;
import com.wiley.cms.cochrane.cmanager.data.IssueEntity;
import com.wiley.cms.cochrane.cmanager.data.db.DbStorageFactory;
import com.wiley.cms.cochrane.cmanager.data.db.IDbStorage;
import com.wiley.cms.cochrane.cmanager.data.issue.IIssueStorage;
import com.wiley.cms.cochrane.cmanager.data.issue.IssueStorageFactory;
import com.wiley.cms.cochrane.cmanager.entitymanager.AbstractManager;
import com.wiley.cms.cochrane.cmanager.entitywrapper.DbWrapper;
import com.wiley.cms.cochrane.cmanager.entitywrapper.ResultStorageFactory;
import com.wiley.cms.cochrane.repository.IRepository;
import com.wiley.cms.cochrane.repository.RepositoryFactory;
import com.wiley.cms.cochrane.repository.RepositoryUtils;
import com.wiley.cms.cochrane.utils.zip.GZipOutput;
import com.wiley.cms.process.IModelController;
import com.wiley.cms.process.ProcessException;
import com.wiley.cms.process.entity.DbEntity;
import com.wiley.cms.process.task.ITaskExecutor;
import com.wiley.cms.process.task.IScheduledTask;
import com.wiley.cms.process.task.TaskVO;
import com.wiley.tes.util.Extensions;
import com.wiley.tes.util.InputUtils;
import com.wiley.tes.util.Logger;
import com.wiley.tes.util.Now;
import com.wiley.tes.util.res.Property;
import com.wiley.tes.util.res.Res;

/**
 * @author <a href='mailto:gkhristich@wiley.ru'>Galina Khristich</a>
 *         Date: 18-Nov-2008
 */
public class Archiver implements ITaskExecutor, IScheduledTask, Runnable {
    public static final String TASK_NAME = "archiving";
    private static final Logger LOG = Logger.getLogger(Archiver.class);

    private static final Res<Property> ARCHIVING_START = Property.get("cochrane.archive.start-pattern");
    private static final Res<Property> ARCHIVING_INTERVAL = Property.get("cochrane.archive.interval");
                            
    private static final int LAST_LEGACY_ARCHIVED_ISSUE = 201407;

    private final String repository = CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.PREFIX_REPOSITORY);
    private final IRepository rp = RepositoryFactory.getRepository();
    private GZipOutput archive;
    private final IIssueStorage issueStorage = IssueStorageFactory.getFactory().getInstance();
    private final IDbStorage dbStorage = DbStorageFactory.getFactory().getInstance();
    private int issueId = DbEntity.NOT_EXIST_ID;

    public Archiver() {
    }

    public Archiver(IssueEntity issue) {
        issueId = issue.getId();
    }

    public static Res<Property> getArchivingStartPattern() {
        return ARCHIVING_START;
    }

    public static Res<Property> getArchivingInterval() {
        return ARCHIVING_INTERVAL;
    }

    public boolean execute(TaskVO task) throws Exception {

        if (!ARCHIVING_START.exist() || !ARCHIVING_INTERVAL.exist()) {
            throw new ProcessException("the automatic archiving properties aren't set");
        }
        Integer interval = ARCHIVING_INTERVAL.get().asInteger();
        if (interval == null || interval < 0) {
            throw new ProcessException("the automatic archiving interval is not correct: " + ARCHIVING_INTERVAL.get());
        }

        IssueEntity ie = null;

        if (interval != 0) {

            Calendar cl = Now.getNowUTC();
            int year = cl.get(Calendar.YEAR);
            int month = Now.getCalendarMonth(cl);
            int diffYear = year - interval / Now.DECEMBER;
            int diffMonth = interval % Now.DECEMBER;
            if (diffMonth >= month) {
                diffYear--;
                month += Now.DECEMBER;
            }
            diffMonth = month - diffMonth;

            IResultsStorage rs = ResultStorageFactory.getFactory().getInstance();
            try {
                ie = rs.findOpenIssueEntity(diffYear, diffMonth);
            } catch (CmsException ce) {
                ie = null;
                LOG.info(String.format("has no issues for archiving found (%d year %d month)", diffYear, diffMonth));
            }
        } else {
            LOG.debug("archiving interval is 0, nothing to archive");
        }

        if (ie != null && !CmsUtils.isSpecialIssue(ie.getId())) {
            issueId = ie.getId();
            ie = IssueStorageFactory.getFactory().getInstance().setIssueArchiving(issueId, true, false);
            if (ie != null && !startArchiving(ie)) {
                return false;
            }
        }
        
        // set next archiving time
        updateSchedule(task);
        return true;
    }

    @Override
    public String getScheduledTemplate() {
        return ARCHIVING_START.get().getValue();
    }

    public void run() {
        startArchiving(IssueStorageFactory.getFactory().getInstance().setIssueArchiving(issueId, true, false));
    }

    public static boolean isUnArchivingFullySupported(IssueEntity issue) {
        return issue != null && issue.getFullNumber() > LAST_LEGACY_ARCHIVED_ISSUE;
    }

    public void startUnArchiving() {
        IssueEntity issue = IssueStorageFactory.getFactory().getInstance().setIssueArchiving(issueId, true, true);
        if (issue == null) {
            LOG.warn(String.format("Issue [%d] cannot be unarchived right now", issueId));
            return;
        }
        String archivePath = FilePathBuilder.getPathToIssueArchive(issue.getId());
        boolean fullSupported = isUnArchivingFullySupported(issue);
        if (!fullSupported) {
            LOG.warn(String.format("un archiving is not fully supported for Issue [%d], the data will be unpacked under"
                + " the Issue directory '%s', but its content will be accessible only from repository file system and"
                    + " will not be displayed in UI", issueId, archivePath));
        }
        LOG.debug(String.format("un archiving is started for Issue [%d]", issueId));

        boolean wasUnarchived = false;
        List<ClDbEntity> dbs = dbStorage.getDbByIssue(issue);
        for (ClDbEntity db : dbs) {
            if (db.getDatabase().getId() == DatabaseEntity.CDSR_TA_KEY) {
                continue;
            }
            String archiveFileName = buildArchiveFileName(db.getTitle(), issue);
            String archiveFilePath = archivePath + archiveFileName;
            File archiveFile = new File(rp.getRealFilePath(archiveFilePath));
            if (!archiveFile.exists()) {
                LOG.warn(String.format("Issue [%d] - an archive %s does not exist and cannot be unarchived",
                        issueId, archiveFilePath));
                continue;
            }
            try {
                LOG.debug(String.format("un archiving is started for Issue [%d], %s", issue.getId(), db.getTitle()));
                String issuePath = FilePathBuilder.getPathToIssue(issueId);

                unpackTar(issueId, archiveFile.getAbsolutePath(), issuePath);

                if (!fullSupported) {
                    wasUnarchived = true;

                } else if (dbStorage.unArchiveIssueDb(db.getId())) {
                    wasUnarchived = true;
                }
                LOG.debug(String.format("un archiving is completed for Issue [%d], %s", issue.getId(), db.getTitle()));
            } catch (Exception e) {
                LOG.error(e.getMessage());
            }
        }

        issueStorage.setIssueArchiving(issue.getId(), false, true);
        if (wasUnarchived && fullSupported) {
            issueStorage.setIssueArchived(issue, false);
        }

        LOG.debug(String.format("un archiving is completed for Issue [%d] ", issue.getId()));
    }

    private boolean startArchiving(IssueEntity issue) {
        if (issue == null) {
            LOG.warn(String.format("Issue [%d] is already archived", issueId));
            return true;
        }

        boolean ret = true;
        LOG.debug(String.format("archiving is started for Issue [%d]", issue.getId()));

        String theLogUser = CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.ACTIVITY_LOG_SYSTEM_NAME);
        IActivityLogService logService = AbstractManager.getActivityLogService();
        //String archivePath = repository + "/archive";
        String archivePath = FilePathBuilder.getPathToIssueArchive(issue.getId());
        try {
            //backupIssueDB(issue);
            Set<String> ignoredDirs = getTemporaryDirs();

            List<ClDbEntity> dbs = dbStorage.getDbByIssue(issue);
            for (ClDbEntity db : dbs) {

                if (db.getAllCount() != null && db.getAllCount() != 0
                        && db.getDatabase().getId() != DatabaseEntity.CDSR_TA_KEY) {

                    //archive = new GZipOutput(archivePath + "/" + db.getIssue().getId() + "/"
                    //        + buildArchiveFileName(db.getTitle(), issue), true, true);

                    String archivedFilePath = archivePath + buildArchiveFileName(db.getTitle(), issue);
                    if (rp.isFileExistsQuiet(archivedFilePath)) {
                        throw new Exception(String.format(
                            "archive %s already exist in repository, need to remove or rename it before new archiving",
                                archivedFilePath));
                    }

                    LOG.debug(String.format("archiving is started for Issue [%d], %s", issue.getId(), db.getTitle()));
                    dbStorage.setDbArchiving(db, true);
                    archive = new GZipOutput(archivePath + buildArchiveFileName(db.getTitle(), issue), true, true);

                    //String dbDirPath = "/" + repository + "/" + db.getIssue().getId() + "/" + db.getTitle() + "/";
                    String dbDirPath = FilePathBuilder.getPathToIssueDb(issue.getId(), db.getTitle());
                    writeDir(dbDirPath, ignoredDirs);

                    archive.close();
                    dbStorage.setDbArchiving(db, false);
                    LOG.debug(String.format("archiving is finished for Issue [%d], %s", issue.getId(), db.getTitle()));
                }

                new DbWrapper(db).archiveIssueDb();
                dbStorage.setDbArchived(db, true);
            }
            issueStorage.setIssueArchiving(issue.getId(), false, false);
            issueStorage.setIssueArchived(issue, true);
            logService.info(ActivityLogEntity.EntityLevel.ISSUE, ILogEvent.ARCHIVED, issue.getId(), issue.getTitle(),
                    theLogUser, "issue archived");
        } catch (Exception e) {
            LOG.error(e, e);
            issueStorage.setIssueArchiving(issue.getId(), false, false);
            logService.info(ActivityLogEntity.EntityLevel.ISSUE, ILogEvent.ARCHIVED_FAILED, issue.getId(),
                    issue.getTitle(), theLogUser, e.getMessage());
            ret = false;
        }
        LOG.debug(String.format("archiving is finished for Issue [%d]", issue.getId()));
        return ret;
    }

    private static String buildArchiveFileName(String dbName, IssueEntity issue) {
        return "archive_" + dbName + "_" + issue.getYear() + "_" + issue.getNumber() + Extensions.TAR_GZ;
    }

    private void unpackTar(Integer issueId, String archivePath, String destinationPath) throws Exception {
        try (TarInputStream tar = new TarInputStream(new GZIPInputStream(rp.getFile(archivePath)))) {
            TarEntry entry;
            String rootPath = rp.getRealFilePath(repository) + FilePathCreator.SEPARATOR;
            while ((entry = tar.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    String name = entry.getName();
                    String fileName = RepositoryUtils.getLastNameByPath(name);
                    String dirName = rootPath + RepositoryUtils.getFolderPathByPath(name, false);
                    new File(dirName).mkdirs();
                    InputUtils.writeFile(dirName, fileName, tar, false);
                }
            }
        }
    }

    @Deprecated
    private void backupIssueDB(IssueEntity issue) throws IOException, URISyntaxException {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("<issue num>", "" + issue.getFullNumber());
        parameters.put("<issue id>", "" + issue.getId());

        String[] queries = InputUtils.readStreamToString(new FileInputStream(new File(new URI(
            CochraneCMSPropertyNames.getCochraneResourcesRoot() + "archive/cochrane_history.sql")))).split(";");

        updateDB(queries, parameters, this.issueStorage);
    }

    static void updateDB(String[] queries, Map<String, String> parameters, IModelController mc) {
        String table = "";

        for (String sql : queries) {

            sql = sql.trim();
            if (sql.startsWith("--")) {
                if (sql.startsWith("--!")) {
                    table = sql;
                }
                continue;
            }

            if (sql.length() == 0) {
                continue;
            }

            for (Map.Entry<String, String> entry: parameters.entrySet()) {
                sql = sql.replaceAll(entry.getKey(), entry.getValue());
            }

            if (sql.startsWith("SELECT")) {
                List results = mc.getNativeQueryResults(sql);
                if (results.size() > 0) {
                    LOG.info(String.format("%s: %d rows are selected.", table, results.size()));
                }
                continue;
            }

            int count = mc.executeNativeQuery(sql);
            if (count > 0) {
                if (sql.startsWith("INSERT")) {
                    LOG.info(String.format("%s: %d rows are inserted.", table, count));
                } else if (sql.startsWith("DELETE")) {
                    LOG.info(String.format("%s: %d rows are deleted.", table, count));
                }
            }
        }
    }

    private static Set<String> getTemporaryDirs() {
        Set<String> ret = new HashSet<>();
        Res<Property> p = Property.get("cochrane.archive.ignored-folders");
        if (!Res.valid(p)) {
            return ret;
        }

        String[] ignoredDirs = p.get().getValues();
        if (ignoredDirs != null) {
            for (String s: ignoredDirs) {
                ret.add(s.trim());
            }
        }
        return ret;
    }

    private void writeFile(File fl) throws IOException {
        String path = fl.getAbsolutePath();
        InputStream is = new FileInputStream(fl);
        archive.put(path.replace(path.substring(0, path.indexOf(repository) + repository.length() + 1), ""), is);
        is.close();
    }

    private void writeDir(String path, Set<String> ignoredDirs) throws IOException {
        Stack<String> dirStack = new Stack<String>();
        dirStack.push(path);

        while (!dirStack.empty()) {
            String tmpDir = dirStack.pop();
            final File[] files = rp.getFilesFromDir(tmpDir);
            if (files == null) {
                continue;
            }

            for (final File file : files) {
                if (file.isDirectory()) {
                    if (ignoredDirs.contains(file.getName())) {
                        continue;
                    }
                    dirStack.push(file.getAbsolutePath());
                } else {
                    writeFile(file);
                }
            }
        }
    }
}
