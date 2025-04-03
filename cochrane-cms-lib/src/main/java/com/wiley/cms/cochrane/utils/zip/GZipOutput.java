package com.wiley.cms.cochrane.utils.zip;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.GZIPOutputStream;

import org.apache.tools.tar.TarEntry;
import org.apache.tools.tar.TarOutputStream;

import com.wiley.cms.cochrane.cmanager.publish.exception.PublishException;
import com.wiley.cms.cochrane.repository.RepositoryUtils;
import com.wiley.tes.util.Logger;

/**
 * Type comments here.
 *
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @version 1.0
 */
public class GZipOutput extends AbstractZipOutput {
    private static final int R755 = 0100755;

    Logger log = Logger.getLogger(GZipOutput.class);

    private TarOutputStream tarOut;

    public GZipOutput(String filePath) throws PublishException {
        this(filePath, true);
    }

    public GZipOutput(String filePath, boolean isGzip) throws PublishException {
        this (RepositoryUtils.createFile(filePath), isGzip);
    }

    public GZipOutput(File file, boolean isGzip) throws PublishException {
        try {
            if (isGzip) {
                tarOut = new TarOutputStream(new GZIPOutputStream(new FileOutputStream(file)));
            } else {
                tarOut = new TarOutputStream(new FileOutputStream(file));
            }
        } catch (IOException e) {
            throw new PublishException(e);
        }
    }

    public GZipOutput(String filePath, boolean isgzip, boolean longPaths) throws PublishException {
        this (filePath, isgzip);
        if (longPaths) {
            tarOut.setLongFileMode(TarOutputStream.LONGFILE_GNU);
        }
    }

    public GZipOutput(OutputStream os, boolean isgzip) throws IOException {
        if (isgzip) {
            tarOut = new TarOutputStream(new GZIPOutputStream(os));
        } else {
            tarOut = new TarOutputStream(os);
        }
    }

    public void put(String entryName, InputStream is) throws IOException {
        ByteArrayOutputStream baos = getBytes(is);
        TarEntry tarAdd = new TarEntry(entryName);
        tarAdd.setMode(R755);
        tarAdd.setSize(baos.size());
        tarOut.putNextEntry(tarAdd);
        tarOut.write(baos.toByteArray());
        tarOut.closeEntry();
        baos.close();
    }

    private ByteArrayOutputStream getBytes(InputStream is) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int len;
        while ((len = is.read(buf)) > 0) {
            baos.write(buf, 0, len);
        }
        return baos;
    }

    public void close() throws IOException {
        tarOut.close();
    }
}
