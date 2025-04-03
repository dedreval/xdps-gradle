package com.wiley.cms.cochrane.cmanager.contentworker;

import java.io.InputStream;
import java.util.Collections;
import java.util.Set;
import java.util.zip.ZipInputStream;

import com.wiley.cms.cochrane.cmanager.res.PackageType;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 05-Aug-21
 */
public interface IPackageParser {

    String parseManifest(PackageType.Entry entry, String path, InputStream zis);

    void parseArticleZip(PackageType.Entry entry, Integer issueId, String zeName, ZipInputStream zis, boolean ta)
            throws Exception;

    default Object parseEmbeddedArticle(PackageType.Entry entry, String zeName, InputStream zis,
                                        PackageArticleResults results) throws Exception {
        return null;
    }

    default void parseStatsFile(PackageType.Entry entry, String cdNumber, String zeName, InputStream zis)
            throws Exception {
    }

    default void parseImage(PackageType.Entry entry, String cdNumber, String zeName, InputStream zis)
            throws Exception {
    }

    default Object parseImportFile(PackageType.Entry entry, String zeName, InputStream zis) {
        return null;
    }

    default Object parseMetadata(PackageType.Entry entry, String zeName, InputStream zis) {
        return null;
    }

    default void parseTopic(PackageType.Entry entry, Integer issueId, String group, String zeName, InputStream zis)
            throws Exception {
    }

    default void parseTranslationRetracted(Integer issueId, String fileName, InputStream zis) {
    }

    default void parseUSC(PackageType.Entry entry, String zeName, InputStream zis) {
    }

    default Object parsePDF(PackageType.Entry entry, String zeName, InputStream zis) {
        return null;
    }

    default Object parseTopic(PackageType.Entry entry, String zeName, InputStream zis) {
        return null;
    }

    default void parseUndefinedEntry(PackageType.Entry entry, String entryName, InputStream zis) throws Exception {
    }

    default Set<PackageType.EntryType> getEntryTypesSupported() {
        return Collections.emptySet();
    }

    default Set<PackageType.EntryType> getEmbeddedEntryTypesSupported() {
        return Collections.emptySet();
    }
}
