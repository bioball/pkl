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
package org.pkl.core.runtime;

public interface VmValueConverter<T> {
  Object WILDCARD_PROPERTY =
      new Object() {
        @Override
        public String toString() {
          return "WILDCARD_PROPERTY";
        }
      };

  Object WILDCARD_ELEMENT =
      new Object() {
        @Override
        public String toString() {
          return "WILDCARD_ELEMENT";
        }
      };

  Object TOP_LEVEL_VALUE =
      new Object() {
        @Override
        public String toString() {
          return "TOP_LEVEL_VALUE";
        }
      };

  T convertString(String value, Iterable<Object> path);

  T convertBoolean(Boolean value, Iterable<Object> path);

  T convertInt(Long value, Iterable<Object> path);

  T convertFloat(Double value, Iterable<Object> path);

  T convertVmValue(VmValue value, Iterable<Object> path);

  default T convert(Object value, Iterable<Object> path) {
    if (value instanceof VmValue) {
      return convertVmValue((VmValue) value, path);
    }
    if (value instanceof String) {
      return convertString((String) value, path);
    }
    if (value instanceof Boolean) {
      return convertBoolean((Boolean) value, path);
    }
    if (value instanceof Long) {
      return convertInt((Long) value, path);
    }
    if (value instanceof Double) {
      return convertFloat((Double) value, path);
    }

    throw new IllegalArgumentException("Cannot convert VM value with unexpected type: " + value);
  }
}
