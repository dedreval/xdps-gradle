package com.wiley.cms.cochrane.cmanager.entitywrapper;

import com.wiley.cms.cochrane.cmanager.data.PrevVO;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author <a href='mailto:aasadulin@wiley.com'>Alexandr Asadulin</a>
 * @version 12.10.2016
 */
public class RecordVersions implements Serializable {

    public static final int UNDEFINED_VERSION = 0;
    static final RecordVersions UNDEF_VERSION_INSTANCE = new RecordVersions();
    private static final long serialVersionUID = 1L;

    private final int version;
    private int pub = UNDEFINED_VERSION;
    private final List<PrevVO> prevVersions;

    private RecordVersions() {
        version = -1;
        prevVersions = Collections.emptyList();
    }

    public RecordVersions(int version, int pub) {
        this.version = version;
        this.pub = pub;
        prevVersions = Collections.emptyList();
    }

    public RecordVersions(int version, int pub, boolean allPrevious, List<PrevVO> list) {
        this.version = version;
        this.pub = pub;

        if (list.size() > 1 && !allPrevious) {

            prevVersions = new ArrayList<PrevVO>();
            prevVersions.add(list.get(0));

        } else {
            prevVersions = list;
        }
    }

    public int getVersion() {
        return version;
    }

    public int getPub() {
        return pub;
    }

    public boolean isPreviousVersionExist() {
        return !prevVersions.isEmpty();
    }

    public int getPreviousVersion() {
        return prevVersions.get(0).version;
    }

    public List<PrevVO> getPreviousVersionsVO() {
        return prevVersions;
    }

    public List<Integer> getPreviousVersions() {

        List<Integer> ret = new ArrayList<Integer>(1);
        for (PrevVO vo: prevVersions) {
            if (vo.version == version) {
                continue;
            }
            ret.add(vo.version);
        }
        return ret;
    }

    /*private void addByOrder(List<Integer> list, int version) {
        int i = 0;
        for (Integer v: list) {
            if (v > version) {
                break;
            }
            i++;
        }
        list.add(i, version);
    }*/
}
