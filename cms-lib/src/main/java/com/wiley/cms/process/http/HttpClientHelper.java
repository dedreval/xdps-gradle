package com.wiley.cms.process.http;

import java.io.IOException;
import java.net.URLEncoder;

import org.apache.http.HttpEntity;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.RedirectStrategy;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import com.wiley.tes.util.Logger;
import com.wiley.tes.util.XmlUtils;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 11/11/2015
 */
public class HttpClientHelper {
    private static final Logger LOG = Logger.getLogger(HttpClientHelper.class);
    private static final int METHOD_WAIT_TIMEOUT = 60000; // 1 minute

    private static final String HEADER_CONNECTION = "Connection";
    private static final String HEADER_CONNECTION_CLOSE = "close";

    private HttpClientHelper() {
    }

    public static CloseableHttpClient createUsualHttpClient() {
        return createUsualHttpClient(METHOD_WAIT_TIMEOUT, true);
    }

    public static CloseableHttpClient createHttpClient(int socketTimeout, RedirectStrategy strategy) {
        return createUsualHttpClient(socketTimeout, false, strategy);
    }

    public static CloseableHttpClient createUsualHttpClient(int socketTimeout, boolean ignoreCookies) {
        return createUsualHttpClient(socketTimeout, ignoreCookies, null);
    }

    public static CloseableHttpClient createAuthHttpClient(int timeout, String user, String password) {
        RequestConfig.Builder requestConfig = RequestConfig.custom();

        requestConfig.setConnectTimeout(timeout);
        requestConfig.setConnectionRequestTimeout(timeout);
        requestConfig.setSocketTimeout(timeout);
        requestConfig.setCookieSpec(CookieSpecs.STANDARD);

        CredentialsProvider provider = new BasicCredentialsProvider();
        UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(user, password);
        provider.setCredentials(AuthScope.ANY, credentials);

        return HttpClients.custom().setDefaultRequestConfig(requestConfig.build()).setDefaultCredentialsProvider(
                provider).build();
    }

    public static CloseableHttpClient createUsualHttpClient(int socketTimeout, boolean ignoreCookies,
                                                            RedirectStrategy strategy) {
        RequestConfig.Builder requestConfig = RequestConfig.custom();
        if (ignoreCookies) {
            requestConfig = requestConfig.setCookieSpec(CookieSpecs.IGNORE_COOKIES);
        }

        requestConfig.setConnectTimeout(socketTimeout);
        requestConfig.setConnectionRequestTimeout(socketTimeout);
        requestConfig.setSocketTimeout(socketTimeout);

        return strategy != null ? HttpClientBuilder.create().setRedirectStrategy(strategy).setDefaultRequestConfig(
                requestConfig.build()).build()
                : HttpClients.custom().setDefaultRequestConfig(requestConfig.build()).build();
    }

    public static HttpGet createUsualGetMethod(String url) {
        HttpGet get = new HttpGet(url);
        get.addHeader(HEADER_CONNECTION, HEADER_CONNECTION_CLOSE);
        return get;
    }

    public static HttpDelete createUsualDeleteMethod(String basePath, String filePath) throws Exception {
        return createUsualDeleteMethod(basePath + URLEncoder.encode(filePath, XmlUtils.UTF_8));
    }

    public static HttpDelete createUsualDeleteMethod(String url) {
        HttpDelete del = new HttpDelete(url);
        del.addHeader(HEADER_CONNECTION, HEADER_CONNECTION_CLOSE);
        return del;
    }

    public static HttpPut createUsualPutMethod(String basePath, String filePath, HttpEntity entity) throws Exception {
        return createUsualPutMethod(basePath + URLEncoder.encode(filePath, XmlUtils.UTF_8), entity);
    }

    public static HttpPut createUsualPutMethod(String url, HttpEntity entity) {
        HttpPut put = new HttpPut(url);
        put.setEntity(entity);
        put.addHeader(HEADER_CONNECTION, HEADER_CONNECTION_CLOSE);
        //put.addHeader("Content-Disposition", "filename=\"" + fileName + "\"");
        return put;
    }

    public static void closeResponse(HttpEntity he, CloseableHttpResponse response) throws IOException {
        EntityUtils.consume(he);
        response.close();
    }
}
