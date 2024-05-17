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

/**
 * The class <code>UsernamePasswordResponseMessage</code> represents a response message for
 * USERNAME/PASSWORD authentication.
 *
 * @author Youchao Feng
 * @version 1.0
 * @date Apr 24, 2015 10:31:02 PM
 */
public class UsernamePasswordResponseMessage implements WritableMessage {

    /**
     * Version. it should be 0x01 in SOCKS5 protocol.
     */
    private final int VERSION = 0x01;

    /**
     * 0 represents SUCCESS.
     */
    private int status;

    /**
     * Constructs an instance of {@link UsernamePasswordResponseMessage}.
     *
     * @param success If username and password is matched, it will return <code>true</code>.
     */
    public UsernamePasswordResponseMessage(boolean success) {
        status = success ? 0x00 : 0x01;
    }

    @Override
    public byte[] getBytes() {
        byte[] bytes = new byte[2];
        bytes[0] = (byte) VERSION;
        bytes[1] = (byte) status;
        return bytes;
    }

    @Override
    public int getLength() {
        return getBytes().length;
    }

    /**
     * Returns status.
     *
     * @return Status.
     */
    public int getStatus() {
        return status;
    }

    /**
     * Sets status.
     *
     * @param status Status.
     */
    public void setStatus(int status) {
        this.status = status;
    }

    /**
     * Returns version.
     *
     * @return version.
     */
    public int getVersion() {
        return VERSION;
    }

}
