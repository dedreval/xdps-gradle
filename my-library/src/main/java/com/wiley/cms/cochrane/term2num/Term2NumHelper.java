package com.wiley.cms.cochrane.term2num;

import com.wiley.cms.cochrane.cmanager.CmsUtils;
import com.wiley.cms.cochrane.cmanager.CochraneCMSProperties;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.utils.SSLChecker;
import com.wiley.cms.process.ProcessException;
import com.wiley.cms.process.RepeatableOperation;
import com.wiley.tes.util.Extensions;
import com.wiley.tes.util.Logger;
import com.wiley.tes.util.ftp.FTPConnection;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.CharEncoding;
import org.apache.commons.lang.StringUtils;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * @author <a href='mailto:aasadulin@wiley.com'>Alexander Asadulin</a>
 * @version 07.12.2015
 */
public class Term2NumHelper {
    private static final Logger LOG = Logger.getLogger(Term2NumHelper.class);

    private static final String CREAT_START_MSG_TEMPL = "Creating %s started";
    private static final String CREAT_COMPL_MSG_TEMPL = "Creating %s completed";
    private static final String CREAT_FAILED_MSG_TEMPL = "Creating %s completed with exception %s";

    private Term2NumHelper() {
    }

    public static void makeTerm2Num(boolean downloadMesh) throws ProcessException {
        String year = getCurrentYear();
        AnnualMeshResources resources = getMeshResources(year);

        if (isDownloadMeshTerms(downloadMesh, resources)) {
            downloadMeshResources(resources);
        }
        unzipMeshResources(resources);

        // load Pharamacological Action tree numbers
        Map<String, ArrayList<String>> mtreeNumsMap = createMTreeNumsMap(resources);
        List<String> descs = createDescriptorList(mtreeNumsMap, resources);
        File paTreeNumFile = makePaTreeNumFile(mtreeNumsMap, descs, resources);

        File fullTreeNumFile = makeFullTreeNumFile(paTreeNumFile, resources);

        String perlCmd = getResourcePath(CochraneCMSPropertyNames.TERM2NUM_PERL);
        String perlSctiptDir = getPerlPathParameter(CochraneCMSPropertyNames.TERM2NUM_PERLSCRIPTS);
        String downloadDir = getPerlPathParameter(CochraneCMSPropertyNames.TERM2NUM_DOWNLOADS);

        generateQualConf(perlCmd, perlSctiptDir, downloadDir, resources);
        createTerm2num(perlCmd, perlSctiptDir, downloadDir, fullTreeNumFile);
    }

    private static String getCurrentYear() {
        return String.valueOf(new GregorianCalendar().get(GregorianCalendar.YEAR));
    }

    private static AnnualMeshResources getMeshResources(String year) {
        AnnualMeshResources resources = new AnnualMeshResources(year);
        resources.add(CochraneCMSPropertyNames.TERM2NUM_REMOTE_DESC_YYYYXML,
                "cms.cochrane.term2num.remote.descYYYYxml.check_zipped");
        resources.add(CochraneCMSPropertyNames.TERM2NUM_REMOTE_Q_YYYYBIN, null);
        resources.add(CochraneCMSPropertyNames.TERM2NUM_REMOTE_QUAL_YYYYXML, null);
        resources.add(CochraneCMSPropertyNames.TERM2NUM_REMOTE_MTREES_YYYYBIN, null);

        return resources;
    }

    private static boolean isDownloadMeshTerms(boolean download, AnnualMeshResources resources) {
        return download || !isMeshResourcesExist(resources);
    }

    private static boolean isMeshResourcesExist(AnnualMeshResources resources) {
        for (AnnualMeshResource res : resources) {
            if (!isMeshResourceExists(res)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isMeshResourceExists(AnnualMeshResource resource) {
        return resource.getLocalFile().exists()
                || resource.getZippedLocalFile() != null && resource.getZippedLocalFile().exists();
    }

    private static void downloadMeshResources(AnnualMeshResources resources) throws ProcessException {
        LOG.debug("Downloading mesh resources started");

        FTPConnection ftpConnection = null;
        try {
            ftpConnection = createFTPConnection();
            for (AnnualMeshResource res : resources) {
                downloadMeshResource(res, ftpConnection);
            }

            LOG.debug("Downloading mesh resources completed");
        } catch (Exception e) {
            String err = "Downloading mesh resources failed, " + e;
            LOG.error(err, e);
            throw new ProcessException(err);
        } finally {
            closeFTPConnection(ftpConnection);
        }
    }

    private static FTPConnection createFTPConnection() throws ProcessException {
        FTPConnection ftpConnection = new FTPConnection();
        try {
            connectToFTP(ftpConnection);
            login(ftpConnection);
        } catch (Exception e) {
            throw new ProcessException("Failed to connect to FTP, " + e);
        }
        return ftpConnection;
    }

    private static void connectToFTP(FTPConnection ftpConnection) throws Exception {
        String host = CochraneCMSProperties.getProperty("cms.cochrane.term2num.ftp.server");
        try {
            if (ftpConnection.connect(host)) {
                LOG.debug("Connected to FTP " + host);
            } else {
                throw new Exception(String.format("Connection to FTP is unsuccessful"));
            }
        } catch (IOException e) {
            throw new Exception(String.format("Connection to FTP %s completed with exception %s", host, e));
        }
    }

    private static void login(FTPConnection ftpConnection) throws Exception {
        String user = CochraneCMSProperties.getProperty("cms.cochrane.term2num.ftp.user");
        String password = CochraneCMSProperties.getProperty("cms.cochrane.term2num.ftp.password");
        try {
            if (!ftpConnection.login(user, password)) {
                throw new Exception(String.format("Log in user %s failed", user));
            }
        } catch (IOException e) {
            throw new Exception(String.format("Log in user %s completed with exception %s", user, e));
        }
    }

    private static void downloadMeshResource(AnnualMeshResource res, FTPConnection ftpConnection) throws Exception {
        boolean done = false;
        for (String remotePath : res.getRemotePaths()) {
            File localFile = remotePath.endsWith(Extensions.ZIP) ? res.getZippedLocalFile() : res.getLocalFile();
            String localPath = localFile.getAbsolutePath();
            try {
                done = ftpConnection.downloadFile(remotePath, localPath) && FileUtils.sizeOf(localFile) > 0;
                if (done) {
                    LOG.debug(String.format("%s has been downloaded from %s.", localPath, remotePath));
                    break;
                } else {
                    LOG.warn(String.format(
                            "Downloading %s from %s failed. Will be repeated with alternative path if available",
                            localPath, remotePath));
                }
            } catch (IOException e) {
                throw new Exception(String.format("Downloading %s failed with exception %s", localPath, e));
            }
        }
        if (!done) {
            throw new Exception(String.format("Downloading %s failed for all attempts", res.localFile));
        }
    }

    private static void closeFTPConnection(FTPConnection ftpConnection) {
        if (ftpConnection != null) {
            ftpConnection.disconnect();
        }
    }

    private static void unzipMeshResources(AnnualMeshResources resources) throws ProcessException {
        for (AnnualMeshResource res : resources) {
            if (res.getZippedLocalFile() != null
                    && (!res.getLocalFile().exists()
                    || FileUtils.isFileNewer(res.getZippedLocalFile(), res.getLocalFile()))) {
                unzipMeshResource(res);
            }
        }
    }

    private static void unzipMeshResource(AnnualMeshResource res) throws ProcessException {
        LOG.debug(String.format("Extracting %s started", res.getZippedLocalFile()));

        String fName = res.getLocalFile().getName();
        try (ZipInputStream is = new ZipInputStream(new FileInputStream(res.getZippedLocalFile()));
            OutputStream os = new BufferedOutputStream(new FileOutputStream(res.getLocalFile()))) {
            ZipEntry entry;
            while ((entry = is.getNextEntry()) != null) {
                if (entry.getName().equals(fName)) {
                    IOUtils.copy(is, os);
                    break;
                }
            }

            LOG.debug(String.format("Extracting %s completed", res.getZippedLocalFile()));
        } catch (IOException e) {
            String err = String.format("Extracting %s completed with exception %s", res.getZippedLocalFile(), e);
            LOG.error(err, e);
            throw new ProcessException(err);
        }
    }

    private static Map<String, ArrayList<String>> createMTreeNumsMap(
            AnnualMeshResources resources) throws ProcessException {
        AnnualMeshResource res = resources.get(CochraneCMSPropertyNames.TERM2NUM_REMOTE_MTREES_YYYYBIN);

        LOG.debug(String.format("Creating Mesh tree numbers map from %s started", res.getLocalFile()));
        try {
            Map<String, ArrayList<String>> mtreeNumsMap = new HashMap<String, ArrayList<String>>();
            List<String> lines = getLines(res.getLocalFile());
            String[] nvPair;
            ArrayList<String> nums;

            for (String line : lines) {
                line = line.trim();
                if (line.length() > 0) {
                    nvPair = line.split(";");

                    nums = mtreeNumsMap.get(nvPair[0]);
                    if (nums == null) {
                        nums = new ArrayList<String>();
                    }

                    // "Chemical Actions and Uses"
                    if (nvPair[1].startsWith("D27")) {
                        nums.add(nvPair[1]);
                    }

                    mtreeNumsMap.put(nvPair[0], nums);
                }
            }

            LOG.debug(String.format("Creating Mesh tree numbers map from %s completed", res.getLocalFile()));
            return mtreeNumsMap;
        } catch (IOException e) {
            String err = (String.format("Creating Mesh tree numbers map from %s completed with exception %s",
                    res.getLocalFile(), e));
            LOG.error(err, e);
            throw new ProcessException(err);
        }
    }

    private static List<String> getLines(File file) throws IOException {
        InputStream is = null;
        try {
            is = new FileInputStream(file);
            return IOUtils.readLines(is, CharEncoding.UTF_8);
        } finally {
            IOUtils.closeQuietly(is);
        }
    }

    private static List<String> createDescriptorList(Map<String, ArrayList<String>> mtreeNumsMap,
                                                     AnnualMeshResources resources) throws ProcessException {
        AnnualMeshResource res = resources.get(CochraneCMSPropertyNames.TERM2NUM_REMOTE_DESC_YYYYXML);

        LOG.debug(String.format("Creating descriptor list from %s started", res.getLocalFile()));
        try {
            DescriptorRecordParser descRecParser = new DescriptorRecordParser(mtreeNumsMap);

            SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setValidating(true);
            factory.setNamespaceAware(true);
            
            SAXParser parser = factory.newSAXParser();
            new RepeatableOperation() {
                Term2NumHandler handler = new Term2NumHandler(descRecParser);

                @Override
                protected void perform() throws Exception {
                    parser.parse(res.getLocalFile(), handler);
                }

                @Override
                protected Exception onNextAttempt(Exception e) {
                    if (SSLChecker.checkCertificate(handler.getSystemId(), e)) {
                        return super.onNextAttempt(e);
                    }
                    fillCounter();
                    return e;
                }
            }.performOperationThrowingException();

            LOG.debug(String.format("Creating descriptor list from %s completed", res.getLocalFile()));
            return descRecParser.getDescriptors();
        } catch (Exception e) {
            String err = String.format("Creating descriptor list from %s completed with exception %s",
                    res.getLocalFile(), e);
            LOG.error(err, e);
            throw new ProcessException(err);
        }
    }

    private static File makePaTreeNumFile(Map<String, ArrayList<String>> mtreeNumsMap,
                                          List<String> descs,
                                          AnnualMeshResources resources) throws ProcessException {
        AnnualMeshResource res = resources.get(CochraneCMSPropertyNames.TERM2NUM_REMOTE_MTREES_YYYYBIN);
        BufferedWriter bw = null;
        File paTreeNumFile = addSuffix2FileName(res.getLocalFile(), ".pa.");

        LOG.debug(String.format(CREAT_START_MSG_TEMPL, paTreeNumFile));
        try {
            bw = new BufferedWriter(new FileWriter(paTreeNumFile));

            bw.write(mtreeNumsMap.toString());
            for (String desc : descs) {
                bw.write(desc);
            }

            LOG.debug(String.format(CREAT_COMPL_MSG_TEMPL, paTreeNumFile));
        } catch (IOException e) {
            String err = String.format(CREAT_FAILED_MSG_TEMPL, paTreeNumFile, e);
            LOG.error(err, e);
            throw new ProcessException(err);
        } finally {
            IOUtils.closeQuietly(bw);
        }

        return paTreeNumFile;
    }

    private static File makeFullTreeNumFile(File paTreeNumFile,
                                            AnnualMeshResources resources) throws ProcessException {
        AnnualMeshResource res = resources.get(CochraneCMSPropertyNames.TERM2NUM_REMOTE_MTREES_YYYYBIN);

        File fullTreeNumFile = addSuffix2FileName(res.getLocalFile(), ".full.");
        fullTreeNumFile.delete();

        LOG.debug(String.format(CREAT_START_MSG_TEMPL, fullTreeNumFile));

        InputStream is1 = null, is2 = null;
        OutputStream os = null;
        try {
            is1 = new FileInputStream(res.getLocalFile());
            is2 = new FileInputStream(paTreeNumFile);
            os = new FileOutputStream(fullTreeNumFile);

            IOUtils.copy(is1, os);
            IOUtils.copy(is2, os);

            LOG.debug(String.format(CREAT_COMPL_MSG_TEMPL, fullTreeNumFile));
        } catch (IOException e) {
            String err = String.format(CREAT_FAILED_MSG_TEMPL, fullTreeNumFile, e);
            LOG.error(err);
            throw new ProcessException(err);
        } finally {
            IOUtils.closeQuietly(is1);
            IOUtils.closeQuietly(is2);
            IOUtils.closeQuietly(os);
        }

        return fullTreeNumFile;
    }

    private static File addSuffix2FileName(File file, String suffix) {
        String[] components = file.getName().split("\\.", 2);
        return new File(file.getParent(), components[0] + suffix + components[1]);
    }

    private static void generateQualConf(String perlCmd,
                                         String perlScriptDir,
                                         String downloadDir,
                                         AnnualMeshResources resources) throws ProcessException {
        AnnualMeshResource res = resources.get(CochraneCMSPropertyNames.TERM2NUM_REMOTE_Q_YYYYBIN);

        LOG.debug("Creating meshqualifiers.config started");
        try {
            String cmd = perlCmd + " " + perlScriptDir + File.separator + "gen_qualifier_config.pl"
                    + " " + res.getLocalFile() + " " + downloadDir;
            executeCommand(cmd);

            LOG.debug("Creating meshqualifiers.config completed");
        } catch (Exception e) {
            String err = "Creating meshqualifiers.config completed with exception " + e;
            LOG.error(err, e);
            throw new ProcessException(err);
        }
    }

    private static void createTerm2num(String perlCmd,
                                       String perlSctiptDir,
                                       String downloadDir,
                                       File fullTreeNumFile) throws ProcessException {
        String meshConfigFile = downloadDir + File.separator + "meshqualifiers.config";
        String outputD = getPerlPathParameter(CochraneCMSPropertyNames.RESOURCES_TERM2NUM_OUTPUT);

        LOG.debug("Creating term2num.xml and term2num.xsl started");
        try {
            String cmd = perlCmd + " " + perlSctiptDir + File.separator
                    + "map_desc2treenum.pl" + " " + fullTreeNumFile
                    + " " + meshConfigFile + " " + outputD;
            executeCommand(cmd);

            LOG.debug("Creating term2num.xml and term2num.xsl completed");
        } catch (Exception e) {
            String err = "Creating term2num.xml and term2num.xsl completed with exception " + e;
            LOG.error(err, e);
            throw new ProcessException(err);
        }
    }

    private static String getPerlPathParameter(String pathProp) throws ProcessException {
        String path = getResourcePath(pathProp);
        try {
            return new File(path).getCanonicalPath();
        } catch (IOException e) {
            String err = String.format("Failed to get canonical representation of %s for perl command, %s", path, e);
            LOG.error(err, e);
            throw new ProcessException(err);
        }
    }

    private static void executeCommand(String command) throws Exception {
        try {
            com.wiley.tes.util.FileUtils.execCommand(command);
        } catch (Exception e) {
            throw new Exception(String.format("Failed to execute command {%s}, %s", command, e));
        }
    }

    private static String getResourcePath(String pathProp) {
        return CmsUtils.getUrlPathPart(CochraneCMSProperties.getProperty(pathProp));
    }

    public static String getLastCreationDate() {
        String retVal = "";
        String outputD = getResourcePath(CochraneCMSPropertyNames.RESOURCES_TERM2NUM_OUTPUT);
        String term2numFile = outputD + File.separator + "term2num.xml";

        File f = new File(term2numFile);
        if (f.exists()) {
            retVal = DateFormat.getDateInstance(DateFormat.LONG, Locale.ENGLISH).format(f.lastModified());
        }

        return retVal;
    }

    /**
     *
     */
    private static class AnnualMeshResources implements Iterable<AnnualMeshResource> {

        private static final String YEAR_STUB = "YYYY";

        private final String year;
        private final Map<String, AnnualMeshResource> resources = new HashMap<>();

        public AnnualMeshResources(String year) {
            this.year = year;
        }

        public void add(String pathProp, String checkZippedProp) {
            String remotePaths = CochraneCMSProperties.getProperty(pathProp).replace(YEAR_STUB, year);
            boolean checkZipped = StringUtils.isNotEmpty(checkZippedProp)
                    ? CochraneCMSProperties.getBoolProperty(checkZippedProp, false)
                    : false;
            AnnualMeshResource res = new AnnualMeshResource(remotePaths, checkZipped);
            resources.put(pathProp, res);
        }

        public AnnualMeshResource get(String pathProp) {
            return resources.get(pathProp);
        }

        public Iterator<AnnualMeshResource> iterator() {
            return resources.values().iterator();
        }
    }

    /**
     *
     */
    private static class AnnualMeshResource {

        private static final String PATH_SEPARATOR = ",";
        private final List<String> remotePaths = new ArrayList<>();
        private File localFile;
        private File zippedLocalFile;

        public AnnualMeshResource(String rawRemotePaths, boolean checkZipped) {
            setRemotePaths(rawRemotePaths, checkZipped);
            setLocalFiles();
        }

        private void setRemotePaths(String rawRemotePaths, boolean checkZipped) {
            for (String path : rawRemotePaths.split(PATH_SEPARATOR)) {
                if (checkZipped) {
                    remotePaths.add(FilenameUtils.removeExtension(path) + Extensions.ZIP);
                }
                remotePaths.add(path);
            }
        }

        private void setLocalFiles() {
            File downloadsDir = getDownloadsDir();
            for (String remotePath : remotePaths) {
                String fName = new File(remotePath).getName();
                if (remotePath.endsWith(Extensions.ZIP)) {
                    zippedLocalFile = new File(downloadsDir, fName);
                } else {
                    localFile = new File(downloadsDir, fName);
                }
            }
        }

        private File getDownloadsDir() {
            String path = CmsUtils.getUrlPathPart(
                    CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.TERM2NUM_DOWNLOADS));
            File dir = new File(path);
            dir.mkdirs();
            return dir;
        }

        public File getLocalFile() {
            return localFile;
        }

        public File getZippedLocalFile() {
            return zippedLocalFile;
        }

        public List<String> getRemotePaths() {
            return remotePaths;
        }
    }
}
