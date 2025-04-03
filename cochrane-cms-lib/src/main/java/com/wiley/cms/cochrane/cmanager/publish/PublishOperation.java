package com.wiley.cms.cochrane.cmanager.publish;

import java.util.Date;

import com.wiley.cms.cochrane.cmanager.data.IResultsStorage;
import com.wiley.cms.cochrane.cmanager.data.PublishEntity;
import com.wiley.cms.cochrane.cmanager.entitywrapper.PublishWrapper;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 12/1/2014
 */
public enum PublishOperation {

    NONE("None"),
    DELETE ("Delete") {
        @Override
        public void start(PublishWrapper publish, PublisherFactory factory) {
            IPublish publisher = factory.getDeleter(publish);
            if (publisher != null) {
                publisher.start(publish, this);
            }
            publish.setDelete(false);
        }

        @Override
        public void beforeOperation(int publishId, IResultsStorage rs) {
            rs.setDeleting(publishId, true, false);
        }

        @Override
        public void afterOperation(PublishWrapper publish, int publishId, IResultsStorage rs) {
            publish.setPublishEntity(rs.updatePublish(publishId, false, true, new Date(), false));
        }

        @Override
        public void onFail(PublishWrapper publish, int publishId, IResultsStorage rs) {
            rs.setDeleting(publishId, false, true);
        }
    }, DELETING("Deleting"),

    GENERATE ("Generate") {
        @Override
        public void start(PublishWrapper publish, PublisherFactory factory) {
            IPublish publisher = factory.getGenerator(publish);
            if (publisher != null) {
                publisher.start(publish, this);
            }
            publish.setGenerate(false);
        }

        @Override
        public void beforeOperation(int publishId, IResultsStorage rs) {
        }

    }, GENERATING("Generating"),

    SEND ("Send"){
        @Override
        public void start(PublishWrapper publish, PublisherFactory factory) {
            IPublish publisher = factory.getSender(publish);
            if (publisher != null) {
                publisher.start(publish, this);
            }
            publish.setSend(false);
        }

        @Override
        public void beforeOperation(int publishId, IResultsStorage rs) {
            rs.setSending(publishId, true, false, false);
        }

        @Override
        public void afterOperation(PublishWrapper publish, int publishId, IResultsStorage rs) {
            publish.setPublishEntity(rs.updatePublish(publishId, false, true, new Date(), false));
        }

        @Override
        public String getNotReady(PublishEntity publish) {
            return !publish.isGenerated() || publish.isGenerating() || publish.getFileName() == null
                ? String.format("Sending has been stopped: package %s is generating or was not generated",
                    publish.getFileName()) : null;
        }

        @Override
        public void onFail(PublishWrapper publish, int publishId, IResultsStorage rs) {
            publish.setPublishEntity(rs.setSending(publishId, false, true, true));
        }
    }, SENDING("Sending"),

    UNPACK ("Unpack") {
        public void start(PublishWrapper publish, PublisherFactory factory) {
            IPublish publisher = factory.getUnpacker(publish);
            if (publisher != null) {
                publisher.start(publish, this);
            }
            publish.setUnpack(false);
        }
    }, UNPACKING("Unpacking");

    private final String name;

    PublishOperation(String name) {
        this.name = name;
    }

    public void start(PublishWrapper publish, PublisherFactory factory) {
    }

    public void beforeOperation(int publishId, IResultsStorage rs) {
    }

    public void afterOperation(PublishWrapper publish, int publishId, IResultsStorage rs) {
    }

    public void onFail(PublishWrapper publish, int publishId, IResultsStorage rs) {
    }

    public String getNotReady(PublishEntity pe) {
        return null;
    }

    public final String getName() {
        return name;
    }
}
