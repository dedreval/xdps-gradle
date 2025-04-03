package com.wiley.cms.cochrane.services;

import java.io.IOException;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.wiley.cms.cochrane.cmanager.data.IResultsStorage;
import com.wiley.cms.cochrane.cmanager.data.record.RecordEntity;
import com.wiley.cms.cochrane.cmanager.entitywrapper.ResultStorageFactory;
import com.wiley.cms.cochrane.utils.Constants;
import com.wiley.tes.util.InputUtils;
import com.wiley.tes.util.Logger;
import org.apache.commons.lang.ArrayUtils;

/**
 * Type comments here.
 *
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @version 1.0
 */
public class PreviewServlet extends DataTransferer {
    private static final Logger LOG = Logger.getLogger(PreviewServlet.class);

    private static final int ISSUE_NUM = 2;
    private static final int DB_NUM = ISSUE_NUM + 1;
    private static final int RECORD_NUM = DB_NUM + 2;

    private static final String TEMP_DIR = "/temp/";
    private static final String IMAGE_N_DIR = "/image_n";
    private static final String IMAGE_T_DIR = "/image_t";
    private static final String CLABOUT_ARTICLES_DIR = "/clabout/articles";
    private static final String CLABOUT_DIR = "/clabout";

    public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        initServlet();

        String requestURI = request.getRequestURI();
        String filepath = requestURI;

        try {
            tryGoToPdf(requestURI, response);
            tryGoToAnotherDB(requestURI, response);

            try {
                //try extract filepath from requested uri
                filepath = requestURI.split(request.getServletPath())[1];
                filepath = findCommonDir(filepath);
                filepath = findCommonFile(filepath);
            } catch (Exception e) {
                LOG.error(e, e);
            }
            //LOG.debug("Trying get " + filepath);

            byte[] data = checkHtmlFileForRootLinks(getData(filepath), requestURI);

            sendResponse(response, requestURI, data);
        } catch (RedirectException e) {
            LOG.debug(e);
        }
    }

    private byte[] checkHtmlFileForRootLinks(byte[] data, String requestURI) {
        byte[] returnData = null;

        if (requestURI.endsWith(".html") || requestURI.endsWith(".htm")) {
            String dir = requestURI.substring(0, requestURI.lastIndexOf("/") + 1);
            String dataAsString = new String(data);
            dataAsString = dataAsString.replaceAll("src=\"/", "src=\"" + dir);
            returnData = dataAsString.getBytes();
        } else if (data != null) {
            returnData = data;
        }
        return returnData;
    }

    private byte[] getData(String filepath) {
        byte[] data = null;

        data = getFileFromArticleDir(filepath, false);
        if (data == null
                && filepath.indexOf(TEMP_DIR + "/") < 0
                && (filepath.indexOf(IMAGE_N_DIR + "/") > 0 || filepath.indexOf(IMAGE_T_DIR + "/") > 0)) {
            String checkedPath = tryFindImageInSourceDir(filepath);
            data = getFileFromArticleDir(checkedPath, false);
        }

        if (data == null) {
            data = getFileFromArticleDir(filepath, false);

            if (data == null) {
                data = getFileFromContentDataDir(filepath);
            }
        }

        return (data == null ? ArrayUtils.EMPTY_BYTE_ARRAY : data);
    }

    private byte[] getFileFromContentDataDir(final String filepath) {
        String localFilePath = filepath;
        String fileName = localFilePath.substring(localFilePath.lastIndexOf("/") + 1, localFilePath.length());
        if (localFilePath.endsWith(".js")) {
            localFilePath = "content-data/js/" + fileName;
        } else {
            localFilePath = "content-data/" + fileName;
        }

        try {
            return readFileToBytes(localFilePath);
        } catch (IOException e) {
            LOG.debug("File couldn't be found in content-data directory.\n" + e.getMessage());
        }
        return null;
    }

    private void tryGoToAnotherDB(String filepath, HttpServletResponse response) throws RedirectException {
        String tmp = filepath;
        if (checkClabout(filepath)) {
            if (filepath.indexOf("/frame.html") > 0) {
                tmp = tmp.replaceFirst(CLABOUT_ARTICLES_DIR, "/clabout/rendered-html_diamond");
            } else if (filepath.indexOf("/CD_fs.htm") > 0) {
                tmp = tmp.replaceFirst(CLABOUT_DIR, "/clabout/rendered-html_cd");
            }

            redirect(response, tmp);
        }
    }

    private void tryGoToPdf(String filepath, HttpServletResponse response) throws RedirectException {
        String tmp = filepath;
        if (filepath.indexOf(IMAGE_N_DIR) > 0 && filepath.indexOf(".pdf") > 0) {
            tmp = tmp.replaceFirst("/rendered-html_diamond", "/" + Constants.RENDERED_PDF_TEX);
            tmp = tmp.replaceFirst("/rnd_html", "/rnd_pdf");
            tmp = tmp.replaceFirst(IMAGE_N_DIR, "");
            //tmp = tmp.substring(0, tmp.lastIndexOf("/") + 1) + "result.pdf";
            redirect(response, tmp);
        }
    }

    private void redirect(HttpServletResponse response, String tmp) throws RedirectException {
        try {
            response.sendRedirect(tmp);
            throw new RedirectException("Redirect to " + tmp);
        } catch (IOException e) {
            LOG.error("Couldn`t redirect to " + tmp + "\n" + e);
        }
    }

    private boolean checkClabout(String filepath) {
        return filepath.indexOf(CLABOUT_ARTICLES_DIR) > 0;
    }

    private String tryFindImageInSourceDir(final String filepath) {
        String tmp = filepath;
        try {
            IResultsStorage rs = ResultStorageFactory.getFactory().getInstance();
            String[] path = filepath.split("/");
            String issue = path[ISSUE_NUM];
            String db = path[DB_NUM];
            String recordName = path[RECORD_NUM];
            RecordEntity record = rs.getRecord(Integer.parseInt(issue), db, recordName);
            String recordPath = record.getRecordPath();
            tmp = recordPath.replaceFirst(".xml",
                    filepath.substring(filepath.lastIndexOf("/image_"), filepath.length()));
        } catch (NumberFormatException e) {
            tmp = tmp.replace("rnd_html", "src");
        } catch (Exception e) {
            LOG.error(e, e);
        }

        return tmp;
    }

    private String findCommonDir(final String filepath) {
        String link = null;
        List<String> commonDir = CommonUIDataList.getCommonUIDirList();
        String foundDirName = null;
        for (String dirName : commonDir) {
            if (filepath.contains("/" + dirName + "/")) {
                if (foundDirName == null
                        || filepath.indexOf(foundDirName) > filepath.indexOf(dirName)) {
                    foundDirName = dirName;
                }
            }
        }
        if (foundDirName == null) {
            link = filepath;
        } else {
            link = CommonUIDataList.getCommonUIDataPath()
                    + "/" + filepath.substring(filepath.indexOf(foundDirName + "/"));
        }
        return link;
    }

    private String findCommonFile(final String filepath) {
        String link = null;
        List<String> commonData = CommonUIDataList.getCommonUIFileList();
        for (String fileName : commonData) {
            if (filepath.contains(fileName)) {
                link = CommonUIDataList.getCommonUIDataPath()
                        + "/" + fileName;
                break;
            }
        }
        if (link == null) {
            link = filepath;
        }
        return link;
    }

    private byte[] getFileFromArticleDir(String filepath, boolean msg) {
        try {
            return readFileToBytes(filepath);
        } catch (IOException e) {
            if (msg) {
                LOG.warn("File couldn't be found in article directory.\n" + e.getMessage());
            }
        }
        return null;
    }

    private byte[] readFileToBytes(String filePath) throws IOException {
        return InputUtils.readStreamToByte(rps.getFile(filePath));
    }
}

