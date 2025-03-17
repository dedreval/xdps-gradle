package com.wiley.tes.util.ftp;

import com.wiley.tes.util.Now;
import com.wiley.tes.util.URIWrapper;

import java.util.List;
import java.util.Set;

/**
 * @author <a href='mailto:aasadulin@wiley.com'>Alexandr Asadulin</a>
 * @version 13.09.2017
 */
public interface FtpInteraction {

    int CONNECTION_TIMEOUT = Now.calculateMillisInMinute();

    void connect(URIWrapper uriWrapper) throws Exception;

    void connect(String host, int port, String login, String password) throws Exception;

    void connect(String host, int port, String login, String password, int timeout) throws Exception;

    void connect(String host, int port, String login, int timeout, String identity) throws Exception;

    void disconnect();

    void changeDirectory(String directory) throws Exception;

    void renameFile(String oldName, String newName) throws Exception;

    void deleteFile(String fileName) throws Exception;

    long getFileSize(String fileName) throws Exception;

    void downloadFile(String serverPath, String localPath) throws Exception;

    void putFile(String serverPath, String localPath) throws Exception;

    List<String> listFiles() throws Exception;

    default boolean hasFile(String fileName) throws Exception {
        return false;
    }

    default Set<String> listFilesFilteredByModifiedTime(int lastModifiedTime) throws Exception {
        throw new UnsupportedOperationException();
    }

    default int getLatestModifiedTime() {
        return 0;
    }

    default boolean isConnected() {
        return false;
    }
}
