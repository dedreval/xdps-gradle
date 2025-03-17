package com.wiley.cms.cochrane.cmanager.parser;

/**
 * @author <a href='mailto:ikirov@wiley.com'>Igor Kirov</a>
 * @version 18.11.2009
 */
public class CLIssueParser extends SourceParser {
    public void endElement(int i) throws FinishException {
        if (this.level == tagsCount && i == tagsCount - 1) {
            result.setClIssue(text.toString());
            level = 0;
        }
    }
}
