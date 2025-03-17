package com.wiley.cms.cochrane.cmanager.packagegenerator;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.ejb.EJB;
import javax.ejb.Local;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;

import com.wiley.cms.cochrane.cmanager.CmsException;
import com.wiley.cms.cochrane.cmanager.CmsUtils;
import com.wiley.cms.cochrane.cmanager.FilePathCreator;
import com.wiley.cms.cochrane.cmanager.data.ClDbEntity;
import com.wiley.cms.cochrane.cmanager.data.IResultsStorage;
import com.wiley.cms.cochrane.cmanager.data.IssueEntity;
import com.wiley.cms.cochrane.cmanager.data.issue.IssueVO;
import com.wiley.cms.cochrane.test.PackageChecker;
import com.wiley.tes.util.Extensions;
import com.wiley.tes.util.Logger;

/**
 * @author <a href='mailto:ikirov@wiley.com'>Igor Kirov</a>
 * @version 29.01.2010
 */
@Stateless
@Local(IPackageGenerator.class)
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class AddFromEntirePackageGenerator extends AbstractPackageGenerator {
    private static final Logger LOG = Logger.getLogger(AddFromEntirePackageGenerator.class);

    @EJB
    private IResultsStorage rs;

    @Override
    public List<String> generateAndUpload(List<String> recordNames, String dbName, int fullIssueNumber,
                                          String suffix) throws CmsException {
        if (recordNames.isEmpty()) {
            return Collections.emptyList();
        }
        return generateAndUpload(recordNames, rs.findOpenIssueEntity(CmsUtils.getYearByIssueNumber(
                fullIssueNumber), CmsUtils.getIssueByIssueNumber(fullIssueNumber)), dbName, suffix);
    }

    @Override
    public List<String> generateAndUpload(List<String> recordNames, int dbId, String suffix) throws CmsException {
        if (recordNames.isEmpty()) {
            return Collections.emptyList();
        }
        ClDbEntity clDb = rs.getDb(dbId);
        if (clDb != null) {
            IssueEntity issue = clDb.getIssue();
            if (issue != null) {
                return generateAndUpload(recordNames, issue, clDb.getTitle(), suffix);
            }
        }
        throw new CmsException(String.format("can't find issue entity by dbId [%d]", dbId));
    }

    @Override
    public List<String> generateAndUpload(List<String> recordNames, int issueId, String dbName, String suffix) {
        String name = uploadPackage(recordNames, issueId, dbName, suffix);
        if (name != null) {
            List<String> ret = new ArrayList<>();
            ret.add(name);
            return ret;
        }
        return Collections.emptyList();
    }

    private List<String> generateAndUpload(List<String> recordNames, IssueEntity issue, String dbName, String suffix) {
        String fileName = null;
        try {
            fileName = generatePackage(recordNames, issue.getId(), issue.getYear(), issue.getNumber(), dbName, suffix);
            deliverPackage(fileName, issue.getId(), dbName);
        } catch (Exception e) {
            LOG.error(e);
        }
        if (fileName != null) {
            List<String> ret = new ArrayList<>();
            ret.add(fileName);
            return ret;
        }
        return Collections.emptyList();
    }

    private String uploadPackage(Collection<String> recordNames, int issueId, String dbName, String suffix) {
        try {
            String fileName = generatePackage(recordNames, issueId, dbName, suffix);
            if (fileName != null) {
                deliverPackage(new File(fileName), issueId, dbName);
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
            LOG.warn("0 records found to upload");
            return null;
        }
        IssueVO vo = iss.getIssueVO(issueId);
        return generatePackage(recordNames, issueId, vo.getYear(), vo.getNumber(), dbName, suffix);
    }

    private String generatePackage(Collection<String> recordNames, int issueId, int year, int month, String dbName,
                                  String suffix) throws Exception {
        timestamp = System.currentTimeMillis();
        deliveryFileName = dbName + FilePathCreator.SEPARATOR
            + PackageChecker.buildCommonPrefix(dbName, year, String.format("%02d", month))
                + suffix + "_" + timestamp + Extensions.ZIP;
        File file = getPackage(recordNames, issueId, dbName, suffix.contains(PackageChecker.WML3G_POSTFIX));
        return file.getAbsolutePath();
    }
}
