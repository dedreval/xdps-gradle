package com.wiley.cms.cochrane.cmanager.parser;

/**
 * @author <a href='mailto:gkhristich@wiley.ru'>Galina Khristich</a>
 *         Date: 31-Jan-2008
 */
public class AuthorSurnameParser extends AuthorParser {
    public void endElement(int i) throws FinishException {
        if (this.level == tagsCount && i == tagsCount - 1) {
            result.setAuthors(" " + text.toString().trim());
            level--;
            text = null;
        }
    }
}
