package com.wiley.cms.cochrane.converter.ml3g;

import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.FilePathBuilder;
import com.wiley.cms.cochrane.cmanager.FilePathCreator;
import com.wiley.cms.cochrane.cmanager.contentworker.JatsPackage;
import com.wiley.cms.cochrane.cmanager.data.record.IRecord;
import com.wiley.cms.cochrane.cmanager.data.record.RecordManifest;
import com.wiley.cms.cochrane.cmanager.data.rendering.RenderingPlan;
import com.wiley.cms.cochrane.cmanager.entitymanager.AbstractManager;
import com.wiley.cms.cochrane.repository.IRepository;
import com.wiley.cms.cochrane.repository.RepositoryUtils;
import com.wiley.cms.cochrane.utils.Constants;
import com.wiley.cms.cochrane.utils.ContentLocation;
import com.wiley.tes.util.Extensions;
import com.wiley.tes.util.Logger;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.apache.commons.lang.CharEncoding;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * @author <a href='mailto:aasadulin@wiley.com'>Alexander Asadulin</a>
 * @version 04.04.2014
 */
public class Ml3gAssetsManager {

    private static final Logger LOG = Logger.getLogger(Ml3gAssetsManager.class);

    private static final IRepository REPOSITORY = AbstractManager.getRepository();

    private Ml3gAssetsManager() {
    }

    public static List<String> collectIssueAssets(int issueId, String dbName, IRecord recVO, StringBuilder errs) {
        return new IssueAssetCollector(issueId, dbName, recVO).getAssetsUris(errs);
    }

    public static List<String> collectAssets(String dbName,
                                             IRecord recVO,
                                             ContentLocation contLocation,
                                             int version,
                                             StringBuilder errs) {
        AssetCollector assetCollector;
        if (contLocation == ContentLocation.ENTIRE) {
            assetCollector = new EntireAssetCollector(dbName, recVO);
        } else {
            assetCollector = new PreviousAssetCollector(dbName, recVO, version);
        }

        List<String> assetsUris = assetCollector.getAssetsUris(errs);

        return assetsUris;
    }

    public static List<String> getAssetsRelativeUris(List<String> assetsUris, String recName) {
        List<String> uris = new ArrayList<String>(assetsUris.size());
        for (String assetUri : assetsUris) {
            uris.add(getAssetRelativeUri(assetUri, recName));
        }

        return uris;
    }

    public static String getAssetRelativeUri(String assetUri, String recName) {
        int idx = assetUri.indexOf(recName) + recName.length() + 1;

        return assetUri.substring(idx);
    }

    public static void copyAssetsFromOneLocation2Another(String dbName,
                                                         int issueId,
                                                         String recName,
                                                         int version,
                                                         ContentLocation srcContentLocation,
                                                         ContentLocation destContentLocation,
                                                         StringBuilder errs) {
        StringBuilder tmpErrs = new StringBuilder();
        String srcAssetsFileUri = getAssetsFileUri(dbName, issueId, recName, version, srcContentLocation);
        String destAssetsFileUri = getAssetsFileUri(dbName, issueId, recName, version, destContentLocation);
        List<String> srcAssetsUris = getAssetsUris(srcAssetsFileUri, tmpErrs);
        List<String> destAssetsUris;

        if (tmpErrs.length() == 0) {
            destAssetsUris = getAssetsPathsOfContentLocation(srcAssetsUris, dbName, recName, version,
                    srcContentLocation, destContentLocation);
            OutputStream os = null;
            try {
                os = new FileOutputStream(new File(REPOSITORY.getRealFilePath(destAssetsFileUri)));
                IOUtils.writeLines(destAssetsUris, "\n", os, CharEncoding.UTF_8);
            } catch (Exception e) {
                tmpErrs.append(e);
            } finally {
                IOUtils.closeQuietly(os);
            }
        }

        if (tmpErrs.length() > 0) {
            errs.append("Failed to copy issues assets [").append(srcAssetsFileUri).append("] to [")
                    .append(destAssetsFileUri).append("], ").append(tmpErrs);
        }
    }

    private static String getAssetsFileUri(String dbName,
                                           int issueId,
                                           String recName,
                                           int version,
                                           ContentLocation contentLocation) {
        String assetsFileUri;
        if (contentLocation == ContentLocation.ISSUE) {
            assetsFileUri = FilePathCreator.getFilePathForMl3gAssets(issueId, dbName, recName);
        } else if (contentLocation == ContentLocation.ISSUE_COPY) {
            assetsFileUri = FilePathCreator.getFilePathForMl3gAssetsCopy(issueId, dbName, recName);
        } else if (contentLocation == ContentLocation.ENTIRE) {
            assetsFileUri = FilePathCreator.getFilePathForEntireMl3gAssets(dbName, recName);
        } else {
            assetsFileUri = FilePathCreator.getPreviousMl3gAssetsPath(recName, version);
        }

        return assetsFileUri;
    }

    private static List<String> getAssetsUris(String assetsFileUri, StringBuilder errs) {
        List<String> uris;
        InputStream is = null;
        try {
            is = REPOSITORY.getFile(assetsFileUri);
            uris = IOUtils.readLines(is, StandardCharsets.UTF_8);
        } catch (Exception e) {
            errs.append("Failed to get assets list from file [").append(assetsFileUri).append("]\n");
            uris = null;
        } finally {
            IOUtils.closeQuietly(is);
        }

        return uris;
    }

    private static List<String> findUrisInBackupFolder(String dbName, int issueId, String cdNumber, List<String> uris) {
        List<String> ret = new ArrayList<>(uris.size());
        uris.forEach(uri -> ret.add(findUriInBackupFolder(dbName, issueId, cdNumber, uri)));
        return ret;
    }

    private static String findUriInBackupFolder(String dbName, int issueId, String cdNumber, String uriLine) {
        String uriFromBackUp = null;
        String[] parts = uriLine.split(",");
        String uri = parts[0];

        String fileName = RepositoryUtils.getLastNameByPath(uri);
        String extension = "." + FilenameUtils.getExtension(fileName);
        
        if (Extensions.RM5.equals(extension)) {
            uriFromBackUp  = findFileInFolder(FilePathBuilder.JATS.getPathToBackupStatsDir(
                    issueId, dbName, cdNumber), fileName);
            if (uriFromBackUp == null) {
                uriFromBackUp  = findFileInFolder(FilePathBuilder.getPathToBackupStatsDir(
                    issueId, dbName, cdNumber), fileName);
            }
        } else if (com.wiley.tes.util.FileUtils.isGraphicExtension(extension)) {
            uriFromBackUp  = findFileInFolder(FilePathBuilder.JATS.getPathToBackupFiguresDir(
                    issueId, dbName, cdNumber), fileName);
            if (uriFromBackUp == null) {
                uriFromBackUp = findFileInFolder(FilePathBuilder.JATS.getPathToBackupThumbnailsDir(
                        issueId, dbName, cdNumber), fileName);
            }
            if (uriFromBackUp == null) {
                uriFromBackUp = findFileInFolder(FilePathBuilder.getPathToBackupFiguresDir(
                        issueId, dbName, cdNumber), fileName);
            }
            if (uriFromBackUp == null) {
                uriFromBackUp = findFileInFolder(FilePathBuilder.getPathToBackupThumbnailsDir(
                        issueId, dbName, cdNumber), fileName);
            }
            if (uriFromBackUp == null) {
                uriFromBackUp = findFileInFolder(FilePathBuilder.getPathToBackupHtmlFiguresDir(
                        issueId, dbName, cdNumber), fileName);
            }
            if (uriFromBackUp == null) {
                uriFromBackUp = findFileInFolder(FilePathBuilder.getPathToBackupHtmlThumbnailsDir(
                        issueId, dbName, cdNumber), fileName);
            }
        }
        return (uriFromBackUp != null) ? (parts.length == 1 ? uriFromBackUp : uriFromBackUp + "," + parts[1]) : uriLine;
    }

    private static String findFileInFolder(String folderPath, String fileName) {
        File[] files = REPOSITORY.getFilesFromDir(folderPath);
        if (files != null) {
            for (File file: files) {
                if (file.getName().equals(fileName)) {
                    return folderPath + FilePathCreator.SEPARATOR + fileName;
                }
            }
        }
        return null;
    }

    private static List<String> getAssetsPathsOfContentLocation(List<String> srcAssetsUris,
                                                                String dbName,
                                                                String recName,
                                                                int version,
                                                                ContentLocation srcContentLocation,
                                                                ContentLocation destContentLocation) {
        if (srcContentLocation == ContentLocation.ISSUE_COPY) {
            return srcAssetsUris;
        }

        String srcPathPrefix;
        String pdfFopPathPrefix;
        String htmlPathPrefix;
        if (destContentLocation == ContentLocation.ENTIRE) {
            srcPathPrefix = isJatsAssets(srcAssetsUris)
                                    ? FilePathCreator.getFilePathToJatsEntireRecord(dbName, recName)
                                    : FilePathCreator.getFilePathToSourceEntire(dbName, recName);
            pdfFopPathPrefix = FilePathCreator.getRenderedDirPathEntire(dbName, recName, RenderingPlan.PDF_FOP);
            htmlPathPrefix = FilePathCreator.getRenderedDirPathEntire(dbName, recName, RenderingPlan.HTML);
        } else {
            srcPathPrefix = isJatsAssets(srcAssetsUris)
                                    ? FilePathCreator.getFilePathToJatsPreviousRecord(version, recName)
                                    : FilePathCreator.getPreviousSrcPath(recName, version);
            pdfFopPathPrefix = FilePathCreator.getRenderedDirPathPrevious(version, recName, RenderingPlan.PDF_FOP);
            htmlPathPrefix = FilePathCreator.getRenderedDirPathPrevious(version, recName, RenderingPlan.HTML);
        }
        srcPathPrefix = srcPathPrefix.substring(0, srcPathPrefix.indexOf(recName));
        pdfFopPathPrefix = pdfFopPathPrefix.substring(0, pdfFopPathPrefix.indexOf(recName));
        htmlPathPrefix = htmlPathPrefix.substring(0, htmlPathPrefix.indexOf(recName));

        boolean inIssue = srcContentLocation == ContentLocation.ISSUE;
        String pdfFopDir = FilePathCreator.getRenderingPlanDirName(RenderingPlan.PDF_FOP, inIssue);
        String htmlDir = FilePathCreator.getRenderingPlanDirName(RenderingPlan.HTML, inIssue);

        String pdfFopDirEntire = FilePathCreator.getRenderingPlanDirName(RenderingPlan.PDF_FOP, false);
        String htmlDirEntire = FilePathCreator.getRenderingPlanDirName(RenderingPlan.HTML, false);

        List<String> destAssetsUris = new ArrayList<>(srcAssetsUris.size());
        for (String srcAssetUri : srcAssetsUris) {
            String prefix;
            if (srcAssetUri.contains(pdfFopDir) || srcAssetUri.contains(pdfFopDirEntire)) {
                prefix = pdfFopPathPrefix;
            } else if (srcAssetUri.contains(htmlDir) || srcAssetUri.contains(htmlDirEntire)) {
                prefix = htmlPathPrefix;
            } else {
                prefix = srcPathPrefix;
            }
            int ind = srcAssetUri.indexOf(recName);
            destAssetsUris.add(prefix + srcAssetUri.substring(ind));
        }

        return destAssetsUris;
    }

    private static boolean isJatsAssets(List<String> srcAssetsUris) {
        return !srcAssetsUris.isEmpty() && srcAssetsUris.get(0).contains(JatsPackage.JATS_FOLDER);
    }

    public static List<String> getAssetsUris(String dbName,
                                             int issueId,
                                             String recName,
                                             int version,
                                             ContentLocation contentLocation,
                                             StringBuilder errs) {
        return getAssetsUris(dbName, issueId, recName, version, contentLocation, false, errs);
    }

    public static List<String> getAssetsUris(String dbName,
                                             int issueId,
                                             String recName,
                                             int version,
                                             ContentLocation contentLocation, boolean takeFromBackup,
                                             StringBuilder errs) {
        String assetsFileUri = getAssetsFileUri(dbName, issueId, recName, version, contentLocation);
        return takeFromBackup ? findUrisInBackupFolder(dbName, issueId, recName, getAssetsUris(assetsFileUri, errs))
                : getAssetsUris(assetsFileUri, errs);
    }

    public static String getAssetsFileContent(List<String> assetsUris) {
        StringBuilder content = new StringBuilder();
        for (String assetUri : assetsUris) {
            content.append(assetUri).append("\n");
        }
        return content.toString();
    }

    /**
     *
     */
    private abstract static class AssetCollector {

        private static final String[] IMAGE_DIRS = CochraneCMSPropertyNames.getImageDirectories().split(",");
        protected final String dbName;
        protected final IRecord recVO;

        public AssetCollector(String dbName, IRecord recVO) {
            this.dbName = dbName;
            this.recVO = recVO;
        }

        public abstract List<String> getAssetsUris(StringBuilder errs);

        protected boolean isImageDir(String uri) {
            for (String imageDir : IMAGE_DIRS) {
                if (uri.contains(imageDir)) {
                    return true;
                }
            }

            return false;
        }
    }

    /**
     *
     */
    private static class IssueAssetCollector extends AssetCollector {

        private static final List<String> GROUPS = new ArrayList<String>() {
            {
                add(Constants.SOURCE);
                add(Constants.RENDERED_HTML_DIAMOND);
                add(Constants.RENDERED_PDF_FOP);
            }
        };

        private int issueId;
        private String dbName;

        public IssueAssetCollector(int issueId, String dbName, IRecord recVO) {
            super(dbName, recVO);
            this.issueId = issueId;
            this.dbName = dbName;
        }

        public List<String> getAssetsUris(StringBuilder errs) {
            RecordManifest resources = new RecordManifest(issueId, dbName, recVO.getName(), recVO.getRecordPath());
            List<String> uris = new ArrayList<>();
            if (uris == null || uris.isEmpty()) {
                LOG.trace("Assets list is empty");
            }
            for (String uri : uris) {
                LOG.trace("Asset uri is " + uri);
            }
            for (String group : GROUPS) {
                List<String> specUris = resources.getUris(group);
                for (String specUri : specUris) {
                    if (specUri.endsWith(Extensions.PDF) || isImageDir(specUri)) {
                        uris.add(specUri);
                    }
                }
            }
            return uris;
        }
    }

    /**
     *
     */
    private static class EntireAssetCollector extends AssetCollector {

        private final IOFileFilter ff = new IOFileFilter() {
            public boolean accept(File file) {
                return isValid(file);
            }

            public boolean accept(File file, String s) {
                return accept(file);
            }
        };

        public EntireAssetCollector(String dbName, IRecord recVO) {
            super(dbName, recVO);
        }

        public List<String> getAssetsUris(StringBuilder errs) {
            List<File> assetsFiles = getAssetsFiles();
            List<String> uris;
            if (assetsFiles.isEmpty()) {
                uris = null;
            } else {
                uris = new ArrayList<>(assetsFiles.size());
                for (File path : assetsFiles) {
                    uris.add(path.getPath());
                }
            }

            return uris;
        }

        private List<File> getAssetsFiles() {
            List<File> paths = getPaths();
            List<File> assetsFiles = new ArrayList<>();

            for (File path : paths) {
                if (!path.exists()) {
                    continue;
                }
                if (path.isFile()) {
                    if (isValid(path)) {
                        assetsFiles.add(path);
                    }
                } else {
                    assetsFiles.addAll(FileUtils.listFiles(path, ff, TrueFileFilter.INSTANCE));
                }
            }

            return assetsFiles;
        }

        protected List<File> getPaths() {
            String recName = recVO.getName();

            List<File> paths = new ArrayList<>();
            paths.add(new File(REPOSITORY.getRealFilePath(
                    FilePathCreator.getFilePathForEnclosureEntire(dbName, recName, ""))));
            paths.add(new File(REPOSITORY.getRealFilePath(
                    FilePathCreator.getRenderedDirPathEntire(dbName, recName, RenderingPlan.HTML))));
            paths.add(new File(REPOSITORY.getRealFilePath(
                    FilePathCreator.getRenderedDirPathEntire(dbName, recName, RenderingPlan.PDF_FOP))));

            return paths;
        }

        private boolean isValid(File file) {
            return (isImageDir(file.getPath()) || file.getName().endsWith(Extensions.PDF));
        }
    }

    /**
     *
     */
    private static class PreviousAssetCollector extends EntireAssetCollector {

        private final int version;

        PreviousAssetCollector(String dbName, IRecord recVO, final int version) {
            super(dbName, recVO);
            this.version = version;
        }

        protected List<File> getPaths() {
            String recName = recVO.getName();

            List<File> paths = new ArrayList<>();
            paths.add(new File(REPOSITORY.getRealFilePath(
                    FilePathCreator.getPreviousFilePathForEnclosure(version, recName, ""))));
            paths.add(new File(REPOSITORY.getRealFilePath(
                    FilePathCreator.getPreviousRenderedDirPath(recName, version, RenderingPlan.HTML))));
            paths.add(new File(REPOSITORY.getRealFilePath(
                    FilePathCreator.getPreviousRenderedDirPath(recName, version, RenderingPlan.PDF_FOP))));

            return paths;
        }
    }
}
