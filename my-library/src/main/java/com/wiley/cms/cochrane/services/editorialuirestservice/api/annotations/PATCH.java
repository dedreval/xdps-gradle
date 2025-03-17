package com.wiley.cms.cochrane.services.editorialuirestservice.api.annotations;

import javax.ws.rs.HttpMethod;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author <a href='mailto:atolkachev@wiley.com'>Andrey Tolkachev</a>
 * Date: 29.08.19
 */

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@HttpMethod("PATCH")
public @interface PATCH {
}