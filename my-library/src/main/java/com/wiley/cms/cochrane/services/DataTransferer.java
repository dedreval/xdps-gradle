package com.wiley.cms.cochrane.services;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.wiley.cms.cochrane.repository.IRepository;
import com.wiley.cms.cochrane.repository.RepositoryFactory;
import com.wiley.tes.util.Extensions;
import com.wiley.tes.util.InputUtils;
import com.wiley.tes.util.Logger;
import com.wiley.tes.util.MimeUtil;
import org.apache.commons.lang.StringUtils;

/**
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @version 1.0
 */
public class DataTransferer extends HttpServlet {
    private static final Logger LOG = Logger.getLogger(DataTransferer.class);

    private static final int BUFFER_SIZE = 8192;
    private static final String CONTENT_LENGTH_HEADER = "Content-Length";

    IRepository rps;

    protected void initServlet() {
        rps = RepositoryFactory.getRepository();
    }

    public void doGet(HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {

        initServlet();

        String requestURI = request.getRequestURI();

        String filePath = requestURI;
        try {
            filePath = requestURI.split(request.getServletPath())[1];
            if (filePath.contains(",")) {
                filePath = StringUtils.substring(filePath, 0, filePath.indexOf(","));
            }
            //LOG.debug("*** SERVLET1 *** " + filePath);
        } catch (Exception e) {
            LOG.error(e.getMessage());
        }

        InputStream responseData = null;
        byte[] bytes;
        long responseDataLength = 0;
        try {
            if (filePath.endsWith(Extensions.XML)) {
                bytes = readFile(filePath);
                bytes = tryCheckImageLinks(request, bytes, filePath);
                responseData = new ByteArrayInputStream(bytes);
                responseDataLength = bytes.length;
            } else {
                responseData = rps.getFile(filePath);
                responseDataLength = rps.getFileLength(filePath);
            }
        } catch (Exception e) {
            LOG.debug(filePath + e, e);
        }
        if (responseData != null) {
            sendResponse(response, requestURI, responseData, responseDataLength);
        }
    }

    private byte[] tryCheckImageLinks(HttpServletRequest request, byte[] bytes, final String filePath) {

        String checkImageLinksParam = request.getParameter("check_image_links");
        if (checkImageLinksParam != null && checkImageLinksParam.equals("1")) {
            ImageLinksHelper imageHelper = new ImageLinksHelper(filePath);
            return imageHelper.checkImageLinks(new String(bytes)).getBytes();
        }
        return bytes;
    }

    protected void sendResponse(HttpServletResponse response, String requestURI, InputStream data, long length)
        throws IOException {

        response.setHeader(CONTENT_LENGTH_HEADER, Long.toString(length));
        setContentType(response, requestURI);
        OutputStream out = response.getOutputStream();

        int readed;
        byte[] buffer = new byte[BUFFER_SIZE];
        while ((readed = data.read(buffer)) > 0) {
            out.write(buffer, 0, readed);
        }
        data.close();
        out.close();
    }

    protected void sendResponse(HttpServletResponse response, String requestURI, byte[] data)
        throws IOException {

        response.setHeader(CONTENT_LENGTH_HEADER, Long.toString(data.length));
        setContentType(response, requestURI);
        OutputStream out = response.getOutputStream();
        out.write(data);
        out.close();
    }

    private void setContentType(HttpServletResponse response, String requestURI) {
        if (requestURI == null) {
            return;
        }

        response.setHeader("Content-Type", MimeUtil.getMimeType(requestURI));
    }

    private byte[] readFile(String filePath) throws IOException {
        return InputUtils.readStreamToByte(rps.getFile(filePath));
    }

}