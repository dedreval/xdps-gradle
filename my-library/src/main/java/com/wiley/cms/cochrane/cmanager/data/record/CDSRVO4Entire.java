package com.wiley.cms.cochrane.cmanager.data.record;

import com.wiley.cms.cochrane.cmanager.CmsUtils;
import com.wiley.cms.cochrane.cmanager.FilePathCreator;
import com.wiley.cms.cochrane.cmanager.data.entire.EntireDBEntity;

/**
 * This class is used by TOC generation process. With migrating to monthly
 * releases from quarterly releases TOC generation process is used data from
 * new entities (PreviousDBEntity and EntireDBEntity instead of CDSREntity and RecordVO).
 *
 * @author <a href="mailto:dkotsubo@wiley.com">Dmitry Kotsubo</a>
 * @version 14.01.2010
 */
public class CDSRVO4Entire extends CDSRVO {

    EntireDBEntity entEntity;

    private String recordPath;
    private int number;
    private int year;

    public CDSRVO4Entire() {
    }

    public CDSRVO4Entire(EntireDBEntity ee) {
        //if (rme != null) {
        //    setId(rme.getId());
        //    setDoi(RevmanMetadataHelper.buildDoi(rme.getCdNumber(), rme.getPubNumber()));
        //    setVersionNumber(rme.getHistoryNumber());
        //    setReviewType(rme.getType());
        //    setGroupSid(rme.getGroup().getSid());
        //}
        setRecordName(ee.getName());
        this.entEntity = ee;
        year = CmsUtils.getYearByIssueNumber(entEntity.getLastIssuePublished());
        number = CmsUtils.getIssueByIssueNumber(entEntity.getLastIssuePublished());
    }

    @Override
    public String getName() {
        return getRecordName();
    }

    public boolean isCurrent(int number, int year) {
        return ((this.number == number) && (this.year == year));
    }

    @Override
    public String getUnitTitle() {
        return entEntity.getUnitTitle();
    }

    @Override
    public String getRecordPath() {
        if (recordPath == null) {
            this.recordPath = FilePathCreator.getFilePathToSourceEntire(
                    entEntity.getDbName(), getRecordName());
        }

        return recordPath;
    }

    @Override
    public void setRecordPath(String recordPath) {
        this.recordPath = recordPath;
    }

    public int getUnitStatusId() {
        return entEntity.getUnitStatus() != null ? entEntity.getUnitStatus().getId() : 0;
    }

    @Override
    public UnitStatusVO getUnitStatus() {
        return (entEntity.getUnitStatus() != null)
                ? (new UnitStatusVO(entEntity.getUnitStatus()))
                : null;
    }

    @Override
    public ProductSubtitleVO getProductSubtitle() {
        return (new ProductSubtitleVO(entEntity.getProductSubtitle()));
    }

    public int getYear() {
        return year;
    }

    public int getNumber() {
        return number;
    }
}