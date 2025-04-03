package com.wiley.cms.cochrane.cmanager.packagegenerator;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.ejb.DependsOn;
import javax.ejb.EJB;
import javax.ejb.Local;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;

import com.wiley.cms.cochrane.cmanager.contentworker.PackageUploader;
import com.wiley.cms.cochrane.cmanager.data.issue.IssueVO;
import com.wiley.cms.cochrane.utils.Constants;
import com.wiley.cms.process.ProcessHelper;
import com.wiley.cms.process.task.ITaskManager;
import com.wiley.cms.cochrane.test.PackageChecker;
import com.wiley.tes.util.DbUtils;
import com.wiley.tes.util.Extensions;
import com.wiley.tes.util.ICollectionCommitter;
import com.wiley.tes.util.Logger;
import com.wiley.tes.util.res.Property;
import com.wiley.tes.util.res.Res;

/**
 * @author <a href='mailto:ikirov@wiley.com'>Igor Kirov</a>
 * @version 13.11.2009
 */

@Stateless
@DependsOn("CochraneCMSProperties")
@Local(IPackageGenerator.class)
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class MonthlyMeshTermsPackageGenerator extends AbstractPackageGenerator {
    private static final Logger LOG = Logger.getLogger(MonthlyMeshTermsPackageGenerator.class);

    private static final Res<Property> MAX_PACK_SIZE = Property.get(
            "cochrane.meshterm-updater.monthly-batch", "" + Constants.UNDEF);
    private static final Res<Property> DELAY_BETWEEN_UPLOAD = Property.get(
            "cochrane.meshterm-updater.monthly-batch-interval", "0");

    @EJB(lookup = ProcessHelper.LOOKUP_TASK_MANAGER)
    private ITaskManager tm;

    @Override
    public List<String> generateAndUpload(List<String> recordNames, int issueId, String dbName, String suffix) {

        int maxPackageSize = getMaxRecordsInPackage();

        List<String> ret = new ArrayList<>();

        DbUtils.commitListByNames(recordNames, new ICollectionCommitter<String>() {
            int delayBetweenUpload = 0;

            @Override
            public void commit(Collection<String> list) {
                String name = uploadPackage(list, issueId, dbName, suffix, delayBetweenUpload);
                if (name != null) {
                    ret.add(name);
                    delayBetweenUpload += DELAY_BETWEEN_UPLOAD.get().asInteger();
                }
            }

            @Override
            public int commitSize() {
                return maxPackageSize;
            }
        });
        return ret;
    }

    private String uploadPackage(Collection<String> recordNames, int issueId, String dbName, String suffix, int delay) {
        try {
            String fileName = generatePackage(recordNames, issueId, dbName, suffix);
            if (fileName != null) {
                if (delay == 0) {
                    deliverPackage(new File(fileName), issueId, dbName);
                } else {
                    tm.addTask(PackageUploader.UPLOAD_PACKAGE, delay, null, PackageUploader.class.getName(), fileName);
                }
            }
            return fileName;

        } catch (Exception e) {
            LOG.error(e);
            return null;
        }
    }

    @Override
    public String generatePackage(Collection<String> recordNames, int issueId, String dbName, String suffix)
            throws Exception {
        if (recordNames.size() == 0) {
            LOG.debug("0 records with updated meshterms");
            return null;
        }
        IssueVO vo = iss.getIssueVO(issueId);
        timestamp = System.currentTimeMillis();
        deliveryFileName = "clsysrev/clsysrev_" + vo.getYear() + "_" + String.format("%02d", vo.getNumber())
                               + PackageChecker.MESH_UPDATE_SUFFIX + PackageChecker.AUT_SUFFIX
                               + suffix + "_" + timestamp + Extensions.ZIP;
        LOG.debug("Count of updated records found = " + recordNames.size());
        File file = getPackage(recordNames, issueId, dbName, suffix.contains(PackageChecker.WML3G_POSTFIX));

        LOG.debug("Zip created, path=" + file.getAbsolutePath());
        return file.getAbsolutePath();
    }

    @Override
    public int getMaxRecordsInPackage() {
        return MAX_PACK_SIZE.get().asInteger();
    }
}
