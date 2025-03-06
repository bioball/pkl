/*
 * Copyright © 2025 Apple Inc. and the Pkl project authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.pkl.gradle;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Properties;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;

public class Dependencies {
  private static final Properties properties;

  static {
    properties = new Properties();
    try (var stream = Dependencies.class.getResourceAsStream("PklPlugin.properties")) {
      if (stream == null) {
        throw new AssertionError("Failed to locate `PklPlugin.properties`");
      }
      properties.load(stream);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static FileCollection getPklTools(Project project) {
    // TODO fixme
    return project.files(
        new File(
            "file:///Users/danielchao/code/apple/pkl/pkl-tools/build/libs/pkl-tools-all-0.29.0-SNAPSHOT.jar"));
    //    var pklToolsPath = (String) properties.get("org.pkl.gradle.pklTools");
    //    // absolute path
    //    if (pklToolsPath.startsWith("file:/")) {
    //      return project.files(new File(pklToolsPath));
    //    }
    ////    return project.getDependencies().create()
    //    throw new RuntimeException("Not implemented yet");
  }
}
