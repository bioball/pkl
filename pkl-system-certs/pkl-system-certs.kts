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
plugins {
  id("pklAllProjects")
  id("pklJavaLibrary")
  id("pklNativeLifecycle")
}

val generateReachability =
  sourceSets.create("generateReachability") {
    runtimeClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().runtimeClasspath
    compileClasspath += sourceSets.main.get().output
    compileClasspath += sourceSets.main.get().compileClasspath
  }

dependencies {
  implementation(projects.pklCore)
  implementation(libs.jspecify)
}

val Target.compilerOutputDir
  get(): Provider<Directory> = layout.buildDirectory.dir("native-libs/$os-$arch")

// keep in sync with org.pkl.certs.NativeLibraries.NativeLibrary.resourcePath
val Target.libraryFile
  get(): Provider<RegularFile> =
    layout.buildDirectory.file(
      "resources/main/NATIVE/org/pkl/certs/${os.simpleName}-${arch.simpleName}/libpkl_certs.${os.sharedLibraryExtension}"
    )

// The JDK whose `include/` headers (jni.h, jni_md.h) the native sources are compiled against;
// keep it in sync with the toolchain configured above so the JNI ABI matches at runtime.
val jdkHome = javaToolchains.launcherFor(java.toolchain).map { it.metadata.installationPath }

val buildNativeLibrary =
  tasks.register<CCompile>("buildNativeLibrary") {
    onlyIf { buildInfo.os.isMacOS || buildInfo.os.isLinux }
    // not sure why Gradle thinks this is a dependency
    dependsOn(tasks.compileJava)

    link = true
    sharedLibrary = true
    positionIndependentCode = true
    val targetMachine = buildInfo.targetMachine
    val fileName = if (buildInfo.os.isMacOS) "pkl_certs_macos.c" else "pkl_certs_windows.c"
    sourceFiles.from(files("src/main/c/${fileName}"))
    includeDirs.from(files("src/main/c/"))
    includeDirs.from(jdkHome.map { it.dir("include") })
    includeDirs.from(
      jdkHome.map {
        val jniPlatformDir =
          when {
            buildInfo.os.isMacOS -> "darwin"
            buildInfo.os.isLinux -> "linux"
            else -> "win32"
          }
        it.dir("include/$jniPlatformDir")
      }
    )

    warningFlags.addAll("all", "extra")

    outputFile = targetMachine.libraryFile

    if (buildInfo.os.isMacOS) {
      frameworks.addAll("CoreFoundation", "Security")
      val exportedSymbolsFile = file("src/main/c/pkl_certs.exported_symbols")
      inputs.file(exportedSymbolsFile)
      linkerFlags.addAll("-exported_symbols_list", exportedSymbolsFile.absolutePath)
    } else {
      linkerFlags.add("Crypt32.lib")
    }
  }

// Use GraalVM's tracing agent to generate reachability metadata for foreign downcalls
val generateReachabilityMetadata =
  tasks.register<JavaExec>("generateReachabilityMetadata") {
    onlyIf { buildInfo.os.isMacOS || buildInfo.os.isLinux }
    // The native-image-agent requires GraalVM's java, not a regular JDK.
    val graalVm =
      if (buildInfo.arch == Target.Arch.AMD64) buildInfo.graalVmAmd64 else buildInfo.graalVmAarch64
    dependsOn(
      if (buildInfo.arch == Target.Arch.AARCH64) ":installGraalVmAarch64"
      else ":installGraalVmAmd64"
    )
    dependsOn(buildNativeLibrary)

    executable(
      File(graalVm.baseDir)
        .resolve("bin")
        .resolve(if (buildInfo.os.isWindows) "java.exe" else "java")
        .absolutePath
    )

    val outputDir =
      layout.buildDirectory.file("resources/main/META-INF/native-image/org.pkl-lang/pkl-system-certs/")
    val outputFile = outputDir.map { it.asFile.resolve("reachability-metadata.json") }
    outputs.file(outputFile)

    classpath = generateReachability.runtimeClasspath
    mainClass = "org.pkl.certs.generatereachability.Main"

    jvmArgumentProviders.add(
      CommandLineArgumentProvider {
        buildList {
          add(
            "-agentlib:native-image-agent=config-output-dir=${outputDir.get().asFile.absolutePath}"
          )
          add("--enable-native-access=ALL-UNNAMED")
        }
      }
    )

    doLast {
      if (!outputFile.get().exists()) {
        throw GradleException("Did not create expected file: ${outputFile.get()}")
      }
    }
  }

tasks.named("compileGenerateReachabilityJava") { dependsOn(buildNativeLibrary) }

tasks.assemble {
  dependsOn(buildNativeLibrary)
  dependsOn(generateReachabilityMetadata)
}

tasks.withType<JavaExec>().configureEach {
  jvmArgumentProviders.add(
    CommandLineArgumentProvider { listOf("--enable-native-access=ALL-UNNAMED") }
  )
}

spotless {
  cpp {
    licenseHeaderFile(
      rootProject.file("build-logic/src/main/resources/license-header.star-block.txt"),
      "// ",
    )
    target("src/*/c/*.c", "src/*/c/*.h")
    targetExclude("src/main/c/org_pkl_certs_NativeCertificateLoader.h")
    eclipseCdt(libs.versions.eclipseCdtFormat.get())
  }
}

publishing {
  publications {
    named<MavenPublication>("library") {
      pom {
        url.set("https://github.com/apple/pkl/tree/main/pkl-config-java")
        description.set("Java config library based on the Pkl config language.")
      }
    }
  }
}
