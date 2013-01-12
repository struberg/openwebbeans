/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.webbeans.component;

import javax.enterprise.inject.spi.AnnotatedType;
import javax.enterprise.inject.spi.Interceptor;
import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.Set;

import org.apache.webbeans.config.WebBeansContext;

/**
 * <p>Implicit self-interceptor Bean implementation.
 * This is Interceptors which got defined by having an &#064;AroundInvoke,
 * &#064;AroundTimeout, etc method inside a bean itself. In that case
 * all business method invocations on that bean are intercepted via those methods
 * in the bean.</p>
 */
public class SelfInterceptorBean<T> extends InterceptorBean<T> implements Interceptor<T>
{
    /**
     *
     * @param annotatedType AnnotatedType will be returned by some methods in the SPI
     * @param webBeansContext
     */
    public SelfInterceptorBean(WebBeansContext webBeansContext, AnnotatedType<T> annotatedType)
    {
        super(webBeansContext, annotatedType);
    }

    /**
     * @return always an empty Set as this interceptor doesn't have any InterceptorBindings
     */
    @Override
    public Set<Annotation> getInterceptorBindings()
    {
        return Collections.emptySet();
    }

}