package com.wiley.cms.cochrane.cmanager.central;

import com.wiley.cms.cochrane.cmanager.CmsException;
import com.wiley.cms.cochrane.cmanager.CmsUtils;
import com.wiley.cms.cochrane.cmanager.CochraneCMSProperties;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.DeliveryPackage;
import com.wiley.cms.cochrane.cmanager.FilePathBuilder;
import com.wiley.cms.cochrane.cmanager.data.DeliveryFileEntity;
import com.wiley.cms.cochrane.cmanager.data.db.DbVO;
import com.wiley.cms.cochrane.cmanager.data.db.IDbStorage;
import com.wiley.cms.cochrane.cmanager.data.record.IRecordStorage;
import com.wiley.cms.cochrane.cmanager.data.record.RecordEntity;
import com.wiley.cms.cochrane.cmanager.data.record.RecordMetadataEntity;
import com.wiley.cms.cochrane.cmanager.entitywrapper.IssueWrapper;
import com.wiley.cms.cochrane.cmanager.export.process.ExportParameters;
import com.wiley.cms.cochrane.cmanager.export.process.ExportProcessor;
import com.wiley.cms.cochrane.cmanager.export.process.Exporter;
import com.wiley.cms.cochrane.cmanager.packagegenerator.IPackageGenerator;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.repository.RepositoryFactory;
import com.wiley.cms.cochrane.repository.RepositoryUtils;
import com.wiley.cms.cochrane.services.IContentManager;
import com.wiley.cms.cochrane.services.integration.MetaxisEndPoint;
import com.wiley.cms.cochrane.test.PackageChecker;
import com.wiley.cms.cochrane.utils.Constants;
import com.wiley.tes.util.Extensions;
import com.wiley.tes.util.InputUtils;
import com.wiley.tes.util.Logger;
import com.wiley.tes.util.Pair;
import com.wiley.tes.util.ftp.FtpInteraction;

import javax.ejb.EJB;
import javax.ejb.Local;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * @author <a href='mailto:ikirov@wiley.com'>Igor Kirov</a>
 * @version 23.08.11
 */
@Stateless
@Local(IPackageDownloader.class)
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class PackageDownloader implements IPackageDownloader {
    private static final Logger LOG = Logger.getLogger(PackageDownloader.class);
    private static final String FILENAME_FORMAT = "^clcentral_%04d_%02d.*\\.zip$";

    @EJB(beanName = "ContentManager")
    private IContentManager manager;

    @EJB(beanName = "DbStorage")
    private IDbStorage dbs;

    @EJB(beanName = "RecordStorage")
    private IRecordStorage rs;

    @EJB(beanName = "AddFromEntirePackageGenerator")
    private IPackageGenerator updatePropertiesPackageGenerator;

    public String uploadCDSRWhenReady(int dbId, Set<Integer> recordIds) throws Exception {
        DbVO dbVo = dbs.getDbVO(dbId);
        List<RecordEntity> list =  rs.getRecordEntitiesByIds(recordIds);
        Set<Integer> revmanIds = new HashSet<>();
        Set<Integer> scheduledIds = new HashSet<>();

        List<String>  datesUpdated = new ArrayList<>();
        List<String> datesPdfUpdated = new ArrayList<>();

        for (RecordEntity re: list) {
            RecordMetadataEntity rme = re.getMetadata();
            String dfName = re.getDeliveryFile().getName();
            if (rme == null || (!rme.isJats() && DeliveryFileEntity.isRevman(re.getDeliveryFile().getType()))) {
                recordIds.remove(re.getId());
                revmanIds.add(re.getId());

            } else if (re.isPublishingCancelled()) {
                recordIds.remove(re.getId());

            } else if (rme.isScheduled()) {
                recordIds.remove(re.getId());
                scheduledIds.add(re.getId());

            } else if (DeliveryPackage.isPropertyUpdateMl3g(dfName)) {
                recordIds.remove(re.getId());
                datesUpdated.add(re.getName());

            } else if (DeliveryPackage.isPropertyUpdatePdf(dfName))  {
                recordIds.remove(re.getId());
                datesPdfUpdated.add(re.getName());                 
            }
        }
        StringBuilder err = null;
        if (!revmanIds.isEmpty()) {
            Pair<String, String> pair = exportCDSRWhenReady(dbVo.getIssueId(), BaseType.getCDSR().get().getId(),
                    dbVo.getId(), revmanIds, true, false);
            err = buildResponse(pair, err);
        }
        if (!recordIds.isEmpty()) {
            Pair<String, String> pair = exportCDSRWhenReady(dbVo.getIssueId(), BaseType.getCDSR().get().getId(),
                    dbVo.getId(), recordIds, true, true);
            err = buildResponse(pair, err);
        }
        if (!scheduledIds.isEmpty()) {
            Pair<String, String> pair = exportCDSRWhenReady(Constants.SPD_ISSUE_ID, BaseType.getCDSR().get().getId(),
                    Constants.SPD_DB_CDSR_ID, scheduledIds, true, true);
            err = buildResponse(pair, err);
        }
        if (!datesUpdated.isEmpty()) {
            updatePropertiesPackageGenerator.generateAndUpload(datesUpdated, dbId,
                    PackageChecker.PROPERTY_UPDATE_WML3G_SUFFIX);
        }
        if (!datesPdfUpdated.isEmpty()) {
            updatePropertiesPackageGenerator.generateAndUpload(datesPdfUpdated, dbId,
                    PackageChecker.PROPERTY_UPDATE_PDF_SUFFIX);
        }
        return err == null ? null : err.toString();
    }

    private StringBuilder buildResponse(Pair<String, String> response, StringBuilder sb) {
        StringBuilder ret = sb;
        String err = (response.second == null || response.second.isEmpty()) ? null
            : (response.first != null
                ? RepositoryUtils.getLastNameByPath(response.first) : "") + "\n\n" + response.second;
        if (err != null) {
            if (ret == null) {
                ret = new StringBuilder();
            }
            ret.append(err).append("\n");
        }
        return ret;
    }

    public String exportCDSRWhenReady(int issueId, Set<Integer> cdNumbers, boolean upload, boolean jats)
            throws Exception {

        String dbName = BaseType.getCDSR().get().getId();
        DbVO dbVo = dbs.getDbVOByNameAndIssue(dbName, issueId);
        Pair<String, String> pair = exportCDSRWhenReady(issueId, dbName, dbVo.getId(), cdNumbers, upload, jats);

        String ret = upload && pair.first != null ? RepositoryUtils.getLastNameByPath(pair.first) : pair.first;
        return ret + "\n" + pair.second;
    }

    private Pair<String, String> exportCDSRWhenReady(int issueId, String dbName, int dbId, Set<Integer> cdNumbers,
                                                     boolean upload, boolean jats) throws CmsException {
        ExportParameters parameters =  new ExportParameters(dbId, CochraneCMSPropertyNames.getSystemUser());
        parameters.setWRForIPackage(true);
        if (jats) {
            parameters.setJatsForIPackage(true);
        }
        parameters.setDbName(dbName);
        String filePath = FilePathBuilder.getPathToIssueDb(issueId, dbName)
                + System.currentTimeMillis() + (jats ? PackageChecker.JATS_POSTFIX : "") + Extensions.ZIP;

        Exporter exporter = new Exporter(parameters, cdNumbers, filePath);
        exporter.run();

        int errCount = exporter.getErrorCount();

        String packName = exporter.getLastArchiveCreated();
        if (packName != null && upload && errCount < cdNumbers.size()) {
            manager.newPackageReceived(new File(RepositoryFactory.getRepository().getRealFilePath(packName)).toURI());
        }
        return new Pair<>(packName, ExportProcessor.getAllErrors(exporter.getErrors()));
    }

    public String downloadFromLocal(String path) {
        File fl = RepositoryUtils.getRealFile(RepositoryFactory.getRepository().getRealFilePath(path));
        try {
            if (!fl.exists()) {
                throw new Exception(fl.getAbsolutePath() + " doesn't exist ");
            }
            deliverPackage(fl.toURI());

        } catch (Exception e) {
            return e.getMessage();
        }
        return "done ";
    }

    public String downloadCentralToLocal(int issueNumber, String path) {
        try {
            File fl = RepositoryUtils.getRealFile(RepositoryFactory.getRepository().getRealFilePath(path));
            if (!fl.exists()) {
                throw new CmsException(String.format("%s doesn't exist", fl.getAbsolutePath()));
            }
            return downloadCentral(CmsUtils.getYearByIssueNumber(issueNumber),
                    CmsUtils.getIssueByIssueNumber(issueNumber), null, path, false);
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    public void downloadCentral(IssueWrapper issue) throws Exception {
        if (downloadCentral(issue.getYear(), issue.getNumber(), issue.getId(), FilePathBuilder.getPathToIssueDb(
                issue.getId(), CochraneCMSPropertyNames.getCentralDbName()), false) == null) {
            throw new CmsException("no proper packages found to upload");
        }
    }

    public String downloadCentralSFTP(int year, int month, Integer issueId) throws Exception {
        return downloadCentral(year, month, issueId, FilePathBuilder.getPathToIssueDb(
                issueId, CochraneCMSPropertyNames.getCentralDbName()), true);
    }

    private String downloadCentral(int year, int month, Integer issueId, String path, boolean aut) throws Exception {
        FtpInteraction ftpInteraction = null;
        URI packageUri = null;
        try {
            ftpInteraction = MetaxisEndPoint.getCentralInteraction(aut);
            List<String> fileNames = ftpInteraction.listFiles();
            Pattern p = Pattern.compile(String.format(FILENAME_FORMAT, year, month));
            for (String fileName : fileNames) {
                LOG.debug(fileName);
                if (packageUri == null && p.matcher(fileName).matches()) {
                    packageUri = downloadCentralPackage(path, ftpInteraction, fileName, issueId, year, month, aut);
                }
            }
        } finally {
            InputUtils.closeConnection(ftpInteraction);
        }
        return packageUri == null ? null : packageUri.toString();
    }

    private URI downloadCentralPackage(String path, FtpInteraction ftpInteraction, String fileName,
                                       Integer issueToUploadId, int year, int month, boolean aut) throws Exception {

        File downloadTo = RepositoryUtils.getRealFile(path 
                + (aut ? fileName.replace(Extensions.ZIP, PackageChecker.AUT_SUFFIX + Extensions.ZIP) : fileName));

        if (downloadTo != null && !downloadTo.exists()) {
            LOG.debug(String.format("> downloadFile [%s] to [%s]", fileName, downloadTo.getAbsolutePath()));
            downloadTo.getParentFile().mkdirs();

            ftpInteraction.downloadFile(fileName, downloadTo.getAbsolutePath());

            LOG.debug("< downloadFile");

            URI ret = downloadTo.toURI();
            if (issueToUploadId != null) {
                deliverPackage(ret);
                dbs.setInitialPackageDeliveredByIssueIdAndTitle(issueToUploadId,
                        CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.CLCENTRAL));
            }
            return ret;
        }
        LOG.warn(String.format("%s cannot be saved in %s", fileName, path));
        return null;
    }

    private void deliverPackage(URI uri) {
        LOG.debug(String.format("> deliverPackage [%s]", uri.toString()));
        manager.newPackageReceived(uri);
        LOG.debug("< deliverPackage");
    }
}