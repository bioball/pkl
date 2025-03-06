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
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;

public abstract class JavaCodeGenTask extends CodeGenTask {
  @Input
  public abstract Property<Boolean> getGenerateGetters();

  @Input
  public abstract Property<Boolean> getGenerateJavadoc();

  @Input
  @Optional
  public abstract Property<String> getParamsAnnotation();

  @Input
  @Optional
  public abstract Property<String> getNonNullAnnotation();

  @Override
  @Internal
  protected final List<String> getExtraFlags() {
    var ret = super.getExtraFlags();
    if (getGenerateGetters().getOrElse(false)) {
      ret.add("--generate-getters");
    }
    if (getGenerateJavadoc().getOrElse(false)) {
      ret.add("--generate-javadoc");
    }
    applyIfNotNull(
        getParamsAnnotation(),
        (paramsAnnotation) -> {
          ret.add("--params-annotation");
          ret.add(paramsAnnotation);
        });
    applyIfNotNull(
        getNonNullAnnotation(),
        (nonNullAnnotation) -> {
          ret.add("--non-null-annotation");
          ret.add(nonNullAnnotation);
        });
    return ret;
  }

  @Override
  @Internal
  protected final String getMainClassName() {
    return "org.pkl.codegen.java.Main";
  }

  @Override
  @TaskAction
  public final void exec() {
    //noinspection ResultOfMethodCallIgnored
    getOutputs().getPreviousOutputFiles().forEach(File::delete);
    super.exec();
  }
}
