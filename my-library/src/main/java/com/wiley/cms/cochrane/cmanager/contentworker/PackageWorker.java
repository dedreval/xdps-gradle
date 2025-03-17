package com.wiley.cms.cochrane.cmanager.contentworker;

import java.net.URI;

import javax.naming.NamingException;

import com.wiley.cms.cochrane.cmanager.AbstractZipPackage;
import com.wiley.cms.cochrane.cmanager.CmsException;
import com.wiley.cms.cochrane.cmanager.CmsUtils;
import com.wiley.cms.cochrane.cmanager.CochraneCMSBeans;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.DeliveryPackageException;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.process.IRecordCache;
import com.wiley.cms.cochrane.process.handler.DbHandler;
import com.wiley.cms.cochrane.process.handler.PackageUploadHandler;
import com.wiley.cms.process.res.ProcessType;
import com.wiley.tes.util.URIWrapper;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 07.06.12
 */
abstract class PackageWorker<P extends AbstractZipPackage> extends AbstractWorker {

    protected P pck;

    protected String getPackageName() {
        return pck == null ? (hasPackageUri()
                ? packageUri.getPath().substring(packageUri.getPath().lastIndexOf("/") + 1).toLowerCase() : "")
                : pck.getPackageFileName();
    }

    protected void initPackage(P pck) throws DeliveryPackageException {

        this.pck = pck;
        deliveryFileId = pck.getPackageId();
        pck.parsePackageName();
    }

    protected IRecordCache getRecordCache() throws NamingException {
        return CochraneCMSPropertyNames.lookupRecordCache();
    }

    protected final void onFinal(URI packageUri, boolean removeOnFTP) {
        if (removeOnFTP) {
            onFinal(packageUri);
        }
    }

    protected void onFinal(URI packageUri) {
        String str = packageUri.toString();
        if (isFtpPackage(str)) {
            deletePackageOnFtp(new URIWrapper(packageUri), deliveryFileId);
        }
    }

    @Override
    public String getLibName() {
        return pck == null ? "undefined" : pck.getLibName();
    }

    protected static boolean isFtpPackage(String packagePath) {
        return packagePath.contains("ftp");
    }

    protected void startUploadProcess(String packageName, URI packageUri, String dbName, int dbId, int issueId,
                                      int fullIssueNumber, int processType) {
        ProcessType pt = ProcessType.find(processType).get();
        DbHandler mainHandler = new PackageUploadHandler(packageName, packageUri.toString(),
                fullIssueNumber, dbName, dbId, deliveryFileId, issueId);
        CochraneCMSBeans.getCMSProcessManager().startProcess(mainHandler, pt, theLogUser);
    }

    protected boolean check4NewFormats(BaseType baseType, String dfName, int type, int issueId, int dbId)
            throws CmsException {
        if (CmsUtils.isSpecialIssue(issueId)) {
            throw new CmsException(String.format(
                    "cannot upload delivery file [%d] to the special Issue for Import or SPD", deliveryFileId));
        }
        return false;
    }
}
