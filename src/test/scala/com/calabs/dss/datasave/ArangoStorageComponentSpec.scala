package com.calabs.dss.datasave

import com.calabs.dss.dataimport.Parsing.Tags
import com.calabs.dss.dataimport.{Edge, Vertex}
import com.calabs.dss.datasave.DSSDataSave.InputData
import org.json4s.DefaultFormats
import org.json4s.JsonAST.JObject
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfter, FunSpec}
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
                    "blueprints.arangodb.edgesCollectionName" -> "testEdges")
  val koProps = Map[String,String]()
  val arangoDbClient = new ArangoDB(okProps)
  // Seems implicit graph declaration is not found within ArangoDB class, make it explicit
  implicit val graph = arangoDbClient.graph
}

class ArangoStorageComponentSpec extends FunSpec with BeforeAndAfter with BeforeAndAfterAll with ArangoDBConfig {

  val jsonStringInsert = Source.fromFile(getClass.getResource("/arangodb/basic-insert.json").getPath).mkString
//  val jsonStringInsert = Source.fromFile(getClass.getResource("/github.json").getPath).mkString
  val jsonStringUpdate = Source.fromFile(getClass.getResource("/arangodb/basic-update.json").getPath).mkString
  implicit val formats = DefaultFormats
  val inputInsert = read[InputData](jsonStringInsert)
  val inputUpdate = read[InputData](jsonStringUpdate)

  before {
    graph.getVertices.asScala.foreach(node => graph.removeVertex(node))
    graph.getEdges.asScala.foreach(edge => graph.removeEdge(edge))

    // Populate the database
    inputInsert.vertices.arr.foreach(vertex => vertex match {
      case JObject(props) => arangoDbClient.saveDocument(Vertex(props.toMap))
      case _ => fail(s"Invalid vertex $vertex")
    })
    inputInsert.edges.arr.foreach(edge => edge match {
      case JObject(props) => arangoDbClient.saveDocument(Edge(props.toMap))
      case _ => fail(s"Invalid edge $edge")
    })
  }

  override def afterAll() : Unit = {
    graph.shutdown()
  }

  describe("Arango Storage Component"){

    it("should be able to detect missing graph properties"){
      intercept[IllegalArgumentException]{
        new ArangoDB(koProps)
      }
    }

    it("should be able to save nodes and edges"){
      val storedVertices = graph.getVertices.iterator.toList
      val storedEdges = graph.getEdges.iterator.toList
      assert(storedVertices.length == 2 && storedEdges.length == 1)

      val nodeA = graph.query().has("name", "A").vertices().toList
      assert(nodeA.length == 1 && nodeA(0).getProperty[Int]("value") == 1)
      val nodeB = graph.query().has("name", "B").vertices().toList
      assert(nodeB.length == 1 && nodeB(0).getProperty[Int]("value") == 2)
      val edgeC = graph.query().has("name", "C").edges().toList
      assert(edgeC.length == 1)
    }

    it("should be able to update nodes and edges"){
      val storedVertices = graph.getVertices.iterator.toList
      val storedEdges = graph.getEdges.iterator.toList
      assert(storedVertices.length == 2 && storedEdges.length == 1)

      // Grab references to vertices/edges ids
      val nodeA = graph.query().has("name", "A").vertices().toList
      assert(nodeA.length == 1 && nodeA(0).getProperty[Int]("value") == 1)
      val nodeB = graph.query().has("name", "B").vertices().toList
      assert(nodeB.length == 1 && nodeB(0).getProperty[Int]("value") == 2)
      val edgeC = graph.query().has("name", "C").edges().toList
      assert(edgeC.length == 1)

      // Do update
      inputUpdate.vertices.arr.foreach(vertex => vertex match {
        case JObject(props) => arangoDbClient.saveDocument(Vertex(props.toMap))
        case _ => fail(s"Invalid vertex $vertex")
      })
      inputUpdate.edges.arr.foreach(edge => edge match {
        case JObject(props) => arangoDbClient.saveDocument(Edge(props.toMap))
        case _ => fail(s"Invalid edge $edge")
      })

      // Check new vertices/edges values
      val newNodeA = graph.getVertex(nodeA(0).getId)
      assert(newNodeA.getProperty[String]("name") == "newA")
      val newNodeB = graph.getVertex(nodeB(0).getId)
      assert(newNodeB.getProperty[String]("name") == "newB")
      val newEdgeC = graph.getEdge(edgeC(0).getId)
      assert(newEdgeC.getProperty[String]("name") == "newC")
    }

  }

}
