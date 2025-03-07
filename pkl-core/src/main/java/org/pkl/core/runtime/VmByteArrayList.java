package org.pkl.core.runtime;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.function.Consumer;
import org.organicdesign.fp.collections.RrbTree;
import org.pkl.core.runtime.Iterators.ReverseTruffleIterator;
import org.pkl.core.runtime.Iterators.TruffleIterator;

public class VmByteArrayList extends VmList {
  private final ByteBuffer byteBuffer;
  
  private final int length;

  public VmByteArrayList(byte[] bytes) {
    super();
    this.byteBuffer = ByteBuffer.wrap(bytes);
    this.length = bytes.length;
  }

  public VmByteArrayList(ByteBuffer byteBuffer, int length) {
    super();
    this.byteBuffer = byteBuffer;
    this.length = length;
  }

  private ByteBuffer copyByteBuffer() {
    var ret = ByteBuffer.allocate(byteBuffer.capacity());
    byteBuffer.rewind();
    ret.put(byteBuffer);
    byteBuffer.rewind();
    ret.flip();
    return ret;
  }

  private VmGenericList toGenericList() {
    var vector = RrbTree.emptyMutable();
    for (var elem : byteBuffer.array()) {
      vector.append(elem);
    }
    return new VmGenericList(vector.immutable());
  }
  
  @Override
  public VmList replace(long index, Object element) {
    if (element instanceof Long longValue && longValue >= 0 && longValue <= 7) {
      var newBuffer = copyByteBuffer();
      newBuffer.put((int) index, longValue.byteValue());
      return new VmByteArrayList(newBuffer, length);
    }
    return toGenericList().replace(index, element);
  }

  @Override
  public Object replaceOrNull(long index, Object element) {
    if (index < 0 || index >= getLength()) {
      return VmNull.withoutDefault();
    }
    return replace(index, element);
  }

  @Override
  public Object get(long index) {
    return byteBuffer.get((int) index);
  }

  @Override
  public Object getOrNull(long index) {
    if (index < 0 || index >= getLength()) {
      return VmNull.withoutDefault();
    }
    return get(index);
  }

  @Override
  public VmList subList(long start, long exclusiveEnd) {
    var newBuffer = byteBuffer.slice((int) start, (int) exclusiveEnd);
    var length = (int) (exclusiveEnd - start);
    return new VmByteArrayList(newBuffer, length);
  }

  @Override
  public Iterator<Object> iterator() {
    return new TruffleIterator<>(new ByteBufferIterable(byteBuffer));
  }

  @Override
  public Iterator<Object> reverseIterator() {
    return new ReverseTruffleIterator<>(new ByteBufferIterable(byteBuffer));
  }

  @Override
  public int getLength() {
    return length;
  }

  @Override
  public boolean isEmpty() {
    return length == 0;
  }

  @Override
  public VmCollection add(Object element) {
    if (element instanceof Long longValue && longValue >= 0 && longValue <= 7) {
      var newBytes = copyByteBuffer();
      newBytes.position()
    }
  }

  @Override
  public VmCollection concatenate(VmCollection other) {
    if (other instanceof VmByteArrayList vmByteArrayList) {
      var newLength = length + vmByteArrayList.length;
      var bb = ByteBuffer.allocate(newLength);
      bb.put(byteBuffer).put(vmByteArrayList.byteBuffer);
      return new VmByteArrayList(bb, newLength);
    }
    return toGenericList().concatenate(other);
  }

  @Override
  public Builder<? extends VmCollection> builder() {
    return null;
  }

  @Override
  public boolean isLengthOne() {
    return false;
  }

  @Override
  public VmList getRest() {
    return null;
  }

  @Override
  public Object getRestOrNull() {
    return null;
  }

  @Override
  public Object getLast() {
    return null;
  }

  @Override
  public Object getLastOrNull() {
    return null;
  }

  @Override
  public boolean contains(Object element) {
    return false;
  }

  @Override
  public long indexOf(Object elem) {
    return 0;
  }

  @Override
  public Object indexOfOrNull(Object elem) {
    return null;
  }

  @Override
  public long lastIndexOf(Object elem) {
    return 0;
  }

  @Override
  public Object lastIndexOfOrNull(Object elem) {
    return null;
  }

  @Override
  public VmPair split(long index) {
    return null;
  }

  @Override
  public Object splitOrNull(long index) {
    return null;
  }

  @Override
  public VmList take(long n) {
    return null;
  }

  @Override
  public VmList takeLast(long n) {
    return null;
  }

  @Override
  public VmList drop(long n) {
    return null;
  }

  @Override
  public VmList dropLast(long n) {
    return null;
  }

  @Override
  public VmList repeat(long n) {
    return null;
  }

  @Override
  public VmList reverse() {
    return null;
  }

  @Override
  public Object[] toArray() {
    return new Object[0];
  }

  @Override
  public VmSet toSet() {
    return null;
  }

  @Override
  public VmListing toListing() {
    return null;
  }

  @Override
  public VmDynamic toDynamic() {
    return null;
  }

  @Override
  public void force(boolean allowUndefinedValues) {

  }

  @Override
  public Object export() {
    return null;
  }

  @Override
  public boolean equals(Object obj) {
    return false;
  }
  
  private static class ByteBufferIterator implements Iterator<Object> {

    private final ByteBuffer byteBuffer;

    ByteBufferIterator(ByteBuffer byteBuffer) {
      this.byteBuffer = byteBuffer;
    }

    @Override
    public boolean hasNext() {
      return byteBuffer.hasRemaining();
    }

    @Override
    public Object next() {
      return (long) byteBuffer.get();
    }
  }

  private static class ByteBufferIterable implements Iterable<Object> {

    private final ByteBuffer byteBuffer;

    ByteBufferIterable(ByteBuffer byteBuffer) {
      this.byteBuffer = byteBuffer;
    }

    @Override
    public Iterator<Object> iterator() {
      return new ByteBufferIterator(byteBuffer);
    }
  }
}
