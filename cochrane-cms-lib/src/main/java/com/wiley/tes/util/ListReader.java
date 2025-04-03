package com.wiley.tes.util;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * @author <a href='mailto:ikirov@wiley.com'>Igor Kirov</a>
 * @version 05.09.11
 */
public class ListReader {
    private static final Logger LOG = Logger.getLogger(ListReader.class);

    private ListReader() {
    }

    public static List<String> readList(InputStream in, String elementName) {
        try {
            return new Handler(in, elementName).getCheckWords();
        } catch (Exception e) {
            LOG.error(e);
            return new ArrayList<String>(0);
        }
    }

    private static class Handler extends DefaultHandler {

        private List<String> mList = new ArrayList<String>();
        private String mElementName;
        private StringBuilder mString = new StringBuilder();

        Handler(InputStream in, String elementName) throws Exception {
            mElementName = elementName;
            SAXParserFactory spf = SAXParserFactory.newInstance();
            SAXParser parser = spf.newSAXParser();
            parser.parse(new BufferedInputStream(in), this);
        }

        public void startElement(String uri, String localName, String qName, Attributes attributes)
            throws SAXException {

            if (mElementName.equals(qName)) {
                clearString();
            }
        }

        public void endElement(String uri, String localName, String qName) throws SAXException {
            if (mElementName.equals(qName)) {
                mList.add(mString.toString().trim());
            }
        }

        public void characters(char[] ch, int start, int length) throws SAXException {
            mString.append(ch, start, length);
        }

        private void clearString() {
            if (mString.length() > 0) {
                mString.delete(0, mString.length());
            }
        }

        List<String> getCheckWords() {
            return mList;
        }
    }
}
