package com.calabs.dss.datasave

import org.scalatest.{BeforeAndAfter, FunSpec}
import collection.JavaConverters._

/**
 * Created by Jordi Aranda
 * <jordi.aranda@bsc.es>
 * 9/1/15
 */

trait ArangoDBConfig {
  val okProps = Map("blueprints.arangodb.host" -> "localhost",
                    "blueprints.arangodb.port" -> "8529",
                    "blueprints.arangodb.name" -> "testGraph",
                    "blueprints.arangodb.verticesCollectionName" -> "testVertices",
                    "blueprints.arangodb.edgesCollectionName" -> "edgesTest")
  val koProps = Map[String,String]()
  val arangoDbClient = new ArangoDB(okProps)
  // Seems implicit graph declaration is not found within ArangoDB class, make it explicit
  implicit val graph = arangoDbClient.graph
}

class ArangoStorageComponentSpec extends FunSpec with BeforeAndAfter with ArangoDBConfig {

  val nodes = Map(("a" -> 1), ("b" -> 2))

  describe("Arango Storage Component"){

    it ("should be able to load a graph from graph properties"){
      intercept[IllegalArgumentException] {
        val arangoDbClient = new ArangoDB(koProps)
      }
    }

    it ("should be able to load a graph from graph properties and store nodes"){
      arangoDbClient.saveMetrics(nodes, DocumentType.NODE)
      assert(graph.getVertices.asScala.size == nodes.size)
    }

  }

}
