package com.mattg.util

import org.json4s._
import org.json4s.JsonDSL.WithDouble._
import org.json4s.native.JsonMethods._

object JsonHelper {
  implicit val formats = DefaultFormats

  // I don't really understand this implicit Manifest stuff, but stackoverflow told me to put it
  // there, and it worked.
  def extractWithDefault[T](params: JValue, key: String, default: T)(implicit m: Manifest[T]): T = {
    (params \ key) match {
      case JNothing => default
      case value => value.extract[T]
    }
  }

  def extractAsOption[T](params: JValue, key: String)(implicit m: Manifest[T]): Option[T] = {
    (params \ key) match {
      case JNothing => None
      case value => Some(value.extract[T])
    }
  }

  def extractChoice[T](params: JValue, key: String, choices: Seq[T])(implicit m: Manifest[T]): T = {
    checkChoice((params \ key).extract[T], choices)
  }

  def extractChoiceWithDefault[T](
    params: JValue,
    key: String,
    choices: Seq[T],
    default: T
  )(implicit m: Manifest[T]): T = {
    val parsed = extractWithDefault(params, key, default)
    checkChoice(parsed, choices)
  }

  def checkChoice[T](value: T, choices: Seq[T]) = {
    if (choices.contains(value))
      value
    else
      throw new IllegalStateException(s"parameter ${value} was not a member of ${choices}")
  }

  def getPathOrNameOrNull(params: JValue, key: String, baseDir: String, nameDir: String): String = {
    (params \ key) match {
      case JNothing => null
      case JString(path) if (path.startsWith("/")) => path
      case JString(name) => s"${baseDir}${nameDir}/${name}/"
      case other => throw new IllegalStateException(s"$key is not a string field")
    }
  }

  def getPathOrName(params: JValue, key: String, baseDir: String, nameDir: String): Option[String] = {
    (params \ key) match {
      case JNothing => None
      case JString(path) if (path.startsWith("/")) => Some(path)
      case JString(name) => Some(s"${baseDir}${nameDir}/${name}/")
      case other => throw new IllegalStateException(s"$key is not a string field")
    }
  }

  def ensureNoExtras(params: JValue, base: String, keys: Seq[String]) {
    params match {
      case JObject(fields) => {
        fields.map(field => {
          if (!keys.contains(field._1)) {
            val message = s"Malformed parameters under $base: unexpected key: ${field._1}"
            throw new IllegalStateException(message)
          }
        })
      }
      case other => {}
    }
  }
}
