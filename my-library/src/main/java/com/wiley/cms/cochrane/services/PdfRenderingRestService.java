package com.wiley.cms.cochrane.services;

import com.sun.jersey.multipart.FormDataParam;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.FilePathBuilder;
import com.wiley.cms.cochrane.cmanager.ISinglePdfRenderingManager;
import com.wiley.cms.cochrane.repository.IRepository;
import com.wiley.cms.cochrane.repository.RepositoryFactory;
import com.wiley.cms.cochrane.services.PdfRenderingRestServiceZipParser.ParsingResults;
import com.wiley.cms.converter.services.ConversionData;
import com.wiley.tes.util.Logger;
import com.wiley.tes.util.XmlUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;

import javax.ejb.EJB;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import static com.wiley.cms.cochrane.cmanager.SinglePdfRenderingManager.RenderingParams;

/**
 * @author <a href='mailto:aasadulin@wiley.com'>Alexandr Asadulin</a>
 * @version 13.03.2017
 */
@Path("/{pdf-rendering:(?i)pdf-rendering}")
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class PdfRenderingRestService {

    private static final Logger LOG = Logger.getLogger(PdfRenderingRestService.class);
    @EJB(beanName = "SinglePdfRenderingManager")
    private ISinglePdfRenderingManager singlePdfRndManager;
    private IRepository repository = RepositoryFactory.getRepository();

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String get() {
        return "HTTP GET is not supported";
    }

    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces("application/pdf")
    public byte[] render(
            @FormDataParam("zip") InputStream zipData,
            @FormDataParam("is-revman") boolean revman,
            @FormDataParam("is-preview") @DefaultValue("true") boolean preview) {
        return getPdfBytes(zipData, revman, false, preview);
    }

    @GET
    @Path("/{cochrane:(?i)cochrane}")
    @Produces(MediaType.TEXT_PLAIN)
    public String getMethod() {
        return "PDF Preview: method GET is not supported";
    }

    @POST
    @Path("/{cochrane:(?i)cochrane}")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces("application/pdf")
    public byte[] render(
            @FormDataParam("zip") InputStream zipData,
            @FormDataParam("is-editorial") @DefaultValue("false") boolean editorial) {
        boolean jats = !editorial;
        return getPdfBytes(zipData, false, jats, true);
    }

    private byte[] getPdfBytes(InputStream zipData, boolean revman, boolean jats, boolean preview) {
        LOG.info("PDF Preview service is started");
        File zip = saveZipDataToTmpDir(zipData);
        ConversionData rndData = parseZip(zip, revman, jats);
        RenderingParams rndParams = new RenderingParams(revman, jats, preview);
        byte[] pdfBytes = renderData(rndData, rndParams);

        removeTemporaryData(rndData.getTempDir());
        LOG.info("PDF Preview service is stopped");
        return pdfBytes;
    }

    private File saveZipDataToTmpDir(InputStream requestData) {
        File tmpDir = createTmpDataDir();
        File zip = new File(tmpDir, "input.zip");
        try (OutputStream zipOS = new FileOutputStream(zip)) {
            IOUtils.copy(requestData, zipOS);
        } catch (Throwable tr) {
            throw new DataProcessingException("Failed to save zip to " + zip + ": " + tr);
        }
        return zip;
    }

    private File createTmpDataDir() {
        File tmpDir = new File(repository.getRealFilePath(ConversionData.generateTempDirPath()));
        boolean created = tmpDir.mkdir();
        if (!created) {
            throw new DataProcessingException("Failed to create temporary data directory " + tmpDir);
        }
        return tmpDir;
    }

    private ConversionData parseZip(File zip, boolean revman, boolean jats) {
        File baseDir = zip.getParentFile();
        ParsingResults parsingResults = new PdfRenderingRestServiceZipParser().parse(zip, baseDir, revman, jats);
        if (!parsingResults.isSuccessful()) {
            throw new DataProcessingException("Failed to parse zip. " + parsingResults.getError());
        }
        return toRenderingData(parsingResults, baseDir, revman, jats);
    }

    private ConversionData toRenderingData(ParsingResults parsingResults, File baseDir, boolean revman, boolean jats) {
        ConversionData rndData = new ConversionData(baseDir.getAbsolutePath());
        if (revman) {
            rndData.setSourceXml(readXmlContent(parsingResults.getSourceXml()));
            rndData.setMetadataXml(readXmlContent(parsingResults.getAssets().get(0)));
        } else {
            String articleName = FilenameUtils.getBaseName(parsingResults.getSourceXml().getName());
            rndData.setName(articleName);
            if (jats) {
                rndData.setSourceXml(readXmlContent(parsingResults.getSourceXml()));
                rndData.setPubName(parsingResults.getPubName());
                rndData.setAssets(parsingResults.getAssets());
                rndData.setJatsAries(parsingResults.isJatsAries());
                rndData.setJatsStatsPresent(parsingResults.isJatsStatsPresent());
            } else {
                rndData.setWml21Xml(readXmlContent(parsingResults.getSourceXml()));
                rndData.setRawDataExists(hasRawDataXml(articleName, parsingResults));
            }
        }
        return rndData;
    }

    private String readXmlContent(File xml) {
        try {
            return XmlUtils.getXmlContentBasedOnEstimatedEncoding(xml.getAbsolutePath());
        } catch (IOException e) {
            throw new DataProcessingException(e.getMessage());
        }
    }

    private boolean hasRawDataXml(String articleName, ParsingResults parsingResults) {
        String rawDataXmlExpectedPath = FilePathBuilder.buildRawDataPathByUri(
                parsingResults.getSourceXml().getPath(), articleName);
        return new File(rawDataXmlExpectedPath).exists();
    }

    private byte[] renderData(ConversionData rndData, RenderingParams requestParams) {
        try {
            return singlePdfRndManager.renderPdf(rndData, requestParams);
        } catch (Exception e) {
            throw new DataProcessingException("Data rendering failed. " + e.getMessage());
        }
    }

    private void removeTemporaryData(String tmpDirPath) {
        try {
            if (!CochraneCMSPropertyNames.isRevmanConversionDebugMode()) {
                repository.deleteDir(tmpDirPath);
            }
        } catch (Exception e) {
            LOG.warn("Failed to delete " + tmpDirPath, e);
        }
    }

    /**
     *
     */
    class DataProcessingException extends WebApplicationException {

        public DataProcessingException(String message) {
            super(Response
                    .status(Response.Status.BAD_REQUEST)
                    .entity(message)
                    .type(MediaType.TEXT_PLAIN).build());
        }
    }
}
