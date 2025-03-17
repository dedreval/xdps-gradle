package com.wiley.tes.util.ftp;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.io.IOUtils;

import com.wiley.tes.util.URIWrapper;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 7/09/2022
 */
public class NoFtpStub implements FtpInteraction {

    private final String startWorkDirectoryPath;
    private File workDirectory;
    private int latestModifiedTime;

    public NoFtpStub(String workDirectoryPath) {
        startWorkDirectoryPath = workDirectoryPath;
    }

    @Override
    public void connect(String host, int port, String login, String password)  {
        connect();
    }

    @Override
    public void connect(String host, int port, String login, int timeout, String identity) {
        connect();
    }

    @Override
    public void connect(String host, int port, String login, String password, int timeout) {
        connect();
    }

    @Override
    public void connect(URIWrapper uriWrapper) {
        connect();
    }

    private void connect() {
        workDirectory = new File(startWorkDirectoryPath);
    }

    @Override
    public void disconnect() {
        workDirectory = null;
    }

    @Override
    public void changeDirectory(String directory) throws Exception {
        checkDisconnected();
        workDirectory = new File(startWorkDirectoryPath, directory);
    }

    @Override
    public void renameFile(String oldName, String newName) {
        throw new UnsupportedOperationException("renaming is currently not supported");
    }

    @Override
    public void deleteFile(String fileName) throws Exception {
        checkDisconnected();
        new File(workDirectory, fileName).delete();
    }

    @Override
    public long getFileSize(String fileName) throws Exception {
        checkDisconnected();
        return new File(workDirectory, fileName).length();
    }

    @Override
    public void downloadFile(String serverPath, String localPath) throws Exception {
        checkDisconnected();

        copyFile(new File(workDirectory, serverPath), new File(localPath));
    }

    @Override
    public void putFile(String serverPath, String localPath) throws Exception {
        checkDisconnected();

        copyFile(new File(localPath), new File(workDirectory, serverPath));
    }

    private static void copyFile(File from, File to) throws Exception {
        InputStream is = null;
        OutputStream os = null;
        try {
            is = new BufferedInputStream(new FileInputStream(from));
            os = new BufferedOutputStream(new FileOutputStream(to));
            IOUtils.copy(is, os);

        } finally {
            IOUtils.closeQuietly(is);
            IOUtils.closeQuietly(os);
        }
    }

    @Override
    public List<String> listFiles() throws Exception {
        checkDisconnected();

        File[] files = workDirectory.listFiles();
        if (files != null) {
            List<String> ret = new ArrayList<>();
            for (File fl: files) {
                ret.add(fl.getName());
            }
            return ret;
        }
        return Collections.emptyList();
    }

    @Override
    public boolean hasFile(String fileName) throws Exception {
        checkDisconnected();

        File[] files = workDirectory.listFiles();
        if (files != null) {
            for (File fl: files) {
                if (fileName.equals(fl.getName())) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public Set<String> listFilesFilteredByModifiedTime(int lastModifiedTime) throws Exception {
        checkDisconnected();

        File[] files = workDirectory.listFiles();
        if (files != null) {
            Set<String> ret = new HashSet<>();
            for (File fl: files) {
                if (fl.lastModified() > lastModifiedTime) {
                    ret.add(fl.getName());
                }
            }
            return ret;

        }
        return Collections.emptySet();
    }

    @Override
    public int getLatestModifiedTime() {
        return latestModifiedTime;
    }

    @Override
    public boolean isConnected() {
        return workDirectory != null;
    }

    private void checkDisconnected() throws Exception {
        if (workDirectory == null) {
            throw new Exception("disconnected");
        }
        if (!workDirectory.exists()) {
            throw new Exception(String.format("%s does not exist", workDirectory.getAbsolutePath()));
        }
    }
}
