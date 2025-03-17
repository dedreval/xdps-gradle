package com.wiley.cms.cochrane.cmanager.parser;

/**
 * @author <a href='mailto:gkhristich@wiley.ru'>Galina Khristich</a>
 *         Date: 05-Jun-2007
 */
public class UnitStatusParser extends SourceParser {
    public void endElement(int i) throws FinishException {
        if (this.level == tagsCount && i == tagsCount - 1) {
            result.setStatus(text.toString().replaceAll("&apos;", "'"));
            level = 0;
        }
    }
}
