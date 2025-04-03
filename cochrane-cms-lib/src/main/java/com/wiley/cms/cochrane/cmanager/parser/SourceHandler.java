package com.wiley.cms.cochrane.cmanager.parser;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * @author <a href='mailto:gkhristich@wiley.ru'>Galina Khristich</a>
 *         Date: 10-May-2007
 */
public class SourceHandler extends DefaultHandler {
    private SourceParsingResult result;
    private String[][] tags;
    private SourceParser[] parsers;
    private StringBuilder text;

    public SourceHandler(SourceParsingResult result,
                         String[][] tags, SourceParser[] parsers) {
        this.result = result;
        this.tags = tags;
        this.parsers = parsers;
    }

    public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {

        for (int i = 0; i < tags.length; i++) {
            String[] tag = tags[i];
            int number = findTags(qName, tag);
            if (number != -1) {
                StringBuilder tmp = parsers[i].startElement(number, atts);
                if (tmp != null) {
                    text = tmp;
                }

            }
        }
    }

    private int findTags(String qName, String[] tags) {
        for (int i = 0; i < tags.length; i++) {
            if (qName.equals(tags[i])) {
                return i;
            }
        }
        return -1;
    }

    public void endElement(String uri, String localName, String qName) throws SAXException {
        for (int i = 0; i < tags.length; i++) {
            String[] tag = tags[i];
            int number = findTags(qName, tag);
            if (number != -1) {
                parsers[i].endElement(number);
                // text = null;
            }
        }
    }

//    public void characters(char ch[], int start, int length)
//    {
//        if (text != null)
//        {
//            text.append(new String(ch, start, length));
//        }
//    }

    public void characters(char[] ch, int start, int length) {
        if (text != null) {
            String str = new String(ch, start, length);
            text.append(str);
        }
    }
}
