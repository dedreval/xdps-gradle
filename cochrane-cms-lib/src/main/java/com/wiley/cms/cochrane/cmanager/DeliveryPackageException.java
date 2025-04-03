package com.wiley.cms.cochrane.cmanager;

import com.wiley.cms.cochrane.utils.Constants;

/**
 * @author <a href='mailto:gkhristich@wiley.ru'>Galina Khristich</a>
 *         Date: 11.12.2006
 */
public class DeliveryPackageException extends Exception {
    private static final long serialVersionUID = 1L;

    private final int status;
    private final boolean silently;
    private final DeliveryPackageInfo dfToRemove;

    private String manuscriptNumber;

    public DeliveryPackageException(int status) {
        this("", status, true, null);
    }

    public DeliveryPackageException(String msg, int status) {
        this(msg, status, false, null);
    }

    public DeliveryPackageException(String msg, int status, boolean silently, DeliveryPackageInfo dfToRemove) {

        super(msg);
        this.status = status;
        this.silently = silently;
        this.dfToRemove = dfToRemove;
    }

    public DeliveryPackageException(DeliveryPackageInfo dfInfo) {
        this("a package will be removed", Constants.UNDEF, true, dfInfo);
    }

    public int getStatus() {
        return status;
    }

    public String getManuscriptNumber() {
        return manuscriptNumber;
    }

    public void setManuscriptNumber(String manuscriptNumber) {
        this.manuscriptNumber = manuscriptNumber;
    }

    public boolean removeDeliveryPackage() {
        return dfToRemove != null;
    }

    public DeliveryPackageInfo getDeliveryPackageInfoToRemove() {
        return dfToRemove;
    }

    public boolean isSilently() {
        return silently;
    }

    public static void throwPickUpError(String err) throws DeliveryPackageException {
        throw new DeliveryPackageException(err, IDeliveryFileStatus.STATUS_PICKUP_FAILED);
    }

    public static void throwNoWorkableRecordsError(String err) throws DeliveryPackageException {
        throw new DeliveryPackageException(err, IDeliveryFileStatus.STATUS_INVALID_CONTENT);
    }

    public static void throwCannotParsePackageError(String err) throws DeliveryPackageException {
        throw new DeliveryPackageException("cannot parse package: " + err, IDeliveryFileStatus.STATUS_CORRUPT_ZIP);
    }
}
