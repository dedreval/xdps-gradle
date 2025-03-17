package com.wiley.cms.cochrane.cmanager.publish.send.literatum;

import java.io.File;
import java.util.Set;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.FileEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;

import com.wiley.cms.cochrane.cmanager.FilePathCreator;
import com.wiley.cms.cochrane.cmanager.res.ConnectionType;
import com.wiley.cms.cochrane.cmanager.res.PublishProfile;
import com.wiley.cms.process.http.HttpClientHelper;
import com.wiley.cms.process.http.IProgressCounter;
import com.wiley.cms.process.http.ProgressFileEntity;
import com.wiley.tes.util.InputUtils;
import com.wiley.tes.util.http.BaseHttpClient;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 9/10/2018
 */
public class AtyponClient extends BaseHttpClient {

    protected PublishProfile.PubLocationPath pubLocation;
    private CloseableHttpClient client;
    private String basePath;

    //public AtyponClient(String folder, String user, String password) {
    //    this(PublishProfile.getProfile().get().getPubLocation(
    //            PubType.TYPE_LITERATUM, null, false, false).getConnectionType(), folder, user, password);
    //}


    public AtyponClient(PublishProfile.PubLocationPath pubLocation, String user, String password) {
        this(pubLocation.getConnectionType(), pubLocation.getFolder() + FilePathCreator.SEPARATOR, user, password);
        this.pubLocation = pubLocation;
    }

    private AtyponClient(ConnectionType connection, String folder, String user, String password) {
        this(connection.getUrl() + folder, connection.getTimeout(), user, password);
    }

    private AtyponClient(String basePath, int timeout, String user, String password) {
        super(false, true);
        client = HttpClientHelper.createAuthHttpClient(timeout, user, password);
        this.basePath = basePath;
    }

    public void sendPackage(String fromPackagePath, String fileName) throws Exception {
        File fl = new File(fromPackagePath + fileName);
        boolean largeFile = fl.length() >= IProgressCounter.MB_BYTES_200;
        FileEntity fileEntity = largeFile
                ? new ProgressFileEntity(fl, ContentType.DEFAULT_BINARY)
                : new FileEntity(fl, ContentType.DEFAULT_BINARY);

        getPutOperation(fileName, fileEntity).performOperationThrowingException();
    }


    public String putObject(String filePath, HttpEntity entity, Set<Integer> okCodes) throws Exception {
        HttpPut put = null;
        try {
            put = HttpClientHelper.createUsualPutMethod(basePath, filePath, entity);
            CloseableHttpResponse response = client.execute(put);
            int statusCode = response.getStatusLine().getStatusCode();
            String retCode = "response: " + statusCode;
            handleError(response, statusCode, put.toString(), put.toString() + " -> " + statusCode, okCodes);
            response.close();
            return retCode;

        } finally {
            if (put != null) {
                put.releaseConnection();
            }
        }
    }

    @Override
    protected String getErrorBody(Object response) {
        try {
            HttpEntity he = ((CloseableHttpResponse) response).getEntity();
            String ret = InputUtils.readStreamToString(he.getContent());
            EntityUtils.consume(he);
            return ret;

        } catch (Exception e) {
            LOG.warn(e.getMessage());
            return response.toString();
        }
    }

    private HttpOperation getPutOperation(String filePath, HttpEntity entity) {
        return new HttpOperation(basePath, 2, OK_PUT_CODES, filePath, entity) {

            @Override
            protected void perform() throws Exception {
                result = putObject((String) params[0], (HttpEntity) params[1], okCodes);
            }

            @Override
            protected void onTrustStoreChanged() {
                client = HttpClientHelper.createAuthHttpClient(pubLocation.getConnectionType().getTimeout(),
                    pubLocation.getServerType().getUser(), pubLocation.getServerType().getPassword());
            }
        };
    }

    @Override
    public String toString() {
        return basePath;
    }
}
