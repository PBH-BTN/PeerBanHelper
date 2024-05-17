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

package sockslib.server.manager;


/**
 * The class <code>User</code> represents a user in SOCKS5 server.
 *
 * @author Youchao Feng
 * @version 1.0
 * @date Apr 16, 2015 11:29:44 AM
 */
public class User {

    /**
     * Username.
     */
    private String username;

    /**
     * Password.
     */
    private String password;

    /**
     * Constructs a instance of {@link User}.
     */
    public User() {
    }

    /**
     * Constructs a instance of {@link User} with username and password.
     *
     * @param username Username.
     * @param password Password.
     */
    public User(String username, String password) {
        this.username = username;
        this.password = password;
    }

    /**
     * Returns username.
     *
     * @return Username.
     */
    public String getUsername() {
        return username;
    }

    /**
     * Sets username.
     *
     * @param username Username.
     */
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * Returns password.
     *
     * @return Password.
     */
    public String getPassword() {
        return password;
    }

    /**
     * Sets password.
     *
     * @param password Password.
     */
    public void setPassword(String password) {
        this.password = password;
    }

    public User copy() {
        return new User(this.username, this.password);
    }

    @Override
    public String toString() {
        return "User{" +
                "username='" + username + '\'' +
                ", password='" + password + '\'' +
                '}';
    }
}
