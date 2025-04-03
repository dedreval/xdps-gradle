package com.wiley.cms.cochrane.cmanager;

import java.util.Collection;

import com.wiley.cms.cochrane.cmanager.data.DeliveryFileVO;
import com.wiley.cms.cochrane.cmanager.data.record.IRecord;
import com.wiley.cms.cochrane.cmanager.data.record.RecordEntity;
import com.wiley.cms.cochrane.cmanager.res.BaseType;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 23.05.12
 */
public interface IDeliveringService {

    void finishUpload(int issueId, int dfId, Collection<Record> records);

    void finishUpload(int issueId, DeliveryFileVO df, String dbName, Collection<Record> records, int jobId);

    void finishUpload(BaseType baseType, int issueId, DeliveryFileVO df, int jobId);

    void finishUpload(int issueId, int dfId, String dbName, int jobId);

    void loadContent(DeliveryFileVO df, Collection<Record> records, boolean cdsr, boolean dashBoard, int jobId);

    void reloadContent(Integer dfId, Collection<IRecord> records, boolean cdsr, int jobId);

    void loadScheduledContent(RecordEntity re);
}
