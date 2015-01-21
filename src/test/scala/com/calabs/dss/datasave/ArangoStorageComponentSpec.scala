package com.calabs.dss.datasave

import com.calabs.dss.dataimport.Parsing.Tags
import com.calabs.dss.dataimport.{Edge, Vertex}
import com.calabs.dss.datasave.DSSDataSave.InputData
import org.json4s.DefaultFormats
import org.json4s.JsonAST.JObject
import org.scalatest.{BeforeAndAfter, FunSpec}
import collection.JavaConverters._
import scala.io.Source

import scala.collection.JavaConversions._

import org.json4s.jackson.Serialization.{read, write}

/**
 * Created by Jordi Aranda
 * <jordi.aranda@bsc.es>
 * 9/1/15
 */

trait ArangoDBConfig {
  val okProps = Map("blueprints.arangodb.host" -> "localhost",
                    "blueprints.arangodb.port" -> "8529",
                    "blueprints.arangodb.db" -> "test",
                    "blueprints.arangodb.name" -> "testGraph",
                    "blueprints.arangodb.verticesCollectionName" -> "testVertices",
                    "blueprints.arangodb.edgesCollectionName" -> "edgesTest")
  val koProps = Map[String,String]()
  val arangoDbClient = new ArangoDB(okProps)
  // Seems implicit graph declaration is not found within ArangoDB class, make it explicit
  implicit val graph = arangoDbClient.graph
}

class ArangoStorageComponentSpec extends FunSpec with BeforeAndAfter with ArangoDBConfig {

  val jsonString = Source.fromFile(getClass.getResource("/basic.json").getPath).mkString
  implicit val formats = DefaultFormats
  val input = read[InputData](jsonString)

  before {
    graph.getVertices.asScala.foreach(node => graph.removeVertex(node))
    graph.getEdges.asScala.foreach(edge => graph.removeEdge(edge))
  }

  describe("Arango Storage Component"){

    it("should be able to detect missing graph properties"){
      intercept[IllegalArgumentException]{
        new ArangoDB(koProps)
      }
    }

    it("should be able to save nodes and edges"){

      input.vertices.arr.foreach(vertex => vertex match {
        case JObject(props) => arangoDbClient.saveDocument(Vertex(props.toMap))
        case _ => fail(s"Invalid vertex $vertex")
      })
      input.edges.arr.foreach(edge => edge match {
        case JObject(props) => arangoDbClient.saveDocument(Edge(props.toMap))
        case _ => fail(s"Invalid edge $edge")
      })

      val storedVertices = graph.getVertices.iterator.toList
      val storedEdges = graph.getEdges.iterator.toList
      assert(storedVertices.length == 2 && storedEdges.length == 1)

    }

  }

}
