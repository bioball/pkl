package org.pkl.core.runtime;

import java.io.ByteArrayOutputStream;
import org.organicdesign.fp.collections.RrbTree;
import org.organicdesign.fp.collections.RrbTree.MutRrbt;
import org.pkl.core.util.LateInit;

public class VmListBuilder implements VmCollection.Builder<VmList> {
  private @LateInit ByteArrayOutputStream outputStream;
  private @LateInit MutRrbt<Object> rrbt = RrbTree.emptyMutable();

  private void initRrbt() {
    this.rrbt = RrbTree.emptyMutable();
    if (this.outputStream != null) {
      for (var elem : this.outputStream.toByteArray()) {
        this.rrbt.append((long) elem);
      }
    }
  }

  private void initOutputStream() {
    this.outputStream = new ByteArrayOutputStream();
  }

  @Override
  public void add(Object element) {
    if (rrbt != null) {
      rrbt.add(element);
      return;
    }
    if (element instanceof Long longValue && longValue >= 0 && longValue <= 7) {
      initOutputStream();
      this.outputStream.write(longValue.intValue());
      return;
    }
    initRrbt();
    rrbt.add(element);
  }

  @Override
  public void addAll(Iterable<?> elements) {
    if (rrbt != null) {
      for (var elem : elements) {
        rrbt.add(elem);
      }
      return;
    }
    if (elements instanceof VmByteArrayList vmByteArrayList) {
      initOutputStream();
      outputStream.writeBytes(vmByteArrayList.bytes);
      return;
    }
    initRrbt();
    for (var elem : elements) {
      rrbt.add(elem);
    }
  }

  @Override
  public VmList build() {
    if (rrbt != null) {
      return VmGenericList.create(rrbt);
    }
    return new VmByteArrayList(outputStream.toByteArray());
  }
}
