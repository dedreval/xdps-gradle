package com.wiley.cms.cochrane.cmanager.parser;

import java.util.Map;

import org.xml.sax.Attributes;

import com.wiley.tes.util.Logger;

/**
 * @author <a href='mailto:svyatoslav.gulin@gmail.com'>Svyatoslav Gulin</a>
 * @version 15.08.2011
 */
public abstract class TagUnitStatusParser extends SourceParser {
    private static final Logger LOG = Logger.getLogger(TagUnitStatusParser.class);

    @Override
    public StringBuilder startElement(int number, Attributes atts) {
        if (number != -1 && level == number) {
            level++;
        }
        if (level == tagsCount && number == tagsCount - 1) {
            text = new StringBuilder();
            String value = atts.getValue("TAGWITH");
            if (value != null && !"null".equals(value) && getStatuses().containsKey(value)) {
                result.setStatus(getStatuses().get(value));
            } else {
                LOG.warn("Status '" + value + "' was not found in list of registered types");
            }
        }
        return text;
    }

    @Override
    public void endElement(int i) throws FinishException {
        ;
    }

    /**
     * Return correspondence between statuses from record in package and status in application database.
     * Map contains <status from record, status from database> pairs.
     *
     * @return correspondence Map
     */
    public abstract Map<String, String> getStatuses();

}
