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

import com.google.errorprone.annotations.ThreadSafe;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.ConnectException;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.time.Duration;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import org.pkl.core.util.ErrorMessages;
import org.pkl.core.util.Exceptions;

/** An {@code HttpClient} implementation backed by {@link java.net.http.HttpClient}. */
@ThreadSafe
final class JdkHttpClient implements HttpClient {
  // non-private for testing
  final java.net.http.HttpClient underlying;

  // call java.net.http.HttpClient.close() if available (JDK 21+)
  private static final MethodHandle closeMethod;

  static {
    var methodType = MethodType.methodType(void.class, java.net.http.HttpClient.class);
    MethodHandle result;
    try {
      //noinspection JavaLangInvokeHandleSignature
      result =
          MethodHandles.publicLookup()
              .findVirtual(java.net.http.HttpClient.class, "close", methodType);
    } catch (NoSuchMethodException | IllegalAccessException e) {
      // use no-op close method
      result = MethodHandles.empty(methodType);
    }
    closeMethod = result;
  }

  JdkHttpClient(
      SSLContext sslContext, Duration connectTimeout, java.net.ProxySelector proxySelector) {
    underlying =
        java.net.http.HttpClient.newBuilder()
            .sslContext(sslContext)
            .connectTimeout(connectTimeout)
            .proxy(proxySelector)
            .followRedirects(Redirect.NEVER)
            .build();
  }

  @Override
  public <T> HttpResponse<T> send(
      HttpRequest request,
      BodyHandler<T> responseBodyHandler,
      HttpRequestChecker httpRequestChecker)
      throws IOException {
    try {
      return underlying.send(request, responseBodyHandler);
    } catch (ConnectException e) {
      // original exception has no message
      throw new ConnectException(
          ErrorMessages.create("errorConnectingToHost", request.uri().getHost()));
    } catch (SSLHandshakeException e) {
      throw new SSLHandshakeException(
          ErrorMessages.create(
              "errorSslHandshake", request.uri().getHost(), Exceptions.getRootReason(e)));
    } catch (SSLException e) {
      throw new SSLException(Exceptions.getRootReason(e));
    } catch (InterruptedException e) {
      // next best thing after letting (checked) InterruptedException bubble up
      Thread.currentThread().interrupt();
      throw new IOException(e);
    }
  }

  @Override
  public void close() {
    try {
      closeMethod.invoke(underlying);
    } catch (RuntimeException | Error e) {
      throw e;
    } catch (Throwable t) {
      throw new AssertionError(t);
    }
  }
}
