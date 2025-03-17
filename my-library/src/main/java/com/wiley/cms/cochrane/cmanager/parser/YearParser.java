package com.wiley.cms.cochrane.cmanager.parser;

import com.wiley.tes.util.Logger;

/**
 * @author <a href='mailto:svyatoslav.gulin@gmail.com'>Svyatoslav Gulin</a>
 * @version 02.09.2011
 */
public class YearParser extends SourceParser {
    private static final Logger LOG = Logger.getLogger(YearParser.class);
    private static final int YEAR_LENGTH = 4;

    public void endElement(int i) throws FinishException {
        if (this.level == tagsCount && i == tagsCount - 1) {
            if (text.length() >= YEAR_LENGTH) {
                try {
                    result.setYear(Integer.parseInt(text.toString().substring(0, YEAR_LENGTH)));
                } catch (NumberFormatException e) {
                    LOG.warn("Parse year error", e);
                }
            }

            level = 0;
        }
    }

}

