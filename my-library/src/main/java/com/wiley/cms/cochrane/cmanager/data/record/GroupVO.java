package com.wiley.cms.cochrane.cmanager.data.record;

import java.io.Serializable;

import com.wiley.cms.cochrane.cmanager.CmsException;
import com.wiley.cms.cochrane.cmanager.contentworker.ArchieEntry;
import com.wiley.cms.cochrane.process.IRecordCache;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 05.12.12
 */
public class GroupVO implements Serializable, IGroupVO {
    public static final String SID_EDITORIAL = "EDITORIAL";
    public static final String SID_EDI = "CEU";
    public static final String SID_CCA = "CCA";

    private static final long serialVersionUID = 1L;

    private int id;
    private String title;
    private String sid;

    public GroupVO(int id) {
        setId(id);
    }

    public GroupVO(GroupEntity entity) {

        setId(entity.getId());
        setName(entity.getSid());
        setUnitTitle(entity.getTitle());
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return sid;
    }

    public void setName(String name) {
        sid = name;
    }

    public String getUnitTitle() {
        return title;
    }

    public void setUnitTitle(String title) {
        this.title = "Cochrane " + title;
    }

    public static GroupVO getGroup(String cdNumber, int pub, String version, String groupId, IRecordCache cache)
            throws CmsException {
        try {
            GroupVO group = cache.getCRGGroup(groupId);
            if (group == null) {
                throw new CmsException(String.format("a crg group: %s not found", groupId));
            }
            return group;

        } catch (CmsException ce) {
            throw CmsException.createForMetadata(new ArchieEntry(cdNumber, pub, version), ce.getMessage());
        }
    }
}
