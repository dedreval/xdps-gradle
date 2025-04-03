package com.wiley.cms.cochrane.cmanager;

import com.wiley.cms.cochrane.cmanager.data.DeliveryFileEntity;
import com.wiley.cms.cochrane.cmanager.data.IResultsStorage;
import com.wiley.cms.cochrane.cmanager.entitywrapper.ResultStorageFactory;
import com.wiley.cms.cochrane.process.IRecordCache;
import com.wiley.cms.cochrane.repository.IRepository;
import com.wiley.tes.util.InputUtils;
import com.wiley.tes.util.Logger;
import com.wiley.tes.util.URIWrapper;
import com.wiley.tes.util.ftp.FtpInteraction;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * @author Sergey Trofimov
 */
public abstract class AbstractZipPackage {
    protected static final Logger LOG = Logger.getLogger(DeliveryPackage.class);
    private static final long PACKAGE_SIZE = 10485760;

    protected boolean isFtp = false;

    protected String packageFileName;
    protected URI packageUri;
    protected int packageId = -1;
    protected IResultsStorage rs;

    public AbstractZipPackage() {
        init();
    }

    public AbstractZipPackage(URI uri, String packageFileName, int dfId) {
        this(packageFileName);

        init();
        packageUri = uri;
        packageId = dfId;
    }

    public AbstractZipPackage(URI packageUri) throws DeliveryPackageException {
        try {
            isFtp = packageUri.toString().contains("ftp");
            packageFileName = checkParams(packageUri);
            this.packageUri = packageUri;

            init();
            packageId = createDeliveryFile(packageFileName);

        } catch (Exception e) {
            throw new DeliveryPackageException(e.getMessage(), IDeliveryFileStatus.STATUS_BAD_FILE_NAME);
        }
    }

    protected AbstractZipPackage(String packageFileName) {
        this.packageFileName = packageFileName.toLowerCase();
    }

    protected static Integer checkInCache(Integer recordId, String cdNumber, String recordDirPath, boolean ta,
                                          IRepository rp, IRecordCache cache) throws Exception {
        if (recordId == null && cache.containsRecord(cdNumber) || recordId != null && !ta) {
            if (recordDirPath != null && rp.isFileExistsQuiet(recordDirPath)) {
                rp.deleteDir(recordDirPath);
            }
            throw CmsException.createForBeingProcessed(cdNumber);
        }
        return recordId;
    }

    protected static String parsePackageName(URI packageUri) {
        String path = packageUri.getPath();
        return path.substring(path.lastIndexOf("/") + 1).toLowerCase();
    }

    protected final void putPackageToRepository(String realDirToZipStore) throws Exception {
        if (realDirToZipStore != null) {
            File dir = new File(realDirToZipStore);
            if (!dir.exists() && !dir.mkdir()) {
                throw new Exception(String.format("%s has no entries parsed or accepted", packageFileName));
            }
            File store = new File(dir, packageFileName);
            InputUtils.writeFile(packageUri.toURL(), store);
        }
    }

    protected int createDeliveryFile(String dfName) throws Exception {
        return rs.createDeliveryFile(dfName, DeliveryFileEntity.TYPE_DEFAULT, IDeliveryFileStatus.STATUS_BEGIN,
                IDeliveryFileStatus.STATUS_PACKAGE_IDENTIFIED);
    }

    public abstract String getLibName();

    public abstract void parsePackageName() throws DeliveryPackageException;

    protected boolean isFtp() {
        return isFtp;
    }

    public int getPackageId() {
        return packageId;
    }

    public String getPackageFileName() {
        return packageFileName;
    }

    public URI getPackageUri() {
        return packageUri;
    }

    protected String checkParams(URI packageUri) throws Exception {
        if (packageUri == null) {
            throw new IllegalArgumentException("package uri=null");
        }
        String fileName = parsePackageName(packageUri);
        if (fileName.length() == 0) {
            throw new Exception("cannot parse filename for uri=" + packageUri);
        }
        return fileName;
    }

    protected void checkFirstZipEntry(String name, ZipEntry ze) throws CmsException {
        if (ze == null) {
            throw new CmsException(String.format("%s is empty or not in an actual zip format", name));
        }
    }

    protected ZipInputStream getZipStream(File tmp) {
        ZipInputStream zis = null;
        try {
            //long size = packageUri.toURL().openStream().available();
            if (isFtp()) {
                long size = getFileSizeByFtp();
                if (size == 0 || size > PACKAGE_SIZE) {
                    throw new Exception(String.format("size of %s is %d", packageUri, size));
                }
            }
            zis = new ZipInputStream(new ByteArrayInputStream(InputUtils.readByteStream(packageUri)));

        } catch (Exception e) {
            try {
                LOG.warn("%s - try to read a tmp file", e.getMessage());
                zis = zipFromTempFile(tmp);

            } catch (IOException e1) {
                LOG.error(e1, e1);
            }
        }
        return zis;
    }

    public static void putFileToRepository(IRepository repository, InputStream is, String path)
        throws DeliveryPackageException {
        try {
            repository.putFile(path, is, false);
        } catch (IOException e) {
            throw new DeliveryPackageException(e.getMessage(), IDeliveryFileStatus.STATUS_PICKUP_FAILED);
        }
    }

    public static void putFileToRepository(IRepository repository, InputStream is, String path, boolean close)
            throws DeliveryPackageException {
        try {
            repository.putFile(path, is, close);
        } catch (IOException e) {
            throw new DeliveryPackageException(e.getMessage(), IDeliveryFileStatus.STATUS_PICKUP_FAILED);
        }
    }

    protected void init() {
        rs = ResultStorageFactory.getFactory().getInstance();
    }

    private long getFileSizeByFtp() {
        URIWrapper uri = new URIWrapper(packageUri);
        FtpInteraction ftp = null;
        long size = 0;
        try {
            ftp = InputUtils.getConnection(uri);
            ftp.changeDirectory(uri.getGoToPath());
            size = ftp.getFileSize(uri.getFileName());
        } catch (Exception e) {
            LOG.error(e.getMessage());
        } finally {
            InputUtils.closeConnection(ftp);
        }
        return size;
    }

    private ZipInputStream zipFromTempFile(File tmp) throws IOException {
        ZipInputStream zis = null;

        if (InputUtils.writeFile(packageUri.toURL(), tmp)) {
            zis = new ZipInputStream(new FileInputStream(tmp));
        }

        return zis;
    }
}
