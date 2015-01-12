package com.calabs.dss.datasave

import com.calabs.dss.datasave.StorageComponent.{TitanComponent, ArangoDBComponent, Neo4jComponent}
import com.tinkerpop.blueprints.Graph

/**
 * Created by Jordi Aranda
 * <jordi.aranda@bsc.es>
 * 9/1/15
 */

sealed trait StorageComponent {

  def storageRepository : StorageRepository
  def checkConfigProps : Boolean

  trait StorageRepository {
    def save(data: Map[String, Any]) : Unit
  }

}

object StorageComponent {

  // Current supported implementations

  trait Neo4jComponent extends StorageComponent {
    def storageRepository = new Neo4jStorageRepository
    class Neo4jStorageRepository extends StorageRepository {
      override def save(data: Map[String, Any]): Unit = ???
    }
  }

  trait ArangoDBComponent extends StorageComponent {
    def storageRepository = new ArangoDBRepository
    class ArangoDBRepository extends StorageRepository {
      override def save(data: Map[String, Any]): Unit = ???
    }
  }

  trait TitanComponent extends StorageComponent {
    def storageRepository = new TitanRepository
    class TitanRepository extends StorageRepository {
      override def save(data: Map[String, Any]): Unit = ???
    }
  }

}

trait Storage {

  // Dependency injection
  self: StorageComponent =>

  def saveMetrics(data: Map[String, Any]) : Unit = {
    storageRepository.save(data)
  }

}

case class Neo4j(props: Map[String, Any]) extends Storage with Neo4jComponent {

  private[this] val neo4jConf = "blueprints.neo4j.conf."

  private[this] val requiredProps = List("directory")
  private[this] val requiredConfProps = List().map(s => neo4jConf ++ s)

  override def checkConfigProps: Boolean = {
    false
  }
}

case class ArangoDB(props: Map[String, Any]) extends Storage with ArangoDBComponent {

  private[this] val arangoDbConf = "blueprints.arangodb.conf."

  private[this] val requiredProps = List("host", "port", "name", "verticesCollectionName", "edgesCollectionName").map(s => arangoDbConf ++ s)
  private[this] val requiredConfProps = List()

  override def checkConfigProps: Boolean = props.forall(propValue => propValue._1.startsWith(arangoDbConf) && requiredProps.contains(propValue))
}

case class Titan(props: Map[String, Any]) extends Storage with TitanComponent {

  private[this] val titanConf = "blueprints.titan.conf."

  private[this] val requiredProps = List()

  override def checkConfigProps: Boolean = props.forall(propValue => propValue._1.startsWith(titanConf) && requiredProps.contains(propValue._1))

}
