package com.wiley.cms.cochrane.medlinedownloader;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * @author <a href='mailto:ikirov@wiley.com'>Igor Kirov</a>
 * @version 27.10.2009
 */
public class MedlineDownloaderParameters implements Serializable {
    private List<Map<String, String>> params;
    private String destinationDirectory;

    public MedlineDownloaderParameters(List<Map<String, String>> params, String destinationDirectory) {
        this.params = params;
        this.destinationDirectory = destinationDirectory;
    }

    public List<Map<String, String>> getDownloaderParameterList() {
        return params;
    }

    public String getDestinationDirectory() {
        return destinationDirectory;
    }
}
