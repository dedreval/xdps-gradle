package com.wiley.cms.cochrane.cmanager.publish.send.wol;

import com.wiley.cms.cochrane.cmanager.CochraneCMSProperties;
import com.wiley.cms.cochrane.utils.NonRetrievableException;
import com.wiley.cms.cochrane.utils.RetrievableException;
import com.wiley.cms.process.http.HttpClientHelper;
import com.wiley.tes.util.Logger;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.ByteArrayBody;
import org.apache.http.entity.mime.content.ContentBody;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.impl.client.CloseableHttpClient;

import javax.net.ssl.SSLException;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.UnknownHostException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;

/**
 * @author <a href='mailto:aasadulin@wiley.com'>Alexandr Asadulin</a>
 * @version 22.06.2012
 */
@Deprecated
public class WolLoaderSender {

    private static final Logger LOG = Logger.getLogger(WolLoaderSender.class);
    private static final ConcurrentMap<String, Semaphore> SENDERS = new ConcurrentHashMap<String, Semaphore>();
    private static final String PACKAGE_MESSAGE = "Package [";

    private final String url;
    private final RetryConfig retryConfig;
    private final CloseableHttpClient httpClient;
    private final WolLoaderResponseParser responseParser;

    private WolLoaderSender(String url, RetryConfig retryConfig) {
        this.url = url;
        this.retryConfig = retryConfig;
        this.httpClient = HttpClientHelper.createUsualHttpClient(
                Integer.parseInt(CochraneCMSProperties.getProperty("cms.cochrane.publish.wol.timeout")), false);
        this.responseParser = new WolLoaderResponseParser();
    }

    public WolLoaderSender(String url,
                           RetryConfig retryConfig,
                           CloseableHttpClient httpClient,
                           WolLoaderResponseParser responseParser) {
        this.retryConfig = retryConfig;
        this.url = url;
        this.httpClient = httpClient;
        this.responseParser = responseParser;
    }

    public static WolLoaderSender getSender(String url, RetryConfig retryConfig) {
        return new WolLoaderSender(url, retryConfig);
    }

    public static WolLoaderSender getSender(String url, int maxConnection, RetryConfig retryConfig) {
        SENDERS.putIfAbsent(url, new Semaphore(maxConnection));
        try {
            SENDERS.get(url).acquire();

            return new WolLoaderSender(url, retryConfig);
        } catch (Exception e) {
            throw new RuntimeException("Can't get wol loader sender", e);
        }
    }

    public static void releaseSender(String url, int maxConnection) {
        Semaphore semaphore = SENDERS.get(url);
        if (semaphore != null) {
            if (semaphore.availablePermits() == maxConnection - 1) {
                SENDERS.remove(url);
            }
            semaphore.release();
        }
    }

    public SendingResult send(String packageName, byte[] bArr) {
        ContentBody fp = new ByteArrayBody(bArr, packageName);
        return trySend(packageName, fp);
    }

    public SendingResult send(String packageName, String path) {
        File fp = new File(path);
        return trySend(packageName, new FileBody(fp));
    }

    private SendingResult trySend(String packageName, ContentBody file) {
        HttpEntity entity = MultipartEntityBuilder.create()
                .addPart(packageName, file)
                .build();
        HttpPost httpPost = new HttpPost(url);
        httpPost.setEntity(entity);

        Exception exception = null;
        String error = StringUtils.EMPTY;

        for (int attempt = 1; attempt <= retryConfig.maxRetries; attempt++) {
            try {
                LOG.debug(String.format("%s attempt to send %s to %s", attempt, packageName, url));
                sendRequest(packageName, httpPost);
                LOG.debug(String.format("%s has been sent successfully", packageName));
                break;
            } catch (RetrievableException e) {
                if (attempt == retryConfig.maxRetries) {
                    error = String.format("Retries limit (%s) is exceeded for %s. Last error is %s",
                            retryConfig.maxRetries, packageName, e.getMessage());
                } else {
                    waitBeforeNextAttempt();
                }
                exception = e;
            } catch (NonRetrievableException e) {
                error = e.getMessage();
                exception = e;
                break;
            }
        }

        if (exception != null) {
            LOG.error(packageName + " sending failed", exception);
        }

        httpPost.releaseConnection();
        return new SendingResult(error);
    }

    private void sendRequest(String packageName, HttpPost httpPost)
            throws RetrievableException, NonRetrievableException {
        WolLoaderResponse loaderResponse;
        try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
            loaderResponse = responseParser.parse(response);
            LOG.debug("WOL loader response: " + loaderResponse);
        } catch (MalformedURLException | ClientProtocolException | UnknownHostException | SSLException e) {
            throw new NonRetrievableException(e.getMessage());
        } catch (IOException e) {
            throw new RetrievableException(e.getMessage());
        } catch (Exception e) {
            throw new NonRetrievableException(e.getMessage());
        }
        if (!loaderResponse.isSuccess()) {
            LOG.error(PACKAGE_MESSAGE + packageName + "] sending failed with error ["
                    + loaderResponse.getMessage() + "]");
            if (loaderResponse.isRepeat()) {
                throw new RetrievableException(loaderResponse.getMessage());
            } else {
                throw new NonRetrievableException(loaderResponse.getMessage());
            }
        }
    }

    private void waitBeforeNextAttempt() {
        try {
            Thread.sleep(retryConfig.delay);
        } catch (InterruptedException e) {
            LOG.warn("Unable to delay the next sending", e);
        }
    }

    /**
     *
     */
    public static class RetryConfig {

        private final int maxRetries;
        private final int delay;

        public RetryConfig(int maxRetries, int delay) {
            this.maxRetries = maxRetries;
            this.delay = delay;
        }

        public int getMaxRetries() {
            return maxRetries;
        }

        public int getDelay() {
            return delay;
        }
    }

    /**
     *
     */
    public class SendingResult {

        private final String error;

        public SendingResult(String error) {
            this.error = error;
        }

        public boolean isSuccessful() {
            return StringUtils.isEmpty(error);
        }

        public String getError() {
            return error;
        }
    }
}
