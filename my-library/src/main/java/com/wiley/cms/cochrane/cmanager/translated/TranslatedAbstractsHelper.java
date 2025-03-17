package com.wiley.cms.cochrane.cmanager.translated;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

import com.wiley.cms.cochrane.cmanager.CochraneCMSBeans;
import com.wiley.cms.cochrane.cmanager.FilePathBuilder;
import com.wiley.cms.cochrane.cmanager.data.record.IRecord;
import com.wiley.cms.cochrane.cmanager.data.record.RecordEntity;
import com.wiley.cms.cochrane.cmanager.entitymanager.DbRecordVO;
import com.wiley.cms.cochrane.cmanager.entitymanager.IRecordManager;
import com.wiley.cms.cochrane.cmanager.entitymanager.RecordHelper;
import com.wiley.cms.cochrane.cmanager.entitymanager.RevmanSource;
import com.wiley.cms.cochrane.cmanager.publish.generate.ArchiveEntry;
import com.wiley.cms.cochrane.repository.IRepository;
import com.wiley.cms.cochrane.repository.RepositoryFactory;
import com.wiley.cms.cochrane.repository.RepositoryUtils;
import com.wiley.cms.cochrane.utils.ContentLocation;
import com.wiley.tes.util.InputUtils;
import com.wiley.tes.util.Pair;
import com.wiley.cms.converter.services.RevmanMetadataHelper;
import com.wiley.tes.util.Extensions;
import com.wiley.tes.util.Logger;
import org.apache.commons.lang.ArrayUtils;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 03.04.12
 */
public class TranslatedAbstractsHelper {
    private static final Logger LOG = Logger.getLogger(TranslatedAbstractsHelper.class);

    private File[] languages;
    private final IRepository rps;

    public TranslatedAbstractsHelper() {
        rps = RepositoryFactory.getRepository();
    }

    public void makeSourceArchiveEntry(String recName, int pubNumber, String langName, String groupArchivePath,
                                 List<ArchiveEntry> entries, boolean isJats) {
        String pathFrom = isJats ? FilePathBuilder.TR.getPathToJatsTARecord(langName, recName)
                : FilePathBuilder.TR.getPathToTARecord(langName, recName);
        try {
            if (!rps.isFileExists(pathFrom)) {
                return;
            }
        } catch (IOException ie) {
            LOG.error(ie, ie);
        }
        ArchiveEntry entry;
        String pubName = RevmanMetadataHelper.buildPubName(recName, pubNumber);
        if (isJats) {
            File tempDar = RepositoryUtils.createDarPackageWithTa(pubName, langName, pathFrom);
            entry = new ArchiveEntry(groupArchivePath + tempDar.getName(), tempDar.getAbsolutePath());
        } else {
            entry = new ArchiveEntry(groupArchivePath + FilePathBuilder.buildTAFileName(langName, pubName),
                    rps.getRealFilePath(pathFrom));
        }
        entries.add(entry);
    }

    public void deleteAbstract(String langName, String recName) {
        deleteAbstract(langName, recName, RecordEntity.VERSION_LAST);
    }

    public void deleteAbstract(String langName, String recName, int version) {
        String pathTo;
        String pathWml21To;
        String pathJatsTo;
        String pathWml3gTo;

        if (DbRecordVO.isHistorical(version)) {
            pathTo = FilePathBuilder.TR.getPathToPreviousTARecord(version, langName, recName);
            pathWml21To = FilePathBuilder.TR.getPathToPreviousWML21TARecord(version, langName, recName);
            pathJatsTo = FilePathBuilder.TR.getPathToPreviousJatsTARecord(version, langName, recName);
            pathWml3gTo = FilePathBuilder.TR.getPathToPreviousWML3GTARecord(version, langName, recName);

        } else {
            pathTo = FilePathBuilder.TR.getPathToTARecord(langName, recName);
            pathWml21To = FilePathBuilder.TR.getPathToWML21TARecord(langName, recName);
            pathJatsTo =  FilePathBuilder.TR.getPathToJatsTARecord(langName, recName);
            pathWml3gTo = FilePathBuilder.TR.getPathToWML3GTARecord(langName, recName);
        }
        try {
            deleteAbstract(pathTo, pathWml21To, rps);
            deleteAbstract(pathJatsTo, pathWml3gTo, rps);

        }  catch (IOException ie) {
            LOG.error(ie);
        }
    }

    public static void makeBackup(int issueId, String recName, IRepository rp) {
        try {
            String backupDir = FilePathBuilder.TR.getPathToBackupTA(issueId);
            File fl = new File(rp.getRealFilePath(backupDir));
            if (!fl.exists() && !fl.mkdir()) {
                LOG.warn("cannot create a translation copy folder: " + fl.getAbsolutePath());
                return;
            }
            fl = new File(fl, recName);
            if (!fl.exists()) {
                if (!fl.mkdir()) {
                    LOG.warn("cannot create a ta record copy folder: " + fl.getAbsolutePath());
                    return;
                }

                List<String> langs = CochraneCMSBeans.getRecordManager().getLanguages(recName);
                for (String lang : langs) {
                    makeBackup(issueId, lang, recName, rp);
                }
            }
        } catch (Exception e) {
            LOG.error(e);
        }
    }

    private static void makeBackup(Integer issueId, String lang, String recName, IRepository rp) {

        String pathFrom = FilePathBuilder.TR.getPathToTARecord(lang, recName);
        String pathWmlFrom = FilePathBuilder.TR.getPathToWML21TARecord(lang, recName);

        String pathJatsFrom = FilePathBuilder.TR.getPathToJatsTARecord(lang, recName);
        String pathWml3gFrom = FilePathBuilder.TR.getPathToWML3GTARecord(lang, recName);

        try {
            if (rp.isFileExists(pathFrom)) {
                String pathTo = FilePathBuilder.TR.getPathToBackupTARecord(issueId, lang, recName);
                rp.putFile(pathTo, rp.getFile(pathFrom), true);
            }
            if (rp.isFileExists(pathWmlFrom)) {
                String pathTo = FilePathBuilder.TR.getPathToBackupWML21TARecord(issueId, lang, recName);
                rp.putFile(pathTo, rp.getFile(pathWmlFrom), true);
            }
            if (rp.isFileExists(pathJatsFrom)) {
                String pathTo = FilePathBuilder.TR.getPathToBackupJatsTARecord(issueId, lang, recName);
                rp.putFile(pathTo, rp.getFile(pathJatsFrom), true);
            }
            if (rp.isFileExists(pathWml3gFrom)) {
                String pathTo = FilePathBuilder.TR.getPathToBackupWML3GTARecord(issueId, lang, recName);
                rp.putFile(pathTo, rp.getFile(pathWml3gFrom), true);
            }

        } catch (IOException ie) {
            LOG.error(ie, ie);
        }
    }

    public Set<String> copyAbstractsFromBackup(String recName, String basePath,
                                               BiFunction<String, String, String> getPathTo) {
        Set<String> ret = new HashSet<>();
        File[] langs = rps.getFilesFromDir(basePath);
        if (langs == null) {
            return ret;
        }

        for (File lang : langs) {
            if (!lang.isDirectory()) {
                continue;
            }

            String langName = lang.getName();
            String pathFrom = FilePathBuilder.TR.getPathToBackupTARecord(basePath, langName, recName);
            try {
                if (rps.isFileExists(pathFrom)) {
                    String pathTo = getPathTo.apply(langName, recName);
                    rps.putFile(pathTo, rps.getFile(pathFrom), true);
                    ret.add(langName);
                }
            } catch (Exception ie) {
                LOG.error(ie, ie);
            }
        }
        try {
            rps.deleteDir(basePath);
        } catch (Exception ie) {
            LOG.error(ie, ie);
        }

        return ret;
    }

    public Set<String> copyAbstractsFromBackup(Integer issueId, String recName) {

        Set<String> ret = new HashSet<>();
        String taFolder = FilePathBuilder.TR.getPathToBackupTA(issueId, recName);
        File[] langs = rps.getFilesFromDir(taFolder);
        if (langs == null) {
            return ret;
        }

        for (File lang : langs) {

            if (!lang.isDirectory()) {
                continue;
            }

            String langName = lang.getName();
            String pathFrom =  FilePathBuilder.TR.getPathToBackupTARecord(issueId, langName, recName);
            String pathWmlFrom = FilePathBuilder.TR.getPathToBackupWML21TARecord(issueId, langName, recName);

            boolean add = false;
            boolean v3 = false;
            try {
                if (rps.isFileExists(pathFrom)) {
                    String pathTo = FilePathBuilder.TR.getPathToTARecord(langName, recName);
                    rps.putFile(pathTo, rps.getFile(pathFrom), true);
                    add = true;
                }
                if (rps.isFileExists(pathWmlFrom)) {
                    String pathTo = FilePathBuilder.TR.getPathToWML21TARecord(langName, recName);
                    rps.putFile(pathTo, rps.getFile(pathWmlFrom), true);
                    v3 = true;
                }

                if (add && v3) {
                    ret.add(langName);
                }
            } catch (IOException ie) {
                LOG.error(ie, ie);
            }
        }

        try {
            rps.deleteDir(taFolder);
        } catch (Exception ie) {
            LOG.error(ie, ie);
        }

        return ret;
    }

    /**
     * Walk the specified translated abstracts directory tree and build the list of translated abstracts of the record.
     * @param taDirPath path to translated abstracts directory
     * @param recName record name
     *
     * @return a map of a language -> a pair of paths (Revman and WML21) to the record's translated abstracts
     */
    private static Map<String, Pair<File, File>> getAbstractPaths(String taDirPath, String recName, Integer version,
                                                                  boolean jats) {
        IRepository rp = RepositoryFactory.getRepository();
        File[] langDirs = rp.getFilesFromDir(taDirPath);
        if (ArrayUtils.isEmpty(langDirs)) {
            return Collections.emptyMap();
        }

        Map<String, Pair<File, File>> ret = new HashMap<>();
        for (File langDir : langDirs) {
            if (!langDir.isDirectory()) {
                continue;
            }

            String lang = langDir.getName();
            Pair<String, String> pair = jats ? fillJatsPath(lang, recName, version, rp)
                    : fillPath(lang, recName, version, true, rp);
            if (pair != null) {
                ret.put(lang, new Pair<>(RepositoryUtils.getRealFile(pair.first),
                        RepositoryUtils.getRealFile(pair.second)));
            }
        }
        return ret;
    }

    /**
     * Walk the specified translated abstracts directory tree and build the map of translated abstracts.
     * @param taDirPath  The path to translated abstracts directory.
     * @param names      Specified set of records. If is null, all the records will be returned.
     * @param version    If it is not NULL, the previous directory is walked.
     * @return   Language -> list of paths (Revman and WML21)
     */
    public static Map<String, List<Pair<String, String>>> getAbstractPaths(String taDirPath, Collection<String> names,
                                                                          Integer version) {
        IRepository rp = RepositoryFactory.getRepository();
        File[] langDirs = rp.getFilesFromDir(taDirPath);
        if (ArrayUtils.isEmpty(langDirs)) {
            return null;
        }

        Map<String, List<Pair<String, String>>> ret = null;
        for (File langDir : langDirs) {

            if (!langDir.isDirectory()) {
                continue;
            }
            String lang = langDir.getName();

            for (String recName: names) {
                Pair<String, String> pair = fillPath(lang, recName, version, false, rp);
                if (pair == null) {
                    continue;
                }

                if (ret == null) {
                    ret = new HashMap<>();
                }
                ret.computeIfAbsent(lang, f -> new ArrayList<>()).add(pair);
            }
        }
        return ret;
    }

    private static Pair<String, String> fillJatsPath(String lang, String recName, Integer ver, IRepository rp) {

        String path = (ver == null) ? FilePathBuilder.TR.getPathToJatsTARecord(lang, recName)
                            : FilePathBuilder.TR.getPathToPreviousJatsTARecord(ver, lang, recName);
        if (!rp.isFileExistsQuiet(path)) {
            return null;
        }

        String pathWml = (ver == null) ? FilePathBuilder.TR.getPathToWML3GTARecord(lang, recName)
                : FilePathBuilder.TR.getPathToPreviousWML3GTARecord(ver, lang, recName);

        if (!rp.isFileExistsQuiet(pathWml)) {
            pathWml = null;
        }

        Pair<String, String> ret = null;
        if (pathWml != null) {
            ret = new Pair<>(path, pathWml);
        }
        return ret;
    }

    private static Pair<String, String> fillPath(String lang, String recName, Integer ver, boolean v3, IRepository rp) {

        String path = (ver == null) ? FilePathBuilder.TR.getPathToTARecord(lang, recName)
                            : FilePathBuilder.TR.getPathToPreviousTARecord(ver, lang, recName);
        if (!rp.isFileExistsQuiet(path)) {
            return null;
        }

        String pathWml = (ver == null) ? FilePathBuilder.TR.getPathToWML21TARecord(lang, recName)
                : FilePathBuilder.TR.getPathToPreviousWML21TARecord(ver, lang, recName);

        if (!rp.isFileExistsQuiet(pathWml)) {
            pathWml = null;
        }

        Pair<String, String> ret = null;
        if (!v3 || pathWml != null) {
            ret = new Pair<>(path, pathWml);
        }
        return ret;
    }

    /**
     * Returns Entire translated abstracts of the record
     * @param recName record name
     * @param jats if this is a JATS record
     * @return language -> pairs (Revman and WML21) Entire translated abstracts
     */
    public static Map<String, Pair<File, File>> getAbstractsFromEntire(String recName, boolean jats) {

        Map<String, Pair<File, File>> paths = new HashMap<>();
        getAbstractsFromEntire(recName, paths, null, jats, RepositoryFactory.getRepository());
        return paths;
    }

    private static void getAbstractsFromEntire(String recName, Map<String, Pair<File, File>> paths, Set<String> skip,
        boolean jats, IRepository rp) {
        List<String> langs = CochraneCMSBeans.getRecordManager().getLanguages(recName, jats);

        for (String lang : langs) {
            if (skip != null && skip.contains(lang)) {
                continue;
            }
            String pathWML = jats ? FilePathBuilder.TR.getPathToWML3GTARecord(lang, recName)
                    : FilePathBuilder.TR.getPathToWML21TARecord(lang, recName);
            String pathSrc = jats ? FilePathBuilder.TR.getPathToJatsTARecord(lang, recName)
                    : FilePathBuilder.TR.getPathToTARecord(lang, recName);
            paths.put(lang, new Pair<>(RepositoryUtils.getRealFile(pathSrc),
                    rp.isFileExistsQuiet(pathWML) ? RepositoryUtils.getRealFile(pathWML) : null));
        }
    }

    static Map<String, Pair<File, File>> getLegacyAbstractsFromIssue(int issueId, Integer dfId, String recName,
                                                                     IRecord record) {
        Set<String> languagesUpdated = record.getLanguages();
        Set<String> languagesRetracted = record.getRetractedLanguages();
        Set<String> skip = languagesUpdated != null ? addCollections(languagesUpdated, languagesRetracted)
                : languagesRetracted;
        if (skip == null) {
            // take legacy abstracts related to the legacy package + from entire
            return getAbstractsFromIssue(issueId, dfId, recName, record);
        }
        // take legacy abstracts from entire only because the JATS package translations were already counted
        Map<String, Pair<File, File>> paths = new HashMap<>();
        getAbstractsFromEntire(recName, paths, skip, false, RepositoryFactory.getRepository());
        return paths;
    }

    private static Set<String> addCollections(Set<String> source, Set<String> add) {
        Set<String> ret = source;
        if (add != null) {
            if (source == null) {
                ret = add;
            }  else {
                ret = new HashSet<>();
                ret.addAll(source);
                ret.addAll(add);
            }
        }
        return ret;
    }

    private static Map<String, Pair<File, File>> getAbstractsFromIssue(int issueId, Integer dfId, String recName,
                                                                       IRecord record) {
        IRepository rp = RepositoryFactory.getRepository();
        RevmanSource revmanSource = RecordHelper.findInitialSourcesForRevmanPackage(recName,
                FilePathBuilder.getPathToRevmanPackage(issueId, "" + dfId), rp);

        if (revmanSource == null || (revmanSource.taFiles.isEmpty() && !record.isUnchanged())) {
            return Collections.emptyMap();
        }

        Map<String, Pair<File, File>> paths = new HashMap<>();
        String wml21Path = FilePathBuilder.TR.getPathToIssueTA(issueId, dfId);
        Set<String> skip = revmanSource.revmanFile == null || record.isUnchanged() ? new HashSet<>() : null;

        for (Map.Entry<String, File> rmTa: revmanSource.taFiles.entrySet()) {

            File revman = rmTa.getValue();
            String lang = rmTa.getKey();

            if (skip != null) {
                // don't try taking it from entire
                skip.add(lang);
            }
            File wml21 = RepositoryUtils.getRealFile(wml21Path + FilePathBuilder.buildTAFileName(lang, recName));
            if (!wml21.exists()) {
                if (skip != null && !revman.getName().endsWith(Extensions.RETRACTED)) {
                    // translation should be taken from entire (if exists)
                    skip.remove(lang);
                }
                continue;
            }
            paths.put(lang, new Pair<>(revman, wml21));
        }

        if (skip != null) {
            getAbstractsFromEntire(recName, paths, skip, false, rp);
        }

        return paths;
    }

    public static Map<String, String> getJatsTranslationsExisted(Integer issueId, Integer dfId, IRecord record,
                                                                 int version, IRepository rp) throws Exception {
        Set<String> languagesUpdated = record.getLanguages();
        Set<String> languagesRetracted = record.getRetractedLanguages();
        Set<String> skip = languagesUpdated != null ? addCollections(languagesUpdated, languagesRetracted)
                : languagesRetracted;
        if (skip == null && !record.insertTaFromEntire() && !record.isUnchanged() && dfId != null) {
            // check if RevMan article is updating
            RevmanSource revmanSource = RecordHelper.findInitialSourcesForRevmanPackage(record.getName(),
                    FilePathBuilder.getPathToRevmanPackage(issueId, "" + dfId), rp);
            if (revmanSource != null && revmanSource.revmanFile != null) {
                return Collections.emptyMap();
            }
        }

        boolean previous = version > RecordEntity.VERSION_LAST;
        ContentLocation location = previous ? ContentLocation.PREVIOUS : ContentLocation.ENTIRE;
        List<String> langs = !previous ? CochraneCMSBeans.getRecordManager().getLanguages(record.getName(), true)
                : CochraneCMSBeans.getRecordManager().getLanguages(record.getName(), version, true);

        Map<String, String> ret = langs.isEmpty() ? Collections.emptyMap() : new HashMap<>();
        for (String language : langs) {
            if (skip != null && skip.contains(language)) {
                continue;
            }
            String taPath = location.getPathToMl3gTA(null, null, version, language, record.getName());
            if (!rp.isFileExists(taPath)) {
                LOG.error(record.getName(), String.format("can not find %s.%s in ML3G", record.getName(), language));
                continue;
            }
            ret.put(language, InputUtils.readStreamToString(rp.getFile(taPath)));
        }
        return ret;
    }

    /**
     * Returns previous versions of translated abstracts of the record
     * @param version historical version number related to directory rel0001, rel0002, etc
     * @param recName record name
     * @param recNumber record number
     * @param jats if this is JATS translations
     * @return pairs (WML21 and Revman) previous versions of translated abstracts
     */
    public static Map<String, Pair<File, File>> getAbstractsFromPrevious(Integer version, String recName, int recNumber,
                                                                         boolean jats) {
        List<DbRecordVO> list = CochraneCMSBeans.getRecordManager().getTranslationHistory(recNumber, version);
        return list.isEmpty() ? Collections.emptyMap()
                : check4Jats(list, getAbstractsFromPrevious(version, recName, jats), jats);
    }

    public static Map<String, Pair<File, File>> getAbstractsToImport(Integer issueId, Integer dfId, Integer version,
            String cdNumber, int recNumber, String pubName, ContentLocation cl) {

        IRecordManager rm = CochraneCMSBeans.getRecordManager();
        IRepository rp = RepositoryFactory.getRepository();
        List<DbRecordVO> importedList = rm.getTranslations(recNumber, dfId);
        Map<String, Pair<File, File>> ret = new HashMap<>();

        for (DbRecordVO tvo: importedList) {
            String label = tvo.getLabel();
            if (!label.equals(pubName)) {
                continue;
            }
            try {
                String language = tvo.getLanguage();
                String jatsTaFolderPath = cl.getPathToJatsTA(issueId, dfId, version, language, pubName);
                String jatsPath = RecordHelper.findPathToJatsTAOriginalRecord(jatsTaFolderPath, cdNumber, rp);
                String wml3gPath = cl.getPathToMl3gTA(issueId, dfId, version, language, pubName);
                if (rp.isFileExists(wml3gPath)) {
                    ret.put(language, new Pair<>(RepositoryUtils.getRealFile(jatsPath),
                            RepositoryUtils.getRealFile(wml3gPath)));
                }
            } catch (Exception e) {
                LOG.error(e);
            }
        }
        return ret;
    }

    private static Map<String, Pair<File, File>> check4Jats(List<DbRecordVO> list, Map<String, Pair<File, File>> map,
                                                            boolean jats) {
        if (map.isEmpty()) {
            return map;
        }
        Map<String, Pair<File, File>> ret = new HashMap<>();
        for (DbRecordVO rvo: list) {
            if (rvo.isJats() == jats && map.containsKey(rvo.getLanguage())) {
                ret.put(rvo.getLanguage(), map.get(rvo.getLanguage()));
            }
        }
        return ret;
    }

    /**
     * Returns previous versions of translated abstracts of the record
     * @param version historical number related to version directory rel0001, rel0002, etc
     * @param recName record name
     * @param jats if this is JATS translations
     * @return pairs (WML21 and Revman) previous versions of translated abstracts
     */
    public static Map<String, Pair<File, File>> getAbstractsFromPrevious(Integer version, String recName,
                                                                         boolean jats) {
        String prevDirPatch = FilePathBuilder.TR.getPathToPreviousTA(version);
        return getAbstractPaths(prevDirPatch, recName, version, jats);
    }

    public static void deleteAbstract(String pathTo, String pathWmlTo, IRepository rp) throws IOException {

        if (rp.isFileExists(pathTo)) {
            rp.deleteFile(pathTo);
        }
        if (rp.isFileExists(pathWmlTo)) {
            rp.deleteFile(pathWmlTo);
        }
    }

    public static void deleteTranslations(String recordName, Integer version, boolean jats) {
        Collection<Pair<File, File>> list = getAbstractsFromPrevious(version, recordName, jats).values();
        if (list.isEmpty()) {
            return;
        }
        for (Pair<File, File> fl: list) {
            if (fl.first != null) {
                fl.first.delete();
            }
            if (fl.second != null) {
                fl.second.delete();
            }
        }
    }

    public static Set<String> updateJatsAbstractsToEntire(int issueId, int dfId, String cdNumber,
                                                          List<DbRecordVO> changed, IRepository rp) {
        Set<String> removed = null;
        for (DbRecordVO vo: changed) {
            String language = vo.getLanguage();

            String pathJatsTo =  FilePathBuilder.TR.getPathToJatsTARecord(language, cdNumber);
            String pathWml3gTo = FilePathBuilder.TR.getPathToWML3GTARecord(language, cdNumber);
            String pathToOld =  FilePathBuilder.TR.getPathToTARecord(language, cdNumber);
            String pathWmlToOld = FilePathBuilder.TR.getPathToWML21TARecord(language, cdNumber);
            try {
                if (vo.isDeleted()) {
                    deleteAbstract(pathJatsTo, pathWml3gTo, rp);
                    deleteAbstract(pathToOld, pathWmlToOld, rp);
                    if (removed == null) {
                        removed = new HashSet<>();
                    }
                    removed.add(language);

                } else if (vo.isHistorical()){
                    deleteAbstract(pathJatsTo, pathWml3gTo, rp);
                    deleteAbstract(pathToOld, pathWmlToOld, rp);

                }  else {
                    String taDirPath = ContentLocation.ISSUE.getPathToJatsTA(issueId, dfId, null, language, cdNumber);
                    String pathFrom = RecordHelper.findPathToJatsTAOriginalRecord(taDirPath, cdNumber, rp);
                    String pathWmlFrom = FilePathBuilder.TR.getPathToIssueTARecord(issueId, dfId, language, cdNumber);

                    updateAbstracts(pathFrom, pathWmlFrom, pathJatsTo, pathWml3gTo, rp);
                    deleteAbstract(pathToOld, pathWmlToOld, rp);
                }
            } catch (Exception ie) {
                LOG.error(ie);
            }
        }
        return removed;
    }

    public static Set<String> updateAbstractsToEntire(int issueId, int dfId, IRecord rec, List<DbRecordVO> changed,
                                                      RevmanSource revmanSource, IRepository rp) {
        String cdNumber = rec.getName();
        String group = rec.getGroupSid();
        String basePathFrom = FilePathBuilder.TR.getPathToRevmanTranslations(issueId, "" + dfId, group);
        Set<String> removed = null;

        for (DbRecordVO vo: changed) {

            String language = vo.getLanguage();
            String pathTo =  FilePathBuilder.TR.getPathToTARecord(language, cdNumber);
            String pathWmlTo = FilePathBuilder.TR.getPathToWML21TARecord(language, cdNumber);
            try {
                if (vo.isDeleted()) {
                    deleteAbstract(pathTo, pathWmlTo, rp);
                    if (removed == null) {
                        removed = new HashSet<>();
                    }
                    removed.add(language);

                } else if (vo.isHistorical()) {
                    deleteAbstract(pathTo, pathWmlTo, rp);

                } else {
                    File rmTa = revmanSource.taFiles.get(language);
                    if (rmTa == null) {
                        // XDPS-1120
                        tryTakeSourceForAnotherGroup(issueId, dfId, cdNumber, language, pathTo, pathWmlTo, rp);
                        continue;
                    }

                    String pathFrom = basePathFrom + rmTa.getName();
                    String pathWmlFrom = FilePathBuilder.TR.getPathToIssueTARecord(issueId, dfId, language, cdNumber);

                    updateAbstracts(pathFrom, pathWmlFrom, pathTo, pathWmlTo, rp);
                }
            } catch (IOException ie) {
                LOG.error(ie);
            }
        }
        return removed;
    }

    private static void updateAbstracts(String pathFrom, String pathWmlFrom, String pathTo, String pathWmlTo,
                                        IRepository rp) throws IOException {
        if (rp.isFileExists(pathFrom)) {
            rp.putFile(pathTo, rp.getFile(pathFrom), true);
        }
        if (rp.isFileExists(pathWmlFrom)) {
            rp.putFile(pathWmlTo, rp.getFile(pathWmlFrom), true);
        }
    }

    private static void tryTakeSourceForAnotherGroup(Integer issueId, Integer dfId, String recName, String langName,
        String pathTo, String pathWmlTo, IRepository rp) throws IOException {

        String baseRevman = FilePathBuilder.getPathToRevmanPackage(issueId, dfId.toString());
        File[] groups = rp.getFilesFromDir(baseRevman);
        if (groups != null) {

            for (File group : groups) {
                if (!group.isDirectory()) {
                    continue;
                }

                RevmanSource revmanSource = RecordHelper.findInitialSourcesForRevmanPackage(recName, group);
                if (revmanSource != null && revmanSource.taFiles.containsKey(langName)) {

                    String pathFrom = FilePathBuilder.TR.getPathToRevmanTranslations(
                            issueId, "" + dfId, group.getName()) + revmanSource.taFiles.get(langName).getName();
                    String pathWmlFrom = FilePathBuilder.TR.getPathToIssueTARecord(issueId, dfId, langName, recName);
                    updateAbstracts(pathFrom, pathWmlFrom, pathTo, pathWmlTo, rp);
                    return;
                }
            }
        }
        LOG.warn(String.format("cannot find %s.%s from %s to copy to entire", recName, langName, baseRevman));
    }

    public static void copyAbstractsToPrevious(String recName, Integer version, IRepository rp) throws IOException {

        List<String> langs = CochraneCMSBeans.getRecordManager().getLanguages(recName);
        for (String langName: langs) {

            String pathFrom = FilePathBuilder.TR.getPathToTARecord(langName, recName);
            String pathWml21From = FilePathBuilder.TR.getPathToWML21TARecord(langName, recName);
            String pathJatsFrom = FilePathBuilder.TR.getPathToJatsTARecord(langName, recName);
            String pathWml3gFrom = FilePathBuilder.TR.getPathToWML3GTARecord(langName, recName);

            if (rp.isFileExists(pathFrom)) {
                String pathTo = FilePathBuilder.TR.getPathToPreviousTARecord(version, langName, recName);
                rp.putFile(pathTo, rp.getFile(pathFrom), true);
            }
            if (rp.isFileExists(pathWml21From)) {
                String pathTo = FilePathBuilder.TR.getPathToPreviousWML21TARecord(version, langName, recName);
                rp.putFile(pathTo, rp.getFile(pathWml21From), true);
            }
            if (rp.isFileExists(pathJatsFrom)) {
                String pathTo = FilePathBuilder.TR.getPathToPreviousJatsTARecord(version, langName, recName);
                rp.putFile(pathTo, rp.getFile(pathJatsFrom), true);
            }
            if (rp.isFileExists(pathWml3gFrom)) {
                String pathTo = FilePathBuilder.TR.getPathToPreviousWML3GTARecord(version, langName, recName);
                rp.putFile(pathTo, rp.getFile(pathWml3gFrom), true);
            }
        }
    }

    public File[] getLanguages() {

        if (languages == null) {
            languages = rps.getFilesFromDir(FilePathBuilder.TR.getPathToTA());
        }

        return languages;
    }
}
