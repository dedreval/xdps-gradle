package com.wiley.cms.cochrane.cmanager.contentworker;

import com.wiley.cms.cochrane.cmanager.CochraneCMSProperties;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.FilePathCreator;
import com.wiley.cms.cochrane.cmanager.MessageSender;
import com.wiley.cms.cochrane.cmanager.res.PubType;
import com.wiley.cms.cochrane.cmanager.res.PublishProfile;
import com.wiley.cms.cochrane.cmanager.res.ServerType;
import com.wiley.cms.cochrane.repository.IRepository;
import com.wiley.cms.cochrane.repository.RepositoryUtils;
import com.wiley.cms.cochrane.test.PackageChecker;
import com.wiley.cms.process.AbstractFactory;
import com.wiley.tes.util.Extensions;
import com.wiley.tes.util.InputUtils;
import com.wiley.tes.util.Logger;
import com.wiley.tes.util.ftp.FtpInteraction;
import com.wiley.tes.util.ftp.NoFtpStub;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 2/22/2021
 */
public final class AriesConnectionManager extends AbstractFactory<FtpInteraction> {
    private static final Logger LOG = Logger.getLogger(AriesConnectionManager.class);

    private static final AriesConnectionManager INSTANCE_D = new AriesConnectionManager(PubType.TYPE_ARIES_D, false);
    private static final AriesConnectionManager INSTANCE_P = new AriesConnectionManager(PubType.TYPE_ARIES_P, false);
    private static final AriesConnectionManager INSTANCE_C = new AriesConnectionManager(PubType.TYPE_ARIES_C, false);
    private static final AriesConnectionManager INSTANCE_V = new AriesConnectionManager(PubType.TYPE_ARIES_V, true);

    private static final String FORMAT_TO_ARIES = "[\\w-]+\\.zip";

    private final String pubType;
    private final boolean optional;
    private final boolean hasGoManifest;
    private PublishProfile.PubLocationPath pubLocationPath;
    private volatile String downloadPath;
    private final Collection<String> fileNames = new HashSet<>();
    private int lastModifiedTime;

    private AriesConnectionManager(String pubType, boolean optional) {
        this.pubType = pubType;
        this.optional = optional;
        hasGoManifest = !optional;
    }

    public static synchronized List<File> checkForNewDeliveryPackagesForCancel(int issue) {
        boolean onlyLatest = CochraneCMSPropertyNames.isUploadAriesOnlyLatest();
        List<File> ret = INSTANCE_C.checkForNewPackages(issue, onlyLatest, true);
        if (!ret.isEmpty()) {
            LOG.info(String.format("%d Aries packages downloaded from SFTP for cancellation", ret.size()));
        }
        return ret;
    }

    public static synchronized List<File> checkForNewDeliveryPackages(int issue) {
        boolean onlyLatest = CochraneCMSPropertyNames.isUploadAriesOnlyLatest();
        List<File> ret = INSTANCE_D.checkForNewPackages(issue, onlyLatest, true);
        List<File> fromPublish = INSTANCE_P.checkForNewPackages(issue, onlyLatest, false);
        if (!ret.isEmpty()) {
            ret.addAll(fromPublish);
        } else {
            ret = fromPublish;
        }
        LOG.info(String.format("%d Aries packages downloaded from SFTP", ret.size()));
        return ret;
    }

    public static synchronized List<File> checkNewDeliveryPackagesForVerification(int issue) {
        boolean onlyLatest = CochraneCMSPropertyNames.isUploadAriesOnlyLatest();
        List<File> ret = INSTANCE_V.checkForNewPackages(issue, onlyLatest, true);
        if (!ret.isEmpty()) {
            LOG.info(String.format("%d Aries QA packages downloaded from SFTP for verification", ret.size()));
        }
        return ret;
    }

    static synchronized void copyPackageToBeingProcessedFolder(String packageName, String packagePath, IRepository rp) {
        try {
            File beingProcessedDir = INSTANCE_D.getBeingProcessedDir();
            if (!beingProcessedDir.exists()) {
                beingProcessedDir.mkdir();
            }
            String pathToBeingProcessed = beingProcessedDir.getAbsolutePath();
            logMovingFile(pathToBeingProcessed, packageName);
            InputUtils.writeFile(pathToBeingProcessed, packageName, rp.getFile(packagePath + packageName));
            if (!packageName.startsWith(PackageChecker.TRANSLATIONS)) {
                String goManifestName = AriesHelper.buildGOFileName(packageName);
                logMovingFile(pathToBeingProcessed, goManifestName);
                InputUtils.writeFile(pathToBeingProcessed, goManifestName, rp.getFile(packagePath + goManifestName));
            }
        } catch (Exception e) {
            LOG.error(e);
        }
    }

    private static void logMovingFile(String pathToBeingProcessed, String goManifestName) {
        LOG.debug("the package %s is being moved to %s ...", goManifestName,
                pathToBeingProcessed);
    }

    @Deprecated
    public static synchronized void removePackage(String packageName) {
        INSTANCE_D.removeFile(packageName);
        INSTANCE_P.removeFile(packageName);
        INSTANCE_C.removeFile(packageName);
        INSTANCE_V.removeFile(packageName);
    }

    public static synchronized void reset() {
        INSTANCE_D.close();
        INSTANCE_P.close();
        INSTANCE_C.close();
        INSTANCE_V.close();
    }

    public static String getLocalStubFolder() {
        return RepositoryUtils.getRealFile("tmp/aries_test").getAbsolutePath();
    }

    private void removeFile(String packageName, FtpInteraction interaction) {
        if (interaction == null) {
            return;
        }
        try {
            LOG.debug(String.format("submission %s is being removed ...", packageName));
            deleteFile(interaction, packageName);
            if (hasGoManifest) {
                String goXmlFileName = AriesHelper.buildGOFileName(packageName);
                deleteFile(interaction, goXmlFileName);
            }
        } catch (Throwable e) {
            LOG.error(e.getMessage(), e.getCause());
            //InputUtils.closeFtpConnection(interaction);
        }
    }

    private synchronized void deleteFile(FtpInteraction interaction, String goXmlFileName) throws Exception {
        if (interaction.hasFile(goXmlFileName)){
            interaction.deleteFile(goXmlFileName);
        }
    }

    private List<File> checkForNewPackages(int issueNumber, boolean uploadOnlyLatest, boolean beingProcessed) {
        FtpInteraction interaction = getInteraction();
        List<File> ret = Collections.emptyList();
        Set<String> names = Collections.emptySet();
        if (interaction != null) {
            try {
                names = uploadOnlyLatest
                        ? interaction.listFilesFilteredByModifiedTime(lastModifiedTime)
                        : interaction.listFilesFilteredByModifiedTime(0);
                if (!names.isEmpty()) {
                    lastModifiedTime = interaction.getLatestModifiedTime();
                    ret = downloadFiles(names, issueNumber, interaction);
                }

            } catch (Throwable e) {
                LOG.error(e.getMessage(), e.getCause());
                InputUtils.closeConnection(interaction);
            }
        }
        if (ret.isEmpty()) {
            ret = beingProcessed /** && uploadOnlyLatest */ ? checkForBeingProcessedPackages(issueNumber, names) : ret;

        } else if (beingProcessed /** && uploadOnlyLatest*/) {
            ret.addAll(checkForBeingProcessedPackages(issueNumber, names));
        }
        return ret;
    }


    private synchronized FtpInteraction getInteraction() {
        FtpInteraction interaction = bean != null ? bean : getExistingInteraction();
        if (interaction == null) {
            return getInstance();
        }
        return initInteraction(interaction);
    }

    @Override
    protected void init() {
        initInteraction(null);
    }

    private FtpInteraction initInteraction(FtpInteraction existingInteraction) {
        if (downloadPath == null) {
            downloadPath = CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.PREFIX_REPOSITORY)
                    + "/downloaded/aries/";
        }
        try {
            if (pubLocationPath == null) {
                pubLocationPath = PublishProfile.getProfile().get().getWhenReadyPubLocation(
                        PubType.MAJOR_TYPE_ARIES_INT, pubType, null);
                if (pubLocationPath == null) {
                    checkOptional();
                    return null;
                }
            }
            if (existingInteraction != null) {
                if (!existingInteraction.isConnected()) {
                    LOG.info(String.format("%s reconnect... ", pubLocationPath));
                    ServerType serverType = checkServerType();
                    existingInteraction.connect(serverType.getHost(), serverType.getPort(), serverType.getUser(),
                            serverType.getPassword(), serverType.getTimeout());
                }
                bean = existingInteraction;

            } else {
                ServerType serverType = checkServerType();
                if (serverType.isLocalHost()) {
                    bean = new NoFtpStub(getLocalStubFolder());
                    bean.connect(serverType.getHost(), serverType.getPort(),
                            serverType.getUser(), serverType.getPassword());
                } else {
                    bean = InputUtils.getSftpConnection(serverType.getHost(), serverType.getPort(),
                            serverType.getUser(), serverType.getPassword(), serverType.getTimeout());
                }
            }
            changeFolder(bean);

        } catch (Throwable tr) {
            LOG.error(tr.getMessage(), tr.getCause());
            close();
        }
        return bean;
    }

    private void checkOptional() throws Exception {
        if (!optional) {
            throw noConfigurationException(pubType + "." + PubType.MAJOR_TYPE_ARIES_INT);
        }
    }

    private ServerType checkServerType() throws Exception {
        ServerType serverType = pubLocationPath.getServerType();
        if (serverType == null) {
            throw noConfigurationException(pubLocationPath.toString());
        }
        return serverType;
    }

    private void changeFolder(FtpInteraction interaction) throws Exception {
        String folder = pubLocationPath.getFolder();
        interaction.changeDirectory(folder == null || folder.isEmpty() ? FilePathCreator.SEPARATOR : folder);
    }

    private static File downloadFile(String fileName, File parent, FtpInteraction interaction) {
        File fl = new File(parent, fileName);
        LOG.info(String.format("%s is being downloaded ...", fileName));
        try {
            interaction.downloadFile(fileName, fl.getAbsolutePath());
            return fl;

        } catch (Exception e) {
            LOG.error(String.format("downloading of %s failed", fileName), e);
            MessageSender.sendFailedLoadPackageMessage(fileName, e.getMessage(), MessageSender.SEND_TO_COCHRANE_ALWAYS);
        }
        return null;
    }
    private List<File> downloadFiles(Set<String> names, int issueNumber, FtpInteraction interaction) {
        File parentDir = RepositoryUtils.getRealFile(downloadPath + issueNumber);
        if (!parentDir.exists()) {
            parentDir.mkdir();
        }

        List<File> ret = new ArrayList<>();
        int limit = CochraneCMSPropertyNames.getAriesDownloaderLimit();
        for (String fileName : names) {
            if (fileName.matches(FORMAT_TO_ARIES)) {
                boolean isTranslation = fileName.startsWith(PackageChecker.TRANSLATIONS);
                File ariesPackage = downloadFile(fileName, parentDir, interaction);
                if (ariesPackage == null || !downloadGoXml(fileName, parentDir, interaction, isTranslation)) {
                    continue;
                }
                removeFile(fileName, interaction);
                ret.add(ariesPackage);
                limit--;
                if (limit == 0) {
                    break;
                }
            } else if (!fileName.endsWith(Extensions.GO_XML)){
                LOG.warn("%s doesn't match to <submission partner>_<submission identifier>.zip", fileName);
            }
        }
        return ret;
    }

    private boolean downloadGoXml(String fileName, File parentDir, FtpInteraction interaction, boolean isTranslation) {
        if (hasGoManifest && !isTranslation) {
            String goXmlName = AriesHelper.buildGOFileName(fileName);
            File goXml = downloadFile(goXmlName, parentDir, interaction);
            if (goXml == null) {
                LOG.warn("can't download %s", goXmlName);
                return false;
            }
        }
        return true;
    }

    private File getBeingProcessedDir() {
        return RepositoryUtils.getRealFile(downloadPath + "beingProcessed");
    }

    private List<File> checkForBeingProcessedPackages(int issueNumber, Set<String> alreadyTakenNames) {
        File beingProcessedDir = getBeingProcessedDir();
        if (beingProcessedDir.exists()) {
            File[] packages = beingProcessedDir.listFiles(RepositoryUtils.ZIP_FF);
            if (packages != null && packages.length > 0) {
                List<File> beingProcessedList = takeBeingProcessedPackages(
                        packages, beingProcessedDir, issueNumber, alreadyTakenNames);
                return beingProcessedList;
            }
        }
        return Collections.emptyList();
    }

    private List<File> takeBeingProcessedPackages(File[] beingProcessedPackages, File beingProcessedDir,
                                                  int issueNumber, Set<String> alreadyTakenNames) {
        List<File> ret = new ArrayList<>();
        for (File bp: beingProcessedPackages) {
            File parentDir = RepositoryUtils.getRealFile(downloadPath + issueNumber);
            File downloaded = new File(parentDir, bp.getName());
            if (downloaded.exists()) {
                if (!alreadyTakenNames.contains(bp.getName())) {
                    ret.add(downloaded);
                }
                bp.delete();
                new File(beingProcessedDir, AriesHelper.buildGOFileName(bp.getName())).delete();
            }
        }
        return ret;
    }

    @Override
    public synchronized void reload() {
        close();
        init();
    }

    private void removeFile(String packageName) {
        if (fileNames.remove(packageName)) {
            removeFile(packageName, getInteraction());
        }
    }

    private void close() {
        pubLocationPath = null;
        downloadPath = null;
        InputUtils.closeConnection(bean);
        bean = null;
    }

    private static FtpInteraction getExistingInteraction() {
        return INSTANCE_D.bean != null ? INSTANCE_D.bean : (INSTANCE_P.bean != null ? INSTANCE_P.bean
                : (INSTANCE_C != null ? INSTANCE_C.bean : INSTANCE_V.bean));
    }

    private static Exception noConfigurationException(String msg) {
        return new Exception(String.format("no configuration for Aries delivering (%s)", msg));
    }
}
