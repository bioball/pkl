/*
 * Copyright © 2024-2026 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.core.http;

import static org.pkl.core.util.IoUtils.validateRewriteRule;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ProxySelector;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.jspecify.annotations.Nullable;
import org.pkl.core.Release;
import org.pkl.core.http.CertificateLoader.ByteArrayCertificateLoader;
import org.pkl.core.http.CertificateLoader.PathCertificateLoader;
import org.pkl.core.http.HttpClient.Builder;
import org.pkl.core.util.ErrorMessages;
import org.pkl.core.util.Exceptions;
import org.pkl.core.util.GlobResolver;
import org.pkl.core.util.GlobResolver.InvalidGlobPatternException;
import org.pkl.core.util.IoUtils;

final class HttpClientBuilder implements HttpClient.Builder {
  private String userAgent;
  private Duration connectTimeout = Duration.ofSeconds(60);
  private Duration requestTimeout = Duration.ofSeconds(60);
  private final List<Path> certificateFiles = new ArrayList<>();
  private final List<ByteBuffer> certificateBytes = new ArrayList<>();
  private int testPort = -1;
  private @Nullable ProxySelector proxySelector;
  private Map<URI, URI> rewrites = new HashMap<>();
  // okay to use Pattern as a map key here because `GlobResolver.toRegexPattern()` caches and
  // gives the same `Pattern` instance for an existing glob pattern.
  // use LinkedHashMap to preserve insertion order.
  private Map<Pattern, Map<String, List<String>>> headers = new LinkedHashMap<>();
  private @Nullable SSLContext sslContext;

  HttpClientBuilder() {
    var release = Release.current();
    this.userAgent =
        "Pkl/" + release.version() + " (" + release.os() + "; " + release.flavor() + ")";
  }

  public HttpClient.Builder setUserAgent(String userAgent) {
    this.userAgent = userAgent;
    return this;
  }

  @Override
  public HttpClient.Builder setConnectTimeout(Duration timeout) {
    this.connectTimeout = timeout;
    return this;
  }

  @Override
  public HttpClient.Builder setRequestTimeout(Duration timeout) {
    this.requestTimeout = timeout;
    return this;
  }

  @Override
  public HttpClient.Builder addCertificates(Path path) {
    certificateFiles.add(path);
    return this;
  }

  @Override
  public Builder addCertificates(byte[] certificateBytes) {
    this.certificateBytes.add(ByteBuffer.wrap(certificateBytes));
    return this;
  }

  @Override
  public Builder addCertificatesFromServiceProviders() {
    var spi = IoUtils.createServiceLoader(CertificateLoader.class);
    for (var loader : spi) {
      if (!loader.isEnabled()) {
        continue;
      }
      if (loader instanceof ByteArrayCertificateLoader bacl) {
        addCertificates(bacl.getBytes());
      } else if (loader instanceof PathCertificateLoader pcl) {
        var paths = pcl.getPaths();
        for (var path : paths) {
          addCertificates(path);
        }
      }
    }
    return this;
  }

  @Override
  public HttpClient.Builder setTestPort(int port) {
    testPort = port;
    return this;
  }

  public HttpClient.Builder setProxySelector(ProxySelector proxySelector) {
    this.proxySelector = proxySelector;
    return this;
  }

  @Override
  public Builder setProxy(@Nullable URI proxyAddress, List<String> noProxy) {
    this.proxySelector = new org.pkl.core.http.ProxySelector(proxyAddress, noProxy);
    return this;
  }

  @Override
  public Builder setRewrites(Map<URI, URI> rewrites) {
    for (var entry : rewrites.entrySet()) {
      validateRewriteRule(entry.getKey());
      validateRewriteRule(entry.getValue());
    }
    this.rewrites = new HashMap<>(rewrites);
    return this;
  }

  @Override
  public Builder addRewrite(URI sourcePrefix, URI targetPrefix) {
    validateRewriteRule(sourcePrefix);
    validateRewriteRule(targetPrefix);
    this.rewrites.put(sourcePrefix, targetPrefix);
    return this;
  }

  @Override
  public Builder setHeaders(Map<String, Map<String, List<String>>> headers) {
    var newHeaders = new LinkedHashMap<Pattern, Map<String, List<String>>>(headers.size());
    for (var rule : headers.entrySet()) {
      Pattern pattern;
      try {
        pattern = GlobResolver.toRegexPattern(rule.getKey());
      } catch (InvalidGlobPatternException e) {
        throw new IllegalArgumentException(e.getMessage(), e);
      }
      var map = new LinkedHashMap<String, List<String>>();
      for (var entry : rule.getValue().entrySet()) {
        IoUtils.validateHeaderName(entry.getKey());
        for (var value : entry.getValue()) {
          IoUtils.validateHeaderValue(value);
        }
        map.put(entry.getKey(), new ArrayList<>(entry.getValue()));
      }
      newHeaders.put(pattern, map);
    }
    this.headers = newHeaders;
    return this;
  }

  @Override
  public Builder addHeaders(String globPattern, Map<String, List<String>> headers) {
    try {
      var pattern = GlobResolver.toRegexPattern(globPattern);
      var existingHeaders = this.headers.computeIfAbsent(pattern, k -> new HashMap<>());
      for (var entry : headers.entrySet()) {
        var headerName = entry.getKey();
        var headerValues = entry.getValue();

        IoUtils.validateHeaderName(headerName);
        for (var value : headerValues) {
          IoUtils.validateHeaderValue(value);
        }

        var existingList = existingHeaders.putIfAbsent(headerName, new ArrayList<>(headerValues));
        if (existingList != null) {
          existingList.addAll(headerValues);
        }
      }
      return this;
    } catch (InvalidGlobPatternException e) {
      throw new IllegalArgumentException(e.getMessage(), e);
    }
  }

  @Override
  public Builder setSslContext(SSLContext sslContext) {
    this.sslContext = sslContext;
    return this;
  }

  @Override
  public HttpClient build() {
    return doBuild().get();
  }

  @Override
  public HttpClient buildLazily() {
    return new LazyHttpClient(doBuild());
  }

  private static List<Certificate> gatherCertificates(
      CertificateFactory factory, List<Path> certificateFiles, List<ByteBuffer> certificateBytes) {
    var certificates = new ArrayList<Certificate>();
    for (var file : certificateFiles) {
      try (var stream = Files.newInputStream(file)) {
        collectCertificates(certificates, factory, stream, file);
      } catch (NoSuchFileException e) {
        throw new HttpClientException(ErrorMessages.create("cannotFindCertFile", file));
      } catch (IOException e) {
        throw new HttpClientException(
            ErrorMessages.create("cannotReadCertFile", Exceptions.getRootReason(e)));
      }
    }
    for (var byteBuffer : certificateBytes) {
      var stream = new ByteArrayInputStream(byteBuffer.array());
      collectCertificates(certificates, factory, stream, "<unavailable>");
    }
    return certificates;
  }

  private static void collectCertificates(
      ArrayList<Certificate> anchors,
      CertificateFactory factory,
      InputStream stream,
      Object source) {
    Collection<X509Certificate> certificates;
    try {
      //noinspection unchecked
      certificates = (Collection<X509Certificate>) factory.generateCertificates(stream);
    } catch (CertificateException e) {
      throw new HttpClientException(
          ErrorMessages.create("cannotParseCertFile", source, Exceptions.getRootReason(e)));
    }
    if (certificates.isEmpty()) {
      throw new HttpClientException(ErrorMessages.create("emptyCertFile", source));
    }
    anchors.addAll(certificates);
  }

  // https://docs.oracle.com/en/java/javase/11/docs/specs/security/standard-names.html#security-algorithm-implementation-requirements
  private static SSLContext createSslContext(
      List<Path> certificateFiles, List<ByteBuffer> certificateBytes) {
    try {
      if (certificateFiles.isEmpty() && certificateBytes.isEmpty()) {
        // use JVM's built-in CA certificates
        return SSLContext.getDefault();
      }

      var certFactory = CertificateFactory.getInstance("X.509");
      List<Certificate> certs = gatherCertificates(certFactory, certificateFiles, certificateBytes);
      var keystore = KeyStore.getInstance(KeyStore.getDefaultType());
      keystore.load(null);
      for (var i = 0; i < certs.size(); i++) {
        keystore.setCertificateEntry("Certificate" + i, certs.get(i));
      }
      var trustManagerFactory = TrustManagerFactory.getInstance("PKIX");
      trustManagerFactory.init(keystore);

      var sslContext = SSLContext.getInstance("TLS");
      sslContext.init(null, trustManagerFactory.getTrustManagers(), new SecureRandom());

      return sslContext;
    } catch (GeneralSecurityException | IOException e) {
      throw new HttpClientException(
          ErrorMessages.create("cannotInitHttpClient", Exceptions.getRootReason(e)), e);
    }
  }

  private Supplier<HttpClient> doBuild() {
    // make defensive copy because Supplier may get called after builder was mutated
    var certificateFiles = List.copyOf(this.certificateFiles);
    var proxySelector =
        this.proxySelector != null ? this.proxySelector : java.net.ProxySelector.getDefault();
    return () -> {
      var sslContext =
          this.sslContext == null
              ? createSslContext(certificateFiles, certificateBytes)
              : this.sslContext;
      var jdkClient = new JdkHttpClient(sslContext, connectTimeout, proxySelector);
      return new RequestRewritingClient(
          userAgent, requestTimeout, testPort, jdkClient, rewrites, headers);
    };
  }
}
