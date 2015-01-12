package com.calabs.dss.datasave

import com.calabs.dss.datasave.GraphStorageComponent.{Neo4jComponent, TitanComponent, ArangoDBComponent}
import com.thinkaurelius.titan.core.TitanFactory
import com.tinkerpop.blueprints.Graph
import com.tinkerpop.blueprints.impls.arangodb.ArangoDBGraph
import com.tinkerpop.blueprints.impls.neo4j2.Neo4j2Graph
import org.apache.commons.configuration.BaseConfiguration

import scala.util.{Failure, Success, Try}
import collection.JavaConversions._

/**
 * Created by Jordi Aranda
 * <jordi.aranda@bsc.es>
 * 9/1/15
 */

object StorageType {
  val DOCUMENT = "1"
  val GRAPH = "2"
}

object DocumentType {
  val DOCUMENT = 1
  val NODE = 2
  val EDGE = 3
}

sealed trait Storage

sealed trait DocumentStorage extends Storage {
  // Dependency injection
  self: DocumentStorageComponent =>
  def saveMetrics(data: Map[String, Any]) : Unit = {
    storageRepository.save(data)
  }
}

sealed trait GraphStorage extends Storage {
  // Dependency injection
  self: GraphStorageComponent =>
  protected[this] val blueprintsConfPrefix = "blueprints."
  def saveMetrics(data: Map[String, Any], documentType: Int)(implicit graph: Graph) : Unit = {
    storageRepository.save(data, documentType)
  }
}

sealed trait StorageComponent {
  def storageRepository : StorageRepository
  def checkRequiredProps : Boolean
  def checkConfigProps : Boolean

  // Handy implicits used when getting property values from properties map
  implicit def optionString2String(value: Option[String]) = value.get
  implicit def optionString2Int(value: Option[String]) = value.get.toInt
  implicit def mapPropsToConfiguration(props: Map[String, String]) = {
    var conf = new BaseConfiguration()
    props.foreach(prop => conf.addProperty(prop._1, prop._2))
    conf
  }

  trait StorageRepository
}

sealed trait DocumentStorageComponent extends StorageComponent {
  def storageRepository : DocumentStorageRepository
  trait DocumentStorageRepository extends StorageRepository {
    def save(data: Map[String, Any]) : Unit
  }
}

sealed trait GraphStorageComponent extends StorageComponent {
  def storageRepository: GraphStorageRepository
  trait GraphStorageRepository extends StorageRepository {
    def save(data: Map[String, Any], documentType: Int)(implicit graph: Graph) : Unit
  }
}

object GraphStorageComponent {

  // Current supported implementations

  trait Neo4jComponent extends GraphStorageComponent {
    def storageRepository = new Neo4jStorageRepository
    class Neo4jStorageRepository extends GraphStorageRepository {
      override def save(data: Map[String, Any], documentType: Int)(implicit graph: Graph): Unit = {
        documentType match {
          case DocumentType.NODE => data.foreach(v => {
            val node = graph.addVertex(null)
            node.setProperty(v._1, v._2)
          })
          case DocumentType.EDGE => throw new IllegalArgumentException(s"Edges storage not supported yet.")
          case DocumentType.DOCUMENT => throw new IllegalArgumentException(s"Documents storage not supported yet.")
        }
      }
    }
  }

  trait ArangoDBComponent extends GraphStorageComponent {
    def storageRepository = new ArangoDBRepository
    class ArangoDBRepository extends GraphStorageRepository {
      override def save(data: Map[String, Any], documentType: Int)(implicit graph: Graph): Unit = ???
    }
  }

  trait TitanComponent extends GraphStorageComponent {
    def storageRepository = new TitanRepository
    class TitanRepository extends GraphStorageRepository {
      override def save(data: Map[String, Any], documentType: Int)(implicit graph: Graph): Unit = ???
    }
  }

}

class Neo4j(props: Map[String, String]) extends GraphStorage with Neo4jComponent {

  private[this] val neo4jPrefix = "neo4j."
  private[this] val neo4jConfPrefix = "conf."
  private[this] val confPrefix = blueprintsConfPrefix ++ neo4jPrefix ++ neo4jConfPrefix

  private[this] object Props {
    val DIRECTORY = "directory"
  }

  private[this] val requiredProps = List(Props.DIRECTORY).map(prop => (prop, blueprintsConfPrefix ++ neo4jPrefix ++ prop)).toMap

  // Drops Blueprints related properties so that only exclusive vendor db properties are left
  private[this] def getNeo4jProps : Map[String, String] = {
    props.filter(prop => !requiredProps.containsValue(prop._1))
  }

  override def checkRequiredProps: Boolean = requiredProps.forall(prop => props.contains(prop._2))

  override def checkConfigProps: Boolean = getNeo4jProps.forall(prop => prop._1.startsWith(confPrefix))

  // Graph initialization
  val requiredPropsOk = checkRequiredProps
  val configPropsOk = checkConfigProps

  implicit val graph : Graph = Try(
    {
      if (!requiredPropsOk) throw new IllegalArgumentException(s"Wrong required parameters for Neo4j storage component. The following parameters are required: $requiredProps.")
      else if(!configPropsOk) throw new IllegalArgumentException(s"Wrong configuration parameters for Neo4j storage component. Please do check they all start with $confPrefix.")
      else new Neo4j2Graph(props.get(requiredProps.get(Props.DIRECTORY)), getNeo4jProps)
    }
  ) match {
    case Success(graph) => graph
    case Failure(e) => throw new IllegalArgumentException(s"Some error occurred when trying to instantiate a Neo4j graph: ${e.getMessage}")
  }

}

class ArangoDB(props: Map[String, String]) extends GraphStorage with ArangoDBComponent {

  private[this] val arangoDbConf = "blueprints.arangodb.conf."

  private[this] object Props {
    val HOST = "host"
    val PORT = "port"
    val NAME = "name"
    val VERTICES_COLLECTION = "verticesCollectionName"
    val EDGES_COLLECTION = "edgesCollectionName"
  }

  private[this] val requiredProps = List(Props.HOST, Props.PORT, Props.NAME, Props.VERTICES_COLLECTION, Props.EDGES_COLLECTION).map(prop => arangoDbConf ++ prop)

  override def checkRequiredProps: Boolean = requiredProps.forall(prop => props.contains(prop))

  override def checkConfigProps: Boolean = props.forall(prop => prop._1.startsWith(arangoDbConf))

  // Graph initialization
  val requiredPropsOk = checkRequiredProps
  val configPropsOk = checkConfigProps

  val graph = Try(
    {
      if (!requiredPropsOk) throw new IllegalArgumentException(s"Wrong required parameters for ArangoDB storage component. The following parameters are required: $requiredProps.")
      else if(!configPropsOk) throw new IllegalArgumentException(s"Wrong configuration parameters for ArangoDB storage component. Please do check they all start with $arangoDbConf.")
      else new ArangoDBGraph(props.get(Props.HOST), props.get(Props.PORT), props.get(Props.NAME).get, props.get(Props.VERTICES_COLLECTION), props.get(Props.EDGES_COLLECTION))
    }
  ) match {
    case Success(graph) => graph
    case Failure(e) => throw new IllegalArgumentException(s"Some error occurred when trying to instantiate an ArangoDB graph: ${e.getMessage}")
  }

}

class Titan(props: Map[String, String]) extends GraphStorage with TitanComponent {

  private[this] val titanConf = "blueprints.titan.conf."

  private [this] object Props {

  }

  private[this] val requiredProps = List().map(prop => blueprintsConfPrefix ++ prop)

  override def checkRequiredProps: Boolean = requiredProps.forall(prop => props.contains(prop))

  override def checkConfigProps: Boolean = props.forall(prop => prop._1.startsWith(titanConf))

  // Graph initialization
  val requiredPropsOk = checkRequiredProps
  val configPropsOk = checkConfigProps

  val graph = Try(
  {
    if (!requiredPropsOk) throw new IllegalArgumentException(s"Wrong required parameters for ArangoDB storage component. The following parameters are required: $requiredProps.")
    else if(!configPropsOk) throw new IllegalArgumentException(s"Wrong configuration parameters for ArangoDB storage component. Please do check they all start with $titanConf.")
    else TitanFactory.open(props)
  }
  ) match {
    case Success(graph) => graph
    case Failure(e) => throw new IllegalArgumentException(s"Some error occurred when trying to instantiate an ArangoDB graph: ${e.getMessage}")
  }

}
