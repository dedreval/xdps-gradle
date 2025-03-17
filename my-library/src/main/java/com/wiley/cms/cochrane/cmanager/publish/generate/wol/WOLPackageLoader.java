package com.wiley.cms.cochrane.cmanager.publish.generate.wol;

import java.io.File;
import java.net.ConnectException;
import java.util.HashMap;
import java.util.Map;

import static org.apache.commons.lang.SystemUtils.LINE_SEPARATOR;

import com.wiley.cms.cochrane.utils.NonRetrievableException;
import com.wiley.cms.cochrane.utils.RetrievableException;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;

import com.wiley.cms.cochrane.cmanager.CochraneCMSProperties;
import com.wiley.cms.cochrane.cmanager.MessageSender;
import com.wiley.cms.process.http.HttpClientHelper;
import com.wiley.tes.util.InputUtils;
import com.wiley.tes.util.Logger;

/**
 * @author <a href='mailto:sgulin@wiley.ru'>Svyatoslav Gulin</a>
 * @version 27.01.2012
 */
@Deprecated
public class WOLPackageLoader {

    private static final Logger LOG = Logger.getLogger(WOLPackageLoader.class);

    private static final String PACKAGE = "Package ";
    private static final String RECORDS = " records = [";

    private CloseableHttpClient httpClient;

    private boolean interrupted;
    private long retryCount;

    public WOLPackageLoader() {
        resetState();
    }

    public boolean sendPackage(File publishedFile, String recordNames) {
        resetState();

        boolean sendSuccessFully = false;
        while ((!sendSuccessFully) && (!interrupted) && (retryCount <= getMaxAttempts())) {
            sendSuccessFully = process(publishedFile, recordNames);
        }

        return sendSuccessFully;
    }

    private int getMaxAttempts() {
        return Integer.parseInt(
            CochraneCMSProperties.getProperty("cms.cochrane.publish.wol.cca.max.attempts"));
    }

    private void resetState() {
        retryCount = 1;
        interrupted = false;
    }

    private boolean process(File file, String publishedRecords) {
        boolean sendResult = false;

        try {
            LOG.debug("Try to send package. Attempt " + retryCount);
            retryCount++;
            LOG.debug("file path = " + file.getAbsolutePath());
            send(file);

            LOG.debug(PACKAGE + file.getName() + " send successfully");

            sendResult = true;
        } catch (RetrievableException ex) {
            LOG.debug(PACKAGE + file.getName() + " was send with error " + ex.getMessage());

            boolean exceeded = retryCount >= getMaxAttempts();

            if (exceeded) {
                LOG.warn("Retries exceeded for package " + file.getName());
                LOG.warn("Publishing process interrupted");
                sendReport(file.getName(), publishedRecords, "Retries exceeded. Max attempts is " + getMaxAttempts());
                interrupted = true;
            }
        } catch (Exception ex) {
            LOG.error("Process will be interrupted", ex);
            sendReport(file.getName(), publishedRecords, ex.getMessage());
            interrupted = true;
        }

        return sendResult;
    }

    private void sendReport(String fileName, String recordNames, String details) {
        Map<String, String> map = new HashMap<String, String>();
        map.put("fileName", fileName);
        map.put("ccaids", recordNames);
        map.put("report", details);

        try {
            MessageSender.sendMessage("cca_ol_publishing_failed", map);

            LOG.warn("Sending failed publishing report: fileName = [" + fileName + "]" + LINE_SEPARATOR + RECORDS
                + recordNames + "]");
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
            LOG.warn("Sending report failed : fileName = [" + fileName + "]" + LINE_SEPARATOR + RECORDS
                + recordNames + "]");
        }
    }


    private void send(File file) throws NonRetrievableException, RetrievableException {
        try {
            StatusLine statusLine = null;
            if (!isInited()) {
                init();
            }

            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            builder.addPart(file.getName(), new FileBody(file));
            HttpEntity entity = builder.build();

            HttpPost post = new HttpPost(CochraneCMSProperties.getProperty("cms.cochrane.publish.wol.cca.loader.url"));
            post.setEntity(entity);

            String resultMessage = null;
            try {
                CloseableHttpResponse response = httpClient.execute(post);
                statusLine = response.getStatusLine();
                int statusCode = statusLine.getStatusCode();
                HttpEntity he = response.getEntity();

                resultMessage = InputUtils.readStreamToString(he.getContent());
                LOG.debug("Sending complete with result = " + resultMessage);

                EntityUtils.consume(he);
                response.close();

            } finally {
                post.releaseConnection();
            }

            LoaderResult loaderResult = buildLoaderResult(statusLine, resultMessage);
            LOG.debug("Loader result is " + loaderResult);

//            if (statusCode != HttpStatus.SC_OK) {
//                new RetrievableException("Method failed (STATUS_CODE - " + statusCode + "): " + resultMessage);
//            }

            if (!loaderResult.getSuccess()) {
                if (LOG.isWarnEnabled()) {
                    String serverMsg = loaderResult.getMessage();

                    LOG.warn("Sending of the package " + file.getName() + " was failed with error '" + serverMsg + "'");
                }
                if (loaderResult.getTryAgain()) {
                    throw new RetrievableException(loaderResult.getMessage());
                } else {
                    throw new NonRetrievableException(loaderResult.getMessage());
                }
            }

        } catch (RetrievableException ex) {
            throw ex;
        } catch (NonRetrievableException ex) {
            throw ex;
        } catch (ConnectException ex) {
            throw new RetrievableException(ex.getMessage(), ex);
        } catch (Exception ex) {
            throw new NonRetrievableException(ex.getMessage(), ex);
        }
    }

    private void init() {
        LOG.debug("Setup new http connection");
        httpClient = HttpClientHelper.createUsualHttpClient(Integer.parseInt(
            CochraneCMSProperties.getProperty("cms.cochrane.publish.wol.cca.timeout")), false);
    }

    protected boolean isInited() {
        return httpClient != null;
    }

    private LoaderResult buildLoaderResult(final StatusLine status, final String errorMessage) {
        final boolean result = (status.getStatusCode() == HttpStatus.SC_OK);
        String message;
        switch (status.getStatusCode()) {
            case HttpStatus.SC_OK:
                message = "Content Loaded Successfully";
                break;
            default:
                message = errorMessage;
        }

        boolean retry = (status.getStatusCode() == HttpStatus.SC_SERVICE_UNAVAILABLE);

        return new LoaderResult(result, retry, status.getStatusCode(), message);
    }

    class LoaderResult {
        private boolean success = true;
        private boolean tryAgain = false;
        private int resultCode = 0;
        private String message = null;

        public LoaderResult(boolean success, boolean tryAgain, int resultCode, String message) {
            this.success = success;
            this.tryAgain = tryAgain;
            this.resultCode = resultCode;
            this.message = message;
        }

        public boolean getSuccess() {
            return success;
        }

        public boolean getTryAgain() {
            return tryAgain;
        }

        public int getResultCode() {
            return resultCode;
        }

        public String getMessage() {
            return message;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("success = ").append(success);
            sb.append(", tryAgain = ").append(tryAgain);
            sb.append(", resultCode = ").append(resultCode);
            sb.append(", message = ").append(message);

            return sb.toString();
        }
    }

}
