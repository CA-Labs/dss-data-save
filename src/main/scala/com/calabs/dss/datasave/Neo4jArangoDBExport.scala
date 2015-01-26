package com.calabs.dss.datasave

import com.tinkerpop.blueprints.Direction
import com.tinkerpop.blueprints.impls.arangodb.ArangoDBVertex
import com.tinkerpop.blueprints.impls.neo4j2.Neo4j2Vertex

import collection.JavaConversions._

/**
 * Created by Jordi Aranda
 * <jordi.aranda@bsc.es>
 * 1/26/15
 */

object Neo4jArangoDBExport {

  def main(args: Array[String]) : Unit = {

    // Neo4j migration to ArangoDB
    val neo4jProps = Map("blueprints.neo4j.directory" -> "/Users/jaranda/neo4j/drwho/drwho")
    val neo4jClient = new Neo4j(neo4jProps)
    val neo4jGraph = neo4jClient.graph

    val neo4jVertices = neo4jGraph.getVertices.toList
    println(s"Number of Neo4j vertices found: ${neo4jVertices.length}")
    val neo4jEdges = neo4jGraph.getEdges.toList
    println(s"Number of Neo4j edges found: ${neo4jEdges.length}")

    //ArangoDB migration
    val arangoDBProps = Map("blueprints.arangodb.host" -> "localhost",
      "blueprints.arangodb.port" -> "8529",
      "blueprints.arangodb.db" -> "test",
      "blueprints.arangodb.name" -> "testGraph",
      "blueprints.arangodb.verticesCollectionName" -> "testVertices",
      "blueprints.arangodb.edgesCollectionName" -> "edgesTest")
    val arangoDbClient = new ArangoDB(arangoDBProps)
    val arangoDbGraph = arangoDbClient.graph

    // Create ArangoDB vertices from Neo4j ones
    neo4jVertices.foreach(vertex => {
      val newVertex = arangoDbGraph.addVertex(vertex.getId)
      vertex.getPropertyKeys.foreach(property => newVertex.setProperty(property, vertex.getProperty[Object](property)))
    })

    // Create ArangoDB edges from Neo4j ones
    neo4jEdges.foreach(edge => {
      val outVertex = arangoDbGraph.getVertex(edge.getVertex(Direction.OUT).getId)
      val inVertex = arangoDbGraph.getVertex(edge.getVertex(Direction.IN).getId)
      val newEdge = arangoDbGraph.addEdge(edge.getId, outVertex, inVertex, edge.getLabel)
      edge.getPropertyKeys.foreach(property => newEdge.setProperty(property, edge.getProperty[Object](property)))
    })

    println(s"Number of ArangoDB vertices inserted: ${arangoDbGraph.getVertices.toList.length}")
    println(s"Number of ArangoDB edges inserted: ${arangoDbGraph.getEdges.toList.length}")

    // Proper raw graphs shutdown
    neo4jGraph.shutdown()
    arangoDbGraph.shutdown()

  }

}
