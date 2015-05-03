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
package org.apache.webbeans.corespi.se;

import java.lang.annotation.Annotation;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.BusyConversationException;
import javax.enterprise.context.ContextException;
import javax.enterprise.context.ConversationScoped;
import javax.enterprise.context.Dependent;
import javax.enterprise.context.NonexistentConversationException;
import javax.enterprise.context.RequestScoped;
import javax.enterprise.context.SessionScoped;
import javax.enterprise.context.spi.Context;
import javax.inject.Singleton;

import org.apache.webbeans.annotation.DestroyedLiteral;
import org.apache.webbeans.annotation.InitializedLiteral;
import org.apache.webbeans.config.WebBeansContext;
import org.apache.webbeans.context.AbstractContextsService;
import org.apache.webbeans.context.ApplicationContext;
import org.apache.webbeans.context.ConversationContext;
import org.apache.webbeans.context.DependentContext;
import org.apache.webbeans.context.RequestContext;
import org.apache.webbeans.context.SessionContext;
import org.apache.webbeans.context.SingletonContext;


public class DefaultContextsService extends AbstractContextsService
{
    private static ThreadLocal<RequestContext> requestContext = null;

    private static ThreadLocal<SessionContext> sessionContext = null;

    private ApplicationContext applicationContext = null;

    private static ThreadLocal<ConversationContext> conversationContext = null;
    
    private static ThreadLocal<SingletonContext> singletonContext = null;

    private static ThreadLocal<DependentContext> dependentContext = null;


    private final boolean supportsConversation;

    static
    {
        requestContext = new ThreadLocal<RequestContext>();
        sessionContext = new ThreadLocal<SessionContext>();
        conversationContext = new ThreadLocal<ConversationContext>();
        dependentContext = new ThreadLocal<DependentContext>();
        singletonContext = new ThreadLocal<SingletonContext>();
    }

    public DefaultContextsService(WebBeansContext webBeansContext)
    {
        super(webBeansContext);
        this.supportsConversation = webBeansContext.getOpenWebBeansConfiguration().supportsConversation();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void endContext(Class<? extends Annotation> scopeType, Object endParameters)
    {
        
        if(supportsContext(scopeType))
        {
            if(scopeType.equals(RequestScoped.class))
            {
                stopRequestContext(endParameters);
            }
            else if(scopeType.equals(SessionScoped.class))
            {
                stopSessionContext(endParameters);
            }
            else if(scopeType.equals(ApplicationScoped.class))
            {
                stopApplicationContext(endParameters);
            }
            else if(scopeType.equals(ConversationScoped.class))
            {
                stopConversationContext(endParameters);
            }
            else if(scopeType.equals(Dependent.class))
            {
                //Do nothing
            }
            else
            {
                stopSingletonContext(endParameters);
            }
        }

    }


    /**
     * {@inheritDoc}
     */
    @Override
    public Context getCurrentContext(Class<? extends Annotation> scopeType)
    {
        if(scopeType.equals(RequestScoped.class))
        {
            return getCurrentRequestContext();
        }
        else if(scopeType.equals(SessionScoped.class))
        {
            return getCurrentSessionContext();
        }
        else if(scopeType.equals(ApplicationScoped.class))
        {
            return applicationContext;
        }
        else if(scopeType.equals(ConversationScoped.class) && supportsConversation)
        {
            return getCurrentConversationContext();
        }
        else if(scopeType.equals(Dependent.class))
        {
            return getCurrentDependentContext();
        }
        else if(scopeType.equals(Singleton.class))
        {
            return getCurrentSingletonContext();
        }

        return null;

    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void startContext(Class<? extends Annotation> scopeType, Object startParameter) throws ContextException
    {
        try
        {
            if(scopeType.equals(RequestScoped.class))
            {
                startRequestContext(startParameter);
            }
            else if(scopeType.equals(SessionScoped.class))
            {
                startSessionContext(startParameter);
            }
            else if(scopeType.equals(ApplicationScoped.class))
            {
                startApplicationContext(startParameter);
            }
            else if(scopeType.equals(ConversationScoped.class))
            {
                startConversationContext(startParameter);
            }
            else if(scopeType.equals(Dependent.class))
            {
                //Do nothing
            }
            else if(scopeType.equals(Singleton.class))
            {
                startSingletonContext(startParameter);
            }
        }
        catch(Exception e)
        {
            if(e instanceof ContextException)
            {
                throw (ContextException)e;
            }
            
            throw new ContextException(e);
        }        
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public boolean supportsContext(Class<? extends Annotation> scopeType)
    {
        
        return scopeType.equals(RequestScoped.class) ||
               scopeType.equals(SessionScoped.class) ||
               scopeType.equals(ApplicationScoped.class) ||
               scopeType.equals(ConversationScoped.class) ||
               scopeType.equals(Dependent.class) ||
               scopeType.equals(Singleton.class);
    }


    @Override
    public void destroy(Object destroyObject)
    {
        RequestContext requestCtx = requestContext.get();
        if (requestCtx != null)
        {
            requestCtx.destroy();
            requestContext.set(null);
            requestContext.remove();
        }

        SessionContext sessionCtx = sessionContext.get();
        if (sessionCtx != null)
        {
            sessionCtx.destroy();
            sessionContext.set(null);
            sessionContext.remove();
        }

        ConversationContext conversationCtx = conversationContext.get();
        if (conversationCtx != null)
        {
            conversationCtx.destroy();
            conversationContext.set(null);
            conversationContext.remove();
        }

        SingletonContext singletonCtx = singletonContext.get();
        if (singletonCtx != null)
        {
            singletonCtx.destroy();
            singletonContext.set(null);
            singletonContext.remove();
        }

        dependentContext.set(null);
        dependentContext.remove();

        if (applicationContext != null)
        {
            applicationContext.destroy();
            applicationContext.destroySystemBeans();
        }
    }



    private Context getCurrentConversationContext()
    {
        ConversationContext conversationCtx = conversationContext.get();
        if (conversationCtx == null)
        {
            conversationCtx = webBeansContext.getConversationManager().getConversationContext(getCurrentSessionContext());
            conversationContext.set(conversationCtx);

            // check for busy and non-existing conversations
            String conversationId = webBeansContext.getConversationService().getConversationId();
            if (conversationId != null && conversationCtx.getConversation().isTransient())
            {
                throw new NonexistentConversationException("Propogated conversation with cid=" + conversationId +
                        " cannot be restored. It creates a new transient conversation.");
            }

            if (conversationCtx.getConversation().iUseIt() > 1)
            {
                //Throw Busy exception
                throw new BusyConversationException("Propogated conversation with cid=" + conversationId +
                        " is used by other request. It creates a new transient conversation");
            }
        }

        return conversationCtx;
    }

    
    private Context getCurrentDependentContext()
    {        
        if(dependentContext.get() == null)
        {
            dependentContext.set(new DependentContext());
        }
        
        return dependentContext.get();
    }

    
    private Context getCurrentRequestContext()
    {        
        return requestContext.get();
    }

    
    private Context getCurrentSessionContext()
    {        
        return sessionContext.get();
    }

    
    private Context getCurrentSingletonContext()
    {        
        return singletonContext.get();
    }

    
    private void startApplicationContext(Object instance)
    {
        if (applicationContext != null)
        {
            // applicationContext is already started
            return;
        }

        ApplicationContext ctx = new ApplicationContext();
        ctx.setActive(true);

        applicationContext = ctx;

        // We do ALSO send the @Initialized(ApplicationScoped.class) at this location but this is WAY to early for userland apps
        // This also gets sent in the application startup code after AfterDeploymentValidation got fired.
        // see AbstractLifecycle#afterStartApplication
        webBeansContext.getBeanManagerImpl().fireEvent(new Object(), InitializedLiteral.INSTANCE_APPLICATION_SCOPED);
    }

    
    private void startConversationContext(Object object)
    {
        ConversationContext ctx = webBeansContext.getConversationManager().getConversationContext(getCurrentSessionContext());

        conversationContext.set(ctx);
    }

    
    private void startRequestContext(Object instance)
    {
        
        RequestContext ctx = new RequestContext();
        ctx.setActive(true);
        
        requestContext.set(ctx);
        webBeansContext.getBeanManagerImpl().fireEvent(new Object(), InitializedLiteral.INSTANCE_REQUEST_SCOPED);
    }

    
    private void startSessionContext(Object instance)
    {
        SessionContext ctx = new SessionContext();
        ctx.setActive(true);
        
        sessionContext.set(ctx);
        webBeansContext.getBeanManagerImpl().fireEvent(new Object(), InitializedLiteral.INSTANCE_SESSION_SCOPED);
    }

    
    private void startSingletonContext(Object object)
    {
        
        SingletonContext ctx = new SingletonContext();
        ctx.setActive(true);
        
        singletonContext.set(ctx);
        webBeansContext.getBeanManagerImpl().fireEvent(new Object(), InitializedLiteral.INSTANCE_SINGLETON_SCOPED);
    }

    
    private void stopApplicationContext(Object object)
    {
        if(applicationContext != null)
        {
            applicationContext.destroy();

            // this is needed to get rid of ApplicationScoped beans which are cached inside the proxies...
            WebBeansContext.currentInstance().getBeanManagerImpl().clearCacheProxies();
            webBeansContext.getBeanManagerImpl().fireEvent(new Object(), DestroyedLiteral.INSTANCE_APPLICATION_SCOPED);
        }
    }

    
    private void stopConversationContext(Object object)
    {
        if(conversationContext.get() != null)
        {
            conversationContext.get().destroy();   
        }

        conversationContext.set(null);
        conversationContext.remove();
    }

    
    private void stopRequestContext(Object instance)
    {
        // cleanup open conversations first
        if (supportsConversation)
        {
            cleanupConversations();
            conversationContext.set(null);
            conversationContext.remove();
        }


        if(requestContext.get() != null)
        {
            requestContext.get().destroy();   
        }

        requestContext.set(null);
        requestContext.remove();
        webBeansContext.getBeanManagerImpl().fireEvent(new Object(), DestroyedLiteral.INSTANCE_REQUEST_SCOPED);
    }

    
    private void stopSessionContext(Object instance)
    {
        if(sessionContext.get() != null)
        {
            sessionContext.get().destroy();   
        }

        sessionContext.set(null);
        sessionContext.remove();
        webBeansContext.getBeanManagerImpl().fireEvent(new Object(), DestroyedLiteral.INSTANCE_SESSION_SCOPED);
    }

    
    private void stopSingletonContext(Object object)
    {
        if(singletonContext.get() != null)
        {
            singletonContext.get().destroy();   
        }

        singletonContext.set(null);
        singletonContext.remove();
        webBeansContext.getBeanManagerImpl().fireEvent(new Object(), DestroyedLiteral.INSTANCE_SINGLETON_SCOPED);
    }

    private void cleanupConversations()
    {
        cleanupConversations(conversationContext.get());
    }



}
