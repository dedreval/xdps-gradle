package com.wiley.cms.cochrane.cmanager.export.process;

import com.wiley.cms.cochrane.cmanager.CmsException;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.FilePathBuilder;
import com.wiley.cms.cochrane.cmanager.FilePathCreator;
import com.wiley.cms.cochrane.cmanager.MessageSender;
import com.wiley.cms.cochrane.cmanager.data.entire.EntireDBEntity;
import com.wiley.cms.cochrane.cmanager.data.entire.EntireDBStorageFactory;
import com.wiley.cms.cochrane.cmanager.data.entire.IEntireDBStorage;
import com.wiley.cms.cochrane.cmanager.data.record.RecordManifest;
import com.wiley.cms.cochrane.cmanager.data.rendering.RenderingPlan;
import com.wiley.cms.cochrane.cmanager.publish.PublishHelper;
import com.wiley.cms.cochrane.cmanager.publish.generate.ArchiveEntry;
import com.wiley.cms.cochrane.converter.ml3g.Ml3gXmlAssets;
import com.wiley.cms.cochrane.utils.Constants;
import com.wiley.cms.cochrane.utils.ContentLocation;
import com.wiley.cms.process.entity.DbEntity;
import com.wiley.tes.util.Extensions;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.apache.commons.lang.StringUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author <a href='mailto:ikirov@wiley.com'>Igor Kirov</a>
 * @version 25.01.2010
 */
public class EntireExporter extends Exporter {
    private final IEntireDBStorage edbs = EntireDBStorageFactory.getFactory().getInstance();

    private String dbName;

    public EntireExporter(ExportParameters params, Set<Integer> items, String filePath) throws CmsException {
        super(params, items, filePath);
    }

    @Override
    protected void init(ExportParameters params) {

        init();
        dbName = params.getDbName();

        if (params.isRenderedText()) {
            contents.add(new RenderedText());
        }
        if (params.isRenderedGraphics()) {
            contents.add(new RenderedGraphics());
        }
        if (params.isSourceGraphics()) {
            contents.add(new SourceGraphics());
        }
        if (params.isXmlSource()) {
            contents.add(new XmlSource());
        }
        if (params.isRenderedPdf()) {
            contents.add(new RenderedPdf());
        }
        if (params.isDvd()) {
            contents.add(new Dvd());
        }
        if (params.isMl3gForIpad()) {
            contents.add(new Ml3gForIpad());
        }

        if (params.isRevManForIPackage()) {
            packageContents.add(new RevmanPackageContent(0, 0, dbName));
        }
        if (params.isWml21ForIPackage()) {
            packageContents.add(new WML21PackageContent(0, 0, dbName));
        }
        if (params.isTaForIPackage()) {
            packageContents.add(new TranslationPackageContent(0, 0, dbName, false));
            packageContents.add(new TranslationPackageContent(0, 0, dbName, true));
        }
        if (params.isJatsForIPackage()) {
            packageContents.add(new JatsPackageContent(0, 0, dbName));
        }
    }

    protected void export(int id) {
        EntireDBEntity entity = edbs.findRecord(id);
        for (IContentExporter content : contents) {
            entries.addAll(content.process(entity));
        }

        for (PackageContent pc : packageContents) {
            pc.export(pc.process(entity));
        }
    }

    /**
     *
     */
    private abstract class EntireContent implements IContentExporter {
        private static final String EXPORT_ERR_TEMPL = "Can't perform export {%s} for review [%s]. %s\n";
        protected String type;
        protected StringBuilder errs;

        protected List<ArchiveEntry> checkAssets(List<ArchiveEntry> entries, String entityName) {
            StringBuilder tempErrs = new StringBuilder();
            final boolean[] success = new boolean[1];
            if (entries.isEmpty()) {
                tempErrs.append("Contents for this type of export is not available. ");
            }
            entries.forEach(entry -> {
                    try {
                        success[0] = rps.isFileExists(entry.getPathToContent());
                    } catch (Exception e) {
                        success[0] = false;
                    }
                    if (!success[0]) {
                        tempErrs.append("File [").append(entry.getPathToContent()).append("] doesn't exist. ");
                    }
                });
            if (tempErrs.length() > 0) {
                errs.append(String.format(EXPORT_ERR_TEMPL, type, entityName, tempErrs));
                return new ArrayList<>(0);
            } else {
                return entries;
            }
        }
    }

    private abstract class SimpleContent extends EntireContent {
        private final IOFileFilter ff = new IOFileFilter() {
            public boolean accept(File file) {
                return isValid(file);
            }

            public boolean accept(File file, String s) {
                return accept(file);
            }
        };

        public SimpleContent() {
            super();
            errs = errsByNotifId.computeIfAbsent(MessageSender.EXPORT_COMPLETED_ID, f -> new StringBuilder());
        }

        public List<ArchiveEntry> process(EntireDBEntity entity) {
            List<ArchiveEntry> tmpEntries = new ArrayList<ArchiveEntry>();
            List<File> contentFiles = getContentFiles(entity.getName());

            for (File path : contentFiles) {
                addArchiveEntry(path, entity.getName(), tmpEntries);
            }

            return checkAssets(tmpEntries, entity.getName());
        }

        protected abstract String getGroup(String uri, String recName);

        protected abstract boolean isValid(File file);

        protected abstract List<File> getFilePaths(String recName);

        public List<ArchiveEntry> process(RecordManifest manifest) {
            return new ArrayList<ArchiveEntry>();
        }

        protected List<File> getContentFiles(String recName) {
            List<File> paths = getFilePaths(recName);
            List<File> contentFiles = new ArrayList<File>();

            paths.stream().filter(File::exists).forEach(path -> {
                    if (path.isFile()) {
                        if (isValid(path)) {
                            contentFiles.add(path);
                        }
                    } else {
                        contentFiles.addAll(FileUtils.listFiles(path, ff, TrueFileFilter.INSTANCE));
                    }
                });

            return contentFiles;
        }

        private void addArchiveEntry(File file, String recName, List<ArchiveEntry> entries) {
            String uri = file.getPath();
            String pathInArch = getPathInArch(uri, recName);
            entries.add(new ArchiveEntry(pathInArch, rps.getRealFilePath(uri)));
        }

        private String getPathInArch(String uri, String recName) {
            String group = getGroup(uri, recName);
            StringBuilder path = new StringBuilder();

            path.append(recName);
            if (StringUtils.isNotEmpty(group)) {
                path.append("/").append(group);
            }
            if (uri.endsWith(recName + Extensions.XML)) {
                path.append("/").append(uri.substring(uri.indexOf(recName)));
            } else {
                path.append("/").append(uri.substring(uri.indexOf(recName) + recName.length() + 1));
            }

            return path.toString();
        }
    }

    /**
     *
     */
    private class Ml3gForIpad extends Ml3gForIpadIssue {

        public Ml3gForIpad() {
            super(DbEntity.NOT_EXIST_ID, dbName);
        }

        @Override
        public Integer getIssueId() {
            return Constants.UNDEF;
        }

        public List<ArchiveEntry> process(EntireDBEntity entity) {
            String recName = entity.getName();
            List<ArchiveEntry> tmpEntries;
            StringBuilder tmpErrs = new StringBuilder();

            Ml3gXmlAssets assets = new Ml3gXmlAssets();
            assets.setXmlUri(FilePathCreator.getFilePathForEntireMl3gXml(dbName, recName));
            assets.setAssetsUris(PublishHelper.getPublishContentUris(this, Constants.UNDEF, recName, false,
                    false, ContentLocation.ENTIRE, tmpErrs));

            if (tmpErrs.length() > 0) {
                errs.append("Failed to perform export {").append(type).append("} for review [").append(recName)
                        .append("], ").append(tmpErrs).append("\n");
                tmpEntries = new ArrayList<>(0);
            } else {
                tmpEntries = getEntries(recName, assets);
            }

            return tmpEntries;
        }
    }

    /**
     *
     */
    private class RenderedText extends SimpleContent {
        private static final String TYPE = "Rendered text (HTML, Javascript, CSS and .xhtml)";

        public RenderedText() {
            type = TYPE;
        }

        protected List<File> getFilePaths(String recName) {
            List<File> paths = new ArrayList<File>();
            paths.add(new File(rps.getRealFilePath(FilePathCreator.getFilePathToSourceEntire(dbName, recName))));
            paths.add(new File(rps.getRealFilePath(FilePathCreator.getFilePathForEnclosureEntire(dbName,
                    recName, ""))));
            paths.add(new File(rps.getRealFilePath(FilePathCreator.getRenderedDirPathEntire(dbName, recName,
                    RenderingPlan.HTML))));
            paths.add(new File(rps.getRealFilePath(FilePathCreator.getRenderedDirPathEntire(dbName, recName,
                    RenderingPlan.PDF_TEX))));

            return paths;
        }

        protected String getGroup(String uri, String recName) {
            String group = null;
            if (uri.contains(FilePathCreator.getRenderingPlanDirName(RenderingPlan.PDF_TEX, false))) {
                group = Constants.RENDERED_PDF_TEX;
            } else if (uri.contains(FilePathCreator.getRenderingPlanDirName(RenderingPlan.HTML, false))
                    && !uri.endsWith(Extensions.XML)) {
                group = Constants.RENDERED_HTML_DIAMOND;
            }

            return group;
        }

        protected boolean isValid(File file) {
            String fileName = file.getName();
            return (fileName.endsWith(Extensions.XML)
                    || fileName.endsWith(Extensions.CSS)
                    || fileName.endsWith(Extensions.HTML)
                    || fileName.endsWith(Extensions.HTM)
                    || fileName.endsWith(Extensions.PDF)
                    || fileName.endsWith(Extensions.JS));
        }
    }

    /**
     *
     */
    private class RenderedGraphics extends SimpleContent {
        private static final String TYPE = "Rendered graphics (image_n, image_t)";

        public RenderedGraphics() {
            type = TYPE;
        }

        protected List<File> getFilePaths(String recName) {
            List<File> paths = new ArrayList<File>();
            paths.add(new File(rps.getRealFilePath(FilePathCreator.getRenderedDirPathEntire(dbName, recName,
                    RenderingPlan.HTML))));

            return paths;
        }

        protected String getGroup(String uri, String recName) {
            return Constants.RENDERED_HTML_DIAMOND;
        }

        protected boolean isValid(File file) {
            return com.wiley.tes.util.FileUtils.isGraphicExtension("." + FilenameUtils.getExtension(file.getName()));
        }
    }

    /**
     *
     */
    private class RenderedPdf extends SimpleContent {
        private static final String TYPE = "Rendered pdf";

        public RenderedPdf() {
            type = TYPE;
        }

        protected List<File> getFilePaths(String recName) {
            List<File> paths = new ArrayList<File>();
            paths.add(new File(rps.getRealFilePath(FilePathCreator.getRenderedDirPathEntire(dbName, recName,
                    RenderingPlan.PDF_TEX))));
            paths.add(new File(rps.getRealFilePath(FilePathCreator.getRenderedDirPathEntire(dbName, recName,
                    RenderingPlan.PDF_FOP))));

            return paths;
        }

        protected String getGroup(String uri, String recName) {
            return uri.contains(FilePathCreator.getRenderingPlanDirName(RenderingPlan.PDF_FOP, false))
                    ? Constants.RENDERED_PDF_FOP
                    : Constants.RENDERED_PDF_TEX;
        }

        protected boolean isValid(File file) {
            return file.getName().endsWith(Extensions.PDF);
        }
    }

    /**
     *
     */
    private class SourceGraphics extends SimpleContent {
        private static final String TYPE = "Source graphics (image_n, image_t, CDXXXXXX-figures, CDXXXXXX-thumbnails)";

        public SourceGraphics() {
            type = TYPE;
        }

        protected List<File> getFilePaths(String recName) {
            List<File> paths = new ArrayList<>();
            File jatsRec = new File(rps.getRealFilePath(FilePathBuilder.JATS.getPathToEntireDir(dbName, recName)));
            if (dbName.equals(CochraneCMSPropertyNames.getCDSRDbName()) && jatsRec.exists()) {
                paths.add(jatsRec);
            } else {
                paths.add(new File(rps.getRealFilePath(
                        FilePathCreator.getFilePathForEnclosureEntire(dbName, recName, ""))));
            }

            return paths;
        }

        protected String getGroup(String uri, String recName) {
            return null;
        }

        protected boolean isValid(File file) {
            return com.wiley.tes.util.FileUtils.isGraphicExtension("." + FilenameUtils.getExtension(file.getName()));
        }
    }

    /**
     *
     */
    private class XmlSource extends SimpleContent {
        private static final String TYPE = "XML Source";

        public XmlSource() {
            type = TYPE;
        }

        protected List<File> getFilePaths(String recName) {
            List<File> paths = new ArrayList<>();
            File jatsRec = new File(rps.getRealFilePath(FilePathBuilder.JATS.getPathToEntireDir(dbName, recName)));
            if (dbName.equals(CochraneCMSPropertyNames.getCDSRDbName()) && jatsRec.exists()) {
                paths.add(jatsRec);
            } else {
                paths.add(new File(rps.getRealFilePath(FilePathCreator.getFilePathToSourceEntire(dbName, recName))));
                paths.add(new File(rps.getRealFilePath(FilePathCreator.getFilePathForEnclosureEntire(dbName, recName,
                                                                                                     ""))));
                paths.add(new File(rps.getRealFilePath(FilePathCreator.getRenderedDirPathEntire(dbName, recName,
                                                                                                RenderingPlan.HTML))));
            }

            return paths;
        }

        protected String getGroup(String uri, String recName) {
            return null;
        }

        protected boolean isValid(File file) {
            return file.getName().endsWith(Extensions.XML);
        }
    }

    /**
     *
     */
    private class Dvd extends SimpleContent {
        private static final String TYPE = "DVD";

        public Dvd() {
            type = TYPE;
        }

        protected List<File> getFilePaths(String recName) {
            List<File> paths = new ArrayList<>();
            paths.add(new File(rps.getRealFilePath(FilePathCreator.getFilePathToSourceEntire(dbName, recName))));
            paths.add(new File(rps.getRealFilePath(FilePathCreator.getFilePathForEnclosureEntire(dbName,
                    recName, ""))));
            paths.add(new File(rps.getRealFilePath(FilePathCreator.getRenderedDirPathEntire(dbName, recName,
                    RenderingPlan.HTML))));

            return paths;
        }

        protected String getGroup(String uri, String recName) {
            return null;
        }

        protected boolean isValid(File file) {
            String fileName = file.getName();
            if (file.getPath().contains(FilePathCreator.getRenderingPlanDirName(RenderingPlan.HTML, false))) {
                return fileName.endsWith(Extensions.XML);
            } else {
                return com.wiley.tes.util.FileUtils.isGraphicExtension("." + FilenameUtils.getExtension(fileName));
            }
        }
    }
}
