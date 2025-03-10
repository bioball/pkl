/*
 * Copyright © 2024-2025 Apple Inc. and the Pkl project authors. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import java.util.Iterator;
import java.util.Objects;
import org.organicdesign.fp.collections.RrbTree;
import org.pkl.core.ast.ConstantNode;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.util.Nullable;

// currently the backing collection is realized at the end of each VmList operation
// this trades efficiency for ease of understanding, as it eliminates the complexity
// of users having to deal with deferred operations that only fail later
// perhaps we could find a compromise, e.g. realize every time a
// property/local is read or a method parameter is set in our language
public abstract class VmList extends VmCollection {
  @TruffleBoundary
  public static VmList of(Object value) {
    return new VmGenericList(RrbTree.emptyMutable().append(value).immutable());
  }

  @TruffleBoundary
  public static VmList of(Object value1, Object value2) {
    return new VmGenericList(RrbTree.emptyMutable().append(value1).append(value2).immutable());
  }

  @TruffleBoundary
  public static VmList create(byte[] elements) {
    return new VmByteArrayList(elements);
  }

  @Override
  public final VmClass getVmClass() {
    return BaseModule.getListClass();
  }

  @Override
  public void accept(VmValueVisitor visitor) {
    visitor.visitList(this);
  }

  @Override
  public <T> T accept(VmValueConverter<T> converter, Iterable<Object> path) {
    return converter.convertList(this, path);
  }

  public abstract VmList add(Object element);

  public abstract VmList replace(long index, Object element);

  public abstract Object replaceOrNull(long index, Object element);

  public abstract Object get(long index);

  public final Object getOrNull(long index) {
    if (index < 0 || index >= getLength()) {
      return VmNull.withoutDefault();
    }
    return get(index);
  }

  public abstract VmList subList(long start, long exclusiveEnd);

  public final Object subListOrNull(long start, long exclusiveEnd) {
    var length = getLength();

    if (start < 0 || start > length) {
      return VmNull.withoutDefault();
    }
    if (exclusiveEnd < start || exclusiveEnd > length) {
      return VmNull.withoutDefault();
    }
    return subList(start, exclusiveEnd);
  }

  @Override
  public final Builder<VmList> builder() {
    return new VmListBuilder();
  }

  public final Object getFirst() {
    checkNonEmpty();
    return get(0);
  }

  public final Object getFirstOrNull() {
    if (isEmpty()) return VmNull.withoutDefault();
    return get(0);
  }

  public abstract VmList getRest();

  public final Object getRestOrNull() {
    if (isEmpty()) return VmNull.withoutDefault();
    return getRest();
  }

  public abstract Object getLast();

  public final Object getLastOrNull() {
    if (isEmpty()) return VmNull.withoutDefault();
    return getLast();
  }

  public final Object getSingle() {
    checkLengthOne();
    return get(0);
  }

  public final Object getSingleOrNull() {
    if (!isLengthOne()) return VmNull.withoutDefault();
    return get(0);
  }

  public abstract boolean contains(Object element);

  public abstract long indexOf(Object elem);
  
  public final Object indexOfOrNull(Object elem) {
    var index = indexOf(elem);
    if (index == -1) {
      return VmNull.withoutDefault();
    }
    return index;
  }

  public abstract long lastIndexOf(Object elem);

  public final Object lastIndexOfOrNull(Object elem) {
    var index = lastIndexOf(elem);
    if (index == -1) {
      return VmNull.withoutDefault();
    }
    return index;
  }

  public abstract VmPair split(long index);

  public final Object splitOrNull(long index) {
    if (index < 0 || index > getLength()) {
      return VmNull.withoutDefault();
    }
    return split(index);
  }

  public abstract VmList take(long n);

  public abstract VmList takeLast(long n);

  public abstract VmList drop(long n);

  public abstract VmList dropLast(long n);

  public abstract VmList repeat(long n);

  public abstract VmList reverse();

  public abstract Object[] toArray();

  public final VmList toList() {
    return this;
  }

  public abstract VmSet toSet();

  public abstract VmListing toListing();

  public abstract VmDynamic toDynamic();

  protected boolean equalsList(VmList other) {
    if (getLength() != other.getLength()) {
      return false;
    }
    for (var i = 0; i < getLength(); i++) {
      if (!(Objects.equals(get(i), other.get(i)))) {
        return false;
      }
    }
    return true;
  }
}
