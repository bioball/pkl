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
package org.pkl.gradle.task;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectories;
import org.gradle.api.tasks.OutputFiles;
import org.pkl.cli.CliEvaluator;
import org.pkl.cli.CliEvaluatorOptions;
import org.pkl.cli.commands.Flags;

public abstract class EvalTask extends PklCliModulesTask {
  public EvalTask() {
    super();
  }

  // not tracked because it might contain placeholders
  // required
  @Internal
  public abstract RegularFileProperty getOutputFile();

  // not tracked because it might contain placeholders
  // optional
  @Internal
  public abstract DirectoryProperty getMultipleFileOutputDir();

  @Input
  public abstract Property<String> getOutputFormat();

  @Input
  public abstract Property<String> getModuleOutputSeparator();

  @Input
  @Optional
  public abstract Property<String> getExpression();

  private final Provider<CliEvaluator> cliEvaluator =
      getProviders()
          .provider(
              () ->
                  new CliEvaluator(
                      new CliEvaluatorOptions(
                          getCliBaseOptions(),
                          mapAndGetOrNull(getOutputFile(), it -> it.getAsFile().getAbsolutePath()),
                          getOutputFormat().get(),
                          getModuleOutputSeparator().get(),
                          mapAndGetOrNull(
                              getMultipleFileOutputDir(), it -> it.getAsFile().getAbsolutePath()),
                          getExpression().getOrElse(CliEvaluatorOptions.Companion.getDefaults().getExpression()))));

  @SuppressWarnings("DuplicatedCode")
  @Override
  @Internal
  protected final List<String> getExtraFlags() {
    var ret = super.getExtraFlags();
    applyIfNotNull(
        getOutputFile(),
        (outputFile) -> {
          ret.add(Flags.OUTPUT_PATH.getLongName());
          ret.add(outputFile.getAsFile().getAbsolutePath());
        });
    applyIfNotNull(
        getMultipleFileOutputDir(),
        (multipleFileOutputDir) -> {
          ret.add("--multiple-file-output-path");
          ret.add(multipleFileOutputDir.getAsFile().getAbsolutePath());
        });
    applyIfNotNull(
        getOutputFormat(),
        (outputFormat) -> {
          ret.add("--format");
          ret.add(outputFormat);
        });
    applyIfNotNull(
        getModuleOutputSeparator(),
        (separator) -> {
          ret.add("--module-output-separator");
          ret.add(separator);
        });
    applyIfNotNull(
        getExpression(),
        (expression) -> {
          ret.add("--expression");
          ret.add(expression);
        });
    return ret;
  }

  @Override
  @Internal
  protected final List<String> getCommandName() {
    return List.of("eval");
  }

  @SuppressWarnings("unused")
  @OutputFiles
  @Optional
  public FileCollection getEffectiveOutputFiles() {
    return getObjects()
        .fileCollection()
        .from(cliEvaluator.map(e -> nullToEmpty(e.getOutputFiles())));
  }

  @OutputDirectories
  @Optional
  public FileCollection getEffectiveOutputDirs() {
    return getObjects()
        .fileCollection()
        .from(cliEvaluator.map(e -> nullToEmpty(e.getOutputDirectories())));
  }

  private static <T> Set<T> nullToEmpty(@Nullable Set<T> set) {
    return set == null ? Collections.emptySet() : set;
  }
}
