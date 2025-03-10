package org.pkl.core.runtime;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import org.organicdesign.fp.collections.RrbTree;

/**
 * Efficient implementation of a {@code VmList} that is backed by a byte array.
 *
 * Need to cast to `long`, because the rest of the codebase uses `long/Long` to represent numbers.
 */
public class VmByteArrayList extends VmList {
  final byte[] bytes;
  
  public VmByteArrayList(byte[] bytes) {
    super();
    this.bytes = bytes;
  }

  private VmGenericList toGenericList() {
    var vector = RrbTree.emptyMutable();
    for (var elem : bytes) {
      vector.append((long) elem);
    }
    return new VmGenericList(vector.immutable());
  }
  
  @Override
  public VmList replace(long index, Object element) {
    if (element instanceof Long longValue && longValue >= 0 && longValue <= 7) {
      var newBytes = Arrays.copyOf(bytes, bytes.length);
      newBytes[(int) index] = longValue.byteValue();
      return new VmByteArrayList(newBytes);
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
    return (long) bytes[(int) index];
  }

  // should be able to avoid allocation here
  @Override
  public VmList subList(long start, long exclusiveEnd) {
    var newList = Arrays.copyOfRange(bytes, (int) start, (int) exclusiveEnd);
    return new VmByteArrayList(newList);
  }

  @Override
  public Iterator<Object> iterator() {
    return new ByteArrayIterator(bytes);
  }

  @Override
  public Iterator<Object> reverseIterator() {
    return new ReverseByteArrayIterator(bytes);
  }

  @Override
  public int getLength() {
    return bytes.length;
  }

  @Override
  public boolean isEmpty() {
    return bytes.length == 0;
  }

  @Override
  public VmList add(Object element) {
    if (element instanceof Long longValue && longValue >= 0 && longValue <= 7) {
      var newBytes = Arrays.copyOf(bytes, bytes.length + 1);
      newBytes[bytes.length] = longValue.byteValue();
      return new VmByteArrayList(newBytes);
    }
    return toGenericList().add(element);
  }

  @Override
  public VmCollection concatenate(VmCollection other) {
    if (other instanceof VmByteArrayList vmByteArrayList) {
      var newLength = bytes.length + vmByteArrayList.bytes.length;
      var newBytes = new byte[newLength];
      System.arraycopy(bytes, 0, newBytes, 0, bytes.length);
      System.arraycopy(vmByteArrayList.bytes, 0, newBytes, bytes.length, vmByteArrayList.bytes.length);
      return new VmByteArrayList(newBytes);
    }
    return toGenericList().concatenate(other);
  }

  @Override
  public boolean isLengthOne() {
    return bytes.length == 1;
  }

  @Override
  public VmList getRest() {
    var newBytes = new byte[bytes.length - 1];
    System.arraycopy(bytes, 1, newBytes, 0, bytes.length - 1);
    return new VmByteArrayList(newBytes);
  }

  @Override
  public Object getLast() {
    return (long) bytes[bytes.length - 1];
  }

  @Override
  public boolean contains(Object element) {
    if (!(element instanceof Long longValue)) {
      return false;
    }
    var byteValue = longValue.byteValue();
    for (var theByte : bytes) {
      if (theByte == byteValue) {
        return true;
      }
    }
    return false;
  }

  @Override
  public long indexOf(Object elem) {
    if (!(elem instanceof Long longValue)) {
      return -1;
    }
    var byteValue = longValue.byteValue();
    for (var i = 0; i < bytes.length; i++) {
      if (bytes[i] == byteValue) {
        return i;
      }
    }
    return -1;
  }

  @Override
  public long lastIndexOf(Object elem) {
    return 0;
  }

  @Override
  public VmPair split(long index) {
    var first = Arrays.copyOf(bytes, (int) index);
    var secondLength = bytes.length - (int) index;
    var second = new byte[secondLength];
    System.arraycopy(bytes, (int) index, second, 0, secondLength);
    return new VmPair(new VmByteArrayList(first), new VmByteArrayList(second));
  }

  @Override
  public VmList take(long n) {
    checkPositive(n);
    return new VmByteArrayList(Arrays.copyOfRange(bytes, 0, (int) n));
  }

  @Override
  public VmList takeLast(long n) {
    checkPositive(n);
    return new VmByteArrayList(Arrays.copyOfRange(bytes, bytes.length - (int) n, bytes.length));
  }

  @Override
  public VmList drop(long n) {
    checkPositive(n);
    var dropInt = (int) n;
    return new VmByteArrayList(Arrays.copyOfRange(bytes, dropInt, bytes.length));
  }

  @Override
  public VmList dropLast(long n) {
    checkPositive(n);
    var dropInt = (int) n;
    return new VmByteArrayList(Arrays.copyOfRange(bytes, 0, bytes.length - dropInt));
  }

  @Override
  public VmList repeat(long n) {
    checkPositive(n);
    var intN = (int) n;
    var newBytes = new byte[bytes.length * intN];
    for (var i = 0; i < intN; i++) {
      System.arraycopy(bytes, 0, newBytes, i * bytes.length, bytes.length);
    }
    return new VmByteArrayList(newBytes);
  }

  @Override
  public VmList reverse() {
    var newBytes = new byte[bytes.length];
    for (var i = 0; i < bytes.length; i++) {
      newBytes[i] = bytes[bytes.length - i - 1];
    }
    return new VmByteArrayList(newBytes);
  }

  @Override
  public Object[] toArray() {
    var ret = new Object[bytes.length];
    for (var i = 0; i < bytes.length; i++) {
      ret[i] = (long) bytes[i];
    }
    return ret;
  }

  @Override
  public VmSet toSet() {
    if (isEmpty()) {
      return VmSet.EMPTY;
    }
    return VmSet.create(this);
  }

  @Override
  public VmListing toListing() {
    var builder = new VmObjectBuilder(getLength());
    for (var elem : bytes) builder.addElement((long) elem);
    return builder.toListing();
  }

  @Override
  public VmDynamic toDynamic() {
    var builder = new VmObjectBuilder(getLength());
    for (var elem : bytes) builder.addElement((long) elem);
    return builder.toDynamic();
  }

  @Override
  public void force(boolean allowUndefinedValues) {
    // do nothing
  }

  @Override
  public Object export() {
    var result = new ArrayList<>(getLength());
    for (var elem : bytes) {
      result.add((long) elem);
    }
    return result;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) return true;
    if (!(other instanceof VmList list)) return false;
    if (other instanceof VmByteArrayList vmByteArrayList) {
      return Arrays.equals(bytes, vmByteArrayList.bytes);
    }
    return super.equalsList(list);
  }
  
  private static class ByteArrayIterator implements Iterator<Object> {

    private final byte[] bytes;
    
    private int cursor = 0;

    ByteArrayIterator(byte[] bytes) {
      this.bytes = bytes;
    }

    @Override
    public boolean hasNext() {
      return cursor < bytes.length;
    }

    @Override
    public Object next() {
      var ret = (long) bytes[cursor];
      cursor++;
      return ret;
    }
  }

  private static class ReverseByteArrayIterator implements Iterator<Object> {

    private final byte[] bytes;

    private int cursor;

    ReverseByteArrayIterator(byte[] bytes) {
      this.bytes = bytes;
      this.cursor = bytes.length - 1;
    }

    @Override
    public boolean hasNext() {
      return cursor >= 0;
    }

    @Override
    public Object next() {
      var ret = (long) bytes[cursor];
      cursor--;
      return ret;
    }
  }
}
