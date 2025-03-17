package com.wiley.cms.cochrane.cmanager;

import javax.ejb.EJBException;

import com.wiley.cms.cochrane.cmanager.contentworker.ArchieEntry;
import com.wiley.cms.cochrane.utils.ErrorInfo;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 07.06.12
 */
public class CmsJTException extends EJBException implements ICmsException {
    private static final long serialVersionUID = 1;

    private final CmsException cmsException;

    public CmsJTException(ErrorInfo errInfo) {
        this(new CmsException(errInfo));
    }

    public CmsJTException(String msg) {
        this(new CmsException(msg));
    }

    private CmsJTException(CmsException ex) {
        cmsException = ex;
    }

    @Override
    public CmsException get() {
        return cmsException;
    }

    @Override
    public boolean hasErrorInfo() {
        return getErrorInfo() != null;
    }

    @Override
    public ErrorInfo getErrorInfo() {
        return get().getErrorInfo();
    }

    @Override
    public String getMessage() {
        return get().getMessage();
    }

    public static CmsJTException create(ArchieEntry meta, ErrorInfo.Type type) {
        return new CmsJTException(new ErrorInfo<>(meta, type, type.getMsg()));
    }

    public static CmsJTException create(ArchieEntry meta, ErrorInfo.Type type, String message) {
        return new CmsJTException(new ErrorInfo<>(meta, type, message));
    }

    public static CmsJTException createForContent(ArchieEntry meta, String message) {
        return new CmsJTException(new ErrorInfo<>(meta, ErrorInfo.Type.CONTENT, message));
    }

    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }
}
