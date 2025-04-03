package com.wiley.cms.cochrane.authentication;

import java.io.Serializable;

/**
 * Type comments here.
 *
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @version 1.0
 */
public interface IVisit extends Serializable {
    String getLogin();

    void setLogin(String login);

    String getPassword();

    void setPassword(String password);

    boolean isAuthed();

    void setAuthed(boolean authed);

    boolean isAdmin();

    void setAdmin(boolean admin);
}
