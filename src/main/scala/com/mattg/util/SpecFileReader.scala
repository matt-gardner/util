package com.mattg.util

import scala.collection.JavaConverters._

import org.json4s._
import org.json4s.JsonDSL.WithDouble._
import org.json4s.native.JsonMethods._

class SpecFileReader(baseDir: String, fileUtil: FileUtil = new FileUtil()) {
  implicit val formats = DefaultFormats

  def readSpecFile(file: java.io.File): JValue = {
    readSpecFile(fileUtil.readLinesFromFile(file))
  }

  def readSpecFile(filename: String): JValue = {
    readSpecFile(fileUtil.readLinesFromFile(filename))
  }

  def readSpecFile(lines: Seq[String]): JValue = {
    val empty_params = new JObject(Nil)
    val params = populateParamsFromSpecs(lines, empty_params)
    removeDeletes(doNestedLoads(params))
  }

  // This handles load statements at the beginning of a file, however many there are.
  def populateParamsFromSpecs(specs: Seq[String], params: JValue): JValue = {
    if (specs(0).startsWith("load")) {
      return readSpecFile(getParamFileFromLoadStatement(specs(0))) merge populateParamsFromSpecs(
        specs.drop(1), params)
    } else {
      params merge parse(specs.mkString(" "))
    }
  }

  // This handles load statements that are given as values in the json (i.e. "graph": "load file").
  def doNestedLoads(params: JValue): JValue = {
    params mapField {
      case (name, JString(load)) if (load.startsWith("load ")) => {
        (name, readSpecFile(getParamFileFromLoadStatement(load)))
      }
      case (name, JArray(list)) => {
        val new_list = list.map(_ match {
          case JString(load) if (load.startsWith("load ")) => {
            readSpecFile(getParamFileFromLoadStatement(load))
          }
          case other => other
        })
        (name, new_list)
      }
      case other => other
    }
  }

  def removeDeletes(params: JValue): JValue = {
    params.removeField(field => field._2 == JString("delete"))
  }

  def getParamFileFromLoadStatement(load: String) = {
    val name = load.split(" ")(1)
    if (name.startsWith("/")) {
      name
    } else {
      s"${baseDir}param_files/${name}.json"
    }
  }
}
