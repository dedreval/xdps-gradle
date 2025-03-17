package com.wiley.cms.cochrane.utils.zip;

import java.io.IOException;
import java.io.InputStream;

/**
 * Type comments here.
 *
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @version 1.0
 */
public interface IZipOutput extends AutoCloseable {
    void put(String entryName, InputStream is) throws IOException;

    void close() throws IOException;
}
