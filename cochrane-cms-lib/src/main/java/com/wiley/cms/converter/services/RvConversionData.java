package com.wiley.cms.converter.services;

import com.wiley.tes.util.Pair;
import org.jdom.Element;

import java.io.File;

/**
 * @author <a href='mailto:aasadulin@wiley.com'>Alexander Asadulin</a>
 * @version 28.08.2015
 */
public class RvConversionData {

    final Pair<Element, String[]> metaParams;
    final File file;

    RvConversionData(File file, Pair<Element, String[]> metaParams) {
        this.metaParams = metaParams;
        this.file = file;
    }
}
