package com.wiley.cms.cochrane.cmanager.publish.udw;

import com.wiley.cms.cochrane.cmanager.CochraneCMSProperties;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.data.record.RecordEntity;
import com.wiley.cms.cochrane.cmanager.entitymanager.AbstractManager;
import com.wiley.cms.cochrane.repository.IRepository;
import com.wiley.tes.util.Logger;
import com.wiley.tes.util.TransformerObjectPool;
import org.apache.axis.utils.StringUtils;

import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URISyntaxException;

import static com.wiley.cms.cochrane.cmanager.FilePathBuilder.ML3G.getPathToEntireMl3gRecord;
import static com.wiley.cms.cochrane.cmanager.FilePathBuilder.ML3G.getPathToPreviousMl3gRecord;
import static com.wiley.cms.cochrane.cmanager.FilePathBuilder.getPathToEntireSrcRecord;

/**
 * @author <a href='mailto:aasadulin@wiley.com'>Alexandr Asadulin</a>
 * @version 17.01.2019
 */
public class UdwFeedDataExtractor {
    private static final Logger LOG = Logger.getLogger(UdwFeedDataExtractor.class);
    private final String dbName;
    private final boolean centralDb;
    private final boolean ccaDb;
    private final IRepository repository;
    private final String wml3gMdXslPath;

    public UdwFeedDataExtractor(String dbName) {
        this(dbName, AbstractManager.getRepository());
    }

    public UdwFeedDataExtractor(String dbName, IRepository repository) {
        this.dbName = dbName;
        this.centralDb = CochraneCMSPropertyNames.getCentralDbName().equals(dbName);
        this.ccaDb = CochraneCMSPropertyNames.getCcaDbName().equals(dbName);
        this.repository = repository;
        this.wml3gMdXslPath = CochraneCMSProperties.getProperty("cms.cochrane.udw.md_xsl");
    }

    public String extract(String articleName, int version) throws Exception {
        String md = extractWml3gMd(articleName, version);
        if (StringUtils.isEmpty(md)) {
            throw new Exception("No data extracted");
        }
        return md.replaceAll("\n", "\t");
    }

    private String extractWml3gMd(String articleName, int version) throws Exception {
        Source transformIn = new StreamSource(new File(getWml3gXmlPath(articleName, version)));
        StringWriter transformOut = new StringWriter();
        Transformer transformer = getTransformer();

        transform(transformIn, transformOut, transformer);

        return transformOut.toString();
    }

    private Transformer getTransformer() throws TransformerException, IOException, URISyntaxException {
        return TransformerObjectPool.getInstance().getTransformer(wml3gMdXslPath);
    }

    private void transform(Source in, StringWriter out, Transformer transformer) {
        try {
            transformer.transform(in, new StreamResult(out));
        } catch (TransformerException e) {
            throw new RuntimeException(e);
        }
    }

    private String getWml3gXmlPath(String articleName, int version) {
        String path = version == RecordEntity.VERSION_LAST
                ? getPathToEntire(articleName)
                : getPathToPreviousMl3gRecord(version, articleName);
        return repository.getRealFilePath(path);
    }

    private String getPathToEntire(String articleId) {
        return ccaDb
                ? getPathToEntireSrcRecord(dbName, articleId)
                : getPathToEntireMl3gRecord(dbName, articleId, centralDb);
    }
}
