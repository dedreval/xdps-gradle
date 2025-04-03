package com.wiley.cms.cochrane.cmanager.contentworker;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
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

import com.wiley.cms.process.entity.DbEntity;
import org.quartz.StatefulJob;

import com.wiley.cms.cochrane.cmanager.CmsException;
import com.wiley.cms.cochrane.cmanager.CmsUtils;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.data.IResultsStorage;
import com.wiley.cms.cochrane.cmanager.data.IssueEntity;
import com.wiley.cms.cochrane.cmanager.data.translation.PublishedAbstractEntity;
import com.wiley.cms.cochrane.cmanager.publish.IPublishStorage;
import com.wiley.cms.cochrane.repository.RepositoryUtils;
import com.wiley.cms.cochrane.services.IContentManager;
import com.wiley.cms.cochrane.utils.IssueDate;
import com.wiley.tes.util.ExceptionParser;
import com.wiley.tes.util.Extensions;
import com.wiley.tes.util.KibanaUtil;
import com.wiley.tes.util.Pair;
import com.wiley.cms.process.task.BaseDownloader;
import com.wiley.cms.process.task.IDownloader;
import com.wiley.tes.util.InputUtils;
import com.wiley.tes.util.Logger;

import static com.wiley.cms.cochrane.cmanager.contentworker.CentralDownloader.resetCentralDownloaderStatus;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 30.07.12
 */
@Singleton
@Local(IDownloader.class)
@Lock(LockType.READ)
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class ArchieDownloader extends BaseDownloader implements StatefulJob {

    static final String TEMP_FOLDER = "tmp/crg/";
    static final String INPUT_FOLDER = "tmp/crg/input/";
    static final String RESPONSE_FOLDER = "tmp/crg/output/";

    private static final Logger LOG = Logger.getLogger(ArchieDownloader.class);

    @EJB(beanName = "PublishStorage")
    protected IPublishStorage ps;

    @EJB(beanName = "ResultsStorage")
    private IResultsStorage rs;

    @EJB(beanName = "ContentManager")
    private IContentManager cm;

    @PostConstruct
    public void start() {
        startDownloader(getClass().getSimpleName(), CochraneCMSPropertyNames.getArchieDownloadSchedule(), true, false);
    }

    @Override
    public void update() {
        start();
    }

    @PreDestroy
    public void stop() {
        stopDownloader(getClass().getSimpleName());
    }

    @Override
    protected boolean canDownload() {
        return CochraneCMSPropertyNames.isArchieDownload();
    }

    @Override
    protected void download() {
        downloadContent();
        sendSuspendedResponses();
    }

    private void sendSuspendedResponses() {

        File dir = RepositoryUtils.getRealFile(TEMP_FOLDER);
        if (!dir.exists())  {
            return;
        }

        File[] packets = dir.listFiles(new FileFilter() {
            public boolean accept(File fl) {
                return fl.isFile() && fl.getName().endsWith(Extensions.XML);
            }
        });

        try {
            for (File fl: packets) {

                String name = fl.getName();
                if (name.endsWith(ArchiePackage.NOTIFICATION_ON_RECEIVE_POSTFIX + Extensions.XML)) {
                    RevmanPackage.notifyReceived(InputUtils.readStreamToString(new FileInputStream(fl)));

                } else if (name.endsWith(ArchiePackage.NOTIFICATION_ON_PUBLISH_POSTFIX + Extensions.XML)) {
                    if (RevmanPackage.notifyPublishedRepeated(name, InputUtils.readStreamToString(
                            new FileInputStream(fl))) == null) {
                        setNotified(name.replace(ArchiePackage.NOTIFICATION_ON_PUBLISH_POSTFIX + Extensions.XML, ""));
                    }
                } else {
                    continue;
                }
                //if (err) {
                //    InputStream is = new BufferedInputStream(new FileInputStream(fl));
                //    RepositoryFactory.getRepository().putFile(REVMAN_RESPONSE_FOLDER + fl.getName(), is, true);
                //}

                fl.delete();
            }
        } catch (Exception e) {
            LOG.error(e);
        }
    }

    private void setNotified(String name) {
        try {
            PublishedAbstractEntity pae = ps.updateWhenReadyOnPublished(Integer.parseInt(name), null, true);
            if (pae != null) {
                CochraneCMSPropertyNames.lookupFlowLogger().onDashboardEvent(
                        KibanaUtil.Event.NOTIFY_PUBLISHED, null, new Date(), pae);
            }
        } catch (Exception e) {
            LOG.warn(e.getMessage());
        }
    }

    protected int checkNewIssue(int issueYear, int issueMonth, int lastDay, ZoneId zone) {
        IssueEntity ie = findIssue(issueYear, issueMonth);
        return ie == null ? DbEntity.NOT_EXIST_ID : ie.getId();
    }

    final IssueEntity findIssue(int issueYear, int issueMonth) {
        try {
            return rs.findOpenIssueEntity(issueYear, issueMonth);
        } catch (CmsException e) {
            return null;
        }
    }

    final void downloadContent() {

        ZonedDateTime now = CmsUtils.getCochraneDownloaderDateTime();
        IssueDate iDate = new IssueDate(now);
        int lastDay = now.toLocalDate().lengthOfMonth();

        int issueId = checkNewIssue(iDate.issueYear, iDate.issueMonth, lastDay, now.getZone());

        LocalDateTime ldt = now.toLocalDateTime();
        if (isHalfTime(ldt, iDate.day, lastDay)) {
            return;
        }
        downloadContent(ldt, iDate.year, iDate.month, iDate.issueYear, iDate.issueMonth, issueId);
    }

    private boolean isHalfTime(LocalDateTime now, int day, int lastDay) {

        Pair<String[], String[]> pair = CochraneCMSPropertyNames.getArchieDownloadScheduleHalfTime();
        if (pair != null) {
            try {
                int startHour = Integer.valueOf(pair.first[0]);
                int startMin = Integer.valueOf(pair.first[1]);
                int endHour = Integer.valueOf(pair.second[0]);
                int endMin = Integer.valueOf(pair.second[1]);

                return isHalfTimeLast(now, day, startHour, startMin, lastDay)
                        || isHalfTimeFirst(now, day, endHour, endMin);

            } catch (NumberFormatException nfe) {
                LOG.warn(nfe);
            }
        }
        return false;
    }

    private boolean isHalfTimeLast(LocalDateTime now, int day, int startHour, int startMin, int lastDay) {
        if (isLastMonthlyDay(lastDay, day)) {
            // last day of month
            LOG.debug("now is the last day of the month... ");
            LocalDateTime control = LocalDateTime.of(now.getYear(), now.getMonthValue(), day, startHour, startMin, 0);
            if (control.isBefore(now)) {
                LOG.info(String.format("now is %s - the temporary halt at the last day of the month.", now));
                return true;
            }
        }
        return false;
    }

    private boolean isHalfTimeFirst(LocalDateTime now, int day, int startHour, int startMin) {
        if (isFirstMonthlyDay(day)) {
            // First day of month
            LOG.debug("now is the first day of the month... ");
            resetCentralDownloaderStatus();
            LocalDateTime control = LocalDateTime.of(now.getYear(), now.getMonthValue(), day, startHour, startMin, 0);
            if (control.isAfter(now)) {
                LOG.info(String.format("now is %s - the temporary halt at the first day of the month.", now));
                return true;
            }
        }
        return false;
    }

    private boolean isFirstMonthlyDay(int day) {
        return day == 1 || CochraneCMSPropertyNames.isUploadArchieFirstMonthlyDay();
    }

    private boolean isLastMonthlyDay(int lastDay, int day) {
        return lastDay == day || CochraneCMSPropertyNames.isUploadArchieLastMonthlyDay();
    }

    protected void downloadContent(LocalDateTime now, int year, int month, int issueYear, int issueMonth, int issueId) {
        LOG.info(String.format("Start download Cochrane data (crg) %s", now));
        try {
            ArchiePackage.downloadArchiePackage(year, month, issueYear, issueMonth, cm);

        } catch (javax.net.ssl.SSLHandshakeException sslEx) {
            ArchiePackage.sendCriticalErrorOnDownload(ExceptionParser.buildMessage(sslEx));
            LOG.error("SSL Error during download Cochrane data (crg)", sslEx);

        } catch (java.net.UnknownHostException uhe) {
            ArchiePackage.sendCriticalErrorOnDownload(ExceptionParser.buildMessage(uhe));
            LOG.error("Unknown Host Error during download Cochrane data (crg)", uhe);

        } catch (javax.xml.ws.WebServiceException we) {
            ArchiePackage.sendCriticalErrorOnDownload(ExceptionParser.buildMessage(we));
            LOG.error("WebService Error during download Cochrane data (crg)", we);
            
        } catch (Exception e) {
            LOG.error("Error during download Cochrane data (crg)", e);
        }
    }
}
