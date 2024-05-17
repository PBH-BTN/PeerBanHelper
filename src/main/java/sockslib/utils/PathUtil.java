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

package sockslib.utils;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.URL;

/**
 * The class <code>PathUtil</code> represents a path utility.
 *
 * @author Youchao Feng
 * @version 1.0
 * @since 1.0
 */
public class PathUtil {

    /**
     * Returns abstract path.
     *
     * @param path the path that can start with "classpath:" or "file:".
     * @return abstract path.
     * @throws FileNotFoundException if the file not found.
     */
    public static String getAbstractPath(String path) throws FileNotFoundException {
        if (path != null && path.startsWith("classpath:")) {
            String classPathValue = path.split(":")[1];
            if (!classPathValue.startsWith(File.separator)) {
                classPathValue = File.separator + classPathValue;
            }

            URL url = PathUtil.class.getResource(classPathValue);
            if (url == null) {
                throw new FileNotFoundException(path);
            }
            return url.getPath();
        } else if (path != null && path.startsWith("file:")) {
            if (path.length() > 6) {
                return path.substring(5);
            }
        }
        return path;
    }

}
