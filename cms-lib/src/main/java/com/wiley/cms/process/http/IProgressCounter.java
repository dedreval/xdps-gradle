package com.wiley.cms.process.http;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 11/28/2018
 */
public interface IProgressCounter {

    int MB_BYTES_1 = 1048576;
    int MB_BYTES_5 = MB_BYTES_1 * 5;
    int MB_BYTES_10 = MB_BYTES_1 * 10;
    int MB_BYTES_20 = MB_BYTES_1 * 20;
    int MB_BYTES_100 = MB_BYTES_1 * 100;
    int MB_BYTES_200 = MB_BYTES_1 * 200;

    void addProgress(int progress);

    default int getProgress(long contentLength) {
        return 0;
    }
}
