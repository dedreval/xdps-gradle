package com.wiley.cms.cochrane.repository;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

import com.wiley.cms.cochrane.cmanager.FilePathBuilder;
import com.wiley.cms.cochrane.utils.Constants;
import com.wiley.cms.cochrane.utils.zip.IZipOutput;
import com.wiley.cms.cochrane.utils.zip.ZipOutput;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;

import com.wiley.cms.cochrane.cmanager.CochraneCMSProperties;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.DeliveryPackageInfo;
import com.wiley.cms.cochrane.cmanager.FilePathCreator;
import com.wiley.tes.util.Extensions;
import com.wiley.tes.util.FileUtils;
import com.wiley.tes.util.Logger;

import static com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames.getCDSRDbName;

/**
 * Type comments here.
 *
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @version 1.0
 */
public class RepositoryUtils {
    public static final FilenameFilter PDF_FF = (dir, name) -> name.contains(Extensions.PDF);
    public static final FilenameFilter SVG_FF = (dir, name) -> name.contains(Extensions.SVG);
    public static final FilenameFilter RM5_FF = (dir, name) -> name.contains(Extensions.RM5);
    public static final FileFilter ZIP_FF = (file) -> file.isFile() && file.getName().endsWith(Extensions.ZIP);

    private static final Logger LOG = Logger.getLogger(RepositoryUtils.class);
    private static final int DELAY_CREATE_DIRS = 1000;

    private static final String FILE_PREFIX = "file://";
    private static final String REPOSITORY_PREFIX = FILE_PREFIX + "/";
    private static final String TEMP_DAR_DIR = "temp_dar/";
    private static final String OLD_FILESYSTEM_ROOT = "/CT/content/";

    private RepositoryUtils() {
    }

    public static String buildURIPath(String path) {
        return FILE_PREFIX + path;
    }

    /**
     * Construct path to file at the file system
     *
     * @param filesystemRoot root dir
     * @param uriString      repository URI
     * @return file instance
     */
    public static File getRealFile(String filesystemRoot, String uriString) {
        LOG.trace(String.format("Getting real file for filesystemRoot=%s uriString=%s", filesystemRoot, uriString));
        URI uri;
        try {
            String separator = "/";
            if (uriString.startsWith("/")) {
                separator = "";
            }
            String uriReplaced = uriString.replace("\\", "/");
            if (uriReplaced.startsWith(OLD_FILESYSTEM_ROOT)) {
                LOG.trace(String.format("URL before replace uriString=%s", uriReplaced));
                uriReplaced = uriReplaced.substring(OLD_FILESYSTEM_ROOT.length());
                LOG.trace(String.format("URL after replace uriString=%s", uriReplaced));
                LOG.trace(String.format("separator before =%s", separator));
                separator = "/";
                LOG.trace(String.format("separator after =%s", separator));
            }
            if (!filesystemRoot.contains(REPOSITORY_PREFIX)) {
                String prefix = REPOSITORY_PREFIX;
                if (filesystemRoot.startsWith("/")) {
                    prefix = FILE_PREFIX;
                }
                if (uriReplaced.contains(filesystemRoot)) {
                    uri = new URI(prefix + separator + uriReplaced);
                } else {
                    uri = new URI(prefix + filesystemRoot + separator + uriReplaced);
                }
            } else {
                if (uriReplaced.contains(filesystemRoot.replace(REPOSITORY_PREFIX, ""))) {
                    uri = new URI(FILE_PREFIX + separator + uriReplaced);
                } else {
                    uri = new URI(filesystemRoot + separator + uriReplaced);
                }
            }

        } catch (URISyntaxException e) {
            LOG.error(e.getMessage());
            return null;
        }
        LOG.trace(String.format("URI for filesystemRoot=%s uriString=%s is %s",
                filesystemRoot, uriString, uri.getPath()));
        return new File(uri);
    }

    public static String getRepositoryPath(File realFile) {
        String prefix  = CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.PREFIX_REPOSITORY, "");
        String abcPath = realFile.getAbsolutePath();
        int ind = abcPath.indexOf(prefix);
        return abcPath.substring(ind);
    }

    public static File getRealFile(String relativePath) {
        String filesystemRoot = getSystemRoot();
        return getRealFile(filesystemRoot, relativePath);
    }

    public static File createDirectory(String dirPath) {

        String filesystemRoot = getSystemRoot();
        File file = getRealFile(filesystemRoot, dirPath);
        if (!file.exists()) {
            boolean ret = file.mkdirs();
            if (!ret) {
                LOG.error("Can't create directory " + file.getAbsolutePath());
                return null;
            }
        }
        return file;
    }

    public static File createFile(String filePath) {
        String filesystemRoot = getSystemRoot();

        File file = getRealFile(filesystemRoot, filePath);

        File f = getRealFile(filesystemRoot, filePath.substring(0, filePath.lastIndexOf("/")));
        if (!f.mkdirs()) {
            try {
                Thread.sleep(DELAY_CREATE_DIRS);
            } catch (InterruptedException e) {
                LOG.debug(e.getMessage(), e);
            }

            f.mkdirs(); // repeat of the creating dirs
        }
        // File file=new File(filePath);
        try {
            file.createNewFile();
        } catch (IOException e) {
            LOG.error("Can't create file " + file.getAbsolutePath());
        }
        return file;
    }

    public static String getRecordNameByPath(String path) {
        return getRecordNameByPath(path, Extensions.XML, Extensions.RETRACTED);
    }

    public static String getLastNameByPath(String path) {
        return FilenameUtils.getName(path);
    }

    private static String getRecordNameByPath(String path, String ... extensions) {
        for (String ext: extensions) {
            if (path.endsWith(ext)) {
                return path.substring(path.lastIndexOf(FilePathCreator.SEPARATOR) + 1, path.length() - ext.length());
            }
        }
        LOG.error("Can't parse record name from path: " + path);
        return getLastNameByPath(path);
    }

    public static String getFolderPathByPath(String path, boolean addSeparator) {
        int ind = path.lastIndexOf(FilePathCreator.SEPARATOR);
        return path.substring(0, addSeparator ? ind + 1 : ind);
    }

    public static String getFirstNameByFileName(String fileName) {
        String[] ret = fileName.split("\\.");
        if (ret.length == 0) {
            LOG.warn("Cannot get a first name from " + fileName);
            return fileName;
        }
        return ret[0];
    }

    public static String getRecordNameByFileName(String fileName) {

        if (!fileName.endsWith(Extensions.XML)) {

            LOG.warn("Cannot parse record name from " + fileName);
            return fileName;
        }
        return FileUtils.cutExtension(fileName, Extensions.XML);
    }

    public static String getSystemRoot() {
        return CochraneCMSProperties.getProperty("filesystem.root",
            System.getProperty("jboss.server.config.url") + "/repository/");
    }

    public static void copyFile(String destFilePath, String url, String srcRepository, String srcWebUrl, IRepository rp)
        throws IOException {

        InputStream bis = null;
        try {
            bis = new BufferedInputStream(srcWebUrl == null ? new FileInputStream(getRealFile(srcRepository, url))
                : new URL(srcWebUrl + url).openStream());
            rp.putFile(destFilePath, bis);
        } finally {
            if (bis != null) {
                bis.close();
            }
        }
    }

    public static boolean copyFile(String srcPath, String destPath, boolean mustBe, IRepository rp) throws IOException {
        boolean ret = rp.isFileExistsQuiet(srcPath);
        if (ret || mustBe) {
            rp.putFile(destPath, rp.getFile(srcPath));
        }
        return ret;
    }

    public static void copyDir(String recName, String dirTo, String dirFrom, DeliveryPackageInfo di,
                               IRepository rp) throws IOException {
        File[] files = rp.getFilesFromDir(dirFrom);
        if (files == null) {
            return;
        }
        for (File f : files) {
            String path = dirTo + FilePathCreator.SEPARATOR + f.getName();
            if (f.isDirectory()) {
                copyDir(recName, path, f.getAbsolutePath(), di, rp);
            } else {
                rp.putFile(path, rp.getFile(f.getAbsolutePath()));
                di.addFile(recName, path);
            }
        }
    }

    public static void deleteFile(String path, boolean isFile, IRepository rp) throws Exception {
        try {
            if (!rp.isFileExistsQuiet(path)) {
                return;
            }
            if (isFile) {
                rp.deleteFile(path);
            } else {
                rp.deleteDir(path);
            }
        } catch (FileNotFoundException e) {
            LOG.error(e, e);
        }
    }

    public static void deleteFile(String path, IRepository rp) throws IOException {
        if (rp.isFileExistsQuiet(path)) {
            rp.deleteFile(path);
            LOG.debug(String.format("%s is removing", path));
        }
    }

    public static void deleteDir(String path, IRepository rp) throws Exception {
        if (rp.isFileExistsQuiet(path)) {
            rp.deleteDir(path);
            LOG.debug(String.format("%s folder is removing", path));
        }
    }

    public static void deleteDirQuietly(String path, IRepository rp) {
        try {
            deleteDir(path, rp);
        } catch (Exception e) {
            LOG.error(e.getMessage());
        }
    }

    public static void replaceFiles(String pathFrom, String pathTo, boolean deleteFrom, IRepository rp) {
        InputStream is = null;
        try {
            if (rp.isFileExists(pathFrom)) {
                is = rp.getFile(pathFrom);
            }
            deleteFile(pathTo, rp);
            if (is != null) {
                rp.putFile(pathTo, is, true);
                if (deleteFrom) {
                    rp.deleteFile(pathFrom);
                }
            }
        } catch (IOException e) {
            LOG.error(e);
        } finally {
            IOUtils.closeQuietly(is);
        }
    }

    public static void addFolderPaths(String basePath, List<String> paths, FilenameFilter filter) {
        addFolderPaths(basePath, paths, filter, RepositoryFactory.getRepository());
    }

    public static void addFolderPaths(String basePath, List<String> paths, IRepository rp) {
        addFolderPaths(basePath, paths, null, rp);
    }

    public static void addFolderPaths(String basePath, List<String> paths, FilenameFilter filter, IRepository rp) {
        File[] files = filter == null ? rp.getFilesFromDir(basePath)
                : new File(rp.getRealFilePath(basePath)).listFiles(filter);
        if (files == null) {
            return;
        }
        for (File fl: files) {
            if (fl.isDirectory()) {
                addFolderPaths(basePath + fl.getName() + FilePathCreator.SEPARATOR, paths, filter, rp);
            } else {
                paths.add(basePath + fl.getName());
            }
        }
    }

    public static String getFileSystemRoot() {

        String repository = RepositoryUtils.getSystemRoot();
        if (!repository.startsWith(REPOSITORY_PREFIX)) {
            repository = REPOSITORY_PREFIX + repository;
        }
        return repository;
    }

    public static File createDarPackageWithReview(String pubName, String dirFrom, int issueId) {
        File tempDar = null;
        try (Stream<Path> paths = Files.walk(Paths.get(dirFrom))) {
            String cdsrDbName = getCDSRDbName();
            tempDar = createFile((issueId != 0 ? FilePathBuilder.getPathToIssueExport(issueId, cdsrDbName)
                                                    : FilePathBuilder.getPathToEntireExport(cdsrDbName))
                                                    + TEMP_DAR_DIR + pubName + Extensions.DAR);
            IZipOutput out = new ZipOutput(tempDar);

            paths.filter(path -> Files.isRegularFile(path)
                               && !path.getParent().getFileName().toString().endsWith(Constants.JATS_TMBNLS_DIR_SUFFIX))
                    .map(Path::toFile)
                    .forEach(file -> {
                            try {
                                String parentFileName = file.getParentFile().getName();
                                String fileName = file.getName();
                                if (parentFileName.endsWith(Constants.JATS_FIG_DIR_SUFFIX)
                                            || parentFileName.endsWith(Constants.JATS_STATS_DIR_SUFFIX)) {
                                    out.put(parentFileName + FilePathCreator.SEPARATOR + fileName,
                                            new FileInputStream(file));
                                } else {
                                    out.put(fileName, new FileInputStream(file));
                                }
                            } catch (IOException e) {
                                LOG.error("can't create file stream " + file.getAbsolutePath());
                            }
                        });
            out.close();
        } catch (IOException e) {
            LOG.error("can't create dar package for directory " + dirFrom);
        }
        return tempDar;
    }

    public static File createDarPackageWithTa(String pubName, String langName, String pathFrom) {
        File tempDar = null;
        try {
            tempDar = createFile(FilePathBuilder.getPathToEntireExport(getCDSRDbName()) + TEMP_DAR_DIR + pubName
                                         + "." + langName + Extensions.DAR);
            ZipOutput out = new ZipOutput(tempDar);

            File file = getRealFile(pathFrom);
            if (file != null) {
                out.put(file.getName(), new FileInputStream(file));
            }

            out.close();

        } catch (IOException e) {
            LOG.error("can't create dar package for path " + pathFrom);
        }

        return tempDar;
    }
}
