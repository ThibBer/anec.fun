package com.anecdot.support

import spray.json._

trait JsonSupport extends DefaultJsonProtocol {
  implicit val mutableMapFormat: RootJsonFormat[scala.collection.mutable.Map[String, Int]] =
    new RootJsonFormat[scala.collection.mutable.Map[String, Int]] {
      override def write(map: scala.collection.mutable.Map[String, Int]): JsValue =
        JsObject(map.map { case (k, v) => k -> JsNumber(v) }.toMap)

      override def read(json: JsValue): scala.collection.mutable.Map[String, Int] = json match {
        case JsObject(fields) =>
          scala.collection.mutable.Map(fields.map { case (k, v) =>
            k -> v.convertTo[Int]
          }.toSeq: _*)
        case _ => deserializationError("Expected a JSON object")
      }
    }
}