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
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.UntrackedTask;

@UntrackedTask(because = "Output names are known only after execution")
public abstract class ProjectPackageTask extends BasePklTask {
  @InputFiles
  public abstract ConfigurableFileCollection getProjectDirectories();

  @Internal
  public abstract DirectoryProperty getOutputPath();

  @Optional
  @OutputDirectory
  public abstract DirectoryProperty getJunitReportsDir();

  @Input
  public abstract Property<Boolean> getOverwrite();

  @Input
  @Optional
  public abstract Property<Boolean> getSkipPublishCheck();

  @Override
  @Internal
  protected final List<String> getCommandName() {
    return List.of("project", "package");
  }

  @Override
  @Internal
  protected String getMainClassName() {
    return PklCliModulesTask.PKL_CLI_MAIN_CLASS_NAME;
  }

  @Override
  @Internal
  protected final List<String> getExtraFlags() {
    var ret = super.getExtraFlags();
    applyIfNotNull(
        getOutputPath(),
        (outputPath) -> {
          ret.add("--output-path");
          ret.add(outputPath.getAsFile().getAbsolutePath());
        });
    applyIfNotNull(
        getJunitReportsDir(),
        (junitReportsDir) -> {
          ret.add("--junit-reports");
          ret.add(junitReportsDir.getAsFile().getAbsolutePath());
        });
    if (getOverwrite().getOrElse(false)) {
      ret.add("--overwrite");
    }
    if (getSkipPublishCheck().getOrElse(false)) {
      ret.add("--skip-publish-check");
    }
    return ret;
  }

  @Override
  @Internal
  protected final List<String> getCliArguments() {
    var ret = super.getCliArguments();
    for (var projectDir : getProjectDirectories()) {
      ret.add(projectDir.getAbsolutePath());
    }
    return ret;
  }

  @Override
  @TaskAction
  public final void exec() {
    if (getProjectDirectories().isEmpty()) {
      throw new InvalidUserDataException("No project directories specified.");
    }
    super.exec();
  }
}
