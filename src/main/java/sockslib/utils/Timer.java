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
 * @date Sep 30, 2015 10:31 AM
 */
public class Timer {

    private static final Logger logger = LoggerFactory.getLogger(Timer.class);

    private long startTime;
    private long endTime;

    private Timer(long startTime) {
        this.startTime = startTime;
    }

    public static Timer start() {
        return new Timer(System.currentTimeMillis());
    }

    public static void open() {
        Runtime.getRuntime().addShutdownHook(new TimerThread(start()));
    }

    public long stop() {
        endTime = System.currentTimeMillis();
        return endTime - startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public long getStartTime() {
        return startTime;
    }

    public long getTotalTime() {
        return endTime - startTime;
    }


}


