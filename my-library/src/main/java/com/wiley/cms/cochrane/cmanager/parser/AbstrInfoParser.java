package com.wiley.cms.cochrane.cmanager.parser;

/**
 * @author <a href='mailto:gkhristich@wiley.ru'>Galina Khristich</a>
 *         Date: 30-Jan-2008
 */
public class AbstrInfoParser extends SourceParser {
    public void endElement(int i) throws FinishException {
        if (this.level == tagsCount && i == tagsCount - 1) {
            result.setAbstractInfo(text.toString());
            level = 0;
            text = null;
        }
    }
}
