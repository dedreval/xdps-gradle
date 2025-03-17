package com.wiley.cms.cochrane.cmanager.parser;

/**
 * @author <a href='mailto:gkhristich@wiley.ru'>Galina Khristich</a>
 *         Date: 05-Jun-2007
 */
public class TitleParser extends SourceParser {
    public void endElement(int i) throws FinishException {
        if (this.level == tagsCount && i == tagsCount - 1) {
            result.setTitle(text.toString().replaceAll("&hyphen;", "-"));
            level = 0;
        } else if (i == 0) {
            throw new FinishException();
        }
    }

}
