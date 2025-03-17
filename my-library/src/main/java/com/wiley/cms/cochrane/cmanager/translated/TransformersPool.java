package com.wiley.cms.cochrane.cmanager.translated;

import java.io.IOException;
import java.net.URI;
import java.net.URL;

import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamSource;

import com.wiley.cms.cochrane.cmanager.CochraneCMSProperties;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.repository.RepositoryUtils;
import com.wiley.tes.util.Logger;

/**
 * @author <a href='mailto:sgulin@wiley.ru'>Svyatoslav Gulin</a>
 * @version 20.10.2011
 */
public class TransformersPool {
    private static final Logger LOG = Logger.getLogger(TransformersPool.class);
    private static TransformerFactory translatedAbstractsFactory;

    private static Templates translatedAbstractsTemplate;
    private static Templates translatedAbstractsTemplate3;
    private static Templates translatedAbstractsTemplateHW;

    private static String transabsPath = getTransabsXmlPath();

    public static Transformer getTransformerWOL() throws TransformerConfigurationException, IOException {

        synchronized (Transformer.class) {
            if (translatedAbstractsTemplate == null) {
                translatedAbstractsTemplate = loadXslt("cms.resources.abstracts.insert.xsl.path");
            }
        }

        Transformer ret = translatedAbstractsTemplate.newTransformer();
        setTransformer(ret);
        return ret;
    }

    public static Transformer getTransformerV3() throws TransformerConfigurationException, IOException {

        synchronized (Transformer.class) {
            if (translatedAbstractsTemplate3 == null) {
                translatedAbstractsTemplate3 = loadXslt("cms.resources.abstracts.insert.xsl.path3");
            }
        }

        Transformer ret = translatedAbstractsTemplate3.newTransformer();
        setTransformer(ret);
        return ret;
    }

    public static Transformer getTransformerHW() throws TransformerConfigurationException, IOException {

        synchronized (Transformer.class) {
            if (translatedAbstractsTemplateHW == null) {
                translatedAbstractsTemplateHW = loadXslt("cms.resources.abstracts.insert.xsl.pathHW");
            }
        }

        Transformer ret = translatedAbstractsTemplateHW.newTransformer();
        setTransformer(ret);
        return ret;
    }

    public static synchronized void refresh() {
        translatedAbstractsTemplate = null;
        translatedAbstractsTemplate3 = null;
        translatedAbstractsTemplateHW = null;

        transabsPath = getTransabsXmlPath();
    }

    private static void setTransformer(Transformer transformer) {
        transformer.setParameter("transabs.xml", transabsPath);
    }

    private static String getTransabsXmlPath() {
        try {
            URL xml = new URI(CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.COCHRANE_RESOURCES)
                + CochraneCMSProperties.getProperty("cms.resources.abstracts.insert.xml.path")).toURL();
            return RepositoryUtils.buildURIPath(xml.getPath());
        } catch (Exception ex) {
            LOG.error(ex, ex);
        }
        return null;
    }

    private static Templates loadXslt(String key) throws IOException {
        try {
            URL xsl = new URI(CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.COCHRANE_RESOURCES)
                    + CochraneCMSProperties.getProperty(key)).toURL();

            return getTransformerFactory().newTemplates(new StreamSource(xsl.openStream()));
        } catch (Exception ex) {
            LOG.error(ex, ex);
            throw new IOException(ex.getMessage());
        }
    }

    private static TransformerFactory getTransformerFactory() throws TransformerConfigurationException {
        if (translatedAbstractsFactory != null) {
            return translatedAbstractsFactory;
        }

        try {
            translatedAbstractsFactory = (TransformerFactory)
                    Class.forName("net.sf.saxon.TransformerFactoryImpl").newInstance();
        } catch (Exception e) {
            LOG.error(e, e);
            throw new TransformerConfigurationException(e.getMessage());
        }

        return translatedAbstractsFactory;
    }
}
