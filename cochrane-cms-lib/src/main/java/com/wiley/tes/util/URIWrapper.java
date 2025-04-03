package com.wiley.tes.util;

import com.wiley.tes.util.ftp.SftpConnection;

import java.net.URI;

/**
 * Type comments here.
 *
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @version 1.0
 */
public class URIWrapper {
    private URI uri;

    private String login;
    private String password;

    private String fileName;
    private String goToPath;

    public URIWrapper(URI uri) {
        setUri(uri);

        String ui = getUri().getUserInfo();

        setLogin(ui.substring(0, ui.indexOf(":")));
        setPassword(ui.substring(ui.indexOf(":") + 1, ui.length()));

        String path = getUri().getPath();
        String goToPathT = path.substring(0, path.lastIndexOf("/"));
        if (goToPathT.indexOf("/") == 0 && !SftpConnection.URI_SCHEME.equals(uri.getScheme())) {
            goToPathT = goToPathT.substring(1, goToPathT.length());
        }

        setGoToPath(goToPathT);

        //String[] dirs = path.split("/");

        setFileName(path.substring(path.lastIndexOf("/") + 1, path.length()));
    }

    public URI getUri() {
        return uri;
    }

    public void setUri(URI uri) {
        this.uri = uri;
    }

    public String getHost() {
        return uri.getHost();
    }

    public int getPort() {
        return uri.getPort();
    }


    public String getLogin() {
        return login;
    }

    public void setLogin(String login) {
        this.login = login;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getGoToPath() {
        return goToPath;
    }

    public void setGoToPath(String goToPath) {
        this.goToPath = goToPath;
    }
}
