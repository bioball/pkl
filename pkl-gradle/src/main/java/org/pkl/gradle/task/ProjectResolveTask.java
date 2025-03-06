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

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;

public abstract class ProjectResolveTask extends BasePklTask {
  @Internal
  public abstract ConfigurableFileCollection getProjectDirectories();

  // Only the `PklProject` files matter for creating PklProject.deps.json files.
  // Otherwise, these tasks can be considered up to date.
  @InputFiles
  public Provider<List<File>> getProjectPklFiles() {
    return getProjectDirectories()
        .getElements()
        .map(
            (files) ->
                files.stream()
                    .map((it) -> it.getAsFile().toPath().resolve("PklProject").toFile())
                    .collect(Collectors.toList()));
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
  @Internal
  protected List<String> getCommandName() {
    return List.of("project", "resolve");
  }

  @Override
  @Internal
  protected String getMainClassName() {
    return PklCliModulesTask.PKL_CLI_MAIN_CLASS_NAME;
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
