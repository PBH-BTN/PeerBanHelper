/*
 * Copyright 2015-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package sockslib.server;

import sockslib.common.AuthenticationException;
import sockslib.common.Credentials;
import sockslib.common.UsernamePasswordCredentials;
import sockslib.common.methods.UsernamePasswordMethod;
import sockslib.server.manager.MemoryBasedUserManager;
import sockslib.server.manager.User;
import sockslib.server.manager.UserManager;

/**
 * The class <code>UsernamePasswordAuthenticator</code> represents a username password
 * authenticator. It will be used by {@link UsernamePasswordMethod}.
 *
 * @author Youchao Feng
 * @version 1.0
 * @date Apr 16, 2015 11:30:46 AM
 */
public class UsernamePasswordAuthenticator implements Authenticator {

    public static final String USER_KEY = "USER";
    /**
     * {@link MemoryBasedUserManager} is default.
     */
    private UserManager userManager = new MemoryBasedUserManager();

    public UsernamePasswordAuthenticator() {
    }

    public UsernamePasswordAuthenticator(UserManager userManager) {
        this.userManager = userManager;
    }

    @Override
    public void doAuthenticate(Credentials credentials, Session session) throws
            AuthenticationException {
        if (credentials instanceof UsernamePasswordCredentials) {
            String username = credentials.getUserPrincipal().getName();
            String password = credentials.getPassword();
            User user = userManager.check(username, password);
            if (user == null) {
                authenticationFailed(session);
            }
            authenticationSuccess(session, user);

        } else {
            throw new AuthenticationException("Only support Username/Password Authentication");
        }
    }

    /**
     * This method will save user in session.
     *
     * @param session Current session.
     * @param user    user.
     */
    protected void authenticationSuccess(Session session, User user) {
        session.setAttribute(USER_KEY, user);
    }

    /**
     * This method will throw a {@link AuthenticationException}
     *
     * @param session Current session
     * @throws AuthenticationException {@link AuthenticationException}
     */
    protected void authenticationFailed(Session session) throws AuthenticationException {
        throw new AuthenticationException(
                "Authentication failed, client from " + session.getClientAddress());
    }

    public UserManager getUserManager() {
        return userManager;
    }

    public void setUserManager(UserManager userManager) {
        this.userManager = userManager;
    }

    public void addUser(String username, String password) {
        userManager.addUser(username, password);
    }

    public void deleteUser(String username) {
        userManager.delete(username);
    }

}
