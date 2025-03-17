package com.wiley.tes.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;

import org.jdom.input.SAXBuilder;
import org.xml.sax.EntityResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import com.wiley.cms.cochrane.cmanager.CochraneCMSProperties;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.tes.util.jdom.JDOMUtils;

/**
 * @author <a href='mailto:gkhristich@wiley.ru'>Galina Khristich</a>
 *         Date: 23-Mar-2007
 */
public class EntitiesResolver {
    private static final Logger LOG = Logger.getLogger(EntitiesResolver.class);
    private static final String DTD_EXT = ".dtd";

    private String dtd;

    public String resolveEntity(InputStream source) throws Exception {
        SAXBuilder builder = new SAXBuilder(); //loader.getBuilder();
        builder.setValidation(true);
        builder.setEntityResolver(new CmsEntityResolver());
        builder.setErrorHandler(new ErrorHandler() {
            public void warning(SAXParseException exception) throws SAXException {
                LOG.debug("warning:" + exception.getMessage());
            }

            public void error(SAXParseException exception) throws SAXException {
                LOG.error("error:" + exception.getMessage());
                throw exception;
            }

            public void fatalError(SAXParseException exception) throws SAXException {
                LOG.error("fatal:" + exception.getMessage());
                throw exception;
            }
        }
        );
        org.jdom.Document doc = builder.build(source);
        return JDOMUtils.printToString(doc);
    }

    private class CmsEntityResolver implements EntityResolver {
        public InputSource resolveEntity(String publicId, String systemId) throws SAXException, IOException {
            String nameDtd;
            String dtdLocation;
            if (systemId.indexOf(DTD_EXT) != -1 && dtd == null) {
                nameDtd = systemId.substring(systemId.lastIndexOf("/") + 1, systemId.indexOf(DTD_EXT));
                dtd = CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.RESOURCES_DTD_PREFIX + nameDtd);
                dtdLocation = dtd;
            } else {
                nameDtd = systemId.substring(systemId.lastIndexOf("/") + 1);
                if (dtd == null) {
                    LOG.error("Not found dtd for systemId:" + systemId);
                }
                dtdLocation = dtd.substring(0, dtd.lastIndexOf("/") + 1) + nameDtd;
            }
            try {
                return new InputSource(new FileInputStream(new File(new URI(dtdLocation))));
            } catch (URISyntaxException e) {
                LOG.error(e, e);
                return new InputSource();
            }
        }
    }
}