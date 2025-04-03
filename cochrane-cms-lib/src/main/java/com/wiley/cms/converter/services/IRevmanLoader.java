package com.wiley.cms.converter.services;

import java.util.Set;

import com.wiley.cms.cochrane.cmanager.DeliveryPackageInfo;
import com.wiley.cms.cochrane.cmanager.data.issue.IssueVO;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 11.07.12
 */
public interface IRevmanLoader {
    /**
      * It starts the asynchronous process of conversion a revman package to wiley2ml and uploading it to an issue
      * @param sources          Source revman records for conversion
      * @param packageName      The package name
      * @param deliveryFileId   The package id
      * @param issue            The issueNumber
      * @param includedNames    The specified record names to conversion. Others ones should be skipped from sources
      * @throws Exception
      */
    void loadRevmanPackage(DeliveryPackageInfo sources, String packageName, int deliveryFileId, IssueVO issue,
        Set<String> includedNames) throws Exception;

    /**
     * It starts the asynchronous process of conversion a revman package to wiley2ml and uploading it to an issue
     * @param sources          Source revman records for conversion
     * @param packageName      The package name
     * @param deliveryFileId   The package id
     * @param issue            The issueNumber
     * @throws Exception
     */
    void loadRevmanPackage(DeliveryPackageInfo sources, String packageName, int deliveryFileId, IssueVO issue)
        throws Exception;

    /**
     * It converts a revman package to wiley2ml and uploading it to an issue
     * @param sources         Source revman records for conversion
     * @param packageName     The package name
     * @param deliveryFileId  The package id
     * @param issue           The issue
     * @throws Exception
     */
    void convertRevmanPackage(DeliveryPackageInfo sources, String packageName, int deliveryFileId, IssueVO issue,
        Set<String> includedNames) throws Exception;

    void onConversionFailed(String packageName, int deliveryFileId, String msg);

    void onConversionSuccessful(String packageName, int deliveryFileId,  String msg);
}
