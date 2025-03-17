package com.wiley.cms.cochrane.cmanager.publish.generate.literatum;

import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.cmanager.res.PubType;
import com.wiley.cms.cochrane.utils.Constants;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 2/4/2019
 */
public enum LiteratumPriorityStrategy {

    LOW() {
        @Override
        public String getManifest(BaseType bt, boolean wasDelivered) {
            return bt.getResourceContent(PubType.TYPE_LITERATUM, Constants.CONTROL_FILE_LIT_LOW);
        }

    }, NORMAl() {
        @Override
        public String getManifest(BaseType bt, boolean wasDelivered) {
            return bt.getResourceContent(PubType.TYPE_LITERATUM, Constants.CONTROL_FILE_LIT_NORMAL);
        }

    }, HIGH() {
        @Override
        public String getManifest(BaseType bt, boolean wasDelivered) {
            return bt.getResourceContent(PubType.TYPE_LITERATUM, Constants.CONTROL_FILE_LIT_HIGH);
        }

    }, HIGH4NEW_LOW4UPDATE {
        @Override
        public String getManifest(BaseType bt, boolean wasDelivered) {
            return bt.getResourceContent(PubType.TYPE_LITERATUM,
                 wasDelivered ? Constants.CONTROL_FILE_LIT_LOW : Constants.CONTROL_FILE_LIT_HIGH);
        }
    };

    abstract String getManifest(BaseType bt, boolean wasDelivered);
}
