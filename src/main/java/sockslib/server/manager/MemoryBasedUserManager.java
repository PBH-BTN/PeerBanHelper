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

import com.google.common.base.Strings;
import com.google.common.collect.Lists;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The class <code>MemoryBasedUserManager</code> represents a user manager that manage users in
 * memory.
 *
 * @author Youchao Feng
 * @version 1.0
 * @date Apr 16, 2015 3:00:27 PM
 */
public class MemoryBasedUserManager implements UserManager {

    /**
     * All users.
     */
    private Map<String, User> users = new HashMap<>();

    @Override
    public void create(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User can't be null");
        }
        if (Strings.isNullOrEmpty(user.getPassword())) {
            throw new IllegalArgumentException("username can't be null or empty");
        }
        users.put(user.getUsername(), user);
    }

    @Override
    public UserManager addUser(String username, String password) {
        if (Strings.isNullOrEmpty(username)) {
            throw new IllegalArgumentException("Username can't be null");
        }
        users.put(username, new User(username, password));
        return this;
    }

    @Override
    public User check(String username, String password) {
        User user = find(username);
        if (user != null && user.getPassword() != null && user.getPassword().equals(password)) {
            return user;
        }
        return null;
    }

    @Override
    public void delete(String username) {
        users.remove(username);
    }

    @Override
    public List<User> findAll() {
        return Lists.newArrayList(users.values());
    }

    @Override
    public void update(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User can't null");
        }
        if (Strings.isNullOrEmpty(user.getUsername())) {
            throw new IllegalArgumentException("Username of the user can't be null or empty");
        }
        users.put(user.getUsername(), user);
    }

    @Override
    public User find(String username) {
        if (Strings.isNullOrEmpty(username)) {
            throw new IllegalArgumentException("Username can't be null or empty");
        }
        return users.get(username);
    }

}
