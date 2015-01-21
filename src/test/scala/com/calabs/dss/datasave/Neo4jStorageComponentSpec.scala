package com.calabs.dss.datasave

import org.scalatest.{BeforeAndAfter, FunSpec}
import collection.JavaConverters._

/**
 * Created by Jordi Aranda
 * <jordi.aranda@bsc.es>
 * 9/1/15
 */

trait Neo4jConfig {
  val okProps = Map(("blueprints.neo4j.directory" -> "/tmp/neo4j"))
  val koProps = Map[String,String]()
  val neo4jClient = new Neo4j(okProps)
  // Seems implicit graph declaration is not found within Neo4j class, make it explicit
  implicit val graph = neo4jClient.graph
}

class Neo4jStorageComponentSpec extends FunSpec with BeforeAndAfter with Neo4jConfig {

  before {
    graph.getVertices.asScala.foreach(node => graph.removeVertex(node))
    graph.getEdges.asScala.foreach(edge => graph.removeEdge(edge))
  }

  describe("Neo4j Storage Component") {

    ignore("should be able to load a graph from graph properties"){}

    ignore("should be able to save nodes and edges"){}

  }

}
