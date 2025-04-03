package com.wiley.cms.cochrane.test;

import java.io.File;
import java.io.FileInputStream;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.io.IOUtils;

import com.wiley.cms.cochrane.cmanager.CmsException;
import com.wiley.cms.cochrane.cmanager.res.PackageType;
import com.wiley.cms.cochrane.cmanager.res.TransAbs;
import com.wiley.cms.cochrane.utils.Constants;
import com.wiley.tes.util.Extensions;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 7/8/2020
 */
public class PackageChecker {

    public static final String CDSR_PREFIX = "crgs";
    public static final String TA_SUFFIX = "ta_";
    public static final String ARCHIE_ARIES_POSTFIX = "archie-aries";
    public static final String JATS_POSTFIX = "_jats";
    public static final String WML3G_POSTFIX = "_wml3g";
    public static final String REPEAT_PACKAGE_NAME_POSTFIX = "_repeat";
    public static final String AUT_SUFFIX = "_aut";
    public static final String AUT_REPEAT_SUFFIX = "_aut_repeat";
    public static final String AUT_REPROCESS_SUFFIX = "_aut_reprocess";
    public static final String TRANSLATIONS = "translations";

    public static final String MESH_UPDATE_SUFFIX = "_mu";
    public static final String PROPERTY_UPDATE_SUFFIX = "_aut_update";
    public static final String PROPERTY_UPDATE_WML3G_SUFFIX = PROPERTY_UPDATE_SUFFIX + WML3G_POSTFIX;
    public static final String PROPERTY_UPDATE_PDF_SUFFIX = PROPERTY_UPDATE_SUFFIX + "_pdf" + WML3G_POSTFIX;

    public static final String ARIES_FOLDER = "aries";
    public static final String ARCHIE = "archie";
    public static final String APTARA = "aptara";
    public static final String METAXIS = "metaxis";
    public static final String ARIES = ARIES_FOLDER;

    public static final String EXPORT_PACKAGE_STATUS_COMPLETED = "Export completed";

    private PackageChecker() {
    }

    public static String getCDSRPackageArticles(File packageFile, boolean jats, Map<String, Set<String>> results) {
        return checkPackage(packageFile, (jats ? PackageType.findCDSRJats() : PackageType.findCDSRRevman()).get(), null,
                false, false, false, results);
    }

    /**
     * Checks a zip archive if its structure maths the xml source export package
     * @param packageFile   The zip archive to check
     * @param cdNumbers     cdNumbers that should be present
     * @param results       If not NULL, it will contain archive entries
     * @return   The error string or NULL if there are no errors
     */
    public static String checkXmlSourceExportPackage(File packageFile, Set<String> cdNumbers,
                                                     Map<String, Set<String>> results) {
        return checkPackage(packageFile, PackageType.find("export-xml-source").get(), cdNumbers,
                false, false, true, results);
    }

    /**
     * Checks a zip archive if its structure maths the JATS initial package
     * @param packageFile  The zip archive to check
     * @param cdNumbers    cdNumbers that should be present
     * @param hasTA        <tt>true</tt> if this archive should contain translations
     * @param results      If not NULL, it will contain archive entries
     * @return  The error string or NULL if there are no errors
     */
    public static String checkJatsPackage(File packageFile, Set<String> cdNumbers, boolean hasTA,
                                          Map<String, Set<String>> results) {
        return checkPackage(packageFile, PackageType.findCDSRJats().get(), cdNumbers, hasTA, true, false, results);
    }

    public static String checkJatsPackage(File packageFile, String cdNumber, boolean hasTA,
                                          Map<String, Set<String>> results) {
        Set<String> cdNumbers = new HashSet<>();
        cdNumbers.add(cdNumber);
        return checkPackage(packageFile, PackageType.findCDSRJats().get(), cdNumbers, hasTA, true, false, results);
    }


    private static String checkPackage(File packageFile, PackageType packageType, Set<String> cdNumbers,
            boolean checkJatsTA, boolean checkDarArchive, boolean mixedContent, Map<String, Set<String>> results) {

        ZipInputStream zis = null;
        StringBuilder errs = new StringBuilder();
        boolean checkCdNumbers = cdNumbers != null;
        try {
            zis = new ZipInputStream(new FileInputStream(packageFile));
            if (checkDarArchive)  {
                ZipEntry ze;
                while ((ze = zis.getNextEntry()) != null) {
                    if (ze.isDirectory()) {
                        continue;
                    }
                    parseJatsZipEntry(zis, ze, packageType, cdNumbers, checkJatsTA, errs, results);
                }
            } else {
                parseArticle(null, packageType, null, cdNumbers, mixedContent, zis, results);
            }
            if (checkCdNumbers) {
                cdNumbers.forEach(missed -> errs.append("\n").append(missed).append(" contains no document file"));
            }

        } catch (Throwable tr) {
            errs.append("\n").append(tr.getMessage());
        } finally {
            IOUtils.closeQuietly(zis);
        }
        return errs.length() == 0 ? null : errs.toString();
    }

    /**
     * Extracts a timestamp string from the name of an export package
     * @param dbName           The Cochrane database name
     * @param exportFileName   The export package name
     * @param curYear          The current year
     * @param curMonth         The current month in the "MM" format
     * @return                 The timestamp related to the export package
     */
    public static String getExportTimestamp(String dbName, String exportFileName, int curYear, String curMonth) {
        return exportFileName.replace(
                Extensions.ZIP, "").replace(
                        buildCommonPrefix(dbName, curYear, curMonth), "").replace(
                                buildCRGSPrefix(curYear, curMonth), "").replace(
                                        JATS_POSTFIX, "").replace(
                                                TA_SUFFIX, "");
    }

    public static boolean isJatsPackage(String packageFileName) {
        return packageFileName.endsWith(PackageChecker.JATS_POSTFIX + Extensions.ZIP);
    }

    public static String buildCRGSPrefix(int year, String month) {
        return CDSR_PREFIX + "-" + year + "-" + month + "_";
    }

    public static String buildCommonPrefix(String dbName, int year, String month) {
        return dbName + "_" + year + "_" + month + "_";
    }

    public static String replaceBackslash2Forward(String str) {
        return str.replaceAll("\\\\", "/");
    }

    private static PackageType.EntryType getPackageEntryType(String path, PackageType packageType,
            PackageType.Entry rootEntry, boolean canBeSkipped) throws CmsException {

        PackageType.Entry entry = rootEntry != null ? rootEntry.match(path)
                : packageType.match(path);
        if (entry == null) {
            if (canBeSkipped) {
                return PackageType.EntryType.UNDEF;
            }
            throw PackageType.throwUnknownEntry(path);
        }
        return entry.getType();
    }

    public static PackageType.Entry getPackageEntry(String path, PackageType packageType) throws CmsException {
        PackageType.Entry entry = packageType.match(path);
        if (entry == null) {
            throw PackageType.throwUnknownEntry(path);
        }
        return entry;
    }

    public static void checkCdNumber(String cdNumber, String entryName) throws CmsException {
        if (!entryName.startsWith(cdNumber)) {
            throw new CmsException(String.format("entry %s does not match to %s", entryName, cdNumber));
        }
    }

    public static void checkPubNumber(int documentPub, int archivePub) throws CmsException {
        if (documentPub != archivePub) {
            throw new CmsException(String.format(
               "pub %d from an archive file name is not equal to pub %d from a document xml",
                    archivePub, documentPub));
        }
    }

    public static void checkCdNumberEquals(String cdNumber, String entryName) throws CmsException {
        if (!entryName.equals(cdNumber)) {
            throw new CmsException(String.format("entry %s is not equal to %s", entryName, cdNumber));
        }
    }

    private static void parseJatsZipEntry(ZipInputStream zis, ZipEntry ze, PackageType packageType,
            Set<String> cdNumbers, boolean hasTA, StringBuilder errs, Map<String, Set<String>> results) {
        try {
            String path = ze.getName();
            PackageType.Entry entry = getPackageEntry(path, packageType);
            PackageType.EntryType type = entry.getType();
            if (PackageType.EntryType.UNDEF == type) {
                return;
            }
            boolean ta = false;
            switch (type)  {
                case TA:
                    ta = true;
                case ARTICLE:
                    parseJatsArticle(entry, packageType, getNamesByPath(path, results), cdNumbers,
                            new ZipInputStream(zis), results);
                    break;
                case TOPIC:
                case TA_RETRACTED:
                case UCS:
                case IMAGE:
                    break;
                default: throw type.throwUnsupportedEntry(path);
            }
            if (ta != hasTA) {
                throw new CmsException(String.format(
                        "a translation presence marker is %b, but actually translation presence is %b", hasTA, ta));
            }
        } catch (Throwable tr) {
            errs.append("\n").append(tr.getMessage());
        }
    }

    private static void parseJatsArticle(PackageType.Entry entry, PackageType packageType, String[] recordNames,
        Set<String> cdNumbers, ZipInputStream zis, Map<String, Set<String>> results) throws Exception {
        parseArticle(entry, packageType, recordNames, cdNumbers, false, zis, results);
    }

    private static void parseArticle(PackageType.Entry rootEntry, PackageType packageType, String[] recordNames,
        Set<String> cdNumbers, boolean mixedContent, ZipInputStream zis, Map<String, Set<String>> results)
            throws Exception {

        ZipEntry ze;
        while ((ze = zis.getNextEntry()) != null) {
            if (ze.isDirectory()) {
                continue;
            }
            String entryName = ze.getName();
            PackageType.EntryType type = getPackageEntryType(entryName, packageType, rootEntry, mixedContent);
            switch (type) {
                case ARTICLE:
                case TA:
                case TA_RETRACTED:
                    String cdNumber;
                    if (recordNames != null) {
                        cdNumber = recordNames[0];
                        checkCdNumber(cdNumber, entryName);

                    } else {
                        cdNumber = getNamesByPath(entryName, results)[0];
                    }
                    if (cdNumbers != null) {
                        cdNumbers.remove(cdNumber);
                    }
                    break;
                case STATS:
                    if (recordNames != null) {
                        checkCdNumber(recordNames[0], entryName);
                    }
                    break;
                default:
                    break;
            }
        }
    }

    private static String[] getNamesByPath(String path, Map<String, Set<String>> results) throws CmsException {
        String[] pathParts = replaceBackslash2Forward(path).split("/");
        String fileName = pathParts[pathParts.length - 1];

        boolean retracted = fileName.endsWith(Extensions.RETRACTED);
        boolean hasPub = fileName.contains(Constants.PUB_PREFIX);

        String pubName = retracted
                ? fileName.replace(Extensions.RETRACTED, "")
                : fileName.replace(Extensions.XML, "").replace(Extensions.DAR, "");

        String[] namesParts =  pubName.split(Constants.NAME_SPLITTER);
        int length = hasPub ? 2 : 1;
        if (namesParts.length < length) {
            throw new CmsException(String.format("can't parse %s to <cd number>[.pub<number>][.<language>]", fileName));
        }
        String cdNumber = namesParts[0];
        pubName = hasPub ? cdNumber + "." + namesParts[1] : cdNumber;
        String language = null;

        if (namesParts.length > length) {
            language = TransAbs.get().get().getMappedCode(namesParts[namesParts.length - 1].toLowerCase());
            if (retracted) {
                language += Extensions.RETRACTED;
            }
        }
        if (results != null) {
            results.computeIfAbsent(pubName, f -> new HashSet<>()).add(
                    language == null ? Constants.ENGLISH_CODE : language);
        }
        return new String[] {cdNumber, pubName, language};
    }
}
