package com.wiley.cms.cochrane.process.handler;


import com.wiley.cms.cochrane.cmanager.CmsUtils;
import com.wiley.cms.cochrane.process.CMSProcessManager;
import com.wiley.cms.process.ProcessHandler;
import com.wiley.cms.process.ProcessVO;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 08.22.19
 *
 * @param <Q>
 */
public class PackageUploadHandler<Q> extends DbHandler<CMSProcessManager, Q> {
    private static final long serialVersionUID = 1L;

    private String packageUri;

    public PackageUploadHandler() {
    }

    public PackageUploadHandler(String packageName, String packageUri, int issue, String dbName, int dbId, int dfId,
                                int issueId) {
        super(issue, dbName, dbId, dfId, packageName, issueId);
        this.packageUri = packageUri;
    }

    @Override
    public void pass(ProcessVO pvo, ProcessHandler to) {
        super.pass(pvo, to);

        if (to instanceof PackageUnpackHandler) {
            ((PackageUnpackHandler) to).setPackageUri(packageUri);
        }
    }

    @Override
    protected void onEnd(ProcessVO pvo, CMSProcessManager manager) {
        if (CmsUtils.isImportIssue(getIssueId())) {
            updateDeliveryFileOnFinish();
        } else {
            manager.getDeliveringService().finishUpload(getIssueId(), getDfId(), getDbName(), pvo.getId());
        }
    }
}
