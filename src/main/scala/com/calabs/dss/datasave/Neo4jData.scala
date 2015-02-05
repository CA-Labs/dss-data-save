package com.calabs.dss.datasave

import com.calabs.dss.dataimport.Parsing.Tags

import com.tinkerpop.blueprints.Direction
import org.json4s.jackson.Serialization

import collection.JavaConversions._
import collection.mutable.{Map => MutableMap}

import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._

/**
 * Created by Jordi Aranda
 * <jordi.aranda@bsc.es>
 * 3/2/15
 */
object Neo4jData {

  def main(args: Array[String]) : Unit = {

    // Neo4j data to data-input-like JSON
    val neo4jProps = Map("blueprints.neo4j.directory" -> "/Users/jaranda/neo4j/drwho/drwho")
    val neo4jClient = new Neo4j(neo4jProps)
    implicit val neo4jGraph = neo4jClient.graph

    val neo4jVertices = neo4jGraph.getVertices.toList
    println(s"Number of Neo4j vertices found: ${neo4jVertices.length}")
    val neo4jEdges = neo4jGraph.getEdges.toList
    println(s"Number of Neo4j edges found: ${neo4jEdges.length}")

    val vertices = neo4jVertices.filter(vertex => !vertex.getPropertyKeys.isEmpty).map(vertex => {
      val props = vertex.getPropertyKeys
      val map = MutableMap.empty[String,Any]
      props.foreach(key => map.update(key, vertex.getProperty(key)))
      map.update(Tags.IMPORT_ID, vertex.getId)
      map.toMap
    })

    val edges = neo4jEdges.map(edge => {
      val props = edge.getPropertyKeys
      val map = MutableMap.empty[String,Any]
      props.foreach(key => map.update(key, edge.getProperty(key)))
      map.update(Tags.IMPORT_ID, edge.getId)
      map.update(Tags.LABEL, edge.getLabel)
      map.update(Tags.FROM, Map(Tags.IMPORT_ID -> edge.getVertex(Direction.OUT).getId))
      map.update(Tags.TO, Map(Tags.IMPORT_ID -> edge.getVertex(Direction.IN).getId))
      map.toMap
    })

    implicit val formats = DefaultFormats
    println(Serialization.writePretty(List("vertices" -> vertices, "edges" -> edges).toMap))

    neo4jGraph.shutdown

  }

}
