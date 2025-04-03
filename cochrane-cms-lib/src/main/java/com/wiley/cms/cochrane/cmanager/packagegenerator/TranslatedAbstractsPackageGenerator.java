package com.wiley.cms.cochrane.cmanager.packagegenerator;

import java.io.File;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;

import javax.ejb.Local;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;

import com.wiley.cms.cochrane.cmanager.FilePathCreator;
import com.wiley.cms.cochrane.cmanager.contentworker.RevmanPackage;
import com.wiley.cms.cochrane.cmanager.data.issue.IssueVO;
import com.wiley.cms.cochrane.utils.zip.IZipOutput;
import com.wiley.tes.util.Logger;

/**
 * @author <a href='mailto:ikirov@wiley.com'>Igor Kirov</a>
 * @version 13.11.2009
 */

@Stateless
@Local(IPackageGenerator.class)
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
@Deprecated
public class TranslatedAbstractsPackageGenerator extends AbstractPackageGenerator {
    private static final Logger LOG = Logger.getLogger(TranslatedAbstractsPackageGenerator.class);

    private InputStream response = null;

    @Override
    public List<String> generateAndUpload(List<String> recordNames, int issueId, String dbName, String suffix) {
        return Collections.emptyList();
    }

    public File generateFile(List<String> recordNames, int issueId, String packageNameSuffix,
                             InputStream response) throws Exception {

        if (recordNames.isEmpty()) {
            return null;
        }
        this.response = response;
        IssueVO vo = iss.getIssueVO(issueId);
        timestamp = System.currentTimeMillis();
        deliveryFileName =
                "clsysrev/clsysrev_" + vo.getYear() + "_" + String.format("%02d", vo.getNumber())
                        + "_" + packageNameSuffix + timestamp + ".zip";
        LOG.debug("Count of updated records found = " + recordNames.size());
        return getPackage(recordNames, issueId, "clsysrev", false);
    }

    @Override
    protected IZipOutput createPackage(int issueId, String dbName) throws Exception {

        IZipOutput out = super.createPackage(issueId, dbName);

        if (response != null) {
            out.put(FilePathCreator.SEPARATOR + timestamp + FilePathCreator.SEPARATOR + dbName
                    + FilePathCreator.SEPARATOR + RevmanPackage.RESPONSE_FILE_NAME, response);
            response.close();
        }

        return out;
    }
}
