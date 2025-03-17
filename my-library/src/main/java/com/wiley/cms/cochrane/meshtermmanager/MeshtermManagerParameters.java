package com.wiley.cms.cochrane.meshtermmanager;

import java.io.File;
import java.io.Serializable;

/**
 * @author <a href='mailto:ikirov@wiley.com'>Igor Kirov</a>
 * @version 03.11.2009
 */
public class MeshtermManagerParameters implements Serializable {
    private File[] files;
    private int it = 0;
    private int issue;
    private int issueId;
    private String title;

    public MeshtermManagerParameters(File[] files, int issue, int issueId, String title) {
        this.files = files;
        this.issue = issue;
        this.issueId = issueId;
        this.title = title;
    }

    public MeshtermManagerParameters(File[] files, int issue, int issueId, String title, int it) {
        this.files = files;
        this.issue = issue;
        this.issueId = issueId;
        this.title = title;
        this.it = it;
    }

    public void setIterator(int it) {
        this.it = it;
    }

    public int getIterator() {
        return it;
    }

    public File[] getFiles() {
        return files;
    }

    public int getIssue() {
        return issue;
    }

    public int getIssueId() {
        return issueId;
    }

    public String getTitle() {
        return title;
    }
}
