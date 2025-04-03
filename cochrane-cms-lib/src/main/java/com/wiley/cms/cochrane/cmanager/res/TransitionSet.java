package com.wiley.cms.cochrane.cmanager.res;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.wiley.tes.util.res.DataTable;
import com.wiley.tes.util.res.JaxbResourceFactory;
import com.wiley.tes.util.res.Res;
import com.wiley.tes.util.res.ResourceManager;
import com.wiley.tes.util.res.ResourceStrId;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 4/2/2015
 */
@XmlRootElement(name = TransitionSet.RES_NAME)
public class TransitionSet extends ResourceStrId implements Serializable {

    static final String RES_NAME = "transitionset";
    private static final long serialVersionUID = 1L;

    private static final DataTable<String, TransitionSet> DT =
            new DataTable<String, TransitionSet>(TransitionSet.RES_NAME);

    private Map<String, Set<Integer>> typeSets = new HashMap<String, Set<Integer>>();

    public static void register(ResourceManager loader) {
        loader.register(TransitionSet.RES_NAME, JaxbResourceFactory.create(TransitionSet.class, DT, Transition.class));
    }

    public static Res<TransitionSet> get(String sid) {
        return DT.get(sid);
    }

    @XmlElement(name = "transition")
    public void setTransitions(Transition[] transitions) {

        typeSets.clear();

        Map<String, Set<Integer>> sets = new HashMap<String, Set<Integer>>();

        for (Transition t: transitions) {

            Set<Integer> set = sets.get(t.type);
            if (set == null) {
                set = new HashSet<Integer>();
                sets.put(t.type, set);
            }
            set.add(RevmanTransitions.resolveRevmanStatus(t.status, t.isCitation()));
        }

        typeSets = sets;
    }

    Set<Integer> getSet(String type) {
        return typeSets.get(type);
    }

    Collection<String> getTypes() {
        return typeSets.keySet();
    }

    static class Transition {

        @XmlAttribute(name = "type")
        private String type;

        @XmlAttribute(name = "status")
        private String status;

        @XmlAttribute(name = "citation")
        private String citation;

        boolean hasCitation() {
            return citation != null;
        }

        boolean isCitation() {
            return "true".equalsIgnoreCase(citation) || "yes".equalsIgnoreCase(citation);
        }

        String getStatus() {
            return status;
        }

        String getType() {
            return type;
        }
    }

    static class TransitionTo extends TransitionSet.Transition {

        @XmlAttribute(name = "transitionset-ref")
        private String transitionsetId;

        @XmlElement(name = "transition")
        private TransitionSet.Transition[] transitionsTo;

        String getTransitionSetRef() {
            return transitionsetId;
        }

        public Transition[] getTransitionsTo() {
            return transitionsTo;
        }
    }
}
