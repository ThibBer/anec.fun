package com.anecdot

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import com.anecdot.Main.jsonFormat1
import spray.json.DefaultJsonProtocol.StringJsonFormat
import spray.json.RootJsonFormat

case class Category(value: String)

object CategoryJsonProtocol extends SprayJsonSupport {
  implicit val categoryFormat: RootJsonFormat[Category] = jsonFormat1(Category.apply)
}