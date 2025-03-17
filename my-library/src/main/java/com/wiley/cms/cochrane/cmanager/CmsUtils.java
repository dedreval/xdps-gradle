package com.wiley.cms.cochrane.cmanager;

import com.wiley.cms.cochrane.cmanager.data.issue.IssueVO;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.cmanager.specrender.xlsfiles.CreatorXlsWithNewOrUpdated;
import com.wiley.cms.cochrane.repository.IRepository;
import com.wiley.cms.cochrane.repository.RepositoryFactory;
import com.wiley.cms.cochrane.repository.RepositoryUtils;
import com.wiley.cms.cochrane.utils.Constants;
import com.wiley.cms.process.ProcessHelper;
import com.wiley.cms.render.services.IRenderingProvider;
import com.wiley.tes.util.Logger;
import com.wiley.tes.util.Now;
import com.wiley.tes.util.WileyDTDXMLOutputter;
import com.wiley.tes.util.XmlUtils;
import com.wiley.tes.util.jdom.DocumentLoader;
import com.wiley.tes.util.res.Res;
import org.apache.commons.lang.StringUtils;
import org.jdom.Attribute;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.xpath.XPath;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Properties;

import static org.apache.commons.lang.SystemUtils.LINE_SEPARATOR;

/**
 * @author <a href='mailto:gkhristich@wiley.ru'>Galina Khristich</a>
 *         Date: 24-Jan-2007
 */
public class CmsUtils {
    private static final Logger LOG = Logger.getLogger(CmsUtils.class);
    private static final String N_A = "N/A";
    private static String configFile = System.getProperty("jboss.server.config.url")
            + "/cochrane-props/repository.properties";
    private static final String XML_DECLARATION_REGEX = "^.*<\\?xml version[^</>]*?>";

    private static final int YEAR_LENGTH = 4;
    private static final int ISSUE_NUMBER_DEGREE = 100;
    private static final String YEAR_TAG = "<YR>";
    private static final String PAGE_TAG = "<PG>";
    private static final String AMP_HTML = "&amp;";
    private static final String AMP = "&";

    private static final int ISSUE_1 = 1;
    private static final int ISSUE_2 = 2;
    private static final int ISSUE_3 = 3;
    private static final int ISSUE_4 = 4;
    private static final int ISSUE_5 = 5;
    private static final int ISSUE_6 = 6;
    private static final int ISSUE_7 = 7;
    private static final int ISSUE_8 = 8;
    private static final int ISSUE_9 = 9;
    private static final int ISSUE_10 = 10;
    private static final int ISSUE_11 = 11;
    private static final int ISSUE_12 = 12;

    private static final int MS_IN_DAY = 1000 * 60 * 60 * 24;

    private CmsUtils() {
    }

    public static String createRecordNamesList(Collection<Record> list) {
        if (list.size() == 0) {
            return null;
        }
        StringBuilder recNames = new StringBuilder();
        for (Record record : list) {
            if (recNames.length() > 0) {
                recNames.append(",");
            }
            recNames.append("'").append(record.getName()).append("'");
        }
        return recNames.toString();
    }

    public static String[] fillTagsArray(String libName) {
        String[] tags;
        if (libName.contains(CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.CLCENTRAL))) {
            tags = CochraneCMSProperties.getProperty("cms.cochrane.title.clcentral").split("/");
        } else if (libName.contains(CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.CLCMR))) {
            tags = CochraneCMSProperties.getProperty("cms.cochrane.title.clcmr").split("/");
        } else {
            tags = CochraneCMSProperties.getProperty("cms.cochrane.title.common").split("/");
        }
        return tags;
    }

    public static String[] fillSortTagsArray(String libName) {
        String[] tags;
        if (libName.contains(CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.CLCENTRAL))) {
            tags = CochraneCMSProperties.getProperty("cms.cochrane.title.sort.clcentral").split("/");
        } else if (libName.contains(CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.CLCMR))) {
            tags = CochraneCMSProperties.getProperty("cms.cochrane.title.sort.clcmr").split("/");
        } else {
            tags = CochraneCMSProperties.getProperty("cms.cochrane.title.sort.common").split("/");
        }
        return tags;
    }

    public static void deleteSourceWithImages(List<String> fileUris) {
        String[] images = CochraneCMSPropertyNames.getImageDirectories().split(",");
        IRepository rps = RepositoryFactory.getRepository();
        String rootDir = null;
        for (String fileUri : fileUris) {
            if (fileUri == null) {
                break;
            }
            try {
                rps.deleteFile(fileUri);
                for (String imageDir : images) {
                    if (fileUri.contains(imageDir)) {
                        // delete image_n
                        String imagePath = fileUri.substring(0, fileUri.lastIndexOf("/"));
//                        try
//                        {
//                            rps.deleteFile(imagePath);
//                            // delete dir RecordName
//                            rps.deleteFile(imagePath.substring(0, imagePath.lastIndexOf("/")));
//                        }
//                        catch (IOException e)
//                        {
//                            //if folder is not empty (folder can contain more than 1 file)
//                            LOG.debug(e);
//                        }
                        rootDir = imagePath.substring(0, imagePath.lastIndexOf("/"));
                    }
                }
            } catch (IOException e) {
                LOG.debug(e, e);
            }
        }

        if (rootDir != null) {
            try {
                rps.deleteFile(rootDir);
            } catch (IOException e) {
                LOG.debug(e);
            }
        }
//        String packagePath = FilePathCreator.getPackageDir(files[0]) + "/";
//        todo check dir isEmpty
//        try
//        {
//            rps.deleteDir(packagePath);
//        }
//        catch (IOException e)
//        {
//            LOG.error(e, e);
//        }
    }

    public static Properties getRepositoryProperty() {
        Properties props = new Properties();
        try {
            props.load(new URL(configFile).openStream());
        } catch (Exception e) {
            LOG.error(e);
        }
        return props;
    }

    public static String[] listToArray(List<String> list) {
        String[] array = new String[list.size()];
        for (int i = 0; i < list.size(); i++) {
            array[i] = list.get(i);
        }
        return array;
    }


    public static void closeStream(Closeable is) {
        if (is != null) {
            try {
                is.close();
            } catch (IOException e) {
                LOG.error(e, e);
            }
        }
    }

    public static String correctDtdPath(final String data) {
        return WileyDTDXMLOutputter.correctDtdPath(data, true);
    }

    public static String getDoctypeDeclaration(final String xml) {
        int ind1 = xml.indexOf("<!DOCTYPE");
        int ind2 = xml.indexOf(">", ind1);
        return xml.substring(ind1, ind2 + 1);
    }

    public static String cutXmlDeclaration(String content) {
        if (StringUtils.isEmpty(content)) {
            return content;
        }
        return content.replaceFirst(XML_DECLARATION_REGEX, "");
    }

    public static boolean moveDir(String srcPath, String toPath, IRepository rp) throws Exception {
        RepositoryUtils.deleteDir(toPath, rp);
        if (rp.isFileExists(srcPath)) {
            writeDir(srcPath, toPath, true, rp);
            rp.deleteDir(srcPath);
            return true;
        }
        return false;
    }

    public static String getOrDefault(String str) {
        return StringUtils.isNotBlank(str) ? str : N_A;
    }

    public static Exception restoreDir(String[] paths, IRepository rp)  {
        try {
            restoreDir(paths[0], paths[1], paths[2], rp);
            return null;
        } catch (Exception e) {
            return e;
        }
    }

    public static void restoreDir(String srcPath, String toPath, String backupPath, IRepository rp) throws Exception {
        if (backupPath != null && rp.isFileExists(backupPath)) {
            LOG.debug(String.format("%s folder is restoring from %s", toPath, backupPath));
            moveDir(backupPath, toPath, rp);

        } else {
            RepositoryUtils.deleteDir(toPath, rp);
        }
        //if (srcPath != null rp.isFileExists(srcPath)) {
        //    rp.deleteDir(srcPath);
        //}
    }

    public static Exception restoreFile(String[] paths, IRepository rp)  {
        String srcPath = paths[0];
        String toPath = paths[1];
        String backupPath = paths[2];
        try {
            RepositoryUtils.deleteFile(toPath, rp);
            if (backupPath != null && rp.isFileExists(backupPath)) {
                LOG.debug(String.format("%s is restoring from %s", toPath, backupPath));
                rp.putFile(toPath, rp.getFile(backupPath));
            }
            //if (srcPath != null && rp.isFileExists(srcPath)) {
            //    rp.deleteDir(srcPath);
            //}
            return null;

        } catch (Exception e) {
            return e;
        }
    }

    public static Exception replaceDir(String[] paths, IRepository rp)  {
        try {
            replaceDir(paths[0], paths[1], paths[2], rp);
            return null;
        } catch (Exception e) {
            return e;
        }
    }

    public static void replaceDir(String srcPath, String toPath, String backupPath, IRepository rp) throws Exception {
        if (rp.isFileExists(toPath)) {
            if (backupPath != null) {
                LOG.debug(String.format("%s folder is moving to %s", toPath, backupPath));
                moveDir(toPath, backupPath, rp);
            } else {
                rp.deleteDir(toPath);
            }
        }
        if (srcPath != null && rp.isFileExists(srcPath)) {
            LOG.debug(String.format("%s folder is copying to %s", srcPath, toPath));
            writeDir(srcPath, toPath, true, rp);
        }
    }

    public static Exception replaceFile(String[] paths, IRepository rp) {
        return replaceFile(paths[0], paths[1], paths[2], rp);
    }

    public static Exception replaceFile(String srcPath, String toPath, String backupPath, IRepository rp) {
        try {
            if (rp.isFileExists(toPath)) {
                if (backupPath != null) {
                    LOG.debug(String.format("%s is moving to %s", toPath, backupPath));
                    rp.putFile(backupPath, rp.getFile(toPath));
                }
                rp.deleteFile(toPath);
            }
            if (srcPath != null && rp.isFileExists(srcPath)) {
                LOG.debug(String.format("%s is copying to %s", srcPath, toPath));
                rp.putFile(toPath, rp.getFile(srcPath));
            }
            return null;

        } catch (Exception e) {
            return e;
        }
    }

    public static void writeDir(String dir, String newPath, boolean ignoreEmptyDir, IRepository rp) throws IOException {
        if (!ignoreEmptyDir) {
            writeDir(dir, newPath);

        } else {
            File[] files = rp.getFilesFromDir(dir);
            if (files == null) {
                return;
            }
            for (File f : files) {
                String path = newPath + "/" + f.getName();
                if (f.isDirectory()) {
                    writeDir(f.getAbsolutePath(), path, ignoreEmptyDir, rp);
                } else {
                    rp.putFile(path, rp.getFile(f.getAbsolutePath()));
                }
            }
        }
    }

    public static void writeDir(String dir, String newPath) throws IOException {
        IRepository rps = RepositoryFactory.getRepository();
        File[] files = rps.getFilesFromDir(dir);
        if (files == null) {
            throw new IOException("directory " + dir + " is empty");
        }
        for (File f : files) {
            String path = newPath + "/" + f.getName();
            if (f.isDirectory()) {
                writeDir(f.getAbsolutePath(), path);
            } else {
                rps.putFile(path, rps.getFile(f.getAbsolutePath()));
            }
        }
    }

    public static String encodeEntities(final String source) {
        StringBuilder buf = new StringBuilder(source.length());
        int curPos = 0;
        int tagPos;
        while ((tagPos = source.indexOf(AMP, curPos)) >= 0) {
            buf.append(source.substring(curPos, tagPos));
            buf.append(AMP_HTML);
            curPos = tagPos + 1;
        }
        buf.append(source.substring(curPos));
        return buf.toString();
    }

    public static String decodeEntities(final String source) {
        StringBuilder buf = new StringBuilder(source.length());
        int curPos = 0;
        int tagPos;
        while ((tagPos = source.indexOf(AMP_HTML, curPos)) >= 0) {
            buf.append(source.substring(curPos, tagPos));
            buf.append(AMP);
            curPos = tagPos + AMP_HTML.length();
        }
        buf.append(source.substring(curPos));
        return buf.toString();
    }

    public static String unescapeEntities(String str) {
        return str != null ? CreatorXlsWithNewOrUpdated.replaceEntities(XmlUtils.unescapeHtmlEntities(str)) : null;
    }

    public static String getRecordPageNumber(String src) {
        if ((src == null) || ("".equals(src))) {
            return "";
        }

        int pageTagPos = src.indexOf(PAGE_TAG);
        String pageNumber = "";
        if (pageTagPos != -1) {
            pageNumber = src.substring(pageTagPos + PAGE_TAG.length(), src.indexOf("-", pageTagPos));
        }

        return pageNumber;
    }

    public static String getRecordYear(String src) {
        if ((src == null) || ("".equals(src))) {
            return "";
        }

        int yearTagPos = src.indexOf(YEAR_TAG);
        String year = "";
        if (yearTagPos != -1) {
            year = src.substring(yearTagPos + YEAR_TAG.length(), yearTagPos + YEAR_TAG.length() + YEAR_LENGTH);
        }

        return year;
    }

    public static int getPreviousIssueNumber(int year, int issue) {
        return (issue == ISSUE_1) ? (year - 1) * ISSUE_NUMBER_DEGREE + ISSUE_12
                : year * ISSUE_NUMBER_DEGREE + (issue - 1);
    }
    
    public static int getIssueNumber(int year, int issue) {
        return year * ISSUE_NUMBER_DEGREE + issue;
    }

    public static int getIssueByIssueNumber(int issueNumber) {
        return issueNumber % ISSUE_NUMBER_DEGREE;
    }

    public static int getYearByIssueNumber(int issueNumber) {
        return issueNumber / ISSUE_NUMBER_DEGREE;
    }

    public static int convertYearByDbName(int year, int issue, String databaseName) {
        return CochraneCMSPropertyNames.getCmrDbName().equals(databaseName) && issue == ISSUE_12 ? year + 1 : year;
    }

    public static String[] buildIssueParamString(int year, int number,  boolean isCentral) {

        if (isCentral) {
            return new String[]{ProcessHelper.buildKeyValueParam(IRenderingProvider.JOB_PARAM_ISSUE_YEAR, year),
                    ProcessHelper.buildKeyValueParam(IRenderingProvider.JOB_PARAM_ISSUE_NUMBER, number)};
        }

        String[] result;
        switch (number) {
            case ISSUE_12:
                result = new String[]{ProcessHelper.buildKeyValueParam(IRenderingProvider.JOB_PARAM_ISSUE_YEAR,
                    year + 1), ProcessHelper.buildKeyValueParam(IRenderingProvider.JOB_PARAM_ISSUE_NUMBER, ISSUE_1)};
                break;
            case ISSUE_1:
                result = new String[]{ProcessHelper.buildKeyValueParam(IRenderingProvider.JOB_PARAM_ISSUE_YEAR, year),
                        ProcessHelper.buildKeyValueParam(IRenderingProvider.JOB_PARAM_ISSUE_NUMBER, ISSUE_1)};
                break;
            case ISSUE_3:
            case ISSUE_4:
                result = new String[]{ProcessHelper.buildKeyValueParam(IRenderingProvider.JOB_PARAM_ISSUE_YEAR, year),
                        ProcessHelper.buildKeyValueParam(IRenderingProvider.JOB_PARAM_ISSUE_NUMBER, ISSUE_2)};
                break;
            case ISSUE_6:
            case ISSUE_7:
                result = new String[]{ProcessHelper.buildKeyValueParam(IRenderingProvider.JOB_PARAM_ISSUE_YEAR, year),
                        ProcessHelper.buildKeyValueParam(IRenderingProvider.JOB_PARAM_ISSUE_NUMBER, ISSUE_3)};
                break;
            case ISSUE_9:
            case ISSUE_10:
                result = new String[]{ProcessHelper.buildKeyValueParam(IRenderingProvider.JOB_PARAM_ISSUE_YEAR, year),
                        ProcessHelper.buildKeyValueParam(IRenderingProvider.JOB_PARAM_ISSUE_NUMBER, ISSUE_4)};
                break;
            default:
                result = new String[]{ProcessHelper.buildKeyValueParam(IRenderingProvider.JOB_PARAM_ISSUE_YEAR, year),
                       ProcessHelper.buildKeyValueParam(IRenderingProvider.JOB_PARAM_ISSUE_NUMBER, number)};
        }
        return result;
    }

    public static int convertIssueByDbName(int issue, String databaseName) {
        // todo replace this logic to something properties
        int ret = issue;
        if (CochraneCMSPropertyNames.getCmrDbName().equals(databaseName)) {

            switch(issue) {
                case ISSUE_12:
                    ret = ISSUE_1;
                    break;

                case ISSUE_3:
                case ISSUE_4:
                    ret = ISSUE_2;
                    break;

                case ISSUE_6:
                case ISSUE_7:
                    ret = ISSUE_3;
                    break;

                case ISSUE_9:
                case ISSUE_10:
                    ret = ISSUE_4;
                    break;
                default:
                    break;
            }
        }

        return ret;
    }

    public static int getFullIssueNumber(Date date) {
        LocalDateTime ldt = LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
        return ldt.getYear() * ISSUE_NUMBER_DEGREE + ldt.getMonth().getValue();
    }

    public static int getFullIssueNumber(LocalDate ld) {
        return ld.getYear() * ISSUE_NUMBER_DEGREE + ld.getMonth().getValue();
    }

    public static int getFullIssueNumber(Date date, int offset) {
        OffsetDateTime offsetDateTime = OffsetDateTime.ofInstant(date.toInstant(), ZoneOffset.ofTotalSeconds(offset));
        return CmsUtils.getIssueNumber(offsetDateTime.getYear(), offsetDateTime.getMonthValue());
    }

    public static String getIssueDay(Date issueDate) {

        Calendar cl =  GregorianCalendar.getInstance();
        cl.setTime(issueDate);
        return String.format(Constants.ISSUE_NUMBER_FORMAT, cl.get(Calendar.DAY_OF_MONTH));
    }

    public static String getIssueMonth(int month) {
        return String.format(Constants.ISSUE_NUMBER_FORMAT, month);
    }

    public static String getIssueMonth(Date issueDate) {

        Calendar cl =  GregorianCalendar.getInstance();
        cl.setTime(issueDate);
        return String.format(Constants.ISSUE_NUMBER_FORMAT, cl.get(Calendar.MONTH) + 1);
    }

    public static String getIssueYear(Date issueDate) {

        Calendar cl =  Calendar.getInstance();
        cl.setTime(issueDate);
        return "" + cl.get(Calendar.YEAR);
    }

    public static Date getDate(int year, int month, int day) {

        Calendar calendar = GregorianCalendar.getInstance();

        calendar.set(year, month - 1, day, 0, 0, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime();
    }

    public static boolean equalsDates(Date firstDate, Date secondDate) {
        boolean ret =  false;

        if (firstDate == null && secondDate == null) {
            ret = true;
        } else if (firstDate != null && secondDate != null) {
            ret = Math.abs(firstDate.getTime() - secondDate.getTime()) < MS_IN_DAY;
        }
        return ret;
    }

    public static boolean isConversionTo3gAvailable(String dbName) {
        Res<BaseType> bt = BaseType.find(dbName);
        return Res.valid(bt) && bt.get().canMl3gConvert();
    }

    public static boolean getErrorsFromQaResults(String message,
                                                 StringBuilder errors,
                                                 DocumentLoader dl) throws IOException, JDOMException {
        Document document = dl.load(message);
        Attribute successfulAttr = (Attribute) XPath.selectSingleNode(document, "/results/job/@successful");
        boolean successful = successfulAttr.getBooleanValue();
        StringBuilder tmp = new StringBuilder();

        if (!successful) {
            List nodes = XPath.selectNodes(document, "//messages/message[@quality < 100]");
            for (Object node : nodes) {
                Element element = (Element) node;
                tmp.append(element.getText()).append(LINE_SEPARATOR);
            }
            if (tmp.length() >= LINE_SEPARATOR.length()) {
                errors.append(tmp.substring(0, tmp.length() - LINE_SEPARATOR.length()));
            }
        }
        return successful;
    }

    public static ZonedDateTime getCochraneDownloaderDateTime() {
        String zone = CochraneCMSPropertyNames.getCochraneDownloaderTimeZone();
        return zone == null ? ZonedDateTime.now() : ZonedDateTime.now(ZoneId.of(zone));
    }

    public static LocalDate getCochraneDownloaderDate() {
        String zone = CochraneCMSPropertyNames.getCochraneDownloaderTimeZone();
        return zone == null ? LocalDate.now() : LocalDate.now(ZoneId.of(zone));
    }

    public static Calendar getArchieDownloaderCalendar() {
        String zone = CochraneCMSPropertyNames.getCochraneDownloaderTimeZone();
        return zone == null ? Now.getNowUTC() : Now.getNow(zone);
    }

    public static int getBeforeTopicalIssueNumber() {
        return getBeforeTopicalIssueNumber(getArchieDownloaderCalendar());
    }

    public static int getBeforeTopicalIssueNumber(Calendar cl) {

        int year = cl.get(Calendar.YEAR);
        int month = cl.get(Calendar.MONTH);
        if (month < 2) {
            year--;
        }
        cl.roll(Calendar.MONTH, false);
        cl.roll(Calendar.MONTH, false);

        month = Now.getCalendarMonth(cl);
        return CmsUtils.getIssueNumber(year, month);
    }

    public static String getLoginName(String log) {
        return org.apache.commons.lang.StringUtils.isNotEmpty(log) ? log : CochraneCMSPropertyNames.getSystemUser();
    }

    /**
     * Extract path part from the given URL and returns it.
     * @param url String representation of the URL
     * @return path part of the given URL or the URL if it's invalid
     */
    public static String getUrlPathPart(String url) {
        try {
            return new URL(url).getPath();
        } catch (MalformedURLException e) {
            return url;
        }
    }

    public static boolean isFirstMoreThanSecond(String version1, String version2) {
        boolean ret = false;
        if (version1 != null && version2 != null) {
            try {
                float v1 = Float.valueOf(version1);
                float v2 = Float.valueOf(version2);
                ret = v1 > v2;

            } catch (NumberFormatException e) {
                LOG.error(e);
            }
        } else if (version2 == null) {
            ret = true;
        }
        return ret;
    }

    public static boolean saveNotification(String out, String fileName) {
        try {
            InputStream is = new ByteArrayInputStream(out.getBytes());
            IRepository rp = RepositoryFactory.getRepository();
            RepositoryUtils.deleteFile(fileName, rp);
            rp.putFile(fileName, is, true);
            printSuccessLog(fileName);
            return true;

        } catch (Exception e) {
            LOG.error(e);
            return false;
        }
    }

    public static boolean saveSFTPNotification(String localPath, String dlqRepeatFolder) {
        IRepository rp = RepositoryFactory.getRepository();
        try (InputStream is = rp.getFile(localPath)) {
            String filePath = dlqRepeatFolder + getPackageNameFromPath(localPath);
            RepositoryUtils.deleteFile(filePath, rp);
            rp.putFile(filePath, is, false);
            printSuccessLog(filePath);
            return true;
        } catch (Exception e) {
            LOG.error(e);
            return false;
        }
    }

    public static boolean deleteFileByPath(String localPath) {
        try {
            IRepository rp = RepositoryFactory.getRepository();
            RepositoryUtils.deleteFile(localPath, rp);
            return true;
        } catch (Exception e) {
            LOG.error(e);
            return false;
        }
    }

    public static String addIssueToPath(String path) {
        Calendar cl = GregorianCalendar.getInstance();
        return path + getIssueNumber(cl.get(Calendar.YEAR), cl.get(Calendar.MONTH) + 1) + FilePathCreator.SEPARATOR;
    }

    public static boolean isImportIssue(Integer issueId) {
        return Constants.IMPORT_JATS_ISSUE_ID.equals(issueId);
    }

    public static boolean isImportIssueNumber(int fullIssueNumber) {
        return Constants.IMPORT_JATS_ISSUE_NUMBER == fullIssueNumber;
    }

    public static boolean isScheduledIssue(Integer issueId) {
        return Constants.SPD_ISSUE_ID.equals(issueId);
    }

    public static boolean isScheduledIssueNumber(int fullIssueNumber) {
        return Constants.SPD_ISSUE_NUMBER == fullIssueNumber;
    }

    public static String buildIssueNumber(IssueVO issue, boolean spd) {
        return spd ? Constants.SPD : Integer.toString(issue.getNumber());
    }

    public static String buildIssueYear(IssueVO issue, boolean spd) {
        return spd ? "" + LocalDate.now().getYear() : Integer.toString(issue.getYear());
    }

    public static String buildIssuePackageTitle(Integer issueNumber) {
        return issueNumber == null ? null : buildIssuePackageTitle(getYearByIssueNumber(issueNumber),
                getIssueByIssueNumber(issueNumber));
    }

    public static String buildIssuePackageTitle(int issueYear, int issueMonth) {
        return String.format("Issue %d (%s) %d",
                issueMonth, LocalDate.of(issueYear, issueMonth, 1).getMonth().toString(), issueYear);
    }

    public static boolean isScheduledDb(Integer dbId) {
        return Constants.SPD_DB_CDSR_ID.equals(dbId) || Constants.SPD_DB_EDITORIAL_ID.equals(dbId);
    }

    public static boolean isSpecialIssue(Integer issueId) {
        return isImportIssue(issueId) || isScheduledIssue(issueId);
    }

    public static boolean isSpecialIssueNumber(int fullIssueNumber) {
        return fullIssueNumber <= Constants.SPD_ISSUE_NUMBER;
    }

    public static String getPackageNameFromPath(String localPath) {
        return localPath.substring(localPath.lastIndexOf("/") + 1);
    }

    private static void printSuccessLog(String fileName) {
        LOG.info("notification is stored in: " + fileName);
    }
}