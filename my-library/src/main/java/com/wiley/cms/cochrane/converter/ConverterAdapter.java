package com.wiley.cms.cochrane.converter;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.ejb.EJB;
import javax.ejb.Local;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;

import com.wiley.cms.cochrane.cmanager.CmsException;
import com.wiley.cms.cochrane.cmanager.CochraneCMSProperties;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.repository.IRepository;
import com.wiley.cms.cochrane.repository.RepositoryFactory;
import com.wiley.cms.cochrane.utils.ErrorInfo;
import com.wiley.cms.converter.services.RevmanMetadataHelper;
import com.wiley.tes.interfaces.BatchConversionDescription;
import com.wiley.tes.interfaces.IConversionResult;
import com.wiley.tes.interfaces.IConvertorsEJB;
import com.wiley.tes.util.InputUtils;
import com.wiley.tes.util.Logger;
import com.wiley.tes.util.XmlUtils;

/**
 * @author <a href='mailto:ikirov@wiley.com'>Igor Kirov</a>
 * @version 14.05.2010
 */
@Stateless
@Local(IConverterAdapter.class)
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class ConverterAdapter implements IConverterAdapter {
    private static final String GENERATION_MODE_ALL = "all";

    private static final Logger LOG = Logger.getLogger(ConverterAdapter.class);

    private static final int MAX_THREADS = 4;
    private static final String WML3G_VERSION = "2.0";

    @EJB(lookup = "java:global/convertors/convertors-ejb/ConvertorsEJB!com.wiley.tes.interfaces.IConvertorsEJB")
    private IConvertorsEJB converter;

    private IRepository rp = RepositoryFactory.getRepository();

    public String convert(String srcPath) throws ConverterException {

        return convert(srcPath, WILEY_ML21, WILEY_ML3G2,
            CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.CLSYSREV));
    }

    public String convertRevman(String source, String sourceFormat, String[] params) throws ConverterException {
        return convertSource(source, sourceFormat, WILEY_ML21, params);
    }

    public String validate(String source, String sourceFormat) throws ConverterException {
        try {
            return converter.convert(source, sourceFormat, sourceFormat, null, null).getErrors();
        } catch (Exception e) {
            LOG.error(e, e);
            throw new ConverterException(e);
        }
    }

    public Map<String, String> validateSVG(BatchConversionDescription convDesc, String srcFormat, String resFormat)
            throws ConverterException {
        try {
            Map<String, IConversionResult> results = converter.convert(convDesc, srcFormat, resFormat,
                                                                       MAX_THREADS);
            Map<String, String> errors = new HashMap<>();
            results.forEach((srcKey, convRes) -> {
                    if (convRes.getErrors() != null && convRes.getErrors().trim().length() > 0) {
                        errors.put(srcKey, convRes.getErrors());
                    }
                });
            return errors;
        } catch (Exception e) {
            LOG.error(e, e);
            throw new ConverterException(e);
        }
    }

    public String mergeTranslations(String article, String translations) throws ConverterException, CmsException {

        logStart(1, WILEY_ML21_SEQ, WILEY_ML21_JOINED);

        List<String> sourceList = new ArrayList<String>();
        sourceList.add(translations);
        try {
            List<IConversionResult> resList = converter.convertWithAssetsBatch(sourceList, WILEY_ML21_SEQ,
                    WILEY_ML21_JOINED, GENERATION_MODE_ALL, null, "", new ArrayList<String[]>(), MAX_THREADS);

            IConversionResult res = resList.iterator().next();
            checkResult(article, res);

            logEnd();

            if (!res.isSuccessful()) {
                throw new CmsException(res.getErrors());
            }
            return res.getResult();

        } catch (Exception e) {
            throw new ConverterException(e.getMessage(), e);
        }
    }

    public Map<String, String> convertRevmanBatch(List<String> srcPaths, String sourceFormat,
        List<String[]> params, List<ErrorInfo> errors) throws ConverterException {
        return convertBatch(srcPaths, sourceFormat, WILEY_ML21, params, errors, ErrorInfo.Type.REVMAN_CONVERSION);
    }

    public  Map<String, String> validateRevmanBatch(List<String> srcPaths, String sourceFormat, List<ErrorInfo> errors)
        throws ConverterException {
        return convertBatch(srcPaths, sourceFormat, sourceFormat, new ArrayList<>(), errors, ErrorInfo.Type.VALIDATION);
    }

    public String convertJats(String srcPath, String srcFmt, String resFmt, Map<String, String> params,
                              Map<String, String> extraXmls, boolean strictValidation) throws ConverterException {
        String source;
        try {
            source = InputUtils.readStreamToString(rp.getFile(srcPath));

        } catch (IOException ie) {
            LOG.error(ie, ie);
            throw new ConverterException(ie);
        }
        return convertSource(source, srcFmt, resFmt, params, extraXmls, strictValidation);
    }

    public String convertSource(String src, String srcFmt, String resFmt, Map<String, String> params,
                                Map<String, String> extraXmls, boolean strictValidation) throws ConverterException {
        try {
            //logStart(1, srcFmt, resFmt);
            IConversionResult result = converter.convert(src, srcFmt, resFmt, params, extraXmls);
            String ret = result.getResult();
            String err = result.getErrors();
            boolean hasErrors = err != null && !err.isEmpty();
            //logEnd();
            if (ret == null || ret.isEmpty() || (strictValidation && hasErrors)) {
                throw new CmsException(hasErrors ? err : "an empty result from conversion");
            }
            if (hasErrors) {
                LOG.warn(err);
            }
            return ret;

        } catch (Throwable e) {
            LOG.error(e, e);
            throw new ConverterException(e);
        }
    }

    public String convert(String srcPath, String sourceFormat, String resultFormat, String dbName)
        throws ConverterException {
        try {
            String src = InputUtils.readStreamToString(rp.getFile(srcPath));
            return convertSource(src, srcPath, sourceFormat, resultFormat, dbName);

        } catch (IOException e) {
            LOG.error(e, e);
            throw new ConverterException(e);
        }
    }

    public String convertSource(String srcContent, String recordName) throws ConverterException {
        return convertSource(srcContent, "Reading from stream " + recordName, WILEY_ML21, WILEY_ML3G2,
            CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.CLSYSREV));
    }

    private String convertSource(String srcContent, String sourceFormat, String resultFormat, String[] params)
        throws ConverterException {

        IConversionResult result;
        try {
            result = converter.convertWithAssets(srcContent, sourceFormat, resultFormat, GENERATION_MODE_ALL, null,
                "", params);
        } catch (Exception e) {
            LOG.error(e, e);
            throw new ConverterException(e);
        }

        checkResult("", result);

        if (!result.isSuccessful() && result.getErrors() != null) {
            throw new ConverterException(new Exception(result.getErrors()));
        }

        return result.getResult();
    }

    private String convertSource(String srcContent, String srcPath, String sourceFormat, String resultFormat,
                                 String dbName) throws ConverterException {
        IConversionResult result = callConverter(srcContent, srcPath, sourceFormat, resultFormat, dbName);
        checkResult(srcPath, result);
        return result.getResult();
    }

    public String validate(String srcPath, String sourceFormat, String resultFormat, String dbName)
        throws ConverterException {
        try {
            String src = InputUtils.readStreamToString(rp.getFile(srcPath));
            return callConverter(src, srcPath, sourceFormat, resultFormat, dbName).getErrors();

        } catch (IOException e) {
            LOG.error(e, e);
            throw new ConverterException(e);
        }
    }

    private IConversionResult callConverter(String srcContent, String srcPath, String sourceFormat, String resultFormat,
        String dbName) throws ConverterException {
        try {
            String[] params = getParams(dbName, new ArrayList<>());
            return converter.convertWithAssets(srcContent, sourceFormat, resultFormat, GENERATION_MODE_ALL, null,
                    "", params);
        } catch (Exception e) {
            LOG.error(e, e);
            throw new ConverterException(e);
        }
    }

    public List<String> convertBatch(List<String> srcPaths, String sourceFormat,
                                     String resultFormat, String dbName, Map<String, String> params,
                                     List<String> assets, Map<String, String> errors) throws ConverterException {

        List<String> adaptedParams = new ArrayList<>(params.size() * 2);
        for (String key : params.keySet()) {
            adaptedParams.add(key);
            adaptedParams.add(params.get(key));
        }

        return convertBatch(srcPaths, sourceFormat, resultFormat, dbName, adaptedParams, assets, errors);
    }

    public List<String> convertBatch(List<String> srcPaths, String sourceFormat, String resultFormat, String dbName,
        int issueYear, int issueNumber, Map<String, String> errors) throws ConverterException {

        List<String> params = new ArrayList<String>();

        if (!isML21DB(dbName)) {
            addParam(params, "issueYear", String.valueOf(issueYear));
            addParam(params, "issueNumber", String.valueOf(issueNumber));
        }

        return convertBatch(srcPaths, sourceFormat, resultFormat, dbName, params, null, errors);
    }

    public List<String> convertBatch(List<String> srcPaths, String sourceFormat, String resultFormat,
        String[] params, List<String> assets, Map<String, String> errors) throws ConverterException {
        try {
            List<IConversionResult> list =
                converter.convertWithAssetsBatch(prepareBatchSources(srcPaths, sourceFormat, resultFormat),
                    sourceFormat, resultFormat, GENERATION_MODE_ALL, assets, "", params, MAX_THREADS);

            return prepareResults(srcPaths, list, errors);

        } catch (IOException e) {
            throw new ConverterException(e.getMessage(), e);
        }
    }

    private Map<String, String> convertBatch(List<String> srcPaths, String sourceFormat, String resultFormat,
        List<String[]> listParams, List<ErrorInfo> errors, ErrorInfo.Type type) throws ConverterException {
        try {
            List<IConversionResult> resultList =
                converter.convertWithAssetsBatch(prepareBatchSources(srcPaths, sourceFormat, resultFormat),
                    sourceFormat, resultFormat, GENERATION_MODE_ALL, null, "", listParams, MAX_THREADS);
            return prepareResults(srcPaths, resultList, errors, type);

        } catch (IOException e) {
            throw new ConverterException(e.getMessage(), e);
        }
    }

    private List<String> convertBatch(List<String> srcPaths, String sourceFormat,
                                      String resultFormat, String dbName, List<String> batchParams, List<String> assets,
                                      Map<String, String> errors) throws ConverterException {
        return convertBatch(srcPaths, sourceFormat, resultFormat, getParams(dbName, batchParams),
                            assets, errors);
    }

    private void logEnd() {
        LOG.debug("< convertBatch");
    }

    private void logStart(int size, String sourceFormat, String resultFormat) {
        LOG.debug(String.format("> convertBatch, recordCount = [%d] sourceFormat = [%s] resultFormat = [%s]",
                size, sourceFormat, resultFormat));
    }

    private List<String> prepareBatchSources(List<String> srcPaths, String sourceFormat, String resultFormat)
        throws IOException {

        logStart(srcPaths.size(), sourceFormat, resultFormat);
        List<String> srcs = new ArrayList<String>();

        for (String srcPath : srcPaths) {

            String src = "";
            if (srcPath != null) {
                src = XmlUtils.getXmlContentBasedOnEstimatedEncoding(rp.getRealFilePath(srcPath));
            }
            srcs.add(src);
        }
        return srcs;
    }

    private Map<String, String> prepareResults(List<String> srcPaths, List<IConversionResult> resList,
        List<ErrorInfo> err, ErrorInfo.Type type) {

        Map<String, String> ret = new HashMap<String, String>();
        Iterator<IConversionResult> it = resList.iterator();
        for (String srcPath : srcPaths) {

            IConversionResult res = it.next();
            if (srcPath != null) {
                checkResult(srcPath, res);
                if (!res.isSuccessful()) {
                    RevmanMetadataHelper.prepareError(srcPath, type, res.getErrors(), err);
                } else {
                    ret.put(srcPath, res.getResult());
                }
            }
        }
        logEnd();
        return ret;
    }

    private List<String> prepareResults(List<String> srcPaths, List<IConversionResult> resultList,
                                        Map<String, String> errors) {
        List<String> ret = new ArrayList<>();
        Iterator<IConversionResult> it = resultList.iterator();

        for (String srcPath : srcPaths) {
            IConversionResult res = it.next();
            if (srcPath != null) {
                checkResult(srcPath, res);
                if (!res.isSuccessful()) {
                    errors.put(srcPath, res.getErrors());
                }
                ret.add(res.getResult());
            } else {
                ret.add("");
            }
        }

        logEnd();
        return ret;
    }

    private String[] getParams(String dbName, List<String> params) {

        addParam(params, "schema-version", WML3G_VERSION);
        addParam(params, "insert-dtd-declarion", "no");

        if (isML21DB(dbName)) {
            addParam(params, "productType", "Cochrane");
        }
        return params.toArray(new String[params.size()]);
    }

    private boolean isML21DB(String dbName) {
        return !(dbName.equals(CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.CLCENTRAL)) || dbName.equals(
            CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.CLCMR)));
    }

    private void addParam(List<String> params, String paramName, String paramValue) {
        params.add(paramName);
        params.add(paramValue);
    }

    private boolean checkResult(String srcPath, IConversionResult result) {
        boolean isNullResult;
        if (!result.isSuccessful()) {
            LOG.error(srcPath + " result isn't successful, errors=" + result.getErrors());
        }
        if (result.getResult() == null || result.getResult().length() == 0) {
            isNullResult = true;
            LOG.error(srcPath + " conversion result is null");
        } else {
            isNullResult = false;
        }
        return isNullResult;
    }

    public InputStream getControlFile() {
        return converter.getClass().getResourceAsStream("/converters/wml21-to-wml3g/control_file/control_file.xml");
    }

    private String checkAssets(String dirName, String recordName) throws IOException {
        int basePoz = rp.getRealFilePath(dirName).length() + 1;
        StringBuilder assetsXml = new StringBuilder();
        assetsXml.append("<java_files>\n");
        File[] files = rp.getFilesFromDir(dirName);
        if (files == null) {
            return "";
        }
        for (File aFile : files) {
            if (aFile.isFile()
                && !aFile.getName().equals("conversion-history.xml")
                && !aFile.getName().equals(recordName)
                && !aFile.getName().contains(".wml.")) {
                appendFile(aFile, basePoz, assetsXml);
            } else if (aFile.isDirectory()) {
                readDir(aFile, assetsXml, basePoz);
            }
        }
        assetsXml.append("</java_files>\n");
        return assetsXml.toString();
    }

    private void appendFile(File aFile, int basePoz, StringBuilder assetsXml) {
        String path = aFile.getAbsolutePath().substring(basePoz).replaceAll("\\\\", "/");

        if (path.substring(path.lastIndexOf(".") + 1)
            .matches("[0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9]")) {
            path = path.substring(0, path.lastIndexOf("."));
        }
        if (path.contains("&")) {
            path = path.replaceAll("&", "&amp;");
        }
        assetsXml.append("<file href=\"")
            .append(path)
            .append("\"/>\n");
    }

    private void readDir(File aDir, StringBuilder assetsXml, int basePoz) throws IOException {
        File[] files = rp.getFilesFromDir(aDir.getAbsolutePath());
        if (files == null) {
            return;
        }
        for (File aFile : files) {
            if (aFile.isFile()) {
                appendFile(aFile, basePoz, assetsXml);
            } else if (aFile.isDirectory()) {
                readDir(aFile, assetsXml, basePoz);
            }
        }
    }

    @Override
    public Map<String, String> getWml21ToWml3gConversionSpecParam(boolean wolDestination) {
        Map<String, String> params = null;
        if (wolDestination) {
            params = new HashMap<>();
            params.put("destination", "WOL");
        }
        return params == null ? Collections.emptyMap() : params;
    }
}