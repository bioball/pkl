package org.pkl.cli.repl

import org.jline.terminal.TerminalBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.pkl.cli.writeLine
import org.pkl.cli.writeText
import org.pkl.commons.toPath
import org.pkl.core.Loggers
import org.pkl.core.SecurityManagers
import org.pkl.core.StackFrameTransformers
import org.pkl.core.evaluatorSettings.TraceMode
import org.pkl.core.http.HttpClient
import org.pkl.core.module.ModuleKeyFactories
import org.pkl.core.repl.ReplServer
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.nio.file.Path

class ReplTest {

  private val server =
    ReplServer(
      SecurityManagers.defaultManager,
      HttpClient.dummyClient(),
      Loggers.stdErr(),
      listOf(ModuleKeyFactories.standardLibrary),
      listOf(),
      mapOf(),
      mapOf(),
      null,
      null,
      null,
      "/".toPath(),
      StackFrameTransformers.defaultTransformer,
      false,
      TraceMode.COMPACT,
    )

  @Test
  fun `test completion`(@TempDir tempDir: Path) {
    val input = PipedOutputStream()
    val output = ReplOutput()
    val terminal = TerminalBuilder.builder()
      .streams(PipedInputStream(input), output.stream)
      .build()
    val repl = Repl(
      tempDir,
      server,
      color = false,
      terminal = terminal,
      printWelcome = false
    )
    val thread = Thread { repl.run() }
    thread.start()
    input.writeText("output.")
    input.write(byteArrayOf(0x09)) // tab for completion
    input.flush()
    println(output.next())
    println(output.next())
  }
}
