/**
 * Copyright © 2024 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.core.stdlib;

import java.util.*;
import org.pkl.core.runtime.*;
import org.pkl.core.util.Nullable;
import org.pkl.core.util.Pair;

public final class PklConverter implements VmValueConverter<Object> {
  private final Map<VmClass, VmFunction> typeConverters;
  private final Pair<Object[], VmFunction>[] pathConverters;

  public PklConverter(VmMapping converters) {
    // `converters` is technically already forced beceause of https://github.com/apple/pkl/issues/406,
    // but let's not rely on this implementation detail.
    converters.force(false, false);
    typeConverters = createTypeConverters(converters);
    pathConverters = createPathConverters(converters);
  }

  @Override
  public Object convertString(String value, Iterable<Object> path) {
    return doConvert(value, path, findTypeConverter(BaseModule.getStringClass()));
  }

  @Override
  public Object convertBoolean(Boolean value, Iterable<Object> path) {
    return doConvert(value, path, findTypeConverter(BaseModule.getBooleanClass()));
  }

  @Override
  public Object convertInt(Long value, Iterable<Object> path) {
    return doConvert(value, path, findTypeConverter(BaseModule.getIntClass()));
  }

  @Override
  public Object convertFloat(Double value, Iterable<Object> path) {
    return doConvert(value, path, findTypeConverter(BaseModule.getFloatClass()));
  }

  @Override
  public Object convertVmValue(VmValue value, Iterable<Object> path) {
    return doConvert(value, path, findTypeConverter(value.getVmClass()));
  }

  private Map<VmClass, VmFunction> createTypeConverters(VmMapping converters) {
    var result = new HashMap<VmClass, VmFunction>();
    converters.iterateMemberValues(
        (key, member, value) -> {
          assert value != null; // forced in ctor
          if (key instanceof VmClass) {
            result.put((VmClass) key, (VmFunction) value);
          }
          return true;
        });
    return result;
  }

  @SuppressWarnings("unchecked")
  private Pair<Object[], VmFunction>[] createPathConverters(VmMapping converters) {
    var result = new ArrayList<Pair<Object[], VmFunction>>();
    var parser = new PathSpecParser();
    converters.iterateMemberValues(
        (key, member, value) -> {
          assert value != null; // forced in ctor
          if (key instanceof String) {
            result.add(Pair.of(parser.parse((String) key), (VmFunction) value));
          }
          return true;
        });
    return result.toArray(new Pair[0]);
  }

  private @Nullable VmFunction getPathConverter(Iterable<Object> path) {
    for (var converter : pathConverters) {
      if (PathConverterSupport.pathMatches(Arrays.asList(converter.first), path)) {
        return converter.second;
      }
    }
    return null;
  }

  /**
   * Finds a type converter for the given class (if one exists).
   *
   * <p>Type converters are covariant, so an Animal converter will accept a Dog or a Cat. This
   * method will return the most specific converter for a type.
   */
  private @Nullable VmFunction findTypeConverter(VmClass clazz) {
    for (var current = clazz; current != null; current = current.getSuperclass()) {
      var found = typeConverters.get(current);
      if (found != null) return found;
    }
    return null;
  }

  private Object doConvert(
      Object value, Iterable<Object> path, @Nullable VmFunction typeConverter) {
    var pathConverter = getPathConverter(path);
    if (pathConverter != null) {
      // path converter wins over type converter
      return pathConverter.apply(value);
    }

    if (typeConverter != null) {
      return typeConverter.apply(value);
    }

    return value;
  }
}
