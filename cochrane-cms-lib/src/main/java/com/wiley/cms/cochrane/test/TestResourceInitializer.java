package com.wiley.cms.cochrane.test;

import java.net.URL;

import com.wiley.cms.cochrane.cmanager.res.PackageType;
import com.wiley.cms.cochrane.cmanager.res.PathType;
import com.wiley.cms.cochrane.cmanager.res.TransAbs;
import com.wiley.tes.util.res.IResourceInitializer;
import com.wiley.tes.util.res.Property;
import com.wiley.tes.util.res.ResourceInitializer;
import com.wiley.tes.util.res.ResourceManager;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 7/8/2020
 */
public class TestResourceInitializer extends ResourceInitializer implements IResourceInitializer {
    private static final String ROOT_FOLDER = "/res";

    private static final TestResourceInitializer INSTANCE = new TestResourceInitializer();

    private TestResourceInitializer() {
        ResourceManager.instance().initialize(this);
    }

    @Override
    public URL[] getResourceRoot() {
        return new URL[] {getClass().getResource(ROOT_FOLDER)};
    }

    @Override
    public void initialize(ResourceManager loader)  {
        Property.register(loader);
        PackageType.register(loader);
        PathType.register(loader);
        TransAbs.register(loader);
    }

    public static void initialize() {
    }
}
