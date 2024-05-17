package sockslib.server;

import sockslib.server.listener.*;
import sockslib.server.msg.CommandMessage;

import java.net.Socket;
import java.util.Map;

/**
 * The interface <code>SessionManager</code> represents a session manager.
 *
 * @author Youchao Feng
 * @version 1.0
 * @date Sep 30, 2015 12:36 PM
 */
public interface SessionManager {

    /**
     * Create a new {@link Session}.
     *
     * @param socket socket.
     * @return session.
     */
    Session newSession(Socket socket);

    /**
     * Returns the session by giving a id.
     *
     * @param id id of session.
     * @return session.
     */
    Session getSession(long id);

    void sessionOnCreate(Session session) throws CloseSessionException;

    void sessionOnCommand(Session session, CommandMessage message) throws CloseSessionException;

    void sessionOnException(Session session, Exception exception);

    void sessionOnClose(Session session);

    /**
     * Remove a {@link SessionListener} by name.
     *
     * @param name name of {@link SessionListener}.
     */
    void removeSessionListener(String name);

    /**
     * Add a {@link SessionListener}.
     *
     * @param name     name of {@link SessionListener}.
     * @param listener instance of {@link SessionListener}.
     */
    void addSessionListener(String name, SessionListener listener);

    /**
     * Returns all managed sessions.
     *
     * @return all managed sessions.
     */
    Map<Long, Session> getManagedSessions();

    SessionManager onSessionClose(String name, SessionCloseListener listener);

    SessionManager onSessionCreate(String name, SessionCreateListener listener);

    SessionManager onCommand(String name, CommandListener listener);

    SessionManager onException(String name, ExceptionListener listener);


}
