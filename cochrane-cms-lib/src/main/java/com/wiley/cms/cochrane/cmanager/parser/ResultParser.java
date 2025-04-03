package com.wiley.cms.cochrane.cmanager.parser;

import java.util.HashMap;
import java.util.Map;

import org.xml.sax.Attributes;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 27.04.12
 */
public class ResultParser extends SourceParser {

    public static final String CENTRAL_YEAR_TAG   = "YR";
    public static final String CENTRAL_AUTHOR_TAG = "UAU";
    public static final String CENTRAL_PAGE_TAG   = "PG";

    private final Map<String, String> results;
    private final String[] tags;

    public ResultParser(String[] tags) {

        super();

        setTagsCount(tags.length);

        this.tags = tags;
        results = new HashMap<String, String>(tagsCount);
    }

    @Override
    public StringBuilder startElement(int number, Attributes atts) {

        if (number >= 0 && number < tags.length) {

            text = new StringBuilder();
            return text;
        }
        return null;
    }

    @Override
    public void endElement(int number) throws FinishException {

        if (number >= 0 && number < tags.length) {

            String tag = tags[number];
            results.put(tag, text.toString());
            level++;
            text = null;
        }

        if (level == tagsCount) {
            throw new FinishException();
        }
    }

    public String getResult(String tag) {
        return results.get(tag);
    }

    public boolean equalsResult(String tag, String result) {
        return result == null ? !results.containsKey(tag) : result.equals(results.get(tag));
    }
}
