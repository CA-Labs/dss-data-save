package com.calabs.dss.datasave

import com.calabs.dss.dataimport.{Edge, Vertex, DataResourceMapper}
import com.calabs.dss.dataimport.Parsing.Tags
import org.json4s.jackson.Serialization
import org.json4s.jackson.Serialization.{read, write}
import scopt.OptionParser

import org.json4s._
import org.json4s.jackson.JsonMethods._

import scala.io.Source
import scala.util.{Failure, Success, Try}
import scala.collection.mutable.{Map => MutableMap}

/**
 * Created by Jordi Aranda
 * <jordi.aranda@bsc.es>
 * 9/1/15
 */

object DSSDataSave {

  case class Config(db: String, properties: String)

  case class InputData(vertices: JArray, edges: JArray)

  def loadDbProps(path: String) : Map[String,String] = {
    val sourceFile = Source.fromFile(path)
    val sourceContent = sourceFile.getLines().toList
    val props = MutableMap[String, String]()
    sourceContent.map(line => {
      val keyValue = line.split(Tags.KEY_VALUE_SEPARATOR)
      if (keyValue.length != 2) throw new IllegalArgumentException(s"Wrong key value $keyValue") else props.update(keyValue(0), keyValue(1))
    })
    props.toMap
  }

  def main(args: Array[String]) : Unit = {

    val parser = new OptionParser[Config]("dss-data-save") {
      head("DSS Data Save tool", "0.0.1")
      opt[String]('d', "database") required() action{ (x,c) =>
        c.copy(db = x)} text("Database backend (current supported implementations are: 'arangodb')")
      opt[String]('p', "properties") required() action{ (x,c) =>
        c.copy(properties = x)} text("Blueprints and concrete vendor properties for underlying chosen database storage")
    }

    implicit val formats = DefaultFormats

    parser.parse(args, Config("database", "properties")) map {
      config => {
        var jsonString = new StringBuilder
        Source.stdin.getLines.foreach(line => jsonString ++= line)
        val json = Try(read[InputData](jsonString.toString))
        val props = Try(loadDbProps(config.properties))
        (json, props) match {
          case (Success(j), Success(props)) => {
            config.db match {
              case Storage.Db.ArangoDB => {
                val arango = new ArangoDB(props)
                implicit val graph = arango.graph
                // Save vertices
                j.vertices.arr.foreach(vertex => vertex match {
                  case JObject(props) => arango.saveDocument(Vertex(props.toMap))
                  case _ => throw new IllegalArgumentException(s"Wrong vertex: $vertex")
                })
                // Save edges
                j.edges.arr.foreach(edge => edge match {
                  case JObject(props) => arango.saveDocument(Edge(props.toMap))
                  case _ => throw new IllegalArgumentException(s"Wrong edge: $edge")
                })
                // Close graph
                graph.shutdown
              }
              case Storage.Db.Neo4j => {
                val neo4j = new Neo4j(props)
                // Neo4j supports transactional graph
                implicit val graph = neo4j.graph
                // Save vertices
                j.vertices.arr.foreach(vertex => vertex match {
                  case JObject(props) => neo4j.saveDocument(Vertex(props.toMap))
                  case _ => throw new IllegalArgumentException(s"Wrong vertex: $vertex")
                })
                // Commit node changes
                graph.commit
                // Save edges
                j.edges.arr.foreach(edge => edge match {
                  case JObject(props) => neo4j.saveDocument(Edge(props.toMap))
                  case _ => throw new IllegalArgumentException(s"Wrong edge: $edge")
                })
                // Commit edge changes
                graph.commit
                // Close graph
                graph.shutdown
              }
              case Storage.Db.Titan => {
                val titan = new Titan(props)
                // Titan supports transactional graph
                implicit val graph = titan.graph
                // Save vertices
                j.vertices.arr.foreach(vertex => vertex match {
                  case JObject(props) => titan.saveDocument(Vertex(props.toMap))
                  case _ => throw new IllegalArgumentException(s"Wrong vertex: $vertex")
                })
                // Commit node changes
                graph.commit
                // Save edges
                j.edges.arr.foreach(edge => edge match {
                  case JObject(props) => titan.saveDocument(Edge(props.toMap))
                  case _ => throw new IllegalArgumentException(s"Wrong edge: $edge")
                })
                // Commit edge changes
                graph.commit
                // Close graph
                graph.shutdown
              }
              case unsupportedDb => Serialization.write(List(("error" -> true), ("reason" -> s"Unsupported database backend $unsupportedDb")).toMap)
            }
          }
          case (Failure(e), _) => Serialization.write(List(("exception" -> true), ("reason" -> s"Invalid input: ${e.getMessage}")).toMap)
          case (_, Failure(e)) => Serialization.write(List(("error" -> true), ("reason" -> s"Invalid database properties: ${e.getMessage}")).toMap)
        }
      }
    }

  }

}
