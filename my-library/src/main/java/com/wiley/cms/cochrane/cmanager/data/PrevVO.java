package com.wiley.cms.cochrane.cmanager.data;

import java.io.Serializable;

import com.wiley.cms.cochrane.cmanager.data.record.RecordMetadataEntity;
import com.wiley.cms.cochrane.utils.Constants;
import com.wiley.cms.converter.services.RevmanMetadataHelper;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 3/28/2017
 */
public class PrevVO implements Serializable {
    private static final long serialVersionUID = 1L;

    public final String name;
    public final String group;
    public final Integer version;
    public final int pub;

    public PrevVO() {
        name = Constants.NA;
        group = Constants.NA;
        pub = Constants.FIRST_PUB;
        version = Constants.FIRST_PUB;
    }

    public PrevVO(RecordMetadataEntity rme) {
        this(rme.getCdNumber(), rme.getGroupSid(), rme.getVersion().getPubNumber(),
                rme.getVersion().getFutureHistoryNumber());
    }

    public PrevVO(String cdNumber, String crgGroup, int pubNumber, Integer historyNumber) {
        name = cdNumber;
        group = crgGroup;
        pub = pubNumber;
        version = historyNumber;
    }

    public String buildDoi() {
        return RevmanMetadataHelper.buildDoi(name, pub);
    }

    public String buildPubName() {
        return RevmanMetadataHelper.buildPubName(name, pub);
    }

    @Override
    public String toString() {
        return String.format("%s.%d %s v%d", name, pub, group, version);
    }
}
