package be.unamur.anecdotfun

import spray.json.{DefaultJsonProtocol, RootJsonFormat}

case class CommandResponse(uniqueId: String, commandType: String, status: String, message: Option[String] = None)

object CommandResponseJsonProtocol extends DefaultJsonProtocol {
  implicit val commandResponseFormat: RootJsonFormat[CommandResponse] = jsonFormat4(CommandResponse.apply)
}