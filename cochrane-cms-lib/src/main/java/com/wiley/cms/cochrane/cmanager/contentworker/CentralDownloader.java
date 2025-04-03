package com.wiley.cms.cochrane.cmanager.contentworker;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.Date;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.ejb.EJB;
import javax.ejb.Local;
import javax.ejb.Lock;
import javax.ejb.LockType;
import javax.ejb.Singleton;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;

import org.quartz.StatefulJob;

import com.wiley.cms.cochrane.cmanager.CmsException;
import com.wiley.cms.cochrane.cmanager.CmsUtils;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.MessageSender;
import com.wiley.cms.cochrane.cmanager.central.IPackageDownloader;
import com.wiley.cms.cochrane.cmanager.data.ClDbEntity;
import com.wiley.cms.cochrane.cmanager.data.IResultsStorage;
import com.wiley.cms.cochrane.cmanager.data.IssueEntity;
import com.wiley.cms.cochrane.process.IRecordCache;
import com.wiley.cms.process.ProcessHelper;
import com.wiley.cms.process.task.BaseDownloader;
import com.wiley.cms.process.task.IDownloader;
import com.wiley.tes.util.Logger;
import com.wiley.tes.util.Now;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 12/3/2021
 */
@Singleton
@Local(IDownloader.class)
@Lock(LockType.READ)
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class CentralDownloader extends BaseDownloader implements StatefulJob {

    private static final Logger LOG = Logger.getLogger(CentralDownloader.class);

    private static boolean downloaded;

    @EJB(beanName = "ResultsStorage")
    private IResultsStorage rs;

    @EJB(beanName = "PackageDownloader")
    private IPackageDownloader pd;

    @EJB(beanName = "RecordCache")
    private IRecordCache recordCache;

    @PostConstruct
    public void start() {
        startDownloader(getClass().getSimpleName(), CochraneCMSPropertyNames.getCentralDownloadSchedule(), true, false);
        downloaded = false;
    }

    @Override
    public void update() {
        start();
    }

    @PreDestroy
    public void stop() {
        stopDownloader(getClass().getSimpleName());
    }

    public static void resetCentralDownloaderStatus() {
        if (downloaded) {
            LOG.info("Monthly CENTRAL downloaded parameter switched to false");
            downloaded = false;
        }
    }

    @Override
    protected boolean canDownload() {
        return CochraneCMSPropertyNames.isCentralDownload() && !downloaded;
    }

    @Override
    protected void download() {

        ZonedDateTime now = CmsUtils.getCochraneDownloaderDateTime();
        IssueEntity ie = findIssue(now.getYear(), now.getMonth().getValue());
        if (ie != null) {
            LOG.info("monthly CENTRAL package for %d is being checked for download ...", ie.getFullNumber());
            ClDbEntity clDb = null;
            try {
                clDb = rs.getDb(rs.createDb(ie.getId(), CochraneCMSPropertyNames.getCentralDbName(), 0));
                if (checkIn(clDb)
                        && pd.downloadCentralSFTP(now.getYear(), now.getMonth().getValue(), ie.getId()) == null) {
                    checkNextDate(ie.getYear(), ie.getNumber());
                }
            } catch (Exception e) {
                LOG.warn(e.getMessage());

            } finally {
                checkOut(clDb);
            }
        }
    }

    private IssueEntity findIssue(int issueYear, int issueMonth) {
        try {
            return rs.findOpenIssueEntity(issueYear, issueMonth);

        } catch (CmsException e) {
            LOG.warn(e.getMessage());
            return null;
        }
    }

    private void checkOut(ClDbEntity clDb) {
        if (clDb != null) {
            recordCache.removeRecord(clDb.getId().toString());
        }
    }

    private boolean checkIn(ClDbEntity clDb) {
        boolean ret = false;
        if (clDb != null) {
            if (clDb.isInitialPackageDelivered()) {
                LOG.info(String.format("monthly CENTRAL package for %d has been already downloaded",
                        clDb.getIssue().getFullNumber()));
                downloaded = true;

            } else if (!recordCache.addRecord(clDb.getId().toString(), false)) {
                LOG.warn(String.format("monthly CENTRAL downloading for %d has been already started by other action",
                        clDb.getIssue().getFullNumber()));
            } else {
                ret = true;
            }
        }
        return ret;
    }

    private void checkNextDate(int year, int issueNumber) {

        Date nextDate = ProcessHelper.getNextDateBySchedule(CochraneCMSPropertyNames.getCentralDownloadSchedule());
        LocalDate date = nextDate == null ? LocalDate.now() : Now.convertToLocalDate(nextDate);
        if (nextDate == null || date.getMonth() != LocalDate.now().getMonth()) {

            String msg = CochraneCMSPropertyNames.getCentralDownloadFailedMsg(
                    Collections.singletonMap("date", LocalDate.now().toString()));
            LOG.warn(msg);
            MessageSender.sendOnCentralDownloadFailed(year, issueNumber, msg);
        }
    }
}
