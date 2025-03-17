package com.wiley.cms.cochrane.cmanager.parser;

/**
 * @author <a href='mailto:gkhristich@wiley.ru'>Galina Khristich</a>
 *         Date: 10-May-2007
 * @author <a href='mailto:svyatoslav.gulin@gmail.com'>Svyatoslav Gulin</a>
 * @version 02.09.2011
 */
public class SourceParsingResult {
    private String title;
    private String doi;
    private String status;
    private String sortTitle;
    private String group;
    private String subTitle;
    private String authors;
    private String abstractInfo;
    private String clIssue;
    private char[] implication;
    private String page;
    private int year;
    private String reviewType;

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public String getSubTitle() {
        return subTitle;
    }

    public void setSubTitle(String subTitle) {
        this.subTitle = subTitle;
    }

    public String getReviewType() {
        return reviewType;
    }

    public void setReviewType(String reviewType) {
        this.reviewType = reviewType;
    }

    public String getSortTitle() {
        return sortTitle;
    }

    public void setSortTitle(String sortTitle) {
        this.sortTitle = sortTitle;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDoi() {
        return doi;
    }

    public void setDoi(String doi) {
        this.doi = doi;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getClIssue() {
        return clIssue;
    }

    public void setClIssue(String clIssue) {
        this.clIssue = clIssue;
    }

    public String getAuthors() {
        return authors;
    }

    public void setAuthors(String authors) {
        if (this.authors != null) {
            String comma = "";
            if (!authors.startsWith(" ")) {
                comma = ",";
            }
            this.authors = this.authors + comma + authors;
        } else {
            this.authors = authors.trim();
        }
    }

    public String getAbstractInfo() {
        return abstractInfo;
    }

    public void setAbstractInfo(String abstractInfo) {
        this.abstractInfo = abstractInfo;
    }

    public char[] getImplication() {
        return implication;
    }

    public void setImplication(char[] implication) {
        this.implication = implication;
    }

    public String getPage() {
        return page;
    }

    public void setPage(String page) {
        this.page = page;
    }

    public int getYear() {
        return year;
    }

    public void setYear(int year) {
        this.year = year;
    }
}