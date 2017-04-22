package tlschannel.helpers

import java.nio.ByteBuffer
import java.security.MessageDigest

import scala.util.Random

import org.scalatest.Matchers

import com.typesafe.scalalogging.slf4j.StrictLogging

import tlschannel.helpers.TestUtil.Memo
import tlschannel.helpers.TestUtil.functionToRunnable

object Loops extends Matchers with StrictLogging {

  val seed = 143000953L

  /*
   * Note that it is necessary to use a multiple of 4 as buffer size for writing. 
   * This is because the bytes to write are generated using Random.nextBytes, that
   * always consumes full (4 byte) integers. A multiple of 4 then prevents "holes" 
   * in the random sequence.
   */
  val bufferSize = 4 * 5000

  val renegotiatePeriod = 10000
  val hashAlgorithm = "SHA-256"

  /**
   * Test a half-duplex interaction, with (optional) renegotiation 
   * before reversing the direction of the flow (as in HTTP)
   */
  def halfDuplex(socketPair: SocketPair, dataSize: Int, renegotiation: Boolean = false, scattering: Boolean = false) = {
    val clientWriterThread = new Thread(() => Loops.writerLoop(dataSize, socketPair.client, renegotiation, scattering), "client-writer")
    val serverWriterThread = new Thread(() => Loops.writerLoop(dataSize, socketPair.server, renegotiation, scattering), "server-writer")
    val clientReaderThread = new Thread(() => Loops.readerLoop(dataSize, socketPair.client, scattering), "client-reader")
    val serverReaderThread = new Thread(() => Loops.readerLoop(dataSize, socketPair.server, scattering), "server-reader")
    Seq(serverReaderThread, clientWriterThread).foreach(_.start())
    Seq(serverReaderThread, clientWriterThread).foreach(_.join())
    Seq(clientReaderThread, serverWriterThread).foreach(_.start())
    Seq(clientReaderThread, serverWriterThread).foreach(_.join())
    socketPair.server.external.close()
    socketPair.client.external.close()
  }

  def fullDuplex(socketPair: SocketPair, dataSize: Int) = {
    val clientWriterThread = new Thread(() => Loops.writerLoop(dataSize, socketPair.client), "client-writer")
    val serverWriterThread = new Thread(() => Loops.writerLoop(dataSize, socketPair.server), "server-write")
    val clientReaderThread = new Thread(() => Loops.readerLoop(dataSize, socketPair.client), "client-reader")
    val serverReaderThread = new Thread(() => Loops.readerLoop(dataSize, socketPair.server), "server-reader")
    Seq(serverReaderThread, clientWriterThread, clientReaderThread, serverWriterThread).foreach(_.start())
    Seq(serverReaderThread, clientWriterThread, clientReaderThread, serverWriterThread).foreach(_.join())
    socketPair.client.external.close()
    socketPair.server.external.close()
  }

  def writerLoop(
    size: Int,
    socketGroup: SocketGroup,
    renegotiate: Boolean = false,
    scattering: Boolean = false): Unit = TestUtil.cannotFail("Error in writer") {

    logger.debug(s"Starting writer loop, size: $size, scathering: $scattering, renegotiate:$renegotiate")
    val random = new Random(seed)
    var bytesSinceRenegotiation = 0
    var bytesRemaining = size
    val bufferArray = Array.ofDim[Byte](bufferSize)
    while (bytesRemaining > 0) {
      val buffer = ByteBuffer.wrap(bufferArray, 0, math.min(bufferSize, bytesRemaining))
      random.nextBytes(buffer.array)
      while (buffer.hasRemaining()) {
        if (renegotiate && bytesSinceRenegotiation > renegotiatePeriod) {
          socketGroup.tls.renegotiate()
          bytesSinceRenegotiation = 0
        }
        val c = if (scattering)
          socketGroup.tls.write(multiWrap(buffer)).toInt
        else
          socketGroup.external.write(buffer)
        assert(c > 0, "blocking write must return a positive number")
        bytesSinceRenegotiation += c
        bytesRemaining -= c.toInt
        assert(c > 0)
        assert(bytesRemaining >= 0)
      }
    }
    logger.debug("Finalizing writer loop")
  }

  def readerLoop(
    size: Int,
    socketGroup: SocketGroup,
    gathering: Boolean = false): Unit = TestUtil.cannotFail("Error in reader") {

    logger.debug(s"Starting reader loop. Size: $size, gathering: $gathering")
    val random = new Random(seed)
    val readArray = Array.ofDim[Byte](bufferSize)
    var bytesRemaining = size
    val digest = MessageDigest.getInstance(hashAlgorithm)
    while (bytesRemaining > 0) {
      val readBuffer = ByteBuffer.wrap(readArray, 0, math.min(bufferSize, bytesRemaining))
      val c = if (gathering)
        socketGroup.tls.read(multiWrap(readBuffer)).toInt
      else
        socketGroup.external.read(readBuffer)
      assert(c > 0, "blocking read must return a positive number")
      digest.update(readBuffer.array(), 0, readBuffer.position())
      bytesRemaining -= c
      assert(bytesRemaining >= 0)
    }
    val actual = digest.digest()
    assert(actual === expectedBytesHash(size))
    logger.debug("Finalizing reader loop")
  }

  val expectedBytesHash = Memo { (size: Int) =>
    val digest = MessageDigest.getInstance(hashAlgorithm)
    val random = new Random(seed)
    var generated = 0
    val bufferSize = 4 * 1024
    val array = Array.ofDim[Byte](bufferSize)
    while (generated < size) {
      random.nextBytes(array)
      val pending = size - generated
      digest.update(array, 0, math.min(bufferSize, pending))
      generated += bufferSize
    }
    digest.digest()
  }

  private def multiWrap(buffer: ByteBuffer) = {
    Array(ByteBuffer.allocate(0), buffer, ByteBuffer.allocate(0))
  }

  private def remaining(buffers: Array[ByteBuffer]) = {
    buffers.map(_.remaining.toLong).sum
  }

}