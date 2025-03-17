package com.wiley.cms.cochrane.cmanager.parser;

import org.xml.sax.Attributes;

/**
 * @author <a href='mailto:ikirov@wiley.com'>Igor Kirov</a>
 * @version 11.01.2010
 */
public class GroupCodeParser extends SourceParser {
    public StringBuilder startElement(int number, Attributes atts) {
        if (number != -1 && level == number) {
            level++;
        }
        if (level == tagsCount && number == tagsCount - 1) {
            result.setGroup(atts.getValue("code"));
            level = 0;
        }
        return null;
    }

    @Override
    public void endElement(int i) throws FinishException {
        ;
    }
}
