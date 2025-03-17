package com.wiley.cms.cochrane.utils.xml;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Class to assist creating org.w3c.Element objects
 *
 * @author mlarson, created on Jun 1, 2006 at 2:24:48 PMJun 1, 2006
 * @version $Id: ElementCreator.java,v 1.5 2011-11-25 14:05:49 sgulin Exp $
 */
public class ElementCreator {
    public Element newElement(Document doc, String elementName, String elementTextChild) {
        Element elem = doc.createElement(elementName);
        elem.appendChild(doc.createTextNode(elementTextChild));

        return elem;
    }
}
