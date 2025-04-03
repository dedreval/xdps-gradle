package com.wiley.cms.cochrane.services.editorialuirestservice.api;

import com.wiley.tes.util.Logger;

import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * @author <a href='mailto:atolkachev@wiley.com'>Andrey Tolkachev</a>
 * Date: 12.09.19
 */

public class RequestWrapper extends HttpServletRequestWrapper {

    private static final Logger LOG = Logger.getLogger(RequestWrapper.class);
    private static final int CHAR_BUFFER = 128;
    private final String body;

    RequestWrapper(HttpServletRequest request) {
        super(request);

        StringBuilder stringBuilder = new StringBuilder();
        try (InputStream inputStream = request.getInputStream();
             BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream))) {
            char[] charBuffer = new char[CHAR_BUFFER];
            int bytesRead;
            while ((bytesRead = bufferedReader.read(charBuffer)) > 0) {
                stringBuilder.append(charBuffer, 0, bytesRead);
            }
        } catch (IOException ex) {
            LOG.error("Error reading the request body...");
        }
        body = stringBuilder.toString();
    }

    @Override
    public ServletInputStream getInputStream() {
        final ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(body.getBytes());

        return new ServletInputStream() {
            int lastByteNumber;

            @Override
            public int read() {
                lastByteNumber = byteArrayInputStream.read();
                return lastByteNumber;
            }

            @Override
            public boolean isFinished() {
                return lastByteNumber == -1;
            }

            @Override
            public boolean isReady() {
                return isFinished();
            }

            @Override
            public void setReadListener(ReadListener readListener) {
            }
        };
    }

    @Override
    public BufferedReader getReader() {
        return new BufferedReader(new InputStreamReader(this.getInputStream()));
    }

    public String getBody() {
        return body;
    }
}
