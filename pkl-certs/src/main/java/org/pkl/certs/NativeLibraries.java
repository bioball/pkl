/*
 * Copyright © 2026 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.certs;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.pkl.core.Platform;
import org.pkl.core.util.IoUtils;

public final class NativeLibraries {
  private NativeLibraries() {}

  private static final Path nativeLibsDir = IoUtils.getPklHomeDir().resolve("native-libs");

  public record NativeLibrary(String name, String version) {
    private String systemLibraryName() {
      return System.mapLibraryName(name);
    }

    // keep in sync with `Target.libraryFile` in pkl-certs.gradle.kts
    private URL resourcePath() {
      var platform = Platform.current();
      var path =
          "/NATIVE/org/pkl/certs/%s-%s/%s"
              .formatted(
                  platform.operatingSystem().name().toLowerCase(Locale.ROOT),
                  platform.processor().architecture(),
                  systemLibraryName());
      var resource = NativeLibraries.class.getResource(path);
      assert resource != null;
      return resource;
    }

    private Path storedLibraryPath() {
      return nativeLibsDir.resolve("%s/%s/%s".formatted(name, version, systemLibraryName()));
    }

    public Path libraryPath() {
      var resourcePath = resourcePath();
      if (resourcePath.getProtocol().equals("file")) {
        try {
          return Path.of(resourcePath.toURI());
        } catch (URISyntaxException e) {
          throw new RuntimeException(e);
        }
      }
      var storedLibraryPath = storedLibraryPath();
      if (Files.exists(storedLibraryPath)) {
        return storedLibraryPath;
      }
      try {
        Files.createDirectories(storedLibraryPath.getParent());
        try (var stream = resourcePath.openStream()) {
          Files.copy(stream, storedLibraryPath);
        }
        return storedLibraryPath;
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
  }

  public static NativeLibrary pklCerts = new NativeLibrary("pkl_certs", "0.0.0");
}
