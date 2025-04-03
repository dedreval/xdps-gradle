package com.wiley.tes.util.ftp;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;
import com.wiley.cms.cochrane.cmanager.CochraneCMSProperties;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.tes.util.URIWrapper;
import org.apache.commons.io.IOUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author <a href='mailto:aasadulin@wiley.com'>Alexandr Asadulin</a>
 * @version 13.09.2017
 */
public class SftpConnection implements FtpInteraction {

    public static final String URI_SCHEME = "sftp";
    private static final int DEFAULT_PORT = 22;
    private Session session;
    private ChannelSftp channel;
    private int latestModifiedTime = 0;

    @Override
    public void connect(String host, int port, String login, String password) throws JSchException {
        connect(host, port, login, password, 0);
    }

    @Override
    public void connect(String host, int port, String login, int timeout, String identity) throws JSchException {
        connect(host, port, login, null, timeout, identity);
    }

    @Override
    public void connect(String host, int port, String login, String password, int timeout) throws JSchException {
        connect(host, port, login, password, timeout,
                CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.SERVER_PRIVATE_KEY));
    }

    private void connect(String host, int port, String login, String password, int timeout, String identity)
            throws JSchException {

        JSch.setLogger(Log4JSch.JSCH_LOG);
        JSch jsch = new JSch();

        if (password == null) {
            jsch.addIdentity(identity);
        }

        session = jsch.getSession(login, host, port);
        if (password != null) {
            session.setPassword(password);
        }
        session.setConfig("StrictHostKeyChecking", "no");
        if (timeout > 0) {
            session.setTimeout(timeout);
        }
        session.connect(CONNECTION_TIMEOUT);
        try {
            channel = (ChannelSftp) session.openChannel(URI_SCHEME);
            channel.connect();

        } catch (JSchException e) {
            disconnect();
            session = null;
            throw e;
        }
    }

    @Override
    public void connect(URIWrapper uriWrapper) throws JSchException {
        int port = uriWrapper.getPort();
        connect(uriWrapper.getHost(), port == -1 ? DEFAULT_PORT : port,
             uriWrapper.getLogin(), uriWrapper.getPassword());
    }

    @Override
    public void disconnect() {
        if (session != null) {
            session.disconnect();
        }
    }

    @Override
    public void changeDirectory(String directory) throws SftpException {
        channel.cd(directory);
    }

    @Override
    public void renameFile(String oldName, String newName) throws Exception {
        channel.rename(oldName, newName);
    }

    @Override
    public void deleteFile(String fileName) throws Exception {
        channel.rm(fileName);
    }

    @Override
    public long getFileSize(String fileName) throws Exception {
        return channel.stat(fileName).getSize();
    }

    @Override
    public void downloadFile(String serverPath, String localPath) throws Exception {
        OutputStream os = null;
        try {
            os = new BufferedOutputStream(new FileOutputStream(localPath));
            channel.get(serverPath, os);
        } finally {
            IOUtils.closeQuietly(os);
        }
    }

    @Override
    public void putFile(String serverPath, String localPath) throws Exception {
        InputStream is = null;
        try {
            is = new BufferedInputStream(new FileInputStream(localPath));
            channel.put(is, serverPath);

        } finally {
            IOUtils.closeQuietly(is);
        }
    }

    @Override
    public List<String> listFiles() throws Exception {
        List<ChannelSftp.LsEntry> files = channel.ls(".");
        return files.stream()
                       .map(ChannelSftp.LsEntry::getFilename)
                       .collect(Collectors.toCollection(() -> new ArrayList<>(files.size())));
    }

    @Override
    public boolean hasFile(String fileName) throws Exception {
        List<ChannelSftp.LsEntry> files = channel.ls(".");
        for (ChannelSftp.LsEntry entry: files) {
            if (fileName.equals(entry.getFilename())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Set<String> listFilesFilteredByModifiedTime(int lastModifiedTime) throws Exception {
        List<ChannelSftp.LsEntry> files = channel.ls(".");
        List<ChannelSftp.LsEntry> lsEntries = files.stream()
                                            .filter(entry -> entry.getAttrs().getSize() > 0)
                                            .filter(entry -> getModifiedTime(entry) > lastModifiedTime)
                                            .sorted(Comparator.comparingInt(SftpConnection::getModifiedTime).reversed())
                                            .collect(Collectors.toList());
        if (lsEntries.isEmpty()) {
            return Collections.emptySet();
        }

        latestModifiedTime = getModifiedTime(lsEntries.get(0));
        return lsEntries.stream()
                       .map(ChannelSftp.LsEntry::getFilename)
                       .collect(Collectors.toSet());
    }

    @Override
    public int getLatestModifiedTime() {
        return latestModifiedTime;
    }

    @Override
    public boolean isConnected() {
        return session != null && session.isConnected() && channel != null && !channel.isClosed();
    }

    private static int getModifiedTime(ChannelSftp.LsEntry entry) {
        return entry.getAttrs().getMTime();
    }
}
