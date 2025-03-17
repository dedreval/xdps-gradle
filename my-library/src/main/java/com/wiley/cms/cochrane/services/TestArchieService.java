package com.wiley.cms.cochrane.services;

import java.io.File;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import org.cochrane.archie.service.Publishing;

import com.wiley.cms.cochrane.cmanager.CmsUtils;
import com.wiley.cms.cochrane.cmanager.CochraneCMSBeans;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.ContentManagerFactory;
import com.wiley.cms.cochrane.cmanager.contentworker.ArchieEntry;
import com.wiley.cms.cochrane.cmanager.contentworker.ArchiePackage;
import com.wiley.cms.cochrane.cmanager.contentworker.ArchieResponseBuilder;
import com.wiley.cms.cochrane.cmanager.contentworker.RevmanPackage;
import com.wiley.cms.cochrane.cmanager.data.translation.PublishedAbstractEntity;
import com.wiley.cms.cochrane.cmanager.publish.IPublishStorage;
import com.wiley.cms.cochrane.cmanager.translated.TranslatedAbstractVO;
import com.wiley.cms.cochrane.utils.SSLChecker;
import com.wiley.cms.qaservice.services.WebServiceUtils;
import com.wiley.tes.util.Logger;
import com.wiley.tes.util.Now;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 14.01.13
 *  example of the parameters:
 *  http://localhost:8080/CochraneCMS-data/restful-services/publish?reviews=10&translations=5&deleted=3
 *  &publish_date=2013-01-11T0:00:00.000-05:00
 */

@Path("/publish")
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class TestArchieService {
    private static final Logger LOG = Logger.getLogger(TestArchieService.class);

    @Path("/download")
    @GET
    @Produces(MediaType.TEXT_HTML)
    public String download() {
        LOG.info("call Publishing.getContentForPublication() ...");
        try {
            LocalDate ld = CmsUtils.getCochraneDownloaderDate();
            int year = ld.getYear();
            int month = ld.getMonthValue();

            File ret = ArchiePackage.downloadArchiePackage(year, month, year, month,
                    ContentManagerFactory.getFactory().getInstance());
            if (ret == null) {
                throw new Exception("packet is empty");
            }
            LOG.info("package for publication: " + ret.getAbsolutePath());
            return ret.getAbsolutePath();

        } catch (Exception e) {
            LOG.error(e.getMessage());
            return e.getMessage();
        }
    }

    @Path("/notify")
    @GET
    @Produces(MediaType.TEXT_HTML)
    public String notifyOnPublished(@QueryParam("wr_record_id") Integer wrRecordId,
                                    @QueryParam("wr_date") String wrDate) {

        IPublishStorage ps = CochraneCMSBeans.getPublishStorage();
        List<Integer> ids = Collections.singletonList(wrRecordId);
        List<PublishedAbstractEntity> list = ps.getWhenReadyByIds(ids);

        ArchieResponseBuilder rb = ArchieResponseBuilder.createOnPublished(new Date(), "" + list.size());
        for (PublishedAbstractEntity pe: list) {
            boolean tr = pe.hasLanguage();
            rb.setForTranslations(tr);
            rb.sPD(pe.sPD().is(), pe.sPD().off());
            rb.addContent(rb.asSuccessfulElement(tr ? new TranslatedAbstractVO(pe) : new ArchieEntry(pe), wrDate));
        }
        String out = rb.getPrettyBody();
        RevmanPackage.notifyPublished(rb, null);
        return String.format("%d wr records notified\nbody:\n%s\nerror:\n%s", list.size(), out, rb.getErrorMessage());
    }

    @GET
    @Produces(MediaType.TEXT_HTML)
    public String setForPublication(@QueryParam("reviews") String nReviews,
                                    @QueryParam("translations") String nTranslations,
        @QueryParam("deleted") String nDeletedProtocols, @QueryParam("licence") String nLicenceType,
        @QueryParam("publish_date") String publishDate) throws Exception {

        StringBuilder sb = new StringBuilder(String.format(
            "Got event: [reviews=%s, translations=%s, deleted=%s, licence=%s, publish_date=%s]",
            nReviews, nTranslations, nDeletedProtocols, nLicenceType, publishDate));

        LOG.info(sb.toString());

        int iReviews = nReviews == null ? 0 : Integer.valueOf(nReviews);
        int iTranslations = nTranslations == null ? 0 : Integer.valueOf(nTranslations);
        int iDeletedProtocols = nDeletedProtocols == null ? 0 : Integer.valueOf(nDeletedProtocols);
        int iLicenceType = nLicenceType == null ? 0 : Integer.valueOf(nLicenceType);

        if (iReviews == 0 && iTranslations == 0 && iDeletedProtocols == 0) {
            sb.append(": wrong params");
            return sb.toString();
        }

        Publishing pub = null;
        try {
            SimpleDateFormat formatter = new SimpleDateFormat(Now.DATE_TIME_FORMAT_OUT);
            Date date = publishDate == null ? new Date() : formatter.parse(publishDate);

            pub = WebServiceUtils.getPublishing();
            byte[] ret = pub.markTestSampleForPublication(CochraneCMSPropertyNames.getArchieDownloadPassword(),
                    iReviews, iTranslations, date, iDeletedProtocols, iLicenceType);
            String err = RevmanPackage.checkPackage(ret, null);
            if (err == null) {
                sb.append(": request has sent");
            } else {
                sb.append(String.format(": request hasn't been accepted because of %s, see the log", err));
            }
        } catch (Exception e) {
            if (SSLChecker.checkCertificate(CochraneCMSPropertyNames.getArchieDownloadService(), e)) {
                sb.append(String.format(
                    "truststore has been updated for %s due to %s\n please repeat the request",
                        CochraneCMSPropertyNames.getArchieDownloadService(), e.getMessage()));
            }

            LOG.error(e.getMessage(), e);
        } finally {
            WebServiceUtils.releaseServiceProxy(pub, Publishing.class);
        }

        return sb.toString();
    }
}
