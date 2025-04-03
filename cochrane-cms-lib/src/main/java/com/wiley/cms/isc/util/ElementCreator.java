package com.wiley.cms.isc.util;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Class to assist creating org.w3c.Element objects and documents
 *
 * @author mlarson, created on Jun 1, 2006 at 2:24:48 PMJun 1, 2006
 * @version $Id: ElementCreator.java,v 1.4 2011-11-25 14:05:49 sgulin Exp $
 */
public class ElementCreator {
    /**
     * Just a convenience method for creating elements with textual children
     *
     * @param doc
     * @param elementName
     * @param elementTextChild
     * @return
     * @throws ParserConfigurationException
     */
    public Element createElementWithText(Document doc, String elementName, String elementTextChild) {
        Element elem = doc.createElement(elementName);
        elem.appendChild(doc.createTextNode(elementTextChild));

        return elem;
    }

    public Document createDocument() throws ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.newDocument();
    }
}
