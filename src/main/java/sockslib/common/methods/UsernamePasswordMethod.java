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

package sockslib.common.methods;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sockslib.client.Socks5;
import sockslib.client.SocksProxy;
import sockslib.common.AuthenticationException;
import sockslib.common.Credentials;
import sockslib.common.SocksException;
import sockslib.common.UsernamePasswordCredentials;
import sockslib.server.Session;
import sockslib.server.UsernamePasswordAuthenticator;
import sockslib.server.msg.UsernamePasswordMessage;
import sockslib.server.msg.UsernamePasswordResponseMessage;
import sockslib.utils.LogMessageBuilder;
import sockslib.utils.LogMessageBuilder.MsgType;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import static com.google.common.base.Preconditions.checkNotNull;


/**
 * The class <code>UsernamePasswordMethod</code> represents the method that need USERNAME/PASSWORD
 * authentication.<br>
 * <b>Notice:</b> This method is only supported by SOCKS5 protocol. It will be used in client and
 * server.
 *
 * @author Youchao Feng
 * @version 1.0
 * @date Mar 17, 2015 5:09:23 PM
 * @see <a href="http://www.ietf.org/rfc/rfc1928.txt">SOCKS Protocol Version 5</a>
 */
public class UsernamePasswordMethod extends AbstractSocksMethod {

    /**
     * Logger.
     */
    private static final Logger logger = LoggerFactory.getLogger(UsernamePasswordMethod.class);

    /**
     * USERNAME/PASSWORD authenticator.
     */
    private UsernamePasswordAuthenticator authenticator = new UsernamePasswordAuthenticator();

    /**
     * Constructs an instance of {@link UsernamePasswordMethod}.
     */
    public UsernamePasswordMethod() {
    }

    /**
     * Constructs an instance of {@link UsernamePasswordMethod} with
     * {@link UsernamePasswordAuthenticator}.
     *
     * @param authenticator Authenticator.
     */
    public UsernamePasswordMethod(UsernamePasswordAuthenticator authenticator) {
        this.authenticator = authenticator;
    }

    @Override
    public final int getByte() {
        return 0x02;
    }

    /**
     * Do authentication.
     */
    @Override
    public void doMethod(SocksProxy socksProxy) throws SocksException, IOException {
        checkNotNull(socksProxy, "Argument [socksProxy] may not be null");
        Credentials credentials = socksProxy.getCredentials();
        if (credentials == null || !(credentials instanceof UsernamePasswordCredentials)) {
            throw new SocksException("Need Username/Password authentication");
        }
        // UsernamePasswordAuthentication authentication = (UsernamePasswordAuthentication) auth;

        String username = credentials.getUserPrincipal().getName();
        String password = credentials.getPassword();
        InputStream inputStream = socksProxy.getInputStream();
        OutputStream outputStream = socksProxy.getOutputStream();
        /*
         * RFC 1929
         *
         * +----+------+----------+------+----------+
         * |VER | ULEN | UNAME | PLEN | PASSWD | |
         * +----+------+----------+------+----------+ | 1 | 1 | 1 to 255 | 1 | 1 to 255 |
         * +----+------+----------+------+----------+ The VER field contains the current version of the
         * subnegotiation, which is X’01’. The ULEN field contains the length of the UNAME field that
         * follows. The UNAME field contains the username as known to the source operating system. The
         * PLEN field contains the length of the PASSWD field that follows. The PASSWD field contains
         * the password association with the given UNAME.
         */
        final int USERNAME_LENGTH = username.getBytes().length;
        final int PASSWORD_LENGTH = password.getBytes().length;
        final byte[] bytesOfUsername = username.getBytes();
        final byte[] bytesOfPassword = password.getBytes();
        final byte[] bufferSent = new byte[3 + USERNAME_LENGTH + PASSWORD_LENGTH];

        bufferSent[0] = 0x01; // VER
        bufferSent[1] = (byte) USERNAME_LENGTH; // ULEN
        System.arraycopy(bytesOfUsername, 0, bufferSent, 2, USERNAME_LENGTH);// UNAME
        bufferSent[2 + USERNAME_LENGTH] = (byte) PASSWORD_LENGTH; // PLEN
        System.arraycopy(bytesOfPassword, 0, bufferSent, 3 + USERNAME_LENGTH, // PASSWD
                PASSWORD_LENGTH);
        outputStream.write(bufferSent);
        outputStream.flush();
        // logger send bytes
        logger.debug("{}", LogMessageBuilder.build(bufferSent, MsgType.SEND));

        byte[] authenticationResult = new byte[2];
        inputStream.read(authenticationResult);
        // logger
        logger.debug("{}", LogMessageBuilder.build(authenticationResult, MsgType.RECEIVE));

        if (authenticationResult[1] != Socks5.AUTHENTICATION_SUCCEEDED) {
            // Close connection if authentication is failed.
            outputStream.close();
            inputStream.close();
            socksProxy.getProxySocket().close();
            throw new AuthenticationException("Username or password error");
        }
    }

    @Override
    public void doMethod(Session session) throws SocksException, IOException {
        checkNotNull(session, "Argument [session] may not be null");
        checkNotNull(authenticator, "Please set an authenticator");
        UsernamePasswordMessage usernamePasswordMessage = new UsernamePasswordMessage();
        session.read(usernamePasswordMessage);
        logger.debug("SESSION[{}] Receive credentials: {}", session.getId(), usernamePasswordMessage
                .getUsernamePasswordCredentials());
        try {
            authenticator.doAuthenticate(usernamePasswordMessage.getUsernamePasswordCredentials(),
                    session);
        } catch (AuthenticationException e) {
            session.write(new UsernamePasswordResponseMessage(false));
            throw e;
        }
        session.write(new UsernamePasswordResponseMessage(true));
    }

    @Override
    public String getMethodName() {
        return "USERNAME/PASSWORD authentication";
    }

    public UsernamePasswordAuthenticator getAuthenticator() {
        return authenticator;
    }

    public void setAuthenticator(UsernamePasswordAuthenticator authenticator) {
        this.authenticator = authenticator;
    }

}
