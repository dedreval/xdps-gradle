package com.wiley.cms.cochrane.cmanager.res;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.wiley.cms.cochrane.cmanager.data.record.RecordMetadataEntity;
import com.wiley.cms.cochrane.cmanager.entitymanager.ICDSRMeta;
import com.wiley.cms.converter.services.RevmanMetadataHelper;
import com.wiley.tes.util.res.JaxbResourceFactory;
import com.wiley.tes.util.res.Res;
import com.wiley.tes.util.res.ResourceManager;
import com.wiley.tes.util.res.ResourceStrId;
import com.wiley.tes.util.res.SingletonRes;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 4/2/2015
 */
@XmlRootElement(name = RevmanTransitions.RES_NAME)
public class RevmanTransitions extends ResourceStrId {
    static final String RES_NAME = "statetransition";

    private static final long serialVersionUID = 1L;

    private static final int CITATION_OFFSET = 1000;
    private static final int INVALID_STATUS = -1;

    private static final SingletonRes<RevmanTransitions> REF = new SingletonRes<>("revman-status-transitions", null);
    private static final JaxbResourceFactory FACTORY = JaxbResourceFactory.create(RevmanTransitions.class,
            TransitionSet.TransitionTo.class);

    private Map<Integer, TransitionHolder> reviewTransitions = new HashMap<>();
    private Map<Integer, TransitionHolder> protocolTransitions = new HashMap<>();
    private Map<Integer, TransitionHolder> uProtocolTransitions = new HashMap<>();
    private TransitionHolder firstTransition;

    public RevmanTransitions() {
        setId(REF.getName());
    }

    public static void register(ResourceManager loader) {
        loader.register(RES_NAME, FACTORY);
    }

    public static Res<RevmanTransitions> get() {
        return REF.getResource();
    }

    static int resolveRevmanStatus(String status, boolean citation) {
        RecordMetadataEntity.RevmanStatus revmanStatus = RecordMetadataEntity.RevmanStatus.getEntity(status);
        if (revmanStatus != null) {
            return resolveRevmanStatus(revmanStatus.dbKey, citation);
        }
        return INVALID_STATUS;
    }

    private static int resolveRevmanStatus(int statusId, boolean citation) {
        return citation ? CITATION_OFFSET + statusId : statusId;
    }

    public void checkTransition(ICDSRMeta newEntity, ICDSRMeta prevEntity) throws Exception {

        TransitionHolder th;
        if (prevEntity == null) {
            th = firstTransition;
        } else {
            int prevStatus = prevEntity.getStatus();
            //th = (prevEntity.isStageP()) ? protocolTransitions.get(prevStatus) : reviewTransitions.get(prevStatus);
            th = prevEntity.isStageR() ? reviewTransitions.get(prevStatus)
                : (prevEntity.isStageP() ? protocolTransitions.get(prevStatus) : uProtocolTransitions.get(prevStatus));
        }

        boolean review = newEntity.isStageR();
        boolean protocol = !review && newEntity.isStageP();
        boolean uProtocol = !review && !protocol && newEntity.isStageUP();

        int status = newEntity.getStatus();
        boolean citation = newEntity.isNewCitation();
        if (th == null || !th.containsTransition(status, review, protocol, uProtocol, citation)) {
            throw new Exception(createTransitionErrorMsg(citation, status, review, protocol, uProtocol,
                    prevEntity == null ? RecordMetadataEntity.RevmanStatus.NONE.dbKey : prevEntity.getStatus(),
                    prevEntity == null || prevEntity.isStageP()));
        }
    }

    private static String createTransitionErrorMsg(boolean isCitation, int status, boolean review, boolean protocol,
                                                   boolean uProtocol, int prevStatus, boolean prevProtocol) {

        String newState = RecordMetadataEntity.RevmanStatus.getStatusName(status);
        String prevState = RecordMetadataEntity.RevmanStatus.getStatusName(prevStatus);
        String citation = isCitation ? "with new citation" : "";

        return  (RecordMetadataEntity.RevmanStatus.NONE.dbKey == prevStatus)
            ? String.format("this %s is %s, but a previous record does not exist",
                getStageString(review, protocol, uProtocol), newState)
            : String.format("this %s %s is %s, but a previous status of %s was %s",
                getStageString(review, protocol, uProtocol), citation, newState,
                getStageString(!prevProtocol, prevProtocol, prevProtocol), prevState);
    }

    private static String getStageString(boolean review, boolean protocol, boolean uProtocol) {
        return review ? "review" : (protocol ? "protocol" : (uProtocol ? "update protocol" : "unknown"));
    }

    @XmlElement(name = "transition")
    public void setTransitions(TransitionSet.TransitionTo[] transitions) {

        reviewTransitions.clear();
        protocolTransitions.clear();
        uProtocolTransitions.clear();

        Map<Integer, TransitionHolder> rTransitions = new HashMap<>();
        Map<Integer, TransitionHolder> pTransitions = new HashMap<>();
        Map<Integer, TransitionHolder> upTransitions = new HashMap<>();

        for (TransitionSet.TransitionTo tTo: transitions) {

            String ref = tTo.getTransitionSetRef();

            TransitionHolder holder = (ref != null) ? new ExTransitionHolder(tTo.getStatus(), tTo.getType(), ref)
                : new TransitionHolder(tTo.getStatus(), tTo.getType(), new HashSet<>(), new HashSet<>(),
                    new HashSet<>());

            TransitionSet.Transition[] trs = tTo.getTransitionsTo();
            if (trs != null) {
                for (TransitionSet.Transition t: trs) {
                    holder.addTransition(t);
                }
            }

            if (RecordMetadataEntity.isStageR(tTo.getType())) {
                rTransitions.put(holder.getStatusId(), holder);

            }  else if (RecordMetadataEntity.isStageP(tTo.getType())) {
                pTransitions.put(holder.getStatusId(), holder);

            }  else if (RecordMetadataEntity.isStageUP(tTo.getType())) {
                upTransitions.put(holder.getStatusId(), holder);

            } else if (firstTransition == null) {
                firstTransition = holder;

            } else {
                firstTransition.addPTransition(holder.setP);
                firstTransition.addRTransition(holder.setR);
            }
        }

        reviewTransitions = rTransitions;
        protocolTransitions = pTransitions;
        uProtocolTransitions = upTransitions;
    }

    @Override
    protected void resolve() {
        resolve(reviewTransitions);
        resolve(protocolTransitions);
        resolve(uProtocolTransitions);
    }

    @Override
    protected void populate() {
        REF.publish(this);
    }

    private static void resolve(Map<Integer, TransitionHolder> transitionSets) {

        for (int statusId: transitionSets.keySet()) {
            if (statusId == INVALID_STATUS) {
                continue;
            }
            TransitionHolder holder = transitionSets.get(statusId);
            if (holder.resolveNeed()) {
                transitionSets.put(statusId, new TransitionHolder(statusId, holder.type, holder.setR, holder.setP,
                        holder.setUP));
            }
        }
    }

    @Override
    protected boolean check() {
        return check(reviewTransitions) || check(protocolTransitions) || check(uProtocolTransitions);
    }

    private static boolean check(Map<Integer, TransitionHolder> transitionSets) {

        boolean ret = true;
        for (int statusId: transitionSets.keySet()) {
            if (statusId == INVALID_STATUS) {
                LOG.info(String.format("transition map %s has invalid status!", transitionSets.get(statusId).type));
                ret = false;
                break;
            }

            TransitionHolder holder = transitionSets.get(statusId);
            if (!holder.check(holder.setP, RevmanMetadataHelper.STAGE_P)
                    || !holder.check(holder.setR, RevmanMetadataHelper.STAGE_R)) {
                ret = false;
                break;
            }

        }
        return ret;
    }

    private static class ExTransitionHolder extends TransitionHolder {

        private final Res<TransitionSet> setRef;

        ExTransitionHolder(String status, String type, String ref) {
            super(status, type, new HashSet<>(), new HashSet<>(), new HashSet<>());
            setRef = ref == null ? null : TransitionSet.get(ref);
        }

        @Override
        public boolean resolveNeed() {

            if (setRef != null && setRef.exist()) {

                for (String type: setRef.get().getTypes()) {
                    if (RecordMetadataEntity.isStageR(type)) {
                        addRTransition(setRef.get().getSet(type));
                    } else if (RecordMetadataEntity.isStageP(type)) {
                        addPTransition(setRef.get().getSet(type));
                    } else if (RecordMetadataEntity.isStageUP(type)) {
                        addUPTransition(setRef.get().getSet(type));
                    }
                }
            }
            return true;
        }
    }

    private static class TransitionHolder {

        private final int statusId;
        private final String type;
        private final Set<Integer> setR;
        private final Set<Integer> setP;
        private final Set<Integer> setUP;

        TransitionHolder(int statusId, String type, Set<Integer> setR, Set<Integer> setP, Set<Integer> setUP) {
            this.setR = setR;
            this.setP = setP;
            this.setUP = setUP;
            this.statusId = statusId;
            this.type = type;
        }

        TransitionHolder(String status, String type, Set<Integer> setR, Set<Integer> setP, Set<Integer> setUP) {
            this(resolveRevmanStatus(status, false), type, setR, setP, setUP);
        }

        boolean resolveNeed() {
            return false;
        }

        boolean containsTransition(int statusId, boolean review, boolean protocol, boolean uProtocol, boolean cit) {
            int citationStatusId = resolveRevmanStatus(statusId, cit);
            //return protocol ? setP.contains(citationStatusId) : setR.contains(citationStatusId);
            return review ? setR.contains(citationStatusId) : (protocol
                    ? setP.contains(citationStatusId) : (uProtocol && setUP.contains(citationStatusId)));
        }

        void addRTransition(Set<Integer> set) {
            setR.addAll(set);
        }

        void addPTransition(Set<Integer> set) {
            setP.addAll(set);
        }

        void addUPTransition(Set<Integer> set) {
            setUP.addAll(set);
        }

        void addTransition(TransitionSet.Transition t) {
            if (RecordMetadataEntity.isStageR(t.getType())) {
                setR.add(resolveRevmanStatus(t.getStatus(), t.isCitation()));

            } else if (RecordMetadataEntity.isStageP(t.getType())) {
                setP.add(resolveRevmanStatus(t.getStatus(), t.isCitation()));

            } else if (RecordMetadataEntity.isStageUP(t.getType())) {
                setUP.add(resolveRevmanStatus(t.getStatus(), t.isCitation()));
            }
        }

        int getStatusId() {
            return statusId;
        }

        boolean check(Set<Integer> set, String toType) {

            for (int id: set) {
                if (id == INVALID_STATUS) {
                    LOG.info(String.format("transition %s map has invalid status for %s transitions!", type, toType));
                    return false;
                }
            }
            return true;
        }

        public String toString() {
            return String.format("status %d %s (%s)", unResolveRevmanStatus(statusId), type,
                    statusId >= CITATION_OFFSET ? "C" : "");
        }

        private static int unResolveRevmanStatus(int statusId) {
            int ret = statusId;
            if (statusId >= CITATION_OFFSET) {
                ret -= CITATION_OFFSET;
            }
            return ret;
        }
    }
}
