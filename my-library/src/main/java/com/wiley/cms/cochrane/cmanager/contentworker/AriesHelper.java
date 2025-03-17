package com.wiley.cms.cochrane.cmanager.contentworker;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.transform.Result;
import javax.xml.transform.Transformer;
import javax.xml.transform.stream.StreamResult;

import org.apache.commons.io.IOUtils;
import org.jdom.Document;
import org.jdom.transform.JDOMSource;

import com.wiley.cms.cochrane.activitylog.IFlowLogger;
import com.wiley.cms.cochrane.cmanager.AbstractZipPackage;
import com.wiley.cms.cochrane.cmanager.CmsException;
import com.wiley.cms.cochrane.cmanager.CochraneCMSBeans;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.FilePathBuilder;
import com.wiley.cms.cochrane.cmanager.data.DeliveryFileVO;
import com.wiley.cms.cochrane.cmanager.data.record.IRecord;
import com.wiley.cms.cochrane.cmanager.entitymanager.CDSRMetaVO;
import com.wiley.cms.cochrane.cmanager.entitymanager.RecordHelper;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.cmanager.res.PackageType;
import com.wiley.cms.cochrane.cmanager.res.PubType;
import com.wiley.cms.cochrane.process.IRecordCache;
import com.wiley.cms.cochrane.process.RecordCache;
import com.wiley.cms.cochrane.repository.IRepository;
import com.wiley.cms.cochrane.repository.RepositoryFactory;
import com.wiley.cms.cochrane.test.PackageChecker;
import com.wiley.cms.cochrane.utils.Constants;
import com.wiley.cms.cochrane.utils.ErrorInfo;
import com.wiley.cms.process.entity.DbEntity;
import com.wiley.tes.util.Extensions;
import com.wiley.tes.util.Logger;
import com.wiley.tes.util.TransformerObjectPool;
import com.wiley.tes.util.jdom.DocumentLoader;
import com.wiley.tes.util.res.Res;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 2/14/2021
 */
public class AriesHelper {
    private static final Logger LOG = Logger.getLogger(AriesHelper.class);

    private static final IPackageParser ARIES_PACKAGE_PARSER = new IPackageParser() {
        @Override
        public Object parseImportFile(PackageType.Entry entry, String zeName, InputStream zis) {
            return new AriesImportFile(zeName).parseImportFile(zis, false);
        }

        @Override
        public String parseManifest(PackageType.Entry entry, String path, InputStream zis) {
            return null;
        }

        @Override
        public void parseArticleZip(PackageType.Entry entry, Integer issueId, String zeName, ZipInputStream zis,
                                    boolean ta) throws Exception {
        }
    };

    private static final IPackageParser ARIES_ACK_PACKAGE_PARSER = new IPackageParser() {
        @Override
        public Object parseImportFile(PackageType.Entry entry, String zeName, InputStream zis) {
            return new AriesImportFile(zeName).parseImportFile(zis, true);
        }

        @Override
        public String parseManifest(PackageType.Entry entry, String path, InputStream zis) {
            return null;
        }

        @Override
        public void parseArticleZip(PackageType.Entry entry, Integer issueId, String zeName, ZipInputStream zis,
                                    boolean ta) throws Exception {
        }
    };

    AriesImportFile importFile;
    private AriesMetadataFile metadataFile;

    private final DocumentLoader documentLoader;
    private Transformer transformer4Import;
    private Transformer transformer4GO;

    public AriesHelper() {
        documentLoader = new DocumentLoader();
    }

    public AriesHelper(DocumentLoader loader) {
        documentLoader = loader;
    }

    public AriesImportFile getImportFile() {
        return importFile;
    }

    public DocumentLoader getDocumentLoader() {
        return documentLoader;
    }
        
    public static Map<String, List<DeliveryFileVO>> mapToManuscriptNumbers(Collection<DeliveryFileVO> dfs) {
        if (dfs.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, List<DeliveryFileVO>> ret = new HashMap<>();
        IRepository rp = RepositoryFactory.getRepository();
        for (DeliveryFileVO dfVO: dfs) {
            try {
                String srcFolder = FilePathBuilder.JATS.getPathToPackage(dfVO.getIssue(), dfVO.getId());
                String importFileName = RecordHelper.findAriesImportSource(srcFolder, rp);
                String manuscriptNumber = getManuscriptNumberByImportName(importFileName);
                ret.computeIfAbsent(manuscriptNumber, f -> new ArrayList<>()).add(dfVO);

            } catch (Exception e) {
                LOG.warn(e.getMessage());
            }
        }
        return ret;
    }

    public static String[] getJournalCodeAndTaskIdByDfName(String dfName) {
        return dfName.replace(Extensions.ZIP, "").split("_");
    }

    public static String getManuscriptNumberByImportName(String importName) {
        return importName.split("_")[0];
    }

    public static String buildGOFileName(String packageName) {
        return packageName.replace(Extensions.ZIP, Extensions.GO_XML);
    }

    public static AriesImportFile checkImportFile(File packageFile, PackageType packageType) throws Exception {
        AriesImportFile importFile = parseImportFile(packageFile, packageType, false);
        if (importFile == null) {
            LOG.warn(String.format("%s - cannot find an aries import xml to determine a production task value",
                    packageFile.getName()));
        }
        return importFile;
    }

    public static AriesImportFile parseImportFile(File file, PackageType packageType, boolean ack) throws Exception {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(file))) {
            ZipEntry ze;
            while ((ze = zis.getNextEntry()) != null) {
                PackageType.Entry entry = packageType.match(ze.getName());
                if (entry != null) {
                    Object ret = entry.getType().parseFinal(entry, ze.getName(), zis, ack ? ARIES_ACK_PACKAGE_PARSER
                            : ARIES_PACKAGE_PARSER, null);
                    if (ret instanceof AriesImportFile) {
                        return (AriesImportFile) ret;
                    }
                }
            }
        }
        return null;
    }

    private static AriesImportFile parseImportFile(File file) throws Exception {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(file))) {
            ZipEntry ze;
            while ((ze = zis.getNextEntry()) != null) {
                if (ze.getName().endsWith(Constants.ARIES_IMPORT_SUFFIX + Extensions.XML)) {
                    return new AriesImportFile(ze.getName()).parseImportFile(zis, false);
                }
            }
        }
        return null;
    }

    AriesImportFile parseImportFile(String packagePath, String zeName, InputStream zis, boolean spd,
                                    Supplier<List<ErrorInfo>> errorCollector, IRepository rp) {
        ByteArrayInputStream bais = null;
        importFile = new AriesImportFile(zeName);

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            IOUtils.copy(zis, baos);
            bais = new ByteArrayInputStream(baos.toByteArray());
            importFile.parseImportFile(bais, getDocumentLoader(), false);
            bais.reset();
            AbstractZipPackage.putFileToRepository(rp, bais, packagePath + zeName);

        } catch (Exception e) {
            RecordHelper.handleErrorMessage(new ErrorInfo<>(zeName, e.getMessage()), errorCollector.get());
        } finally {
            IOUtils.closeQuietly(bais);
        }
        importFile.checkSPDCancel(spd);
        return importFile;
    }

    void checkImportFile(GoManifest goManifest, CDSRMetaVO meta, String packageName) throws CmsException {
        if (importFile == null) {
            CmsException.throwMetadataError(meta, String.format(
                "%s has no 'production-task-description'", packageName));
        }
        if (importFile.error() != null) {
            CmsException.throwMetadataError(meta, importFile.error());
        }
        if (!goManifest.getProductionTaskId().equals(importFile.getProductionTaskId())) {
            CmsException.throwMetadataError(meta, String.format(
                "import submission %s does not relevant to the production task: %s",
                    importFile.getProductionTaskId(), goManifest.getProductionTaskId()));
        }
        if (meta != null) {
            checkImportFile(meta.getCdNumber(), meta);
        }
    }

    private void checkImportFile(String cdNumber, CDSRMetaVO meta) throws CmsException {
        BaseType btSource = importFile.getDbTypeByArticleType();
        Res<BaseType> bt = BaseType.find(
                RecordHelper.getDbNameByRecordNumber(RecordHelper.buildRecordNumber(cdNumber)));
        if (!Res.valid(bt) || !bt.get().getId().equals(btSource.getId())) {
            CmsException.throwMetadataError(meta, String.format(
                "article-type '%s' (%s) doesn't match to record name '%s' related to manuscript %s",
                    importFile.getArticleType(), btSource.getId(), cdNumber, importFile.getManuscriptNumber()));
        }
    }

    AriesMetadataFile parseMetadata(String packagePath, String zeName, InputStream zis,
                                    Supplier<List<ErrorInfo>> errorCollector, IRepository rp) {
        ByteArrayInputStream bais = null;
        if (metadataFile == null) {
            metadataFile = new AriesMetadataFile(zeName);
        }
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            IOUtils.copy(zis, baos);
            bais = new ByteArrayInputStream(baos.toByteArray());
            metadataFile.parseMetadataFile(bais, getDocumentLoader());
            bais.reset();
            AbstractZipPackage.putFileToRepository(rp, bais, packagePath + zeName);

        } catch (Exception e) {
            RecordHelper.handleErrorMessage(new ErrorInfo<>(zeName, e.getMessage()), errorCollector.get());
        } finally {
            IOUtils.closeQuietly(bais);
        }
        return metadataFile;
    }

    void checkMetadataFile(GoManifest goManifest, CDSRMetaVO meta) throws CmsException {
        if (metadataFile != null) {

            if (metadataFile.error() != null) {
                CmsException.throwMetadataError(meta, metadataFile.error());
            }
            if (!metadataFile.getFileName().equals(goManifest.getMetadataFileName())) {
                CmsException.throwMetadataError(meta, String.format(
                    "metadata file name %s is not equal to the %s in %s",
                        metadataFile.getFileName(), goManifest.getMetadataFileName(), Extensions.GO_XML));
            }
            if (!metadataFile.getManuscriptNumber().equals(importFile.getManuscriptNumber())) {
                CmsException.throwMetadataError(meta, String.format(
                    "metadata manuscript number %s is not equal to the %s in %s", metadataFile.getManuscriptNumber(),
                        importFile.getManuscriptNumber(), importFile.getFileName()));
            }
            //meta.setHighProfile(metadataFile.isHighProfile());
        }
    }

    public boolean isDeliver() {
        return importFile != null && importFile.isDeliver();
    }

    public boolean isPublish() {
        return importFile != null && importFile.isPublish();
    }

    public boolean isCancel() {
        return importFile != null && importFile.isCancel();
    }

    public int getHighPriority() {
        return metadataFile == null ? 0 : addHighProfile(metadataFile.isHighProfile(),
                addHighFrequency(metadataFile.isHighPublicationPriority(), 0));
    }

    public static int addHighFrequency(boolean highFrequency, int value) {
        return addHighPriority(highFrequency, value, 1);
    }

    public static boolean isHighFrequency(int value) {
        return isHighPriority(value, 1);
    }

    public static int addHighProfile(boolean highProfile, int value) {
        return addHighPriority(highProfile, value, 2);
    }

    public static boolean isHighProfile(int value) {
        return isHighPriority(value, 2);
    }

    private static int addHighPriority(boolean hp, int value, int number) {
        return value | (hp ? number : 0);
    }

    private static boolean isHighPriority(int value, int number) {
        return (value & number) == number;
    }
        
    GoManifest parseGoManifest(String packageName, URI packageUri, String packagePath,
                                      Supplier<List<ErrorInfo>> errorCollector, IRepository rp) throws Exception {
        String fileName = buildGOFileName(packageName);
        ByteArrayInputStream bais = null;
        InputStream is = null;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            is = rp.getFile(buildGOFileName(packageUri.getPath()));
            IOUtils.copy(is, baos);
            bais = new ByteArrayInputStream(baos.toByteArray());
            GoManifest goManifest = GoManifest.parseGoManifest(fileName, bais, getDocumentLoader());
            AbstractZipPackage.putFileToRepository(rp, bais, packagePath + fileName);

            if (goManifest.getMetadataFileName() != null) {
                metadataFile = new AriesMetadataFile(goManifest.getMetadataFileName());
            }
            return goManifest;

        } catch (Exception e) {
            RecordHelper.handleErrorMessage(new ErrorInfo<>(fileName, e.getMessage()), errorCollector.get());
            throw e;

        } finally {
            IOUtils.closeQuietly(is);
            IOUtils.closeQuietly(bais);
        }
    }

    void checkOnReceive(String packageFileName, Integer packageId, GoManifest goManifest, CDSRMetaVO meta,
               boolean forSPD, IFlowLogger flLogger) throws CmsException {

        if (flLogger != null) {
            flLogger.receiveProduct(packageFileName, packageId, meta, PackageChecker.ARIES,
                    forSPD && importFile != null ? importFile.getTargetOnlinePubDateSrc() : null, 0);
        }
        //checkGoManifest(goManifest, meta);
        checkImportFile(goManifest, meta, packageFileName);
        checkMetadataFile(goManifest, meta);
    }

    static void checkGoManifest(GoManifest goManifest, CDSRMetaVO meta) throws CmsException {
        if (!goManifest.getFileName().replace(Extensions.GO_XML, "").endsWith(goManifest.getProductionTaskId())) {
            CmsException.throwMetadataError(meta, String.format("%s does not relevant to the production task: %s",
                    goManifest.getFileName(), goManifest.getProductionTaskId()));
        }
        String err = goManifest.checkOnEnd();
        if (err != null) {
            CmsException.throwMetadataError(meta, err);
        }
    }

    public String createAckGO(String srcPath, String dstPath, IRepository rp) throws Exception {

        InputStream is = rp.getFile(srcPath);
        StringWriter writer = new StringWriter();
        Result output = new StreamResult(writer);

        Document doc = documentLoader.load(is);
        Transformer tr = transformer4GO();

        tr.transform(new JDOMSource(doc), output);

        String ret = writer.toString();
        if (dstPath != null) {
            rp.putFile(dstPath, new ByteArrayInputStream(ret.getBytes()));
        }
        return ret;
    }

    public String createAckImport(String srcPath, String dstPath, String predictDate, String onlineDate, IRepository rp)
            throws Exception {

        InputStream is = rp.getFile(srcPath);
        StringWriter writer = new StringWriter();
        Result output = new StreamResult(writer);

        Document doc = documentLoader.load(is);
        Transformer tr = transformer4Import();
        if (predictDate != null) {
            tr.setParameter("date_predict", predictDate);
        }
        if (onlineDate != null) {
            tr.setParameter("date_online", onlineDate);
        }
        tr.transform(new JDOMSource(doc), output);
        String ret = writer.toString();
        if (dstPath != null) {
            rp.putFile(dstPath, new ByteArrayInputStream(ret.getBytes()));
        }

        return ret;
    }

    void acknowledgementOnReceived(String dbName, Integer dbId, String packageName, Integer dfId, IRecord record,
                                   boolean spd, IFlowLogger flLogger) {
        try {
            IRecordCache cache = flLogger.getRecordCache();
            if (isDeliver() && record != null) {
                cache.checkAriesRecordOnReceived(importFile.getManuscriptNumber(), record.getName(), record.getId());
                CochraneCMSBeans.getPublishService().publishWhenReadySync(dbName, dbId, dfId, record.getName(),
                        PubType.TYPE_ARIES_ACK_D, null);
            } else if (isPublish()) {
                RecordCache.ManuscriptValue ret = cache.checkAriesRecordOnPublished(
                        importFile.getManuscriptNumber(), null, DbEntity.NOT_EXIST_ID, dfId);
                if (ret.readyForAcknowledgement()) {
                    checkImportFile(ret.cdNumber(), null);  
                    CochraneCMSBeans.getPublishService().sendAcknowledgementAriesOnPublish(dbName, dbId, dfId,
                            importFile.getManuscriptNumber(), ret.whenReadyId(), false);
                }
            } else if (isCancel() && record != null) {
                boolean dashboard = BaseType.find(dbName).get().hasSFLogging();
                flLogger.onProductUnpacked(packageName, null, record.getName(), null, dashboard, spd);

                cache.checkAriesRecordOnPublished(importFile.getManuscriptNumber(), null, DbEntity.NOT_EXIST_ID, dfId);
                CochraneCMSBeans.getPublishService().publishWhenReadySync(dbName, dbId, dfId, record.getName(),
                         PubType.TYPE_SEMANTICO, null);
            }
        } catch (Exception e) {
            LOG.error(e);
        }
    }

    private Transformer transformer4Import() throws Exception {
        if (transformer4Import == null) {
            transformer4Import = TransformerObjectPool.Factory.instance().getTransformer(
                    CochraneCMSPropertyNames.getCochraneResourcesRoot() + "aries/aries_import.xsl");
        }
        return transformer4Import;
    }

    private Transformer transformer4GO() throws Exception {
        if (transformer4GO == null) {
            transformer4GO = TransformerObjectPool.Factory.instance().getTransformer(
                    CochraneCMSPropertyNames.getCochraneResourcesRoot() + "aries/aries_go.xsl");
        }
        return transformer4GO;
    }
}
