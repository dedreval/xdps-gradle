package com.wiley.tes.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.io.IOUtils;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 26.09.13
 */
public class FileUtils {
    public static final File[] EMPTY_DIR = new File[0];
    public static final String[] ZERO_STRING_ARRAY = new String[0];

    static final int BUFFER_SIZE = 8192;

    private static final Logger LOG = Logger.getLogger(FileUtils.class);

    private static final String REPORT_NO_DIR_FOUND = "record's directory is not found";
    private static final int ERROR_CODE_OK = 0;
    private static final int ERROR_CODE_OK_139 = 139;
    private static final int ERROR_CODE_1 = 1;

    private static final Set<String> IMAGE_EXTENSIONS = new HashSet<>();

    static {
        IMAGE_EXTENSIONS.add(Extensions.BMP);
        IMAGE_EXTENSIONS.add(Extensions.PNG);
        IMAGE_EXTENSIONS.add(Extensions.JPEG);
        IMAGE_EXTENSIONS.add(Extensions.JPG);
        IMAGE_EXTENSIONS.add(Extensions.GIF);
        IMAGE_EXTENSIONS.add(Extensions.SVG);
    }

    private FileUtils() {
    }

    public static boolean isGraphicExtension(String ext) {
        return IMAGE_EXTENSIONS.contains(ext);
    }

    public static long getFileSize(String realFilePath) {
        File fl = new File(realFilePath);
        return fl.exists() ? fl.length() : 0;
    }

    public static boolean renameFile(File oldFile, File newFile) {
        if (newFile.exists()) {
            newFile.delete();
        }
        return oldFile.renameTo(newFile);
    }

    public static String readStream(URL url) throws IOException {
        return readStream(url, null);
    }

    public static String readStream(URI uri) throws IOException {
        return readStream(uri, null);
    }

    public static String readStream(URI uri, String separator) throws IOException {
        return readStream(uri.toURL(), separator);
    }

    public static String readStream(URL url, String separator) throws IOException {
        BufferedReader is = null;
        InputStream uriStream = null;
        StringBuilder dataStr = new StringBuilder(BUFFER_SIZE);
        try {
            uriStream = url.openConnection().getInputStream();
            is = new BufferedReader(new InputStreamReader(uriStream, StandardCharsets.UTF_8));
            String read;
            while ((read = is.readLine()) != null) {
                dataStr.append(read);
                if (separator != null) {
                    dataStr.append(separator);
                }
            }

        } finally {
            IOUtils.closeQuietly(uriStream);
            IOUtils.closeQuietly(is);
        }
        return dataStr.toString();
    }

    public static String cutExtension(String fileName) {
        return fileName.substring(0, fileName.lastIndexOf('.'));
    }

    public static String cutExtension(String fileName, String ext) {
        return fileName.substring(0, fileName.length() - ext.length());
    }

    public static String buildFilePaths(String basePath, String folder, StringBuilder res) {
        File dir;
        try {
            dir = new File(new URI(basePath + folder));
        } catch (URISyntaxException e) {
            LOG.error(e.getMessage() + basePath + folder);
            return "couldn't define file's uri";
        }

        String ret = null;
        if (!dir.exists()) {
            LOG.error(REPORT_NO_DIR_FOUND + ": " + dir);
            ret = REPORT_NO_DIR_FOUND;
        } else {
            buildFilePaths(basePath, folder, res, dir);
        }
        return ret;
    }

    private static void buildFilePaths(String basePath, String folder, StringBuilder res, File dir) {

        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (!file.getName().equals("..") && !file.getName().equals(".")) {
                if (file.isDirectory()) {
                    buildFilePaths(basePath, folder + "/" + file.getName(), res);
                } else {
                    res.append("<file url=\"" + "/").append(folder).append("/").append(file.getName()).append("\"/>");
                }
            }
        }
    }

    public static void clearDirectory(String dirPath) {
        try {
            File dir = new File(new URI(dirPath));
            if (dir.exists()) {
                deleteDirs(dir, false);
            }
        } catch (URISyntaxException e) {
            LOG.error(e, e);
        }
    }

    public static void deleteDirectory(String dirPath) {
        try {
            File dir = new File(new URI(dirPath));
            if (dir.exists()) {
                deleteDirs(dir);
            }
        } catch (URISyntaxException e) {
            LOG.error(e, e);
        }
    }

    public static void deleteDirs(File file, boolean deleteMainDir) {
        deleteDirs(file, deleteMainDir, false);
    }

    public static boolean deleteDirs(File file, boolean deleteMainDir, boolean onlyEmptyDir) {

        File[] list = file.listFiles();
        boolean ret = true;
        if (list == null) {
            if (deleteMainDir) {
                ret = delete(file);
            }
            return ret;
        }

        for (File fl : list) {
            if (fl.isDirectory()) {
                ret = deleteDirs(fl, true, onlyEmptyDir) && ret;

            } else if (!onlyEmptyDir)  {
                ret = delete(fl) && ret;

            } else {
                ret = false;
            }
        }
        if (ret && onlyEmptyDir || deleteMainDir) {
            ret = delete(file);
        }
        return ret;
    }

    private static boolean delete(File file) {
        if (file.exists() && !file.delete()) {
            if (file.exists()) {
                LOG.debug("Couldn't delete " + file.getAbsolutePath());
                return false;
            }
        }
        return true;
    }

    public static void deleteDirs(File file) {
        deleteDirs(file, true);
    }

    public static void copyFileByCommand(String sourceFile, String destFile) throws Exception {
        String command = String.format("cp %s %s", sourceFile, destFile);
        LOG.debug(command);
        Process process = Runtime.getRuntime().exec(command);
        StringBuffer err = new StringBuffer();
        checkFinishing(process, new StringBuffer(), err);
        if (err.length() > 0) {
            throw new Exception(err.toString());
        }
    }

    public static int execCommand(String command) throws Exception {
        Process process = Runtime.getRuntime().exec(command);
        return checkFinishing(process);
    }

    public static int execCommand(String command, File dir) throws Exception {
        Process process = Runtime.getRuntime().exec(command, null, dir);
        return checkFinishing(process);
    }

    public static boolean isCodeWarning(int errorCode) {
        return errorCode == ERROR_CODE_1;
    }

    public static boolean isCodeError(int errorCode) {
        return errorCode > ERROR_CODE_OK && errorCode != ERROR_CODE_OK_139;
    }

    public static void handleOutputStream(StringBuffer exOutput, BufferedReader stdoutReader, StringBuffer output)
        throws IOException {
        while (true) {
            String line = stdoutReader.readLine();
            if (line == null) {
                break;
            }
            output.append(line).append(" ");
        }

        if (exOutput != null) {
            exOutput.append(output);
        }
    }

    private static void checkFinishing(Process process, StringBuffer out, StringBuffer err) {
        try {
            int exitCode = process.waitFor();
            if (isCodeError(exitCode)) {

                BufferedReader stdoutReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                BufferedReader stderrReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                handleOutputStream(null, stdoutReader, out);
                handleOutputStream(null, stderrReader, err);

                if (err.length() == 0) {
                    err.append(String.format("it's failed with exit code %d", exitCode));
                }
            }
        } catch (Exception ie) {
            err.append(ie.getMessage());
        }
        process.destroy();
    }

    private static int checkFinishing(Process process) throws IOException {
        int exitCode = -1;
        while (true) {
            try {
                exitCode = process.exitValue();
                break;
            } catch (Exception ex) {
                byte[] buf = new byte[process.getInputStream().available()];
                process.getInputStream().read(buf);
                byte[] buf2 = new byte[process.getErrorStream().available()];
                process.getErrorStream().read(buf2);
            }
        }
        process.destroy();
        return exitCode;
    }
}
