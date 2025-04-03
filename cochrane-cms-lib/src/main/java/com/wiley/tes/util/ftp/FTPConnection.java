package com.wiley.tes.util.ftp;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.StringTokenizer;

import com.wiley.cms.cochrane.cmanager.CochraneCMSProperties;
import com.wiley.tes.util.InputUtils;
import com.wiley.tes.util.Logger;
import com.wiley.tes.util.Now;
import com.wiley.tes.util.URIWrapper;

import org.apache.commons.io.IOUtils;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;

/**
 * @author <a href='mailto:ikirov@wiley.com'>Igor Kirov</a>
 * @version 23.08.11
 */
public class FTPConnection {
    private static final Logger LOG = Logger.getLogger(FTPConnection.class);
    private static final int DEFAULT_TIMEOUT = Now.calculateMillisInMinute() * 2;

    private final FTPClient delegate;

    public FTPConnection() {
        delegate = new FTPClient();
        delegate.setDefaultTimeout(
                CochraneCMSProperties.getIntProperty("cms.cochrane.ftp.timeout.connection", DEFAULT_TIMEOUT));
        setDataTimeout(CochraneCMSProperties.getIntProperty("cms.cochrane.ftp.timeout.data_retrieve", DEFAULT_TIMEOUT));
    }

    public static FTPClient connectByFTPClient(String serverName, String login, String password,
                                               int port, int timeout) throws IOException {
        FTPClient ftp = new FTPClient();
        ftp.setConnectTimeout(DEFAULT_TIMEOUT);
        if (port < 0) {
            ftp.connect(serverName);
        } else {
            ftp.connect(serverName, port);
        }
        ftp.login(login, password);
        if (timeout > 0) {
            ftp.setDataTimeout(timeout);
            ftp.setControlKeepAliveTimeout(timeout / Now.MS_IN_SEC >> 1);
        }
        return ftp;
    }

    public static boolean deletePackageOnFtp(URIWrapper uri) {
        FtpInteraction ftp = null;
        try {
            ftp = InputUtils.getConnection(uri);
            ftp.changeDirectory(uri.getGoToPath());
            ftp.deleteFile(uri.getFileName());
            return true;

        } catch (Exception e) {
            LOG.error("Failed to delete package " + uri.getFileName(), e);
        } finally {
            InputUtils.closeConnection(ftp);
        }
        return false;
    }

    void setDataTimeout(int timeout) {
        delegate.setDataTimeout(timeout);
    }

    public boolean connect(String host) throws IOException {
        return connect(host, 21);
    }

    public boolean connect(String host, int port) throws IOException {
        delegate.enterLocalPassiveMode();
        delegate.connect(host, port);

        if (!isPositiveCompleteResponse(getServerReply())) {
            disconnect();
            return false;
        }
        return true;
    }

    public void disconnect() {
        try {
            delegate.disconnect();
        } catch (IOException ignored) {
        }
    }

    public boolean login(String username, String password) throws IOException {
        int response = executeCommand("user " + username);
        if (!isPositiveIntermediateResponse(response)) {
            return false;
        }
        response = executeCommand("pass " + password);
        return isPositiveCompleteResponse(response);
    }

    public boolean logout() throws IOException {
        int response = executeCommand("quit");
        return isPositiveCompleteResponse(response);
    }

    public boolean changeDirectory(String directory) throws IOException {
        int response = executeCommand("cwd " + directory);
        return isPositiveCompleteResponse(response);
    }

    public boolean renameFile(String oldName, String newName) throws IOException {
        int response = executeCommand("rnfr " + oldName);
        if (!isPositiveIntermediateResponse(response)) {
            return false;
        }
        response = executeCommand("rnto " + newName);
        return isPositiveCompleteResponse(response);
    }

    public boolean makeDirectory(String directory) throws IOException {
        int response = executeCommand("mkd " + directory);
        return isPositiveCompleteResponse(response);
    }

    public boolean removeDirectory(String directory) throws IOException {
        int response = executeCommand("rmd " + directory);
        return isPositiveCompleteResponse(response);
    }

    public boolean parentDirectory() throws IOException {
        int response = executeCommand("cdup");
        return isPositiveCompleteResponse(response);
    }

    public boolean deleteFile(String fileName) throws IOException {
        int response = executeCommand("dele " + fileName);
        return isPositiveCompleteResponse(response);
    }

    public String getCurrentDirectory() throws IOException {
        String response = getExecutionResponse("pwd");
        StringTokenizer strtok = new StringTokenizer(response);

        // Get rid of the first token, which is the return code
        if (strtok.countTokens() < 2) {
            return null;
        }
        strtok.nextToken();
        String directoryName = strtok.nextToken();

        // Most servers surround the directory name with quotation marks
        int strlen = directoryName.length();
        if (strlen == 0) {
            return null;
        }
        if (directoryName.charAt(0) == '\"') {
            directoryName = directoryName.substring(1);
            strlen--;
        }
        if (directoryName.charAt(strlen - 1) == '\"') {
            return directoryName.substring(0, strlen - 1);
        }
        return directoryName;
    }

    public String getSystemType() throws IOException {
        return excludeCode(getExecutionResponse("syst"));
    }

    public long getModificationTime(String fileName) throws IOException {
        String response = excludeCode(getExecutionResponse("mdtm " + fileName));
        try {
            return Long.parseLong(response);
        } catch (Exception e) {
            return -1L;
        }
    }

    public long getFileSize(String fileName) throws IOException {
        String response = excludeCode(getExecutionResponse("size " + fileName));
        try {
            return Long.parseLong(response);
        } catch (Exception e) {
            return -1L;
        }
    }

    public boolean downloadFile(String fileName) throws IOException {
        return isPositiveCompleteResponse(delegate.retr(fileName));
    }

    public boolean downloadFile(String serverPath, String localPath) throws IOException {
        OutputStream os = null;
        try {
            os = new FileOutputStream(new File(localPath));
            delegate.setFileType(FTP.BINARY_FILE_TYPE);
            delegate.retrieveFile(serverPath, os);
            return true;
        } catch (Exception e) {
            LOG.error(e, e);
            return false;
        } finally {
            IOUtils.closeQuietly(os);
        }
    }

    private int getServerReply() throws IOException {
        String rep = getFullServerReply();
        LOG.debug("reply:" + rep);
        return Integer.parseInt(rep.substring(0, 3));
    }

    public static String getServerReplyStr(FTPClient ftpClient) {

        StringBuilder sb = new StringBuilder();
        String[] replies = ftpClient.getReplyStrings();
        if (replies != null && replies.length > 0) {
            for (String reply : replies) {
                sb.append(reply);
            }
        }
        return sb.toString();
    }

    private String getFullServerReply() throws IOException {
        String[] replyStrings = delegate.getReplyStrings();

        for (String reply : replyStrings) {
            if (Character.isDigit(reply.charAt(0)) &&
                    Character.isDigit(reply.charAt(1)) &&
                    Character.isDigit(reply.charAt(2)) &&
                    reply.charAt(3) == ' ') {
                return reply;
            }
        }

        return null;
    }

    private String getFullServerReply(StringBuffer fullReply)
            throws IOException {
        String[] replyStrings = delegate.getReplyStrings();

        for (String reply : replyStrings) {
            fullReply.append(reply).append("\n");
            if (Character.isDigit(reply.charAt(0)) &&
                    Character.isDigit(reply.charAt(1)) &&
                    Character.isDigit(reply.charAt(2)) &&
                    reply.charAt(3) == ' ') {
                return reply;
            }
        }

        return null;
    }

    public String listFiles() throws IOException {
        return listFiles("");
    }

    public String listFiles(String params) throws IOException {
        StringBuffer files = new StringBuffer();
        StringBuffer dirs = new StringBuffer();

        getAndParseDirList(params, files, dirs);

        return files.toString();
    }

    public String listSubdirectories() throws IOException {
        return listSubdirectories("");
    }

    public String listSubdirectories(String params) throws IOException {
        StringBuffer files = new StringBuffer();
        StringBuffer dirs = new StringBuffer();

        getAndParseDirList(params, files, dirs);

        return dirs.toString();
    }

    private boolean getAndParseDirList(String params, StringBuffer files, StringBuffer dirs) throws IOException {
        // reset the return variables (we're using StringBuffers instead of
        // Strings because you can't change a String value and pass it back
        // to the calling routine -- changing a String creates a new object)
        files.setLength(0);
        dirs.setLength(0);

        // get the NLST and the LIST -- don't worry if the commands
        // don't work, because we'll just end up sending nothing back
        // if that's the case
        FTPFile[] ftpFiles = delegate.listFiles();
        for (FTPFile file : ftpFiles) {
            if (file.isFile()) {
                if (files.length() > 0) {
                    files.append("\n");
                }
                files.append(file.getName());
            }

            if (file.isDirectory()) {
                if (dirs.length() > 0) {
                    dirs.append("\n");
                }
                dirs.append(file.getName());
            }
        }

        return true;
    }

    public int executeCommand(String command) throws IOException {
        LOG.debug("ftp command: " + command);
        delegate.sendCommand(command);
        return getServerReply();
    }

    public String getExecutionResponse(String command) throws IOException {
        delegate.sendCommand(command);
        return getFullServerReply();
    }

    public void storeFile(String remoteFolder, InputStream is) throws Exception {
        if (!delegate.storeFile(remoteFolder, is)) {
            throw new Exception(getServerReplyStr(delegate));
        }
    }

    private boolean isPositivePreliminaryResponse(int response) {
        return (response >= 100 && response < 200);
    }

    private boolean isPositiveIntermediateResponse(int response) {
        return (response >= 300 && response < 400);
    }

    private boolean isPositiveCompleteResponse(int response) {
        return (response >= 200 && response < 300);
    }

    private boolean isTransientNegativeResponse(int response) {
        return (response >= 400 && response < 500);
    }

    private boolean isPermanentNegativeResponse(int response) {
        return (response >= 500 && response < 600);
    }

    private String excludeCode(String response) {
        if (response.length() < 5) {
            return response;
        }
        //return response.substring(4);
        return response.substring(4).trim();
    }

}
