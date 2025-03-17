package com.wiley.cms.cochrane.cmanager.data;

import java.io.Serializable;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import com.wiley.cms.cochrane.cmanager.DeliveryPackage;
import com.wiley.cms.cochrane.cmanager.IDeliveryFileStatus;

/**
 * Type comments here.
 *
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @version 1.0
 */
public class DeliveryFileVO implements Serializable {
    public static final Set<Integer> FINAL_FAILED_STATES = new HashSet<>();
    public static final Set<Integer> FINAL_STATES = new HashSet<>();
    public static final Set<Integer> SUCCESS_STATES = new HashSet<>();
    public static final Set<Integer> PARTIALLY_SUCCESS_STATES = new HashSet<>();

    private static final long serialVersionUID = 1L;

    private Integer id;
    private String name;
    private String vendor;
    private Date date;
    private int issue;
    private int fullIssueNumber;
    private int status;
    private int interimStatus;
    private int modifyStatus;
    private int dbId;
    private String dbName;
    private int type;
    private boolean isAut;

    static {
        FINAL_FAILED_STATES.add(IDeliveryFileStatus.STATUS_RND_SOME_FAILED);
        FINAL_FAILED_STATES.add(IDeliveryFileStatus.STATUS_RND_FAILED);
        FINAL_FAILED_STATES.add(IDeliveryFileStatus.STATUS_QAS_FINISHED_SOME_BAD);
        FINAL_FAILED_STATES.add(IDeliveryFileStatus.STATUS_VALIDATION_FAILED);
        FINAL_FAILED_STATES.add(IDeliveryFileStatus.STATUS_VALIDATION_SOME_FAILED);

        FINAL_STATES.add(IDeliveryFileStatus.STATUS_RND_FINISHED_SUCCESS);
        FINAL_STATES.add(IDeliveryFileStatus.STATUS_PACKAGE_LOADED);
        FINAL_STATES.add(IDeliveryFileStatus.STATUS_PUBLISHING_FINISHED_SUCCESS);
        FINAL_STATES.add(IDeliveryFileStatus.STATUS_RND_NOT_STARTED);
        FINAL_STATES.addAll(FINAL_FAILED_STATES);

        SUCCESS_STATES.add(IDeliveryFileStatus.STATUS_RND_FINISHED_SUCCESS);
        SUCCESS_STATES.add(IDeliveryFileStatus.STATUS_PACKAGE_LOADED);
        SUCCESS_STATES.add(IDeliveryFileStatus.STATUS_PUBLISHING_STARTED);
        SUCCESS_STATES.add(IDeliveryFileStatus.STATUS_PUBLISHING_FINISHED_SUCCESS);
        SUCCESS_STATES.add(IDeliveryFileStatus.STATUS_RND_SUCCESS_AND_FULL_FINISH);

        PARTIALLY_SUCCESS_STATES.add(IDeliveryFileStatus.STATUS_QAS_FINISHED_SOME_BAD);
        PARTIALLY_SUCCESS_STATES.add(IDeliveryFileStatus.STATUS_RND_SOME_FAILED);
        PARTIALLY_SUCCESS_STATES.add(IDeliveryFileStatus.STATUS_VALIDATION_SOME_FAILED);
    }

    public DeliveryFileVO() {
    }

    public DeliveryFileVO(DeliveryFileEntity dfe) {
        if (dfe == null) {
            return;
        }

        setId(dfe.getId());
        setName(dfe.getName());
        setVendor(dfe.getVendor());
        setDate(dfe.getDate());
        if (dfe.getIssue() != null) {
            setIssue(dfe.getIssue().getId());
            setFullIssueNumber(dfe.getIssue().getFullNumber());
        }
        if (dfe.getStatus() != null) {
            setStatus(dfe.getStatus().getId());
        }
        if (dfe.getInterimStatus() != null) {
            setInterimStatus(dfe.getInterimStatus().getId());
        }
        if (dfe.getModifyStatus() != null) {
            setModifyStatus(dfe.getModifyStatus().getId());
        }

        ClDbEntity db = dfe.getDb();
        if (db != null) {
            setDbId(db.getId());
            setDbName(db.getTitle());
        }
        type = dfe.getType();
        isAut = DeliveryFileEntity.isAriesSFTP(type) || DeliveryPackage.isAut(getName());
    }

    public boolean isAut() {
        return isAut;
    }

    public boolean isEmpty() {
        return id == null;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public final String getDbName() {
        return dbName;
    }

    public final void setDbName(final String name) {
        dbName = name;
    }

    public String getVendor() {
        return vendor;
    }

    public void setVendor(final String vendor) {
        this.vendor = vendor;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(final Date date) {
        this.date = date;
    }

    public int getIssue() {
        return issue;
    }

    public void setIssue(int issue) {
        this.issue = issue;
    }

    public final int getFullIssueNumber() {
        return fullIssueNumber;
    }

    public final void setFullIssueNumber(int number) {
        fullIssueNumber = number;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public int getInterimStatus() {
        return interimStatus;
    }

    public void setInterimStatus(int interimStatus) {
        this.interimStatus = interimStatus;
    }

    public int getModifyStatus() {
        return modifyStatus;
    }

    public void setModifyStatus(int modifyStatus) {
        this.modifyStatus = modifyStatus;
    }

    public int getDbId() {
        return dbId;
    }

    public void setDbId(int dbId) {
        this.dbId = dbId;
    }

    public int getType() {
        return type;
    }

    public boolean isRenderingStarted() {
        return getInterimStatus() == IDeliveryFileStatus.STATUS_RENDERING_STARTED;
    }

    public boolean isRenderingFullSuccessful() {
        return getStatus() == IDeliveryFileStatus.STATUS_RND_FINISHED_SUCCESS;
    }

    public boolean isRenderingNotStarted() {
        return getStatus() == IDeliveryFileStatus.STATUS_RND_NOT_STARTED;
    }

    public boolean isLoadingFinishedSuccessful() {
        return isRenderingFullSuccessful() || getStatus() == IDeliveryFileStatus.STATUS_RND_SOME_FAILED;
    }

    public boolean isLoadingFinished() {
        return FINAL_STATES.contains(getStatus());
    }
}
