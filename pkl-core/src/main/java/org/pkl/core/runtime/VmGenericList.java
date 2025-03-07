package org.pkl.core.runtime;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.organicdesign.fp.collections.RrbTree;
import org.organicdesign.fp.collections.RrbTree.ImRrbt;
import org.organicdesign.fp.collections.RrbTree.MutRrbt;
import org.pkl.core.runtime.Iterators.ReverseTruffleIterator;
import org.pkl.core.runtime.Iterators.TruffleIterator;
import org.pkl.core.util.Nullable;

public final class VmGenericList extends VmList {
  private final ImRrbt<Object> rrbt;

  private boolean forced;

  public VmGenericList(ImRrbt<Object> rrbt) {
    this.rrbt = rrbt;
  }

  @Override
  @TruffleBoundary
  public int getLength() {
    return rrbt.size();
  }

  @Override
  @TruffleBoundary
  public VmList add(Object element) {
    return VmList.create(rrbt.append(element));
  }

  @TruffleBoundary
  public VmList replace(long index, Object element) {
    return VmList.create(rrbt.replace((int) index, element));
  }

  @TruffleBoundary
  public Object replaceOrNull(long index, Object element) {
    if (index < 0 || index >= getLength()) {
      return VmNull.withoutDefault();
    }
    return VmList.create(rrbt.replace((int) index, element));
  }

  @Override
  @TruffleBoundary
  public VmList concatenate(VmCollection other) {
    return other.isEmpty() ? this : VmList.create(rrbt.concat(other));
  }

  @TruffleBoundary
  public Object get(long index) {
    return rrbt.get((int) index);
  }

  @TruffleBoundary
  public Object getOrNull(long index) {
    if (index < 0 || index >= getLength()) {
      return VmNull.withoutDefault();
    }
    return rrbt.get((int) index);
  }

  @TruffleBoundary
  public VmList subList(long start, long exclusiveEnd) {
    return VmList.create(rrbt.subList((int) start, (int) exclusiveEnd));
  }

  @TruffleBoundary
  public Object subListOrNull(long start, long exclusiveEnd) {
    var length = getLength();

    if (start < 0 || start > length) {
      return VmNull.withoutDefault();
    }
    if (exclusiveEnd < start || exclusiveEnd > length) {
      return VmNull.withoutDefault();
    }
    return VmList.create(rrbt.subList((int) start, (int) exclusiveEnd));
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
  @TruffleBoundary
  public VmCollection.Builder<VmList> builder() {
    return new Builder();
  }

  @TruffleBoundary
  public VmList getRest() {
    checkNonEmpty();
    return VmList.create(rrbt.drop(1));
  }

  @TruffleBoundary
  public Object getRestOrNull() {
    if (rrbt.isEmpty()) return VmNull.withoutDefault();
    return VmList.create(rrbt.drop(1));
  }

  @TruffleBoundary
  public Object getLast() {
    checkNonEmpty();
    return rrbt.get(rrbt.size() - 1);
  }

  @TruffleBoundary
  public Object getLastOrNull() {
    if (isEmpty()) return VmNull.withoutDefault();
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
  public Object indexOfOrNull(Object elem) {
    long result = rrbt.indexOf(elem);
    if (result == -1) return VmNull.withoutDefault();
    return result;
  }

  @TruffleBoundary
  public long lastIndexOf(Object elem) {
    return rrbt.lastIndexOf(elem);
  }

  @TruffleBoundary
  public Object lastIndexOfOrNull(Object elem) {
    long result = rrbt.lastIndexOf(elem);
    if (result == -1) return VmNull.withoutDefault();
    return result;
  }

  @TruffleBoundary
  public VmPair split(long index) {
    var tuple = rrbt.split((int) index);
    return new VmPair(VmList.create(tuple._1()), VmList.create(tuple._2()));
  }

  @TruffleBoundary
  public Object splitOrNull(long index) {
    if (index < 0 || index > getLength()) {
      return VmNull.withoutDefault();
    }
    return split(index);
  }

  @TruffleBoundary
  public VmList take(long n) {
    if (n == 0) return EMPTY;
    if (n >= rrbt.size()) return this;

    checkPositive(n);
    return VmList.create(rrbt.take(n));
  }

  @TruffleBoundary
  public VmList takeLast(long n) {
    if (n == 0) return EMPTY;
    if (n >= rrbt.size()) return this;

    checkPositive(n);
    return VmList.create(rrbt.drop(rrbt.size() - n));
  }

  @TruffleBoundary
  public VmList drop(long n) {
    if (n == 0) return this;
    if (n >= rrbt.size()) return EMPTY;

    checkPositive(n);
    return VmList.create(rrbt.drop(n));
  }

  @TruffleBoundary
  public VmList dropLast(long n) {
    if (n == 0) return this;
    if (n >= rrbt.size()) return EMPTY;

    checkPositive(n);
    return VmList.create(rrbt.take(rrbt.size() - n));
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
    return VmList.create(result);
  }

  @TruffleBoundary
  public VmList reverse() {
    return VmList.create(rrbt.reverse());
  }

  @TruffleBoundary
  public Object[] toArray() {
    return rrbt.toArray();
  }

  public VmList toList() {
    return this;
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
    //noinspection SimplifiableIfStatement
    if (!(other instanceof VmList list)) return false;
    if (other instanceof VmGenericList vmGenericList) {
      return rrbt.equals(vmGenericList.rrbt);
    }
    return rrbt.equals(list.rrbt);
  }

  @Override
  @TruffleBoundary
  public int hashCode() {
    return rrbt.hashCode();
  }


  private static final class Builder implements VmCollection.Builder<VmList> {
    private final MutRrbt<Object> list = RrbTree.emptyMutable();

    @Override
    @TruffleBoundary
    public void add(Object element) {
      list.append(element);
    }

    @Override
    @TruffleBoundary
    public void addAll(Iterable<?> elements) {
      list.concat(elements);
    }

    @Override
    public VmList build() {
      return VmList.create(list);
    }
  }
}
