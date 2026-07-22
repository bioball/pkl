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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.pkl.core.Platform;
import org.pkl.core.http.CertificateLoader.PathCertificateLoader;

public final class LinuxCertificateLoader implements PathCertificateLoader {
  // taken from https://go.dev/src/crypto/x509/root_linux.go
  private static final String[] LINUX_CERT_PATHS = {
    "/etc/ssl/certs/ca-certificates.crt", // Debian/Ubuntu/Gentoo etc.
    "/etc/pki/tls/certs/ca-bundle.crt", // Fedora/RHEL 6
    "/etc/ssl/ca-bundle.pem", // OpenSUSE
    "/etc/pki/tls/cacert.pem", // OpenELEC
    "/etc/pki/ca-trust/extracted/pem/tls-ca-bundle.pem", // CentOS/RHEL 7
    "/etc/ssl/cert.pem", // Alpine Linux
  };

  private static final String[] LINUX_CERT_DIRS = {
    "/etc/ssl/certs", // SLES10/SLES11
    "/etc/pki/tls/certs", // Fedora/RHEL
  };

  @Override
  public boolean isEnabled() {
    var osName = Platform.current().operatingSystem().name();
    return osName.equals("Linux");
  }

  @Override
  public List<Path> getPaths() {
    try {
      var ret = new ArrayList<Path>();
      for (var candidate : LINUX_CERT_PATHS) {
        var path = Path.of(candidate);
        if (Files.exists(path)) {
          ret.add(path);
          break;
        }
      }
      for (var candidateDir : LINUX_CERT_DIRS) {
        var dir = Path.of(candidateDir);
        if (Files.exists(dir)) {
          try (var files = Files.list(dir)) {
            ret.addAll(files.toList());
          }
          break;
        }
      }
      if (ret.isEmpty()) {
        throw new IllegalStateException("Did not find any certificates in the certification path");
      }
      return ret;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
