package com.wiley.tes.util;

import org.apache.soap.encoding.soapenc.Base64;

/**
 * @author <a href='mailto:ikirov@wiley.com'>Igor Kirov</a>
 * @version 05.09.11
 */
public class ImageBase64 {
    String mFile;
    String mBase64Data;
    String mType;

    public ImageBase64(String file, String type, String base64Data) {
        this.mFile = file;
        this.mType = type;
        this.mBase64Data = base64Data != null ? base64Data.trim() : null;

    }

    public ImageBase64(String base64Data) {
        this(null, null, base64Data);
    }

    public String getFile() {
        return mFile;
    }

    public String getType() {
        return mType;
    }

    public String getBase64Data() {
        return mBase64Data;
    }

    public byte[] decode() {
        return Base64.decode(mBase64Data);
    }
}
