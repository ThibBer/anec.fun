package be.unamur.anecdotfun

import akka.NotUsed
import akka.stream.{IOResult, OverflowStrategy, QueueOfferResult}
import akka.stream.scaladsl.{Flow, Keep, Sink, Source, SourceQueueWithComplete, StreamConverters}
import akka.util.ByteString
import com.fazecast.jSerialComm.SerialPortInvalidPortException

import java.io.{BufferedInputStream, BufferedOutputStream}
import scala.concurrent.Future
import scala.util.{Failure, Success}
import system.dispatcher

import scala.concurrent.duration.FiniteDuration


class Microphone(serial: SerialThread) {
  private var queue: Option[SourceQueueWithComplete[ByteString]] = None
  private var source: Option[Source[ByteString, NotUsed]] = None

  private def handleVoiceFlowData(bytes: Array[Byte]): Unit = {
    queue.foreach { q =>
      q.offer(ByteString(bytes)).onComplete {
        case Success(QueueOfferResult.Enqueued) =>
        case Success(QueueOfferResult.Dropped) =>
          println("Dropped")
        case Success(QueueOfferResult.Failure(e)) =>
          println("Failure")
          e.printStackTrace()
        case Success(QueueOfferResult.QueueClosed) =>
          println("Queue closed")
        case Failure(e) =>
          println("Failure")
          e.printStackTrace()
      }
    }
    if (isEndOfSequence(bytes)) {
      serial.stopVoiceFlow()
      queue.foreach(_.complete())
    }
  }

  def startListening[T](to: Sink[ByteString, Future[T]], duration: FiniteDuration): Option[Future[T]] = {
    try {
      val (q, s) = Source.queue[ByteString](bufferSize = 1024, overflowStrategy = OverflowStrategy.dropTail)
        .watchTermination() { (mat, future) =>
          println("Stream started")
          future.onComplete {
            case Success(_) => println("Stream completed successfully")
            case Failure(e) => println(s"Stream failed with error: $e")
          }
          mat
        }
        .preMaterialize()

      queue = Some(q)
      source = Some(s)

      serial.onReceiveVoiceSerialData = handleVoiceFlowData
      serial.startVoiceFlow(duration)
      val graph = s
        .conflate((a, b) => a ++ b)
        .via(convertSound())
        .runWith(to)
      Some(graph)
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
      "-ar",
      "16k",
      "-f",
      "wav",
      //      "--blocksize",
      //      "256",
      "pipe:1").start


    val inputStream = new BufferedInputStream(process.getInputStream)
    val outputStream = new BufferedOutputStream(process.getOutputStream)

    val source = StreamConverters.fromInputStream(() => inputStream)
    val sink = StreamConverters.fromOutputStream(() => outputStream)
    Flow.fromSinkAndSourceMat(sink, source)(Keep.left)
  }

  def close(): Unit = {
    serial.stopVoiceFlow()
  }

  private def isEndOfSequence(buffer: Array[Byte]): Boolean = {
    val eos = Array(127, -128, 127, -128, 127, -128, 127, -128).map(_.toByte)
    buffer.sliding(eos.length).exists(_.sameElements(eos))
  }
}
