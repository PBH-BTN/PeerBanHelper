/*
 * Copyright 2015-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package sockslib.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Youchao Feng
 * @version 1.0
 * @date Sep 30, 2015 11:14 AM
 */
public class TimerThread extends Thread {

    private static final Logger logger = LoggerFactory.getLogger(TimerThread.class);
    private Timer timer;

    public TimerThread(Timer timer) {
        this.timer = timer;
    }

    @Override
    public void run() {
        logger.info("Total Run Time: {}ms", timer.stop());
    }
}
