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

public final class VmGenericList extends VmList {
  public static final VmList EMPTY = new VmGenericList(RrbTree.empty());

  private final ImRrbt<Object> rrbt;

  private boolean forced;

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

  public VmGenericList(ImRrbt<Object> rrbt) {
    this.rrbt = rrbt;
  }

  @Override
  @TruffleBoundary
  public int getLength() {
    return rrbt.size();
  }

  @Override
  public boolean isEmpty() {
    return rrbt.isEmpty();
  }

  @Override
  @TruffleBoundary
  public VmList add(Object element) {
    return VmGenericList.create(rrbt.append(element));
  }

  @TruffleBoundary
  public VmList replace(long index, Object element) {
    return VmGenericList.create(rrbt.replace((int) index, element));
  }

  @TruffleBoundary
  public Object replaceOrNull(long index, Object element) {
    if (index < 0 || index >= getLength()) {
      return VmNull.withoutDefault();
    }
    return VmGenericList.create(rrbt.replace((int) index, element));
  }

  @Override
  @TruffleBoundary
  public VmList concatenate(VmCollection other) {
    return other.isEmpty() ? this : VmGenericList.create(rrbt.concat(other));
  }

  @TruffleBoundary
  public Object get(long index) {
    return rrbt.get((int) index);
  }

  @TruffleBoundary
  @Override
  public VmList subList(long start, long exclusiveEnd) {
    return VmGenericList.create(rrbt.subList((int) start, (int) exclusiveEnd));
  }

  @Override
  public Iterator<Object> iterator() {
    if (rrbt.isEmpty()) return Iterators.emptyTruffleIterator();
    return new TruffleIterator<>(rrbt);
  }

  @Override
  public Iterator<Object> reverseIterator() {
    if (rrbt.isEmpty()) return Iterators.emptyTruffleIterator();
    return new ReverseTruffleIterator<>(rrbt);
  }

  @Override
  public boolean isLengthOne() {
    return rrbt.size() == 1;
  }

  @TruffleBoundary
  public VmList getRest() {
    checkNonEmpty();
    return VmGenericList.create(rrbt.drop(1));
  }

  @TruffleBoundary
  public Object getLast() {
    checkNonEmpty();
    return rrbt.get(rrbt.size() - 1);
  }

  @TruffleBoundary
  @SuppressWarnings("deprecation")
  public boolean contains(Object element) {
    return rrbt.contains(element);
  }

  @TruffleBoundary
  public long indexOf(Object elem) {
    return rrbt.indexOf(elem);
  }

  @TruffleBoundary
  public long lastIndexOf(Object elem) {
    return rrbt.lastIndexOf(elem);
  }

  @TruffleBoundary
  public VmPair split(long index) {
    var tuple = rrbt.split((int) index);
    return new VmPair(VmGenericList.create(tuple._1()), VmGenericList.create(tuple._2()));
  }

  @TruffleBoundary
  public VmList take(long n) {
    if (n == 0) return EMPTY;
    if (n >= rrbt.size()) return this;

    checkPositive(n);
    return VmGenericList.create(rrbt.take(n));
  }

  @TruffleBoundary
  public VmList takeLast(long n) {
    if (n == 0) return EMPTY;
    if (n >= rrbt.size()) return this;

    checkPositive(n);
    return VmGenericList.create(rrbt.drop(rrbt.size() - n));
  }

  @TruffleBoundary
  public VmList drop(long n) {
    if (n == 0) return this;
    if (n >= rrbt.size()) return EMPTY;

    checkPositive(n);
    return VmGenericList.create(rrbt.drop(n));
  }

  @TruffleBoundary
  public VmList dropLast(long n) {
    if (n == 0) return this;
    if (n >= rrbt.size()) return EMPTY;

    checkPositive(n);
    return VmGenericList.create(rrbt.take(rrbt.size() - n));
  }

  @TruffleBoundary
  public VmList repeat(long n) {
    if (n == 0) return EMPTY;
    if (n == 1) return this;

    checkPositive(n);

    var result = rrbt.mutable();
    for (var i = 1; i < n; i++) {
      result = result.concat(rrbt);
    }
    return VmGenericList.create(result);
  }

  @TruffleBoundary
  public VmList reverse() {
    return VmGenericList.create(rrbt.reverse());
  }

  @TruffleBoundary
  public Object[] toArray() {
    return rrbt.toArray();
  }

  @TruffleBoundary
  public VmSet toSet() {
    if (rrbt.isEmpty()) return VmSet.EMPTY;
    return VmSet.create(rrbt);
  }

  @TruffleBoundary
  public VmListing toListing() {
    var builder = new VmObjectBuilder(rrbt.size());
    for (var elem : rrbt) builder.addElement(elem);
    return builder.toListing();
  }

  @TruffleBoundary
  public VmDynamic toDynamic() {
    var builder = new VmObjectBuilder(rrbt.size());
    for (var elem : rrbt) builder.addElement(elem);
    return builder.toDynamic();
  }

  @Override
  @TruffleBoundary
  public void force(boolean allowUndefinedValues) {
    if (forced) return;

    forced = true;

    try {
      for (var elem : rrbt) {
        VmValue.force(elem, allowUndefinedValues);
      }
    } catch (Throwable t) {
      forced = false;
      throw t;
    }
  }

  @Override
  @TruffleBoundary
  public List<Object> export() {
    var result = new ArrayList<>(rrbt.size());
    for (var elem : rrbt) {
      result.add(VmValue.export(elem));
    }
    return result;
  }

  @Override
  @TruffleBoundary
  public boolean equals(@Nullable Object other) {
    if (this == other) return true;
    if (!(other instanceof VmList list)) return false;
    if (other instanceof VmGenericList vmGenericList) {
      return rrbt.equals(vmGenericList.rrbt);
    }
    return super.equalsList(list);
  }

  @Override
  @TruffleBoundary
  public int hashCode() {
    return rrbt.hashCode();
  }
}
