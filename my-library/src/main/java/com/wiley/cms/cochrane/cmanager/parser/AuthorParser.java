package com.wiley.cms.cochrane.cmanager.parser;

import org.xml.sax.Attributes;

/**
 * @author <a href='mailto:gkhristich@wiley.ru'>Galina Khristich</a>
 *         Date: 30-Jan-2008
 */
public class AuthorParser extends SourceParser {
    public StringBuilder startElement(int number, Attributes atts) {

        if (number != -1 && level == number) {
            level++;
        }
        if (level == tagsCount - 1 && number == tagsCount - 2) {
            if (!atts.getValue("creatorRole").equals("author")) {
                level--;
                //text = null;
            } else {
                text = new StringBuilder();
            }
        }
        return text;
    }

    public void endElement(int i) throws FinishException {
        if (this.level == tagsCount && i == tagsCount - 1) {
            result.setAuthors(text.toString().trim());
            level--;
            text = null;
        }
    }


}
