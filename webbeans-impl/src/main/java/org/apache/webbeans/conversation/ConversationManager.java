/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law
 * or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.apache.webbeans.conversation;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.enterprise.context.Conversation;
import javax.enterprise.inject.spi.Bean;

import org.apache.webbeans.annotation.DefaultLiteral;
import org.apache.webbeans.config.WebBeansFinder;
import org.apache.webbeans.container.BeanManagerImpl;
import org.apache.webbeans.context.ConversationContext;
import org.apache.webbeans.util.Asserts;

/**
 * Manager for the conversations.
 * Each conversation is related with conversation id and session id.
 * 
 * @version $Rev$ $Date$
 *
 */
public class ConversationManager
{
    /**Current conversations*/
    private Map<Conversation, ConversationContext> conversations = new ConcurrentHashMap<Conversation, ConversationContext>();

    /**
     * Creates new conversation maanger
     */
    public ConversationManager()
    {
        
    }

    /**
     * Gets conversation manager instance.
     * @return conversation manager
     */
    public static ConversationManager getInstance()
    {
        ConversationManager manager = (ConversationManager) WebBeansFinder.getSingletonInstance(WebBeansFinder.SINGLETON_CONVERSATION_MANAGER);
        
        return manager;
    }

    /**
     * Adds new conversation context.
     * @param conversation new conversation
     * @param context new context
     */
    public void addConversationContext(Conversation conversation, ConversationContext context)
    {
        conversations.put(conversation, context);
    }

    /**
     * Remove given conversation.
     * @param conversation conversation instance
     * @return context
     */
    public ConversationContext removeConversation(Conversation conversation)
    {
        Asserts.assertNotNull(conversation, "conversation can not be null");

        return conversations.remove(conversation);
    }

    /**
     * Gets conversation's context instance.
     * @param conversation conversation instance
     * @return conversation related context
     */
    public ConversationContext getConversationContext(Conversation conversation)
    {
        Asserts.assertNotNull(conversation, "conversation can not be null");

        return conversations.get(conversation);
    }

    /**
     * Gets conversation with id and session id.
     * @param conversationId conversation id
     * @param sessionId session id
     * @return conversation
     */
    public Conversation getConversation(String conversationId, String sessionId)
    {
        Asserts.assertNotNull(conversationId, "conversationId parameter can not be null");
        Asserts.assertNotNull(sessionId,"sessionId parameter can not be null");

        ConversationImpl conv = null;
        Set<Conversation> set = conversations.keySet();
        Iterator<Conversation> it = set.iterator();

        while (it.hasNext())
        {
            conv = (ConversationImpl) it.next();
            if (conv.getId().equals(conversationId) && conv.getSessionId().equals(sessionId))
            {
                return conv;
            }
        }

        return null;

    }

    /**
     * Destroy conversations with given session id.
     * @param sessionId session id
     */
    public void destroyConversationContextWithSessionId(String sessionId)
    {
        Asserts.assertNotNull(sessionId, "sessionId parameter can not be null");

        ConversationImpl conv = null;
        Set<Conversation> set = conversations.keySet();
        Iterator<Conversation> it = set.iterator();

        while (it.hasNext())
        {
            conv = (ConversationImpl) it.next();
            if (conv.getSessionId().equals(sessionId))
            {
                it.remove();
            }
        }
    }

    /**
     * Creates new conversation instance.
     * @return new conversation instance
     */
    public Conversation createNewConversationInstance()
    {
        Conversation conversation = getConversationInstance();

        return conversation;

    }

    /**
     * Gets conversation instance from conversation bean.
     * @return conversation instance
     */
    @SuppressWarnings("unchecked")
    public Conversation getConversationInstance()
    {

        Bean<Conversation> bean = (Bean<Conversation>)BeanManagerImpl.getManager().resolveByType(Conversation.class, new DefaultLiteral()).iterator().next();
        Conversation conversation = BeanManagerImpl.getManager().getInstance(bean);

        return conversation;
    }

    /**
     * Destroy unactive conversations.
     */
    public void destroyWithRespectToTimout()
    {
        ConversationImpl conv = null;
        Set<Conversation> set = conversations.keySet();
        Iterator<Conversation> it = set.iterator();

        while (it.hasNext())
        {
            conv = (ConversationImpl) it.next();
            long timeout = conv.getTimeout();

            if (timeout != 0L)
            {
                if ((System.currentTimeMillis() - conv.getActiveTime()) > timeout)
                {
                    it.remove();
                }
            }
        }
    }

    /**
     * Destroys all conversations
     */
    public void destroyAllConversations()
    {
        if (conversations != null)
        {
            conversations.clear();
        }
    }
}
