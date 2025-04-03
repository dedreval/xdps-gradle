package com.wiley.cms.converter.services;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.ejb.EJB;
import javax.ejb.Local;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import com.wiley.cms.cochrane.cmanager.CmsUtils;
import com.wiley.cms.cochrane.converter.ml3g.JatsMl3gAssetsManager;
import org.apache.commons.lang.StringUtils;
import org.jdom.Content;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.output.XMLOutputter;
import org.jdom.xpath.XPath;

import com.wiley.cms.cochrane.cmanager.CmsException;
import com.wiley.cms.cochrane.cmanager.CochraneCMSBeans;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.DeliveryPackageInfo;
import com.wiley.cms.cochrane.cmanager.FilePathBuilder;
import com.wiley.cms.cochrane.cmanager.FilePathCreator;
import com.wiley.cms.cochrane.cmanager.MessageSender;
import com.wiley.cms.cochrane.cmanager.contentworker.ArchieEntry;
import com.wiley.cms.cochrane.cmanager.contentworker.RevmanPackage;
import com.wiley.cms.cochrane.cmanager.data.issue.IssueVO;
import com.wiley.cms.cochrane.cmanager.data.record.RecordEntity;
import com.wiley.cms.cochrane.cmanager.entitymanager.ICDSRMeta;
import com.wiley.cms.cochrane.cmanager.entitymanager.RecordHelper;
import com.wiley.cms.cochrane.cmanager.entitywrapper.ResultStorageFactory;
import com.wiley.cms.cochrane.cmanager.translated.TranslatedAbstractVO;
import com.wiley.cms.cochrane.converter.IConverterAdapter;
import com.wiley.cms.cochrane.converter.ml3g.Ml3gAssetsManager;
import com.wiley.cms.cochrane.meshtermmanager.IMeshtermUpdater;
import com.wiley.cms.cochrane.meshtermmanager.MeshtermUpdater;
import com.wiley.cms.cochrane.repository.IRepository;
import com.wiley.cms.cochrane.repository.RepositoryFactory;
import com.wiley.cms.cochrane.repository.RepositoryUtils;
import com.wiley.cms.cochrane.utils.Constants;
import com.wiley.cms.cochrane.utils.ErrorInfo;
import com.wiley.cms.cochrane.utils.ImageUtil;
import com.wiley.tes.util.Extensions;
import com.wiley.tes.util.Pair;
import com.wiley.cms.qaservice.services.IProvideQa;
import com.wiley.cms.qaservice.services.WebServiceUtils;
import com.wiley.tes.util.DbConstants;
import com.wiley.tes.util.ImageBase64;
import com.wiley.tes.util.Logger;
import com.wiley.tes.util.WileyDTDXMLOutputter;
import com.wiley.tes.util.XmlUtils;

import static com.wiley.cms.cochrane.process.handler.ContentHandler.getMl3gToMl3gParams;
import static com.wiley.cms.cochrane.utils.ImageUtil.THUMBNAIL_DIFF_PERCENT;
import static com.wiley.cms.cochrane.utils.ImageUtil.THUMBNAIL_HEIGHT;
import static com.wiley.cms.cochrane.utils.ImageUtil.THUMBNAIL_WIDTH;

/**
 * @author <a href='mailto:ikirov@wiley.com'>Igor Kirov</a>
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * @version 11.07.12
 */
@Stateless
@Local(IConversionProcess.class)
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class ConversionProcessImpl implements IConversionProcess {

    private static final Logger LOG = Logger.getLogger(ConversionProcessImpl.class);
    private static final String NORMAL_DIR = "normal_dir";
    private static final int VALIDATION_NO = 0;

    @EJB(beanName = "ConverterAdapter")
    private IConverterAdapter converter;
    private IRepository rp = RepositoryFactory.getRepository();
    private RevmanMetadataHelper rvh = new RevmanMetadataHelper(ResultStorageFactory.getFactory().getInstance());

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public List<ErrorInfo> convertTranslations(String trPath, String destPath, Map<String, TranslatedAbstractVO> recs,
        IssueVO issue, DeliveryPackageInfo result, Set<String> includedNames) throws Exception {
        GroupConversion conversion = new TranslationConversion(trPath, destPath, recs, includedNames,
                CochraneCMSPropertyNames.getRevmanValidation());
        conversion.convertGroup(issue, result);

        if (result != null && !result.isEmpty()) {
            rvh.rs.createRecords(result.getRecordPaths(), result.getDfId(),
                    result.getRecordsWithRawData(), result.getRecords().keySet(), null, true);
        }
        updateErrorRecords(conversion, null, result);
        return conversion.err;
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public List<ErrorInfo> convert(String srcPath, IssueVO issue, int packageId, String packName,
        DeliveryPackageInfo result, Set<String> includedNames) throws Exception {

        int ind = parsePath(srcPath, issue);
        GroupConversion conversion = new GroupConversion(srcPath, srcPath.substring(0, ind), includedNames,
                CochraneCMSPropertyNames.getRevmanValidation());
        Map<String, ICDSRMeta> metadata = conversion.convertGroup(issue, result);

        if (metadata != null && !metadata.isEmpty()) {

            updateErrorRecords(conversion, metadata, result);

            Set<String> delNames = new HashSet<>();
            Set<String> missedNames = new HashSet<>();
            Set<String> goodNames = new HashSet<>();
            for (ICDSRMeta rme: metadata.values()) {

                String recName = rme.getCdNumber();
                if (rme.isDeleted()) {
                    delNames.add(recName);
                    goodNames.add(recName);
                } else if (conversion.containsReview(recName)) {
                    goodNames.add(recName);
                } else {
                    missedNames.add(recName);
                }
            }
            if (!metadata.isEmpty()) {
                prepareSuccessful(goodNames, delNames, missedNames, metadata, conversion, packageId, result);
            }
        } else if (conversion.err.isEmpty() && !rp.isFileExists(FilePathCreator.getRevmanTopicSource(srcPath))) {
            conversion.err.add(new ErrorInfo(ErrorInfo.Type.EMPTY_CONTENT, true));
        }
        return conversion.err;
    }

    private void updateErrorRecords(GroupConversion conv, Map<String, ICDSRMeta> metadata, DeliveryPackageInfo result) {
        List<String> errNames = conv.err.isEmpty() ? null : new ArrayList<>();
        for (ErrorInfo err: conv.err) {

            ArchieEntry re = (ArchieEntry) err.getErrorEntity();
            if (errNames != null && err.isError()) {
                errNames.add(re.getName());
            }
            if (metadata == null) {
                continue;
            }
            ICDSRMeta rme = err.isError() ? metadata.remove(re.getName()) : metadata.get(re.getName());
            if (rme != null) {
                re.setPubNumber(rme.getPubNumber());
                re.setCochraneVersion(rme.getCochraneVersion());
            }
        }
        if (errNames != null && !errNames.isEmpty()) {
            CochraneCMSBeans.getRecordManager().setRecordFailed(RecordEntity.UNPUBLISHED_STATES,
                    result.getDbId(), errNames, false, false, false);
        }
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public void convertWithStrictValidation(ConversionData data) throws Exception {
        if (data.convertToRevman()) {
            prepare(data);
            parseMetadata(data);
            validateRevman(data);
            convertToWml(data);
            output(data);
        }
        if (data.convertJatsToWml3g()) {
            validateJats(data);
            validateSvg(data);
            convertJatsToWml3g(data);
            outputWml3g(data);
        }
        if (data.convertToWml3g()) {
            convertToWml3g(data);
            outputWml3g(data);
        }
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public List<ErrorInfo> convert(String srcPath, String destPath, Set<String> includedNames) throws Exception {
        return convert(srcPath, destPath, null, includedNames);
    }

    private int parsePath(String srcPath, IssueVO issue) throws CmsException {
        if (issue == null) {
            throw new CmsException("Issue entity is null.");
        }
        int ind = srcPath.indexOf(RevmanPackage.REVMAN_FOLDER);
        if (ind == -1) {
            throw new CmsException("Conversion sources must be stored in the 'revman' folder.");
        }
        return ind;
    }

    private void prepareSuccessful(Set<String> goodNames, Set<String> delNames, Set<String> missedNames,
        Map<String, ICDSRMeta> metadata, GroupConversion conv, int packageId, DeliveryPackageInfo result) {

        rvh.rs.createRecords(result.getRecordPaths(), packageId, result.getRecordsWithRawData(), goodNames,
                delNames, false);

        for (String recName: missedNames) {
            ICDSRMeta rme = metadata.remove(recName);
            ArchieEntry re = new ArchieEntry(recName, rme.getPubNumber(), rme.getCochraneVersion());
            conv.err.add(ErrorInfo.missingArticle(re));
        }

        if (!missedNames.isEmpty()) {
            CochraneCMSBeans.getRecordManager().setRecordFailed(RecordEntity.UNPUBLISHED_STATES,
                    result.getDbId(), missedNames, false, false, false);
        }
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public List<ErrorInfo> convert(String srcPath, String destPath, IssueVO issue, Set<String> includedNames)
            throws Exception {

        File[] srcDir = rp.getFilesFromDir(srcPath);
        if (srcDir == null) {
            throw new Exception(String.format("Source folder %s is not exists.", srcPath));
        }
        if (srcDir.length == 0) {
            throw new Exception(String.format("Source folder %s is empty.", srcPath));
        }
        GroupConversion conversion = new GroupConversion(srcPath, destPath, includedNames,
            CochraneCMSPropertyNames.getRevmanValidation());
        for (File group: srcDir) {
            if (!group.isFile()) {
                conversion.setSrcPath(srcPath + group.getName());
                conversion.convertGroup(issue);
            }
        }
        boolean meshStart = false;
        IMeshtermUpdater meshtermUpdater = MeshtermUpdater.Factory.getFactory().getInstance();
        Map<String, String> sourcePaths = new HashMap<>();
        for (String key: conversion.reviewNames.keySet()) {
            String patch = conversion.reviewNames.get(key);
            if (patch == null) {
                continue;
            }
            sourcePaths.put(key, patch);
            if (sourcePaths.size() == DbConstants.DB_QUERY_VARIABLE_COUNT) {
                updateMeshterms(sourcePaths, meshtermUpdater, meshStart);
                meshStart = true;
                sourcePaths.clear();
            }
        }
        if (!sourcePaths.isEmpty()) {
            updateMeshterms(sourcePaths, meshtermUpdater, meshStart);
        }
        if (meshStart) {
            LOG.debug("meshterm updating has completed");
        }
        return conversion.err;
    }

    private void updateMeshterms(Map<String, String> sourcePaths, IMeshtermUpdater meshtermUpdater, boolean meshStart)
            throws Exception {
        if (!meshStart) {
            LOG.debug("meshterm updating is starting...");
        }
        meshtermUpdater.updateMeshterms(sourcePaths);
    }

    private void validateRevman(ConversionData data) throws Exception {
        String errors;
        try {
            errors = !CochraneCMSPropertyNames.isRevmanPdfValidation() ? "" : converter.validate(
                    data.getSourceXml(), RevmanMetadataHelper.getMappedType(data.getReviewType(), false));
        } catch (Exception e) {
            throw new Exception("Failed to validate Revman xml, converter returns exception", e);
        }
        if (StringUtils.isNotEmpty(errors)) {
            throw new Exception("Revman xml is invalid, validation returns following errors: " + errors);
        }
    }

    private void validateJats(ConversionData data) throws Exception {
        String errors;
        try {
            errors = converter.validate(data.getSourceXml(), IConverterAdapter.JATS_XSD);
        } catch (Exception e) {
            throw new Exception("Failed to validate JATS xml, converter returns exception", e);
        }
        if (StringUtils.isNotEmpty(errors)) {
            throw new Exception("Jats xml is invalid, validation returns following errors: " + errors);
        }
    }

    private void validateSvg(ConversionData data) throws Exception {
        JatsMl3gAssetsManager.validateSVG(data.getContentDir(), rp);
    }

    private void convertJatsToWml3g(ConversionData data) throws Exception {
        try {
            boolean strictValidation = CochraneCMSPropertyNames.isJatsPdfStrictValidation();
            String result1 = converter.convertJats(
                            data.getJatsXmlPath(), IConverterAdapter.JATS_CDSR, IConverterAdapter.WILEY_ML3G,
                            null, null, strictValidation);

            String packageDescriptor = JatsMl3gAssetsManager.createPackageDescriptor(data);
            writeResult(packageDescriptor, null, Constants.PACKAGE_DESCRIPTOR,
                        data.getTempDir() + FilePathCreator.SEPARATOR, null);

            String finalResult = converter.convertSource(
                            result1, IConverterAdapter.WILEY_ML3G, IConverterAdapter.WILEY_ML3G, getMl3gToMl3gParams(),
                            getMl3gToMl3gExtraXmls(packageDescriptor), strictValidation);
            if (finalResult != null && finalResult.trim().length() > 0) {
                data.setResultContainerXml(finalResult);
            }
        } catch (Exception e) {
            throw new Exception(String.format("Failed to perform JATS to WML3G conversion for %s", data.getName()), e);
        }
    }

    private Map<String, String> getMl3gToMl3gExtraXmls(String packageDescriptor) {
        Map<String, String> extraXmls = new HashMap<>(1);
        extraXmls.put("conv-package-descriptor", packageDescriptor);
        return extraXmls;
    }

    private void convertToWml3g(ConversionData data) throws Exception {
        try {
            List<String> srcPaths = new ArrayList<>();
            String path = rp.getRealFilePath(data.getTempDir() + FilePathCreator.SEPARATOR + data.getWml21Name()
                                + Extensions.XML);
            srcPaths.add(path);
            Map<String, String> errsBySrcPath = new HashMap<>();
            List<String> relativeAssetsUris = Ml3gAssetsManager.getAssetsRelativeUris(getAssets(data), data.getName());
            List<String> results = converter.convertBatch(srcPaths,
                                                          IConverterAdapter.WILEY_ML21, IConverterAdapter.WILEY_ML3G_SA,
                                                          CochraneCMSPropertyNames.getCDSRDbName(),
                                                          Collections.emptyMap(), relativeAssetsUris, errsBySrcPath);
            String result = results.isEmpty() ? null : results.get(0);
            if (result != null && result.trim().length() > 0) {
                data.setResultContainerXml(result);

            } else if (!errsBySrcPath.isEmpty()) {
                throw new Exception(errsBySrcPath.get(path));
            } else {
                throw new Exception("empty result");
            }
        } catch (Exception e) {
            throw new Exception("Failed to perform WML21 to WML3G conversion", e);
        }
    }

    private List<String> getAssets(ConversionData data) {
        List<String> assetsUris = new ArrayList<>();
        String dir = data.getContentDir();
        File fl = new File(rp.getRealFilePath(dir));
        File[] assetFolders = fl.listFiles();
        if (assetFolders == null) {
            return assetsUris;
        }
        for (File folder: assetFolders) {
            if (!folder.isDirectory()) {
                continue;
            }
            File[] assetFiles = folder.listFiles();
            if (assetFiles == null) {
                continue;
            }
            for (File assetFile: assetFiles) {
                assetsUris.add(assetFile.getAbsolutePath());
            }
        }
        return assetsUris;
    }

    private void convertToWml(ConversionData data) throws Exception {
        try {
            data.setSourceContainerXml(prepare(data.getSourceXml(), data.getMetadataXml()));
        } catch (Exception e) {
            throw new Exception("Failed to prepare data for Revman to WML21 conversion", e);
        }
        try {
            data.setResultContainerXml(converter.convertRevman(data.getSourceContainerXml(),
                    RevmanMetadataHelper.getMappedType(data.getReviewType(), false), data.getParams()));
        } catch (Exception e) {
            throw new Exception("Failed to perform Revman to WML21 conversion", e);
        }
    }

    private void output(Map<String, String> results, String toDir, DeliveryPackageInfo dpi,
        Map<String, String> ret, List<ErrorInfo> err, Map<String, TranslatedAbstractVO> trMap) {
        for (String path: results.keySet()) {
            try {
                if (trMap != null) {
                    TranslatedAbstractVO vo = trMap.get(RepositoryUtils.getLastNameByPath(path));
                    String name = vo.getName();
                    writeResult(rvh.dl.load(results.get(path)), name, FilePathBuilder.buildTAName(
                            vo.getLanguage(), name), toDir, dpi);
                    if (dpi != null) {
                        RecordHelper.copyWML21(name, FilePathBuilder.getPathToIssuePackage(dpi.getIssueId(),
                            dpi.getDbName(), dpi.getDfId()), FilePathBuilder.getPathToEntireSrc(dpi.getDbName()), dpi);
                    }
                    continue;
                }
                output(results.get(path), toDir, dpi, ret);
            } catch (Exception e) {
                RevmanMetadataHelper.prepareError(path, ErrorInfo.Type.REVMAN_CONVERSION, e.getMessage(), err);
            }
        }
    }

    private void outputWml3g(ConversionData dt) throws Exception {
        try {
            if (dt.getResultContainerXml() == null) {
                throw new Exception("no conversion WML3G results");
            }
            dt.setWml3gXml(writeResult(dt.getResultContainerXml(), null, dt.getName(),
                    dt.getTempDir() + FilePathCreator.SEPARATOR, null));

        } catch (Exception e) {
            throw new Exception("Failed to write Revman to WML3G conversion results to filesystem: " + e.getMessage());
        }
    }

    private void output(ConversionData dt) throws Exception {
        try {
            if (dt.getResultContainerXml() == null) {
                throw new Exception("no conversion results");
            }
            Document xmlDoc = rvh.dl.load(dt.getResultContainerXml());
            dt.setName(RevmanMetadataHelper.getUnitName(xmlDoc));
            dt.setRawDataExists(writeRawData(xmlDoc, dt.getName(), dt.getContentDir(), null));
            writeAnalysesData(xmlDoc, dt.getName(), dt.getContentDir(), null);
            writeImages(xmlDoc, dt.getName(), dt.getContentDir(), null);
            dt.setWml21Xml(writeResult(xmlDoc, null, dt.getWml21Name(), dt.getTempDir()
                    + FilePathCreator.SEPARATOR, null));
        } catch (Exception e) {
            throw new Exception("Failed to write Revman to WML21 conversion results to filesystem: " + e.getMessage());
        }
    }

    private void output(String res, String contDir, DeliveryPackageInfo dpi, Map<String, String> ret) throws Exception {
        Document xmlDoc = rvh.dl.load(res);
        String name = RevmanMetadataHelper.getUnitName(xmlDoc);
        String recordDir = contDir + name;
        rp.deleteDir(recordDir);
        writeRawData(xmlDoc, name, recordDir, dpi);
        writeAnalysesData(xmlDoc, name, recordDir, dpi);
        writeImages(xmlDoc, name, recordDir, dpi);
        writeResult(xmlDoc, name, name, contDir, dpi);
        if (ret != null) {
            ret.put(name, recordDir + Extensions.XML);
        }
    }

    private boolean writeRawData(Document xmlDoc, String name, String contentDir, DeliveryPackageInfo dpi)
        throws Exception {
        Element rawDataContainer = (Element) XPath.selectSingleNode(xmlDoc, "CONTAINER/CONTAINER_RAW_DATA");
        if (rawDataContainer == null) {
            return false;
        }
        boolean ret = false;
        Element reviewRawData = rawDataContainer.getChild(RevmanMetadataHelper.REVIEW_EL);
        if (reviewRawData != null) {
            String dir = rawDataContainer.getAttributeValue(NORMAL_DIR);
            String href = FilePathBuilder.buildRawDataPathByDir(dir, name);
            Document reviewRawDataDoc = new Document().addContent(reviewRawData.detach());
            XMLOutputter xout = new XMLOutputter();
            String path = contentDir + FilePathCreator.SEPARATOR + href;
            rp.putFile(path, new ByteArrayInputStream(xout.outputString(reviewRawDataDoc).getBytes(
                    StandardCharsets.UTF_8)));
            if (dpi != null) {
                dpi.addFile(name, path);
            }
            ret = true;
        }

        return ret;
    }

    private void writeAnalysesData(Document xmlDoc, String name, String contentDir, DeliveryPackageInfo dpi)
        throws Exception {
        Element rawDataContainer = (Element) XPath.selectSingleNode(xmlDoc, "CONTAINER/CONTAINER_CUT_DOWN");
        if (rawDataContainer == null) {
            return;
        }
        Element reviewRawData = rawDataContainer.getChild("COCHRANE_REVIEW");
        if (reviewRawData != null) {
            String dir = rawDataContainer.getAttributeValue(NORMAL_DIR);
            String href = dir + FilePathCreator.SEPARATOR + name + "StatsDataOnly.rm5";
            XMLOutputter xout = new XMLOutputter();
            Document reviewRawDataDoc = new Document().addContent(reviewRawData.detach());
            String path = contentDir + FilePathCreator.SEPARATOR + href;
            rp.putFile(path, new ByteArrayInputStream(xout.outputString(reviewRawDataDoc).getBytes(
                    StandardCharsets.UTF_8)));
            if (dpi != null) {
                dpi.addFile(name, path);
            }
        }
    }

    private void writeImages(Document xmlDoc, String name, String contentDir, DeliveryPackageInfo dpi)
        throws Exception {
        Element imagesContainer = (Element) XPath.selectSingleNode(xmlDoc, "CONTAINER/CONTAINER_IMAGES");
        if (imagesContainer == null) {
            return;
        }
        String imPath = null;
        String imFilter = null;
        boolean useImageMagic = CochraneCMSPropertyNames.isImageMagicUse();
        boolean useExtent = false;

        boolean canResize = false;
        if (useImageMagic) {
            imPath = CochraneCMSPropertyNames.getImageMagicPath();
            imFilter = CochraneCMSPropertyNames.getImageMagicFilter();
            canResize = CochraneCMSPropertyNames.isImageMagicResize();
            useExtent = "extent".equals(CochraneCMSPropertyNames.getImageMagicCmd());
        }

        String normalDir = contentDir + FilePathCreator.SEPARATOR + imagesContainer.getAttributeValue(NORMAL_DIR);
        String thumbnailDir = contentDir + FilePathCreator.SEPARATOR
                + imagesContainer.getAttributeValue("thumbnail_dir");
        List images = imagesContainer.getChildren("IMAGE");
        for (Object imgObj : images) {
            Element image = (Element) imgObj;
            String normalType = image.getAttributeValue("normal_type");
            String normalName = image.getAttributeValue("normal_name") + "." + normalType;
            String thumbnailType = image.getAttributeValue("thumbnail_type");
            String thumbnailName = image.getAttributeValue("thumbnail_name") + "." + thumbnailType;
            String normalPath = normalDir + FilePathCreator.SEPARATOR + normalName;
            String thumbnailPath = thumbnailDir + FilePathCreator.SEPARATOR + thumbnailName;

            byte[] imgBytes = new ImageBase64(image.getTextTrim()).decode();

            List<Exception> eList = new ArrayList<>();
            BufferedImage img = ImageUtil.loadImage(imgBytes, eList);
            if (img == null) {
                if (imgBytes.length > 0)  {
                    rp.putFile(normalPath, new ByteArrayInputStream(imgBytes));
                }
                LOG.error("Cannot read image, raw data written for " + normalName);
            }
            if (eList.size() > 0) {
                LOG.error("ImageReader errors occurred for " + normalName);
                for (Exception e : eList) {
                    LOG.error(e);
                }
            }
            if (img != null) {
                ImageUtil.writeNormal(img, normalType, normalPath);
                if (dpi == null || !useImageMagic) {
                    ImageUtil.writeThumbnail(img, thumbnailType, thumbnailPath, THUMBNAIL_WIDTH,
                            THUMBNAIL_HEIGHT, THUMBNAIL_DIFF_PERCENT);

                } else if (canResize && ImageUtil.canResize(img.getWidth(), img.getHeight(), THUMBNAIL_WIDTH,
                        THUMBNAIL_HEIGHT, THUMBNAIL_DIFF_PERCENT)) {
                    ImageUtil.makeThumbnailResize(normalPath, thumbnailDir, thumbnailName,
                            THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT, imPath, imFilter);

                } else if (useExtent) {
                    ImageUtil.makeThumbnailExtent(normalPath, thumbnailDir, thumbnailName,
                            THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT, imPath, imFilter);

                } else {
                    ImageUtil.makeThumbnailCrop(normalPath, thumbnailDir, thumbnailName,
                            THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT, imPath, imFilter);
                }
                if (dpi != null) {
                    dpi.addFile(name, normalPath);
                    dpi.addFile(name, thumbnailPath);
                }
            }
        }
    }

    private String writeResult(Document xmlDoc, String name, String fileName, String tempDir, DeliveryPackageInfo dpi)
        throws Exception {
        return writeResult(WileyDTDXMLOutputter.output(xmlDoc), name, fileName, tempDir, dpi);
    }

    private String writeResult(String result, String name, String fileName, String tempDir, DeliveryPackageInfo dpi)
        throws Exception {
        String path = tempDir + fileName + Extensions.XML;
        rp.putFile(path, new ByteArrayInputStream(result.getBytes(StandardCharsets.UTF_8)));
        if (dpi != null) {
            dpi.addFile(name, path);
            dpi.addRecordPath(fileName, path);
        }
        return result;
    }

    private void prepare(ConversionData data) throws Exception {
        try {
            data.setReviewType(rvh.getReviewType(data.getSourceXml()));
            if (!RevmanMetadataHelper.containsMappedType(data.getReviewType())) {
                throw new IllegalArgumentException("Unknown review type " + data.getReviewType());
            }
        } catch (Exception e) {
            throw new Exception("Failed to get review type", e);
        }
    }

    private void parseMetadata(ConversionData data) throws Exception {
        if (CochraneCMSPropertyNames.isRevmanPdfValidation()) {
            IProvideQa qaProvider = WebServiceUtils.getProvideQa();
            String qaResult = qaProvider.check(data.getMetadataXml(), IProvideQa.REVMAN_METADATA_PROFILE,
                    CochraneCMSPropertyNames.getCDSRDbName());

            StringBuilder errs = new StringBuilder();
            errs.append("Validation of metadata completed with errors. ");
            boolean success;
            try {
                success = CmsUtils.getErrorsFromQaResults(qaResult, errs, rvh.dl);
            } catch (Exception e) {
                success = false;
            }
            if (!success) {
                throw new Exception(errs.toString());
            }
        }
        try {
            data.setParams(rvh.parseMetadata(data.getMetadataXml()));
        } catch (Exception e) {
            throw new Exception("Failed to parse metadata xml", e);
        }
    }

    private String prepare(File review, Element metadata, Document localTopicsXmlDoc, List<Document> topicsXmlDocs)
        throws Exception {
        return prepare(rvh.dl.load(review), metadata,
            localTopicsXmlDoc == null ? null : (Element) localTopicsXmlDoc.getRootElement().clone(), topicsXmlDocs);
    }

    private String prepare(String xml, String metadata) throws Exception {
        Document metadataXmlDoc = rvh.dl.load(metadata);
        return prepare(rvh.dl.load(xml), metadataXmlDoc.getRootElement().detach(), null, null);
    }

    private String prepare(Document sourceXmlDoc, Content metadata, Element localTopics, List<Document> topicsXmlDocs) {
        Element root = new Element(XmlUtils.CONTAINER);
        root.addContent(sourceXmlDoc.getRootElement().detach());

        if (localTopics != null) {
            root.addContent(new Element("CONTAINER_LOCAL_TOPICS").addContent((Content) localTopics.clone()));
        }
        if (topicsXmlDocs != null) {
            for (Document doc: topicsXmlDocs) {
                root.addContent(new Element("CONTAINER_TOPICS").addContent((Element) doc.getRootElement().clone()));
            }
        }
        root.addContent(new Element("CONTAINER_METADATA").addContent((Content) metadata.clone()));

        XMLOutputter xout = new XMLOutputter();
        return xout.outputString(root);
    }

    private Map<String, ICDSRMeta> extractRecordMetadataEntities(Document metadataXmlDoc,
        Map<String, Pair<Element, String[]>> metadata, IssueVO issue, List<ErrorInfo> err, Set<String> includedNames,
        String rvsPath) throws Exception {

        Map<String, ICDSRMeta> ret = new HashMap<>();
        for (Object obj : XPath.selectNodes(metadataXmlDoc, RevmanMetadataHelper.REVIEW_XPATH)) {
            Element el = ((Element) obj);
            String id = el.getAttributeValue(RevmanMetadataHelper.CD_NUMBER_ATTR);
            if (includedNames != null && !includedNames.contains(id)) {
                continue;
            }
            try {
                ICDSRMeta meta = rvh.createMetadata4Upload(id, el, metadataXmlDoc, metadata, issue.getFullNumber());
                rp.putFile(rvsPath + FilePathBuilder.buildMetadataRecordName(id),
                        new ByteArrayInputStream(new XMLOutputter().outputString(el).getBytes(StandardCharsets.UTF_8)));
                if (meta != null) {
                    ret.put(meta.getCdNumber(), meta);
                }
            } catch (CmsException ce) {
                handleErrorMsg(id, ce.hasErrorInfo() ? ce.getErrorInfo() : new ErrorInfo<>(
                    new ArchieEntry(id), ce.getMessage()), el, err, metadata, ret);
            } catch (Exception e) {
                handleErrorMsg(id, new ErrorInfo<>(new ArchieEntry(id), e.getMessage()), el, err, metadata, ret);
            }
        }
        return ret;
    }

    private void getRecordMetadataEntity(String id, Map<String, Pair<Element, String[]>> metadata,
        Map<String, ICDSRMeta> ret, IssueVO issue, Integer v, boolean ta, List<ErrorInfo> err) {
        try {
            ICDSRMeta meta = rvh.getMetadata4Reconversion(id, metadata, issue, v, ta);
            ret.put(meta.getCdNumber(), meta);
        } catch (CmsException ce) {
            handleErrorMsg(id, ce.hasErrorInfo() ? ce.getErrorInfo() : new ErrorInfo<>(
                new ArchieEntry(id), ce.getMessage()), null, err, metadata, ret);
        } catch (Exception e) {
            handleErrorMsg(id, new ErrorInfo<>(new ArchieEntry(id), e.getMessage()), null, err, metadata, ret);
        }
    }

    private Map<String, ICDSRMeta> getRecordMetadata(File[] reviews,
        Map<String, Pair<Element, String[]>> metadata, IssueVO issue, Integer version, List<ErrorInfo> err,
        Set<String> includedNames) {

        Map<String, ICDSRMeta> ret = new HashMap<>();
        for (File review : reviews) {
            String id = RevmanPackage.getRecordNameByFileName(review.getName());
            if (includedNames != null && !includedNames.contains(id)) {
                continue;
            }
            getRecordMetadataEntity(id, metadata, ret, issue, version, false, err);
        }
        return ret;
    }

    private Document findTopic(String srcPath) throws IOException, JDOMException {
        String topicsPath = FilePathCreator.getRevmanTopicSource(srcPath);
        if (rp.isFileExists(topicsPath)) {
            return rvh.dl.load(rp.getFile(topicsPath));
        }
        topicsPath = FilePathBuilder.getPathToTopics(RepositoryUtils.getLastNameByPath(srcPath)); // take from entire
        Document topicsXmlDoc = null;
        if (rp.isFileExists(topicsPath)) {
            topicsXmlDoc = rvh.dl.load(rp.getFile(topicsPath));
        }
        return topicsXmlDoc;
    }

    private Document getTopics(String srcPath, List<Document> topicsXmlDocs) throws IOException, JDOMException {
        Document topicsXmlDoc = findTopic(srcPath);
        File[] parentDirs = topicsXmlDoc == null ? null : new File(
                rp.getRealFilePath(srcPath)).getParentFile().listFiles();
        if (parentDirs == null) {
            LOG.error(String.format("no topics.xml has found in %s", srcPath));
            return null;
        }
        for (File topicDir: parentDirs) {
            if (!topicDir.isDirectory()) {
                continue;
            }
            File topics = new File(topicDir, Constants.TOPICS_SOURCE);
            if (topics.exists())  {
                topicsXmlDocs.add(rvh.dl.load(topics));
            }
        }
        return topicsXmlDoc;
    }

    private void handleErrorMsg(String id, ErrorInfo ei, Element el, List<ErrorInfo> err,
        Map<String, Pair<Element, String[]>> metadata, Map<String, ICDSRMeta> ret) {

        RecordHelper.handleErrorMessage(ei, err);
        metadata.put(id, new Pair<>(el , null));
        ret.put(id, null);
    }

    private void handleErrorMsg(String id, List<ErrorInfo> err, String errMsg, String srcPath) {
        for (ErrorInfo ei: err) {
            ArchieEntry re = (ArchieEntry) ei.getErrorEntity();
            if (id.equals(re.getName())) {
                return;
            }
        }
        LOG.error(errMsg);
        err.add(new ErrorInfo<>(new ArchieEntry(id), ErrorInfo.Type.METADATA,
            String.format("Metadata is not exist - %s", srcPath), "metadata is not exist"));
    }

    private class TranslationConversion extends GroupConversion {

        private Map<String, TranslatedAbstractVO> translationsMap;

        TranslationConversion(String srcPath, String destPath, Map<String, TranslatedAbstractVO> includedFiles,
                              Set<String> includedNames, int check) {
            super(srcPath, destPath, includedNames, check > 1 ? check : 0);
            translationsMap = includedFiles;
        }

        @Override
        protected Integer getPrevNum() {
            return FilePathBuilder.isPreviousPath(srcPath)
                    ? FilePathBuilder.cutOffPreviousVersion(srcPath, true) : null;
        }

        @Override
        protected Map<String, ICDSRMeta> convertGroup(IssueVO issue, DeliveryPackageInfo result)
            throws Exception {
            File[] translations = new File(rp.getRealFilePath(srcPath)).listFiles(new FilenameFilter() {
                    public boolean accept(File dir, String name) {
                        return translationsMap.get(name) != null
                            && (includedNames == null || includedNames.contains(translationsMap.get(name).getName()));
                    }
                }
            );
            if (translations == null) {
                throw new Exception(String.format("%s doesn't exist", srcPath));
            }
            Map<String, ICDSRMeta> metadataEntities = new HashMap<>();
            processTranslations(issue, translations, metadataEntities, result);

            if (!metadataEntities.isEmpty()) {
                LOG.info(String.format(MessageSender.MSG_RM_CONVERTED, translations.length, err.size(), srcPath));
            }
            return metadataEntities;
        }

        @Override
        protected Map<String, ICDSRMeta> convertGroup(IssueVO issue) {
            throw new UnsupportedOperationException();
        }

        private void processTranslations(IssueVO issue, File[] records, Map<String, ICDSRMeta> entities,
            DeliveryPackageInfo result) throws Exception {

            Map<String, Pair<List<String>, Map<String, RvConversionData>>> sources = new HashMap<>();
            int batch = batchSize == 0 ? records.length : batchSize;
            Map<String, Pair<Element, String[]>> metadata = new HashMap<>();

            for (File rv: records) {
                String fName = rv.getName();
                TranslatedAbstractVO tvo = translationsMap.get(fName);
                String id = tvo.getName();
                if (tvo.isDeleted()) {
                    if (result != null) {
                        RecordHelper.copyWML21(id, FilePathBuilder.getPathToIssuePackage(result.getIssueId(),
                            result.getDbName(), result.getDfId()),
                                FilePathBuilder.getPathToEntireSrc(result.getDbName()), result);
                    }
                    continue;
                }
                if (!entities.containsKey(id)) {
                    IssueVO issueVo = null;
                    if (result != null && rp.isFileExists(FilePathBuilder.RM.getPathToRevmanMetadata(
                            result.getIssueId(), result.getCurrentGroup(), id))) {
                        issueVo = issue;
                    }
                    getRecordMetadataEntity(id, metadata, entities, issueVo, prevNum, true, err);
                }
                ICDSRMeta rm = entities.get(id);
                if (rm == null) {
                    RevmanMetadataHelper.prepareLastError(fName, err);
                    continue;
                }
                String format = "TR_" + RevmanMetadataHelper.getMappedType(rm.getType(), false);
                Pair<List<String>, Map<String, RvConversionData>> source = addToConvert(srcPath + fName, format,
                    sources, rv, metadata.get(id), batch);
                if (source != null) {
                    convertRM(format, sources.get(format), null, null, result);
                }
            }
            convertRM(sources, null, null, result);
        }

        @Override
        protected String setPath(RvConversionData rv, Document top, List<Document> tops, String path, String rvPath) {
            return rvPath;
        }

        @Override
        protected void outputResults(Map<String, String> results, DeliveryPackageInfo callBack) {
            output(results, destPath, callBack, null, err, translationsMap);
        }
    }

    private class GroupConversion {
        protected String srcPath;
        protected final int batchSize;
        protected Set<String> includedNames;
        protected final List<ErrorInfo> err = new ArrayList<>();
        protected String destPath;
        protected final Integer prevNum;
        private final int check;
        private Map<String, String> reviewNames = new HashMap<>();

        private GroupConversion(String srcPath, String destPath, Set<String> included, int check) {
            this.srcPath = srcPath;
            this.destPath = destPath;
            this.check = check;
            this.batchSize = CochraneCMSPropertyNames.getRevmanConversionBatchSize();
            this.includedNames = included;
            prevNum = getPrevNum();
        }

        protected Integer getPrevNum() {
            return FilePathBuilder.isPreviousPath(srcPath)
                    ? FilePathBuilder.cutOffPreviousVersion(srcPath, false) : null;
        }

        private void setSrcPath(String srcPath) {
            this.srcPath = srcPath;
        }

        private boolean containsReview(String name) {
            return reviewNames.containsKey(name);
        }

        protected Map<String, ICDSRMeta> convertGroup(IssueVO issue) throws Exception {
            File[] reviews = rp.getFilesFromDir(FilePathCreator.getDirForRevmanReviews(srcPath));
            if (reviews == null || reviews.length <= 0) {
                return null;
            }
            Map<String, Pair<Element, String[]>> metadata = new HashMap<>();
            Map<String, ICDSRMeta> metadataEntities = getRecordMetadata(reviews, metadata,
                    issue, prevNum, err, includedNames);
            processReviews(reviews, metadata, metadataEntities, null);

            if (!metadataEntities.isEmpty()) {
                LOG.info(String.format(MessageSender.MSG_RM_CONVERTED, metadataEntities.size(), err.size(), srcPath));
            }
            return metadataEntities;
        }

        protected Map<String, ICDSRMeta> convertGroup(IssueVO issue, DeliveryPackageInfo result)
            throws Exception {

            String rvsPath = FilePathCreator.getDirForRevmanReviews(srcPath) + FilePathCreator.SEPARATOR;
            File[] reviews = rp.getFilesFromDir(rvsPath);
            boolean reviewExist = reviews != null && reviews.length > 0;
            String metadataPath = FilePathCreator.getRevmanMetadataSource(srcPath);
            if (!rp.isFileExists(metadataPath)) {
                if (!reviewExist) {
                    return null;
                }
                throw new CmsException("Revman metadata.xml is not exist.");
            }
            Document metadataXmlDoc = rvh.dl.load(rp.getFile(metadataPath));
            Map<String, Pair<Element, String[]>> metadata = new HashMap<>();
            Map<String, ICDSRMeta> metadataEntities = extractRecordMetadataEntities(
                metadataXmlDoc, metadata, issue, err, includedNames, rvsPath);
            if (reviewExist) {
                processReviews(reviews, metadata, metadataEntities, result);
            }
            if (!metadataEntities.isEmpty()) {
                LOG.info(String.format(MessageSender.MSG_RM_CONVERTED, metadataEntities.size(), err.size(), srcPath));
            }
            return metadataEntities;
        }

        private void processReviews(File[] reviews, Map<String, Pair<Element, String[]>> metadata,
            Map<String, ICDSRMeta> entities, DeliveryPackageInfo result) throws Exception {

            List<Document> topicsXmlDocs = new ArrayList<>();
            Document topicsXmlDoc = getTopics(srcPath, topicsXmlDocs);
            Map<String, Pair<List<String>, Map<String, RvConversionData>>> sources = new HashMap<>();

            int batch = batchSize == 0 ? reviews.length : batchSize;
            String rvsPath = FilePathCreator.getDirForRevmanReviews(srcPath) + FilePathCreator.SEPARATOR;

            for (File rv: reviews) {
                if (rv.isDirectory()) {
                    continue;
                }
                String fName = rv.getName();
                if (!fName.endsWith(Extensions.XML)) {
                    continue;
                }             
                String id = RevmanPackage.getRecordNameByFileName(fName);
                if (includedNames != null && !includedNames.contains(id)) {
                    continue;
                }
                Pair<Element, String[]> metaParams = metadata.get(id);
                if (metaParams == null) {
                    handleErrorMsg(id, err, String.format("Metadata of %s/%s doesn't exist", srcPath, fName), srcPath);
                    continue;
                } else if (metaParams.second == null) {
                    continue;
                }
                if (entities.get(id).isDeleted()) {
                    continue;
                }
                String format = RevmanMetadataHelper.getMappedType(entities.get(id).getType(), false);
                Pair<List<String>, Map<String, RvConversionData>> source = addToConvert(rvsPath + fName, format,
                        sources, rv, metaParams, batch);
                if (source != null) {
                    convertRM(format, sources.get(format), topicsXmlDocs, topicsXmlDoc, result);
                }
            }
            convertRM(sources, topicsXmlDocs, topicsXmlDoc, result);
        }

        protected void convertRM(Map<String, Pair<List<String>, Map<String, RvConversionData>>> sources,
            List<Document> topicsXmlDocs, Document topicsXmlDoc, DeliveryPackageInfo result) throws Exception {

            for (String format: sources.keySet()) {
                Pair<List<String>, Map<String, RvConversionData>> source = sources.get(format);
                if (!source.first.isEmpty()) {
                    convertRM(format, source, topicsXmlDocs, topicsXmlDoc, result);
                }
            }
        }

        protected Pair<List<String>, Map<String, RvConversionData>> addToConvert(String path, String format,
            Map<String, Pair<List<String>, Map<String, RvConversionData>>> src, File rv, Pair<Element, String[]> params,
            int batch) {

            Pair<List<String>, Map<String, RvConversionData>> source = src.get(format);
            if (source == null) {
                source = new Pair<>(new ArrayList<>(), new HashMap<>());
                src.put(format, source);
            }
            source.first.add(path);
            source.second.put(path, new RvConversionData(rv, params));
            return source.second.size() == batch ? source : null;
        }

        protected void convertRM(String format, Pair<List<String>, Map<String, RvConversionData>> source,
            List<Document> topicsXmlDocs, Document topicsXmlDoc, DeliveryPackageInfo result) throws Exception {

            Set<String> paths = check > VALIDATION_NO
                    ? converter.validateRevmanBatch(source.first, format, err).keySet() : source.second.keySet();
            //Set<String> paths = source.second.keySet();
            List<String> rv4Paths = new ArrayList<>();
            List<String[]> rv4Params = new ArrayList<>();
            String path = FilePathCreator.getRevmanPackageInputDirPath(srcPath);  //todo - wrong for translations
            for (String rvPath: paths) {
                RvConversionData rv = source.second.get(rvPath);
                if (rv == null) {
                    LOG.error("unknown record: " + rvPath);
                    continue;
                }
                rv4Paths.add(setPath(rv, topicsXmlDoc, topicsXmlDocs, path, rvPath));
                rv4Params.add(rv.metaParams.second);
            }
            Map<String, String> convResults = converter.convertRevmanBatch(rv4Paths, format, rv4Params, err);
            rp.deleteDir(path);
            outputResults(convResults, result);
            source.first.clear();
            source.second.clear();
        }

        protected String setPath(RvConversionData rv, Document top, List<Document> tops, String base, String rvPath)
                throws Exception {
            String rv4Xml = prepare(rv.file, rv.metaParams.first, top, tops);
            String rv4Path = base + rv.file.getName();  // input
            rp.putFile(rv4Path, new ByteArrayInputStream(rv4Xml.getBytes()));
            reviewNames.put(rvh.getCdNumberValue(rv.metaParams.second), null);
            return rv4Path;
        }
               
        protected void outputResults(Map<String, String> results, DeliveryPackageInfo callBack) {
            output(results, destPath, callBack, reviewNames, err, null);
        }
    }
}