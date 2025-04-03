package com.wiley.cms.cochrane.utils.zip;

import com.wiley.tes.util.Logger;

/**
 * Type comments here.
 *
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @version 1.0
 */
public abstract class AbstractZipOutput implements IZipOutput {
    static final int BUFFER_SIZE = 1024;

    private static final Logger LOG = Logger.getLogger(AbstractZipOutput.class);

    byte[] buf = new byte[BUFFER_SIZE];
}
