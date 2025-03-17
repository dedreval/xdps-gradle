package com.wiley.cms.process.http;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

import org.apache.commons.io.output.CountingOutputStream;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.FileEntity;

import com.wiley.tes.util.Logger;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 11/28/2018
 */
public class ProgressFileEntity extends FileEntity {
    private static final Logger LOG = Logger.getLogger(ProgressFileEntity.class);
    private static final int HUNDRED_PERCENT = 100;

    private IProgressCounter counter;
    private final String fileName;

    public ProgressFileEntity(File file, ContentType contentType) {
        super(file, contentType);

        fileName = file.getName();

        setCounter(new IProgressCounter() {

            long all = 0;
            int part = 0;

            @Override
            public int getProgress(long contentLength) {
                if (contentLength <= 0) {
                    return 0;
                }
                return (int) (HUNDRED_PERCENT * all / contentLength) / 2;
            }

            @Override
            public void addProgress(int delta) {
                all += delta;
                part += delta;

                if (part >= IProgressCounter.MB_BYTES_200) {
                    part = 0;
                    LOG.info(String.format("%s -> %d mb (%d percent)", fileName,
                            (all / IProgressCounter.MB_BYTES_1) / 2, getProgress(getContentLength())));
                }
            }});
    }

    @Override
    public void writeTo(OutputStream out) throws IOException {
        CountingOutputStream output = new CountingOutputStream(out) {
            @Override
            protected void beforeWrite(int n) {
                if (counter != null && n != 0) {
                    counter.addProgress(n);
                }
                super.beforeWrite(n);
            }
        };
        super.writeTo(output);
    }

    public void setCounter(IProgressCounter counter) {
        this.counter = counter;
    }

    public IProgressCounter getCounter() {
        return counter;
    }
}
