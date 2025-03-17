package com.wiley.cms.cochrane.services;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.wiley.cms.cochrane.cmanager.FilePathCreator;
import com.wiley.tes.util.Logger;

/**
 * Type comments here.
 *
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @version 1.0
 */
public class ImageListServlet extends DataTransferer {
    private static final Logger LOG = Logger.getLogger(ImageListServlet.class);

    public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        initServlet();

        byte[] data = getData(request);

        sendResponse(response, request.getRequestURI(), data);
    }

    private byte[] getData(HttpServletRequest request) {
        String filePath = request.getRequestURI().split(request.getServletPath())[1];

        byte[] data;

        ImageLinksHelper imageHelper = new ImageLinksHelper(filePath);
        List<String> uris = imageHelper.getImageList();
        data = createResponse(uris);

        return data;
    }

    private byte[] createResponse(List<String> uris) {
        byte[] data;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\"?>\n<uris>\n");

        for (String uri : uris) {
            xml.append("<uri>");
            try {
                xml.append(FilePathCreator.getUri(uri));
            } catch (URISyntaxException e) {
                LOG.error(e, e);
            }
            xml.append("</uri>\n");
        }

        xml.append("</uris>");

        try {
            baos.write(xml.toString().getBytes());
        } catch (IOException e) {
            LOG.error(e, e);
        }

        data = baos.toByteArray();
        return data;
    }
}
