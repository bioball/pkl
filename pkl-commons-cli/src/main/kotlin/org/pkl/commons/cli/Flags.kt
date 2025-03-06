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
package org.pkl.commons.cli

object Flags {
  // flags of BaseOptions
  @JvmField val ALLOWED_MODULES = Flag("--allowed-modules")
  @JvmField val ALLOWED_RESOURCES = Flag("--allowed-resources")
  @JvmField val ROOT_DIR = Flag("--root-dir")
  @JvmField val CACHE_DIR = Flag("--cache-dir")
  @JvmField val WORKING_DIR = Flag("--working-dir", "-w")
  @JvmField val PROPERTY = Flag("--property", "-p")
  @JvmField val COLOR = Flag("--color")
  @JvmField val NO_CACHE = Flag("--no-cache")
  @JvmField val FORMAT = Flag("--format", "-f")
  @JvmField val ENV_VAR = Flag("--env-var", "-e")
  @JvmField val MODULE_PATH = Flag("--module-path")
  @JvmField val SETTINGS = Flag("--settings")
  @JvmField val TIMEOUT = Flag("--timeout", "-t")
  @JvmField val CA_CERTIFICATES = Flag("--ca-certificates")
  @JvmField val HTTP_PROXY = Flag("--http-proxy")
  @JvmField val HTTP_NO_PROXY = Flag("--http-no-proxy")
  @JvmField val EXTERNAL_MODULE_READER = Flag("--external-module-reader")
  @JvmField val EXTERNAL_RESOURCE_READER = Flag("--external-resource-reader")
  @JvmField val TEST_PORT = Flag("--test-port")

  // flags of ProjectOption
  @JvmField val PROJECT_DIR = Flag("--project-dir")
  @JvmField val OMIT_PROJECT_SETTINGS = Flag("--omit-project-settings")
  @JvmField val NO_PROJECT = Flag("--no-project")

  // flags of TestOption
  @JvmField val JUNIT_REPORTS = Flag("--junit-reports")
  @JvmField val OVERWRITE = Flag("--overwrite")
}
