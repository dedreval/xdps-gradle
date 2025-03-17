package com.wiley.cms.cochrane.cmanager.contentworker;

import java.io.File;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Objects;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.ejb.EJB;
import javax.ejb.Local;
import javax.ejb.Lock;
import javax.ejb.LockType;
import javax.ejb.Singleton;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import com.wiley.cms.cochrane.cmanager.CmsUtils;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.FilePathCreator;
import com.wiley.cms.cochrane.cmanager.ICochraneContentSupport;
import com.wiley.cms.cochrane.cmanager.data.IssueEntity;
import com.wiley.cms.cochrane.cmanager.data.translation.PublishedAbstractEntity;
import com.wiley.cms.cochrane.cmanager.entitywrapper.IssueWrapper;
import com.wiley.cms.cochrane.cmanager.publish.send.cochrane.CochraneSender;
import com.wiley.cms.cochrane.repository.RepositoryFactory;
import com.wiley.cms.cochrane.repository.RepositoryUtils;
import com.wiley.cms.process.entity.DbEntity;
import com.wiley.cms.process.task.IDownloader;
import com.wiley.tes.util.Extensions;
import com.wiley.tes.util.KibanaUtil;
import com.wiley.tes.util.Logger;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 30.12.20
 */
@Singleton
@Local(IDownloader.class)
@Lock(LockType.READ)
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class AriesDownloader extends ArchieDownloader {
    private static final Logger LOG = Logger.getLogger(AriesDownloader.class);

    @EJB(beanName = "CochraneContentSupport")
    private ICochraneContentSupport ccs;

    @PostConstruct
    public void start() {
        startDownloader(getClass().getSimpleName(), CochraneCMSPropertyNames.getAriesDownloadSchedule(), true, false);
    }

    @Override
    public void update() {
        start();
    }

    @PreDestroy
    public void stop() {
        super.stop();
    }

    @Override
    protected boolean canDownload() {
        boolean ret = CochraneCMSPropertyNames.isAriesDownloadSFTP();
        if (!ret) {
            AriesConnectionManager.reset();
        }
        return ret;
    }

    @Override
    protected void download() {
        downloadContent();
        sendSuspendedResponses();
    }

    @Override
    protected int checkNewIssue(int issueYear, int issueMonth, int lastDay, ZoneId zone) {
        IssueEntity ie = findIssue(issueYear, issueMonth);
        if (ie == null) {
            ZonedDateTime ldPublish = ZonedDateTime.of(issueYear, issueMonth, lastDay, 0, 0, 0, 0, zone);
            Date publish = Date.from(ldPublish.toInstant());
            return IssueWrapper.createIssueDb("", issueMonth, issueYear, publish);
        }
        return ie.getId();
    }

    @Override
    protected void downloadContent(LocalDateTime now, int year, int month, int issueYear, int issueMonth, int issueId) {
        LOG.info(String.format("Start download Aries packages %s", now));
        try {
            if (issueId != DbEntity.NOT_EXIST_ID) {
                ccs.uploadAriesPackages(issueId, CmsUtils.getIssueNumber(issueYear, issueMonth));
                return;
            }
            LOG.warn(String.format("Issue %d, %d does not exist to download Aries packages", issueMonth, issueYear));

        } catch (Exception e) {
            LOG.error("Error during download Aries packages", e);
        }
    }

    private void sendSuspendedResponses() {

        String tempDQLFolder = CochraneSender.RETRY_FOLDER;
        File dir = RepositoryUtils.getRealFile(tempDQLFolder);
        if (!dir.exists())  {
            return;
        }

        File[] packets = dir.listFiles(fl -> fl.isFile() && fl.getName().endsWith(Extensions.ZIP));

        try {
            for (File fl: Objects.requireNonNull(packets)) {
                String fileName = fl.getName();
                String filePath = getFullFilePath(tempDQLFolder, fileName);
                CochraneSender cochraneSender = CochraneSender.createEntireInstance();
                boolean success = cochraneSender.sendUndeliveredNotificationsBySftp(filePath);
                if (success) {
                    PublishedAbstractEntity pae = ps.findWhenReadyByCochraneNotification(fileName);
                    if (pae != null){
                        setNotified(pae.getId(), fileName);
                    }
                }
            }
        } catch (Exception e) {
            LOG.error(e);
        }
    }

    private void setNotified(Integer paeId, String fileName) {
        try {
            PublishedAbstractEntity pae = ps.updateWhenReadyOnPublished(paeId, null, true);
            if (pae != null) {
                CochraneCMSPropertyNames.lookupFlowLogger().onDashboardEvent(
                        KibanaUtil.Event.NOTIFY_PUBLISHED, fileName, new Date(), pae);
            }
        } catch (Exception e) {
            LOG.warn(e.getMessage());
        }
    }

    private String getFullFilePath(String tempDQLFolder, String name) {
        return RepositoryFactory.getRepository().getRealFilePath(FilePathCreator.SEPARATOR + tempDQLFolder + name);
    }
}
