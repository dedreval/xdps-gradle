package com.wiley.cms.cochrane.meshtermmanager;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.Serializable;
import java.io.StringWriter;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.ejb.Local;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import com.wiley.cms.cochrane.cmanager.entitymanager.AbstractManager;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.Text;
import org.jdom.output.XMLOutputter;
import org.jdom.xpath.XPath;

import com.wiley.cms.cochrane.activitylog.ActivityLogEntity;
import com.wiley.cms.cochrane.activitylog.IActivityLogService;
import com.wiley.cms.cochrane.activitylog.ILogEvent;
import com.wiley.cms.cochrane.cmanager.CmsUtils;
import com.wiley.cms.cochrane.cmanager.CochraneCMSProperties;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.data.meshterm.DescriptorEntity;
import com.wiley.cms.cochrane.cmanager.data.meshterm.IMeshtermStorage;
import com.wiley.cms.cochrane.cmanager.data.meshterm.MeshtermStorageFactory;
import com.wiley.cms.cochrane.cmanager.data.meshterm.QualifierEntity;
import com.wiley.cms.cochrane.repository.IRepository;
import com.wiley.cms.cochrane.repository.RepositoryFactory;
import com.wiley.cms.process.AbstractBeanFactory;
import com.wiley.tes.util.XmlUtils;
import com.wiley.tes.util.jdom.DocumentLoader;

/**
 * @author <a href='mailto:ikirov@wiley.com'>Igor Kirov</a>
 * @version 09.11.2009
 */
@Stateless
@Local(IMeshtermUpdater.class)
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class MeshtermUpdater implements IMeshtermUpdater, Serializable {
    private static final String XML_HEADER = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";
    private static final String CONTAINER_OPEN_TAG = "<container>";
    private static final String CONTAINER_CLOSE_TAG = "</container>";
    private static final String AMP = "&";
    private static final String AMP_HTML = "&amp;";

    private IMeshtermStorage meshtermStorage;

    public void updateMeshterms(Map<String, String> sourcePaths) throws Exception {

        IRepository rps = RepositoryFactory.getRepository();
        meshtermStorage = MeshtermStorageFactory.getFactory().getInstance();

        List<String> recordNames = getRecords(sourcePaths.keySet());
        for (String recordName : recordNames) {
            String path = sourcePaths.get(recordName);
            String pubmedXml = generatePubmedXml(recordName);
            if (pubmedXml != null) {
                InputStream is = rps.getFile(path);
                String sourceXml = IOUtils.toString(is, XmlUtils.getEncoding(is));
                String resultXml = insertMeshterms(sourceXml, pubmedXml);
                is = new ByteArrayInputStream(resultXml.getBytes(StandardCharsets.UTF_8));
                rps.putFile(path, is);
            }
        }
    }

    public void updateMeshterms(Map<String, String> sourcePaths, int issue, int pckId, String pckName)
        throws Exception {

        IActivityLogService logService = AbstractManager.getActivityLogService();
        String logUser = CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.ACTIVITY_LOG_SYSTEM_NAME);

        logService.info(ActivityLogEntity.EntityLevel.FILE, ILogEvent.MESHTERM_UPDATER_STARTED, pckId, pckName,
                logUser, null);

        updateMeshterms(sourcePaths);

        logService.info(ActivityLogEntity.EntityLevel.FILE, ILogEvent.MESHTERM_UPDATER_COMPLETED, pckId, pckName,
                logUser, null);
    }

    private List<String> getRecords(Set<String> recordNames) {
        return meshtermStorage.findRecords(recordNames);
    }

    private String generatePubmedXml(String recordName) {
        StringBuilder xml = new StringBuilder();
        xml.append("<MeshHeadingList>").append("\n");
        List<DescriptorEntity> descriptorEntities = meshtermStorage.getDescriptors(recordName);
        if (descriptorEntities.size() == 0) {
            return null;
        }
        for (DescriptorEntity descriptorEntity : descriptorEntities) {
            xml.append("<MeshHeading>").append("\n");
            xml.append(descriptorEntity.toString()).append("\n");
            List<QualifierEntity> qualifierEntities = meshtermStorage.getQualifiers(recordName, descriptorEntity);
            //QualifierEntity qe = meshtermStorage.getNullQualifier();
            //qualifierEntities.remove(qe);
            for (QualifierEntity qualifierEntity : qualifierEntities) {
                String qf = qualifierEntity.getQualifier();
                if (qf != null && qualifierEntity.getQualifier().length() > 0) {
                    xml.append(qualifierEntity.toString()).append("\n");
                }
            }
            xml.append("</MeshHeading>").append("\n");
        }
        xml.append("</MeshHeadingList>");
        return xml.toString();
    }

    private String insertMeshterms(String sourceXml, String pubmedXml) throws Exception {
        URL xsl = new URI(CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.COCHRANE_RESOURCES)
                + CochraneCMSProperties.getProperty("cms.resources.meshterm.insert.xsl.path")).toURL();
        TransformerFactory factory =
                (TransformerFactory) Class.forName("net.sf.saxon.TransformerFactoryImpl").newInstance();
        Transformer transformer = factory.newTransformer(new StreamSource(xsl.openStream()));

        String container = getContainer(sourceXml, pubmedXml);

        StringWriter writer = new StringWriter();
        transformer
                .transform(new StreamSource(new ByteArrayInputStream(container.getBytes())), new StreamResult(writer));

        String doctypeDeclaration = CmsUtils.getDoctypeDeclaration(sourceXml);

        StringBuilder resultXml = new StringBuilder();
        resultXml.append(XML_HEADER).append("\n");
        resultXml.append(doctypeDeclaration).append("\n");
        resultXml.append(StringUtils.substringBetween(writer.toString(), CONTAINER_OPEN_TAG, CONTAINER_CLOSE_TAG));
        String result = processEntities(resultXml.toString());
        result = result.replaceAll(AMP_HTML, AMP);

        return result;
    }

    private String processEntities(String xml) throws Exception {
        DocumentLoader dl = new DocumentLoader();
        Document document = dl.load(xml);

        replaceEntities((Element) XPath.selectSingleNode(document, "//MeSHterms"));

        XMLOutputter out = new XMLOutputter();
        return out.outputString(document);
    }

    private void replaceEntities(Element e) {
        if (e == null) {
            return;
        }
        List content = e.getContent();
        for (Object obj : content) {
            if (obj instanceof Text) {
                Text t = (Text) obj;
                String text = t.getText();

                text = text.replaceAll("-", "&hyphen;");
                text = text.replaceAll("'", "&apos;");

                t.setText(text);
            } else if (obj instanceof Element) {
                replaceEntities((Element) obj);
            }
        }
    }

    private String getContainer(String sourceXml, String pubmedXml) {
        StringBuilder xml = new StringBuilder();
        xml.append(XML_HEADER).append("\n");
        xml.append(CONTAINER_OPEN_TAG).append("\n");
        xml.append(pubmedXml).append("\n");

        int ind = sourceXml.indexOf("<component");
        xml.append(sourceXml.substring(ind));

        xml.append(CONTAINER_CLOSE_TAG);

        return xml.toString().replaceAll(AMP, AMP_HTML);
    }

    /**
     * Just factory
     */
    public static class Factory extends AbstractBeanFactory<IMeshtermUpdater> {
        private static final Factory INSTANCE = new Factory();

        private Factory() {
            super(CochraneCMSPropertyNames.buildLookupName("MeshtermUpdater", IMeshtermUpdater.class));
        }

        public static Factory getFactory() {
            return INSTANCE;
        }
    }
}
