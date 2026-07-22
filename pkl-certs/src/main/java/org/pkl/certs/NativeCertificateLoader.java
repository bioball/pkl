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

import org.pkl.core.Platform;
import org.pkl.core.http.CertificateLoader.ByteArrayCertificateLoader;

public class NativeCertificateLoader implements ByteArrayCertificateLoader {
  static boolean isAvailable() {
    var osName = Platform.current().operatingSystem().name();
    return osName.equals("macOS") || osName.equals("Windows");
  }

  static {
    if (isAvailable()) {
      System.load(NativeLibraries.pklCerts.libraryPath().toAbsolutePath().toString());
    }
  }

  @Override
  public boolean isEnabled() {
    return isAvailable();
  }

  public native byte[] getBytes();
}
