package com.wiley.cms.cochrane.term2num;

// SAX

import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

/**
 * // SAXEventHandler.java
 * // $Id: SAXEventHandler.java,v 1.4 2011-11-25 14:05:48 sgulin Exp $
 *
 * @author <a href='mailto:stirnov@wiley.ru'>Tyrnov Sergey</a>
 *         Date: 16-Jan-2009
 */

public class SAXEventHandler extends DefaultHandler {
    private static final String XML_HEADER = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>";
    private static final String DESCRIPTOR_RECORD = "DescriptorRecord";

    private final StringBuffer descBuf;
    private final DescriptorRecordParser descRecParser;

    public SAXEventHandler(DescriptorRecordParser descRecParser) {
        descBuf = new StringBuffer();
        this.descRecParser = descRecParser;
    }

    public void startElement(String uri, String localName, String qName, Attributes atts) {
        if (qName.equals(DESCRIPTOR_RECORD)) {
            descBuf.setLength(0);
            descBuf.append(XML_HEADER);
        }
        descBuf.append("<" + qName);
        if (atts != null) {
            for (int i = 0, len = atts.getLength(); i < len; i++) {
                descBuf.append(" ").append(atts.getLocalName(i)).append("=\"").append(atts.getValue(i)).append("\"");
            }
        }
        descBuf.append(">");
    }

    public void endElement(String uri, String localName, String qName) {
        descBuf.append("</").append(qName).append(">\n");
        if (qName.equals(DESCRIPTOR_RECORD)) {
            descRecParser.parseRecord(descBuf.toString());
            descBuf.setLength(0);
        }
    }

    public void characters(char[] ch, int start, int len) {
        if ((ch.length == 1)) {
            switch (ch[0]) {
                case '&':
                    descBuf.append("&amp;");
                    break;
                case '<':
                    descBuf.append("&lt;");
                    break;
                case '>':
                    descBuf.append("&gt;");
                    break;
                case '"':
                    descBuf.append("&quot;");
                    break;
                case '\'':
                    descBuf.append("&apos;");
                    break;
                default:
                    descBuf.append(new String(ch, start, len));
                    break;
            }
        } else {
            descBuf.append(new String(ch, start, len));
        }
    }
}
