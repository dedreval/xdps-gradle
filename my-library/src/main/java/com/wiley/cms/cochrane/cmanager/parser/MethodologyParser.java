package com.wiley.cms.cochrane.cmanager.parser;

import org.xml.sax.Attributes;

/**
 * @author <a href='mailto:gkhristich@wiley.ru'>Galina Khristich</a>
 *         Date: 05-Jun-2007
 */
public class MethodologyParser extends SourceParser {
    public StringBuilder startElement(int number, Attributes atts) {

        if (number != -1 && level == number) {
            level++;
        }
        if (level == tagsCount && number == tagsCount - 1) {
            result.setReviewType(atts.getValue("unitType"));
            level = 0;
        }
        return null;
    }

    public void endElement(int i) throws FinishException {
        ;
    }

}

