package com.wiley.cms.cochrane.cmanager.contentworker;

import java.io.InputStream;
import java.net.URI;
import java.util.HashSet;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import org.jdom.Document;
import org.jdom.Element;

import com.wiley.cms.cochrane.cmanager.CmsException;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.utils.xml.JDOMHelper;
import com.wiley.tes.util.jdom.DocumentLoader;
import com.wiley.tes.util.jdom.JDOMUtils;

/**
 * @author <a href='mailto:atolkachev@wiley.ru'>Andrey Tolkachev</a>
 * Date: 18.01.21
 */
public class GoManifest {
    public static final String GO_PATH = "//GO";
    public static final String PARAMS_PATH = GO_PATH + "/header/parameters";
    public static final String FILE_GROUP_PATH = GO_PATH + "/filegroup";
    public static final String PRODUCTION_TASK_ID_PATH = PARAMS_PATH + "/parameter[@name='production-task-id']/@value";
    public static final String ARCHIVE_FILE_PATH = FILE_GROUP_PATH + "/archive-file/@name";
    public static final String IMPORT_FILE_PATH = FILE_GROUP_PATH + "/import-file/@name";
    public static final String METADATA_FILE_PATH = FILE_GROUP_PATH + "/metadata-file/@name";

    private final String fileName;
    private String productionTaskId;
    private String archiveFileName;
    private String importFileName;
    private String metadataFileName;
    private Set<String> assetFileNames = new HashSet<>();
    private String err = null;

    public GoManifest(String fileName) {
        this.fileName = fileName;
    }

    private static void validateGoManifest(String fileName, InputStream xml) throws CmsException {
        String xsdPath = CochraneCMSPropertyNames.getCochraneResourcesRoot() + "aries/go_manifest_schema.xsd";
        try {
            InputStream xsd = new URI(xsdPath).toURL().openStream();
            SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            Schema schema = factory.newSchema(new StreamSource(xsd));
            Validator validator = schema.newValidator();
            validator.validate(new StreamSource(xml));
        } catch (Exception e) {
            throw new CmsException(String.format("manifest '%s' is not valid against '%s' schema: %s", fileName,
                    xsdPath, e.getMessage()));
        }
    }

    static GoManifest parseGoManifest(String fileName, InputStream is, DocumentLoader loader) throws Exception {

        validateGoManifest(fileName, is);
        is.reset();

        Document doc  = loader.load(is);
        GoManifest goManifest = new GoManifest(fileName);

        goManifest.setProductionTaskId(goManifest.getGoAttributeValue(doc, PRODUCTION_TASK_ID_PATH, true));
        goManifest.setArchiveFileName(goManifest.getGoAttributeValue(doc, ARCHIVE_FILE_PATH, true));
        goManifest.setImportFileName(goManifest.getGoAttributeValue(doc, IMPORT_FILE_PATH, true));
        goManifest.setMetadataFileName(goManifest.getGoAttributeValue(doc, METADATA_FILE_PATH, false));

        setGoAssets(JatsHelper.getElement(doc, GoManifest.FILE_GROUP_PATH, true), goManifest);
        is.reset();
        return goManifest;
    }

    private static void setGoAssets(Element fileGroupElement, GoManifest goManifest) {
        JDOMUtils.getChildren(fileGroupElement)
                .stream()
                .map(af -> af.getAttributeValue("name"))
                .forEach(goManifest::addAssetFileName);
    }

    private String getGoAttributeValue(Document doc, String xPath, boolean mandatory) throws CmsException {
        String attributeValue = JDOMHelper.getAttributeValue(doc, xPath, null);
        if (attributeValue != null && !attributeValue.isEmpty()) {
            return attributeValue;

        } else if (mandatory) {
            throw new CmsException(String.format("no value found by: '%s' in '%s' manifest", xPath, getFileName()));
        }
        return null;
    }

    public String getFileName() {
        return fileName;
    }

    public String getProductionTaskId() {
        return productionTaskId;
    }

    public void setProductionTaskId(String productionTaskId) {
        this.productionTaskId = productionTaskId;
    }

    public String getArchiveFileName() {
        return archiveFileName;
    }

    public void setArchiveFileName(String archiveFileName) {
        this.archiveFileName = archiveFileName;
    }

    public String getImportFileName() {
        return importFileName;
    }

    public void setImportFileName(String fileName) {
        importFileName = fileName;
    }

    public String getMetadataFileName() {
        return metadataFileName;
    }

    public void setMetadataFileName(String fileName) {
        metadataFileName = fileName;
    }

    public void removeAssetFileName(String assetFileName) {
        if (!assetFileNames.remove(assetFileName)) {
            assetFileNames.add(assetFileName);
        }
    }

    String checkOnEnd() {
        removeAssetFileName(getArchiveFileName());
        if (err == null && !assetFileNames.isEmpty()) {
            StringBuilder sb = new StringBuilder(String.format(
                "the file list in '%s' manifest does not match to files included in '%s', differences are:\n",
                    getFileName(), getArchiveFileName()));
            assetFileNames.forEach(f -> sb.append(f).append("\n"));
            err = sb.toString();
        }
        return err;
    }

    private void addAssetFileName(String assetFileName) {
        if (assetFileNames.contains(assetFileName)) {
            err = String.format("'%s' is not unique in '%s' manifest", assetFileName, getFileName());
        } else {
            assetFileNames.add(assetFileName);
        }
    }
}
