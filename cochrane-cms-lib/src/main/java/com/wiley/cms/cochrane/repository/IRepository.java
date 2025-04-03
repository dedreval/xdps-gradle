package com.wiley.cms.cochrane.repository;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import javax.imageio.stream.ImageOutputStream;

/**
 * The Cochrane Repository interface.
 *
 * @author <a href='mailto:ademidov@wiley.ru'>Andrey Demidov</a>
 * @version 1.0
 */
public interface IRepository {
    /**
     * Create new or replace file in the Repository
     *
     * @param uri   Repository URI
     * @param data  InputStream with file data
     * @param close close data InputStream after loading or not
     * @throws IOException
     */
    void putFile(String uri, InputStream data, boolean close) throws IOException;

    /**
     * Create new or replace file in the Repository
     * and close data InputStream
     *
     * @param uri  Repository URI
     * @param data InputStream with file data
     * @throws IOException
     */
    void putFile(String uri, InputStream data) throws IOException;

    /**
     * Get file length from the Repository
     *
     * @param uri Repository URI
     * @return return file length or IOException
     * @throws IOException
     */
    long getFileLength(String uri) throws IOException;

    long getFileLastModified(String uri) throws IOException;

    /**
     * Get file from the Repository
     *
     * @param uri Repository URI
     * @return return InputStream with file data (its must be closed) or IOException
     * @throws IOException
     */
    InputStream getFile(String uri) throws IOException;

    /**
     * Get file bytes from the Repository
     *
     * @param uri Repository URI
     * @return return data byte array or IOException
     * @throws IOException
     */
    byte[] getFileAsByteArray(String uri) throws IOException;

    /**
     * Delete file from the Repository
     *
     * @param uri Repository URI
     * @throws IOException
     */
    void deleteFile(String uri) throws IOException;

    /**
     * Check if file exists in the Repository
     * @param uri Repository URI
     * @return false in case specified file doesn't exist or something goes wrong and true otherwise
     */
    boolean isFileExistsQuiet(String uri);

    /**
     * Check if file exists in the Repository
     *
     * @param uri Repository URI
     * @throws IOException
     */
    boolean isFileExists(String uri) throws IOException;

    /**
     * Rename existing file in the Repository
     *
     * @param oldName old file name
     * @param newName new file name
     * @return
     */
    boolean renameFile(String oldName, String newName);


    void deleteDir(String packagePath) throws Exception;

    String getRepositoryPlace();

    File[] getFilesFromDir(String uri);


    String getRealFilePath(String uri);

    void putImage(BufferedImage img, String type, String path) throws IOException;

    ImageOutputStream getImageOutputStream(String path) throws IOException;
}
