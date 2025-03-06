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

import java.util.List;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputDirectory;

public abstract class CodeGenTask extends ModulesTask {
  @OutputDirectory
  public abstract DirectoryProperty getOutputDir();

  @Input
  public abstract Property<String> getIndent();

  @Input
  public abstract Property<Boolean> getGenerateSpringBootConfig();

  @Input
  public abstract Property<Boolean> getImplementSerializable();

  @Input
  public abstract MapProperty<String, String> getRenames();

  @Override
  @Internal
  protected final List<String> getCommandName() {
    return List.of();
  }

  @Override
  @Internal
  protected List<String> getExtraFlags() {
    var ret = super.getExtraFlags();
    applyIfNotNull(
        getOutputDir(),
        (outputDir) -> {
          ret.add("--output-dir");
          ret.add(outputDir.getAsFile().getAbsolutePath());
        });
    applyIfNotNull(
        getIndent(),
        (indent) -> {
          ret.add("--indent");
          ret.add(indent);
        });
    if (getGenerateSpringBootConfig().getOrElse(false)) {
      ret.add("--generate-spring-boot");
    }
    if (getImplementSerializable().getOrElse(false)) {
      ret.add("--implement-serializable");
    }
    applyIfNotNull(
        getRenames(),
        (renames) -> {
          for (var entry : renames.entrySet()) {
            ret.add("--rename");
            ret.add("%s=%s".formatted(entry.getKey(), entry.getValue()));
          }
        });
    return ret;
  }
}
