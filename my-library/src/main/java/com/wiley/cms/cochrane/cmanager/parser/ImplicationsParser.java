package com.wiley.cms.cochrane.cmanager.parser;

import org.xml.sax.Attributes;

/**
 * @author <a href='mailto:gkhristich@wiley.ru'>Galina Khristich</a>
 *         Date: 31-Jan-2008
 */
public class ImplicationsParser extends SourceParser {
    public StringBuilder startElement(int number, Attributes atts) {

        if (number != -1 && level == number) {
            level++;
        }
        if (level == tagsCount - 1 && number == tagsCount - 2) {
            text = new StringBuilder();
        }
        if (level == tagsCount && number == tagsCount - 1) {
            text = new StringBuilder();
        }

        return text;
    }

    public void endElement(int i) throws FinishException {
        if (this.level == tagsCount && i == tagsCount - 1) {
            result.setImplication(text.toString().trim().toCharArray());
            level--;
            text = null;
            throw new FinishException();
        }
        if (this.level == tagsCount - 1 && i == tagsCount - 2) {
            if (!text.toString().equals("Implications for practice")) {
                level--;
            }
        }
    }

}
