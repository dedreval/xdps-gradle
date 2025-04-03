package com.wiley.cms.cochrane.cmanager.export.process;

import java.util.HashMap;
import java.util.Set;

import com.wiley.cms.cochrane.cmanager.CmsException;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.FilePathBuilder;
import com.wiley.cms.cochrane.cmanager.MessageParameters;
import com.wiley.tes.util.Extensions;
import com.wiley.tes.util.Logger;

/**
 * @author <a href='mailto:ikirov@wiley.com'>Igor Kirov</a>
 * @version 11.08.2011
 */
public class EntireExportProcessor extends ExportProcessor {
    public EntireExportProcessor(ExportParameters params, Set<Integer> items) throws CmsException {
        super(params, items);
    }

    @Override
    protected void init() throws CmsException {
        exporter = new EntireExporter(params, items, filePath);
        log = Logger.getLogger(EntireExportProcessor.class);
    }

    @Override
    protected String getMessage(String errs, boolean hasErrs) {
        String path = exporter.buildExportPaths();

        HashMap<String, String> map = new HashMap<>();
        map.put(MessageParameters.NAME, params.getDbName());
        map.put(MessageParameters.PATH, path);
        map.put(MessageParameters.ERRS, errs);

        return (hasErrs
                ? CochraneCMSPropertyNames.getExportEntireCompletedWithErrors(map)
                : CochraneCMSPropertyNames.getExportEntireCompletedSuccessfully(map));
    }

    @Override
    protected String generateFilePath() {
        return FilePathBuilder.getPathToEntireExport(params.getDbName()) + date.getTime() + Extensions.ZIP;
    }
}
