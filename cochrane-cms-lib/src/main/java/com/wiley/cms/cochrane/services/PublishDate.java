package com.wiley.cms.cochrane.services;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;

import javax.validation.constraints.NotNull;

import com.wiley.cms.cochrane.cmanager.CmsUtils;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 12/18/2020
 */
public class PublishDate implements Serializable {
    public static final PublishDate EMPTY = new PublishDate(null, null);
    private static final long serialVersionUID = 1L;

    private final Date date;
    private final String input;
    private int issue = 0;

    public PublishDate(String input, Date date, int issue) {
        this(input, date);
        this.issue = issue;
    }

    public PublishDate(@NotNull String input) {
        this.input = input;
        OffsetDateTime ofdt = OffsetDateTime.parse(input, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        this.date = Date.from(ofdt.toInstant());
        LocalDate ld = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        this.issue = CmsUtils.getIssueNumber(ld.getYear(), ld.getMonthValue());
    }

    private PublishDate(String input, Date date) {
        this.input = input;
        this.date = date;
    }

    public String get() {
        return input;
    }

    public Date date() {
        return date;
    }

    public int issue() {
        return issue;
    }

    public boolean isEmpty() {
        return date == null && issue == 0 && (input == null || input.trim().length() == 0);
    }

    @Override
    public String toString() {
        return input;
    }
}
