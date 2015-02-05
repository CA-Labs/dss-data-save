package com.calabs.dss.datasave

import com.calabs.dss.dataimport.{Edge, Vertex}
import com.calabs.dss.datasave.DSSDataSave.InputData
import org.json4s.DefaultFormats
import org.json4s.JsonAST.JObject
import org.json4s.jackson.Serialization._
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfter, FunSpec}

import collection.JavaConverters._
import collection.JavaConversions._
import scala.io.Source

import org.json4s.jackson.Serialization.{read, write}

import scala.util.{Success, Failure, Try}
import scala.Nothing

/**
 * Created by Jordi Aranda
 * <jordi.aranda@bsc.es>
 * 9/1/15
 */

trait Neo4jConfig {
  val okProps = Map[String, String](("blueprints.neo4j.directory" -> "/tmp/neo4j"), ("blueprints.neo4j.conf.allow_store_upgrade" -> "true"))
  val koProps = Map[String,String]()
  val neo4jClient = new Neo4j(okProps)
  implicit val graph = neo4jClient.graph
}

class Neo4jStorageComponentSpec extends FunSpec with BeforeAndAfter with BeforeAndAfterAll with Neo4jConfig {

  val jsonStringInsert = Source.fromFile(getClass.getResource("/neo4j/basic-insert.json").getPath).mkString
  val jsonStringUpdate = Source.fromFile(getClass.getResource("/neo4j/basic-update.json").getPath).mkString
  implicit val formats = DefaultFormats
  val inputInsert = read[InputData](jsonStringInsert)
  val inputUpdate = read[InputData](jsonStringUpdate)

  before {
    graph.getVertices.asScala.foreach(node => graph.removeVertex(node))
    graph.getEdges.asScala.foreach(edge => graph.removeEdge(edge))

    // Populate the database
    inputInsert.vertices.arr.foreach(vertex => vertex match {
      case JObject(props) => {
        val saved = Try(neo4jClient.saveDocument(Vertex(props.toMap)))
        saved match {
          case Failure(_) => {
            graph.rollback()
            fail("Error occurred when saving a vertex, rolling back...")
          }
          case Success(_) => {} // Nothing to be done, scalish way to do that?
        }
      }
      case _ => {
        graph.rollback()
        fail(s"Invalid vertex $vertex")
      }
    })
    // Commit new nodes
    graph.commit()

    inputInsert.edges.arr.foreach(edge => edge match {
      case JObject(props) => {
        val saved = Try(neo4jClient.saveDocument(Edge(props.toMap)))
        saved match {
          case Failure(_) => {
            graph.rollback()
            fail("Error occurred when saving edges, rolling back...")
          }
          case Success(_) => {} // Nothing to be done, scalish way to do that?
        }
      }
      case _ => {
        graph.rollback()
        fail(s"Invalid edge $edge, rolling back...")
      }
    })
    // Commit new edges
    graph.commit()
  }

  override def afterAll(): Unit = {
    graph.shutdown()
  }

  describe("Neo4j Storage Component") {

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
        case JObject(props) => neo4jClient.saveDocument(Vertex(props.toMap))
        case _ => fail(s"Invalid vertex $vertex")
      })
      inputUpdate.edges.arr.foreach(edge => edge match {
        case JObject(props) => neo4jClient.saveDocument(Edge(props.toMap))
        case _ => fail(s"Invalid edge $edge")
      })

      // Check new vertices/edges values
      val newNodeA = graph.getVertex(nodeA(0).getId)
      assert(newNodeA.getProperty[String]("name") == "newA")
      val newNodeB = graph.getVertex(nodeB(0).getId)
      assert(newNodeB.getProperty[String]("name") == "newB")
      val newEdgeC = graph.getEdge(edgeC(0).getId)
      assert(newEdgeC.getProperty[String]("name") == "newC")
      assert(newEdgeC.getLabel == "has")
    }

  }

}
