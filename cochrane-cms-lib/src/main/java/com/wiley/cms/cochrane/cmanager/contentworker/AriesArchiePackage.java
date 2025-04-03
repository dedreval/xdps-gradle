package com.wiley.cms.cochrane.cmanager.contentworker;

import com.wiley.cms.cochrane.cmanager.CmsException;
import com.wiley.cms.cochrane.cmanager.DeliveryPackageException;
import com.wiley.cms.cochrane.cmanager.entitymanager.RecordHelper;
import com.wiley.cms.cochrane.cmanager.res.PackageType;
import com.wiley.cms.cochrane.cmanager.translated.TranslatedAbstractVO;
import com.wiley.cms.cochrane.repository.IRepository;
import com.wiley.cms.cochrane.test.PackageChecker;
import com.wiley.cms.cochrane.utils.Constants;
import com.wiley.cms.cochrane.utils.ErrorInfo;
import com.wiley.cms.converter.services.RevmanMetadataHelper;
import com.wiley.tes.util.FileUtils;
import com.wiley.tes.util.Logger;
import org.apache.commons.io.IOUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.validation.constraints.NotNull;

/**
 * @author <a href='mailto:atolkachev@wiley.ru'>Andrey Tolkachev</a>
 * Date: 21.01.21
 */
public class AriesArchiePackage extends JatsPackage {
    private static final Logger LOG = Logger.getLogger(AriesArchiePackage.class);

    private static final Set<PackageType.EntryType> SUPPORTED_EMBEDDED_TYPES = new HashSet<>(Arrays.asList(
            PackageType.EntryType.ARTICLE, PackageType.EntryType.IMAGE, PackageType.EntryType.TA,
                PackageType.EntryType.STATS));

    AriesArchiePackage(URI packageUri) throws DeliveryPackageException {
        super(packageUri);
    }

    public AriesArchiePackage(String packageFileName) {
        super(packageFileName);
    }

    public AriesArchiePackage(URI packageUri, String dbName, int deliveryId, int fullIssueNumber) {
        super(packageUri, dbName, deliveryId, fullIssueNumber);
    }

    @Override
    public Set<PackageType.EntryType> getEmbeddedEntryTypesSupported() {
        return SUPPORTED_EMBEDDED_TYPES;
    }

    @Override
    public void parseArticleZip(PackageType.Entry entry, Integer issueId, String fileName,
                               ZipInputStream externalZis, boolean ta) throws Exception {
        ZipInputStream zis = new ZipInputStream(externalZis);

        String pubName = FileUtils.cutExtension(fileName);
        String[] nameParts = pubName.split(Constants.NAME_SPLITTER);
        String cdNumber = nameParts[0];
        PackageArticleResults<String> results = new PackageArticleResults<>();
        TranslatedAbstractVO tvo = null;
        String basePath;
        int pubNumber;
        try {
            if (ta) {
                tvo = TranslatedAbstractsPackage.parseFileName(nameParts);
                basePath = getTABasePath(issueId, tvo, getJatsFolder(pubName, cdNumber, tvo.getOriginalLanguage()));
                pubNumber = tvo.getPubNumber();
                tvo.setDoi(RevmanMetadataHelper.buildDoi(cdNumber, pubNumber));
            } else {
                basePath = getArticleBasePath(pubName, cdNumber);
                pubNumber = RevmanMetadataHelper.parsePubNumber(pubName);
            }
            results.setBasePath(basePath).setCdNumber(cdNumber).setPubNumber(pubNumber);
            ZipEntry ze = null;
            ZipEntry zeLast = null;

            while ((ze = zis.getNextEntry()) != null) {
                zeLast = ze;
                String zeName = ze.getName();
                if (ze.isDirectory()) {
                    LOG.warn("%s is a directory that are not expected for Aries package structure", zeName);
                    continue;
                }

                String path = basePath + zeName;
                PackageType.Entry zipEntry = entry.match(zeName);
                if (zipEntry == null) {
                    LOG.warn("%s - an unknown entry: %s", fileName, zeName);
                    putFileToRepository(rps, zis, path);
                    continue;
                }
                PackageType.EntryType type = zipEntry.getType();

                if (!isEntryTypeSupported(type, true)) {
                    throw type.throwUnsupportedEntry(zeName);
                }
                results.setCurrentPath(path);
                type.parseFinal(entry, zeName, zis, this, results);
            }

            checkFirstZipEntry(fileName, zeLast);

            //String[] articleResult = getArticleResult(results);
            String recordPath = results.getArticleResult(); // articleResult != null ? articleResult[1] : null;
            if (recordPath == null) {
                recordPath = results.getTranslationResult();
            }
            if (recordPath == null) {
                throw new CmsException("can't find a document file");
            }
            int pub = RevmanMetadataHelper.parsePubNumber(results.getPublisherId());
            PackageChecker.checkPubNumber(pub, pubNumber);    //todo
            if (ta) {
                unpackTranslation(tvo, basePath, recordPath, fileName);
            } else {
                addReview(basePath, cdNumber, pubNumber, results.getStatsResult(), recordPath, 0, null);
            }

        } catch (Throwable tr) {
            RecordHelper.handleErrorMessage(new ErrorInfo<>(new ArchieEntry(cdNumber), tr.getMessage()), errors());
        }
    }

    @Override
    public void parseImage(PackageType.Entry entry, @NotNull String cdNumber, String zeName, InputStream zis)
            throws Exception {
        PackageChecker.checkCdNumber(cdNumber, zeName);
        super.parseImage(entry, cdNumber, zeName, zis);
    }

    @Override
    public void parseStatsFile(PackageType.Entry entry, String cdNumber, String zeName, InputStream zis)
            throws Exception {
        PackageChecker.checkCdNumber(cdNumber, zeName);
        parseStatsFile(entry, zeName, zis);
    }

    @Override
    public Object parseEmbeddedArticle(PackageType.Entry entry, String zeName, InputStream zis,
                                       PackageArticleResults results) throws Exception {

        PackageChecker.checkCdNumber(results.getCdNumber(), zeName);
        String path = results.getCurrentPath();
        String recordPath = path.replace(Constants.JATS_FINAL_SUFFIX, "");
        String pubName = parseArticle(zeName, recordPath, zis, rps);
        results.setPublisherId(pubName);
        return recordPath;
    }


    private String parseArticle(String zeName, String recordPath, InputStream zis, IRepository rp) throws Exception {
        ByteArrayInputStream bais = null;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            IOUtils.copy(zis, baos);
            bais = new ByteArrayInputStream(baos.toByteArray());

            String pubName = helper().extractPublisherId(bais);
            String cdNumber = RevmanMetadataHelper.parseCdNumber(pubName);
            PackageChecker.checkCdNumber(cdNumber, zeName);

            bais.reset();
            putFileToRepository(rp, bais, recordPath);
            return pubName;

        } catch (Exception e) {
            RecordHelper.handleErrorMessage(new ErrorInfo<>(zeName, e.getMessage()), errors());
            throw e;
        } finally {
            IOUtils.closeQuietly(bais);
        }
    }
}
