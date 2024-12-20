package com.anecdot

import akka.NotUsed
import akka.actor.typed.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ContentType
import akka.http.scaladsl.model.ContentTypes
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.HttpMethods
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.MediaTypes
import akka.http.scaladsl.model.Multipart.FormData
import akka.http.scaladsl.model.headers
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Source
import akka.stream.scaladsl.StreamConverters
import akka.util.ByteString
import com.azure.ai.openai.OpenAIAsyncClient
import com.azure.ai.openai.OpenAIClientBuilder
import com.azure.ai.openai.models.ChatCompletionsJsonResponseFormat
import com.azure.ai.openai.models.ChatCompletionsOptions
import com.azure.ai.openai.models.ChatRequestMessage
import com.azure.ai.openai.models.ChatRequestSystemMessage
import com.azure.ai.openai.models.ChatRequestUserMessage
import com.azure.core.credential.AzureKeyCredential
import spray.json.DefaultJsonProtocol._
import spray.json._

import java.io.ByteArrayInputStream
import java.util
import java.util.Base64
import scala.collection.immutable
import scala.compat.java8.FutureConverters._
import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.Future

case class CombinedPhrases(
    text: String
)

case class Phrases(
    offsetMilliseconds: Int,
    durationMilliseconds: Int,
    text: String,
    words: Seq[Words],
    locale: String,
    confidence: Double
)

case class STTResponse(
    durationMilliseconds: Int,
    combinedPhrases: Seq[CombinedPhrases],
    phrases: Seq[Phrases]
)

case class Words(
    text: String,
    offsetMilliseconds: Int,
    durationMilliseconds: Int
)

object JsonFormats {
  implicit val wordsFormat: JsonFormat[Words] = jsonFormat3(Words.apply)
  implicit val combinedPhrasesFormat: JsonFormat[CombinedPhrases] = jsonFormat1(
    CombinedPhrases.apply
  )
  implicit val phrasesFormat: JsonFormat[Phrases] = jsonFormat6(Phrases.apply)
  implicit val sttResponseFormat: JsonFormat[STTResponse] = jsonFormat3(
    STTResponse.apply
  )
}

class SpeechToText(implicit
    val system: ActorSystem[Nothing],
    val materializer: Materializer
) {

  import JsonFormats._

  implicit val executionContext: ExecutionContextExecutor =
    system.executionContext
  private val azureAiKey = Option(System.getenv("AZURE_AI_KEY")).getOrElse("default_azure_ai_key")
  private val azureAiRegion = Option(System.getenv("AZURE_AI_REGION")).getOrElse("default_azure_ai_region")
  private val openAiEndpoint = Option(System.getenv("OPENAI_ENDPOINT")).getOrElse("default_openai_endpoint")
  private val modelName = Option(System.getenv("OPENAI_MODEL_NAME")).getOrElse("gpt-4o-mini")
  private val openAiClient: OpenAIAsyncClient = new OpenAIClientBuilder()
    .credential(new AzureKeyCredential(azureAiKey))
    .endpoint(openAiEndpoint)
    .buildAsyncClient()

  private var accumulatedPayloads = ByteString.empty

  def addPayload(payload: String): Unit = {
    val decodedBytes = Base64.getDecoder.decode(payload)

    accumulatedPayloads ++= ByteString(decodedBytes)
  }

  def recognize(
  ): Source[String, NotUsed] = {
    println(s"Payloads size: ${accumulatedPayloads.length}")
    val audioSource: Source[ByteString, NotUsed] = Source
      .single(accumulatedPayloads)
      .flatMapConcat { bytes =>
        val audioStream = new ByteArrayInputStream(bytes.toArray)
        StreamConverters.fromInputStream(() => audioStream)
      }

    val formData = FormData(
      FormData.BodyPart(
        "definition",
        HttpEntity(ContentTypes.`application/json`, """{"locales":["fr-FR"]}""")
      ),
      FormData.BodyPart(
        "audio",
        HttpEntity.IndefiniteLength(
          ContentType.Binary(MediaTypes.`audio/wav`),
          audioSource
        ),
        Map("filename" -> "audio.wav")
      )
    )

    val request = HttpRequest(
      method = HttpMethods.POST,
      uri =
        s"https://$azureAiRegion.api.cognitive.microsoft.com/speechtotext/transcriptions:transcribe?api-version=2024-11-15",
      headers =
        List(headers.RawHeader("Ocp-Apim-Subscription-Key", azureAiKey)),
      entity = formData.toEntity
    )

    val responseFuture: Future[String] = Http()
      .singleRequest(request)
      .flatMap { response =>
        if (response.status.isSuccess) {
          Unmarshal(response.entity).to[String].map { body =>
            val response = body.parseJson.convertTo[STTResponse]
            response.combinedPhrases.map(_.text).mkString(" ")
          }
        } else {
          Future.failed(
            new RuntimeException(
              s"Request failed with status ${response.status}"
            )
          )
        }
      }
      .recover { case exception =>
        println(s"Request failed: $exception")
        ""
      }

    Source.future(responseFuture)
  }

  def resetPayloads(): Unit = {
    accumulatedPayloads = ByteString.empty
  }

  def detectIntent(intents: Array[String]): Flow[String, String, ?] = {
    val intentsList = intents.mkString(", ")
    val systemPrompt = new ChatRequestSystemMessage(
      s"Tu es un assistant qui classe des histoires suivant plusieurs catégories: $intentsList et \"autre\". Tu vas répondre en json la catégorie qui correspond le mieux à l'histoire avec le format {\"value\": \"nom\"}"
    )
    Flow[String].mapAsync(1) { text =>
      {
        println("text= " + text)
        val userMessage = new ChatRequestUserMessage(text)

        val messages = util.ArrayList[ChatRequestMessage]()
        messages.add(systemPrompt)
        messages.add(userMessage)
        openAiClient
          .getChatCompletions(
            modelName,
            new ChatCompletionsOptions(messages).setResponseFormat(
              ChatCompletionsJsonResponseFormat()
            )
          )
          .map(chatCompletion => {
            val intent = chatCompletion.getChoices.get(0).getMessage.getContent
            println("intent detected= " + intent)
            intent
          })
          .toFuture
          .toScala

      }
    }
  }

}
