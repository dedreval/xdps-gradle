package com.wiley.tes.util;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import org.quartz.CronExpression;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 02.08.13
 */
public class Now {

    public static final String DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm";
    public static final String DATE_TIME_FORMAT_OUT = "yyyy-MM-dd'T'HH:mm:ss";
    public static final String DATE_FORMAT = "yyyy-MM-dd";
    public static final String DATE_FORMAT_LIGHT = "yyyy-M-d";
    public static final String LONG_DATE_FORMAT = "yyyyMMddHHmmss";

    public static final DateFormat SHORT_DATE_FORMATTER = new SimpleDateFormat("yyyyMMdd");
    public static final DateFormat LONG_DATE_FORMATTER = new SimpleDateFormat(LONG_DATE_FORMAT);
    public static final SimpleDateFormat DATE_TIME_STANDARD_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
    public static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern(DATE_FORMAT);
    public static final DateTimeFormatter DATE_FORMATTER_LIGHT = DateTimeFormatter.ofPattern(DATE_FORMAT_LIGHT);
    public static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern(DATE_TIME_FORMAT);
    public static final DateTimeFormatter DATE_TIME_FORMATTER_OUT = DateTimeFormatter.ofPattern(DATE_TIME_FORMAT_OUT);
    public static final DateTimeFormatter SIMPLE_DATE_TIME_FORMATTER
            = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss z");

    public static final ZoneId UTC_ZONE = TimeZone.getTimeZone("UTC").toZoneId();
    public static final String START_DATE = formatDateUTC(new Date());

    public static final int MS_IN_SEC   = 1000;
    public static final int SEC_IN_MIN  = 60;
    public static final int MIN_IN_HOUR = 60;
    public static final int HOUR_IN_DAY = 24;

    public static final int JANUARY   = 1;
    public static final int MARCH     = 3;
    public static final int APRIL     = 4;
    public static final int JUNE      = 6;
    public static final int JULY      = 7;
    public static final int SEPTEMBER = 9;
    public static final int OCTOBER   = 10;
    public static final int DECEMBER  = 12;

    private static final DateTimeFormatter EPOCH_FORMATTER = DateTimeFormatter.ofPattern(LONG_DATE_FORMAT);
    private static final ZoneId EPOCH_ZONE = ZoneId.of("EST5EDT");

    private Now() {
    }

    public static int calculateMillisInHour() {
        return  calculateMillisInMinute() * MIN_IN_HOUR;
    }

    public static int calculateMillisInMinute() {
        return MS_IN_SEC * SEC_IN_MIN;
    }

    public static Calendar getNowUTC() {

        Calendar cl = GregorianCalendar.getInstance();
        cl.setTimeInMillis(cl.getTimeInMillis() - getTimeZoneOffset(cl));
        return cl;
    }

    public static Calendar convertToUTC(Date date) {

        Calendar cl = GregorianCalendar.getInstance();
        cl.setTimeInMillis(date.getTime());
        cl.setTimeInMillis(cl.getTimeInMillis() - getTimeZoneOffset(cl));
        return cl;
    }

    public static Calendar convertToZone(Date date, String zone) {

        if (zone == null) {
            Calendar cl = GregorianCalendar.getInstance();
            cl.setTimeInMillis(date.getTime());
        }

        Calendar cl = Calendar.getInstance(TimeZone.getTimeZone(zone));
        cl.setTimeInMillis(convertToUTC(date).getTimeInMillis() + getTimeZoneOffset(cl));
        return cl;
    }

    public static int getCalendarMonth(Calendar cl) {
        return cl.get(Calendar.MONTH) + 1;
    }

    public static int getCalendarYear(Calendar cl) {
        return cl.get(Calendar.YEAR);
    }

    public static Calendar getNow(String zone) {

        if (zone == null) {
            return GregorianCalendar.getInstance();
        }

        Calendar cl = Calendar.getInstance(TimeZone.getTimeZone(zone));
        cl.setTimeInMillis(getNowUTC().getTimeInMillis() + getTimeZoneOffset(cl));
        return cl;
    }

    public static Date normalizeDate(Calendar cl) {
        cl.clear(Calendar.MILLISECOND);
        cl.clear(Calendar.SECOND);
        cl.clear(Calendar.MINUTE);
        cl.clear(Calendar.HOUR);

        return cl.getTime();
    }

    public static Date normalizeDate(Date issueDate) {
        Calendar cl = GregorianCalendar.getInstance();
        cl.setTime(issueDate);
        return normalizeDate(cl);
    }

    public static Date normalizeDate(int year, int month, int day) {
        return normalizeDate(LocalDate.of(year, month, day));
    }

    public static Date normalizeDate(LocalDate ld) {
        return Date.from(ld.atTime(0, 0).atZone(ZoneId.systemDefault()).toInstant());
    }

    public static String buildFullTime(long time) {
        int msInHour = calculateMillisInHour();
        int msInMin = calculateMillisInMinute();
        int hour = (int) (time / msInHour);
        int rest = (int) (time % msInHour);
        int min =  rest / msInMin;
        int sec =  (rest % msInMin) / MS_IN_SEC;
        return String.format("%d:%02d:%02d", hour, min, sec);
    }

    public static String buildTime(long time) {

        int msInHour = calculateMillisInHour();
        int hour = (int) (time / msInHour);
        int min =  (int) ((time % msInHour) / calculateMillisInMinute());

        return hour > 0 ? String.format("%d hour(s) %d minute(s)", hour, min)
                : (min > 0 ? String.format("%d minute(s)", min) : "1 minute");
    }

    public static String buildDate(Date date) {
        return String.format("%td-%tb-%tY %tT", date, date, date, date);
    }

    public static Date getNextValidTimeAfter(String expression, Date date) throws ParseException {
        CronExpression ce = new CronExpression(expression);
        return ce.getNextValidTimeAfter(date);
    }

    private static int getTimeZoneOffset(Calendar cl) {
        return cl.get(Calendar.ZONE_OFFSET) + cl.get(Calendar.DST_OFFSET);
    }

    public static Date parseDateUTC(String date, DateTimeFormatter formatter) {
        LocalDateTime ldt = LocalDateTime.parse(date, formatter);
        return convertToDate(ldt, ZoneOffset.UTC);
    }

    public static Date convertToDate(LocalDateTime ldt) {
        return Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant());
    }

    public static Date parseDate(String date) {
        return parseDate(date, DateTimeFormatter.ISO_DATE_TIME, ZoneId.systemDefault());
    }

    public static Date parseDate(String date, DateTimeFormatter formatter, ZoneId zoneId) {
        LocalDateTime ldt = LocalDateTime.parse(date, formatter);
        return Date.from(ldt.atZone(zoneId).toInstant());
    }

    public static String formatDate(Date date,  DateTimeFormatter formatter, ZoneId zoneId) {
        return formatter.format(LocalDateTime.ofInstant(date.toInstant(), zoneId));
    }

    public static String formatDate(Date date) {
        return formatDate(date, DateTimeFormatter.ISO_DATE, ZoneId.systemDefault());
    }

    public static String formatDateUTC(Date date) {
        return DateTimeFormatter.ISO_INSTANT.format(ZonedDateTime.ofInstant(date.toInstant(), UTC_ZONE));
    }

    public static long getNowMinusDays(int days) {
        LocalDateTime ldt = LocalDateTime.now();
        if (days > 0) {
            ldt = ldt.minusDays(days);
        }
        return ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    public static long getNowMinusMonths(int months) {
        LocalDateTime ldt = LocalDateTime.now();
        if (months > 0) {
            ldt = ldt.minusMonths(months);
        }
        return ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    public static LocalDate convertToLocalDate(Date date) {
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private static Date convertToDate(LocalDateTime ldt, ZoneOffset zone) {
        Instant instant = ldt.toInstant(zone);
        return Date.from(instant);
    }
}
