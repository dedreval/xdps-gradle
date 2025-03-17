package com.wiley.cms.cochrane.services;

import com.wiley.cms.cochrane.cmanager.FilePathCreator;
import com.wiley.cms.cochrane.cmanager.contentworker.RevmanPackage;
import com.wiley.cms.cochrane.converter.ml3g.JatsMl3gAssetsManager;
import com.wiley.cms.cochrane.repository.RepositoryFactory;
import com.wiley.cms.cochrane.repository.RepositoryUtils;
import com.wiley.cms.cochrane.utils.Constants;
import com.wiley.tes.util.Extensions;
import com.wiley.tes.util.FileUtils;
import com.wiley.tes.util.Logger;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.StringUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * @author <a href='mailto:aasadulin@wiley.com'>Alexandr Asadulin</a>
 * @version 13.09.2018
 */
public class PdfRenderingRestServiceZipParser {
    private static final Logger LOG = Logger.getLogger(PdfRenderingRestServiceZipParser.class);
    private static final String INCORRECT_PACKAGE_STRUCTURE = "Failed to extract zip %s. Incorrect package structure.";

    public ParsingResults parse(File zip, File destDir, boolean revman, boolean jats) {
        try {
            return tryParse(zip, destDir, revman, jats);
        } catch (Exception e) {
            return new ParsingResults(e.getMessage());
        }
    }

    private ParsingResults tryParse(File zip, File destDir, boolean revman, boolean jats)
            throws Exception {
        File tmpDir = createTmpDir(destDir);
        List<BaseDirAwareFile> extractedFiles = extractZip(zip, tmpDir);
        ParsingResults rawParsingResults = defineData(extractedFiles, revman, jats);
        ParsingResults parsingResults = moveDataToDestDir(rawParsingResults, destDir, revman);
        removeTmpData(tmpDir);
        return parsingResults;
    }

    private File createTmpDir(File destDir) throws IOException {
        try {
            return Files.createTempDirectory(destDir.toPath(), "tmp_").toFile();
        } catch (IOException e) {
            throw new IOException(String.format("Failed to create temporary directory in %s. %s", destDir, e));
        }
    }

    private List<BaseDirAwareFile> extractZip(File zip, File destDir) throws IOException {
        String zipName = zip.getName();
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zip))) {
            List<BaseDirAwareFile> extractedFiles = new ArrayList<>();
            ZipEntry ze;
            while ((ze = zis.getNextEntry()) != null) {
                String zeName = ze.getName();
                if (zeName.endsWith(Extensions.DAR) || zeName.endsWith(Extensions.ZIP)) {
                    File internalZip = extractFile(destDir, extractedFiles, zis, zeName);
                    extractInternalZip(internalZip, extractedFiles);

                } else if (!ze.isDirectory()) {
                    extractFile(destDir, extractedFiles, zis, zeName);
                }
            }
            return extractedFiles;
        } catch (IOException e) {
            LOG.error(INCORRECT_PACKAGE_STRUCTURE, zipName);
            throw new IOException(String.format(INCORRECT_PACKAGE_STRUCTURE, zipName));
        }
    }

    private void extractInternalZip(File internalZip, List<BaseDirAwareFile> extractedFiles)
            throws IOException {
        String internalZipName = internalZip.getName();
        boolean isAriesZip = internalZipName.endsWith(Extensions.ZIP);
        try (ZipInputStream izis = new ZipInputStream(new FileInputStream(internalZip))) {
            String cdNumber = RepositoryUtils.getFirstNameByFileName(internalZipName);
            ZipEntry ize;
            while ((ize = izis.getNextEntry()) != null) {
                if (ize.isDirectory()) {
                    continue;
                }
                String izeName = isAriesZip ? defineZipEntryName(ize.getName(), cdNumber) : ize.getName();
                extractFile(internalZip.getParentFile(), extractedFiles, izis, izeName);
            }
        } catch (IOException e) {
            LOG.error(INCORRECT_PACKAGE_STRUCTURE, internalZipName);
            throw new IOException(String.format(INCORRECT_PACKAGE_STRUCTURE, internalZipName));
        }
    }

    private String defineZipEntryName(String zeName, String cdNumber) {
        String izeName = zeName;
        if (izeName.endsWith(Constants.JATS_FINAL_EXTENSION)) {
            izeName = izeName.replace(Constants.JATS_FINAL_SUFFIX, "");

        } else if (izeName.endsWith(Constants.JATS_INPUT_EXTENSION)) {
            izeName = izeName.replace(Constants.JATS_INPUT_SUFFIX, "");

        } else if (FileUtils.isGraphicExtension("." + FilenameUtils.getExtension(izeName))) {
            izeName = cdNumber + Constants.JATS_FIG_DIR_SUFFIX + FilePathCreator.SEPARATOR + izeName;

        } else if (izeName.endsWith(Extensions.RM5)) {
            izeName = cdNumber + Constants.JATS_STATS_DIR_SUFFIX + FilePathCreator.SEPARATOR + izeName;
        }
        return izeName;
    }

    private File extractFile(File destDir, List<BaseDirAwareFile> extractedFiles, ZipInputStream zis, String zeName)
                             throws IOException {
        File file = new File(destDir, zeName);
        copyStreamToFile(zis, file);
        extractedFiles.add(new BaseDirAwareFile(destDir, file));
        return file;
    }

    private void copyStreamToFile(InputStream is, File file) throws IOException {
        file.getParentFile().mkdirs();
        Files.copy(is, file.toPath());
    }

    private ParsingResults defineData(List<BaseDirAwareFile> files, boolean revman, boolean jats)
            throws Exception {
        File sourceXml = findSourceXml(files);
        List<File> assets = findAssets(files, revman, jats, sourceXml);
        return new ParsingResults(sourceXml, assets);
    }

    private File findSourceXml(List<BaseDirAwareFile> files) throws Exception {
        String srcFileNamePtrn = "\\w\\w\\d{6}(-final|-input)?(\\.pub\\d+)?\\.xml";
        List<BaseDirAwareFile> srcXml = findFilesByNamePattern(files, srcFileNamePtrn);
        String checkReport = checkIfFoundExactlyOneFile(srcXml);
        if (!StringUtils.isEmpty(checkReport)) {
            throw new Exception(String.format("Source xml not found among %s. %s", files, checkReport));
        }
        return srcXml.get(0).getFile();
    }

    private List<File> findAssets(List<BaseDirAwareFile> files, boolean revman, boolean jats, File sourceXml)
            throws Exception {
        List<File> assets;
        if (revman) {
            assets = Collections.singletonList(findMetadataXml(files));
        } else if (jats) {
            List<String> assetsPaths = JatsMl3gAssetsManager.getRealAssetsPaths(sourceXml.getPath(),
                                                                                RepositoryFactory.getRepository());
            assets = assetsPaths.stream()
                    .map(RepositoryUtils::getRealFile)
                    .collect(Collectors.toList());
            assets.add(findArchiveFile(files));
        } else {
            assets = files.stream()
                    .map(BaseDirAwareFile::getFile)
                    .filter(file -> !file.equals(sourceXml))
                    .collect(Collectors.toList());
        }
        return assets;
    }

    private File findArchiveFile(List<BaseDirAwareFile> files) throws Exception {
        String archFileNamePtrn = "\\w\\w\\d{6}(\\.pub\\d+)?\\.(dar|zip)";
        List<BaseDirAwareFile> archFile = findFilesByNamePattern(files, archFileNamePtrn);
        String checkReport = checkIfFoundExactlyOneFile(archFile);
        if (!StringUtils.isEmpty(checkReport)) {
            throw new Exception(String.format("Archive with review not found among %s. %s", files,
                                              checkReport));
        }
        return archFile.get(0).getFile();
    }

    private File findMetadataXml(List<BaseDirAwareFile> files) throws Exception {
        List<BaseDirAwareFile> metadataXml = findFilesByNamePattern(files, RevmanPackage.METADATA_SOURCE);
        String checkReport = checkIfFoundExactlyOneFile(metadataXml);
        if (!StringUtils.isEmpty(checkReport)) {
            throw new Exception(String.format("Metadata xml not found among %s. %s", files, checkReport));
        }
        return metadataXml.get(0).getFile();
    }

    private ParsingResults moveDataToDestDir(ParsingResults rawResults, File destDir, boolean revman) {
        File newSrcXml = moveFileToDestDir(rawResults.sourceXml, destDir);
        List<File> newAssets;
        if (revman) {
            newAssets = Collections.singletonList(moveFileToDestDir(rawResults.assets.get(0), destDir));
        } else {
            File assetsBaseDir = new File(destDir, FilenameUtils.getBaseName(newSrcXml.getName()));
            newAssets = rawResults.assets.stream()
                    .map(f -> moveFileToDestDir(f, new File(assetsBaseDir, f.getParentFile().getName())))
                    .collect(Collectors.toList());
        }
        return new ParsingResults(newSrcXml, newAssets);
    }

    private File moveFileToDestDir(File srcFile, File destDir) {
        try {
            File destFile = new File(destDir, srcFile.getName());
            org.apache.commons.io.FileUtils.moveFile(srcFile, destFile);
            return destFile;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void removeTmpData(File tmpDir) {
        org.apache.commons.io.FileUtils.deleteQuietly(tmpDir);
    }

    private List<BaseDirAwareFile> findFilesByNamePattern(List<BaseDirAwareFile> inspFiles, String namePtrn) {
        return inspFiles.stream()
                       .filter(inspFile -> inspFile.getName().matches(namePtrn))
                       .collect(Collectors.toList());
    }

    private String checkIfFoundExactlyOneFile(List<BaseDirAwareFile> files) {
        String report = StringUtils.EMPTY;
        if (files.isEmpty()) {
            report = "No file satisfies the search parameters.";
        } else if (files.size() > 1) {
            report = "Several files satisfy the search parameters: " + files + ".";
        }
        return report;
    }

    /**
     *
     */
    private static class BaseDirAwareFile {
        final File baseDir;
        final File file;

        BaseDirAwareFile(File baseDir, File file) {
            this.baseDir = baseDir;
            this.file = file;
        }

        File getFile() {
            return file;
        }

        String getName() {
            return file.getName();
        }

        String getRelativePath() {
            return FilenameUtils.separatorsToUnix(baseDir.toPath().relativize(file.toPath()).toString());
        }

        @Override
        public String toString() {
            return getRelativePath();
        }
    }

    /**
     *
     */
    public static class ParsingResults {
        private String error;
        private File sourceXml;
        private List<File> assets;

        public ParsingResults(File sourceXml, List<File> assets) {
            this.sourceXml = sourceXml;
            this.assets = assets;
        }

        public ParsingResults(String error) {
            this.error = error;
        }

        public boolean isSuccessful() {
            return StringUtils.isEmpty(error);
        }

        public String getError() {
            return error;
        }

        public File getSourceXml() {
            return sourceXml;
        }

        public List<File> getAssets() {
            return assets;
        }

        public String getPubName() {
            return getAssets()
                           .stream()
                           .filter(asset -> asset.getName().endsWith(Extensions.DAR)
                                                    || asset.getName().endsWith(Extensions.ZIP))
                           .findFirst()
                           .map(File::getName)
                           .orElse(null);
        }

        public boolean isJatsAries() {
            return getAssets().stream()
                              .anyMatch(asset -> asset.getName().endsWith(Extensions.ZIP));
        }

        public boolean isJatsStatsPresent() {
            return getAssets().stream()
                              .anyMatch(asset -> asset.getName().endsWith(Extensions.RM5));
        }
    }
}
