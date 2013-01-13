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
package org.apache.webbeans.util;

import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.decorator.Decorator;
import javax.enterprise.context.Dependent;
import javax.enterprise.context.NormalScope;
import javax.enterprise.context.spi.Contextual;
import javax.enterprise.event.Event;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.Alternative;
import javax.enterprise.inject.Disposes;
import javax.enterprise.inject.IllegalProductException;
import javax.enterprise.inject.Instance;
import javax.enterprise.inject.Produces;
import javax.enterprise.inject.Specializes;
import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.AfterDeploymentValidation;
import javax.enterprise.inject.spi.AnnotatedField;
import javax.enterprise.inject.spi.AnnotatedMethod;
import javax.enterprise.inject.spi.AnnotatedParameter;
import javax.enterprise.inject.spi.AnnotatedType;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.BeforeBeanDiscovery;
import javax.enterprise.inject.spi.BeforeShutdown;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.enterprise.inject.spi.InjectionTarget;
import javax.enterprise.inject.spi.InterceptionType;
import javax.enterprise.inject.spi.Interceptor;
import javax.enterprise.inject.spi.ObserverMethod;
import javax.enterprise.inject.spi.PassivationCapable;
import javax.enterprise.inject.spi.ProcessAnnotatedType;
import javax.enterprise.inject.spi.ProcessBean;
import javax.enterprise.inject.spi.ProcessInjectionTarget;
import javax.enterprise.inject.spi.ProcessManagedBean;
import javax.enterprise.inject.spi.ProcessObserverMethod;
import javax.enterprise.inject.spi.ProcessProducer;
import javax.enterprise.inject.spi.ProcessProducerField;
import javax.enterprise.inject.spi.ProcessProducerMethod;
import javax.enterprise.inject.spi.ProcessSessionBean;
import javax.enterprise.util.TypeLiteral;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Scope;
import javax.interceptor.AroundInvoke;
import javax.interceptor.AroundTimeout;
import javax.interceptor.InvocationContext;

import org.apache.webbeans.annotation.AnnotationManager;
import org.apache.webbeans.component.AbstractInjectionTargetBean;
import org.apache.webbeans.component.AbstractOwbBean;
import org.apache.webbeans.component.AbstractProducerBean;
import org.apache.webbeans.component.BeanManagerBean;
import org.apache.webbeans.component.ConversationBean;
import org.apache.webbeans.component.EnterpriseBeanMarker;
import org.apache.webbeans.component.EventBean;
import org.apache.webbeans.component.ExtensionBean;
import org.apache.webbeans.component.InjectionPointBean;
import org.apache.webbeans.component.InjectionTargetBean;
import org.apache.webbeans.component.InstanceBean;
import org.apache.webbeans.component.ManagedBean;
import org.apache.webbeans.component.NewBean;
import org.apache.webbeans.component.NewManagedBean;
import org.apache.webbeans.component.OwbBean;
import org.apache.webbeans.component.ProducerFieldBean;
import org.apache.webbeans.component.ProducerMethodBean;
import org.apache.webbeans.component.ResourceBean;
import org.apache.webbeans.component.creation.AnnotatedTypeBeanBuilder;
import org.apache.webbeans.component.creation.ExtensionBeanBuilder;
import org.apache.webbeans.component.creation.ManagedBeanBuilder;
import org.apache.webbeans.component.creation.NewEjbBeanBuilder;
import org.apache.webbeans.component.creation.NewManagedBeanBuilder;
import org.apache.webbeans.config.EJBWebBeansConfigurator;
import org.apache.webbeans.config.OWBLogConst;
import org.apache.webbeans.config.OpenWebBeansConfiguration;
import org.apache.webbeans.config.WebBeansContext;
import org.apache.webbeans.container.BeanManagerImpl;
import org.apache.webbeans.container.ExternalScope;
import org.apache.webbeans.container.InjectionResolver;
import org.apache.webbeans.decorator.WebBeansDecoratorConfig;
import org.apache.webbeans.exception.WebBeansConfigurationException;
import org.apache.webbeans.exception.inject.DefinitionException;
import org.apache.webbeans.exception.inject.InconsistentSpecializationException;
import org.apache.webbeans.inject.AlternativesManager;
import org.apache.webbeans.intercept.InterceptorData;
import org.apache.webbeans.intercept.InterceptorDataImpl;
import org.apache.webbeans.logger.WebBeansLoggerFacade;
import org.apache.webbeans.plugins.OpenWebBeansEjbLCAPlugin;
import org.apache.webbeans.plugins.PluginLoader;
import org.apache.webbeans.portable.creation.InjectionTargetProducer;
import org.apache.webbeans.portable.creation.ProducerBeansProducer;
import org.apache.webbeans.portable.events.discovery.ErrorStack;
import org.apache.webbeans.portable.events.generics.GProcessAnnotatedType;
import org.apache.webbeans.portable.events.generics.GProcessBean;
import org.apache.webbeans.portable.events.generics.GProcessInjectionTarget;
import org.apache.webbeans.portable.events.generics.GProcessManagedBean;
import org.apache.webbeans.portable.events.generics.GProcessObservableMethod;
import org.apache.webbeans.portable.events.generics.GProcessProducer;
import org.apache.webbeans.portable.events.generics.GProcessProducerField;
import org.apache.webbeans.portable.events.generics.GProcessProducerMethod;
import org.apache.webbeans.portable.events.generics.GProcessSessionBean;
import org.apache.webbeans.spi.plugins.OpenWebBeansEjbPlugin;
import org.apache.webbeans.spi.plugins.OpenWebBeansPlugin;

/**
 * Contains some utility methods used in the all project.
 *
 * @version $Rev$ $Date$
 */
@SuppressWarnings("unchecked")
public final class WebBeansUtil
{
    /**
     * Enforcing that interceptor callbacks should not be
     * able to throw checked exceptions is configurable
     */
    private static volatile Boolean enforceCheckedException;

    private final static Class<Instance<?>> INSTANCE_TYPE
            = new TypeLiteral<Instance<?>>()
    {
        private static final long serialVersionUID = 3555319035805031154L;
    }.getRawType();

    private final static Class<Provider<?>> PROVIDER_TYPE
            = new TypeLiteral<Provider<?>>()
    {
        private static final long serialVersionUID = -2611190564495920054L;
    }.getRawType();

    private final static Class<Event<?>>    EVENT_TYPE
            = new TypeLiteral<Event<?>>()
    {
        private static final long serialVersionUID = -1395145871249763477L;
    }.getRawType();

    private final WebBeansContext webBeansContext;

    public WebBeansUtil(WebBeansContext webBeansContext)
    {
        this.webBeansContext = webBeansContext;
    }

    /**
     * Lifycycle methods like {@link javax.annotation.PostConstruct} and
     * {@link javax.annotation.PreDestroy} must not define a checked Exception
     * regarding to the spec. But this is often unnecessary restrictive so we
     * allow to disable this check application wide.
     *
     * @return <code>true</code> if the spec rule of having no checked exception should be enforced
     */
    private boolean isNoCheckedExceptionEnforced()
    {
        if (enforceCheckedException == null)
        {
            enforceCheckedException = Boolean.parseBoolean(webBeansContext.getOpenWebBeansConfiguration().
                    getProperty(OpenWebBeansConfiguration.INTERCEPTOR_FORCE_NO_CHECKED_EXCEPTIONS, "true"));
        }

        return enforceCheckedException.booleanValue();
    }

    /**
     * Gets current classloader with current thread.
     *
     * @return Current class loader instance
     */
    public static ClassLoader getCurrentClassLoader()
    {
        ClassLoader loader =  Thread.currentThread().getContextClassLoader();

        if (loader == null)
        {
            loader = WebBeansUtil.class.getClassLoader();
        }

        return loader;
    }

    /**
     * Checks the generic type requirements.
     */
    public static void checkGenericType(Class<?> clazz, Class<? extends Annotation> scope)
    {
        Asserts.assertNotNull(clazz);

        if (ClassUtil.isDefinitionContainsTypeVariables(clazz))
        {
            if(!scope.equals(Dependent.class))
            {
                throw new WebBeansConfigurationException("Generic type may only defined with scope @Dependent " +
                        "for ManagedBean class : " + clazz.getName());
            }
        }
    }


    /**
     * Check producer method/field bean return type.
     * @param bean producer bean instance
     * @param member related member instance
     */
    public static void checkProducerGenericType(Bean<?> bean,Member member)
    {
        Asserts.assertNotNull(bean,"Bean is null");

        Type type = null;

        if(bean instanceof ProducerMethodBean)
        {
            type = ((ProducerMethodBean<?>)bean).getCreatorMethod().getGenericReturnType();
        }
        else if(bean instanceof ProducerFieldBean)
        {
            type = ((ProducerFieldBean<?>)bean).getCreatorField().getGenericType();
        }
        else
        {
            throw new IllegalArgumentException("Bean must be Producer Field or Method Bean instance : " + bean);
        }

        String messageTemplate = "Producer Field/Method Bean with name : %s" + 
                         " in bean class : %s"; 

        String memberName = member.getName();
        String declaringClassName = member.getDeclaringClass().getName();
        if(checkGenericForProducers(type, messageTemplate, memberName, declaringClassName))
        {
            if(!bean.getScope().equals(Dependent.class))
            {
                String message = format(messageTemplate, memberName, declaringClassName);
                throw new WebBeansConfigurationException(message + " scope must bee @Dependent");
            }
        }
    }

    /**
     * Check generic types for producer method and fields.
     * @param type generic return type
     * @param messageTemplate error message
     * @return true if parametrized type argument is TypeVariable
     */
    //Helper method
    private static boolean checkGenericForProducers(Type type, String messageTemplate, Object... errorMessageArgs)
    {
        boolean result = false;

        if(type instanceof TypeVariable)
        {
            String message = format(messageTemplate, errorMessageArgs);
            throw new WebBeansConfigurationException(message + " return type can not be type variable");
        }

        if(ClassUtil.isParametrizedType(type))
        {
            Type[] actualTypes = ClassUtil.getActualTypeArguments(type);

            if(actualTypes.length == 0)
            {
                String message = format(messageTemplate, errorMessageArgs);
                throw new WebBeansConfigurationException(message +
                        " return type must define actual type arguments or type variable");
            }

            for(Type actualType : actualTypes)
            {
                if(ClassUtil.isWildCardType(actualType))
                {
                    String message = format(messageTemplate, errorMessageArgs);
                    throw new WebBeansConfigurationException(message +
                            " return type can not define wildcard actual type argument");
                }

                if(ClassUtil.isTypeVariable(actualType))
                {
                    result = true;
                }
            }
        }

        return result;
    }

    /**
     * Returns true if this class can be candidate for simple web bean, false otherwise.
     *
     * @param clazz implementation class
     * @return true if this class can be candidate for simple web bean
     * @throws WebBeansConfigurationException if any configuration exception occurs
     */
    public boolean isManagedBean(Class<?> clazz) throws WebBeansConfigurationException
    {
        try
        {
            isManagedBeanClass(clazz);

        }
        catch (WebBeansConfigurationException e)
        {
            return false;
        }

        return true;
    }

    /**
     * Return <code>true</code> if the given class is ok for manage bean conditions,
     * <code>false</code> otherwise.
     *
     * @param clazz class in hand
     * @return <code>true</code> if the given class is ok for simple web bean conditions.
     */
    public void isManagedBeanClass(Class<?> clazz)
    {
        Asserts.nullCheckForClass(clazz, "Class is null");

        int modifier = clazz.getModifiers();

        if (!Modifier.isStatic(modifier) && ClassUtil.isInnerClazz(clazz))
        {
            throw new WebBeansConfigurationException("Bean implementation class : "
                                                     + clazz.getName() + " can not be non-static inner class");
        }

        if (!ClassUtil.isConcrete(clazz) && !AnnotationUtil.hasClassAnnotation(clazz, Decorator.class))
        {
            throw new WebBeansConfigurationException("Bean implementation class : " + clazz.getName()
                                                     + " have to be concrete if not defines as @Decorator");
        }

        if (!isConstructureOk(clazz))
        {
            throw new WebBeansConfigurationException("Bean implementation class : " + clazz.getName()
                                                     + " must define at least one Constructor");
        }

        if(Extension.class.isAssignableFrom(clazz))
        {
            throw new WebBeansConfigurationException("Bean implementation class can not implement "
                                                     + "javax.enterprise.inject.spi.Extension.!");
        }

        Class<?>[] interfaces = clazz.getInterfaces();
        if(interfaces != null && interfaces.length > 0)
        {
            for(Class<?> intr : interfaces)
            {
                if(intr.getName().equals("javax.ejb.EnterpriseBean"))
                {
                    throw new WebBeansConfigurationException("Bean implementation class can not implement "
                                                             + "javax.ejb.EnterpriseBean");
                }
            }
        }

        // and finally call all checks which are defined in plugins like JSF, JPA, etc
        List<OpenWebBeansPlugin> plugins = webBeansContext.getPluginLoader().getPlugins();
        for (OpenWebBeansPlugin plugin : plugins)
        {
            try
            {
                plugin.isManagedBean(clazz);
            }
            catch (Exception e)
            {
                PluginLoader.throwsException(e);
            }
        }
    }

    public void checkManagedBeanCondition(Class<?> clazz) throws WebBeansConfigurationException
    {
        int modifier = clazz.getModifiers();

        if (AnnotationUtil.hasClassAnnotation(clazz, Decorator.class) && AnnotationUtil.hasClassAnnotation(clazz, javax.interceptor.Interceptor.class))
        {
            throw new WebBeansConfigurationException("ManagedBean implementation class : " + clazz.getName()
                                                     + " may not annotated with both @Interceptor and @Decorator annotation");
        }

        if (!AnnotationUtil.hasClassAnnotation(clazz, Decorator.class) && !AnnotationUtil.hasClassAnnotation(clazz, javax.interceptor.Interceptor.class))
        {
            webBeansContext.getInterceptorUtil().checkSimpleWebBeansInterceptorConditions(clazz);
        }

        if (Modifier.isInterface(modifier))
        {
            throw new WebBeansConfigurationException("ManagedBean implementation class : " + clazz.getName() + " may not _defined as interface");
        }
    }

    /**
     * Returns true if given class supports injections,
     * false otherwise.
     * <p>
     * Each plugin is asked with given class that supports
     * injections or not.
     * </p>
     * @param clazz scanned class
     * @return  true if given class supports injections
     */
    public boolean supportsJavaEeComponentInjections(Class<?> clazz)
    {
        if (clazz.isInterface() || clazz.isAnnotation() || clazz.isEnum())
        {
            // interfaces, annotations and enums are no subject of injection
            return false;
        }

        // and finally call all checks which are defined in plugins like JSF, JPA, etc
        List<OpenWebBeansPlugin> plugins = webBeansContext.getPluginLoader().getPlugins();
        for (OpenWebBeansPlugin plugin : plugins)
        {
            //Ejb plugin handles its own events
            //Also EJb beans supports injections
            if(!(plugin instanceof OpenWebBeansEjbPlugin))
            {
                if(plugin.supportsJavaEeComponentInjections(clazz))
                {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Defines applicable constructor.
     * @param <T> type info
     * @param clazz class type
     * @return constructor
     * @throws WebBeansConfigurationException any configuration exception
     */
    public <T> Constructor<T> defineConstructor(Class<T> clazz) throws WebBeansConfigurationException
    {
        Asserts.nullCheckForClass(clazz);
        Constructor<?>[] constructors = webBeansContext.getSecurityService().doPrivilegedGetDeclaredConstructors(clazz);

        return defineConstructor(constructors, clazz);

    }


    public <T> Constructor<T> defineConstructor(Constructor<?>[] constructors, Class<T> clazz)
    {
        Constructor<T> result = null;

        boolean inAnnotation = false;

        /* Check for @Initializer */
        for (Constructor<?> constructor : constructors)
        {
            if (constructor.getAnnotation(Inject.class) != null)
            {
                if (inAnnotation)// duplicate @In
                {
                    throw new WebBeansConfigurationException("There are more than one Constructor with "
                                                             + "Initializer annotation in class " + clazz.getName());
                }
                inAnnotation = true;
                result = (Constructor<T>) constructor;
            }
        }

        if (result == null)
        {
            result = getNoArgConstructor(clazz);

            if(result == null)
            {
                throw new WebBeansConfigurationException("No constructor is found for the class : " + clazz.getName());
            }
        }


        Annotation[][] parameterAnns = result.getParameterAnnotations();
        for (Annotation[] parameters : parameterAnns)
        {
            for (Annotation param : parameters)
            {
                if (param.annotationType().equals(Disposes.class))
                {
                    throw new WebBeansConfigurationException("Constructor parameter annotations can not contain " +
                            "@Disposes annotation in class : " + clazz.getName());
                }

                if(param.annotationType().equals(Observes.class))
                {
                    throw new WebBeansConfigurationException("Constructor parameter annotations can not contain " +
                            "@Observes annotation in class : " + clazz.getName());
                }
            }

        }

        return result;

    }

    /**
     * Check that simple web beans class has compatible constructor.
     * @param clazz web beans simple class
     * @throws WebBeansConfigurationException if the web beans has incompatible
     *             constructor
     */
    public boolean isConstructureOk(Class<?> clazz) throws WebBeansConfigurationException
    {
        Asserts.nullCheckForClass(clazz);

        if (getNoArgConstructor(clazz) != null)
        {
            return true;
        }

        Constructor<?>[] constructors = webBeansContext.getSecurityService().doPrivilegedGetDeclaredConstructors(clazz);

        for (Constructor<?> constructor : constructors)
        {
            if (constructor.getAnnotation(Inject.class) != null)
            {
                return true;
            }
        }

        return false;
    }

    /**
     * New WebBeans component class.
     *
     * @param <T>
     * @param clazz impl. class
     * @return the new component
     */
    public <T> NewManagedBean<T> createNewComponent(Class<T> clazz, Type apiType)
    {
        Asserts.nullCheckForClass(clazz);

        NewManagedBean<T> comp;

        if (webBeansContext.getWebBeansUtil().isManagedBean(clazz))
        {
            NewManagedBeanBuilder<T> newBeanCreator
                = new NewManagedBeanBuilder<T>(webBeansContext, webBeansContext.getAnnotatedElementFactory().newAnnotatedType(clazz));
            newBeanCreator.defineApiType();
            newBeanCreator.defineScopeType("");
            newBeanCreator.defineConstructor();
            newBeanCreator.defineInjectedFields();
            newBeanCreator.defineInjectedMethods();
            newBeanCreator.defineDisposalMethods();
            comp = newBeanCreator.getBean();
        }
        else if (EJBWebBeansConfigurator.isSessionBean(clazz, webBeansContext))
        {
            NewEjbBeanBuilder<T> newBeanCreator
                = new NewEjbBeanBuilder<T>(webBeansContext, webBeansContext.getAnnotatedElementFactory().newAnnotatedType(clazz));
            newBeanCreator.defineApiType();
            newBeanCreator.defineInjectedFields();
            newBeanCreator.defineInjectedMethods();
            comp = newBeanCreator.getBean();
        }
        else
        {
            throw new WebBeansConfigurationException("@New annotation on type : " + clazz.getName()
                                                     + " must defined as a simple or an enterprise web bean");
        }
        return comp;
    }

    /**
     * Creates a new extension bean.
     *
     * @param <T> extension service class
     * @param clazz impl. class
     * @return a new extension service bean
     */
    public <T> ExtensionBean<T> createExtensionComponent(Class<T> clazz)
    {
        Asserts.nullCheckForClass(clazz);
        ExtensionBeanBuilder<T> extensionBeanCreator = new ExtensionBeanBuilder<T>(webBeansContext, clazz);
        ExtensionBean<T> bean = extensionBeanCreator.getBean();
        extensionBeanCreator.defineObserverMethods(bean);
        return bean;
    }


    /**
     * Creates a new manager bean instance.
     * @return new manager bean instance
     */
    public BeanManagerBean getManagerBean()
    {
        return new BeanManagerBean(webBeansContext);
    }

    /**
     * Creates a new instance bean.
     * @return new instance bean
     */
    @SuppressWarnings("serial")
    public <T> InstanceBean<T> getInstanceBean()
    {
        return new InstanceBean<T>(webBeansContext);
    }

    /**
     * Creates a new event bean.
     * @return new event bean
     */
    @SuppressWarnings("serial")
    public <T> EventBean<T> getEventBean()
    {
        return new EventBean<T>(webBeansContext);
    }


    /**
     * Returns new conversation bean instance.
     * The name is explicitly specified in 6.7.2 and is not the normal default name.
     * @return new conversation bean
     */
    public ConversationBean getConversationBean()
    {
        ConversationBean conversationComp = new ConversationBean(webBeansContext);

        WebBeansDecoratorConfig.configureDecorators(conversationComp);

        return conversationComp;
    }

    /**
     * Returns a new injected point bean instance.
     * @return new injected point bean
     */
    public InjectionPointBean getInjectionPointBean()
    {
        return new InjectionPointBean(webBeansContext);
    }

    /**
     * Check the {@link PostConstruct} or {@link PreDestroy} annotated method
     * criterias, and return post construct or pre destroyDependents method.
     * <p>
     * Web Beans container is responsible for setting the post construct or pre
     * destroyDependents annotation if the web beans component is not an EJB components,
     * in this case EJB container is responsible for this.
     * </p>
     *
     * @param clazz checked class
     * @param commonAnnotation post construct or predestroy annotation
     * @param invocationContext whether the takes an invocationContext, as in
     *            interceptors defiend outside of the bean class.
     * @return post construct or predestroy method
     */
    public Set<Method> checkCommonAnnotationCriterias(Class<?> clazz, Class<? extends Annotation> commonAnnotation, boolean invocationContext)
    {
        Asserts.nullCheckForClass(clazz);

        Method[] methods = webBeansContext.getSecurityService().doPrivilegedGetDeclaredMethods(clazz);
        Method result = null;
        boolean found = false;
        for (Method method : methods)
        {
            if (AnnotationUtil.hasMethodAnnotation(method, commonAnnotation))
            {
                if (ClassUtil.isMoreThanOneMethodWithName(method.getName(), clazz))
                {
                    continue;
                }

                if (found)
                {
                    throw new WebBeansConfigurationException("@" + commonAnnotation.getSimpleName()
                            + " annotation is declared more than one method in the class : " + clazz.getName());
                }

                found = true;
                result = method;

                // Check method criterias
                Class<?>[] params = method.getParameterTypes();
                if (params.length > 0)
                {
                    // Check method criterias
                    if (params.length != 1 || !params[0].equals(InvocationContext.class))
                    {
                        throw new WebBeansConfigurationException("@" + commonAnnotation.getSimpleName()
                                + " annotated method : " + method.getName() + " in class : " + clazz.getName()
                                + " can not take any formal arguments other than InvocationContext");
                    }
                }
                else if(invocationContext)
                {
                    // Maybe it just intercepts itself, but we were looking at it like an @Interceptor
                    return null;
                }

                if (!method.getReturnType().equals(Void.TYPE))
                {
                    throw new WebBeansConfigurationException("@" + commonAnnotation.getSimpleName()
                            + " annotated method : " + method.getName() + " in class : " + clazz.getName()
                            + " must return void type");
                }

                if (isNoCheckedExceptionEnforced() && ClassUtil.isMethodHasCheckedException(method))
                {
                    throw new WebBeansConfigurationException("@" + commonAnnotation.getSimpleName()
                            + " annotated method : " + method.getName() + " in class : " + clazz.getName()
                            + " can not throw any checked exception");
                }

                if (Modifier.isStatic(method.getModifiers()))
                {
                    throw new WebBeansConfigurationException("@" + commonAnnotation.getSimpleName()
                            + " annotated method : " + method.getName() + " in class : "
                            + clazz.getName() + " can not be static");
                }
            }
        }

        if (result != null)
        {
            Set<Method> resultSet = new HashSet<Method>();
            resultSet.add(result);
            return resultSet;
        }
        else
        {
            return null;
        }
    }

    public <T> Set<Method> checkCommonAnnotationCriterias(AnnotatedType<T> annotatedType, Class<? extends Annotation> commonAnnotation, boolean invocationContext)
    {
        Class<?> clazz = annotatedType.getJavaClass();

        Set<Method> result = new HashSet<Method>();
        Set<Class<?>> foundInClass = new HashSet<Class<?>>();
        Set<AnnotatedMethod<? super T>> methods = annotatedType.getMethods();
        for(AnnotatedMethod<? super T> methodA : methods)
        {
            AnnotatedMethod<T> methodB = (AnnotatedMethod<T>)methodA;
            Method method = methodB.getJavaMember();
            if (method.isAnnotationPresent(commonAnnotation))
            {
                if (ClassUtil.isMoreThanOneMethodWithName(method.getName(), clazz))
                {
                    continue;
                }

                if (foundInClass.contains(method.getDeclaringClass()))
                {
                    throw new WebBeansConfigurationException("@" + commonAnnotation.getSimpleName()
                            + " annotation is declared more than one method in the class : " + clazz.getName());
                }
                result.add(method);
                foundInClass.add(method.getDeclaringClass());

                // Check method criterias
                if (!methodB.getParameters().isEmpty())
                {
                    if (methodB.getParameters().size() != 1 || !ClassUtil.getClass(methodB.getParameters().get(0).getBaseType()).equals(InvocationContext.class))
                    {
                        throw new WebBeansConfigurationException("@" + commonAnnotation.getSimpleName()
                                + " annotated method : " + method.getName() + " in class : " + clazz.getName()
                                + " can not take any formal arguments other than InvocationContext");
                    }
                }
                else if(invocationContext)
                {
                    // Maybe it just intercepts itself, but we were looking at it like an @Interceptor
                    return null;
                }

                if (!method.getReturnType().equals(Void.TYPE))
                {
                    throw new WebBeansConfigurationException("@" + commonAnnotation.getSimpleName()
                            + " annotated method : " + method.getName() + " in class : " + clazz.getName()
                            + " must return void type");
                }

                if (isNoCheckedExceptionEnforced() && ClassUtil.isMethodHasCheckedException(method))
                {
                    throw new WebBeansConfigurationException("@" + commonAnnotation.getSimpleName()
                            + " annotated method : " + method.getName() + " in class : " + clazz.getName()
                            + " can not throw any checked exception");
                }

                if (Modifier.isStatic(method.getModifiers()))
                {
                    throw new WebBeansConfigurationException("@" + commonAnnotation.getSimpleName()
                            + " annotated method : " + method.getName() + " in class : " + clazz.getName()
                            + " can not be static");
                }
            }

        }


        return result;
    }

    /**
     * Check the {@link AroundInvoke} annotated method criterias, and return
     * around invoke method.
     * <p>
     * Web Beans container is responsible for setting around invoke annotation
     * if the web beans component is not an EJB components, in this case EJB
     * container is responsible for this.
     * </p>
     *
     * @param clazz checked class
     * @return around invoke method
     */
    public Set<Method> checkAroundInvokeAnnotationCriterias(Class<?> clazz, Class<? extends Annotation> annot)
    {
        Asserts.nullCheckForClass(clazz);

        Method[] methods = webBeansContext.getSecurityService().doPrivilegedGetDeclaredMethods(clazz);
        Method result = null;
        boolean found = false;
        for (Method method : methods)
        {
            if (AnnotationUtil.hasMethodAnnotation(method, annot))
            {
                // Overriden methods
                if (ClassUtil.isMoreThanOneMethodWithName(method.getName(), clazz))
                {
                    continue;
                }

                if (found)
                {
                    throw new WebBeansConfigurationException("@" + annot.getSimpleName()
                            + " annotation is declared more than one method in the class : " + clazz.getName());
                }

                found = true;
                result = method;

                // Check method criterias
                Class<?>[] params = method.getParameterTypes();
                if (params.length != 1 || !params[0].equals(InvocationContext.class))
                {
                    throw new WebBeansConfigurationException("@" + annot.getSimpleName() + " annotated method : "
                            + method.getName() + " in class : " + clazz.getName()
                            + " can not take any formal arguments other than InvocationContext");
                }

                if (!method.getReturnType().equals(Object.class))
                {
                    throw new WebBeansConfigurationException("@" + annot.getSimpleName() + " annotated method : "
                            + method.getName() + " in class : " + clazz.getName() + " must return Object type");
                }

                if (Modifier.isStatic(method.getModifiers()) || Modifier.isFinal(method.getModifiers()))
                {
                    throw new WebBeansConfigurationException("@" + annot.getSimpleName() + " annotated method : "
                            + method.getName() + " in class : " + clazz.getName() + " can not be static or final");
                }
            }
        }

        if (result != null)
        {
            Set<Method> resultSet = new HashSet<Method>();
            resultSet.add(result);
            return resultSet;
        }
        else
        {
            return null;
        }
    }

    public <T> Set<Method> checkAroundInvokeAnnotationCriterias(AnnotatedType<T> annotatedType, Class<? extends Annotation> annot)
    {
        Method result = null;
        boolean found = false;
        Set<AnnotatedMethod<? super T>> methods = annotatedType.getMethods();
        for(AnnotatedMethod<? super T> methodA : methods)
        {
            AnnotatedMethod<T> method = (AnnotatedMethod<T>)methodA;

            if (method.isAnnotationPresent(annot))
            {
                // Overriden methods
                if (ClassUtil.isMoreThanOneMethodWithName(method.getJavaMember().getName(),
                                                          annotatedType.getJavaClass()))
                {
                    continue;
                }

                if (found)
                {
                    throw new WebBeansConfigurationException("@" + annot.getSimpleName()
                            + " annotation is declared more than one method in the class : "
                            + annotatedType.getJavaClass().getName());
                }

                found = true;
                result = method.getJavaMember();

                List<AnnotatedParameter<T>> parameters = method.getParameters();
                List<Class<?>> clazzParameters = new ArrayList<Class<?>>();
                for(AnnotatedParameter<T> parameter : parameters)
                {
                    clazzParameters.add(ClassUtil.getClazz(parameter.getBaseType()));
                }

                Class<?>[] params = clazzParameters.toArray(new Class<?>[clazzParameters.size()]);

                if (params.length != 1 || !params[0].equals(InvocationContext.class))
                {
                    throw new WebBeansConfigurationException("@" + annot.getSimpleName() + " annotated method : "
                            + method.getJavaMember().getName() + " in class : " + annotatedType.getJavaClass().getName()
                            + " can not take any formal arguments other than InvocationContext");
                }

                if (!method.getJavaMember().getReturnType().equals(Object.class))
                {
                    throw new WebBeansConfigurationException("@" + annot.getSimpleName() + " annotated method : "
                            + method.getJavaMember().getName()+ " in class : " + annotatedType.getJavaClass().getName()
                            + " must return Object type");
                }

                if (Modifier.isStatic(method.getJavaMember().getModifiers()) ||
                    Modifier.isFinal(method.getJavaMember().getModifiers()))
                {
                    throw new WebBeansConfigurationException("@" + annot.getSimpleName() + " annotated method : "
                            + method.getJavaMember().getName( )+ " in class : " + annotatedType.getJavaClass().getName()
                            + " can not be static or final");
                }
            }
        }

        if (result != null)
        {
            Set<Method> resultSet = new HashSet<Method>();
            resultSet.add(result);
            return resultSet;
        }
        else
        {
            return null;
        }
    }


    /**
     * Configures the interceptor stack of the web beans component.
     *
     * @param annotation annotation type
     * @param definedInInterceptorClass check if annotation is defined in
     *            interceptor class (as opposed to bean class)
     * @param definedInMethod check if the interceptor is defined in the comp.
     *            method
     * @param stack interceptor stack
     * @param annotatedInterceptorClassMethod if definedInMethod, this specify
     *            method
     * @param defineWithInterceptorBinding if interceptor is defined with WebBeans
     *            spec, not EJB spec
     */
    public <T> void configureInterceptorMethods(Interceptor<?> webBeansInterceptor,
                                                 AnnotatedType<T> annotatedType,
                                                 Class<? extends Annotation> annotation,
                                                 boolean definedInInterceptorClass,
                                                 boolean definedInMethod,
                                                 List<InterceptorData> stack,
                                                 Method annotatedInterceptorClassMethod,
                                                 boolean defineWithInterceptorBinding)
    {
        InterceptorData intData;
        Set<Method> methods = null;
        OpenWebBeansEjbLCAPlugin ejbPlugin;
        Class<? extends Annotation> prePassivateClass  = null;
        Class<? extends Annotation> postActivateClass  = null;

        ejbPlugin = webBeansContext.getPluginLoader().getEjbLCAPlugin();
        if(ejbPlugin != null)
        {
            prePassivateClass  = ejbPlugin.getPrePassivateClass();
            postActivateClass  = ejbPlugin.getPostActivateClass();
        }

        //Check for default constructor of EJB based interceptor
        if(webBeansInterceptor == null)
        {
            if(definedInInterceptorClass)
            {
                Constructor<?> ct = getNoArgConstructor(annotatedType.getJavaClass());
                if (ct == null)
                {
                    throw new WebBeansConfigurationException("class : " + annotatedType.getJavaClass().getName()
                            + " must have no-arg constructor");
                }
            }
        }

        if (annotation.equals(AroundInvoke.class) ||
                annotation.equals(AroundTimeout.class))
        {
            methods = checkAroundInvokeAnnotationCriterias(annotatedType, annotation);
        }
        else if (annotation.equals(PostConstruct.class) || ((postActivateClass != null) && (annotation.equals(postActivateClass)))
                 || annotation.equals(PreDestroy.class) || ((prePassivateClass != null) && (annotation.equals(prePassivateClass))))
        {
            methods = checkCommonAnnotationCriterias(annotatedType, annotation, definedInInterceptorClass);
        }

        if (methods != null && !methods.isEmpty())
        {
            for (Method method : methods)
            {
                intData = new InterceptorDataImpl(defineWithInterceptorBinding, webBeansContext);
                intData.setDefinedInInterceptorClass(definedInInterceptorClass);
                intData.setDefinedInMethod(definedInMethod);
                intData.setInterceptorBindingMethod(annotatedInterceptorClassMethod);
                intData.setWebBeansInterceptor(webBeansInterceptor);

                if (definedInInterceptorClass)
                {
                    intData.setInterceptorClass(annotatedType.getJavaClass());
                }

                intData.setInterceptorMethod(method, annotation);
                stack.add(intData);
            }
        }
    }


    /**
     * Create a new instance of the given class using it's default constructor
     * regardless if the constructor is visible or not.
     * This is needed to construct some package scope classes in the TCK.
     *
     * @param <T>
     * @param clazz
     * @return
     * @throws WebBeansConfigurationException
     */
    public <T> T newInstanceForced(Class<T> clazz) throws WebBeansConfigurationException
    {
        // FIXME: This new instance should have JCDI injection performed
        Constructor<T> ct = getNoArgConstructor(clazz);
        if (ct == null)
        {
            throw new WebBeansConfigurationException("class : " + clazz.getName() + " must have no-arg constructor");
        }

        if (!ct.isAccessible())
        {
            webBeansContext.getSecurityService().doPrivilegedSetAccessible(ct, true);
        }

        try
        {
            return ct.newInstance();
        }
        catch( IllegalArgumentException e )
        {
            throw new WebBeansConfigurationException("class : " + clazz.getName() + " is not constructable", e);
        }
        catch( IllegalAccessException e )
        {
            throw new WebBeansConfigurationException("class : " + clazz.getName() + " is not constructable", e);
        }
        catch( InvocationTargetException e )
        {
            throw new WebBeansConfigurationException("class : " + clazz.getName() + " is not constructable", e);
        }
        catch( InstantiationException e )
        {
            throw new WebBeansConfigurationException("class : " + clazz.getName() + " is not constructable", e);
        }
    }

    /**
     * Returns true if interceptor stack contains interceptor with given type.
     *
     * @param stack interceptor stack
     * @param type interceptor type
     * @return true if stack contains the interceptor with given type
     */
    public static boolean isContainsInterceptorMethod(List<InterceptorData> stack, InterceptionType type)
    {
        if (stack.size() > 0)
        {
            Iterator<InterceptorData> it = stack.iterator();
            while (it.hasNext())
            {
                Method m = null;
                InterceptorData data = it.next();

                if (type.equals(InterceptionType.AROUND_INVOKE))
                {
                    m = data.getAroundInvoke();
                }
                else if (type.equals(InterceptionType.AROUND_TIMEOUT))
                {
                    m = data.getAroundTimeout();
                }
                else if (type.equals(InterceptionType.POST_CONSTRUCT))
                {
                    m = data.getPostConstruct();
                }
                else if (type.equals(InterceptionType.POST_ACTIVATE))
                {
                    m = data.getPostActivate();
                }
                else if (type.equals(InterceptionType.PRE_DESTROY))
                {
                    m = data.getPreDestroy();
                }
                else if (type.equals(InterceptionType.PRE_PASSIVATE))
                {
                    m = data.getPrePassivate();
                }

                if (m != null)
                {
                    return true;
                }

            }
        }

        return false;
    }

    public static String getManagedBeanDefaultName(String clazzName)
    {
        Asserts.assertNotNull(clazzName);

        if(clazzName.length() > 0)
        {
            StringBuilder name = new StringBuilder(clazzName);
            name.setCharAt(0, Character.toLowerCase(name.charAt(0)));

            return name.toString();
        }

        return clazzName;
    }

    public static String getProducerDefaultName(String methodName)
    {
        StringBuilder buffer = new StringBuilder(methodName);

        if (buffer.length() > 3 &&  (buffer.substring(0, 3).equals("get") || buffer.substring(0, 3).equals("set")))
        {

            if(Character.isUpperCase(buffer.charAt(3)))
            {
                buffer.setCharAt(3, Character.toLowerCase(buffer.charAt(3)));
            }

            return buffer.substring(3);
        }
        else if ((buffer.length() > 2 &&  buffer.substring(0, 2).equals("is")))
        {
            if(Character.isUpperCase(buffer.charAt(2)))
            {
                buffer.setCharAt(2, Character.toLowerCase(buffer.charAt(2)));
            }

            return buffer.substring(2);
        }

        else
        {
            buffer.setCharAt(0, Character.toLowerCase(buffer.charAt(0)));
            return buffer.toString();
        }
    }

    /**
     * Return true if a list of beans are directly specialized/extended each other.
     *
     * @param beans, a set of specialized beans.
     *
     * @return
     */
    protected static boolean isDirectlySpecializedBeanSet(Set<Bean<?>> beans)
    {

        ArrayList<AbstractOwbBean<?>> beanList = new ArrayList<AbstractOwbBean<?>>();

        for(Bean<?> bb : beans)
        {
            AbstractOwbBean<?>bean = (AbstractOwbBean<?>)bb;
            beanList.add(bean);
        }

        java.util.Collections.sort(beanList, new java.util.Comparator()
        {
            public int compare(Object o1, Object o2)
            {
                AbstractOwbBean<?> b1 = (AbstractOwbBean<?>)o1;
                AbstractOwbBean<?> b2 = (AbstractOwbBean<?>)o2;
                Class c1 = b1.getReturnType();
                Class c2 = b2.getReturnType();
                if (c2.isAssignableFrom(c1))
                {
                    return 1;
                }

                if (c1.isAssignableFrom(c2))
                {
                    return -1;
                }

                throw new InconsistentSpecializationException(c1 + " and " + c2 + "are not assignable to each other." );
            }
        });

        for(int i=0; i<beanList.size() - 1; i++)
        {
            if (!beanList.get(i).getReturnType().equals(beanList.get(i+1).getReturnType().getSuperclass()))
            {
                return false;
            }
        }
        return true;
    }

    public void configureSpecializations(List<Class<?>> beanClasses)
    {
        for(Class<?> clazz : beanClasses)
        {
            configureSpecializations(clazz, beanClasses);
        }
    }

    /**
     * Configures the bean specializations.
     * <p>
     * Specialized beans inherit the <code>name</code> property
     * from their parents. Specialized bean deployment priority
     * must be higher than its super class related bean.
     * </p>
     *
     * @param specializedClass specialized class
     * @throws DefinitionException if name is defined
     * @throws InconsistentSpecializationException related with priority
     * @throws WebBeansConfigurationException any other exception
     */
    protected void configureSpecializations(Class<?> specializedClass, List<Class<?>> beanClasses)
    {
        Asserts.nullCheckForClass(specializedClass);

        Bean<?> superBean = null;
        Bean<?> specialized = null;
        Set<Bean<?>> resolvers = isConfiguredWebBeans(specializedClass, true);
        AlternativesManager altManager = webBeansContext.getAlternativesManager();

        if (resolvers != null)
        {
            if(resolvers.isEmpty())
            {
                throw new InconsistentSpecializationException("Specialized bean for class : " + specializedClass
                        + " is not enabled in the deployment.");
            }

            specialized = resolvers.iterator().next();

            if(resolvers.size() > 1)
            {
                if (!isDirectlySpecializedBeanSet(resolvers))
                {
                    throw new InconsistentSpecializationException("More than one specialized bean for class : "
                            + specializedClass + " is enabled in the deployment.");
                }
                // find the widest bean which satisfies the specializedClass
                for( Bean<?> sp : resolvers)
                {
                    if (sp == specialized)
                    {
                        continue;
                    }

                    if (((AbstractOwbBean<?>)sp).getReturnType().
                            isAssignableFrom(((AbstractOwbBean<?>)specialized).getReturnType()))
                    {
                        specialized = sp;
                    }
                }
            }

            Class<?> superClass = specializedClass.getSuperclass();

            resolvers = isConfiguredWebBeans(superClass,false);

            for(Bean<?> candidates : resolvers)
            {
                AbstractOwbBean<?> candidate = (AbstractOwbBean<?>)candidates;

                if(!(candidate instanceof NewBean))
                {
                    if(candidate.getReturnType().equals(superClass))
                    {
                        superBean = candidates;
                        break;
                    }
                }
            }

            if (superBean != null)
            {
                webBeansContext.getBeanManagerImpl().getNotificationManager().disableOverriddenObservers(superClass);

                // Recursively configure super class first if super class is also a special bean.
                // So the name and bean meta data could be populated to this beanclass.
                if (beanClasses.contains(superClass) && ((AbstractOwbBean<?>)superBean).isEnabled())
                {
                    configureSpecializations(superClass, beanClasses);
                }

                if (!AnnotationUtil.hasClassAnnotation(specializedClass, Alternative.class))
                {
                    //disable superbean if the current bean is not an alternative
                    ((AbstractOwbBean<?>)superBean).setEnabled(false);
                }
                else if(altManager.isClassAlternative(specializedClass))
                {
                    //disable superbean if the current bean is an enabled alternative
                    ((AbstractOwbBean<?>)superBean).setEnabled(false);
                }

                AbstractOwbBean<?> comp = (AbstractOwbBean<?>)specialized;
                if (comp.isSpecializedBean())
                {
                    // This comp is already configured in previous invocation
                    // return directly, else Exception might be fired when set
                    // bean name again.
                    return;
                }

                //Check types of the beans
                if(comp.getClass() != superBean.getClass())
                {
                    throw new DefinitionException("@Specialized Class : " + specializedClass.getName()
                            + " and its super class may be the same type of bean,i.e, ManagedBean, SessionBean etc.");
                }

                if(superBean.getName() != null)
                {
                    if(comp.getName() != null)
                    {
                        throw new DefinitionException("@Specialized Class : " + specializedClass.getName()
                                + " may not explicitly declare a bean name");
                    }

                    comp.setSpecializedBean(true);
                }
            }

            else
            {
                throw new InconsistentSpecializationException("WebBean component class : " + specializedClass.getName()
                        + " is not enabled for specialized by the " + specializedClass + " class");
            }
        }

    }

    /**
     * Configure a list of producer method beans, which override the same method
     * and the bean classes are directly extended each other.
     *
     * @param sortedProducerBeans
     */
    protected void configSpecializedProducerMethodBeans(List<ProducerMethodBean> sortedProducerBeans)
    {
        if (sortedProducerBeans.isEmpty())
        {
            return;
        }

        AlternativesManager altManager = webBeansContext.getAlternativesManager();
        Method superMethod = sortedProducerBeans.get(0).getCreatorMethod();

        for(int i=1; i<sortedProducerBeans.size(); i++)
        {
            ProducerMethodBean bean = sortedProducerBeans.get(i);
            ProducerMethodBean superBean = sortedProducerBeans.get(i - 1);

            // inherit name is super class has name
            boolean isSuperHasName = configuredProducerSpecializedName(bean, bean.getCreatorMethod(), superMethod);

            // disable super bean if needed
            if (bean.getCreatorMethod().getAnnotation(Alternative.class) == null)
            {
                //disable superbean if the current bean is not an alternative
                superBean.setEnabled(false);
            }
            else if(altManager.isClassAlternative(bean.getBeanClass()))
            {
                //disable superbean if the current bean is an enabled alternative
                superBean.setEnabled(false);
            }

            //if no name defined, set superMethod to this bean since this
            //bean's method might have name defined.
            if (!isSuperHasName)
            {
                superMethod = bean.getCreatorMethod();
            }
        }
    }

    /**
     * Configure direct/indirect specialized producer method beans.
     */
    public void configureProducerMethodSpecializations()
    {
        Method method;
        ProducerMethodBean pbean;
        ProducerMethodBean pLeft;
        ProducerMethodBean pRight;

        // collect all producer method beans
        Set<Bean<?>> beans = webBeansContext.getBeanManagerImpl().getBeans();
        List<ProducerMethodBean> producerBeans = new ArrayList<ProducerMethodBean>();
        for(Bean b : beans)
        {
            if (b instanceof ProducerMethodBean)
            {
                producerBeans.add((ProducerMethodBean)b);
            }
        }

        // create sorted bean helper.
        SortedListHelper<ProducerMethodBean> producerBeanListHelper = new
                SortedListHelper<ProducerMethodBean>(new ArrayList<ProducerMethodBean>(),
                new Comparator<ProducerMethodBean> ()
                {
                    public int compare(ProducerMethodBean e1, ProducerMethodBean e2)
                    {
                        if (e1.getBeanClass().isAssignableFrom(e2.getBeanClass()))
                        {
                            return -1;
                        }
                        else if (e1.equals(e2))
                        {
                            return 0;
                        }
                        return 1;
                    }
                });

        while(true)
        {
            pbean = null;
            method = null;
            producerBeanListHelper.clear();

            //locate a specialized bean
            for(ProducerMethodBean pb : producerBeans)
            {
                if (pb.isSpecializedBean())
                {
                    pbean = pb;
                    method = pb.getCreatorMethod();
                    producerBeanListHelper.add(pb);
                    break;
                }
            }
            if (pbean == null)
            {
                break;
            }

            pRight = pbean;
            pLeft = pRight;
            boolean pLeftContinue = true;
            boolean pRightContinue = true;

            // find all pbean's super beans and sub sub beans
            while(pLeftContinue || pRightContinue)
            {
                pRightContinue = false;
                pLeftContinue = false;
                for(ProducerMethodBean pb : producerBeans)
                {
                    //left
                    if (pLeft!= null &&
                        pLeft.getBeanClass().getSuperclass().equals(pb.getBeanClass()))
                    {
                        Method superMethod = ClassUtil.getClassMethodWithTypes(pb.getBeanClass(), method.getName(),
                                Arrays.asList(method.getParameterTypes()));

                        //Added by GE, method check is necessary otherwise getting wrong method qualifier annotations
                        if (superMethod != null && superMethod.equals(pb.getCreatorMethod()))
                        {
                            producerBeanListHelper.add(pb);
                            pLeft = (pb.isSpecializedBean()) ? pb : null;
                        }
                        else
                        {
                            pLeft = null;
                        }
                        if (pLeft != null)
                        {
                            pLeftContinue = true;
                        }
                    }
                    //right
                    if (pRight != null &&
                        pb.getBeanClass().getSuperclass().equals(pRight.getBeanClass()))
                    {
                        if (!pb.isSpecializedBean())
                        {
                            pRight = null;
                        }
                        else
                        {
                            Method superMethod = ClassUtil.getClassMethodWithTypes(pb.getBeanClass(), method.getName(),
                                    Arrays.asList(method.getParameterTypes()));
                            //Added by GE, method check is necessary otherwise getting wrong method qualifier annotations
                            if (superMethod != null && superMethod.equals(pb.getCreatorMethod()))
                            {
                                producerBeanListHelper.add(pb);
                                pRight = pb;
                            }
                            else
                            {
                                pRight = null;
                            }
                        }
                        if (pRight != null)
                        {
                            pRightContinue = true;
                        }
                    }
                } // for
            } // while

            //remove the group from producer bean list
            for(ProducerMethodBean pb : producerBeanListHelper.getList())
            {
                producerBeans.remove(pb);
            }
            //configure the directly extended producer beans
            configSpecializedProducerMethodBeans(producerBeanListHelper.getList());
        }
    }


    public Set<Bean<?>> isConfiguredWebBeans(Class<?> clazz,boolean annotate)
    {
        Asserts.nullCheckForClass(clazz);

        Set<Bean<?>> beans = new HashSet<Bean<?>>();

        Set<Bean<?>> components = webBeansContext.getBeanManagerImpl().getComponents();
        Iterator<Bean<?>> it = components.iterator();

        while (it.hasNext())
        {
            AbstractOwbBean<?> bean = (AbstractOwbBean<?>)it.next();

            boolean enterprise = false;
            if(bean instanceof EnterpriseBeanMarker)
            {
                enterprise = true;
            }

            if (bean.getTypes().contains(clazz) ||
                    (enterprise && bean.getBeanClass().equals(clazz)))
            {
                if(annotate)
                {
                    if(bean.getReturnType().isAnnotationPresent(Specializes.class))
                    {
                        if(!(bean instanceof NewBean))
                        {
                            beans.add(bean);
                        }
                    }
                }
                else
                {
                    beans.add(bean);
                }
            }
        }

        return beans;
    }

    public <T> Constructor<T> getNoArgConstructor(Class<T> clazz)
    {
        return webBeansContext.getSecurityService().doPrivilegedGetDeclaredConstructor(clazz);
    }

    /**
     * Configures the name of the producer method for specializing the parent.
     *
     * @param component producer method component
     * @param method specialized producer method
     * @param superMethod overriden super producer method
     */
    public boolean configuredProducerSpecializedName(AbstractOwbBean<?> component, Method method, Method superMethod)
    {
        return webBeansContext.getAnnotationManager().configuredProducerSpecializedName(component, method, superMethod);
    }

    /**
     * Returns true if instance injection point false otherwise.
     *
     * @param injectionPoint injection point definition
     * @return true if instance injection point
     */
    public static boolean checkObtainsInjectionPointConditions(InjectionPoint injectionPoint)
    {
        Type type = injectionPoint.getType();

        Class<?> candidateClazz = null;
        if(type instanceof Class)
        {
            candidateClazz = (Class<?>)type;
        }
        else if(type instanceof ParameterizedType)
        {
            ParameterizedType pt = (ParameterizedType)type;
            candidateClazz = (Class<?>)pt.getRawType();
        }

        if(!candidateClazz.isAssignableFrom(Instance.class))
        {
            return false;
        }

        Class<?> rawType;

        if(ClassUtil.isParametrizedType(injectionPoint.getType()))
        {
            ParameterizedType pt = (ParameterizedType)injectionPoint.getType();

            rawType = (Class<?>) pt.getRawType();

            Type[] typeArgs = pt.getActualTypeArguments();

            if(!(rawType.isAssignableFrom(Instance.class)))
            {
                throw new WebBeansConfigurationException("<Instance> field injection " + injectionPoint.toString()
                        + " must have type javax.inject.Instance");
            }
            else
            {
                if(typeArgs.length != 1)
                {
                    throw new WebBeansConfigurationException("<Instance> field injection " + injectionPoint.toString()
                            + " must not have more than one actual type argument");
                }
            }
        }
        else
        {
            throw new WebBeansConfigurationException("<Instance> field injection " + injectionPoint.toString()
                    + " must be defined as ParameterizedType with one actual type argument");
        }

        return true;
    }

    /**
     * The result of this invocation get's cached
     * @see #isScopeTypeNormalCache
     * @param scopeType
     * @return <code>true</code> if the given scopeType represents a
     *         {@link javax.enterprise.context.NormalScope}d bean
     */
    public boolean isScopeTypeNormal(Class<? extends Annotation> scopeType)
    {
        Asserts.assertNotNull(scopeType, "scopeType argument can not be null");

        Boolean isNormal = isScopeTypeNormalCache.get(scopeType);

        if (isNormal != null)
        {
            return isNormal.booleanValue();
        }


        if (scopeType.isAnnotationPresent(NormalScope.class))
        {
            isScopeTypeNormalCache.put(scopeType, Boolean.TRUE);
            return true;
        }

        if(scopeType.isAnnotationPresent(Scope.class))
        {
            isScopeTypeNormalCache.put(scopeType, Boolean.FALSE);
            return false;
        }

        List<ExternalScope> additionalScopes = webBeansContext.getBeanManagerImpl().getAdditionalScopes();
        for (ExternalScope additionalScope : additionalScopes)
        {
            if (additionalScope.getScope().equals(scopeType))
            {
                isNormal = additionalScope.isNormal() ? Boolean.TRUE : Boolean.FALSE;
                isScopeTypeNormalCache.put(scopeType, isNormal);
                return isNormal.booleanValue();
            }
        }

        // no scopetype found so far -> kawumms
        throw new IllegalArgumentException("scopeType argument must be annotated with @Scope or @NormalScope");
    }

    /**
     * we cache results of calls to {@link #isScopeTypeNormalCache} because
     * this doesn't change at runtime.
     * We don't need to take special care about classloader
     * hierarchies, because each cl has other classes.
     */
    private static Map<Class<? extends Annotation>, Boolean> isScopeTypeNormalCache =
            new ConcurrentHashMap<Class<? extends Annotation>, Boolean>();
    
    public static void checkNullInstance(Object instance, Class<? > scopeType, String errorMessage, 
            Object... errorMessageArgs)
    {
        if (instance == null)
        {
            if (!scopeType.equals(Dependent.class))
            {
                String message = format(errorMessage, errorMessageArgs);
                throw new IllegalProductException(message);
            }
        }
    }

    public void checkSerializableScopeType(Class<? extends Annotation> scopeType, boolean isSerializable, String errorMessage,
            Object... errorMessageArgs)
    {
        if (webBeansContext.getBeanManagerImpl().isPassivatingScope(scopeType))
        {
            if (!isSerializable)
            {
                String message = format(errorMessage, errorMessageArgs);
                throw new IllegalProductException(message);
            }
        }
    }

    public static Bean<?> getMostSpecializedBean(BeanManager manager, Bean<?> component)
    {
         Set<Bean<?>> beans;

         if (component instanceof EnterpriseBeanMarker)
         {
             beans = new HashSet<Bean<?>>();
             Set<Bean<?>> allBeans = ((BeanManagerImpl)(manager)).getBeans(Object.class, AnnotationUtil.asArray(component.getQualifiers()));

             for(Bean<?> candidateBean : allBeans)
             {
                 if (candidateBean instanceof EnterpriseBeanMarker)
                 {
                     /*
                      * If a bean class of a session bean X is annotated @Specializes, then the bean class of X must directly extend
                      * the bean class of another session bean Y. Then X directly specializes Y, as defined in Section 4.3, ‚"Specialization".
                      */
                     Class<?> candidateSuperClass = candidateBean.getBeanClass().getSuperclass();
                     if (candidateSuperClass.equals(component.getBeanClass()))
                     {
                         beans.add(candidateBean);
                     }
                 }
             }
         }
         else
         {
             beans = manager.getBeans(component.getBeanClass(),
                     AnnotationUtil.asArray(component.getQualifiers()));
         }

        for(Bean<?> bean : beans)
        {
            Bean<?> find = bean;

            if(!find.equals(component))
            {
                if(AnnotationUtil.hasClassAnnotation(find.getBeanClass(), Specializes.class))
                {
                    return getMostSpecializedBean(manager, find);
                }
            }
        }

        return component;
    }

    /**
     * Returns <code>ProcessAnnotatedType</code> event.
     * @param <T> bean type
     * @param annotatedType bean class
     * @return event
     */
    public <T> GProcessAnnotatedType fireProcessAnnotatedTypeEvent(AnnotatedType<T> annotatedType)
    {
        GProcessAnnotatedType processAnnotatedEvent = new GProcessAnnotatedType(annotatedType);

        //Fires ProcessAnnotatedType
        webBeansContext.getBeanManagerImpl().fireEvent(processAnnotatedEvent,AnnotationUtil.EMPTY_ANNOTATION_ARRAY);

        if (processAnnotatedEvent.isModifiedAnnotatedType())
        {
            webBeansContext.getAnnotatedElementFactory().setAnnotatedType(processAnnotatedEvent.getAnnotatedType());
        }

        return processAnnotatedEvent;
    }

    /**
     * Returns <code>ProcessInjectionTarget</code> event.
     * @param <T> bean type
     * @param bean bean instance
     * @return event
     */
    public <T> GProcessInjectionTarget fireProcessInjectionTargetEvent(AbstractInjectionTargetBean<T> bean)
    {
        GProcessInjectionTarget processInjectionTargetEvent = createProcessInjectionTargetEvent(bean);
        return fireProcessInjectionTargetEvent(processInjectionTargetEvent);


    }

    public GProcessInjectionTarget fireProcessInjectionTargetEvent(GProcessInjectionTarget processInjectionTargetEvent)
    {
        //Fires ProcessInjectionTarget
        webBeansContext.getBeanManagerImpl().fireEvent(processInjectionTargetEvent, AnnotationUtil.EMPTY_ANNOTATION_ARRAY);

        return processInjectionTargetEvent;
    }

    public <T> GProcessInjectionTarget createProcessInjectionTargetEvent(AbstractInjectionTargetBean<T> bean)
    {
        InjectionTargetProducer<T> injectionTarget = new InjectionTargetProducer<T>(bean);
        return new GProcessInjectionTarget(injectionTarget, bean.getAnnotatedType());
    }


    /**
     * Returns <code>ProcessInjectionTarget</code> event.
     * @param <T> bean type
     * @return event
     */
    public <T> GProcessInjectionTarget fireProcessInjectionTargetEventForJavaEeComponents(Class<T> componentClass)
    {
        AnnotatedType<T> annotatedType = webBeansContext.getAnnotatedElementFactory().newAnnotatedType(componentClass);
        InjectionTarget<T> injectionTarget = webBeansContext.getBeanManagerImpl().createInjectionTarget(annotatedType);
        GProcessInjectionTarget processInjectionTargetEvent = new GProcessInjectionTarget(injectionTarget,annotatedType);

        //Fires ProcessInjectionTarget
        return fireProcessInjectionTargetEvent(processInjectionTargetEvent);

    }


    public GProcessProducer fireProcessProducerEventForMethod(ProducerMethodBean<?> producerMethod, AnnotatedMethod<?> method)
    {
        GProcessProducer producerEvent = new GProcessProducer(new ProducerBeansProducer(producerMethod),method);

        //Fires ProcessProducer for methods
        webBeansContext.getBeanManagerImpl().fireEvent(producerEvent, AnnotationUtil.EMPTY_ANNOTATION_ARRAY);

        return producerEvent;
    }

    public GProcessProducer fireProcessProducerEventForField(ProducerFieldBean<?> producerField, AnnotatedField<?> field)
    {
        GProcessProducer producerEvent = new GProcessProducer(new ProducerBeansProducer(producerField),field);

        //Fires ProcessProducer for fields
        webBeansContext.getBeanManagerImpl().fireEvent(producerEvent, AnnotationUtil.EMPTY_ANNOTATION_ARRAY);

        return producerEvent;
    }

    public void fireProcessProducerMethodBeanEvent(Map<ProducerMethodBean<?>, AnnotatedMethod<?>> annotatedMethods, AnnotatedType<?> annotatedType)
    {
        WebBeansContext webBeansContext = this.webBeansContext;
        AnnotationManager annotationManager = webBeansContext.getAnnotationManager();

        for(Map.Entry<ProducerMethodBean<?>, AnnotatedMethod<?>> beanEntry : annotatedMethods.entrySet())
        {
            ProducerMethodBean<?> bean = beanEntry.getKey();
            AnnotatedMethod<?> annotatedMethod = beanEntry.getValue();
            Annotation[] annotationsFromSet = AnnotationUtil.asArray(bean.getQualifiers());
            Method disposal = annotationManager.getDisposalWithGivenAnnotatedMethod(annotatedType, bean.getReturnType(), annotationsFromSet);

            AnnotatedMethod<?> disposalAnnotated = null;
            GProcessProducerMethod processProducerMethodEvent = null;
            if(disposal != null)
            {
                disposalAnnotated = webBeansContext.getAnnotatedElementFactory().newAnnotatedMethod(disposal, annotatedType);
                processProducerMethodEvent = new GProcessProducerMethod(bean,annotatedMethod,
                                                                        disposalAnnotated.getParameters().get(0));
            }
            else
            {
                processProducerMethodEvent = new GProcessProducerMethod(bean,annotatedMethod,null);
            }


            //Fires ProcessProducer
            webBeansContext.getBeanManagerImpl().fireEvent(processProducerMethodEvent, AnnotationUtil.EMPTY_ANNOTATION_ARRAY);
        }
    }

    public void fireProcessObservableMethodBeanEvent(Map<ObserverMethod<?>,AnnotatedMethod<?>> annotatedMethods)
    {
        for(Map.Entry<ObserverMethod<?>, AnnotatedMethod<?>> observableMethodEntry : annotatedMethods.entrySet())
        {
            ObserverMethod<?> observableMethod = observableMethodEntry.getKey();
            AnnotatedMethod<?> annotatedMethod = observableMethodEntry.getValue();

            GProcessObservableMethod event = new GProcessObservableMethod(annotatedMethod, observableMethod);

            //Fires ProcessProducer
            webBeansContext.getBeanManagerImpl().fireEvent(event, AnnotationUtil.EMPTY_ANNOTATION_ARRAY);
        }
    }


    public void fireProcessProducerFieldBeanEvent(Map<ProducerFieldBean<?>,AnnotatedField<?>> annotatedFields)
    {
        for(Map.Entry<ProducerFieldBean<?>, AnnotatedField<?>> beanEntry : annotatedFields.entrySet())
        {
            ProducerFieldBean<?> bean = beanEntry.getKey();
            AnnotatedField<?> field = beanEntry.getValue();

            GProcessProducerField processProducerFieldEvent = new GProcessProducerField(bean,field);

            //Fire ProcessProducer
            webBeansContext.getBeanManagerImpl().fireEvent(processProducerFieldEvent, AnnotationUtil.EMPTY_ANNOTATION_ARRAY);
        }
    }

    public static void checkInjectionPointNamedQualifier(InjectionPoint injectionPoint)
    {
        Set<Annotation> qualifierset = injectionPoint.getQualifiers();
        Named namedQualifier = null;
        for(Annotation qualifier : qualifierset)
        {
            if(qualifier.annotationType().equals(Named.class))
            {
                namedQualifier = (Named)qualifier;
                break;
            }
        }

        if(namedQualifier != null)
        {
            String value = namedQualifier.value();

            if(value == null || value.equals(""))
            {
                Member member = injectionPoint.getMember();
                if(!(member instanceof Field))
                {
                    throw new WebBeansConfigurationException("Injection point type : " + injectionPoint
                                                             + " can not define @Named qualifier without value!");
                }
            }
        }

    }

    /**
     * Sets bean enabled flag.
     * @param bean bean instance
     */
    public void setInjectionTargetBeanEnableFlag(InjectionTargetBean<?> bean)
    {
        bean.setEnabled(isBeanEnabled(bean));
    }

    public boolean isBeanEnabled(InjectionTargetBean<?> bean)
    {
        Asserts.assertNotNull(bean, "bean can not be null");
        return isBeanEnabled(bean.getAnnotatedType(), bean.getReturnType(), bean.getStereotypes());
    }
    
    public boolean isBeanEnabled(AnnotatedType<?> at, Class<?> beanType, Set<Class<? extends Annotation>> stereotypes)
    {
        
        boolean isAlternative = hasInjectionTargetBeanAnnotatedWithAlternative(beanType, stereotypes); 

        if(!isAlternative)
        {
            isAlternative =  at.getAnnotation(Alternative.class) != null;
        }
        
        if(isAlternative && !webBeansContext.getAlternativesManager().isAlternative(beanType, stereotypes))
        {
            return false;
        }
        return true;
    }

    public static boolean hasInjectionTargetBeanAnnotatedWithAlternative(InjectionTargetBean<?> bean)
    {
        return hasInjectionTargetBeanAnnotatedWithAlternative(bean.getReturnType(), bean.getStereotypes());
    }
    
    public static boolean hasInjectionTargetBeanAnnotatedWithAlternative(Class<?> beanType, Set<Class<? extends Annotation>> stereotypes)
    {
        Asserts.assertNotNull(beanType, "bean type can not be null");
        Asserts.assertNotNull(stereotypes, "stereotypes can not be null");

        boolean alternative = false;

        if(AnnotationUtil.hasClassAnnotation(beanType, Alternative.class))
        {
            alternative = true;
        }

        if(!alternative)
        {
            for(Class<? extends Annotation> stereoType : stereotypes)
            {
                if(AnnotationUtil.hasClassAnnotation(stereoType, Alternative.class))
                {
                    alternative = true;
                    break;
                }
            }

        }

        return alternative;

    }

    public void setBeanEnableFlagForProducerBean(InjectionTargetBean<?> parent, AbstractProducerBean<?> producer, Annotation[] annotations)
    {
        Asserts.assertNotNull(parent, "parent can not be null");
        Asserts.assertNotNull(producer, "producer can not be null");
        producer.setEnabled(isProducerBeanEnabled(parent, producer.getStereotypes(), annotations));
    }
    
    public boolean isProducerBeanEnabled(InjectionTargetBean<?> parent, Set<Class<? extends Annotation>> stereotypes, Annotation[] annotations)
    {

        boolean alternative = false;

        if (AnnotationUtil.hasAnnotation(annotations, Alternative.class))
        {
            alternative = true;
        }

        if (!alternative)
        {
            for (Class<? extends Annotation> stereoType : stereotypes)
            {
                if (AnnotationUtil.hasClassAnnotation(stereoType, Alternative.class))
                {
                    alternative = true;
                    break;
                }
            }
        }

        if (alternative)
        {
            return hasInjectionTargetBeanAnnotatedWithAlternative(parent) &&
                    webBeansContext.getAlternativesManager().isBeanHasAlternative(parent);
        }
        else
        {
            return parent.isEnabled();
        }
    }

    public static boolean isExtensionEventType(Class<?> clazz)
    {
        if(clazz.equals(BeforeBeanDiscovery.class) ||
                clazz.equals(AfterBeanDiscovery.class) ||
                clazz.equals(AfterDeploymentValidation.class) ||
                clazz.equals(BeforeShutdown.class) ||
                clazz.equals(GProcessAnnotatedType.class) ||
                clazz.equals(GProcessInjectionTarget.class) ||
                clazz.equals(GProcessProducer.class) ||
                clazz.equals(GProcessProducerField.class) ||
                clazz.equals(GProcessProducerMethod.class) ||
                clazz.equals(GProcessManagedBean.class) ||
                clazz.equals(GProcessBean.class) ||
                clazz.equals(GProcessSessionBean.class) ||
                clazz.equals(GProcessObservableMethod.class)
                )
        {
            return true;
        }

        return false;
    }

    public static boolean isExtensionBeanEventType(Class<?> clazz)
    {
        if(clazz.equals(GProcessAnnotatedType.class) ||
                clazz.equals(GProcessInjectionTarget.class) ||
                clazz.equals(GProcessManagedBean.class) ||
                clazz.equals(GProcessSessionBean.class) ||
                clazz.equals(GProcessBean.class)
                )
        {
            return true;
        }

        return false;
    }

    public static boolean isDefaultExtensionBeanEventType(Class<?> clazz)
    {
        if(clazz.equals(ProcessAnnotatedType.class) ||
                clazz.equals(ProcessInjectionTarget.class) ||
                clazz.equals(ProcessManagedBean.class) ||
                clazz.equals(ProcessBean.class) ||
                clazz.equals(ProcessSessionBean.class)
                )
        {
            return true;
        }

        return false;
    }

    public static boolean isExtensionProducerOrObserverEventType(Class<?> clazz)
    {
        if(clazz.equals(GProcessProducer.class) ||
                clazz.equals(GProcessProducerField.class) ||
                clazz.equals(GProcessProducerMethod.class) ||
                clazz.equals(GProcessObservableMethod.class)
                )
        {
            return true;
        }

        return false;

    }

    public static boolean isDefaultExtensionProducerOrObserverEventType(Class<?> clazz)
    {
        if(clazz.equals(ProcessProducer.class) ||
                clazz.equals(ProcessProducerField.class) ||
                clazz.equals(ProcessProducerMethod.class) ||
                clazz.equals(ProcessObserverMethod.class)
                )
        {
            return true;
        }

        return false;

    }

    public static boolean isDependent(Bean<?> bean)
    {
        if(!(bean instanceof OwbBean))
        {
            if(bean.getScope().equals(Dependent.class))
            {
                return true;
            }

            return false;
        }

        return ((OwbBean) bean).isDependent();
    }

    public void inspectErrorStack(String logMessage)
    {
        BeanManagerImpl manager = webBeansContext.getBeanManagerImpl();
        //Looks for errors
        ErrorStack stack = manager.getErrorStack();
        try
        {
            if(stack.hasErrors())
            {
                stack.logErrors();
                throw new WebBeansConfigurationException(logMessage);
            }
        }
        finally
        {
            stack.clear();
        }
    }

    /**
     *
     * @param contextual the {@link Bean} to check
     * @return the uniqueId if it is {@link PassivationCapable} and enabled
     */
    public static String isPassivationCapable(Contextual<?> contextual)
    {
        if(contextual instanceof Bean)
        {
            if(contextual instanceof AbstractOwbBean)
            {
                if( ((AbstractOwbBean<?>)contextual).isPassivationCapable())
                {
                    return ((AbstractOwbBean<?>)contextual).getId();
                }
            }

            else if(contextual instanceof PassivationCapable)
            {
                PassivationCapable pc = (PassivationCapable)contextual;

                return pc.getId();
            }
        }
        else
        {
            if((contextual instanceof PassivationCapable) && (contextual instanceof Serializable))
            {
                PassivationCapable pc = (PassivationCapable)contextual;

                return pc.getId();
            }
        }

        return null;
    }

    /**
     * This method will be used in {@link AfterBeanDiscovery#addBean(javax.enterprise.inject.spi.Bean)}}
     */
    public <T> ManagedBean<T> defineManagedBeanWithoutFireEvents(AnnotatedType<T> type)
    {
        Class<T> clazz = type.getJavaClass();

        ManagedBeanBuilder<T, ManagedBean<T>> managedBeanCreator = new ManagedBeanBuilder<T, ManagedBean<T>>(webBeansContext, type);

        managedBeanCreator.defineApiType();

        //Define meta-data
        managedBeanCreator.defineStereoTypes();

        //Scope type
        managedBeanCreator.defineScopeType(WebBeansLoggerFacade.getTokenString(OWBLogConst.TEXT_MB_IMPL) + clazz.getName() +
                WebBeansLoggerFacade.getTokenString(OWBLogConst.TEXT_SAME_SCOPE));

        //Check for Enabled via Alternative
        setInjectionTargetBeanEnableFlag(managedBeanCreator.getBean());
        managedBeanCreator.checkCreateConditions();
        managedBeanCreator.defineName();
        managedBeanCreator.defineQualifiers();
        managedBeanCreator.defineConstructor();
        managedBeanCreator.defineInjectedFields();
        managedBeanCreator.defineInjectedMethods();
        managedBeanCreator.defineDisposalMethods();
        ManagedBean<T> managedBean = managedBeanCreator.getBean();
        managedBeanCreator.defineProducerMethods(managedBean);
        managedBeanCreator.defineProducerFields(managedBean);
        managedBeanCreator.defineObserverMethods(managedBean);

        WebBeansDecoratorConfig.configureDecorators(managedBean);
        webBeansContext.getWebBeansInterceptorConfig().defineBeanInterceptorStack(managedBean);

        managedBeanCreator.validateDisposalMethods(managedBean);//Define disposal method after adding producers

        return managedBean;
    }


    /**
     * Determines if the injection is to be performed into a static field.
     *
     * @param injectionPoint
     * @return <code>true</code> if the injection is into a static field
     */
    public static boolean isStaticInjection(InjectionPoint injectionPoint)
    {
        if (injectionPoint != null)
        {
            Member member = injectionPoint.getMember();
            if (member != null && Modifier.isStatic(member.getModifiers()))
            {
                return true;
            }
        }

        return false;
    }

    public boolean isPassivationCapableDependency(InjectionPoint injectionPoint)
    {
        //Don't attempt to get an instance of the delegate injection point
        if (injectionPoint.isDelegate())
        {
            return true;
        }
        InjectionResolver instance = webBeansContext.getBeanManagerImpl().getInjectionResolver();

        Bean<?> bean = instance.getInjectionPointBean(injectionPoint);
        if((bean instanceof EnterpriseBeanMarker) ||
                (bean instanceof ResourceBean) ||
                (bean instanceof InstanceBean) ||
                (bean instanceof EventBean) ||
                (bean instanceof InjectionPointBean) ||
                (bean instanceof BeanManagerBean)
                )
        {
            return true;
        }

        else if(webBeansContext.getBeanManagerImpl().isNormalScope(bean.getScope()))
        {
            return true;
        }
        else
        {
            if(isPassivationCapable(bean) != null)
            {
                return true;
            }
        }
        return false;
    }

    public static void throwRuntimeExceptions(Exception e)
    {
        if(RuntimeException.class.isAssignableFrom(e.getClass()))
        {
            throw (RuntimeException)e;
        }

        throw new RuntimeException(e);
    }

    /**
     * @return <code>true</code> if this annotated type represents a decorator.
     */
    public static boolean isDecorator(AnnotatedType<?> annotatedType)
    {
        return annotatedType.isAnnotationPresent(Decorator.class);
    }

    /**
     * Return true if this annotated type represents a decorator.
     * @param annotatedType annotated type
     * @return true if decorator
     */
    public boolean isAnnotatedTypeDecoratorOrInterceptor(AnnotatedType<?> annotatedType)
    {
        if(isDecorator(annotatedType) ||
           isCdiInterceptor(annotatedType))
        {
            return true;
        }
        else if(webBeansContext.getInterceptorsManager().isInterceptorClassEnabled(annotatedType.getJavaClass()))
        {
            return true;
        }
        else if(webBeansContext.getDecoratorsManager().isDecoratorEnabled(annotatedType.getJavaClass()))
        {
            return true;
        }


        return false;
    }

    /**
     * @return <code>true</code> if this AnnotatedType represents a CDI Interceptor
     *         defined via a {@link javax.interceptor.Interceptor} annotation
     */
    public static boolean isCdiInterceptor(AnnotatedType<?> annotatedType)
    {
        return annotatedType.isAnnotationPresent(javax.interceptor.Interceptor.class);
    }

    public <T> ManagedBean<T> defineManagedBean(AnnotatedType<T> type)
    {
        Class<T> clazz = type.getJavaClass();

        AnnotatedTypeBeanBuilder<T> managedBeanCreator = new AnnotatedTypeBeanBuilder<T>(type, webBeansContext);

        managedBeanCreator.defineApiType();

        //Define meta-data
        managedBeanCreator.defineStereoTypes();

        //Scope type
        managedBeanCreator.defineScopeType(WebBeansLoggerFacade.getTokenString(OWBLogConst.TEXT_MB_IMPL) + clazz.getName()
                                           + WebBeansLoggerFacade.getTokenString(OWBLogConst.TEXT_SAME_SCOPE));

        //Check for Enabled via Alternative
        managedBeanCreator.defineEnabled();
        managedBeanCreator.checkCreateConditions();
        managedBeanCreator.defineName();
        managedBeanCreator.defineQualifiers();
        managedBeanCreator.defineConstructor();
        managedBeanCreator.defineInjectedFields();
        managedBeanCreator.defineInjectedMethods();
        managedBeanCreator.defineDisposalMethods();
        ManagedBean<T> managedBean = managedBeanCreator.getBean();
        managedBeanCreator.defineProducerMethods(managedBean);
        managedBeanCreator.defineProducerFields(managedBean);
        managedBeanCreator.defineObserverMethods(managedBean);
        WebBeansDecoratorConfig.configureDecorators(managedBean);
        webBeansContext.getWebBeansInterceptorConfig().defineBeanInterceptorStack(managedBean);

        managedBeanCreator.validateDisposalMethods(managedBean); //Define disposal method after adding producers

        return managedBean;
    }

    @SuppressWarnings("unchecked")
    public <T> ManagedBean<T> defineAbstractDecorator(AnnotatedType<T> type)
    {

        ManagedBean<T> bean = defineManagedBean(type);

        //X TODO move proxy instance creation into JavassistProxyFactory!
        Class clazz = webBeansContext.getProxyFactory().createAbstractDecoratorProxyClass(bean);

        bean.setConstructor(defineConstructor(clazz));
        bean.setIsAbstractDecorator(true);
        return bean;
    }

    /**
     * Define decorator bean.
     * @param <T> type info
     * @param annotatedType decorator class
     */
    public <T> ManagedBean<T> defineDecorator(AnnotatedType<T> annotatedType)
    {
        if (webBeansContext.getDecoratorsManager().isDecoratorEnabled(annotatedType.getJavaClass()))
        {
            ManagedBean<T> delegate = null;

            Set<AnnotatedMethod<? super T>> methods = annotatedType.getMethods();
            for(AnnotatedMethod<? super T> methodA : methods)
            {
                Method method = methodA.getJavaMember();
                if(AnnotationUtil.hasMethodAnnotation(method, Produces.class))
                {
                    throw new WebBeansConfigurationException("Decorator class : " + annotatedType.getJavaClass() + " can not have producer methods but it has one with name : "
                                                             + method.getName());
                }

                if(AnnotationUtil.hasMethodParameterAnnotation(method, Observes.class))
                {
                    throw new WebBeansConfigurationException("Decorator class : " + annotatedType.getJavaClass() + " can not have observer methods but it has one with name : "
                                                             + method.getName());
                }

            }

            if(Modifier.isAbstract(annotatedType.getJavaClass().getModifiers()))
            {
                delegate = defineAbstractDecorator(annotatedType);
            }
            else
            {
                delegate = defineManagedBean(annotatedType);
            }

            if (delegate != null)
            {
                WebBeansDecoratorConfig.configureDecoratorClass(delegate);
            }
            return delegate;
        }
        else
        {
            return null;
        }
    }

    public <T> ManagedBean<T> defineInterceptor(AnnotatedType<T> annotatedType)
    {
        Class<?> clazz = annotatedType.getJavaClass();
        if (webBeansContext.getInterceptorsManager().isInterceptorClassEnabled(clazz))
        {
            ManagedBean<T> delegate = null;

            webBeansContext.getInterceptorUtil().checkAnnotatedTypeInterceptorConditions(annotatedType);
            delegate = defineManagedBean(annotatedType);

            if (delegate != null)
            {
                Set<Annotation> annTypeSet = annotatedType.getAnnotations();
                Annotation[] anns = annTypeSet.toArray(new Annotation[annTypeSet.size()]);
                AnnotationManager annotationManager = webBeansContext.getAnnotationManager();
                webBeansContext.getWebBeansInterceptorConfig().configureInterceptorClass(delegate,
                                                               annotationManager.getInterceptorBindingMetaAnnotations(anns));
            }
            return delegate;
        }
        else
        {
            return null;
        }
    }

    /**
     * Checks the implementation class for checking conditions.
     *
     * @param type implementation class
     * @throws org.apache.webbeans.exception.WebBeansConfigurationException if any configuration exception occurs
     */
    public <X> void checkManagedBeanCondition(AnnotatedType<X> type) throws WebBeansConfigurationException
    {
        int modifier = type.getJavaClass().getModifiers();

        if (type.isAnnotationPresent(Decorator.class) && type.isAnnotationPresent(javax.interceptor.Interceptor.class))
        {
            throw new WebBeansConfigurationException("Annotated type "+ type +  " may not annotated with both @Interceptor and @Decorator annotation");
        }

        if (!type.isAnnotationPresent(Decorator.class) && !type.isAnnotationPresent(javax.interceptor.Interceptor.class))
        {
            checkManagedWebBeansInterceptorConditions(type);
        }

        if (Modifier.isInterface(modifier))
        {
            throw new WebBeansConfigurationException("ManagedBean implementation class : " + type.getJavaClass().getName() + " may not defined as interface");
        }
    }

    private <X> void checkManagedWebBeansInterceptorConditions(AnnotatedType<X> type)
    {
        Annotation[] anns = AnnotationUtil.asArray(type.getAnnotations());

        Class<?> clazz = type.getJavaClass();
        boolean hasClassInterceptors = false;
        AnnotationManager annotationManager = webBeansContext.getAnnotationManager();
        if (annotationManager.getInterceptorBindingMetaAnnotations(anns).length > 0)
        {
            hasClassInterceptors = true;
        }
        else
        {
            Annotation[] stereoTypes = annotationManager.getStereotypeMetaAnnotations(anns);
            for (Annotation stero : stereoTypes)
            {
                if (annotationManager.hasInterceptorBindingMetaAnnotation(stero.annotationType().getDeclaredAnnotations()))
                {
                    hasClassInterceptors = true;
                    break;
                }
            }
        }

        if(Modifier.isFinal(clazz.getModifiers()) && hasClassInterceptors)
        {
            throw new WebBeansConfigurationException("Final managed bean class with name : " + clazz.getName() + " can not define any InterceptorBindings");
        }

        Set<AnnotatedMethod<? super X>> methods = type.getMethods();
        for(AnnotatedMethod<? super X> methodA : methods)
        {
            Method method = methodA.getJavaMember();
            int modifiers = method.getModifiers();
            if (!method.isSynthetic() && !method.isBridge() && !Modifier.isStatic(modifiers) && !Modifier.isPrivate(modifiers) && Modifier.isFinal(modifiers))
            {
                if (hasClassInterceptors)
                {
                    throw new WebBeansConfigurationException("Maanged bean class : " + clazz.getName()
                                                    + " can not define non-static, non-private final methods. Because it is annotated with at least one @InterceptorBinding");
                }

                if (annotationManager.hasInterceptorBindingMetaAnnotation(
                    AnnotationUtil.asArray(methodA.getAnnotations())))
                {
                    throw new WebBeansConfigurationException("Method : " + method.getName() + "in managed bean class : " + clazz.getName()
                                                    + " can not be defined as non-static, non-private and final . Because it is annotated with at least one @InterceptorBinding");
                }
            }

        }
    }

    // Note: following code for method 'format' is taken from google guava - apache 2.0 licenced library
    // com.google.common.base.Preconditions.format(String, Object...)
    /**
     * Substitutes each {@code %s} in {@code template} with an argument. These
     * are matched by position - the first {@code %s} gets {@code args[0]}, etc.
     * If there are more arguments than placeholders, the unmatched arguments will
     * be appended to the end of the formatted message in square braces.
     *
     * @param template a non-null string containing 0 or more {@code %s}
     *     placeholders.
     * @param args the arguments to be substituted into the message
     *     template. Arguments are converted to strings using
     *     {@link String#valueOf(Object)}. Arguments can be null.
     */
    private static String format(String template,
            Object... args)
    {
        template = String.valueOf(template); // null -> "null"

        // start substituting the arguments into the '%s' placeholders
        StringBuilder builder = new StringBuilder(
                template.length() + 16 * args.length);
        int templateStart = 0;
        int i = 0;
        while (i < args.length)
        {
            int placeholderStart = template.indexOf("%s", templateStart);
            if (placeholderStart == -1)
            {
                break;
            }
            builder.append(template.substring(templateStart, placeholderStart));
            builder.append(args[i++]);
            templateStart = placeholderStart + 2;
        }
        builder.append(template.substring(templateStart));

        // if we run out of placeholders, append the extra args in square braces
        if (i < args.length)
        {
            builder.append(" [");
            builder.append(args[i++]);
            while (i < args.length)
            {
                builder.append(", ");
                builder.append(args[i++]);
            }
            builder.append(']');
        }

        return builder.toString();
    }


}
