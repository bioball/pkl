/*
 * Copyright © 2026 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.core.profiler;

import com.oracle.truffle.api.Option;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.ThreadLocalAction;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.instrumentation.ContextsListener;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Registration;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.RootNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;
import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionStability;

/** Sampling profiler that writes folded stacks output for use with flame graph tools. */
@Registration(id = PklProfiler.ID, name = "Pkl Profiler")
public final class PklProfiler extends TruffleInstrument {

  public static final String ID = "pkl-profiler";

  @Option(
      name = "Output",
      help = "Write a folded stacks profile to this file.",
      usageSyntax = "<path>",
      category = OptionCategory.USER,
      stability = OptionStability.EXPERIMENTAL)
  static final OptionKey<String> OUTPUT = new OptionKey<>("");

  @Option(
      name = "Period",
      help = "Sampling period in milliseconds.",
      usageSyntax = "<ms>",
      category = OptionCategory.USER,
      stability = OptionStability.EXPERIMENTAL)
  static final OptionKey<Integer> PERIOD = new OptionKey<>(10);

  // Accumulated samples: stack (outermost frame first) → count
  private final ConcurrentHashMap<List<String>, LongAdder> stackCounts = new ConcurrentHashMap<>();

  // Contexts that are currently alive; sampler submits to each of these.
  private final Set<TruffleContext> activeContexts = ConcurrentHashMap.newKeySet();

  private volatile boolean running;
  private Thread samplerThread;
  private String outputPath;
  private final AtomicBoolean flushed = new AtomicBoolean();

  @Override
  protected void onCreate(Env env) {
    outputPath = env.getOptions().get(OUTPUT);
    env.getInstrumenter()
        .attachContextsListener(
            new ContextsListener() {
              @Override
              public void onContextCreated(TruffleContext context) {
                activeContexts.add(context);
              }

              @Override
              public void onLanguageContextCreated(TruffleContext context, LanguageInfo language) {}

              @Override
              public void onLanguageContextInitialized(
                  TruffleContext context, LanguageInfo language) {
                // onContextCreated may fire before the listener is attached (instrument is
                // initialized during Context.build(), but the context object is created in the
                // same call). onLanguageContextInitialized fires during context.initialize(),
                // which always happens after Context.build() returns, so this is the reliable
                // fallback for the first context.
                activeContexts.add(context);
              }

              @Override
              public void onLanguageContextFinalized(
                  TruffleContext context, LanguageInfo language) {}

              @Override
              public void onLanguageContextDisposed(
                  TruffleContext context, LanguageInfo language) {}

              @Override
              public void onContextClosed(TruffleContext context) {
                activeContexts.remove(context);
                if (activeContexts.isEmpty()) {
                  flush();
                }
              }
            },
            true);

    if (!outputPath.isEmpty()) {
      startSampling(env, env.getOptions().get(PERIOD));
    }
  }

  private void startSampling(Env env, int periodMs) {
    running = true;
    samplerThread =
        env.createSystemThread(
            () -> {
              while (running) {
                try {
                  //noinspection BusyWait
                  Thread.sleep(periodMs);
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                  return;
                }
                if (!running) return;
                for (var context : activeContexts) {
                  try {
                    var future = env.submitThreadLocal(context, null, new CollectSampleAction());
                    // Wait long enough for the safepoint to fire; skip if the guest thread
                    // is blocked in I/O or a native call and doesn't reach a safepoint.
                    future.get(periodMs * 10L, TimeUnit.MILLISECONDS);
                  } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                  } catch (ExecutionException | TimeoutException ignored) {
                    // Guest thread not executing guest code; skip sample.
                  }
                }
              }
            });
    samplerThread.setName("pkl-profiler-sampler");
    samplerThread.setDaemon(true);
    samplerThread.start();
  }

  @Override
  protected void onDispose(Env env) {
    flush();
  }

  private void flush() {
    if (!flushed.compareAndSet(false, true)) return;
    running = false;
    if (samplerThread != null) {
      samplerThread.interrupt();
      try {
        samplerThread.join(500);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
    if (!outputPath.isEmpty() && !stackCounts.isEmpty()) {
      writeFoldedStacks(outputPath);
    }
  }

  @Override
  protected OptionDescriptors getOptionDescriptors() {
    return new PklProfilerOptionDescriptors();
  }

  private class CollectSampleAction extends ThreadLocalAction {

    CollectSampleAction() {
      super(false /* no side effects */, true /* synchronous */);
    }

    @Override
    protected void perform(Access access) {
      var stack = new ArrayList<String>();
      Truffle.getRuntime()
          .iterateFrames(
              frameInstance -> {
                if (frameInstance.getCallTarget() instanceof RootCallTarget rct) {
                  var root = rct.getRootNode();
                  if (!root.isInternal()) {
                    var label = frameLabel(root);
                    if (label != null) stack.add(label);
                  }
                }
                return null;
              });
      if (!stack.isEmpty()) {
        // iterateFrames visits innermost-first; reverse so root is leftmost in folded stacks.
        Collections.reverse(stack);
        stackCounts.computeIfAbsent(new ArrayList<>(stack), k -> new LongAdder()).increment();
      }
    }
  }

  private static String frameLabel(RootNode root) {
    var section = root.getSourceSection();
    var name = root.getName();
    if (section == null || !section.isAvailable()) {
      return name; // null → caller skips the frame
    }
    var sourceName = section.getSource().getName();
    var line = section.getStartLine();
    if (name != null && !name.isEmpty()) {
      return name + " (" + sourceName + ":" + line + ")";
    }
    return sourceName + ":" + line;
  }

  private void writeFoldedStacks(String outputPath) {
    try (var writer = Files.newBufferedWriter(Path.of(outputPath))) {
      for (var entry : stackCounts.entrySet()) {
        writer.write(String.join(";", entry.getKey()));
        writer.write(' ');
        writer.write(Long.toString(entry.getValue().sum()));
        writer.newLine();
      }
    } catch (IOException e) {
      System.err.println(
          "pkl-profiler: failed to write profile to " + outputPath + ": " + e.getMessage());
    }
  }
}
