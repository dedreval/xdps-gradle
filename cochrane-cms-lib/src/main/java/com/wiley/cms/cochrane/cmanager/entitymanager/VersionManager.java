package com.wiley.cms.cochrane.cmanager.entitymanager;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.ejb.Local;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;

import com.wiley.cms.cochrane.cmanager.CmsUtils;
import com.wiley.cms.cochrane.cmanager.PreviousVersionException;
import com.wiley.cms.cochrane.cmanager.data.PrevVO;
import com.wiley.cms.cochrane.cmanager.data.record.RecordEntity;
import com.wiley.cms.cochrane.cmanager.data.record.RecordMetadataEntity;
import com.wiley.cms.cochrane.cmanager.entity.VersionEntity;
import com.wiley.cms.cochrane.process.ModelController;
import com.wiley.cms.cochrane.repository.IRepository;
import com.wiley.cms.cochrane.repository.RepositoryFactory;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 9/25/2020
 */
@Local(IVersionManager.class)
@Stateless
public class VersionManager extends ModelController implements IVersionManager {

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public List<PrevVO> getVersions(int issueNumber, String cdNumber) {
        return checkVersions(issueNumber == 0
                ? VersionEntity.queryVersionsVO(cdNumber, getManager()).getResultList()
                : VersionEntity.queryVersionsVO(issueNumber, cdNumber, getManager()).getResultList());
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public List<PrevVO> getVersions(String cdNumber) {
        return checkVersions(VersionEntity.queryVersionsVO(cdNumber, getManager()).getResultList());
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public PrevVO getLastVersion(String cdNumber) {
        List<PrevVO> list = VersionEntity.queryVersionVO(cdNumber, getManager()).getResultList();
        return list.isEmpty() ? null : list.get(0);
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public PrevVO getVersion(String cdNumber, Integer versionNumber) {
        List<PrevVO> list = (versionNumber == null || RecordEntity.VERSION_LAST == versionNumber)
                ? VersionEntity.queryVersionVO(cdNumber, getManager()).getResultList()
                : VersionEntity.queryVersionVO(cdNumber, versionNumber, getManager()).getResultList();
        return list.isEmpty() ? null : list.get(0);
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public List<PrevVO> getLastVersions(int issueNumber, Collection<String> cdNumbers) {
        return VersionEntity.queryVersionsVO(issueNumber, cdNumbers, getManager()).getResultList();
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public ICDSRMeta populateMetadataVersion(ICDSRMeta latestMetadata) throws PreviousVersionException {

        int lastPub = latestMetadata.getPubNumber();
        List<RecordMetadataEntity> list = RecordMetadataEntity.queryRecordMetadataLast(latestMetadata.getName(),
                getManager()).getResultList();
        RecordMetadataEntity history = null;
        RecordMetadataEntity prev = null;

        Iterator<RecordMetadataEntity> it = list.iterator();
        while (it.hasNext()) {
            RecordMetadataEntity rme = it.next();
            if (CmsUtils.isSpecialIssueNumber(rme.getIssue())) {
                it.remove();
                continue;
            }
            int pub = rme.getPubNumber();
            if (lastPub > pub) {
                if (prev != null) {
                    throw wrongOrderingException(latestMetadata, prev, rme);
                }
                if (history == null) {
                    history = rme;
                    it.remove();

                } else if (pub > history.getPubNumber()) {
                    throw wrongOrderingException(latestMetadata, history, rme);
                }
            } else if (lastPub == pub) {
                if (history != null) {
                    throw wrongOrderingException(latestMetadata, history, rme);
                }
                if (prev == null) {
                    prev = rme;
                }
            } else {
                throw wrongOrderingException(latestMetadata, latestMetadata, rme);
            }
        }
        RecordMetadataEntity lastRme = find(RecordMetadataEntity.class, latestMetadata.getId());
        if (lastRme == null) {
            throw new PreviousVersionException(String.format("no metadata by [%d]", latestMetadata.getId()));
        }
        if (lastRme.isScheduled()) {
            lastRme.setIssue(lastRme.getPublishedIssue());
            getManager().merge(lastRme);
        }

        list.forEach(r -> setHistoryVersion(r, RecordEntity.VERSION_INTERMEDIATE));

        if (prev != null) {
            Integer futureNumber = prev.getVersion().getFutureHistoryNumber();
            lastRme.getVersion().setFutureHistoryNumber(futureNumber == RecordEntity.VERSION_LAST ? 1 : futureNumber);
            
        } else if (history != null) {
            setHistoryVersion(history, history.getVersion().getFutureHistoryNumber());
            lastRme.getVersion().setFutureHistoryNumber(history.getVersion().getFutureHistoryNumber() + 1);

        } else {
            lastRme.getVersion().setFutureHistoryNumber(1);
        }
        setHistoryVersion(lastRme, RecordEntity.VERSION_LAST);
        return prev != null ? prev : history;
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public void clearVersionFolders(String cdNumber) {

        IRepository rp = RepositoryFactory.getRepository();
        List<PrevVO> list = getVersions(cdNumber);
        list.forEach(pvo -> RecordHelper.deletePreviousDir(pvo.name, pvo.version, pvo.group, rp));
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public String clearVersionFolders(int issueNumber, String cdNumber)  {
        List<PrevVO> list = VersionEntity.queryVersionsVOByIssue(issueNumber, cdNumber, getManager()).getResultList();
        if (list.isEmpty()) {
            return null;
        }
        Map<Integer, PrevVO> prev = new HashMap<>();
        String ret = null;
        for (PrevVO pvo: list) {
            if (ret == null) {
                ret = pvo.group;
            }
            if (!prev.containsKey(pvo.version)) {
                prev.put(pvo.version, pvo);
            }
        }
        if (!prev.isEmpty()) {
            IRepository rp = RepositoryFactory.getRepository();
            prev.values().forEach(p -> RecordHelper.deletePreviousDir(p.name, p.version, p.group, rp));
        }
        return ret;
    }

    private PreviousVersionException wrongOrderingException(ICDSRMeta lastMeta, ICDSRMeta meta1, ICDSRMeta meta2) {
        return new PreviousVersionException(String.format(
            "wrong ordering for %s.pub%d, where a last version is pub%d [%d], but a next one is pub%d [%d]",
                lastMeta.getName(), lastMeta.getPubNumber(),
                    meta1.getPubNumber(), meta1.getId(), meta2.getPubNumber(), meta2.getId()));
    }

    private void setHistoryVersion(RecordMetadataEntity history, Integer version) {
        history.getVersion().setHistoryNumber(version);
        getManager().merge(history);
    }

    private List<PrevVO> checkVersions(List<PrevVO> ret) {
        int pub = 0;
        Iterator<PrevVO> it = ret.iterator();
        while (it.hasNext()) {
            PrevVO prev = it.next();
            if (pub == 0 || pub != prev.pub) {
                pub = prev.pub;
            } else {
                it.remove();
            }
        }
        return ret;
    }
}
