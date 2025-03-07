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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.organicdesign.fp.collections.RrbTree;
import org.organicdesign.fp.collections.RrbTree.ImRrbt;
import org.organicdesign.fp.collections.RrbTree.MutRrbt;
import org.organicdesign.fp.collections.UnmodCollection;
import org.organicdesign.fp.collections.UnmodIterable;
import org.pkl.core.ast.ConstantNode;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.runtime.Iterators.ReverseTruffleIterator;
import org.pkl.core.runtime.Iterators.TruffleIterator;
import org.pkl.core.util.Nullable;

// currently the backing collection is realized at the end of each VmList operation
// this trades efficiency for ease of understanding, as it eliminates the complexity
// of users having to deal with deferred operations that only fail later
// perhaps we could find a compromise, e.g. realize every time a
// property/local is read or a method parameter is set in our language
public abstract class VmList extends VmCollection {
  public static final VmList EMPTY = new VmGenericList(RrbTree.empty());

  @TruffleBoundary
  public static VmList of(Object value) {
    return new VmGenericList(RrbTree.emptyMutable().append(value).immutable());
  }

  @TruffleBoundary
  public static VmList of(Object value1, Object value2) {
    return new VmGenericList(RrbTree.emptyMutable().append(value1).append(value2).immutable());
  }

  @SuppressWarnings("unchecked")
  static VmList create(ImRrbt<?> rrbt) {
    if (rrbt.isEmpty()) return EMPTY;
    return new VmGenericList((ImRrbt<Object>) rrbt);
  }

  @TruffleBoundary
  @SuppressWarnings("unchecked")
  static VmList create(MutRrbt<?> rrbt) {
    if (rrbt.isEmpty()) return EMPTY;
    return new VmGenericList((ImRrbt<Object>) rrbt.immutable());
  }

  // keeping both `create(Iterable)` and `create(UnmodIterable)` around
  // allows to easily find call sites that create a VmList
  // from a non-Paguro collection (which should be rare)
  @TruffleBoundary
  public static VmList create(Iterable<?> iterable) {
    return create(RrbTree.emptyMutable().concat(iterable).immutable());
  }

  @SuppressWarnings("unchecked")
  static VmList create(UnmodIterable<?> iterable) {
    return create((Iterable<Object>) iterable);
  }

  @TruffleBoundary
  static VmList create(UnmodCollection<?> collection) {
    if (collection.isEmpty()) return EMPTY;
    return new VmGenericList(RrbTree.emptyMutable().concat(collection).immutable());
  }

  @TruffleBoundary
  public static VmList create(byte[] elements) {
    return new VmByteArrayList(elements);
  }

  @TruffleBoundary
  public static VmList create(Object[] elements) {
    if (elements.length == 0) return EMPTY;
    var vector = RrbTree.emptyMutable();
    for (var elem : elements) {
      vector.append(elem);
    }
    return new VmGenericList(vector.immutable());
  }

  @TruffleBoundary
  public static VmList create(Object[] elements, int length) {
    if (elements.length == 0) return EMPTY;
    var vector = RrbTree.emptyMutable();
    for (var i = 0; i < length; i++) {
      vector.append(elements[i]);
    }
    return new VmGenericList(vector.immutable());
  }

  @TruffleBoundary
  public static VmList createFromConstantNodes(ExpressionNode[] elements) {
    if (elements.length == 0) return EMPTY;
    var vector = RrbTree.emptyMutable();
    for (var elem : elements) {
      assert elem instanceof ConstantNode;
      vector.append(((ConstantNode) elem).getValue());
    }
    return new VmGenericList(vector.immutable());
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

  public abstract VmList replace(long index, Object element);

  public abstract Object replaceOrNull(long index, Object element);

  public abstract Object get(long index);

  public abstract Object getOrNull(long index);

  public abstract VmList subList(long start, long exclusiveEnd);

  public abstract Iterator<Object> iterator();

  public abstract Iterator<Object> reverseIterator();

  public final Object getFirst() {
    checkNonEmpty();
    return get(0);
  }

  public final Object getFirstOrNull() {
    if (isEmpty()) return VmNull.withoutDefault();
    return get(0);
  }

  public abstract VmList getRest();

  public abstract Object getRestOrNull();

  public abstract Object getLast();

  public abstract Object getLastOrNull();

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
  
  public abstract Object indexOfOrNull(Object elem);

  public abstract long lastIndexOf(Object elem);

  public abstract Object lastIndexOfOrNull(Object elem);
  
  public abstract VmPair split(long index);

  public abstract Object splitOrNull(long index);

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
}
