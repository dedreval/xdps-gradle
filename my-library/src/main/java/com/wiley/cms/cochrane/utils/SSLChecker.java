package com.wiley.cms.cochrane.utils;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManagerFactory;

import org.apache.commons.io.IOUtils;

import com.wiley.cms.cochrane.cmanager.CmsException;
import com.wiley.cms.process.ProcessHelper;
import com.wiley.cms.process.task.IScheduledTask;
import com.wiley.cms.process.task.ITaskExecutor;
import com.wiley.cms.process.task.TaskVO;
import com.wiley.tes.util.Logger;
import com.wiley.tes.util.Now;
import com.wiley.tes.util.res.Property;
import com.wiley.tes.util.res.Res;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 10/1/2020
 */
public class SSLChecker implements ITaskExecutor, IScheduledTask {
    private static final Logger LOG = Logger.getLogger(SSLChecker.class);

    private static final Res<Property> SCHEDULE = Property.get("cms.task.ssl_checker.schedule");
    private static final Res<Property> LOAD_SCRIPT = Property.get("cms.task.ssl_loader.script", "");
    private static final Res<Property> LOAD_FOLDER = Property.get("cms.task.ssl_loader.folder", "");
    private static final Res<Property> LOAD_TIMEOUT = Property.get("cms.task.ssl_loader.time-out", "20");

    public boolean execute(TaskVO task) throws Exception {

        updateSchedule(task);
        return true;
    }

    public static boolean checkCertificate(String uri, Throwable tr) {
        return isSSLIssue(tr) && checkCertificate(uri);
    }

    public static synchronized boolean checkCertificate(String uri) {
        try {
            KeyStore ks = getKeyStore();
            return ks != null && uploadCertificate(uri, ks);
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
        }
        return false;
    }

    public static boolean isSSLIssue(Throwable tr) {
        if (tr == null) {
            return false;
        }
        return tr instanceof SSLException || tr instanceof KeyStoreException || isSSLIssue(tr.getCause());
    }

    private static boolean uploadCertificate(String url, KeyStore ks) throws Exception {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");

        Enumeration aliases = ks.aliases();
        Set<String> expiredAliases = new HashSet<>();
        while (aliases.hasMoreElements()) {

            String alias = aliases.nextElement().toString();
            Certificate crt = ks.getCertificate(alias);
            if (crt instanceof X509Certificate)  {
                X509Certificate x509crt = (X509Certificate) crt;
                checkExpired(alias, x509crt, expiredAliases);
            }
        }
        
        String alias = URI.create(url).getHost();
        String certificatePath = downloadCertificate(url, alias);
        expiredAliases.add(alias);
        removeCertificatesFromTruststore(expiredAliases, ks);
        importCertificateToTruststore(alias, certificatePath, ks, cf);

        return true;
    }

    private static String downloadCertificate(String url, String alias) throws CmsException {
        String loadScript = getLoadScript();
        String destinationFolder = getCertificatesFolder();
        if (destinationFolder.length() == 0) {
            destinationFolder = new File(loadScript).getParent() + "/downloaded_certificates/";
        }
        String destPath = destinationFolder + alias + ".cert";
        if (loadScript.length() == 0) {
            throw new CmsException(String.format(
                    "can't download SSL certificate for %s as SSL getter script is not set", url));
        }
        long startTime = System.currentTimeMillis();
        StringBuilder err = new StringBuilder();
        int ret = ProcessHelper.execCommand(String.format("%s %s %s", loadScript, url, destPath), null,
                getCertificatesDownLoadTimeout(), TimeUnit.SECONDS, err);
        if (ret < 0 || err.length() > 0) {
            throw new CmsException(
                    String.format("can't download SSL certificate for %s due to: [%d code] %s", url, ret, err));
        }
        checkDownloadedValid(destPath, startTime);
        return destPath;
    }

    private static void checkDownloadedValid(String destPath, long startTime) throws CmsException {
        File fl = new File(destPath);
        if (!fl.exists() || fl.length() == 0 || fl.lastModified() / Now.MS_IN_SEC < startTime / Now.MS_IN_SEC) {
            throw new CmsException(String.format("can't download SSl certificate from %s", destPath));
        }
    }

    private static Set<String> checkExpired(String alias, X509Certificate crt, Set<String> expiredAliases) {
        Set<String> ret = expiredAliases;
        try {
            crt.checkValidity();

        } catch (CertificateExpiredException cee) {
            LOG.debug(String.format("%s is being removed from truststore due to %s", alias, cee.getMessage()));
            if (ret == null) {
                ret = new HashSet<>();
            }
            ret.add(alias);

        } catch (CertificateNotYetValidException cve) {
            LOG.warn(String.format("%s is not valid: %s", alias, cve.getMessage()));
        }
        return ret;
    }

    private static String getCertificateStorePath() {
        return System.getProperty("javax.net.ssl.trustStore");
    }

    private static char[] getCertificateStorePass() {
        return System.getProperty("javax.net.ssl.trustStorePassword").toCharArray();
    }

    private static String getLoadScript() {
        return LOAD_SCRIPT.get().getValue().trim();
    }

    private static String getCertificatesFolder() {
        return LOAD_FOLDER.get().getValue().trim();
    }

    private static int getCertificatesDownLoadTimeout() {
        return LOAD_TIMEOUT.get().asInteger();
    }

    private static KeyStore getKeyStore() throws Exception {
        InputStream ksIs = null;
        KeyStore ks;
        try {
            String storePath = getCertificateStorePath();
            ks = KeyStore.getInstance("jks");

            if (storePath != null && storePath.length() > 0) {
                if (!new File(storePath).exists()) {
                    ks.load(null, getCertificateStorePass());
                    saveTruststore(ks);
                }
                ksIs = new BufferedInputStream(new FileInputStream(storePath));
                ks.load(ksIs, getCertificateStorePass());

            } else {
                throw new CmsException("a path to keystore file is empty");
            }

        } finally {
            IOUtils.closeQuietly(ksIs);
        }
        return ks;
    }

    private static void removeCertificatesFromTruststore(Collection<String> aliases, KeyStore ks) throws Exception {
        aliases.forEach(alias -> removeCertificateFromTruststore(alias, ks));
        saveTruststore(ks);
    }

    private static void removeCertificateFromTruststore(String alias, KeyStore ks)  {
        try {
            if (ks.getCertificate(alias) != null) {
                ks.deleteEntry(alias);
            }
        } catch (Exception e) {
            LOG.error(e);
        }
    }

    private static void saveTruststore(KeyStore ks) throws Exception {
        FileOutputStream storeOutputStream = new FileOutputStream(getCertificateStorePath());
        ks.store(storeOutputStream, getCertificateStorePass());
        storeOutputStream.close();
    }

    private static void importCertificateToTruststore(String alias, String path, KeyStore ks, CertificateFactory cf)
            throws Exception {
        InputStream certificateInputStream = null;
        try {
            LOG.debug(String.format("%s is being imported to keystore ...", alias));
            certificateInputStream = new BufferedInputStream(new FileInputStream(path));
            Certificate certificate = cf.generateCertificate(certificateInputStream);

            ks.setCertificateEntry(alias, certificate);
            saveTruststore(ks);

            String algorithm = TrustManagerFactory.getDefaultAlgorithm();
            TrustManagerFactory trustMgr = TrustManagerFactory.getInstance(algorithm);
            trustMgr.init(ks);

            SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, trustMgr.getTrustManagers(), new SecureRandom());
            SSLContext.setDefault(sslContext);
            HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());

        } finally {
            IOUtils.closeQuietly(certificateInputStream);
        }
    }

    @Override
    public String getScheduledTemplate() {
        return SCHEDULE.get().getValue();
    }
}
