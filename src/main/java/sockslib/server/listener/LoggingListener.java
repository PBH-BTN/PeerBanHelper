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

package sockslib.server.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sockslib.common.AddressType;
import sockslib.server.Session;
import sockslib.server.msg.CommandMessage;

/**
 * The class <code>LoggingListener</code> is a logger.
 *
 * @author Youchao Feng
 * @version 1.0
 * @date Nov 04, 2015 4:24 PM
 */
public class LoggingListener implements SessionListener {

    private static final Logger logger = LoggerFactory.getLogger(LoggingListener.class);

    @Override
    public void onCreate(Session session) throws CloseSessionException {
        logger.info("Create SESSION[{}] for {}", session.getId(), session.getClientAddress());
    }

    @Override
    public void onCommand(Session session, CommandMessage message) throws CloseSessionException {
        logger.info("SESSION[{}] request:{}  {}:{}", session.getId(), message.getCommand(),
                message.getAddressType() != AddressType.DOMAIN_NAME ?
                        message.getInetAddress() :
                        message.getHost(), message.getPort());
    }

    @Override
    public void onClose(Session session) {
        logger.info("SESSION[{}] closed", session.getId());
    }

    @Override
    public void onException(Session session, Exception exception) {
        logger.error("SESSION[{}] occurred error:{}, message:{}", session.getId(), exception.getClass
                ().getSimpleName(), exception.getMessage());
        exception.printStackTrace();
    }
}
