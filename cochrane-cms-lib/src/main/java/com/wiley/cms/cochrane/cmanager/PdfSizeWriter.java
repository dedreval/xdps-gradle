package com.wiley.cms.cochrane.cmanager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import com.wiley.cms.cochrane.cmanager.data.record.RecordStorageFactory;
import com.wiley.cms.cochrane.cmanager.data.rendering.RenderingPlan;
import com.wiley.cms.cochrane.repository.RepositoryFactory;
import com.wiley.cms.cochrane.utils.Constants;
import com.wiley.tes.util.Extensions;
import com.wiley.tes.util.InputUtils;
import com.wiley.tes.util.Logger;

/**
 * @author <a href='mailto:gkhristich@wiley.ru'>Galina Khristich</a>
 *         Date: 25-Jun-2007
 */
public class PdfSizeWriter {
    private static final Logger LOG = Logger.getLogger(PdfSizeWriter.class);
    private static final long KILO = 1024;
    private static final String K = " K)";

    private PdfSizeWriter() {
    }

    public static void writeSize(String source, String rep)  {
        try {
            File dirHtml = new File(new URI(rep + "/"
                    + FilePathCreator.getRenderedDirPath(source, RenderingPlan.HTML)));
            File dirPdf = new File(new URI(rep + "/"
                    + FilePathCreator.getRenderedDirPath(source, RenderingPlan.PDF_FOP)));
            if (!dirHtml.exists() || !dirPdf.exists()) {
                LOG.error("Not found rendered dirs " + dirHtml.getAbsolutePath() + ", " + dirPdf.getAbsolutePath());
                return;
            }
            String recordName = FilePathCreator.getSplitedUri(source)[2];
            long fullPDFLength = new File(dirPdf + "/" + recordName + ".pdf").length() / KILO;
            long abstractPDFLength = new File(dirPdf + "/" + recordName + Constants.PDF_ABSTRACT_SUFFIX
                                + Extensions.PDF).length() / KILO;
            writeSize(dirHtml, fullPDFLength, abstractPDFLength);
        } catch (Exception e) {
            LOG.error(e);
        }
    }

    public static void writePdfSize(int dfId, String recNamesString) throws IOException, URISyntaxException {
        List<String> pathsList = RecordStorageFactory.getFactory().getInstance()
            .getRecordPathByDf(dfId, true, recNamesString);
        if (pathsList == null || pathsList.size() == 0) {
            LOG.debug("Not found records for dfId=" + dfId + " to write pdf size");
            return;
        }
        LOG.debug(String.format("writePdfSize started, dfId=%d, number of records is %d", dfId, pathsList.size()));

        String rep = RepositoryFactory.getRepository().getRepositoryPlace();

        for (String source : pathsList) {
            writeSize(source, rep);
        }

        LOG.debug("writePdfSize finished");
    }

    private static void writeSize(File dirHtml, long fullPDFLength, long abstractPDFLength)
        throws IOException {

        FileOutputStream out = null;
        try {
            for (File file : dirHtml.listFiles()) {
                String name = file.getName();
                if (name.equals("pdf_head-meta.html")
                    || name.equals("pdf_head.html")
                    || name.equals("pdf_abstract_head.html")
                    || name.equals("toc.html")) {
                    String str = InputUtils.readStreamToString(new FileInputStream(file));
                    out = new FileOutputStream(file);

                    out.write(str.replace("(abstract)n/a K)", abstractPDFLength + K)
                            .replace("n/a K)", fullPDFLength + K).getBytes());
                    //out.flush();
                    out.close();
                }
            }
        } finally {
            if (out != null) {
                out.close();
            }
        }
    }
}