package com.wiley.cms.converter.services;

import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.ISinglePdfRenderingManager;
import com.wiley.cms.cochrane.cmanager.MessageSender;
import com.wiley.cms.cochrane.cmanager.SinglePdfRenderingManager;
import com.wiley.cms.cochrane.cmanager.entitymanager.AbstractManager;
import com.wiley.cms.cochrane.repository.IRepository;
import com.wiley.cms.cochrane.repository.RepositoryFactory;
import com.wiley.tes.util.InputUtils;
import com.wiley.tes.util.Logger;
import com.wiley.tes.util.UserFriendlyMessageBuilder;
import org.apache.commons.lang.StringUtils;
import org.jboss.ws.api.annotation.WebContext;

import javax.activation.DataHandler;
import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.jws.WebParam;
import javax.jws.WebService;
import javax.jws.soap.SOAPBinding;
import javax.mail.util.ByteArrayDataSource;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * @author <a href='mailto:ikirov@wiley.com'>Igor Kirov</a>
 * @version 05.09.11
 */
@Stateless
@WebService(name = "IProvideConversion", targetNamespace = IProvideConversion.NAMESPACE)
@SOAPBinding(style = SOAPBinding.Style.DOCUMENT, parameterStyle = SOAPBinding.ParameterStyle.WRAPPED)
@WebContext(contextRoot = "/CochraneCMS")
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class ProvideConversion implements IProvideConversion {
    private static final Logger LOG = Logger.getLogger(ProvideConversion.class);
    private static final SinglePdfRenderingManager.RenderingParams DEFAULT_RND_PARAMS =
            new SinglePdfRenderingManager.RenderingParams(true, false, true);

    @EJB(beanName = "SinglePdfRenderingManager")
    private ISinglePdfRenderingManager singlePdfRndManager;

    public ConversionResult convert(@WebParam(name = "sourceXml", targetNamespace = NAMESPACE) DataHandler source,
                                    @WebParam(name = "metadataXml", targetNamespace = NAMESPACE) DataHandler metadata) {
        DataHandler result = null;
        ConversionData data = null;
        String errorMsg = "";
        boolean success = false;
        long stTime = System.currentTimeMillis();
        try {
            data = readRequestData(source, metadata);
            byte[] pdfBytes = singlePdfRndManager.renderPdf(data, DEFAULT_RND_PARAMS);
            result = new DataHandler(new ByteArrayDataSource(pdfBytes, null));

            long pTime = System.currentTimeMillis() - stTime;
            success = !isTimeoutExceeded(pTime);
            if (!success) {
                errorMsg = CochraneCMSPropertyNames.getPreviewServiceResponseTimeoutExceededMsg(pTime);
                LOG.error(errorMsg);
            }
        } catch (Exception e) {
            success = false;
            String msg = UserFriendlyMessageBuilder.build(e);
            errorMsg = CochraneCMSPropertyNames.getPreviewServiceProcessingFailedMsg(msg);
            LOG.error(e, e);
        } finally {
            if (success && !CochraneCMSPropertyNames.isRevmanConversionDebugMode()) {
                try {
                    IRepository rp = AbstractManager.getRepository();
                    if (rp.isFileExists(data.getTempDir())) {
                        rp.deleteDir(data.getTempDir());
                    }
                } catch (Exception ignored) {
                    LOG.error(ignored);
                }
            }
            if (!success) {
                sendErrorMsg(data, errorMsg);
            }
        }

        return new ConversionResult(success, errorMsg, result);
    }

    private ConversionData readRequestData(DataHandler source, DataHandler metadata) throws IOException {
        ConversionData data = new ConversionData();
        data.setSourceXml(InputUtils.readStreamToString(source.getInputStream(), StandardCharsets.ISO_8859_1.name()));
        data.setMetadataXml(InputUtils.readStreamToString(metadata.getInputStream()));
        saveRequestData(data);
        return data;
    }

    private void saveRequestData(ConversionData data) throws IOException {
        IRepository rp = RepositoryFactory.getRepository();
        rp.putFile(data.getTempDir() + "/revman.xml",
                new ByteArrayInputStream(data.getSourceXml().getBytes(StandardCharsets.ISO_8859_1.name())));
        rp.putFile(data.getTempDir() + "/metadata.xml",
                new ByteArrayInputStream(data.getMetadataXml().getBytes(StandardCharsets.UTF_8.name())));
    }

    private boolean isTimeoutExceeded(long time) {
        return (time >= CochraneCMSPropertyNames.getPreviewServiceResponseTimeout());
    }

    private void sendErrorMsg(ConversionData data, String error) {
        String tmpDir = (data == null || StringUtils.isEmpty(data.getTempDir()))
                ? CochraneCMSPropertyNames.getNotAvailableMsg()
                : data.getTempDir();

        Map<String, String> map = new HashMap<String, String>();
        map.put(MessageSender.MSG_PARAM_ERROR, error);
        map.put(MessageSender.MSG_PARAM_REPORT, tmpDir);

        MessageSender.sendMessage(MessageSender.MSG_TITLE_PREVIEW_SERVICE_EXCEPTION, map);
    }
}
