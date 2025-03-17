package com.wiley.cms.cochrane.cmanager;

import com.wiley.cms.cochrane.utils.ErrorInfo;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 8/19/2021
 */
public interface ICmsException {

    CmsException get();

    ErrorInfo getErrorInfo();

    boolean hasErrorInfo();
}
