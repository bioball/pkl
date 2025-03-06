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
package org.pkl.cli.commands

import org.pkl.commons.cli.Flag

object Flags {
  @JvmField val OUTPUT_PATH: Flag = Flag("--output-path", "-o")
  @JvmField val MODULE_OUTPUT_SEPARATOR: Flag = Flag("--module-output-separator")
  @JvmField val EXPRESSION: Flag = Flag("--expression", "-x")
  @JvmField val MULTIPLE_FILE_OUTPUT_PATH: Flag = Flag("--multiple-file-output-path", "-m")
  @JvmField val SKIP_PUBLISH_CHECK: Flag = Flag("--skip-publish-check")
  @JvmField val TEST_MODE: Flag = Flag("--test-mode")
  @JvmField val NO_TRANSITIVE: Flag = Flag("--no-transitive")
}
