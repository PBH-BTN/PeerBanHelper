package sockslib.server;

import sockslib.server.listener.*;
import sockslib.server.msg.CommandMessage;

import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

/**
 * The class <code>BasicSessionManager</code> implements {@link SessionManager}
 *
 * @author Youchao Feng
 * @version 1.0
 * @date Oct 10,2015 7:15 PM
 */
public class BasicSessionManager implements SessionManager {

    private static int nextSessionId = 0;
    private Map<Long, Session> managedSessions = new HashMap<>();
    private Map<String, SessionCreateListener> sessionCreateListenerMap = new HashMap<>();
    private Map<String, SessionCloseListener> sessionCloseListenerMap = new HashMap<>();
    private Map<String, CommandListener> commandListenerMap = new HashMap<>();
    private Map<String, ExceptionListener> exceptionListenerMap = new HashMap<>();

    @Override
    public Session newSession(Socket socket) {
        Session session = new SocksSession(++nextSessionId, socket, managedSessions);
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
