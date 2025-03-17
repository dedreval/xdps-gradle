package com.wiley.cms.cochrane.converter;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import com.wiley.cms.cochrane.cmanager.CmsException;
import com.wiley.cms.cochrane.utils.ErrorInfo;
import com.wiley.tes.interfaces.BatchConversionDescription;

/**
 * @author <a href='mailto:ikirov@wiley.com'>Igor Kirov</a>
 * @version 14.05.2010
 */

public interface IConverterAdapter {

    String USW = "USW";
    String WILEY_ML21 = "WILEY_ML21";
    String WILEY_ML21_SEQ = "WILEY_ML21_SEQ";
    String WILEY_ML21_JOINED = "WILEY_ML21_JOINED";
    String WILEY_ML3G2 = "WILEY_ML3GV2";
    String WILEY_ML3G_SA = "WILEY_ML3GV2_SA";
    String WILEY_ML3GV2_HW = "WILEY_ML3GV2_HW";

    String SVG = "SVG";
    String SVG_VAL = "SVG_VAL";
    String JATS_XSD = "JATS_XSD";
    String JATS_CDSR = "JATS_CDSR";
    String WILEY_ML3G = "WILEY_ML3G";
    String WILEY_ML3G_CV = "WILEY_ML3G_CV";
    String WILEY_ML3G_SEQ = "WILEY_ML3G_SEQ";
    String WILEY_ML3G_JOINED = "WILEY_ML3G_JOINED";
    String WILEY_ML3GV2_GRAMMAR = "WILEY_ML3GV2_GRAMMAR";

    String convert(String srcPath) throws ConverterException;

    String convert(String srcPath, String sourceFormat, String resultFormat, String dbName) throws ConverterException;

    String convertSource(String source, String srcFmt, String resFmt, Map<String, String> params,
                         Map<String, String> extraXmls, boolean strictValidation) throws ConverterException;

    String convertJats(String srcPath, String srcFmt, String resFmt, Map<String, String> params,
                       Map<String, String> extraXmls, boolean strictValidation) throws ConverterException;

    String convertRevman(String source, String sourceFormat, String[] params) throws ConverterException;

    String validate(String source, String sourceFormat) throws ConverterException;

    Map<String, String> validateSVG(BatchConversionDescription source, String sourceFormat, String resultFormat)
            throws ConverterException;

    String validate(String srcPath, String sourceFormat, String resultFormat, String dbName) throws ConverterException;

    Map<String, String> convertRevmanBatch(List<String> srcPaths, String sourceFormat, List<String[]> params,
        List<ErrorInfo> errors) throws ConverterException;

    Map<String, String> validateRevmanBatch(List<String> srcPaths, String sourceFormat, List<ErrorInfo> errors)
        throws ConverterException;

    String convertSource(String srcContent, String recordName) throws ConverterException;

    List<String> convertBatch(List<String> srcPaths, String sourceFormat, String resultFormat,
                              String dbName, Map<String, String> params, List<String> assets,
                              Map<String, String> errors) throws ConverterException;

    List<String> convertBatch(List<String> srcPaths, String sourceFormat, String resultFormat, String dbName,
        int issueYear, int issueNumber, Map<String, String> errors) throws ConverterException;

    InputStream getControlFile();

    String mergeTranslations(String article, String translations) throws ConverterException, CmsException;

    Map<String, String> getWml21ToWml3gConversionSpecParam(boolean wolDestination);
}