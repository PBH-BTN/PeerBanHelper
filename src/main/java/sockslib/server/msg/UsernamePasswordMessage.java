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

package sockslib.server.msg;

import sockslib.common.SocksException;
import sockslib.common.UsernamePasswordCredentials;

import java.io.IOException;
import java.io.InputStream;

import static sockslib.utils.StreamUtil.checkEnd;
import static sockslib.utils.StreamUtil.readString;


/**
 * The class <code>UsernamePasswordMessage</code> represents a USERNAME/PASSWROD authentication
 * message.
 *
 * @author Youchao Feng
 * @version 1.0
 * @date Apr 16, 2015 11:41:28 AM
 */
public class UsernamePasswordMessage implements ReadableMessage, WritableMessage {

    /**
     * Username password authentication.
     */
    private UsernamePasswordCredentials credentials;

    /**
     * Version.
     */
    private int version = 0x01;

    /**
     * Length of username.
     */
    private int usernameLength;

    /**
     * Length of password.
     */
    private int passwordLength;

    /**
     * Username.
     */
    private String username;

    /**
     * Password.
     */
    private String password;

    /**
     * Constructs an instance of {@link UsernamePasswordMessage}.
     */
    public UsernamePasswordMessage() {

    }

    public UsernamePasswordMessage(String username, String password) {
        this.username = username;
        this.password = password;
        usernameLength = username.getBytes().length;
        passwordLength = password.getBytes().length;
    }

    @Override
    public byte[] getBytes() {

        final int SIZE = 3 + usernameLength + passwordLength;

        byte[] bytes = new byte[SIZE];

        bytes[0] = (byte) version;
        bytes[1] = (byte) usernameLength;
        for (int i = 0; i < usernameLength; i++) {
            bytes[2 + i] = username.getBytes()[i];
        }

        bytes[2 + usernameLength] = (byte) passwordLength;

        for (int i = 0; i < passwordLength; i++) {
            bytes[3 + usernameLength + i] = password.getBytes()[i];
        }

        return bytes;
    }

    @Override
    public int getLength() {
        return getBytes().length;
    }

    @Override
    public void read(InputStream inputStream) throws SocksException, IOException {
        version = checkEnd(inputStream.read());
        usernameLength = checkEnd(inputStream.read());
        username = readString(inputStream, usernameLength);
        passwordLength = checkEnd(inputStream.read());
        password = readString(inputStream, passwordLength);
        credentials = new UsernamePasswordCredentials(username, password);
    }

    /**
     * Returns version.
     *
     * @return Version.
     */
    public int getVersion() {
        return version;
    }

    /**
     * Returns username.
     *
     * @return username.
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

    /**
     * Returns an instance of {@link UsernamePasswordCredentials}.
     *
     * @return An instance of {@link UsernamePasswordCredentials} .
     */
    public UsernamePasswordCredentials getUsernamePasswordCredentials() {
        return credentials;
    }

}
