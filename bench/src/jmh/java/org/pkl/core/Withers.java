/*
 * Copyright © 2025 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.core;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Warmup;

@SuppressWarnings("unused")
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 5, time = 2)
@OutputTimeUnit(TimeUnit.SECONDS)
@Fork(1)
public class Withers {
  interface Wither<R, M> {
    R with(Consumer<M> setter);
  }

  record Thing(String p1, String p2, String p3, String p4, String p5, String p6, String p7)
      implements Wither<Thing, Thing.Memento> {
    @Override
    public Thing with(Consumer<Memento> setter) {
      var mementor = new Memento(this);
      setter.accept(mementor);
      return mementor.build();
    }

    static final class Memento {
      String p1;
      String p2;
      String p3;
      String p4;
      String p5;
      String p6;
      String p7;

      private Memento(Thing thing) {
        this.p1 = thing.p1;
        this.p2 = thing.p2;
        this.p3 = thing.p3;
        this.p4 = thing.p4;
        this.p5 = thing.p5;
        this.p6 = thing.p6;
        this.p7 = thing.p7;
      }

      private Thing build() {
        return new Thing(p1, p2, p3, p4, p5, p6, p7);
      }
    }
  }

  @SuppressWarnings("UnusedReturnValue")
  record Thing2(String p1, String p2, String p3, String p4, String p5, String p6, String p7) {
    public Thing2 withP1(String p1) {
      return new Thing2(p1, p2, p3, p4, p5, p6, p7);
    }

    public Thing2 withP2(String p2) {
      return new Thing2(p1, p2, p3, p4, p5, p6, p7);
    }

    public Thing2 withP3(String p3) {
      return new Thing2(p1, p2, p3, p4, p5, p6, p7);
    }

    public Thing2 withP4(String p4) {
      return new Thing2(p1, p2, p3, p4, p5, p6, p7);
    }

    public Thing2 withP5(String p5) {
      return new Thing2(p1, p2, p3, p4, p5, p6, p7);
    }

    public Thing2 withP6(String p6) {
      return new Thing2(p1, p2, p3, p4, p5, p6, p7);
    }

    public Thing2 withP7(String p7) {
      return new Thing2(p1, p2, p3, p4, p5, p6, p7);
    }
  }

  @Benchmark
  public void withers() {
    var thing = new Thing("one", "two", "three", "four", "five", "six", "seven");
    thing.with(
        (m) -> {
          m.p1 = "a";
          m.p2 = "b";
          m.p3 = "c";
          m.p4 = "d";
          m.p5 = "e";
          m.p6 = "f";
          m.p7 = "g";
        });
  }

  @Benchmark
  public void classicalWithers() {
    var thing = new Thing2("one", "two", "three", "four", "five", "six", "seven");
    thing.withP1("a").withP2("b").withP3("c").withP4("d").withP5("e").withP6("f").withP7("g");
  }
}
