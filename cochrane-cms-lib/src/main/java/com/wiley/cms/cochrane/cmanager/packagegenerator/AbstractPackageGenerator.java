package com.wiley.cms.cochrane.cmanager.packagegenerator;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import javax.naming.OperationNotSupportedException;

import com.wiley.cms.cochrane.cmanager.CochraneCMSProperties;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.FilePathBuilder;
import com.wiley.cms.cochrane.cmanager.FilePathCreator;
import com.wiley.cms.cochrane.cmanager.data.db.DbStorageFactory;
import com.wiley.cms.cochrane.cmanager.data.db.IDbStorage;
import com.wiley.cms.cochrane.cmanager.data.issue.IIssueStorage;
import com.wiley.cms.cochrane.cmanager.data.issue.IssueStorageFactory;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.repository.IRepository;
import com.wiley.cms.cochrane.repository.RepositoryFactory;
import com.wiley.cms.cochrane.repository.RepositoryUtils;
import com.wiley.cms.cochrane.services.IContentManager;
import com.wiley.cms.cochrane.utils.zip.IZipOutput;
import com.wiley.cms.cochrane.utils.zip.ZipOutput;

/**
 * @author <a href='mailto:ikirov@wiley.com'>Igor Kirov</a>
 * @author <a href='mailto:svyatoslav.gulin@gmail.com'>Svyatoslav Gulin</a>
 * @version 29.01.2010
 */
public abstract class AbstractPackageGenerator implements IPackageGenerator {
    protected String fileSystemRoot = CochraneCMSProperties.getProperty("filesystem.root");
    protected IRepository rps = RepositoryFactory.getRepository();
    protected IIssueStorage iss = IssueStorageFactory.getFactory().getInstance();

    protected String deliveryFileName;
    protected long timestamp;

    public AbstractPackageGenerator() {
    }

    public void deliverPackage(String fileName, int issueId, String dbName) throws Exception {
        deliverPackage(new File(fileName), issueId, dbName);
    }

    public String generatePackage(Collection<String> recordNames, int issueId, String dbName, String suffix)
            throws Exception {
        throw new OperationNotSupportedException();
    }

    public List<String> generateAndUpload(List<String> recordNames, int fullIssueNumber, String suffix)
            throws Exception {
        throw new OperationNotSupportedException();
    }

    public List<String> generateAndUpload(List<String> recordNames, String dbName, int fullIssueNumber, String suffix)
           throws Exception {
        throw new OperationNotSupportedException();
    }

    protected void deliverPackage(File file, int issueId, String dbName) throws Exception {

        IContentManager manager = CochraneCMSPropertyNames.lookup("ContentManager", IContentManager.class);

        IDbStorage storage = DbStorageFactory.getFactory().getInstance();
        storage.setInitialPackageDeliveredByIssueIdAndTitle(issueId, dbName);

        manager.newPackageReceived(file.toURI());
    }

    File getPackage(Collection<String> recordNames, int issueId, String dbName, boolean ml3g) throws Exception {

        IZipOutput out = createPackage(issueId, dbName);
        String packagePath = FilePathCreator.buildPackagePath("" + timestamp, dbName);
        boolean ed = BaseType.find(dbName).get().isEditorial();
        boolean cca = !ed && BaseType.find(dbName).get().isCCA();
        for (String recordName : recordNames) {
            InputStream is;
            if (ml3g) {
                is = cca ? rps.getFile(FilePathBuilder.getPathToEntireSrcRecord(dbName, recordName))
                        : rps.getFile(FilePathCreator.getFilePathForEntireMl3gXml(dbName, recordName));
                if (ed || cca) {
                    String path = FilePathBuilder.getPathToEntireSrcRecordDir(dbName, recordName);
                    writeDir(out, path, recordName, cca ? ""  : packagePath);
                }
            } else {
                is = rps.getFile(FilePathCreator.getFilePathToSourceEntire(dbName, recordName));
                String path = cca ? FilePathBuilder.getPathToEntireSrcRecordDir(dbName, recordName)
                        : FilePathCreator.getFilePathForEnclosureEntire(dbName, recordName, "");
                writeDir(out, path, recordName, cca ? ""  : packagePath);
            }
            if (cca) {
                out.put(FilePathCreator.buildPackagePathRecord("", recordName), is);
            } else {
                out.put(FilePathCreator.buildPackagePathRecord(packagePath, recordName), is);
            }
        }
        out.close();

        return RepositoryUtils.getRealFile(fileSystemRoot,
                CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.PREFIX_REPOSITORY) + "/" + issueId + "/"
                        + deliveryFileName);
    }

    protected IZipOutput createPackage(int issueId, String dbName) throws Exception {
        return new ZipOutput(
                CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.PREFIX_REPOSITORY) + "/" + issueId + "/"
                        + deliveryFileName);
    }

    private void writeFile(IZipOutput out, File file, String recordName, String packagePath) throws IOException {

        InputStream is = new FileInputStream(file);
        String fileName = file.toURI().toString();
        out.put(FilePathCreator.buildPackagePathRecordFile(packagePath, recordName, fileName), is);
        //out.put( "/" + timestamp + "/" + dbName + "/" + fileName.substring(fileName.indexOf(recordName)), is);
        is.close();
    }

    private void writeDir(IZipOutput out, String path, String recordName, String packagePath) throws IOException {
        Queue<String> queue = new LinkedList<>();
        queue.offer(path);

        while (!queue.isEmpty()) {
            String tmpDir = queue.poll();
            final File[] files = rps.getFilesFromDir(tmpDir);
            if (files == null) {
                continue;
            }
            for (final File file : files) {
                if (file.isDirectory()) {
                    queue.offer(file.getAbsolutePath());
                } else {
                    writeFile(out, file, recordName, packagePath);
                }
            }
        }
    }
}
