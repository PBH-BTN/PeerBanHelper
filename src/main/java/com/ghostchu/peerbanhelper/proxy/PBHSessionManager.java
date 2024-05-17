package com.ghostchu.peerbanhelper.proxy;

import lombok.extern.slf4j.Slf4j;
import sockslib.server.Session;
import sockslib.server.SessionManager;
import sockslib.server.SocksSession;
import sockslib.server.listener.*;
import sockslib.server.msg.CommandMessage;

import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
public class PBHSessionManager implements SessionManager {

    private final AtomicLong sessionIdKeeper = new AtomicLong(0);
    private final Map<Long, Session> managedSessions = new ConcurrentHashMap<>();
    private final Map<String, SessionCreateListener> sessionCreateListenerMap = new ConcurrentHashMap<>();
    private final Map<String, SessionCloseListener> sessionCloseListenerMap = new ConcurrentHashMap<>();
    private final Map<String, CommandListener> commandListenerMap = new ConcurrentHashMap<>();
    private final Map<String, ExceptionListener> exceptionListenerMap = new ConcurrentHashMap<>();

    @Override
    public Session newSession(Socket socket) {
        Session session = new SocksSession(sessionIdKeeper.incrementAndGet(), socket, managedSessions);
        managedSessions.put(session.getId(), session);
        return session;
    }


    @Override
    public Session getSession(long id) {
        return managedSessions.get(id);
    }

    @Override
    public void sessionOnCreate(Session session) throws CloseSessionException {
        for (SessionCreateListener listener : sessionCreateListenerMap.values()) {
            listener.onCreate(session);
        }
    }

    @Override
    public void sessionOnCommand(Session session, CommandMessage message)
            throws CloseSessionException {
        for (CommandListener listener : commandListenerMap.values()) {
            listener.onCommand(session, message);
        }
    }

    @Override
    public void sessionOnException(Session session, Exception exception) {
        for (ExceptionListener listener : exceptionListenerMap.values()) {
            listener.onException(session, exception);
        }
    }

    @Override
    public void sessionOnClose(Session session) {
        for (SessionCloseListener listener : sessionCloseListenerMap.values()) {
            listener.onClose(session);
        }
    }

    @Override
    public void removeSessionListener(String name) {
        this.removeCommandListener(name);
        this.removeExceptionListener(name);
        this.removeSessionCloseListener(name);
        this.removeSessionCreateListener(name);
    }

    @Override
    public void addSessionListener(String name, SessionListener listener) {
        this.onCommand(name, listener);
        this.onException(name, listener);
        this.onSessionClose(name, listener);
        this.onSessionCreate(name, listener);
    }

    @Override
    public Map<Long, Session> getManagedSessions() {
        return managedSessions;
    }

    @Override
    public SessionManager onSessionClose(String name, SessionCloseListener listener) {
        sessionCloseListenerMap.put(name, listener);
        return this;
    }

    @Override
    public SessionManager onSessionCreate(String name, SessionCreateListener listener) {
        sessionCreateListenerMap.put(name, listener);
        return this;
    }

    @Override
    public SessionManager onCommand(String name, CommandListener listener) {
        commandListenerMap.put(name, listener);
        return this;
    }

    @Override
    public SessionManager onException(String name, ExceptionListener listener) {
        exceptionListenerMap.put(name, listener);
        return this;
    }

    public SessionCloseListener removeSessionCloseListener(String name) {
        return sessionCloseListenerMap.remove(name);
    }

    public SessionCreateListener removeSessionCreateListener(String name) {
        return sessionCreateListenerMap.remove(name);
    }

    public CommandListener removeCommandListener(String name) {
        return commandListenerMap.remove(name);
    }

    public ExceptionListener removeExceptionListener(String name) {
        return exceptionListenerMap.remove(name);
    }

}