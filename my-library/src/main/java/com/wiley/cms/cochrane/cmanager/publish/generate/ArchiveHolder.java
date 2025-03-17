package com.wiley.cms.cochrane.cmanager.publish.generate;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import org.apache.commons.io.IOUtils;

import com.wiley.cms.cochrane.cmanager.data.IResultsStorage;
import com.wiley.cms.cochrane.cmanager.data.PublishEntity;
import com.wiley.cms.cochrane.cmanager.entitywrapper.PublishWrapper;
import com.wiley.cms.cochrane.cmanager.publish.util.GenerationErrorCollector;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.repository.IRepository;
import com.wiley.cms.cochrane.utils.zip.GZipOutput;
import com.wiley.cms.cochrane.utils.zip.IZipOutput;
import com.wiley.cms.cochrane.utils.zip.ZipOutput;
import com.wiley.tes.util.Extensions;
import com.wiley.tes.util.Logger;


/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 10/6/2018
 */
public class ArchiveHolder {
    private static final Logger LOG = Logger.getLogger(ArchiveHolder.class);

    protected int curRecordCount = 0;
    protected int skippedRecordCount = 0;

    protected String pathToFolder;
    protected String archiveTemplate;

    protected String exportFileName;
    protected PublishEntity export;
    protected IZipOutput archive;

    private int batch = 0;

    protected final void init(String name, PublishEntity entity, String path, String template, String type) {
        export = entity;
        setExportFileName(name);

        pathToFolder = path;
        archiveTemplate = template;

        batch = BaseType.find(entity.getDb().getTitle()).get().getPubInfo(type).getBatch();
    }

    protected void init(PublishWrapper publish, PublishEntity entity, String path) {

        init(publish.getNewPackageName(), entity, path, publish.getPath().getArchive(),
                publish.getPath().getPubType().getId());

        publish.setPublishEntity(export);
    }

    protected void setExportFileName(String name) {
        exportFileName = name;
        export.setFileName(exportFileName);
    }

    protected void setArchive(IZipOutput archive) {
        this.archive = archive;
    }

    public String getExportFileName() {
        return exportFileName;
    }

    public PublishEntity getExport() {
        return export;
    }

    public String getFolder() {
        return pathToFolder;
    }

    public String getExportFilePath() {
        return exportFileName == null || exportFileName.isEmpty() ? null : pathToFolder + exportFileName;
    }

    public void closeArchive() {
        if (archive != null) {
            try {
                archive.close();
                LOG.debug("GEN > " + exportFileName);

            } catch (IOException ie) {
                LOG.error(ie);
            }
        }
    }

    public void addToArchive(List<ArchiveEntry> entries, GenerationErrorCollector errorCollector, IRepository rps)
            throws IOException {
        for (ArchiveEntry entry : entries) {
            addToArchive(entry, errorCollector);
        }
    }

    private void addToArchive(ArchiveEntry entry, GenerationErrorCollector errorCollector) throws IOException {
        InputStream is = null;
        try {
            if (entry.getContent() != null) {
                is = new ByteArrayInputStream(entry.getContent().getBytes(StandardCharsets.UTF_8));
            } else {
                File fl = new File(entry.getPathToContent());
                if (fl.exists()) {
                    is = new FileInputStream(fl);
                } else {
                    errorCollector.addError(GenerationErrorCollector.NotificationLevel.WARN,
                            ArchiveGenerator.generateNotExistsStr(entry.getPathToContent()));
                }
            }
            if (is != null) {
                archive.put(entry.getPathInArchive(), is);
            }

        } catch (IOException e) {
            LOG.error("Failed to add file to archive: [" + entry.getPathInArchive() + "]", e);
            throw e;

        } finally {
            IOUtils.closeQuietly(is);
        }
    }

    public void onRecordAdded() {
        curRecordCount++;
    }

    public void onRecordSkipped() {
        skippedRecordCount++;
    }

    public PublishEntity onGeneration(Date date, IRepository rps, IResultsStorage rs) {
        //if (archive == null) {
        //    return rs.setGenerating(export.getId(), false, true, 0);
        // }
        long size = archive == null ? 0 : getFile(getParentFile(rps), rps).length();
        return (date != null && export.isGenerating())
                ? rs.updatePublish(export.getId(), exportFileName, false, true, date, true, size)
                : rs.setGenerating(export.getId(), false, true, size);
    }

    public void onWhenReadyFail(String generateName, String exportTypeName, Exception e) {
        //if (export != null) {
        //    PublishHelper.logWhenReadyFail(export, generateName, exportTypeName, e);
        //}
    }

    protected void clearArchive(IRepository rps) {
        File fl = getFile(getParentFile(rps), rps);
        if (fl != null && fl.exists()) {
            fl.delete();
        }

        /*String archivePath = getExportFilePath();
        if (archivePath != null) {
            try {
                if (rps.isFileExists(archivePath)) {
                    rps.deleteDir(archivePath);
                }
            } catch (Exception e) {
                LOG.error("Failed to delete: [" + archivePath + "]", e);
            }
        }*/
    }

    protected void clearArchiveFolder(IRepository rps) {
        String archivePath = getFolder();
        try {
            if (rps.isFileExists(archivePath)) {
                rps.deleteDir(archivePath);
            }
        } catch (Exception e) {
            LOG.error("Failed to delete directory: [" + archivePath + "]", e);
        }
    }

    protected void initArchive(IRepository rps) throws Exception {
        File parent = getParentFile(rps);
        if (parent == null) {
            return;
        }
        File exportFile = getFile(parent, rps);
        if (exportFile != null) {
            String archivePath = exportFile.getAbsolutePath();
            if (archivePath.endsWith(Extensions.TAR_GZ)) {
                setArchive(new GZipOutput(exportFile, true));
            } else if (archivePath.endsWith(Extensions.ZIP)) {
                setArchive(new ZipOutput(exportFile));
            } else if (!archivePath.endsWith(Extensions.TXT)) {
                setArchive(new GZipOutput(exportFile, false));
            }
        }
    }

    protected File getFile(File parent, IRepository rps) {
        if (getExportFileName() != null && !getExportFileName().isEmpty()) {
            return parent == null
                    ? new File(rps.getRealFilePath(getFolder()), getExportFileName())
                    : new File(parent, getExportFileName());
        }
        return null;
    }

    protected File getParentFile(IRepository rps) {
        String folder = getFolder();
        if (folder != null && !folder.isEmpty()) {
            File fl = new File(rps.getRealFilePath(folder));
            fl.mkdirs();
            return fl;
        }
        return null;
    }

    public int getBatch() {
        return batch;
    }

    protected boolean isEmpty() {
        return curRecordCount == 0;
    }
}
