package com.wiley.cms.cochrane.cmanager.parser;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import com.wiley.cms.cochrane.cmanager.data.record.IRecord;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 8/28/2019
 */
public class Wml3gParser extends DefaultHandler {

    private int read = -1;
    private IRecord record = null;

    @Override
    public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {
        if (isEnd()) {
            return;
        }
        if (!isStartContentMeta()) {
            if (qName.equals("contentMeta")) {
                read = 0;
            }

        } else if (!isSetSortTitle() && qName.equals("title") && "main".equals(atts.getValue("type"))) {
            read = 1;
        }
    }

    @Override
    public void characters(char[] ch, int start, int length) {
        if (isSetSortTitle()) {
            record.setTitle(new String(ch, start, length));
            read = 2;
        }
    }

    public DefaultHandler init(IRecord record) {
        read = -1;
        this.record = record;
        return this;
    }

    private boolean isSetSortTitle() {
        return read == 1;
    }

    private boolean isStartContentMeta() {
        return read >= 0;
    }

    private boolean isEnd() {
        return read == 2;
    }

    //private boolean isStartContentMetaTitle() {
    //    return isStartContentMeta() && read < 2;
    //}
}
