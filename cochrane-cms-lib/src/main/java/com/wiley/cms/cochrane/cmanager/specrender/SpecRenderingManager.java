package com.wiley.cms.cochrane.cmanager.specrender;

import java.util.ArrayList;
import java.util.List;

import com.wiley.cms.cochrane.cmanager.specrender.data.ISpecRenderingStorage;
import com.wiley.cms.cochrane.cmanager.specrender.data.SpecRenderingFileVO;
import com.wiley.cms.cochrane.cmanager.specrender.data.SpecRenderingStorageFactory;
import com.wiley.cms.cochrane.cmanager.specrender.data.SpecRenderingVO;
import com.wiley.cms.cochrane.cmanager.specrender.exception.SpecRendCreatorException;
import com.wiley.cms.cochrane.cmanager.specrender.process.Creator;
import com.wiley.cms.cochrane.repository.RepositoryFactory;
import com.wiley.tes.util.InputUtils;
import com.wiley.tes.util.Logger;

/**
 * Type comments here.
 *
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @version 1.0
 */
public class SpecRenderingManager {
    private static final Logger LOG = Logger.getLogger(SpecRenderingManager.class);

    private SpecRenderingManager() {
    }

    public static Object getSyncSafeObject() {
        return SpecRenderingManager.class;
    }

    public static void recreateAll(int dbId, boolean sync) {
        try {
            if (sync) {
                synchronized (getSyncSafeObject()) {
                    removeAll(dbId);
                    new Creator(dbId).createSpecRendering();
                }
            } else {
                removeAll(dbId);
                new Thread(new Creator(dbId)).start();
            }
        } catch (SpecRendCreatorException e) {
            LOG.error("Creation of spec rendering files failed.", e);
        }
    }

    private static void removeAll(int dbId) {
        LOG.debug("Removing of old spec rendering files started ...");
        List<SpecRenderingVO> list = getResultStorage().getSpecRenderingVOList(dbId);
        if (list != null && list.size() > 0) {
            for (SpecRenderingVO vo : list) {
                removeSpecRenderingVO(vo);
            }
        }
        LOG.debug("Removing of old spec rendering files finished successfully");
    }

    private static void removeSpecRenderingVO(SpecRenderingVO vo) {
        List<SpecRenderingFileVO> files = vo.getFiles();
        if (files != null && files.size() > 0) {
            for (SpecRenderingFileVO file : vo.getFiles()) {
                if (file.getFilePathPublish() != null) {
                    try {
                        RepositoryFactory.getRepository().deleteFile(file.getFilePathPublish());
                    } catch (Exception e) {
                        LOG.debug(e, e);
                    }
                }
                if (file.getFilePathLocal() != null) {
                    try {
                        RepositoryFactory.getRepository().deleteFile(file.getFilePathLocal());
                    } catch (Exception e) {
                        LOG.debug(e, e);
                    }
                }
            }

            try {
                String publishFilepath = vo.getFiles().iterator().next().getFilePathPublish();

                InputUtils.removeParentFolder(publishFilepath);
                InputUtils.removeParentFolder(vo.getFiles().iterator().next().getFilePathLocal());

                InputUtils.removeParentFolder(publishFilepath.substring(0, publishFilepath.lastIndexOf('/')));
            } catch (Exception e) {
                LOG.debug(e, e);
            }

        }
        getResultStorage().remove(vo.getId());
    }

    public static List<SpecRenderingVO> getSpecRenderingVOList(int dbId) {
        return getResultStorage().getSpecRenderingVOList(dbId);
    }

    public static List<SpecRenderingWrapper> getSpecRenderingWrapperList(int dbId) {
        return getSpecRenderingWrapperList(getResultStorage().getSpecRenderingVOList(dbId));
    }

    private static List<SpecRenderingWrapper> getSpecRenderingWrapperList(List<SpecRenderingVO> list) {
        List<SpecRenderingWrapper> wrapperList = new ArrayList<SpecRenderingWrapper>();
        for (SpecRenderingVO vo : list) {
            wrapperList.add(new SpecRenderingWrapper(vo));
        }
        return wrapperList;
    }

    private static ISpecRenderingStorage getResultStorage() {
        return SpecRenderingStorageFactory.getFactory().getInstance();
    }
}
