package com.wiley.cms.cochrane.cmanager.publish.generate;

import java.util.List;
import java.util.function.Function;

/**
 * @author <a href='mailto:aasadulin@wiley.com'>Alexandr Asadulin</a>
 * @version 05.10.2018
 * 
 * @param <T> the type of the input used in the content collector
 */
public interface ArticleContentCollector<T> extends Function<T, List<String>> {
}
