package com.wiley.cms.cochrane.cmanager.parser;

import com.wiley.tes.util.Logger;

/**
 * @author <a href='mailto:svyatoslav.gulin@gmail.com'>Svyatoslav Gulin</a>
 * @version 02.09.2011
 */
public class PageParser extends SourceParser {
    private static final Logger LOG = Logger.getLogger(PageParser.class);

    public void endElement(int i) throws FinishException {
        if (this.level == tagsCount && i == tagsCount - 1) {
            if (text.indexOf("-") > -1) {
                result.setPage(text.toString().substring(0, text.indexOf("-")));
            }

            level = 0;
        }
    }

}

