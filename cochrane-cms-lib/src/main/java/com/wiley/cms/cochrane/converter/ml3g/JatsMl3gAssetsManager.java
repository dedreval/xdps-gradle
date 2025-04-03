package com.wiley.cms.cochrane.converter.ml3g;

import com.wiley.cms.cochrane.cmanager.CmsException;
import com.wiley.cms.cochrane.cmanager.CmsUtils;
import com.wiley.cms.cochrane.cmanager.CochraneCMSBeans;
import com.wiley.cms.cochrane.cmanager.FilePathCreator;
import com.wiley.cms.cochrane.cmanager.contentworker.ContentHelper;
import com.wiley.cms.cochrane.cmanager.contentworker.JatsHelper;
import com.wiley.cms.cochrane.cmanager.data.record.IRecord;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.cmanager.translated.TranslatedAbstractVO;
import com.wiley.cms.cochrane.converter.IConverterAdapter;
import com.wiley.cms.cochrane.repository.IRepository;
import com.wiley.cms.cochrane.repository.RepositoryUtils;
import com.wiley.cms.cochrane.test.ContentChecker;
import com.wiley.cms.cochrane.utils.Constants;
import com.wiley.cms.cochrane.utils.ContentLocation;
import com.wiley.cms.cochrane.utils.ErrorInfo;
import com.wiley.cms.cochrane.utils.ImageUtil;
import com.wiley.cms.cochrane.utils.xml.JDOMHelper;
import com.wiley.cms.converter.services.ConversionData;
import com.wiley.cms.converter.services.RevmanMetadataHelper;
import com.wiley.tes.interfaces.BatchConversionDescription;
import com.wiley.tes.interfaces.SourceDescription;
import com.wiley.tes.util.Extensions;
import com.wiley.tes.util.FileUtils;
import com.wiley.tes.util.InputUtils;
import com.wiley.tes.util.Logger;
import com.wiley.tes.util.XmlUtils;
import com.wiley.tes.util.jdom.JDOMUtils;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.StringUtils;
import org.jdom.Document;
import org.jdom.Element;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FilenameFilter;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static com.wiley.cms.cochrane.utils.ImageUtil.THUMBNAIL_DIFF_PERCENT;
import static com.wiley.cms.cochrane.utils.ImageUtil.THUMBNAIL_HEIGHT;
import static com.wiley.cms.cochrane.utils.ImageUtil.THUMBNAIL_WIDTH;

/**
 * @author <a href='mailto:atolkachev@wiley.com'>Andrey Tolkachev</a>
 * @version 04.12.2020
 */
public class JatsMl3gAssetsManager {
    private static final Logger LOG = Logger.getLogger(JatsMl3gAssetsManager.class);
    private static final String N_PREFIX = "n";
    private static final String T_PREFIX = "t";

    private static final String[] DIR_SUFFIXES = new String[] {
        Constants.JATS_FIG_DIR_SUFFIX,
        Constants.JATS_TMBNLS_DIR_SUFFIX,
        Constants.JATS_STATS_DIR_SUFFIX,
        Constants.IMAGE_N_FOLDER
    };

    private static final String[] PDF_SUFFIXES = new String[] {
        Constants.PDF_ABSTRACT_SUFFIX
    };

    private JatsMl3gAssetsManager() {
    }

    public static void validateSVG(String cutRecPath, IRepository rp) throws Exception {
        List<String> svgUris = getAssetFilesByDirSuffix(cutRecPath, RepositoryUtils.SVG_FF,
                Constants.JATS_FIG_DIR_SUFFIX, rp);
        if (svgUris.isEmpty()) {
            return;
        }

        Map<String, SourceDescription> sourceDescriptions = new HashMap<>();
        for (String imageUri: svgUris) {
            SourceDescription srcDesc = SourceDescription.builder()
                    .content(InputUtils.readStreamToString(rp.getFile(imageUri)))
                    .build();
            sourceDescriptions.put(FilenameUtils.getName(imageUri), srcDesc);
        }
        BatchConversionDescription convDesc = BatchConversionDescription.builder()
                .sourceDescriptions(sourceDescriptions)
                .build();
        Map<String, String> errors = CochraneCMSBeans.getConverter().validateSVG(convDesc, IConverterAdapter.SVG,
                IConverterAdapter.SVG_VAL);
        if (errors.size() > 0) {
            StringBuilder builder = new StringBuilder("\n");
            errors.forEach((fileName, error) -> builder.append(fileName).append(" contains error(s):\n").append(error));
            throw new CmsException(builder.toString());
        }
    }

    public static void validateAndCreateThumbnails(String cutRecPath, IRepository rp) throws Exception {
        validateSVG(cutRecPath, rp);

        List<String> imageUris = getAssetFilesByDirSuffix(cutRecPath, null, Constants.JATS_FIG_DIR_SUFFIX, rp);
        for (String imageUri: imageUris) {
            byte[] imageBytes = rp.getFileAsByteArray(imageUri);
            List<Exception> eList = new ArrayList<>();
            String extension = FilenameUtils.getExtension(imageUri);

            BufferedImage bufferedImage;
            String imageFileName = RepositoryUtils.getLastNameByPath(imageUri);
            if (Extensions.SVG.equals("." + extension)) {
                bufferedImage = ImageUtil.loadSvgImage(imageBytes, eList);
                imageFileName = imageFileName.replace(Extensions.SVG, Extensions.PNG);
                extension = StringUtils.substringAfter(Extensions.PNG, ".");
            } else {
                bufferedImage = ImageUtil.loadImage(imageBytes, eList);
            }

            if (bufferedImage == null || imageBytes.length == 0 || !eList.isEmpty()) {
                StringBuilder err = new StringBuilder(ErrorInfo.Type.WRONG_IMAGE_THUMBNAIL.getMsg()).append(
                        " for ").append(imageFileName).append(" as some errors happened during image transcoding: ");
                if (imageBytes.length == 0) {
                    err.append("\n").append(String.format("size of %s is zero", imageFileName));
                }
                eList.forEach(e -> err.append("\n").append(e.getClass().getSimpleName()).append(
                        ": ").append(e.getMessage()));
                LOG.error(err);
                if (bufferedImage == null || imageBytes.length == 0) {
                    throw new CmsException(err.toString());
                }
            }

            String path = cutRecPath + Constants.JATS_TMBNLS_DIR_SUFFIX + FilePathCreator.SEPARATOR + imageFileName;
            ImageUtil.writeThumbnail(bufferedImage, extension, path, THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT,
                    THUMBNAIL_DIFF_PERCENT);
        }
    }

    public static String generateAssetsAndPackageDescriptor(BaseType bt, IRecord record, Integer issueId,
                                                            ContentLocation location, boolean ariesMl3gSource,
                                                            ContentHelper helper, IRepository rp) throws Exception {
        List<String> jatsAssets = getRealAssetsPaths(record.getRecordPath(), rp);

        List<String> assetsMapping = getAssetsMapping(jatsAssets, record, ariesMl3gSource);
        if (!ariesMl3gSource) {
            InputStream is = new ByteArrayInputStream(Ml3gAssetsManager.getAssetsFileContent(assetsMapping).getBytes(
                    StandardCharsets.UTF_8));
            rp.putFile(location.getPathToMl3gAssets(issueId, bt.getId(),
                    record.getHistoryNumber(), record.getName()), is);
        }
        AtomicBoolean isNRF = new AtomicBoolean(false);
        boolean aries = ariesMl3gSource || hasAriesAssetLinks(record.getRecordPath(), helper, rp, isNRF);
        return createPackageDescriptor(bt, record, issueId, aries, ariesMl3gSource, location, assetsMapping, isNRF);
    }

    public static List<String> getRealAssetsPaths(String recordPath, IRepository rp) {
        String cutRecPath = RepositoryUtils.getRecordNameByFileName(recordPath);

        List<String> list = new ArrayList<>();
        Arrays.stream(DIR_SUFFIXES).map(
                dirSuffix -> getAssetFilesByDirSuffix(cutRecPath, null, dirSuffix, rp)).forEach(list::addAll);
        return list;
    }

    public static List<String> getAssetFilesByDirSuffix(String cutRecPath, FilenameFilter filter, String dirSuffix,
                                                        IRepository rp) {
        List<String> list = new ArrayList<>();
        RepositoryUtils.addFolderPaths(cutRecPath + dirSuffix + FilePathCreator.SEPARATOR, list, filter, rp);
        return list;
    }

    public static String getStatsDataPath(String recordDir, IRepository rp) {
        File[] files = rp.getFilesFromDir(recordDir);
        if (files != null) {
            for (File fl: files) {
                String statsDirName = fl.getName();
                if (!fl.isDirectory() || !statsDirName.contains(Constants.JATS_STATS_DIR_SUFFIX)) {
                    continue;
                }
                File[] statFiles = fl.listFiles(RepositoryUtils.RM5_FF);
                if (statFiles != null && statFiles.length > 0) {
                    return recordDir + FilePathCreator.SEPARATOR + statsDirName + FilePathCreator.SEPARATOR
                            + statFiles[0].getName();
                }
                break;
            }
        }
        return null;
    }

    public static boolean hasStatsData(String recordPath, IRepository rp) {
        File[] files = rp.getFilesFromDir(recordPath);
        return files != null && Arrays.stream(files).anyMatch(
                fl -> fl.isDirectory() && fl.getName().contains(Constants.JATS_STATS_DIR_SUFFIX));
    }

    private static List<String> getAssetsMapping(List<String> realAssetsPaths, IRecord record,
                                                 boolean ariesMl3gSource) {
        List<String> assetsMapping = new ArrayList<>(realAssetsPaths.size());
        for (String path : realAssetsPaths) {
            if (path.contains(Constants.JATS_FIG_DIR_SUFFIX)) {
                addMappedAsset(assetsMapping, path, Constants.IMAGE_N_DIR);
            } else if (path.contains(Constants.JATS_TMBNLS_DIR_SUFFIX)) {
                addMappedAsset(assetsMapping, path, Constants.IMAGE_T_DIR);
            } else if (path.contains(Constants.JATS_STATS_DIR_SUFFIX)) {
                addMappedAsset(assetsMapping, path, Constants.SUPINFO_DIR);

            } else if (ariesMl3gSource) {
                if (path.contains(Constants.IMAGE_N_DIR)) {
                    addMappedAsset4OnlyFolder(assetsMapping, path, Constants.IMAGE_N_DIR);
                } else if (path.contains(Constants.IMAGE_T_DIR)) {
                    addMappedAsset4OnlyFolder(assetsMapping, path, Constants.IMAGE_T_DIR);
                }
            }
        }
        LOG.debug(String.format("creating assets mapping for record %s, list size = %d assets",
                                RevmanMetadataHelper.buildPubName(record.getName(), record.getPubNumber()),
                                assetsMapping.size()));
        return assetsMapping;
    }

    private static void addMappedAsset(List<String> assetsMapping, String path, String folder) {
        String prefix = folder.equals(Constants.IMAGE_N_DIR) ? N_PREFIX
                                : folder.equals(Constants.IMAGE_T_DIR) ? T_PREFIX : "";
        String initialFileName = FilenameUtils.getName(path);
        String finalFileName = folder.equals(Constants.SUPINFO_DIR)
                               ? initialFileName.replace(Constants.JATS_STATS_DIR_SUFFIX, Constants.STATS_DATA_ONLY)
                               : initialFileName;
        assetsMapping.add(path + "," + folder + FilePathCreator.SEPARATOR + prefix + finalFileName);
    }

    private static void addMappedAsset4OnlyFolder(List<String> assetsMapping, String path, String folder) {
        String initialFileName = FilenameUtils.getName(path);
        assetsMapping.add(initialFileName + "," + folder + FilePathCreator.SEPARATOR + initialFileName);
    }

    private static String createPackageDescriptor(BaseType bt, IRecord record, Integer issueId, boolean aries,
                                                  boolean ariesMl3gSource, ContentLocation location,
                                                  List<String> assetsMapping, AtomicBoolean isNRF) {
        StringBuilder builder = new StringBuilder(XmlUtils.XML_HEAD);

        PackageDescriptor.buildHeader(bt, record, builder);
        PackageDescriptor.buildElements(record.getName(), builder);

        if (!assetsMapping.isEmpty()) {
            PackageDescriptor.buildFiles(assetsMapping, aries, builder);
            if (!ariesMl3gSource) {
                PackageDescriptor.buildGeneratedFiles(assetsMapping, aries, builder);
            }
        }
        if (!ariesMl3gSource) {
            PackageDescriptor.buildAssetFiles(record, issueId, bt.getId(), location, assetsMapping, builder, isNRF);
        }
        PackageDescriptor.buildCloseTags(builder);

        LOG.debug(String.format("creating package descriptor for record %s",
                                RevmanMetadataHelper.buildPubName(record.getName(), record.getPubNumber())));
        return builder.toString();
    }

    private static List<String> getAssetUrisByDirSuffix(List<String> assetsMapping, String folderSuffix) {
        return assetsMapping.stream()
                       .filter(asset -> asset.contains(folderSuffix))
                       .map(JatsMl3gAssetsManager::getRealAssetUri)
                       .collect(Collectors.toCollection(() -> new ArrayList<>(assetsMapping.size())));
    }

    private static List<String> getMappedAssetsByDirSuffix(List<String> assetsMapping, String folderSuffix) {
        return assetsMapping.stream()
                       .filter(asset -> asset.contains(folderSuffix))
                       .map(JatsMl3gAssetsManager::getMappedAsset)
                       .collect(Collectors.toCollection(() -> new ArrayList<>(assetsMapping.size())));
    }

    public static String getRealAssetUri(String assetUri) {
        return assetUri.split(",")[0];
    }

    public static String getMappedAsset(String assetUri) {
        return assetUri.split(",")[1];
    }

    private static boolean hasAriesAssetLinks(String recordPath, ContentHelper helper, IRepository rp,
                                              AtomicBoolean isNRF)
            throws Exception {
        InputStream is = rp.getFile(recordPath);
        Document doc  = helper.getDocumentLoader().load(is);
        boolean ariesLinks = false;

        Element extLink = ContentHelper.getElement(doc, JatsHelper.EXT_LINK_PATH, false);
        Element floatsGroup = ContentHelper.getElement(doc, JatsHelper.FLOATS_GROUP, false);
        String href = "href";
        if (extLink != null) {
            ariesLinks = !JDOMHelper.getAttributeValue(extLink, href, JatsHelper.XLINK_NS, "").contains(
                    FilePathCreator.SEPARATOR);
        } else if (floatsGroup != null) {
            if (hasAssetSeparator(floatsGroup, href)) {
                return true;
            }
            Element figGroup = floatsGroup.getChild("fig-group");
            if (figGroup != null) {
                ariesLinks = hasAssetSeparator(figGroup, href);
            }
        }

        Set<String> entryNamesFromXml =
                ContentHelper.getMandatoryValues(doc, ContentChecker.SUPPLEMENTARY_MATERIALS_PATH_FOR_NRF);
        if (!entryNamesFromXml.isEmpty()) {
            isNRF.set(true);
            ariesLinks = true;
        }

        return ariesLinks;
    }

    private static boolean hasAssetSeparator(Element groupElement, String href) {
        return JDOMUtils.getChildren(groupElement, "fig")
                .stream()
                .anyMatch(f -> !JDOMHelper.getAttributeValue(f.getChild("graphic"), href, JatsHelper.XLINK_NS, "")
                        .contains(FilePathCreator.SEPARATOR));
    }

    /* For PDF Preview Service */
    public static String createPackageDescriptor(ConversionData data) {
        StringBuilder builder = new StringBuilder(XmlUtils.XML_HEAD);

        PackageDescriptor.buildHeader(data, builder);
        PackageDescriptor.buildElements(data.getName(), builder);
        if (data.isJatsAries()) {
            PackageDescriptor.buildFiles(data, builder);
        }
        PackageDescriptor.buildStatsAssetFile(data, builder);
        PackageDescriptor.buildCloseTags(builder);

        return builder.toString();
    }

    public static String createPublishDateDescriptor(int predictIssue, BaseType baseType, IRecord record) {
        StringBuilder builder = new StringBuilder(XmlUtils.XML_HEAD);

        PublishDateDescriptor.buildHeader(builder);
        boolean added = PublishDateDescriptor.buildDates(record.getFirstOnline(), record.getPublishedOnlineCitation(),
                record.getPublishedOnlineFinalForm(), builder);
        if (!baseType.isCCA()) {
            PublishDateDescriptor.buildIssues(predictIssue, record, record.getPublishedOnlineFinalForm(), builder);
            added = true;
        }
        PublishDateDescriptor.buildCloseTag(builder);

        String result = builder.toString();
        LOG.debug(String.format("adding publication dates/issues for:\n %s\n%s",
                                RevmanMetadataHelper.buildPubName(record.getName(), record.getPubNumber()), result));
        return added ? result : null;
    }

    private static class PackageDescriptor {
        private static final String DESCRIPTOR_VERSION =
                "<descriptor xmlns=\"http://ct.wiley.com/ns/hermes/descriptor\" version=\"0.1\">";
        private static final String DESCRIPTOR_CLOSE = "</descriptor>";
        private static final String PRODUCT_ITEM_DOI = "<product-item doi=\"";
        private static final String PRODUCT_ITEM_CLOSE = "</product-item>";
        private static final String ELEMENTS = "<elements>";
        private static final String ELEMENTS_CLOSE = "</elements>";
        private static final String ID_TYPE_SOURCE_VALUE = "<id type=\"file\" sourceValue=\"";
        private static final String TARGET_VALUE = "targetValue=\"";
        private static final String FILES = "<files>";
        private static final String FILES_CLOSE = "</files>";
        private static final String FILE_SOURCE_PATH = "<file sourcePath=\"";
        private static final String TARGET_PATH = "targetPath=\"";
        private static final String GENERATED_FILES = "<generated-files>";
        private static final String GENERATED_FILES_CLOSE = "</generated-files>";
        private static final String ASSET_FILES = "<asset-files>";
        private static final String ASSET_FILES_CLOSE = "</asset-files>";
        private static final String FILE_TARGET_PATH = "<file targetPath=\"";
        private static final String QUOT_GT_LINE_SEP = "\">\n";
        private static final String QUOT_GT_CLOSE_LINE_SEP = "\"/>\n";
        private static final String QUOT_SPACE = "\" ";

        private static void buildHeader(BaseType bt, IRecord record, StringBuilder builder) {
            builder.append("\n").append(DESCRIPTOR_VERSION).append("\n")
                    .append(PRODUCT_ITEM_DOI).append(
                            bt.getProductType().buildDoi(record.getName(), record.getPubNumber()))
                    .append(QUOT_GT_LINE_SEP);
        }

        private static void buildElements(String recordName, StringBuilder builder) {
            builder.append("\n").append(ELEMENTS).append("\n")
                    .append(ID_TYPE_SOURCE_VALUE).append(recordName).append(QUOT_SPACE)
                    .append(TARGET_VALUE).append(recordName).append(QUOT_GT_CLOSE_LINE_SEP)
                    .append(ELEMENTS_CLOSE).append("\n");
        }

        private static void buildFiles(List<String> assetsMapping, boolean aries, StringBuilder builder) {
            builder.append("\n").append(FILES).append("\n");
            assetsMapping.stream()
                    .filter(asset -> !asset.contains(Constants.JATS_TMBNLS_DIR_SUFFIX))
                    .forEach(asset -> builder.append(FILE_SOURCE_PATH)
                                              .append(cutAssetPath(getRealAssetUri(asset), aries)).append(QUOT_SPACE)
                                              .append(TARGET_PATH)
                                              .append(getMappedAsset(asset))
                                              .append(QUOT_GT_CLOSE_LINE_SEP));
            builder.append(FILES_CLOSE).append("\n");
        }

        private static void buildGeneratedFiles(List<String> assetsMapping, boolean aries, StringBuilder builder) {
            builder.append("\n").append(GENERATED_FILES).append("\n");
            mapFiguresToThumbnails(assetsMapping).forEach(
                    asset -> builder.append(FILE_SOURCE_PATH)
                                     .append(cutAssetPath(getRealAssetUri(asset), aries)).append(QUOT_SPACE)
                                     .append(TARGET_PATH)
                                     .append(getMappedAsset(asset)).append(QUOT_GT_CLOSE_LINE_SEP));
            builder.append(GENERATED_FILES_CLOSE).append("\n");
        }

        private static void buildAssetFiles(IRecord record, Integer issueId, String dbName, ContentLocation location,
                                            List<String> assetsMapping, StringBuilder builder, AtomicBoolean isNRF) {
            builder.append("\n").append(ASSET_FILES).append("\n");
            getDescriptorAssets(record, issueId, dbName, location, assetsMapping, isNRF).forEach(
                    asset -> builder.append(FILE_TARGET_PATH)
                                     .append(asset)
                                     .append(QUOT_GT_CLOSE_LINE_SEP));
            builder.append(ASSET_FILES_CLOSE).append("\n");
        }

        private static void buildCloseTags(StringBuilder builder) {
            builder.append("\n").append(PRODUCT_ITEM_CLOSE)
                    .append("\n").append(DESCRIPTOR_CLOSE);
        }

        private static List<String> mapFiguresToThumbnails(List<String> assetsMapping) {
            List<String> figuresUris = getAssetUrisByDirSuffix(assetsMapping, Constants.JATS_FIG_DIR_SUFFIX);
            List<String> mappedTmbnls = getMappedAssetsByDirSuffix(assetsMapping, Constants.JATS_TMBNLS_DIR_SUFFIX);
            List<String> mappedFigsToTmbnls = new ArrayList<>(mappedTmbnls.size());

            mappedTmbnls.forEach(
                    tmbnlMappedAsset -> figuresUris.stream().filter(
                        figureUri -> tmbnlMappedAsset.endsWith(FilenameUtils.getBaseName(figureUri) + Extensions.PNG))
                            .map(figureUri -> figureUri + "," + tmbnlMappedAsset)
                            .forEach(mappedFigsToTmbnls::add));
            return mappedFigsToTmbnls;
        }
            
        private static List<String> getDescriptorAssets(IRecord record, Integer issueId, String dbName,
                                                        ContentLocation location, List<String> assetsMapping,
                                                        AtomicBoolean isNRF) {
            List<String> descriptorAssets = new ArrayList<>();
            descriptorAssets.add(FilenameUtils.getName(
                location.getPathToPdfFileWithSuffix(issueId, dbName, record.getHistoryNumber(), record.getName(), "")));

            Collection<String> languages = record.getLanguages();
            if (languages != null) {
                languages.stream()
                        .filter(TranslatedAbstractVO::isMappedLanguage4Fop)
                        .map(lang -> FilenameUtils.getName(
                                location.getPathToPdfTAFileWithSuffix(issueId, dbName, record.getHistoryNumber(),
                                        record.getName(), lang, Constants.PDF_ABSTRACT_SUFFIX)))
                        .forEach(descriptorAssets::add);
            }

            if (record.isStageR() && !record.isWithdrawn()) {
                Arrays.stream(PDF_SUFFIXES).forEachOrdered(
                        suffix -> descriptorAssets.add(FilenameUtils.getName(
                                location.getPathToPdfFileWithSuffix(issueId, dbName, record.getHistoryNumber(),
                                        record.getName(), suffix))));
            }

            List<String> statsData = getMappedAssetsByDirSuffix(assetsMapping, Constants.JATS_STATS_DIR_SUFFIX);
            if (!statsData.isEmpty() && !isNRF.get()) {
                descriptorAssets.add(statsData.get(0));
            }

            return descriptorAssets;
        }

        private static String cutAssetPath(String assetPath, boolean aries) {
            String[] pathParts = assetPath.split(FilePathCreator.SEPARATOR);
            String ret = pathParts[pathParts.length - 1];
            return aries ? ret : pathParts[pathParts.length - 2] + FilePathCreator.SEPARATOR + ret;
        }

        /* For PDF Preview Service */
        private static void buildHeader(ConversionData data, StringBuilder builder) {
            String pubName = FileUtils.cutExtension(data.getPubName());
            String doi = RevmanMetadataHelper.buildDoi(RevmanMetadataHelper.parseCdNumber(pubName),
                                                       RevmanMetadataHelper.parsePubNumber(pubName));
            builder.append("\n").append(DESCRIPTOR_VERSION).append("\n")
                    .append(PRODUCT_ITEM_DOI).append(doi).append(QUOT_GT_LINE_SEP);
        }

        /* For PDF Preview Service */
        private static void buildFiles(ConversionData data, StringBuilder builder) {
            builder.append("\n").append(FILES).append("\n");
            data.getAssets()
                    .forEach(asset -> {
                            String assetName = asset.getName();
                            if (assetName.endsWith(Extensions.ZIP)) {
                                return;
                            }
                            String parentName = asset.getParentFile().getName();
                            builder.append(FILE_SOURCE_PATH)
                                    .append(assetName).append(QUOT_SPACE)
                                    .append(TARGET_PATH)
                                    .append(parentName).append(FilePathCreator.SEPARATOR).append(assetName)
                                    .append(QUOT_GT_CLOSE_LINE_SEP);
                        });
            builder.append(FILES_CLOSE).append("\n");
        }

        /* For PDF Preview Service */
        private static void buildStatsAssetFile(ConversionData data, StringBuilder builder) {
            if (data.isJatsStatsPresent()) {
                builder.append("\n").append(ASSET_FILES).append("\n");
                builder.append(FILE_TARGET_PATH)
                        .append(Constants.TABLE_N_DIR).append("/")
                        .append(data.getName()).append(Constants.STATS_DATA_ONLY).append(Extensions.RM5)
                        .append(QUOT_GT_CLOSE_LINE_SEP);
                builder.append(ASSET_FILES_CLOSE).append("\n");
            }
        }
    }

    private static class PublishDateDescriptor {
        private static final String PUB_FLOW = "CochranePubFlow";
        private static final String PUB_FLOW_NS = " xmlns=\"http://ct.wiley.com/ns/xdps/pubflow\"";
        private static final String DATE_ONLINE_FIRST = "dateOnlineFirst";
        private static final String DATE_ONLINE_CITATION = "dateOnlineCitation";
        private static final String DATE_ONLINE_FINAL = "dateOnlineFinal";
        private static final String ISSUE_PROTOCOL_FIRST = "issueProtocolFirst";
        private static final String VOLUME_PROTOCOL_FIRST = "volumeProtocolFirst";
        private static final String ISSUE_REVIEW_FIRST = "issueReviewFirst";
        private static final String VOLUME_REVIEW_FIRST = "volumeReviewFirst";
        private static final String ISSUE_CURRENT = "issueCurrent";
        private static final String VOLUME_CURRENT = "volumeCurrent";
        private static final String ISSUE_SELF_CITATION = "issueSelfCitation";
        private static final String VOLUME_SELF_CITATION = "volumeSelfCitation";
        private static final String ISSUE_CITATION = "issueCitation";
        private static final String VOLUME_CITATION = "volumeCitation";
        private static final String LINE_SEP_LT = "\n<";
        private static final String GT = ">";
        private static final String LT_CLOSE = "</";
        private static final String GT_LINE_SEP = ">\n";

        private static void buildHeader(StringBuilder builder) {
            builder.append(LINE_SEP_LT).append(PUB_FLOW).append(PUB_FLOW_NS).append(GT_LINE_SEP);
        }

        private static boolean buildDates(String firstOnline, String citationLastChanged, String finalOnlineForm,
                                          StringBuilder sb) {
            boolean ret = false;
            if (citationLastChanged != null) {
                //String formattedCitationDate = DATE_FORMAT.format(citationLastChanged);
                //buildDate(formattedCitationDate, DATE_ONLINE_FIRST, builder);
                buildDate(citationLastChanged, DATE_ONLINE_CITATION, sb);
                ret = true;
            }

            if (firstOnline != null) {
                //String formattedCitationDate = DATE_FORMAT.format(citationLastChanged);
                buildDate(firstOnline, DATE_ONLINE_FIRST, sb);
                ret = true;
                //buildDate(formattedCitationDate, DATE_ONLINE_CITATION, builder);
            }

            if (finalOnlineForm != null) {
                //String formattedPublishDate = DATE_FORMAT.format(versionPublished);
                buildDate(finalOnlineForm, DATE_ONLINE_FINAL, sb);
                ret = true;
            }
            return ret;
        }

        private static void buildDate(String formattedDate, String dateTag, StringBuilder builder) {
            builder.append(LINE_SEP_LT).append(dateTag).append(GT)
                    .append(formattedDate)
                    .append(LT_CLOSE).append(dateTag).append(GT_LINE_SEP);
        }

        private static void buildIssues(int flowIssue, IRecord record, String finalOnlineForm, StringBuilder sb) {
            if (record.getProtocolFirstIssue() != 0) {
                buildIssueAndVolume(record.getProtocolFirstIssue(),
                        ISSUE_PROTOCOL_FIRST, VOLUME_PROTOCOL_FIRST, sb);
            }
            if (record.getReviewFirstIssue() != 0) {
                buildIssueAndVolume(record.getReviewFirstIssue(),
                        ISSUE_REVIEW_FIRST, VOLUME_REVIEW_FIRST, sb);
            }
            boolean spd = CmsUtils.isScheduledIssueNumber(flowIssue);
            int predictIssue = spd ? record.getPublishedIssue() : flowIssue;
            boolean emptyDates = finalOnlineForm == null && record.getPublishedIssue() == 0;
            if (emptyDates || spd) {
                // actual publication dates are supported, final dates were not set
                buildIssueAndVolume(predictIssue, ISSUE_CURRENT, VOLUME_CURRENT, sb);
                if (record.getSelfCitationIssue() != 0) {
                    // presumably amended content
                    buildIssueAndVolume(record.getSelfCitationIssue(),
                            ISSUE_SELF_CITATION, VOLUME_SELF_CITATION, sb);
                } else {
                    // presumably new content (new DOI)
                    buildIssueAndVolume(predictIssue, ISSUE_SELF_CITATION, VOLUME_SELF_CITATION, sb);
                }
                if (record.getCitationIssue() != 0) {
                    buildIssueAndVolume(record.getCitationIssue(), ISSUE_CITATION, VOLUME_CITATION, sb);
                }

            } else if (record.getPublishedIssue() != 0 && finalOnlineForm != null) {
                // actual publication dates are supported, final dates were set
                buildIssueAndVolume(record.getPublishedIssue(), ISSUE_CURRENT, VOLUME_CURRENT, sb);
                if (record.getSelfCitationIssue() != 0) {
                    buildIssueAndVolume(record.getSelfCitationIssue(),
                            ISSUE_SELF_CITATION, VOLUME_SELF_CITATION, sb);
                }
                if (record.getCitationIssue() != 0) {
                    buildIssueAndVolume(record.getCitationIssue(), ISSUE_CITATION, VOLUME_CITATION, sb);
                }
            }
            // else it either legacy JATS where all publication properties were pre-set and converted from content
            // or new content where some publication properties are pre-set
        }

        private static void buildIssueAndVolume(int issueFullNumber, String issueTag, String volumeTag,
                                                StringBuilder builder) {
            if (issueFullNumber != 0) {
                builder.append(LINE_SEP_LT).append(issueTag).append(GT)
                        .append(CmsUtils.getIssueByIssueNumber(issueFullNumber))
                        .append(LT_CLOSE).append(issueTag).append(GT)
                        .append(LINE_SEP_LT).append(volumeTag).append(GT)
                        .append(CmsUtils.getYearByIssueNumber(issueFullNumber))
                        .append(LT_CLOSE).append(volumeTag).append(GT_LINE_SEP);
            }
        }

        private static void buildCloseTag(StringBuilder builder) {
            builder.append("\n").append(LT_CLOSE).append(PUB_FLOW).append(GT_LINE_SEP);
        }
    }
}
