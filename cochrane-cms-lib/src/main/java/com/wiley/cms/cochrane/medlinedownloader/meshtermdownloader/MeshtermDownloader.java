package com.wiley.cms.cochrane.medlinedownloader.meshtermdownloader;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;

import org.apache.http.util.EntityUtils;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.output.XMLOutputter;
import org.jdom.xpath.XPath;

import com.wiley.cms.cochrane.cmanager.CochraneCMSProperties;
import com.wiley.cms.cochrane.medlinedownloader.IMedlineDownloader;
import com.wiley.cms.cochrane.medlinedownloader.MedlineDownloaderException;
import com.wiley.cms.cochrane.utils.SSLChecker;
import com.wiley.cms.process.RepeatableOperation;
import com.wiley.cms.process.http.HttpClientHelper;
import com.wiley.tes.util.jdom.DocumentLoader;

/**
 * @author <a href='mailto:ikirov@wiley.com'>Igor Kirov</a>
 * @version 22.10.2009
 */
public class MeshtermDownloader implements IMedlineDownloader {

    private static final int INTERVAL_MILLIS = 333;

    private CloseableHttpClient httpClient;
    private File medlineRecordFile;
    private File searchLogFile;
    private String revmanId;
    private DocumentLoader dl;

    public void download(Map<String, String> params) throws MedlineDownloaderException {
        try {
            init(params);

            ESearchOperation eSearch = new ESearchOperation(getESearchUrl()) {
                @Override
                protected void perform() throws Exception {
                    result = esearch(baseUrl);
                }
            };
            eSearch.performOperationThrowingException();
            if (eSearch.result == null) {
                return;
            }
            new ESearchOperation(getEFetchUrl()) {
                @Override
                protected void perform() throws Exception {
                    efetch(eSearch.result, baseUrl);
                }
            }.performOperationThrowingException();

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new MedlineDownloaderException(e);
        }
    }

    private void init(Map<String, String> params) throws Exception {
        revmanId = params.get("revmanId");
        if (revmanId == null || revmanId.equals("")) {
            throw new MedlineDownloaderException("revmanId parameter required");
        }

        String destinationDirectoryName = params.get("destinationDirectory");
        if (destinationDirectoryName == null) {
            throw new MedlineDownloaderException("destinationDirectory parameter required");
        }
        File destinationDirectory = new File(new URI(destinationDirectoryName));
        if (!destinationDirectory.exists()) {
            if (!destinationDirectory.mkdirs()) {
                throw new MedlineDownloaderException(
                    "Cannot create destination directory " + destinationDirectory.getAbsolutePath());
            }
        }

        String writeSearchResult = params.get("writeSearchResult");
        if (writeSearchResult != null && writeSearchResult.equals("true")) {
            searchLogFile = new File(
                new URI(destinationDirectoryName + "/" + revmanId + ".search.xml"));
        }

        medlineRecordFile = new File(
            new URI(destinationDirectoryName + "/" + revmanId + ".xml"));

        httpClient = HttpClientHelper.createUsualHttpClient();
        dl = new DocumentLoader();
    }

    private static String getESearchUrl() {
        return CochraneCMSProperties.getProperty("cms.cochrane.medline.esearch.url");
    }

    private static String getEFetchUrl() {
        return CochraneCMSProperties.getProperty("cms.cochrane.medline.efetch.url");
    }

    private String esearch(String baseUrl) throws Exception {
        String url = baseUrl + "db=pubmed&retmode=xml&term="
                + encode(revmanId + "[page] AND medline[sb]") + "&sort=pub+date";

        HttpGet search = HttpClientHelper.createUsualGetMethod(url);
        Thread.sleep(INTERVAL_MILLIS);

        CloseableHttpResponse resp =  httpClient.execute(search);

        InputStream response = null;
        Document searchResult = null;
        try {
            int statusCode =  resp.getStatusLine().getStatusCode();
            if (statusCode == HttpStatus.SC_OK) {

                HttpEntity he = resp.getEntity();
                response = he.getContent();
                searchResult = dl.load(response);

                EntityUtils.consume(he);
                resp.close();
            }

        } finally {
            IOUtils.closeQuietly(response);
            search.releaseConnection();
        }

        if (searchResult != null) {
            if (searchLogFile != null) {
                writeSearchResult(searchResult);
            }

            int count = Integer.parseInt(
                    ((Element) XPath.selectSingleNode(searchResult, "/eSearchResult/Count")).getTextTrim());

            if (count > 0) {
                List nodes = XPath.selectNodes(searchResult, "/eSearchResult/IdList/Id");
                return ((Element) nodes.get(0)).getText();
            }
        }

        return null;
    }

    private void writeSearchResult(Document document) throws Exception {
        XMLOutputter xout = new XMLOutputter();
        FileOutputStream searchResultLog = new FileOutputStream(searchLogFile);
        xout.output(document, searchResultLog);
        searchResultLog.close();
    }

    private static String encode(String str) throws MedlineDownloaderException {
        try {
            return URLEncoder.encode(str, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new MedlineDownloaderException("Unsupported encoding UTF-8");
        }
    }

    private String efetch(String id, String baseUrl) throws Exception {
        String url = baseUrl + "db=pubmed&retmode=xml&id=" + id;
        HttpGet fetch = HttpClientHelper.createUsualGetMethod(url);

        Thread.sleep(INTERVAL_MILLIS);
        CloseableHttpResponse resp = httpClient.execute(fetch);

        InputStream response = null;
        OutputStream fetchResultLog = null;
        try {
            fetchResultLog = new BufferedOutputStream(new FileOutputStream(medlineRecordFile));
            HttpEntity he = resp.getEntity();
            response = he.getContent();
            IOUtils.copy(response, fetchResultLog);

            HttpClientHelper.closeResponse(he, resp);

        } finally {
            IOUtils.closeQuietly(fetchResultLog);
            IOUtils.closeQuietly(response);
            fetch.releaseConnection();
        }

        return medlineRecordFile.getAbsolutePath();
    }

    private abstract class ESearchOperation extends RepeatableOperation {
        final String baseUrl;
        String result;

        ESearchOperation(String baseUrl) {
            super(2);
            this.baseUrl = baseUrl;
        }

        @Override
        protected Exception onNextAttempt(Exception e) {
            if (SSLChecker.checkCertificate(baseUrl, e)) {
                httpClient = HttpClientHelper.createUsualHttpClient();
                return super.onNextAttempt(e);
            }
            fillCounter();
            return e;
        }
    }
}
