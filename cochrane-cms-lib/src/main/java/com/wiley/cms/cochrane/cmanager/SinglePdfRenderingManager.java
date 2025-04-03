package com.wiley.cms.cochrane.cmanager;

import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.repository.IRepository;
import com.wiley.cms.cochrane.repository.RepositoryFactory;
import com.wiley.cms.cochrane.utils.RetrievableException;
import com.wiley.cms.converter.services.ConversionData;
import com.wiley.cms.converter.services.IConversionProcess;
import com.wiley.cms.process.RepeatableOperation;
import com.wiley.cms.qaservice.services.IProvideQa;
import com.wiley.cms.qaservice.services.WebServiceUtils;
import com.wiley.cms.render.services.IProvideSinglePdfRendering;
import com.wiley.cms.render.services.IRenderingProvider;
import com.wiley.tes.util.Extensions;
import com.wiley.tes.util.Logger;
import com.wiley.tes.util.jdom.DocumentLoader;
import org.apache.commons.io.IOUtils;

import javax.activation.DataHandler;
import javax.ejb.EJB;
import javax.ejb.Local;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import java.io.ByteArrayInputStream;
import java.net.MalformedURLException;

import static com.wiley.cms.cochrane.cmanager.CochraneCMSProperties.getIntProperty;
import static com.wiley.cms.cochrane.cmanager.CochraneCMSProperties.getProperty;

/**
 * @author <a href='mailto:aasadulin@wiley.com'>Alexandr Asadulin</a>
 * @version 13.03.2017
 */
@Stateless
@Local(ISinglePdfRenderingManager.class)
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class SinglePdfRenderingManager implements ISinglePdfRenderingManager {
    private static final int DEFAULT_RETRY_CNT_ON_RETRIEVABLE_EXCEPTION = 3;
    private static final float DEFAULT_STAMP_FILL_OPACITY = .3f;
    private static final Logger LOG = Logger.getLogger(SinglePdfRenderingManager.class);
    private static final int FONT_SIZE = 72;
//    private static final BaseColor LIGHT_GRAY_COLOR = new BaseColor(208, 208, 208);
    private static final int X_OFFSET = 300;
    private static final int Y_OFFSET = 400;
    private static final int ANGLE = 55;

    @EJB(beanName = "ConversionProcessImpl")
    private IConversionProcess revmanConvManager;
    private IProvideQa qaProviderStub;
    private IProvideSinglePdfRendering pdfRndProviderStub;
    private final DocumentLoader documentLoader = new DocumentLoader();
    private final IRepository repository = RepositoryFactory.getRepository();

    public SinglePdfRenderingManager() {
    }

    public SinglePdfRenderingManager(IConversionProcess revmanConvManager,
                                     IProvideQa qaProviderStub,
                                     IProvideSinglePdfRendering pdfRndProviderStub) {
        this();

        this.revmanConvManager = revmanConvManager;
        this.qaProviderStub = qaProviderStub;
        this.pdfRndProviderStub = pdfRndProviderStub;
    }

    public byte[] renderPdf(ConversionData rndData, RenderingParams rndParams) throws Exception {
        try {
            byte[] pdfBytes = executePdfRendering(rndData, rndParams);
            saveRenderingResults(rndData, pdfBytes);
            return pdfBytes;
        } catch (Exception e) {
            LOG.error("PDF rendering failed", e);
            throw e;
        }
    }

    private byte[] executePdfRendering(ConversionData rndData, RenderingParams rndParams) throws Exception {
        convertIntermediateData(rndData, BaseType.find(rndData.getDbName()).get(), rndParams.revman, rndParams.jats);
        int retryTimes = getIntProperty("cms.cochrane.pdf_preview.retry_times_on_fail",
                DEFAULT_RETRY_CNT_ON_RETRIEVABLE_EXCEPTION);
        ConditionalRepeatableOperation<byte[]> repeatableOp = new ConditionalRepeatableOperation<byte[]>(retryTimes) {
            @Override
            protected void tryPerform() throws Exception {
                if (!rndParams.jats) {
                    checkData(rndData);
                }
                result = renderData(rndData, rndParams.preview);
            }
        };
        repeatableOp.performOperationThrowingException();

        return repeatableOp.result;
    }

    private void convertIntermediateData(ConversionData rndData, BaseType bt, boolean revman, boolean jats)
            throws Exception {
        if (revman) {
            rndData.setConvertToRevman(true);
        }
        if (jats) {
            rndData.setConvertJatsToWml3g(true);
        } else {
            rndData.setConvertToWml3g(true);
        }
        revmanConvManager.convertWithStrictValidation(rndData);
    }

    private void checkData(ConversionData rndData) throws Exception {
        String qaCheck;
        StringBuilder errors = new StringBuilder();
        boolean successful;
        IProvideQa qaProvider = null;
        try {
            qaProvider = obtainQaProvider();
            qaCheck = qaProvider.check(rndData.getWml21Xml(), "cochrane", "clsysrev");
            successful = CmsUtils.getErrorsFromQaResults(qaCheck, errors, documentLoader);
        } catch (Exception e) {
            throw new RetrievableException("Failed to perform validation WML21 xml, QA service returns error", e);
        } finally {
            releaseQaProvider(qaProvider);
        }
        if (successful) {
            LOG.debug("WML21 xml validation completed successfully.\n" + qaCheck);
        } else {
            throw new Exception("WML21 xml is invalid, validation returns following errors: " + errors);
        }
    }

    private IProvideQa obtainQaProvider() throws MalformedURLException {
        if (qaProviderStub != null) {
            return qaProviderStub;
        } else {
            return WebServiceUtils.getProvideQa();
        }
    }

    private void releaseQaProvider(IProvideQa provider) {
        if (qaProviderStub == null) {
            WebServiceUtils.releaseServiceProxy(provider, IProvideQa.class);
        }
    }

    private byte[] renderData(ConversionData rndData, boolean preview) throws Exception {
        DataHandler renderingResult = callRenderingService(rndData);
        byte[] pdfBytes = getRenderedData(renderingResult);
//        if (preview) {
//            pdfBytes = makePdfPreview(pdfBytes);
//        }
        return pdfBytes;
    }

    private DataHandler callRenderingService(ConversionData rndData) throws Exception {
        IProvideSinglePdfRendering pdfRndProvider = null;
        try {
            pdfRndProvider = obtainRenderingServiceProvider();
            String dbName = rndData.getDbName();
            return pdfRndProvider.renderSinglePdfByPlan(IRenderingProvider.PLAN_PDF_FOP,
                    dbName, rndData.getWml3GXmlUri(), rndData.isRawDataExists(), rndData.isJatsStatsPresent());
        } catch (Exception e) {
            throw new RetrievableException("PDF rendering aborted", e);
        } finally {
            releaseRenderingServiceProvider(pdfRndProvider);
        }
    }

    private IProvideSinglePdfRendering obtainRenderingServiceProvider() throws Exception {
        if (pdfRndProviderStub != null) {
            return pdfRndProviderStub;
        } else {
            return WebServiceUtils.getProvideSinglePdfRendering();
        }
    }

    private void releaseRenderingServiceProvider(IProvideSinglePdfRendering pdfRndProvider) {
        if (pdfRndProviderStub == null) {
            WebServiceUtils.releaseServiceProxy(pdfRndProvider, IProvideSinglePdfRendering.class);
        }
    }

    private byte[] getRenderedData(DataHandler renderingResult) throws Exception {
        if (renderingResult == null) {
            throw new Exception("Rendering result is empty");
        }
        return IOUtils.toByteArray(renderingResult.getInputStream());
    }

//    private byte[] makePdfPreview(byte[] pdfBytes) throws Exception {
//        try {
//            ByteArrayInputStream pdfContentStream = new ByteArrayInputStream(pdfBytes);
//            byte[] modPdfBytes = stampAndEncryptPdf(pdfContentStream);
//            return modPdfBytes;
//        } catch (Exception e) {
//            throw new Exception("Failed to encrypt PDF and write to filesystem", e);
//        }
//    }

//    private byte[] stampAndEncryptPdf(ByteArrayInputStream pdfContentStream) throws Exception {
//        PdfReader reader = new PdfReader(pdfContentStream);
//        ByteArrayOutputStream stampedPdfContentStream = new ByteArrayOutputStream();
//        PdfStamper stamper = new PdfStamper(reader, stampedPdfContentStream);
//
//        encryptPdf(stamper);
//        stampPdf(stamper, reader.getNumberOfPages());
//
//        stamper.close();
//        return stampedPdfContentStream.toByteArray();
//    }
//
//    private void stampPdf(PdfStamper stamper, int pageCount) throws Exception {
//        BaseFont bf = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.EMBEDDED);
//        for (int i = 1; i <= pageCount; i++) {
//            PdfGState gState = new PdfGState();
//            gState.setFillOpacity(getStampFillOpacity());
//
//            PdfContentByte over = stamper.getOverContent(i);
//            over.beginText();
//            over.setFontAndSize(bf, FONT_SIZE);
//            over.setColorFill(LIGHT_GRAY_COLOR);
//            over.saveState();
//            over.setGState(gState);
//            over.showTextAligned(PdfContentByte.ALIGN_CENTER, "For Preview Only", X_OFFSET, Y_OFFSET, ANGLE);
//            over.restoreState();
//            over.endText();
//        }
//    }

    private float getStampFillOpacity() {
        try {
            return Float.parseFloat(getProperty("cms.cochrane.pdf_preview.stamp_fill_opacity", "0.3"));
        } catch (NumberFormatException e) {
            return DEFAULT_STAMP_FILL_OPACITY;
        }
    }

//    private void encryptPdf(PdfStamper stamper) throws Exception {
//        stamper.setEncryption("".getBytes(), "w1l3ypassw0rd".getBytes(),
//                PdfWriter.ALLOW_PRINTING | PdfWriter.ALLOW_COPY, false);
//    }

    private void saveRenderingResults(ConversionData rndData, byte[] pdfBytes) throws Exception {
        String pdfUri = rndData.getTempDir() + "/" + rndData.getName() + Extensions.PDF;
        repository.putFile(pdfUri, new ByteArrayInputStream(pdfBytes));
    }

    /**
     *
     */
    public static class RenderingParams {
        public final boolean revman;
        public final boolean jats;
        public final boolean preview;

        public RenderingParams(boolean revman, boolean jats, boolean preview) {
            this.revman = revman;
            this.jats = jats;
            this.preview = preview;
        }
    }

    /**
     *
     */
    private abstract class ConditionalRepeatableOperation<T> extends RepeatableOperation {
        T result;

        public ConditionalRepeatableOperation(int retryTimes) {
            super(retryTimes);
        }

        @Override
        protected void perform() throws Exception {
            try {
                tryPerform();
            } catch (Exception e) {
                if (!(e instanceof RetrievableException)) {
                    fillCounter();
                }
                throw e;
            }
        }

        protected abstract void tryPerform() throws Exception;
    }
}
