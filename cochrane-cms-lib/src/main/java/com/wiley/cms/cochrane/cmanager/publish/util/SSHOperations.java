package com.wiley.cms.cochrane.cmanager.publish.util;

import java.util.Arrays;

import com.wiley.cms.cochrane.cmanager.CmsException;
import com.wiley.cms.cochrane.cmanager.CochraneCMSProperties;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.res.ServerType;
import com.wiley.cms.cochrane.utils.ssh.RemoteFileSSH;
import com.wiley.tes.util.Logger;
import com.wiley.tes.util.UserFriendlyMessageBuilder;
import com.wiley.tes.util.res.Res;

/**
 * @author <a href='mailto:ikirov@wiley.com'>Igor Kirov</a>
 * @version 10.11.11
 */
public class SSHOperations {
    private static final Logger LOG = Logger.getLogger(SSHOperations.class);
    private static final String NO_SUCH_FILE_OR_DIRECTORY = "No such file or directory";
    private static final String TO = " to ";
    private static final String FOLDER = "Folder ";
    private static final String FIND_PARAM = "-type f";

    private SSHOperations() {
    }

    public static boolean isFileExists(String path, String serverName, String serverLogin, String serverPassword) {
        RemoteFileSSH ssh = null;

        try {
            ssh = new RemoteFileSSH(serverName, serverLogin, serverPassword,
                    CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.SERVER_PRIVATE_KEY));
            ssh.setKeepConnection(true);

            int status = ssh.find(path, " | wc -l");

            int res = 0;

            if (status == RemoteFileSSH.ERROR_OK) {
                res = Integer.parseInt(ssh.getOutput().trim());
            }

            if (res != 1) {
                return false;
            }
        } finally {
            if (ssh != null) {
                ssh.closeConnection();
            }
        }
        return true;
    }

    public static boolean isPathExists(String path, String serverName, String serverLogin, String serverPassword) {
        RemoteFileSSH ssh = null;

        try {
            ssh = new RemoteFileSSH(serverName, serverLogin, serverPassword,
                    CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.SERVER_PRIVATE_KEY));
            ssh.setKeepConnection(true);

            return ssh.find(path, FIND_PARAM) == RemoteFileSSH.ERROR_OK;

        } finally {
            if (ssh != null) {
                ssh.closeConnection();
            }
        }
    }

    public static void rename(String oldPath, String newPath, String serverName, String serverLogin,
                              String serverPassword) throws Exception {
        RemoteFileSSH ssh = null;

        try {
            ssh = new RemoteFileSSH(serverName, serverLogin, serverPassword,
                    CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.SERVER_PRIVATE_KEY));
            ssh.setKeepConnection(true);

            int status = ssh.find(oldPath, " -prune | wc -l");
            boolean exist = false;
            if (status == RemoteFileSSH.ERROR_OK) {
                exist = Integer.parseInt(ssh.getOutput().trim()) == 1;
            }

            if (exist && (ssh.rename(oldPath, newPath) != RemoteFileSSH.ERROR_OK)) {
                throw new Exception(
                        "Unable to rename directory: " + oldPath + TO + newPath + ". Details: " + ssh.getErrors());
            }
        } finally {
            if (ssh != null) {
                ssh.closeConnection();
            }
        }
    }

    public static int countRecords(String path, String params, String serverName, String serverLogin,
                                   String serverPassword) {
        RemoteFileSSH ssh = null;
        Integer res = null;

        try {
            ssh = new RemoteFileSSH(serverName, serverLogin, serverPassword,
                    CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.SERVER_PRIVATE_KEY));

            LOG.debug("running find: find " + path + " " + params);

            int status = ssh.find(path, params);

            if (status == RemoteFileSSH.ERROR_OK) {
                try {
                    res = Integer.valueOf(ssh.getOutput().trim());
                } catch (NumberFormatException e) {
                    LOG.error("Cannot count number of updated records. Error details: ", e);
                }
            }

            return res;
        } finally {
            if (ssh != null) {
                ssh.closeConnection();
            }
        }
    }

    public static void unpackPackage(String path, String packageFileName, String serverPath, String serverName,
                                     String serverLogin, String serverPassword) throws Exception {
        RemoteFileSSH ssh = null;

        try {
            ssh = new RemoteFileSSH(serverName, serverLogin, serverPassword,
                    CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.SERVER_PRIVATE_KEY));
            ssh.setKeepConnection(true);

            String pathToPkg = serverPath + "/" + packageFileName;
            LOG.info("Unpacking package: " + pathToPkg + " to: " + path);

            int status = ssh.expand(pathToPkg, path);

            if (status != RemoteFileSSH.ERROR_OK) {
                throw new Exception(
                        "Cannot unpack package " + packageFileName + ". Error details: " + ssh.getErrors());
            }
        } finally {
            if (ssh != null) {
                ssh.closeConnection();
            }
        }
    }

    private static boolean isPathAllowed(String path) {
        return path.endsWith("articles/") || path.endsWith("tar/") || path.endsWith("new/") || path.endsWith("titles/")
                || path.endsWith("old/");
    }

    public static void deleteFolders(String[] names, String path, String serverName, String serverLogin,
                                     String serverPassword) throws Exception {
        RemoteFileSSH ssh = null;

        try {
            ssh = new RemoteFileSSH(serverName, serverLogin, serverPassword,
                    CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.SERVER_PRIVATE_KEY));
            ssh.setKeepConnection(true);

            String fixedPath = path.endsWith("/") ? path : path.concat("/");

            /* try to prevent deletion of accidental folder */
            if (!isPathAllowed(fixedPath)) {
                throw new Exception("Unacceptable folder to delete: " + fixedPath);
            }

            LOG.info("Removing folder(s): " + (names[0].equals("") ? fixedPath
                    : Arrays.toString(names) + " in " + fixedPath));

            for (String name : names) {
                if (ssh.delete(name, fixedPath) != RemoteFileSSH.ERROR_OK) {
                    if (ssh.getErrors().contains(NO_SUCH_FILE_OR_DIRECTORY)) {
                        LOG.error(FOLDER + fixedPath + name + " was not deleted: "
                                + ssh.getErrors());
                    } else {
                        throw new Exception(FOLDER + fixedPath + name
                                + " was not deleted. Error details: " + ssh.getErrors());
                    }
                }
            }
        } finally {
            if (ssh != null) {
                ssh.closeConnection();
            }
        }
    }

    public static void mkdir(String path, String serverName, String serverLogin, String serverPassword)
        throws Exception {
        RemoteFileSSH ssh = null;

        try {
            ssh = new RemoteFileSSH(serverName, serverLogin, serverPassword,
                    CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.SERVER_PRIVATE_KEY));

            if (ssh.makeDir(path) != RemoteFileSSH.ERROR_OK) {
                throw new Exception("Cannot create directory: " + ssh.getErrors());
            }
        } finally {
            if (ssh != null) {
                ssh.closeConnection();
            }
        }
    }

    public static void recreateLinks(String path, String[] symbolicLinksList, String serverName, String serverLogin,
                                     String serverPassword) throws Exception {
        RemoteFileSSH ssh = null;

        try {
            ssh = new RemoteFileSSH(serverName, serverLogin, serverPassword,
                    CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.SERVER_PRIVATE_KEY));
            ssh.setKeepConnection(true);

            for (String lp : symbolicLinksList) {
                if (ssh.createSymbolicLink(lp, path) != RemoteFileSSH.ERROR_OK) {
                    throw new Exception("Cannot create symbolic link. Error details: " + ssh.getErrors());
                }
            }
        } finally {
            if (ssh != null) {
                ssh.closeConnection();
            }
        }
    }

    public static void touch(String path, String serverName, String serverLogin, String serverPassword)
        throws Exception {

        RemoteFileSSH ssh = null;
        try {
            ssh = new RemoteFileSSH(serverName, serverLogin, serverPassword,
                    CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.SERVER_PRIVATE_KEY));
            ssh.setKeepConnection(false);

            if (ssh.touch(path) != RemoteFileSSH.ERROR_OK) {
                throw new Exception("Cannot create file on remote server. Error details: " + ssh.getErrors());
            }
        } finally {
            if (ssh != null) {
                ssh.closeConnection();
            }
        }
    }

    public static void createTarGz(String path, String packageName, String serverName, String serverLogin,
                                   String serverPassword) throws Exception {
        RemoteFileSSH ssh = null;

        try {
            ssh = new RemoteFileSSH(serverName, serverLogin, serverPassword,
                    CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.SERVER_PRIVATE_KEY));

            if (ssh.find(path, FIND_PARAM) != RemoteFileSSH.ERROR_OK) {
                throw new Exception("Failed to make .tar.gz package:  " + ssh.getErrors());
            }

            if (ssh.createTarGz(packageName, path) != RemoteFileSSH.ERROR_OK) {
                if (ssh.getErrors().contains("Nothing to do!")) {
                    LOG.error(".tar.gz package has not been created: " + ssh.getErrors());
                } else {
                    throw new Exception("Failed to make .tar.gz package: " + ssh.getErrors());
                }
            }
        } finally {
            if (ssh != null) {
                ssh.closeConnection();
            }
        }
    }

    public static String[] listFiles(String path, String serverName, String serverLogin, String serverPassword)
        throws Exception {

        RemoteFileSSH ssh = null;

        try {
            ssh = new RemoteFileSSH(serverName, serverLogin, serverPassword,
                    CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.SERVER_PRIVATE_KEY));

            if (ssh.find(path, FIND_PARAM) != RemoteFileSSH.ERROR_OK) {
                throw new Exception("Cannot access directory: " + ssh.getErrors());
            } else {
                LOG.info(ssh.getOutput());
                return ssh.getOutput().trim().split(" ");
            }
        } finally {
            if (ssh != null) {
                ssh.closeConnection();
            }
        }
    }

    public static void send(String localPath, String remotePath) throws Exception {
        RemoteFileSSH ssh = null;

        //String cmsServerName = CochraneCMSProperties.getProperty("cochrane.cms.server.name");
        //String cmsServerLogin = CochraneCMSProperties.getProperty("cochrane.cms.server.login");
        //String cmsServerPassword = CochraneCMSProperties.getProperty("cochrane.cms.server.password");

        Res<ServerType> st = ServerType.find(ServerType.LOCALHOST);
        if (!Res.valid(st)) {
            throw new CmsException("cannot set SSH connection because localhost credentials are not set");
        }
        ServerType localHost = st.get();
        try {
            ssh = new RemoteFileSSH(localHost.getHost(), localHost.getUser(), localHost.getPassword(),
                    CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.SERVER_PRIVATE_KEY));

            if (ssh.sendFile(localPath, remotePath) != RemoteFileSSH.ERROR_OK) {
                throw new Exception("Cannot send file from " + localPath + TO + remotePath + " Error details: "
                        + UserFriendlyMessageBuilder.build(ssh.getErrors()));
            }
        } finally {
            if (ssh != null) {
                ssh.closeConnection();
            }
        }
    }
}
