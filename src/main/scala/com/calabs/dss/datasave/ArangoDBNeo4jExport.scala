package com.calabs.dss.datasave

import com.tinkerpop.blueprints.Direction

import collection.JavaConversions._

/**
 * Created by Jordi Aranda
 * <jordi.aranda@bsc.es>
 * 1/26/15
 */
object ArangoDBNeo4jExport {

  def main(args: Array[String]) : Unit = {

    //ArangoDB migration to Neo4j
    val arangoDBProps = Map("blueprints.arangodb.host" -> "localhost",
      "blueprints.arangodb.port" -> "8529",
      "blueprints.arangodb.db" -> "test",
      "blueprints.arangodb.name" -> "testGraph",
      "blueprints.arangodb.verticesCollectionName" -> "testVertices",
      "blueprints.arangodb.edgesCollectionName" -> "edgesTest")
    val arangoDbClient = new ArangoDB(arangoDBProps)
    val arangoDbGraph = arangoDbClient.graph

    val arangoDbVertices = arangoDbGraph.getVertices.toList
    println(s"Number of ArangoDB vertices found: ${arangoDbVertices.length}")
    val arangoDbEdges = arangoDbGraph.getEdges.toList
    println(s"Number of ArangoDB edges found: ${arangoDbEdges.length}")

    val neo4jProps = Map("blueprints.neo4j.directory" -> "/Users/jaranda/neo4j/newdrwho")
    val neo4jClient = new Neo4j(neo4jProps)
    val neo4jGraph = neo4jClient.graph

    // Create Neo4j vertices from ArangoDB ones
    arangoDbVertices.foreach(vertex => {
      val newVertex = neo4jGraph.addVertex(vertex.getId)
      vertex.getPropertyKeys.foreach(property => newVertex.setProperty(property, vertex.getProperty[Object](property)))
    })
    // Commit Neo4j transaction
    neo4jGraph.commit

    // Create ArangoDB edges from Neo4j ones
    arangoDbEdges.foreach(edge => {
      val outVertex = neo4jGraph.getVertex(edge.getVertex(Direction.OUT).getId)
      val inVertex = neo4jGraph.getVertex(edge.getVertex(Direction.IN).getId)
      val newEdge = neo4jGraph.addEdge(edge.getId, outVertex, inVertex, edge.getLabel)
      edge.getPropertyKeys.foreach(property => newEdge.setProperty(property, edge.getProperty[Object](property)))
    })
    // Commit Neo4j transaction
    neo4jGraph.commit

    println(s"Number of Neo4j vertices inserted: ${neo4jGraph.getVertices.toList.length}")
    println(s"Number of Neo4j edges inserted: ${neo4jGraph.getEdges.toList.length}")

    // Proper raw graphs shutdown
    neo4jGraph.shutdown()
    arangoDbGraph.shutdown()

  }

}
