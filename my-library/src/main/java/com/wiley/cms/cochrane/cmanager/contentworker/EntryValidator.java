package com.wiley.cms.cochrane.cmanager.contentworker;

import com.wiley.cms.cochrane.cmanager.CmsException;
import com.wiley.cms.cochrane.cmanager.entitymanager.CDSRMetaVO;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;

/**
 * @author <a href='mailto:nchernyshe@wiley.com'>Nikita Chernyshev</a>
 * Date: 19/6/2023
 */
public class EntryValidator {
    public static final String FILENAME_MISMATCH_PREFIX =
            "Filename mismatch between XML callout(s) and file(s) from the package. ";
    public static final String ZERO_LENGTH_PREFIX = "Zero length file(s) detected: ";
    private static final String IN_THE_PACKAGE = " in the package: ";
    private static final long ZERO_LONG = 0L;
    private static final String XML = "XML";
    private static final String ZIP = "ZIP";

    private EntryValidator() {
    }

    public static void validateCollections(Set<String> entryNamesFromZip, Set<String> entryNamesFromXml,
                                           CDSRMetaVO meta) throws CmsException {
        Set<String> differences = compareEntryNames(entryNamesFromZip, entryNamesFromXml);
        if (!differences.isEmpty()) {
            logDifferentNamesError(differences, XML, meta);
        }
        differences = compareEntryNames(entryNamesFromXml, entryNamesFromZip);
        if (!differences.isEmpty()) {
            logDifferentNamesError(differences, ZIP, meta);
        }
    }

    public static void validateZipEntries(Set<ZipEntry> zipEntries, CDSRMetaVO meta) throws CmsException {
        Set<String> entryNames = zipEntries.stream()
                .filter(e -> e.getSize() == ZERO_LONG)
                .map(ZipEntry::getName)
                .collect(Collectors.toSet());

        if (!entryNames.isEmpty()) {
            logEmptyFileError(entryNames, meta);
        }
    }

    private static Set<String> compareEntryNames(Set<String> entryNames, Set<String> anotherEntryNames) {
        Set<String> differences = new HashSet<>(entryNames);
        differences.removeAll(anotherEntryNames);
        return differences;
    }

    private static void logDifferentNamesError(Set<String> differences, String format,
                                               CDSRMetaVO meta) throws CmsException {
        if (XML.equals(format)) {
            String errorMsg = getErrorMsgForXml(differences, meta);
            CmsException.throwMetadataError(meta, errorMsg);
        } else if (ZIP.equals(format)) {
            String errorMsg = getErrorMsgForZip(differences, meta);
            CmsException.throwMetadataError(meta, errorMsg);
        }
    }

    private static void logEmptyFileError(Set<String> entryNames, CDSRMetaVO meta) throws CmsException {
        String errorMsg = getErrorMsgZeroLengthFile(entryNames, meta);
        CmsException.throwMetadataError(meta, errorMsg);
    }

    private static String getErrorMsgForXml(Set<String> differences, CDSRMetaVO meta) {
        return FILENAME_MISMATCH_PREFIX + "There are callout(s): " + getJoin(differences) + " in the "
                + meta.getArticleName() + " that do not match with a file(s)"
                + IN_THE_PACKAGE + meta.getArchiveFileName();
    }

    private static String getErrorMsgForZip(Set<String> differences, CDSRMetaVO meta) {
        return FILENAME_MISMATCH_PREFIX + "There is a file(s): " + getJoin(differences) + IN_THE_PACKAGE
                + meta.getArchiveFileName() + " that are not called out in the " + meta.getArticleName();
    }

    private static String getErrorMsgZeroLengthFile(Set<String> entryNames, CDSRMetaVO meta) {
        return ZERO_LENGTH_PREFIX + getJoin(entryNames) + IN_THE_PACKAGE + meta.getArchiveFileName();
    }

    private static String getJoin(Set<String> entryNames) {
        return String.join(", ", entryNames);
    }
}