package com.wiley.cms.cochrane.utils.zip;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.wiley.cms.cochrane.repository.RepositoryUtils;

/**
 * Type comments here.
 *
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @version 1.0
 */

public class ZipOutput extends AbstractZipOutput {
    private ZipOutputStream out;

    public ZipOutput(OutputStream os) {
        out = new ZipOutputStream(os);
    }

    public ZipOutput(File fl) throws FileNotFoundException {
        this(new FileOutputStream(fl));
    }

    public ZipOutput(String filePath) throws FileNotFoundException {
        this(new FileOutputStream(RepositoryUtils.createFile(filePath)));
    }

    public void put(String entryName, InputStream is) throws IOException {
        out.putNextEntry(new ZipEntry(entryName));

        // Transfer bytes from the file to the ZIP file
        int len;
        while ((len = is.read(buf)) > 0) {
            out.write(buf, 0, len);
        }

        // Complete the entry
        out.closeEntry();
        is.close();
    }

    public ZipOutputStream getZipOutputStream() {
        return out;
    }


    public void close() throws IOException {
        out.close();
    }
}

