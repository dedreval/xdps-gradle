package com.wiley.cms.converter.services;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.wiley.cms.cochrane.cmanager.DeliveryPackageInfo;
import com.wiley.cms.cochrane.cmanager.data.issue.IssueVO;
import com.wiley.cms.cochrane.cmanager.translated.TranslatedAbstractVO;
import com.wiley.cms.cochrane.utils.ErrorInfo;

/**
 * @author <a href='mailto:ikirov@wiley.com'>Igor Kirov</a>
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * @version 02.07.12
 */
public interface IConversionProcess {

    void convertWithStrictValidation(ConversionData data) throws Exception;

    /**
     * It converts RevMan group to WileyML21 for the particularly issue and package, saves results in the issue
     * @param srcPath           The path of source Revman group
     * @param deliveryFileId    The identifier of the delivery package
     * @param deliveryFileName  The name of delivery package
     * @param issue             The issue
     * @param result            The container with conversion results
     * @param includedNames     If not NULL - records included in this set only will be converted
     * @return                  Converting result list
     * @throws Exception
     */
    List<ErrorInfo> convert(String srcPath, IssueVO issue, int deliveryFileId, String deliveryFileName,
        DeliveryPackageInfo result, Set<String> includedNames) throws Exception;

    /*List<ErrorInfo> convert(URI srcUri, String destPath, int issueYear, int issueNumber,
        boolean saveMetadata, Set<String> includedNames) throws Exception;*/

    /**
     * It converts RevMan group to WileyML21 for the particularly issue and saves results in a destination path
     * @param srcUri        The path of source Revman group
     * @param destPath      The destination path
     * @param issue         The issue
     * @param includedNames If not NULL - records included in this set only will be converted
     * @return              Error list
     * @throws Exception
     */
    List<ErrorInfo> convert(String srcUri, String destPath, IssueVO issue, Set<String> includedNames)
            throws Exception;

    /**
     * It converts RevMan articles to WileyML21 and saves results in a destination path
     * @param srcUri        The path of source Revman group
     * @param destPath      The destination path
     * @param includedNames If not NULL - records included in this set only will be converted
     * @return              Error list
     * @throws Exception
     */
    List<ErrorInfo> convert(String srcUri, String destPath, Set<String> includedNames) throws Exception;

    /**
     * It converts RevMan translations 3.0 to WileyML21 and saves results in a destination path
     * @param trPath          The path of translations folder
     * @param destPath        The destination path
     * @param translations    The map with translations details where an initial translation file name -> VO.
     *                        The path field of this VO is mandatory and contains the name of result's file.
     * @param issue           It defines the Issue the articles metadata should be taken.
     *                        If NULL, entire metadata will be used.
     * @param result          The place to result paths will be saved. It can be NULL.
     * @param includedNames   If not NULL - records included in this set only will be converted.
     * @return                Error list
     * @throws Exception
     */
    List<ErrorInfo> convertTranslations(String trPath, String destPath, Map<String, TranslatedAbstractVO> translations,
        IssueVO issue, DeliveryPackageInfo result, Set<String> includedNames) throws Exception;
}
