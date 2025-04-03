package com.wiley.cms.cochrane.cmanager.publish.util;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import com.wiley.cms.cochrane.cmanager.CochraneCMSBeans;
import com.wiley.cms.cochrane.cmanager.data.PrevVO;
import com.wiley.cms.cochrane.cmanager.entitymanager.IVersionManager;
import com.wiley.cms.cochrane.cmanager.parser.SourceParser;
import com.wiley.cms.cochrane.utils.Constants;
import org.apache.commons.io.IOUtils;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.xpath.XPath;

import com.wiley.cms.cochrane.cmanager.CochraneCMSProperties;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.FilePathCreator;
import com.wiley.cms.cochrane.cmanager.data.entire.EntireDBStorageFactory;
import com.wiley.cms.cochrane.cmanager.data.entire.IEntireDBStorage;
import com.wiley.cms.cochrane.cmanager.parser.SourceParsingResult;
import com.wiley.cms.cochrane.repository.IRepository;
import com.wiley.cms.cochrane.repository.RepositoryFactory;
import com.wiley.tes.util.InputUtils;
import com.wiley.tes.util.Logger;
import com.wiley.tes.util.jdom.DocumentLoader;
import com.wiley.tes.util.res.Property;
import com.wiley.tes.util.res.Res;

/**
 * @author <a href='mailto:gkhristich@wiley.ru'>Galina Khristich</a>
 *         Date: 29-Jan-2008
 */
public class DoiXmlCreator {
    private static final Logger LOG = Logger.getLogger(DoiXmlCreator.class);

    private static final String CLEDITORIAL = CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.CLEDITORIAL);
    private static final String MRW_DOI_CLOSE = "</mrw_doi>";

    private static IEntireDBStorage edbs = EntireDBStorageFactory.getFactory().getInstance();
    private static IVersionManager vm = CochraneCMSBeans.getVersionManager();
    private static IRepository rp = RepositoryFactory.getRepository();
    private static final String XML_VERSION_1_0 = "<?xml version=\"1.0\" ?>";
    private static final String DOI_BATCH_ID = "<doi_batch_id>";
    private static final String MRW_DOI_HEAD = "<mrw_doi>\n<head>\n";
    private static final String DOI_BATCH_ID1 = "</doi_batch_id>\n";
    private static final String TIMESTAMP = "<timestamp>";
    private static final String TIMESTAMP1 = "</timestamp>\n";
    private static final String PRODUCT_ID = "<product_id>";
    private static final String PRODUCT_ID1 = "</product_id>\n";
    private static final String HEAD = "</head>\n";
    private static final String ARTICLE = "<article>\n";
    private static final String ARTICLE1 = "</article>\n";

    private static final Res<Property> DS_DOI_LINK_TEMPLATE = Property.get("cms.cochrane.publish.ds.doiurl");

    private DoiXmlCreator() {
    }

    public static InputStream getCcaDoiXml(List<String> recordPaths, Date aDate) {
        Calendar calendar = new GregorianCalendar();
        calendar.setTime(aDate);

        String timestamp = calendar.get(Calendar.YEAR) + "" + aDate.getTime();
        StringBuilder xml = new StringBuilder();

        addCCAHead(xml, CochraneCMSPropertyNames.getCcaDbName(), timestamp);

        DocumentLoader dl = new DocumentLoader();

        XPath xPath = null;
        try {
            String doiXpath = "/a:component/a:header/a:publicationMeta[@level='unit']/a:doi";
            xPath = XPath.newInstance(doiXpath);
            xPath.addNamespace("a", Constants.WILEY_NAMESPACE_URI);
        } catch (Exception e) {
            LOG.error(e, e);
        }

        for (String recordPath : recordPaths) {
            try {
                String source = InputUtils.readStreamToString(rp.getFile(recordPath));
                Document document = dl.load(source);
                Element doi = (Element) xPath.selectSingleNode(document);
                addArticle(doi.getText(), xml);
            } catch (Exception e) {
                LOG.error("Failed to parse CCA source: [" + recordPath + "]", e);
            }
        }
        xml.append(MRW_DOI_CLOSE);

        return new ByteArrayInputStream(xml.toString().getBytes());
    }

    public static InputStream getDoiXml4DS(String dbName, Date aDate, Collection<String> pubNames, String doiPrefix,
                                           String meshUpdate, Function<String, String> buildDoi) {
        Calendar calendar = new GregorianCalendar();
        calendar.setTime(aDate);
        String timestamp = calendar.get(Calendar.YEAR) + "" + aDate.getTime();

        StringBuilder xml = new StringBuilder();
        addHead4DS(dbName, timestamp, meshUpdate, xml);

        String template = DS_DOI_LINK_TEMPLATE.get().getValue();
        addArticle4DS(doiPrefix, template, xml);

        for (String name: pubNames) {
            addArticle4DS(buildDoi.apply(name), template, xml);
        }
        xml.append(MRW_DOI_CLOSE);

        return new ByteArrayInputStream(xml.toString().getBytes());
    }

    public static InputStream getDoiXml(String dbName, Date date, Set<String> names, String meshUpdate, int lastIssue) {
        return proceed(dbName, date, names, meshUpdate, lastIssue);
    }

    public static InputStream getDoiXml(String dbName, Date date, Map<String, String> paths) {
        Calendar calendar = new GregorianCalendar();
        calendar.setTime(date);
        String timestamp = calendar.get(Calendar.YEAR) + "" + date.getTime();

        StringBuilder xml = new StringBuilder();

        addHead(xml, dbName, timestamp, null);
        for (String record : paths.keySet()) {
            addArticle(getDoiFromSource(record, paths.get(record)), xml);
        }
        xml.append(MRW_DOI_CLOSE);
        return new ByteArrayInputStream(xml.toString().getBytes());
    }

    public static InputStream getDoiXmlEntire(String dbName, Date date) {
        List<String> records = edbs.findSysrevRecordNames();
        records.addAll(edbs.findEditorialRecordNames());
        return proceed(dbName, date, records, null, 0);
    }

    private static InputStream proceed(String dbName, Date date, Collection<String> records, String meshUpdate,
                                       int lastIssue) {
        Calendar calendar = new GregorianCalendar();
        calendar.setTime(date);

        String timestamp = calendar.get(Calendar.YEAR) + "" + date.getTime();
        StringBuilder xml = new StringBuilder();

        addHead(xml, dbName, timestamp, meshUpdate);
        for (String record : records) {
            List<PrevVO> list = vm.getVersions(lastIssue, record);
            if (!list.isEmpty()) {
                addArticle(list.remove(0).buildDoi(), xml);
            } else {
                addArticle(getDoiFromEditorialSource(record), xml);
            }
            if (!list.isEmpty()) {
                addArticle(list.remove(0).buildDoi(), xml);
            }
        }
        xml.append(MRW_DOI_CLOSE);
        return new ByteArrayInputStream(xml.toString().getBytes());
    }

    private static String getDoiFromEditorialSource(String recordName) {
        return getDoiFromSource(recordName, FilePathCreator.getFilePathToSourceEntire(CLEDITORIAL, recordName));
    }

    private static String getDoiFromSource(String recordName, String path) {
        InputStream is = null;
        try {
            is = rp.getFile(path);
            String xml = InputUtils.readStreamToString(is);
            SourceParsingResult result = SourceParser.parseSource(xml);
            return result.getDoi();
        } catch (IOException e) {
            LOG.error("Failed to parse source: " + recordName, e);
        } finally {
            IOUtils.closeQuietly(is);
        }
        return null;
    }

    private static void addArticle4DS(String doi, String template, StringBuilder xml) {
        String url = CochraneCMSProperties.replaceProperty(template, "doi", doi);
        addArticleStart(xml);
        xml.append(doi).append("</doi>\n<url>").append(url);
        addArticleEnd(xml);
    }

    private static void addArticleStart(StringBuilder xml) {
        xml.append("<article>\n<doi>");
    }

    private static void addArticleEnd(StringBuilder xml) {
        xml.append("</url>\n</article>\n");
    }

    private static void addArticle(String doi, StringBuilder xml) {
        if (doi == null) {
            return;
        }
        addArticleStart(xml);
        xml.append(doi).append("</doi>\n<url>www3.interscience.wiley.com/resolve/doi?DOI=").append(doi);
        addArticleEnd(xml);
    }


    //http://www3.interscience.wiley.com/resolve/doi?DOI=<DOI>
    private static void addHead(StringBuilder xml, String dbTitle, String timestamp, String meshUpdate) {

        addHeadStart(dbTitle, timestamp, xml);

        addSubProductId(meshUpdate, xml);

        xml.append(HEAD)
            .append(ARTICLE).append("<doi>10.1002/14651858</doi>\n")
            .append("<url>www3.interscience.wiley.com/resolve/doi?DOI=10.1002/14651858</url>\n")
            .append(ARTICLE1);
    }

    private static void addSubProductId(String meshUpdate, StringBuilder xml) {
        if (meshUpdate != null) {
            xml.append("<sub_product_id>").append(meshUpdate).append("</sub_product_id>\n");
        }
    }

    private static void addHead4DS(String dbTitle, String timestamp, String meshUpdate, StringBuilder xml) {
        addHeadStart(dbTitle, timestamp, xml);
        addSubProductId(meshUpdate, xml);
        xml.append(HEAD);
    }

    private static void addCCAHead(StringBuilder xml, String dbTitle, String timestamp) {
        addHeadStart(dbTitle, timestamp, xml);
        xml.append(HEAD).append(ARTICLE).append(
                "<doi>10.1002/9781119969488</doi>\n").append(
                "<url>www3.interscience.wiley.com/resolve/doi?DOI=10.1002/9781119969488</url>\n").append(ARTICLE1);
    }

    private static void addHeadStart(String dbTitle, String timestamp, StringBuilder xml) {
        xml.append(XML_VERSION_1_0).append(MRW_DOI_HEAD)
            .append(DOI_BATCH_ID)
            .append(dbTitle).append(timestamp).append(DOI_BATCH_ID1)
            .append(TIMESTAMP)
            .append(timestamp).append(TIMESTAMP1)
            .append(PRODUCT_ID)
            .append(dbTitle).append(PRODUCT_ID1);
    }
}