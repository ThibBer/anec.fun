package be.unamur.anecdotfun

import akka.NotUsed
import akka.stream.{IOResult, OverflowStrategy, QueueOfferResult}
import akka.stream.scaladsl.{FileIO, Flow, Keep, RunnableGraph, Sink, Source, StreamConverters}
import akka.util.ByteString
import com.fazecast.jSerialComm.{SerialPort, SerialPortInvalidPortException}

import java.io.{BufferedInputStream, BufferedOutputStream}
import java.nio.file.Paths
import scala.concurrent.Future
import scala.util.{Failure, Success}
import system.dispatcher


class Microphone {
  private val portName = "COM4"
  private var comPort: Option[SerialPort] = None

  def startListening(): Option[RunnableGraph[NotUsed]] = {
    try {
      val (queue, source) = Source.queue[ByteString](bufferSize = 1024, overflowStrategy = OverflowStrategy.dropHead)
        .preMaterialize()
      val port = SerialPort.getCommPorts.find(_.getSystemPortName == portName).getOrElse {
        throw new IllegalStateException(s"Could not find $portName port")
      }
      if (!port.openPort(1000)) {
        println("Port is not opened")
        return None
      }
      port.setBaudRate(115200)
      val readerThread = new Thread(() => {
        port.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 0, 0)
        val stream = port.getInputStream
        val buffer = new Array[Byte](1024)
        var bytesRead = 0
        try {
          while ( {
            bytesRead = stream.read(buffer)
            bytesRead
          } != -1) {
            // Potentially a bug somewhere but the stream reads 1 then 31 bytes instead of 32.
            if (bytesRead > 1) {
              queue.offer(ByteString(buffer.take(bytesRead))) onComplete {
                case Success(QueueOfferResult.Enqueued) => println(s"Enqueued: $bytesRead")
                case Success(QueueOfferResult.Dropped) => println(s"Dropped: $bytesRead")
                case Success(QueueOfferResult.Failure(ex)) => println(s"Failed to enqueue: $bytesRead, reason: $ex")
                case Success(QueueOfferResult.QueueClosed) => println(s"Queue closed: $bytesRead")
                case Failure(ex) => println(s"Offer failed: $ex")
              }
            }
          }
          println("end while")
        } catch {
          case e: Exception =>
            e.printStackTrace()
        } finally {
          println("closed")
          stream.close()
        }
      })

      println(s"Port opened, reading serial on $port")
      comPort = Some(port)
      readerThread.start()
      Some(
        source
          .via(convertSound())
          .to(FileIO.toPath(Paths.get("test.wav")))
      )
    } catch {
      case e: SerialPortInvalidPortException =>
        println("Wrong port")
        None
      case e: Exception =>
        e.printStackTrace()
        None
    }


  }

  private def convertSound(): Flow[ByteString, ByteString, Future[IOResult]] = {
    val process = ProcessBuilder("ffmpeg",
      "-f",
      "s8",
      "-ar",
      "6k",
      "-ac",
      "1",
      "-i",
      "pipe:0",
      "-ab",
      "16k",
      "-f",
      "s16le",
      "pipe:1").start


    val inputStream = new BufferedInputStream(process.getInputStream)
    val outputStream = new BufferedOutputStream(process.getOutputStream)

    val source = StreamConverters.fromInputStream(() => inputStream)
    val sink = StreamConverters.fromOutputStream(() => outputStream)
    Flow.fromSinkAndSourceMat(sink, source)(Keep.left)
  }


  def close(): Unit = comPort match
    case Some(port) => port.closePort()
    case None => println("Not opened")
}
