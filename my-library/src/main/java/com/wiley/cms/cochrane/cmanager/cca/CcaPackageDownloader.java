package com.wiley.cms.cochrane.cmanager.cca;

import com.wiley.cms.cochrane.cmanager.CochraneCMSProperties;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.MessageSender;
import com.wiley.cms.cochrane.cmanager.contentworker.AbstractWorker;
import com.wiley.cms.cochrane.cmanager.contentworker.CCAWorker;
import com.wiley.cms.cochrane.cmanager.contentworker.WorkerFactory;
import com.wiley.cms.cochrane.repository.RepositoryUtils;
import com.wiley.cms.cochrane.services.IContentManager;
import com.wiley.cms.process.task.BaseDownloader;
import com.wiley.cms.process.task.IDownloader;
import com.wiley.tes.util.InputUtils;
import com.wiley.tes.util.Logger;
import com.wiley.tes.util.URIWrapper;
import com.wiley.tes.util.ftp.FtpInteraction;

import org.quartz.StatefulJob;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.ejb.EJB;
import javax.ejb.Local;
import javax.ejb.Lock;
import javax.ejb.LockType;
import javax.ejb.Singleton;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * @author <a href='mailto:aasadulin@wiley.com'>Alexandr Asadulin</a>
 * @version 30.03.2012
 */
@Singleton
@Local(IDownloader.class)
@Lock(LockType.READ)
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class CcaPackageDownloader extends BaseDownloader implements StatefulJob {

    private static final Logger LOG = Logger.getLogger(CcaPackageDownloader.class);
    private static final String ERROR_DOWNLOADING_MESSAGE = "Error downloading file [%s]";


    private final String filenameFormat = getRegexp(CCAWorker.class);

    @EJB(beanName = "ContentManager")
    private IContentManager manager;

    private String getRegexp(Class <? extends AbstractWorker> clazz) {
        for (Object[] entry : WorkerFactory.PACKAGE_REGEXP_LIST) {
            if (clazz == entry[1]) {
                return (String) entry[0];
            }
        }
        return "";
    }

    @PostConstruct
    public void start() {
        startDownloader(getClass().getSimpleName(), CochraneCMSPropertyNames.getCCADownloadSchedule(), true, false);
    }

    @PreDestroy
    public void stop() {
        stopDownloader(getClass().getSimpleName());
    }

    @Override
    public void update() {
        start();
    }

    @Override
    protected boolean canDownload() {
        return CochraneCMSPropertyNames.isCCADownload();
    }

    @Override
    protected void download() {
        LOG.debug("> Download CCA packages started");

        String source = CochraneCMSPropertyNames.getCCADownloadUrl();
        FtpInteraction ftpInteraction = null;
        List<String> downloadedFiles = new ArrayList<>();
        List<String> failedFiles = new ArrayList<>();
        List<String> ignoredFiles = null;
        try {
            URIWrapper uri = getUri(source);
            ftpInteraction = InputUtils.getConnection(uri);
            if (ftpInteraction == null) {
                return;
            }

            if (uri.getGoToPath() != null && !uri.getGoToPath().isEmpty() && !uri.getGoToPath().equals("/")) {
                ftpInteraction.changeDirectory(uri.getGoToPath());
            }

            List<String> fileNames = ftpInteraction.listFiles();
            for (String fileName : fileNames) {
                if (fileName.matches(filenameFormat)) {
                    processFile(fileName, ftpInteraction, downloadedFiles, failedFiles, this::deliverPackage);
                } else {
                    if (ignoredFiles == null) {
                        ignoredFiles = new ArrayList<>();
                    }
                    ignoredFiles.add(fileName);
                }
            }
        } catch (Exception e) {
            LOG.error("Failed to download CCA packages", e);

        } finally {
            InputUtils.closeConnection(ftpInteraction);
        }

        if (!downloadedFiles.isEmpty()) {
            sentNotification(downloadedFiles, failedFiles);
        }
    }

    private void processFile(String fileName,
                             FtpInteraction ftpInteraction,
                             List<String> downloadedFiles,
                             List<String> failedFiles, BiConsumer<String, URI> deliver) {
        try {
            URI packageUri = downloadPackage(ftpInteraction, fileName);
            deliver.accept(fileName, packageUri);
            downloadedFiles.add(fileName);
        } catch (Exception e) {
            failedFiles.add(fileName);
            LOG.error(String.format("Package %s processing failed", fileName), e);
        }
    }

    private URI downloadPackage(FtpInteraction ftpInteraction, String fileName) throws Exception {
        if (isUnlocked(fileName, ftpInteraction)) {
            return moveFile(ftpInteraction, fileName);
        } else {
            String err = String.format(ERROR_DOWNLOADING_MESSAGE, fileName)
                    + ". File is not fully uploaded or it is locked another process";
            throw new Exception(err);
        }
    }

    private URI moveFile(FtpInteraction ftpInteraction, String fileName) throws Exception {
        File downloadTo = RepositoryUtils.getRealFile(
                CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.PREFIX_REPOSITORY) + "/cca/downloaded/"
                        + fileName);
        downloadTo.getParentFile().mkdirs();

        LOG.debug(String.format("Download file [%s] to [%s] started", fileName, downloadTo.getAbsolutePath()));
        try {
            ftpInteraction.downloadFile(fileName, downloadTo.getAbsolutePath());
            ftpInteraction.deleteFile(fileName);
            LOG.debug("Downloading file [" + fileName +  "] successfully completed");
        } catch (Exception e) {
            throw new Exception(String.format(ERROR_DOWNLOADING_MESSAGE, fileName), e);
        }

        return downloadTo.toURI();
    }

    private void deliverPackage(String fileName, URI uri) {
        LOG.debug(String.format("> deliverPackage [%s]", uri.toString()));
        manager.newPackageReceived(uri);
        LOG.debug("< deliverPackage");
    }

    private boolean isUnlocked(String fileName, FtpInteraction ftpInteraction) {
        boolean unlocked = true;
        String tempFileName = fileName + ".part";
        try {
            ftpInteraction.renameFile(fileName, tempFileName);
            ftpInteraction.renameFile(tempFileName, fileName);
        } catch (Exception e) {
            unlocked = false;
        }

        return unlocked;
    }

    private URIWrapper getUri(String uri) throws URISyntaxException {
        return new URIWrapper(new URI(uri));
    }

    private void sentNotification(List<String> downloadedFiles, List<String> failedFiles) {
        Map<String, String> params = new HashMap<>();
        params.put("loaded", createMessageFromList(downloadedFiles));
        params.put("failed", createMessageFromList(failedFiles));
        //params.put("ignored", createMessageFromList(ignoredFiles));
        String msgBody = CochraneCMSProperties.getProperty("cca.package_loading.results", params);

        params.clear();
        params.put("body", msgBody);
        MessageSender.sendMessage("cca_downloading_finished", params);
    }

    private String createMessageFromList(List<String> lst) {
        if (lst.isEmpty()) {
            return "none";
        } else {
            StringBuilder strb = new StringBuilder();
            strb.append(lst.get(0));
            for (int i = 1; i < lst.size(); i++) {
                strb.append(", ").append(lst.get(i));
            }

            return strb.toString();
        }
    }
}
