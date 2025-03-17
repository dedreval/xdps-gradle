package com.wiley.cms.cochrane.cmanager.publish.util;

/**
 * @author <a href='mailto:ikirov@wiley.com'>Igor Kirov</a>
 * @version 08.04.2010
 */
public enum FileNameMatcher {
    CLSYSREV("") {
        @Override
        public String getCommand() {
            return "\\( -name \"CD*\" -o -name \"MR*\" \\)";
        }
    },
    CLABOUT("") {
        @Override
        public String getCommand() {
            return "\\( -name \"*\" ! -name \".\" \\)";
        }
    },
    CLCENTRAL("CN*"),
    CLCMR("CMR*"),
    CLDARE("DARE*"),
    CLHTA("HTA*"),
    CLEED("NHSEED*");


    private String pattern;

    FileNameMatcher(String pattern) {
        this.pattern = pattern;
    }

    public String getCommand() {
        return "-name \"" + pattern + "\"";
    }
}
