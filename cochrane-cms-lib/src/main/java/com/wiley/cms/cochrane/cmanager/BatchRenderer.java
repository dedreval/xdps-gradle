package com.wiley.cms.cochrane.cmanager;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import com.wiley.cms.cochrane.cmanager.data.DeliveryFileEntity;
import com.wiley.cms.cochrane.cmanager.data.rendering.RenderingPlanEntity;
import com.wiley.cms.cochrane.repository.IRepository;
import com.wiley.cms.cochrane.repository.RepositoryFactory;
import com.wiley.tes.util.Logger;

/**
 * @param <T> Record
 * @author <a href='mailto:sgulin@wiley.com'>Svyatoslav Gulin</a>
 * @version 28.10.2011
 */
public abstract class BatchRenderer<T> {
    private static final int XML_STRING_LENGTH = 130;

    private static final Logger LOG = Logger.getLogger(BatchRenderer.class);

    private String timestamp;
    private RenderingPlanEntity[] planEntities;
    private DeliveryFileEntity dfEntity;
    private List<T> records;

    protected BatchRenderer(List<T> records, String timestamp, RenderingPlanEntity[] planEntities,
                            DeliveryFileEntity dfEntity) {
        this.timestamp = timestamp;
        this.planEntities = planEntities;
        this.dfEntity = dfEntity;
        this.records = records;
    }

    protected BatchRenderer(List<T> records, String timestamp) {
        this.timestamp = timestamp;
        this.records = records;
    }

    public static StringBuilder startBuildUriForCentral(int partSize) {

        StringBuilder xmlWithUrls = new StringBuilder(partSize * XML_STRING_LENGTH);
        xmlWithUrls.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>").append("<files>");
        return xmlWithUrls;
    }

    public static URI endBuildUriForCentral(int issueId, String dbName, String timestamp,
        StringBuilder xmlWithUrls, int partNum, IRepository rps) throws URISyntaxException, IOException {
        xmlWithUrls.append("</files>");

        String pathToXml = getPathToXml(issueId, dbName, timestamp, partNum);
        if (rps.isFileExists(pathToXml)) {
            LOG.warn("CENTRAL URI already exist " + pathToXml);
        }
        rps.putFile(pathToXml, new BufferedInputStream(new ByteArrayInputStream(xmlWithUrls.toString().getBytes())));
        return FilePathCreator.getUri(pathToXml);
    }

    public static void buildUriForCentral(String uri, StringBuilder xmlWithUrls) {
        xmlWithUrls.append("<file href='").append(uri).append("'/>");
    }

    public URI[] createUrisForCentral() throws URISyntaxException, IOException {
        IRepository rps = RepositoryFactory.getRepository();
        int centralPartSize = CochraneCMSPropertyNames.getCentralPartSize();
        int pckNumber = (int) Math.ceil((double) records.size() / centralPartSize);
        LOG.debug("records size=" + records.size() + " pckNumber=" + pckNumber);
        URI[] uris = new URI[pckNumber];
        int j = 0;

        for (int i = 0; i < pckNumber; i++) {
            StringBuilder xmlWithUrls = startBuildUriForCentral(centralPartSize);

            int k = 0;
            StringBuilder recNames = new StringBuilder();
            while (j < records.size() && k < centralPartSize) {
                T record = records.get(j++);
                buildUriForCentral(getSourceFilePath(record), xmlWithUrls);
                if (k > 0) {
                    recNames.append(",");
                }
                recNames.append("'").append(record.toString()).append("'");
                k++;
            }

            uris[i] = endBuildUriForCentral(getIssueId(), getDbTitle(), getTimestamp(), xmlWithUrls, i, rps);
            createRendering(recNames.toString());
            LOG.debug("createUrisAndRndForCentral finished i=" + i + "  recs size=" + records.size());
        }
        return uris;
    }

    //public String getPathToXml(int i) {
    //    return getPathToXml(getIssueId(), this.getDbTitle(), this.getTimestamp(), i);
    //}

    private static String getPathToXml(int issueId, String dbName, String timestamp, int i) {
        return issueId == 0 ? FilePathCreator.getFilePathToUrlsCentralEntire(dbName, timestamp, i)
            : FilePathCreator.getFilePathToUrlsCentral(String.valueOf(issueId), dbName, timestamp, i);
    }

    public abstract String getSourceFilePath(T entity) throws URISyntaxException;

    public void createRendering(String recordNames) {
    }

    public int getIssueId() {
        return 0;
    }

    public String getDbTitle() {
        return null;
    }


    public String getTimestamp() {
        return timestamp;
    }

    public RenderingPlanEntity[] getPlanEntities() {
        return planEntities;
    }

    public DeliveryFileEntity getDfEntity() {
        return dfEntity;
    }

    public List<T> getRecords() {
        return records;
    }
}
