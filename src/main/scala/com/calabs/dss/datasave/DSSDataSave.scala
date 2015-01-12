package com.calabs.dss.datasave

import org.json4s.jackson.Serialization
import scopt.OptionParser

import org.json4s._
import org.json4s.jackson.JsonMethods._

import scala.util.{Failure, Success, Try}

/**
 * Created by Jordi Aranda
 * <jordi.aranda@bsc.es>
 * 9/1/15
 */

object DSSDataSave {

  case class Config(db: String, properties: String)

  def main(args: Array[String]) : Unit = {

    val parser = new OptionParser[Config]("dss-data-save") {
      head("DSS Data Save tool", "0.0.1")
      opt[String]('d', "database") required() action{ (x,c) =>
        c.copy(db = x)} text("Database backend (current supported implementations are 'neo4j', 'titan' and 'arangodb')")
      opt[String]('p', "properties") required() action{ (x,c) =>
        c.copy(properties = x)} text("Blueprints and concrete vendor properties for underlying chosen database storage")
    }

    implicit val formats = DefaultFormats

    parser.parse(args, Config("database", "properties")) map {
      config => {
        var jsonString = new StringBuilder
        io.Source.stdin.getLines.foreach(line => jsonString ++= line)
        val json = Try(parse(jsonString.toString).extract[Map[String, Any]])
        val result = json match {
          case Success(j) => {
            println(j)
            Serialization.write(List("status" -> "ok").toMap)
          }
          case Failure(e) => Serialization.write((List(("exception" -> true), ("reason" -> e.getMessage)).toMap))
        }
        println(result)
      }
    }

  }

}
