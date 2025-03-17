package com.wiley.cms.cochrane.process.handler;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.wiley.cms.cochrane.activitylog.IFlowLogger;
import com.wiley.cms.cochrane.activitylog.ILogEvent;
import com.wiley.cms.cochrane.cmanager.CmsUtils;
import com.wiley.cms.cochrane.cmanager.CochraneCMSBeans;
import com.wiley.cms.cochrane.cmanager.DeliveryPackage;
import com.wiley.cms.cochrane.cmanager.DeliveryPackageException;
import com.wiley.cms.cochrane.cmanager.DeliveryPackageInfo;
import com.wiley.cms.cochrane.cmanager.FilePathBuilder;
import com.wiley.cms.cochrane.cmanager.IDeliveryFileStatus;
import com.wiley.cms.cochrane.cmanager.MessageSender;
import com.wiley.cms.cochrane.cmanager.contentworker.ArchieEntry;
import com.wiley.cms.cochrane.cmanager.contentworker.AriesArchiePackage;
import com.wiley.cms.cochrane.cmanager.contentworker.AriesSFTPPackage;
import com.wiley.cms.cochrane.cmanager.contentworker.ImportJatsPackage;
import com.wiley.cms.cochrane.cmanager.contentworker.JatsPackage;
import com.wiley.cms.cochrane.cmanager.data.DeliveryFileEntity;
import com.wiley.cms.cochrane.cmanager.data.IResultsStorage;
import com.wiley.cms.cochrane.cmanager.data.record.IRecord;
import com.wiley.cms.cochrane.cmanager.data.record.RecordEntity;
import com.wiley.cms.cochrane.cmanager.data.record.RecordStorageFactory;
import com.wiley.cms.cochrane.cmanager.entitymanager.DbRecordVO;
import com.wiley.cms.cochrane.cmanager.entitymanager.ICDSRMeta;
import com.wiley.cms.cochrane.cmanager.entitymanager.IRecordManager;
import com.wiley.cms.cochrane.cmanager.entitymanager.RecordHelper;
import com.wiley.cms.cochrane.cmanager.entitywrapper.ResultStorageFactory;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.cmanager.res.PackageType;
import com.wiley.cms.cochrane.repository.IRepository;
import com.wiley.cms.cochrane.repository.RepositoryFactory;
import com.wiley.tes.util.Extensions;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 7/7/2019
 *
 */
public class CDSRPackageUnpackHandler extends PackageUnpackHandler {
    private static final long serialVersionUID = 1L;

    public CDSRPackageUnpackHandler() {
    }

    public CDSRPackageUnpackHandler(DbHandler handler, String packageUri) {
        super(handler, packageUri);
    }


    @Override
    protected Map<String, IRecord> unpackCommon(BaseType bt, URI uri, Integer issueId, Integer dfId, String owner,
                                                IFlowLogger logger) throws Exception {
        DeliveryPackage dp = new DeliveryPackage(uri, bt.getId(), dfId, getContentHandler().getIssue());
        IRepository rp = RepositoryFactory.getRepository();
        packageName = dp.getPackageFileName();
        DeliveryPackageInfo dfInfo = dp.extractData(issueId,
            getContentHandler().getDbId(), DeliveryFileEntity.TYPE_WML3G, logger.getRecordCache());

        logger.getActivityLog().logDeliveryFile(ILogEvent.PACKAGE_UNZIPPED, dfId, packageName, owner);

        List<String> names = dfInfo.getRecordNames();
        StringBuilder errs = null;
        IRecordManager rm = CochraneCMSBeans.getRecordManager();
        IResultsStorage rs = ResultStorageFactory.getFactory().getInstance();
        Collection<Integer> ids = new ArrayList<>();
        boolean cdsr = bt.isCDSR();
        for (String cdNumber: names) {
            try {
                ICDSRMeta meta = rs.findLatestMetadata(cdNumber, false);
                ArchieEntry ae = new ArchieEntry(cdNumber, 0);
                boolean statsData = cdsr && RecordHelper.setExistingCDSRSrcPath(bt.getId(), cdNumber, ae,
                        RecordEntity.VERSION_LAST, meta.isJats(), rp);
                ae.setPath(FilePathBuilder.getPathToIssuePackage(
                        issueId, bt.getId(), dfId) + cdNumber + Extensions.XML);
                ids.add(rm.createRecord(bt, ae, dfId, null, statsData, false));

            } catch (Exception e) {
                errs = errs == null ? new StringBuilder() : errs;
                addError(cdNumber, e.getMessage(), errs);
                LOG.error(e.getMessage());
            }
        }

        if (errs != null) {
            MessageSender.sendFailedLoadPackageMessage(packageName, errs.toString(), bt.getShortName(), null);
        }

        Map<String, IRecord> results = Collections.emptyMap();
        if (!ids.isEmpty()) {
            results = new HashMap<>();
            List<IRecord> records = RecordStorageFactory.getFactory().getInstance().getTinyRecords(ids);
            for (IRecord record : records) {
                String cdNumber = record.getName();
                record.setLanguages(getLanguages(cdNumber, rm));
                record.setRecordPath(
                        FilePathBuilder.getPathToIssuePackage(issueId, bt.getId(), dfId) + cdNumber + Extensions.XML);
                results.put(cdNumber, record);
            }
        }
        getContentHandler().updateDeliveryFileOnRecordCreate(packageName, DeliveryFileEntity.TYPE_WML3G, owner,
                results.size(), ResultStorageFactory.getFactory().getInstance(), logger.getActivityLog());
        return results;
    }

    private static Set<String> getLanguages(String cdNumber, IRecordManager rm) {
        List<DbRecordVO> dbRecordVOS = rm.getLastTranslations(RecordHelper.buildRecordNumberCdsr(cdNumber));
        if (dbRecordVOS.isEmpty()) {
            return  null;
        }
        Set<String> languages = new HashSet<>();
        dbRecordVOS.forEach(ta -> languages.add(ta.getLanguage()));
        return languages;
    }

    @Override
    protected Map<String, IRecord> unpackJats(BaseType bt, URI uri, Integer issueId, int dfId,
                                              String owner, IFlowLogger logger) throws Exception {
        int issue = getContentHandler().getIssue();

        JatsPackage jp = CmsUtils.isImportIssue(issueId) ? new ImportJatsPackage(uri, bt.getId(), dfId, issue)
                : new JatsPackage(uri, bt.getId(), dfId, issue);
        packageName = jp.getPackageFileName();
        Map<String, IRecord> results = jp.extractData(issueId, getContentHandler().getDbId(),
                PackageType.findCDSRJats().get(), logger);
        if (results.isEmpty()) {
            setUnzipped(dfId, owner, logger.getActivityLog());
        } else {
            getContentHandler().updateDeliveryFileOnRecordCreate(packageName, getTypeUpdatedForArchie(jp), owner,
                    results.size(), ResultStorageFactory.getFactory().getInstance(), logger.getActivityLog());
        }
        return results;
    }

    @Override
    protected Map<String, IRecord> unpackAries(BaseType bt, URI uri, Integer issueId, int dfId, String owner,
                                               IFlowLogger logger) throws Exception {
        int issue = getContentHandler().getIssue();
        boolean fromArchie = DeliveryPackage.isJats(uri.getPath());

        JatsPackage ap;
        PackageType packageType;
        if (fromArchie) {
            ap = new AriesArchiePackage(uri, bt.getId(), dfId, issue);
            packageType = PackageType.findCDSRJatsArchieAries().get();
        } else {
            ap = new AriesSFTPPackage(uri, bt.getId(), dfId, issue);
            packageType = PackageType.findCDSRJatsAries().get();
        }
        packageName = ap.getPackageFileName();
        Map<String, IRecord> results;
        try {
            results = ap.extractData(issueId, getContentHandler().getDbId(), packageType, logger);

        } catch (DeliveryPackageException de) {
            if (!fromArchie && (de.getStatus() == IDeliveryFileStatus.STATUS_PACKAGE_LOADED
                    || de.getStatus() == IDeliveryFileStatus.STATUS_PACKAGE_DELETED)) {
                removeAriesPackage();
                if (ap instanceof AriesSFTPPackage){
                    AriesSFTPPackage ariesSFTPPackage = (AriesSFTPPackage) ap;
                    de.setManuscriptNumber(ariesSFTPPackage.getAriesImportFile());
                }
            }
            throw de;
        }

        if (results.isEmpty()) {
            setUnzipped(dfId, owner, logger.getActivityLog());
        } else {
            if (!fromArchie) {
                removeAriesPackage();
            }
            getContentHandler().updateDeliveryFileOnRecordCreate(packageName,
                fromArchie ? getTypeUpdatedForArchie(ap) : getTypeUpdatedForAries(ap),
                    owner, results.size(), ResultStorageFactory.getFactory().getInstance(), logger.getActivityLog());
        }
        return results;
    }

    private static int getTypeUpdatedForAries(JatsPackage jp) {
        int typeUpdated = DeliveryFileEntity.TYPE_ARIES;
        if (jp.getReviewCount() > 0) {
            typeUpdated = DeliveryFileEntity.setJats(typeUpdated);
        }
        if (jp.getTranslationCount() > 0) {
            typeUpdated = DeliveryFileEntity.setJatsTranslation(typeUpdated);
        }
        return typeUpdated;
    }

    private static int getTypeUpdatedForArchie(JatsPackage jp) {
        int typeUpdated = jp.getReviewCount() > 0 ? DeliveryFileEntity.TYPE_JATS : DeliveryFileEntity.TYPE_DEFAULT;
        if (jp.getTranslationCount() > 0) {
            typeUpdated = DeliveryFileEntity.setJatsTranslation(typeUpdated);
        }
        return typeUpdated;
    }
}
