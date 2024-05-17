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

import java.util.List;

/**
 * The class <code>UserManager</code> represents a manager that can manage users.
 *
 * @author Youchao Feng
 * @version 1.0
 * @date Apr 16, 2015 11:30:18 AM
 */
public interface UserManager {

    void create(User user);

    /**
     * Adds a user to the {@link UserManager}.
     *
     * @param username Username.
     * @param password Password.
     * @return Current UserManager instance
     */
    UserManager addUser(String username, String password);

    /**
     * Finds a user by username and password. If there is no user matches the username and password,
     * return <code>null</code>
     *
     * @param username Username.
     * @param password Password.
     * @return Instance of {@link User}. If the user doesn't exist, it will return <code>null</code>.
     */
    User check(String username, String password);

    /**
     * Deletes a user by username.
     *
     * @param username Username.
     */
    void delete(String username);

    /**
     * Finds all users.
     *
     * @return All users.
     */
    List<User> findAll();

    /**
     * Updates an user information.
     *
     * @param user Updated user
     */
    void update(User user);

    /**
     * Finds an user by username. Return <code>null</code> if the user doesn't exist.
     *
     * @param username Username of an user
     * @return User which username is equal the parameter.
     */
    User find(String username);

}
