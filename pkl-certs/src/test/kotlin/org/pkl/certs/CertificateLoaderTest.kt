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
package org.pkl.certs

import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.pkl.core.http.HttpClient

/**
 * Simple smoke test around cert loading; it's hard to test anything beyond this because we'd have
 * to mutate OS settings.
 */
class CertificateLoaderTest {
  @Test
  @EnabledOnOs(OS.MAC, OS.WINDOWS)
  fun `NativeCertificateLoader configures HTTPS certs`() {
    val httpClient =
      with(HttpClient.builder()) {
        addCertificates(NativeCertificateLoader().bytes)
        build()
      }
    val request =
      with(HttpRequest.newBuilder(URI("https://pkl-lang.org"))) {
        method("HEAD", HttpRequest.BodyPublishers.noBody())
        build()
      }
    val response = httpClient.send(request, HttpResponse.BodyHandlers.discarding()) {}
    assertThat(response.statusCode()).isEqualTo(200)
  }

  @Test
  @EnabledOnOs(OS.LINUX)
  fun `LinuxCertificateloader configures HTTPS certs`() {
    val httpClient =
      with(HttpClient.builder()) {
        for (path in LinuxCertificateLoader().paths) {
          addCertificates(path)
        }
        build()
      }
    val request =
      with(HttpRequest.newBuilder(URI("https://pkl-lang.org"))) {
        method("HEAD", HttpRequest.BodyPublishers.noBody())
        build()
      }
    val response = httpClient.send(request, HttpResponse.BodyHandlers.discarding()) {}
    assertThat(response.statusCode()).isEqualTo(200)
  }
}
