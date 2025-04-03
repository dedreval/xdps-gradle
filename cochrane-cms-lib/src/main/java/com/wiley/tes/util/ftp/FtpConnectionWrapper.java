package com.wiley.tes.util.ftp;

import com.wiley.tes.util.Logger;
import com.wiley.tes.util.URIWrapper;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;

/**
 * @author <a href='mailto:ikirov@wiley.com'>Igor Kirov</a>
 * @version 23.08.11
 */
public class FtpConnectionWrapper implements FtpInteraction {

    private static final Logger LOG = Logger.getLogger(FtpConnectionWrapper.class);
    private static final int DEFAULT_PORT = 21;
    private final FTPConnection ftpConnection;

    public FtpConnectionWrapper() {
        ftpConnection = new FTPConnection();
    }

    @Override
    public void connect(String host, int port, String login, String password) throws Exception {
        connect(host, port, login, password, 0);
    }

    @Override
    public void connect(String host, int port, String login, int timeout, String identity) throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    public void connect(String host, int port, String login, String password, int timeout) throws Exception {
        if (timeout > 0) {
            ftpConnection.setDataTimeout(timeout);
        }
        boolean success = ftpConnection.connect(host, port);
        success = success && ftpConnection.login(login, password);

        if (!success) {
            throw new Exception(String.format("Connection to FTP %s failed", host));
        }
    }

    @Override
    public void connect(URIWrapper uriWrapper) throws Exception {
        int port = uriWrapper.getPort();
        connect(uriWrapper.getHost(), port == -1 ? DEFAULT_PORT : port,
                uriWrapper.getLogin(), uriWrapper.getPassword());
    }

    @Override
    public void disconnect() {
        try {
            ftpConnection.logout();
            ftpConnection.disconnect();
        } catch (IOException e) {
            LOG.warn("Failed to disconnect from FTP", e);
        }
    }

    @Override
    public void changeDirectory(String directory) throws Exception {
        if (!ftpConnection.changeDirectory(directory)) {
            throw new Exception("Failed to change directory to " + directory);
        }
    }

    @Override
    public void renameFile(String oldName, String newName) throws Exception {
        if (!ftpConnection.renameFile(oldName, newName)) {
            throw new Exception(String.format("Failed to rename file %s to %s", oldName, newName));
        }
    }

    @Override
    public void deleteFile(String fileName) throws Exception {
        if (!ftpConnection.deleteFile(fileName)) {
            throw new Exception("Failed to delete directory " + fileName);
        }
    }

    @Override
    public long getFileSize(String fileName) throws Exception {
        return ftpConnection.getFileSize(fileName);
    }

    @Override
    public void downloadFile(String serverPath, String localPath) throws Exception {
        if (!ftpConnection.downloadFile(serverPath, localPath)) {
            throw new Exception("Failed to download " + serverPath);
        }
    }

    @Override
    public void putFile(String serverPath, String localPath) throws Exception {
        String remoteFileName = FilenameUtils.getName(serverPath);
        String remoteDir = StringUtils.substringBefore(serverPath, remoteFileName);
        if (StringUtils.isNotEmpty(remoteDir) && !remoteDir.equals("/")) {
            changeDirectory(remoteDir);
        }

        InputStream is = null;
        try {
            is = new FileInputStream(localPath);
            ftpConnection.storeFile(remoteFileName, is);
        } finally {
            IOUtils.closeQuietly(is);
        }
    }

    @Override
    public List<String> listFiles() throws Exception {
        String files = ftpConnection.listFiles();
        return Arrays.asList(files.split("\n"));
    }
}
