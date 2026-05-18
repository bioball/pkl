package org.pkl.cli.repl

import java.io.OutputStream
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Captures JLine terminal output as discrete chunks, one per newline.
 *
 * Lines that are part of [ReplMessages.welcome] (plus the trailing blank line) are silently
 * dropped by skipping the first N lines of output.
 */
class ReplOutput {
  private val buffer = StringBuilder()
  private val queue = LinkedBlockingQueue<String>()

  val stream: OutputStream =
    object : OutputStream() {
      override fun write(b: Int) {
        buffer.append(b.toChar())
        if (buffer.toString() == "pkl0> ") {
          // discard prompt
          buffer.clear()
          queue.put("")
        }
        if (b.toChar() == '\n') enqueue()
      }

      override fun write(b: ByteArray, off: Int, len: Int) {
        buffer.append(String(b, off, len, Charsets.UTF_8))
        if (buffer.contains('\n')) enqueue()
      }

      override fun flush() {}

      private fun enqueue() {
        val chunk = buffer.toString()
        buffer.clear()
        queue.put(chunk)
      }
    }

  /** Blocks until the next output chunk arrives, or returns null if [timeout] elapses. */
  fun next(timeout: Long = 5, unit: TimeUnit = TimeUnit.SECONDS): String? =
    queue.take()

  /** Returns all chunks currently in the queue without blocking. */
  fun drain(): List<String> = buildList { queue.drainTo(this) }
}
