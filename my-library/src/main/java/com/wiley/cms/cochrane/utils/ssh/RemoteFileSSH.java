package com.wiley.cms.cochrane.utils.ssh;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import com.wiley.tes.util.FileUtils;
import com.wiley.tes.util.Logger;

import ch.ethz.ssh2.ChannelCondition;
import ch.ethz.ssh2.Connection;
import ch.ethz.ssh2.Session;
import ch.ethz.ssh2.StreamGobbler;


/**
 * Access to the files on remote computer
 * <p/>
 * How to use:
 * <p/>
 * For single operation:
 * <p/>
 * host - server to connect
 * user, pass - SSH user name & password
 * <p/>
 * --------------------------------------------------------------------------
 * RemoteFileSSH        ssh = new RemoteFileSSH(host, user, pass);
 * <p/>
 * if (ssh.freeSpace() == ssh.ERROR_OK)
 * {
 * String output = ssh.getOutput();
 * }
 * else
 * {
 * System.err.println(ssh.getStatus();
 * System.err.println(ssh.getErrors();
 * }
 * --------------------------------------------------------------------------
 * <p/>
 * For many subsequent operations with the same host use setKeepConnection(true) -
 * it will substantially reduce time. Do not forget to call closeConnection() at the end.
 * <p/>
 * --------------------------------------------------------------------------
 * RemoteFileSSH        ssh = new RemoteFileSSH(host, user, pass);
 * <p/>
 * ssh.setKeepConnection(true);
 * <p/>
 * if (ssh.freeSpace() == ssh.ERROR_OK)
 * {
 * String output = ssh.getOutput();
 * }
 * else
 * {
 * System.err.println(ssh.getStatus();
 * System.err.println(ssh.getErrors();
 * }
 * <p/>
 * ssh.closeConnection();
 * --------------------------------------------------------------------------
 * <p/>
 * Supported operations:
 * <p/>
 * - delete(String name, String path)             Deletes file or folder
 * - delete(String[] names, String path)          Deletes group of files or folders
 * - expand(String name, String path)             Expands tar or qzip
 * - freespace(String path)                       Returns free space as String: getOutput()
 * - list(String path)                            Returns list of files within specified directory: getOutput()
 * - zip(String packageName, String path)         Zip directory to specified packageName
 *
 * @author <a href='mailto:mimalykh@wiley.com'>Michael Malykh</a>
 */
public class RemoteFileSSH {
    public static final int ERROR_OK = 0;
    public static final int ERROR_FAILED = -1;
    public static final int ERROR_PARAMS = -2;
    public static final int ERROR_AUTHENTICATION = -3;
    public static final int ERROR_CONNECTION = -4;

    private static final Logger LOG = Logger.getLogger(RemoteFileSSH.class);

    // gunzip [ -acfhlLnNrtvV ] [-S suffix] [ name ...    ]
    // http://man.yolinux.com/cgi-bin/man2html?cgi_command=gunzip&cgi_section=
    private static final String COMMAND_UNZIP = "gunzip %file%";
    // http://www.gnu.org/software/tar/manual/html_node/extracting-archives.html#SEC26
    private static final String COMMAND_UNTAR = "tar -xf %file%";
    /**
     * Decompress and extract a tar.gz archive keeping original file unchanged.
     * Solaris OS realization of tar program doesn't support -z option
     * that's why we have to use this commands instead of the command 'tar -zxf'
     */
    private static final String COMMAND_EXTRACT_TGZ = "gunzip -c %file% | tar -xf -";

    // rm -R -f     Remove all directories and files below given directory
    // -r, -R, --recursive:  remove directories and their contents recursively
    // -f, --force:  ignore nonexistent files, never prompt
    private static final String COMMAND_DELETE = "rm -R %file%";

    // df [OPTION]... [FILE]...
    // -h, --human-readable: print sizes in human readable format (e.g., 1K 234M 2G)
    private static final String COMMAND_FREESPACE = "df -h %dir%";
    private static final String COMMAND_LN = "ln -s %file% %link%";
    private static final String COMMAND_MKDIR = "mkdir %dir%";
    private static final String COMMAND_FIND = "find %dir% %params%";
    private static final String COMMAND_TGZ = "tar -cf %file% .; gzip %file%";
    private static final String COMMAND_RENAME = "mv %old% %new%";
    private static final String COMMAND_SCP = "scp %file1% %file2%";
    private static final String COMMAND_TOUCH = "touch %file%";

    // cd [DIR]...
    private static final String COMMAND_CHANGEDIR = "cd %dir%";
    private static final int TIMEOUT = 2000;
    private static final String FILE_MARKER = "%file%";

    private boolean keepConnection = false;

    private String hostname = "127.0.0.1";
    private String username = "user";
    private String password = "pass";
    private String keyPath = ".";

    private Session session = null;
    private Connection connection = null;

    private StringBuffer errors = new StringBuffer();
    private StringBuffer output = new StringBuffer();
    private int status = ERROR_OK;

    public RemoteFileSSH(String hostname, String username, String password, String keyPath) {
        this.hostname = hostname;
        this.username = username;
        this.password = password;
        this.keyPath = keyPath;
    }

    public RemoteFileSSH(String hostname, String username, String password) {
        this(hostname, username, password, null);
    }

    /**
     * Sets if it is necessary to keep connection after operation is done
     *
     * @param keepConnection if <b>false</b> (default) connections will be closed automatically
     *                       after each operation, if <b>true</b>  user of this class is responsible
     *                       for close connection explicitly
     */
    public void setKeepConnection(boolean keepConnection) {
        this.keepConnection = keepConnection;
    }

    public boolean getKeepConnection() {
        return keepConnection;
    }

    public int getStatus() {
        return status;
    }

    /**
     * Returns errors from remote computer
     *
     * @return Errors as one string
     */
    public String getErrors() {
        return errors.toString();
    }

    /**
     * Returns output from remote computer
     *
     * @return Output as one string
     */
    public String getOutput() {
        return output.toString();
    }

    /**
     * Deletes file or folder on remote computer
     *
     * @param name file name, could be either absolute or relative to the <b>path</b> value
     * @param path path to the file, can be null if <code>name</code> contains full path
     * @return ERROR_OK if operation completed successfully. To get additional information
     *         call getStatus()/getErrors()
     */
    public int delete(String name, String path) {

        resetStatus();

        if (name == null) {
            status = ERROR_PARAMS;
            return status;
        }

        try {
            openConnection(false);
            processCommand(COMMAND_DELETE.replace("%file%", buildPath(name, path)), null, null, false);
        } catch (Exception e) {
            errors.append(e.toString());
        } finally {
            if (!keepConnection) {
                closeConnection();
            }
        }

        return status;
    }

    /**
     * Deletes array of files on remote computer
     *
     * @param names array of file names, could be either absolute or relative to the <b>path</b> value
     * @param path  path to the files, can be null if <code>names[]</code> contains full path
     * @return ERROR_OK if operation completed successfully. To get additional information
     *         call getStatus()/getErrors()
     */
    public int delete(String[] names, String path) {

        resetStatus();

        if (names == null) {
            status = ERROR_PARAMS;
            return status;
        }

        try {
            openConnection(false);

            for (String name : names) {
                processCommand(COMMAND_DELETE.replace(FILE_MARKER, buildPath(name, path)), null, null);
            }

        } catch (Exception e) {
            errors.append(e.toString());
        } finally {
            if (!keepConnection) {
                closeConnection();
            }
        }

        return status;
    }

    /**
     * Expands tar file on remote computer keeping the tar file unchanged
     *
     * @param name      file name, could be either absolute or relative to the <b>path</b> value
     * @param expandDir where to expand relative tar entries
     * @return ERROR_OK if operation completed successfully. To get additional information
     *         call getStatus()/getErrors()
     */
    public int expand(String name, String expandDir) {

        resetStatus();

        if (name == null) {
            status = ERROR_PARAMS;
            return status;
        }

        try {
            String dir = expandDir;
            String cmd;

            boolean connected = openConnection(false);

            if (connected) {
                if (dir == null || dir.length() == 0) {
                    int separator = name.lastIndexOf("/");

                    if (separator > 0) {
                        dir = name.substring(0, separator);
                    }
                }

                cmd = COMMAND_CHANGEDIR.replace("%dir%", dir) + "; " + COMMAND_EXTRACT_TGZ.replace(FILE_MARKER, name);
                processCommand(cmd, null, null);
            }
        } catch (Exception e) {
            errors.append(e.toString());
        } finally {
            if (!keepConnection) {
                closeConnection();
            }
        }

        return status;
    }

    /**
     * Requests information free space on remote computer.
     * To get information call getOutput() if call returns ERROR_OK
     *
     * @param path path where to check free space
     * @return operation status
     */
    public int freespace(String path) {

        resetStatus();

        if (path == null) {
            status = ERROR_PARAMS;
            return status;
        }

        try {
            openConnection(false);
            processCommand(COMMAND_FREESPACE.replace("%dir%", path), null, null);
        } catch (Exception e) {
            errors.append(e.toString());
        } finally {
            if (!keepConnection) {
                closeConnection();
            }
        }

        return status;
    }

    /**
     * Creates symbolic link on remote computer.
     * To get information call getOutput() if call returns ERROR_OK
     *
     * @param filePath absolute path to file
     * @param linkPath absolute path to directory, where symbolic link will be created
     * @return operation status
     */
    public int createSymbolicLink(String filePath, String linkPath) {

        resetStatus();

        if (filePath == null) {
            status = ERROR_PARAMS;
            return status;
        }

        try {
            openConnection(false);
            processCommand(
                    COMMAND_LN.replace("%file%", filePath).replace("%link%", linkPath), null, null);
        } catch (Exception e) {
            errors.append(e.toString());
        } finally {
            if (!keepConnection) {
                closeConnection();
            }
        }

        return status;
    }

    /**
     * Creates new directory on remote computer.
     * To get information call getOutput() if call returns ERROR_OK
     *
     * @param dir path to directory
     * @return operation status
     */
    public int makeDir(String dir) {

        resetStatus();

        if (dir == null) {
            status = ERROR_PARAMS;
            return status;
        }

        try {
            openConnection(false);
            processCommand(COMMAND_MKDIR.replace("%dir%", dir), null, null);
        } catch (Exception e) {
            errors.append(e.toString());
        } finally {
            if (!keepConnection) {
                closeConnection();
            }
        }

        return status;
    }

    /**
     * Performs 'find' command.
     * To get information call getOutput() if call returns ERROR_OK
     *
     * @param dir    path to directory
     * @param params additional parameters
     * @return operation status
     */
    public int find(String dir, String params) {
        resetStatus();

        if (dir == null) {
            status = ERROR_PARAMS;
            return status;
        }

        try {
            openConnection(false);
            processCommand(COMMAND_FIND.replace("%dir%", dir).replace("%params%", params), null, null);
        } catch (Exception e) {
            errors.append(e.toString());
        } finally {
            if (!keepConnection) {
                closeConnection();
            }
        }

        return status;
    }


    /**
     * Returns list of files on remote computer
     * To get information call getOutput() if call returns ERROR_OK
     *
     * @param path directory to list
     * @return operation status
     */
    public int list(String path) {

        resetStatus();

        if (path == null) {
            status = ERROR_PARAMS;
            return status;
        }

        try {
            openConnection(false);
            processCommand(COMMAND_CHANGEDIR.replace("%dir%", path) + "; dir", null, null);
        } catch (Exception e) {
            errors.append(e.toString());
        } finally {
            if (!keepConnection) {
                closeConnection();
            }
        }

        return status;
    }

    public int touch(String filePath) {
        resetStatus();

        if (filePath == null) {
            status = ERROR_PARAMS;
            return status;
        }

        try {
            openConnection(false);
            processCommand(COMMAND_TOUCH.replace("%file%", filePath), null, null);
        } catch (Exception e) {
            errors.append(e.toString());
        } finally {
            if (!keepConnection) {
                closeConnection();
            }
        }

        return status;
    }

    public int rename(String oldPath, String newPath) {
        resetStatus();

        if (oldPath == null || newPath == null) {
            status = ERROR_PARAMS;
            return status;
        }

        try {
            openConnection(false);
            processCommand(COMMAND_RENAME.replace("%old%", oldPath).replace("%new%", newPath), null, null);
        } catch (Exception e) {
            errors.append(e.toString());
        } finally {
            if (!keepConnection) {
                closeConnection();
            }
        }

        return status;
    }

    public int sendFile(String localPath, String remotePath) {
        resetStatus();

        if (localPath == null || remotePath == null) {
            status = ERROR_PARAMS;
            return status;
        }

        try {
            openConnection(false);
            String command = COMMAND_SCP.replace("%file1%", localPath).replace("%file2%", remotePath);
            processCommand(command, null, null);
        } catch (Exception e) {
            errors.append(e.toString());
        } finally {
            if (!keepConnection) {
                closeConnection();
            }
        }

        return status;
    }

    /**
     * Zip given directory to a specified package name
     *
     * @param packageName path to result package
     * @param path        directory to zip
     * @return operation status
     */
    public int createTarGz(String packageName, String path) {
        resetStatus();

        if (packageName == null || path == null) {
            status = ERROR_PARAMS;
            return status;
        }

        try {
            openConnection(false);
            String command =
                    COMMAND_CHANGEDIR.replace("%dir%", path) + "; " + COMMAND_TGZ.replace("%file%", packageName);
            processCommand(command, null, null);
        } catch (Exception e) {
            errors.append(e.toString());
        } finally {
            if (!keepConnection) {
                closeConnection();
            }
        }

        return status;
    }

    public boolean openConnection(boolean force) throws IOException {

        if (connection != null && !force) {
            return true;
        }

        closeConnection();

        try {
            /* Create a connection instance */

            connection = new Connection(hostname);

            /* Now connect */
            connection.connect();

            /* Authenticate. */
            boolean isAuthenticated = keyPath != null && connection.authenticateWithPublicKey(
                    username, new File(keyPath), null);
            if (!isAuthenticated) {
                isAuthenticated = connection.authenticateWithPassword(username, password);
            }

            if (!isAuthenticated) {
                status = ERROR_AUTHENTICATION;
                errors.append("Authentication failed.");
            }

        } catch (IOException e) {
            status = ERROR_CONNECTION;
            errors.append(e.toString());
            closeConnection();
        }

        return (connection != null);
    }


    public void closeConnection() {
        try {
            if (session != null) {
                session.close();
            }

            if (connection != null) {
                connection.close();
            }
        } catch (Exception ignore) {
            ;
        } finally {
            session = null;
            connection = null;
        }
    }

    private int processCommand(String command, StringBuffer cmdOutput, StringBuffer cmdErrors, boolean isLoggingEnabled) {
        if (isLoggingEnabled) {
            LOG.debug(command);
        }
        if (command == null || connection == null) {
            return ERROR_FAILED;
        }
        try {
            session = connection.openSession();
            session.execCommand(command);

            InputStream stdout = new StreamGobbler(session.getStdout());
            InputStream stderr = new StreamGobbler(session.getStderr());

            BufferedReader stdoutReader = new BufferedReader(new InputStreamReader(stdout));
            BufferedReader stderrReader = new BufferedReader(new InputStreamReader(stderr));

            //handleOutputStream(cmdOutput, stdoutReader);
            //handleErrorStream(cmdErrors, stderrReader);
            FileUtils.handleOutputStream(cmdOutput, stdoutReader, output);
            FileUtils.handleOutputStream(cmdErrors, stderrReader, errors);

            int conditions = ChannelCondition.TIMEOUT;
            while (conditions == ChannelCondition.TIMEOUT) {
                conditions = session.waitForCondition(ChannelCondition.EXIT_STATUS | ChannelCondition.CLOSED, TIMEOUT);
            }
            status = session.getExitStatus();

        } catch (IOException e) {
            errors.append(e.toString());
            status = ERROR_FAILED;

        } finally {
            if (session != null) {
                session.close();
            }
            session = null;
        }
        return status;
    }

    private int processCommand(String command, StringBuffer cmdOutput, StringBuffer cmdErrors) {
        return this.processCommand(command, cmdOutput, cmdErrors, true);
    }

    /*private void handleErrorStream(StringBuffer cmdErrors,
                                   BufferedReader stderrReader) throws IOException {
        while (true) {
            String line = stderrReader.readLine();
            if (line == null) {
                break;
            }

            errors.append(line);
        }

        if (cmdErrors != null) {
            cmdErrors.append(errors);
        }
    }*/

    /*private void handleOutputStream(StringBuffer cmdOutput,
                                    BufferedReader stdoutReader) throws IOException {
        while (true) {
            String line = stdoutReader.readLine();
            if (line == null) {
                break;
            }

            output.append(line).append(" ");
        }

        if (cmdOutput != null) {
            cmdOutput.append(output);
        }
    }*/

    private String buildPath(String name, String path) {
        if (path == null || path.length() == 0) {
            return name;
        }

        return path.lastIndexOf("/") != (path.length() - 1) ? path + "/" + (name == null ? ""
                : name) : path + (name == null ? "" : name);
    }

    private void resetStatus() {
        status = ERROR_OK;
        errors.setLength(0);
        output.setLength(0);
    }

}


