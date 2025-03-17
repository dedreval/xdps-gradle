package com.wiley.cms.cochrane.utils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.Calendar;

import com.wiley.cms.cochrane.cmanager.CmsUtils;
import com.wiley.tes.util.Now;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 12/6/2016
 */
public class IssueDate {
    private static final String TRANSLATIONS = "translations";
    public final int year;
    public final int month;
    public final int day;

    public final int issueYear;
    public final int issueMonth;

    public IssueDate(Calendar cl) {
        this (cl, Constants.NO_ISSUE);
    }

    public IssueDate(LocalDate ld) {
        this (ld, Constants.NO_ISSUE);
    }

    public IssueDate(Calendar cl, int issueNumber) {
        this(cl.get(Calendar.YEAR), Now.getCalendarMonth(cl), Calendar.DAY_OF_MONTH, issueNumber);
    }

    public IssueDate(LocalDate ld, int issueNumber) {
        this(ld.getYear(), ld.getMonthValue(), ld.getDayOfMonth(), issueNumber);
    }

    public IssueDate(LocalDateTime ldt, int issueNumber) {
        this(ldt.getYear(), ldt.getMonthValue(), ldt.getDayOfMonth(), issueNumber);
    }

    public IssueDate(ZonedDateTime ldt) {
        this(ldt, Constants.NO_ISSUE);
    }

    public IssueDate(ZonedDateTime ldt, int issueNumber) {
        this(ldt.getYear(), ldt.getMonthValue(), ldt.getDayOfMonth(), issueNumber);
    }

    private IssueDate(int year, int month, int day, int issueNumber) {
        this.year = year;
        this.month = month;
        this.day = day;

        if (issueNumber == Constants.NO_ISSUE) {
            issueYear = year;
            issueMonth = month;

        } else {
            issueYear = CmsUtils.getYearByIssueNumber(issueNumber);
            issueMonth = CmsUtils.getIssueByIssueNumber(issueNumber);
        }
    }

    public static String getTranslationPackagePrefix() {
        return TRANSLATIONS;
    }
}
