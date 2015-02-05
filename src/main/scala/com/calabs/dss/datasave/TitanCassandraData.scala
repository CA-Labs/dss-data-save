package com.calabs.dss.datasave

import com.tinkerpop.blueprints.Direction
import org.json4s.DefaultFormats
import org.json4s.jackson.Serialization

import collection.JavaConversions._
import collection.mutable.{Map => MutableMap}

/**
 * Created by Jordi Aranda
 * <jordi.aranda@bsc.es>
 * 4/2/15
 */

object TitanCassandraData {

  def main(args: Array[String]) : Unit = {

    // Neo4j data to data-input-like JSON
    val titanProps = Map("blueprints.titan.storage.directory" -> "/Users/jaranda/cassandra/drwho-migration", "blueprints.titan.storage.backend" -> "cassandra")
    val titanClient = new Titan(titanProps)
    implicit val titanGraph = titanClient.graph

    val titanVertices = titanGraph.query().vertices().toList
    println(s"Number of Titan vertices found: ${titanVertices.length}")
    val titanEdges = titanGraph.query().edges().toList
    println(s"Number of Neo4j edges found: ${titanEdges.length}")

    titanVertices.foreach(println)
    titanEdges.foreach(println)

    titanGraph.shutdown

  }

}
