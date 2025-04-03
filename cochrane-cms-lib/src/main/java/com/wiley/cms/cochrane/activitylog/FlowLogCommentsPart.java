package com.wiley.cms.cochrane.activitylog;

import javax.validation.constraints.NotNull;

import org.apache.commons.lang.StringEscapeUtils;

import com.wiley.cms.cochrane.cmanager.contentworker.AriesHelper;
import com.wiley.cms.cochrane.utils.Constants;
import com.wiley.tes.util.Logger;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 3/12/2021
 */
public enum FlowLogCommentsPart {

    STAGE {
        @Override
        void build(String packageName, IFlowProduct product, StringBuilder sb) {
            sb.append("{");
            build(product.getStage(), sb);
        }

        @Override
        void parse(String value, IFlowProduct product) {
            product.setStage(value);
        }
    },

    SOURCE_STATUS {
        @Override
        void build(String packageName, IFlowProduct product, StringBuilder sb) {
            build(product.getSourceStatus(), sb);
        }

        @Override
        boolean parse(String value, StringBuilder sb, boolean prevLineWasAdded) {
            return super.parse(value, sb, false) || prevLineWasAdded;
        }

        @Override
        void parse(String value, IFlowProduct product) {
            product.setSourceStatus(value);
        }
    },

    STATUS {
        @Override
        void build(String packageName, IFlowProduct product, StringBuilder sb) {
            build(product.getPublicationType(), sb);
        }

        @Override
        boolean parse(String value, StringBuilder sb, boolean prevLineWasAdded) {
            return super.parse(value, sb, false) || prevLineWasAdded;
        }

        @Override
        void parse(String value, IFlowProduct product) {
            product.setPublicationType(value);
        }
    },

    COCHRANE_VERSION {
        @Override
        void build(String packageName, IFlowProduct product, StringBuilder sb) {
            build(product.getCochraneVersion(), sb);
        }

        @Override
        boolean parse(String value, StringBuilder sb, boolean prevLineWasAdded) {
            return parse(value, "[v.", "] ", sb, false) || prevLineWasAdded;
        }

        @Override
        void parse(String value, IFlowProduct product) {
            product.setCochraneVersion(value);
        }
    },
        
    SID {
        @Override
        void build(String packageName, IFlowProduct product, StringBuilder sb) {
            build(product.getSID(), sb);
        }

        @Override
        boolean parse(String value, StringBuilder sb, boolean prevLineWasAdded) {
            return parse(value, "[", "] ", sb, prevLineWasAdded) || prevLineWasAdded;
        }

        @Override
        void parse(String value, IFlowProduct product) {
            product.setSID(value);
        }
    },

    FIRST_ONLINE_DATE {
        @Override
        void build(String packageName, IFlowProduct product, StringBuilder sb) {
            build(product.getFirstOnlineDate(), sb);
        }

        @Override
        boolean parse(String value, StringBuilder sb, boolean prevLineWasAdded) {
            if (value != null && value.startsWith(SPD_PREFIX)) {
                parse(value.substring(0, SPD.length()), " ", " ", sb, prevLineWasAdded);
                parse(value.substring(SPD.length()), " ", " ", sb, false);
                return true;
                //boolean ret = super.parse(value.substring(SPD.length()), " ", " ", sb, prevLineWasAdded);
                //return super.parse(value.substring(0, SPD.length()), " ", " ", sb, !ret && prevLineWasAdded);
            }
            return parse(value, " ", " ", sb, prevLineWasAdded) || prevLineWasAdded;
        }

        @Override
        void parse(String value, IFlowProduct product) {
            if (value != null && value.startsWith(SPD_PREFIX)) {
                product.setFirstOnlineDate(value.substring(SPD.length()));
                product.sPD(!value.startsWith(SPD_CANCEL));

            } else {
                product.setFirstOnlineDate(value);
            }
        }
    },

    ONLINE_DATE {
        @Override
        void build(String packageName, IFlowProduct product, StringBuilder sb) {
            build(product.getOnlineDate(), sb);
        }

        @Override
        boolean parse(String value, StringBuilder sb, boolean prevLineWasAdded) {
            return parse(value, " ", " ", sb, prevLineWasAdded) || prevLineWasAdded;
        }

        @Override
        void parse(String value, IFlowProduct product) {
            product.setOnlineDate(value);
        }
    },

    PACKAGE_NAME {
        @Override
        void build(String packageName, IFlowProduct product, StringBuilder sb) {
            build(packageName, sb);
        }

        @Override
        void parse(String value, IFlowProduct product) {
            product.setPackageName(value);
        }

        //@Override
        //boolean parse(String value, StringBuilder sb, boolean prevLineWasAdded) {
        //    return super.parse(value, "<b>", "</b>", sb, false) || prevLineWasAdded;
        //}
    },

    SPD_DATE {
        @Override
        void build(String packageName, IFlowProduct product, StringBuilder sb) {
            if (product.sPD().is()) {
                append(product.sPD().off() ? SPD_CANCEL : SPD, sb);
            }
            build(product.getSPDDate(), sb);
        }

        @Override
        boolean parse(String value, StringBuilder sb, boolean prevLineWasAdded) {
            if (value != null && value.startsWith(SPD_PREFIX)) {
                parse(value.substring(0, SPD.length()), " ", " ", sb, prevLineWasAdded);
                parse(value.substring(SPD.length()), " ", " ", sb, false);
                return true;
            }
            return parse(value, " ", " ", sb, prevLineWasAdded) || prevLineWasAdded;
        }

        @Override
        void parse(String value, IFlowProduct product) {
            if (value != null && value.startsWith(SPD_PREFIX)) {
                product.setSPDDate(value.substring(SPD.length()));
                product.sPD(!value.startsWith(SPD_CANCEL));
            }
        }
    },

    HP {
        @Override
        void build(String packageName, IFlowProduct product, StringBuilder sb) {
            build(product.isHighProfile() ? "P" : null, sb);
        }

        @Override
        boolean parse(String value, StringBuilder sb, boolean prevLineWasAdded) {
            if ("P".equals(value)) {
                super.parse("HP", sb, prevLineWasAdded);
            }
            return false;
        }

        @Override
        void parse(String value, IFlowProduct product) {
            if ("P".equals(value)) {
                product.setHighPriority(AriesHelper.addHighProfile(true, product.getHighPriority()));
            }
        }
    },

    TITLE_ID {
        @Override
        void build(String packageName, IFlowProduct product, StringBuilder sb) {
            build(product.getTitleId() != null ? product.getTitleId().toString() : null, sb);
        }

        @Override
        boolean parse(String value, StringBuilder sb, boolean prevLineWasAdded) {
            return false;
        }

        @Override
        void parse(String value, IFlowProduct product) {
            if (value != null && !value.isEmpty()) {
                try {
                    product.setTitleId(Integer.valueOf(value));
                } catch (NumberFormatException nfe) {
                    LOG.warn(nfe.getMessage());
                }
            }
        }
    },

    HF {
        @Override
        void build(String packageName, IFlowProduct product, StringBuilder sb) {
            build(product.isHighFrequency() ? "H" : null, sb);
        }

        @Override
        boolean parse(String value, StringBuilder sb, boolean prevLineWasAdded) {
            if ("H".equals(value)) {
                super.parse("HF", sb, prevLineWasAdded);
            }
            return false;
        }

        @Override
        void parse(String value, IFlowProduct product) {
            if ("H".equals(value)) {
                product.setHighPriority(AriesHelper.addHighFrequency(true, product.getHighPriority()));
            }
        }
    },

    RECORDS_COUNT {
        @Override
        void build(String packageName, IFlowProduct product, StringBuilder sb) {
            if (product.getEventRecords() == null && product.getTotalRecords() == null) {
                build(null, sb);
            } else {
                String value = getCounter(product.getEventRecords(), "") + getCounter(product.getTotalRecords(), "_");
                build(value, sb);
            }
            sb.append("}");
        }

        @Override
        boolean parse(String value, StringBuilder sb, boolean prevLineWasAdded) {
            return parse(value != null ? value.replace("_", " ") : value, "[", "] ", sb,
                    prevLineWasAdded) || prevLineWasAdded;
        }

        @Override
        void parse(String value, IFlowProduct product) {
            if (value != null && !value.isEmpty()) {
                try {
                    String[] parts = value.split("_");
                    product.setEventRecords(getCounter(parts[0]), parts.length > 1 ? getCounter(parts[1]) : null);

                } catch (NumberFormatException nfe) {
                    LOG.warn(nfe.getMessage());
                }
            }
        }

        private Integer getCounter(@NotNull String value) {
            return value.isEmpty() ? null : Integer.valueOf(value);
        }

        private String getCounter(@NotNull Integer value, @NotNull String prefix) {
            return value == null ? "" : prefix + value;
        }
    };

    private static final Logger LOG = Logger.getLogger(FlowLogCommentsPart.class);

    private static final String SPD_PREFIX = "[SPD";
    private static final String SPD = SPD_PREFIX + "+]";
    private static final String SPD_CANCEL = SPD_PREFIX + "-]";

    abstract void build(String packageName, IFlowProduct product, StringBuilder sb);

    boolean parse(String value, StringBuilder sb, boolean newLine) {
        return parse(value, null, " ", sb, newLine);
    }

    void parse(String value, IFlowProduct product) {
    }

    static boolean parse(String value, String prefix, @NotNull String postfix, StringBuilder sb, boolean newLine) {
        if (value != null && !value.isEmpty() && !Constants.NA.equals(value)) {
            if (newLine) {
                appendNextLine(sb);
            }
            if (prefix != null) {
                sb.append(prefix);
            }
            sb.append(value).append(postfix);
            return true;
        }
        return false;
    }

    static void build(String value, StringBuilder sb) {
        append(value, sb).append(";");
    }

    public static String buildComments(String packageName, String err, IFlowProduct product) {
        StringBuilder sb = new StringBuilder();
        for (FlowLogCommentsPart partType: values()) {
            partType.build(packageName, product, sb);
        }
        return err != null ? sb.append(err).toString() : sb.toString();
    }

    public static String parseComments(String comments, IFlowProduct product) {
        if (comments != null && comments.startsWith("{")) {
            int ind = comments.indexOf('}');
            if (ind > 0) {
                String head = comments.substring(1, ind);
                parseHead(head, product);
                return head.length() + 2 < comments.length() ? comments.substring(ind + 1) : null;
            }
        }
        return comments;
    }

    public static String parseComments(String comments) {
        if (comments != null && comments.startsWith("{")) {
            int ind = comments.indexOf('}');
            if (ind > 0) {
                StringBuilder sb = new StringBuilder();
                String head = comments.substring(1, ind);
                parseHead(head, sb);
                parseMessage(head.length() + 2 < comments.length() ? comments.substring(ind + 1) : null, sb);
                return sb.toString();
            }
        }
        return comments;
    }

    private static void parseHead(String head, StringBuilder sb) {
        String[] headParts = head.split(";");
        int size = headParts.length;
        boolean lineAdded = false;
        for (FlowLogCommentsPart partType: values()) {
            if (partType.ordinal() < size) {
                lineAdded = partType.parse(headParts[partType.ordinal()].trim(), sb, lineAdded);
            }
        }
    }

    private static void parseHead(String head, IFlowProduct product) {
        String[] headParts = head.split(";");
        int size = headParts.length;
        for (FlowLogCommentsPart partType: values()) {
            if (partType.ordinal() < size) {
                partType.parse(headParts[partType.ordinal()].trim(), product);
            }
        }
    }

    private static void parseMessage(String message, StringBuilder sb) {
        if (message != null) {
            appendNextLine(sb);
            appendNextLine(sb).append(StringEscapeUtils.escapeHtml(StringEscapeUtils.escapeXml(message)));
        }
    }

    private static StringBuilder appendNextLine(StringBuilder sb) {
        return sb.append("<br>");
    }

    private static StringBuilder append(String value, StringBuilder sb) {
        if (value != null) {
            sb.append(value);
        }
        return sb;
    }
}
