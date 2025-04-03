package com.wiley.tes.util;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;

import com.wiley.cms.cochrane.repository.RepositoryFactory;
import com.wiley.tes.util.ftp.FtpConnectionWrapper;
import com.wiley.tes.util.ftp.FtpInteraction;
import com.wiley.tes.util.ftp.SftpConnection;

/**
 * @author <a href='mailto:gkhristich@wiley.ru'>Galina Khristich</a>
 *         Date: 05-Mar-2007
 */
public class InputUtils {
    private static final Logger LOG = Logger.getLogger(InputUtils.class);

    private InputUtils() {
    }

    public static void removeParentFolder(String path) {
        String folder = path.substring(0, path.lastIndexOf('/'));
        try {
            RepositoryFactory.getRepository().deleteFile(folder);
        } catch (IOException e) {
            LOG.debug(e, e);
        }
    }

    public static boolean writeFile(URL rawData, File rawDataFile) throws IOException {
        InputStream stream = null;
        BufferedOutputStream fos = null;
        BufferedInputStream is = null;
        try {
            LOG.debug("rawData " + rawData + " tmp " + rawDataFile.getName());

            stream = rawData.openStream();
            is = new BufferedInputStream(stream, FileUtils.BUFFER_SIZE);
            fos = new BufferedOutputStream(new FileOutputStream(rawDataFile));
            int readed;
            boolean isNotEmpty = false;
            byte[] buffer = new byte[FileUtils.BUFFER_SIZE];
            while ((readed = is.read(buffer)) > 0) {
                fos.write(buffer, 0, readed);
                isNotEmpty = true;
            }
            fos.flush();
            return isNotEmpty;
        } finally {
            try {
                if (stream != null) {
                    stream.close();
                }
                if (is != null) {
                    is.close();
                }
                if (fos != null) {
                    fos.close();
                }
            } catch (Exception e) {
                ;
            }

        }
    }

    public static byte[] readByteStream(URI uri) throws IOException {
        BufferedInputStream is = null;
        InputStream uriStream = null;
        ByteArrayOutputStream byteStream = null;
        try {
            uriStream = uri.toURL().openStream();
            is = new BufferedInputStream(uriStream);

            byteStream = new ByteArrayOutputStream(FileUtils.BUFFER_SIZE);

            int readed;
            byte[] buffer = new byte[FileUtils.BUFFER_SIZE];
            while ((readed = is.read(buffer)) > 0) {
                byteStream.write(buffer, 0, readed);
            }
        } catch (IOException e) {
            LOG.error(e, e);
            throw new IOException(e.getMessage());
        } finally {
            try {
                if (uriStream != null) {
                    uriStream.close();
                }
                if (is != null) {
                    is.close();
                }
                if (byteStream != null) {
                    byteStream.close();
                }
            } catch (IOException e) {
                LOG.error(e, e);
            }
        }
        return byteStream.toByteArray();
    }

    public static ByteArrayOutputStream readStream(InputStream stream, boolean close) throws IOException {
        ByteArrayOutputStream byteStream = null;
        try {
            byteStream = new ByteArrayOutputStream(FileUtils.BUFFER_SIZE);

            int readed;
            byte[] buffer = new byte[FileUtils.BUFFER_SIZE];
            while ((readed = stream.read(buffer)) > 0) {
                byteStream.write(buffer, 0, readed);
            }
        } finally {
            try {
                if (close && stream != null) {
                    stream.close();
                }
                if (byteStream != null) {
                    byteStream.close();
                }
            } catch (IOException e) {
                LOG.error(e, e);
            }
        }
        return byteStream;
    }

    public static byte[] readStreamToByte(InputStream stream) throws IOException {
        return readStream(stream, true).toByteArray();
    }

    public static String readStreamToString(InputStream stream) throws IOException {
        return readStream(stream, true).toString();
    }

    public static String readStreamToString(InputStream stream, String encoding) throws IOException {
        return readStream(stream, true).toString(encoding);
    }

    public static FtpInteraction getSftpConnection(URI uri, int timeout) throws Exception {
        if (!SftpConnection.URI_SCHEME.equals(uri.getScheme())) {
            throw new Exception(String.format("scheme '%s' is not to SFTP", uri.getScheme()));
        }
        String[] userInfo = uri.getUserInfo().split(":");
        return getSftpConnection(uri.getHost(), uri.getPort(),
                userInfo[0], userInfo.length > 1 ? userInfo[1] : null, timeout);
    }

    public static FtpInteraction getSftpConnection(String host, int port, String login, String password, int timeout)
            throws Exception {
        try {
            FtpInteraction ftpInteraction = new SftpConnection();
            ftpInteraction.connect(host, port, login, password, timeout);
            return ftpInteraction;

        } catch (Exception e) {
            throw new Exception(String.format("failed to establish connection to %s %d", host, port), e);
        }
    }

    public static FtpInteraction getFtpConnection(String host, int port, String login, String password, int timeout)
            throws Exception {
        try {
            FtpInteraction ftpInteraction = new FtpConnectionWrapper();
            ftpInteraction.connect(host, port, login, password, timeout);
            return ftpInteraction;

        } catch (Exception e) {
            throw new Exception(String.format("failed to establish connection to %s %d ", host, port), e);
        }
    }

    public static FtpInteraction getConnection(URIWrapper uri) throws Exception {
        String hostMsg = "host " + uri.getHost() + " port " + uri.getPort();
        LOG.debug("Establishing connection to " + hostMsg);

        try {
            FtpInteraction ftpInteraction = SftpConnection.URI_SCHEME.equals(uri.getUri().getScheme())
                    ? new SftpConnection()
                    : new FtpConnectionWrapper();
            ftpInteraction.connect(uri);

            return ftpInteraction;
        } catch (Exception e) {
            throw new Exception("Failed to establish connection to " + hostMsg, e);
        }
    }

    public static void closeConnection(FtpInteraction interaction) {
        if (interaction != null) {
            interaction.disconnect();
        }
    }

    public static void writeDir(File dir, String rootPath) {
        try {
            File[] files = dir.listFiles();
            for (File f : files) {
                String path = rootPath + "/" + f.getName();
                if (f.isDirectory()) {
                    boolean ok = new File(path).mkdir();

                    writeDir(f, path);
                } else {
                    writeFile(new File(path), new BufferedInputStream(new FileInputStream(f)));
                }
            }
        } catch (Exception e) {
            LOG.error("error in" + dir.getName());
            //throw new Exception(e);
        }
    }

    public static void writeFile(String pathToDir, String toName, InputStream from) throws Exception {
        writeFile(pathToDir, toName, from, true);
    }

    public static void writeFile(String pathToDir, String toName, InputStream from, boolean close) throws Exception {
        if (pathToDir != null) {
            File dir = new File(pathToDir);
            if (!dir.exists()) {
                dir.mkdir();
            }
            File store = new File(dir, toName);
            writeFile(store, from, close);
        }
    }

    public static void writeFile(File to, InputStream from) throws IOException {
        writeFile(to, from, true);
    }

    public static void writeFile(File to, InputStream from, boolean close) throws IOException {
        BufferedOutputStream fos = null;
        try {
            fos = new BufferedOutputStream(new FileOutputStream(to));
            int readed;
            byte[] buffer = new byte[FileUtils.BUFFER_SIZE];
            while ((readed = from.read(buffer)) > 0) {
                fos.write(buffer, 0, readed);
            }
            fos.flush();
        } finally {
            try {
                if (from != null && close) {
                    from.close();
                }
                if (fos != null) {
                    fos.close();
                }
            } catch (Exception e) {
                LOG.warn(e.getMessage());
            }
        }
    }
}


