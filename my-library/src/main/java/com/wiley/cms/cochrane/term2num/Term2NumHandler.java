package com.wiley.cms.cochrane.term2num;

import java.io.IOException;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 10/18/2020
 */
public class Term2NumHandler extends SAXEventHandler {

    private String systemId;

    Term2NumHandler(DescriptorRecordParser descRecParser)  {
        super(descRecParser);
    }

    @Override
    public InputSource resolveEntity(String publicId, String systemId) throws IOException, SAXException {
        this.systemId = systemId;
        return null;
    }

    public String getSystemId() {
        return systemId;
    }
}
