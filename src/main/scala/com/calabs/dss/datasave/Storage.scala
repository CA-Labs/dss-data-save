package com.calabs.dss.datasave

import com.calabs.dss.dataimport.Parsing.Tags
import com.calabs.dss.dataimport.{Parsing, Edge, Vertex, Document}
import com.calabs.dss.datasave.GraphStorageComponent.{Neo4jComponent, TitanComponent, ArangoDBComponent}
import com.thinkaurelius.titan.core.TitanFactory
import com.tinkerpop.blueprints.Graph
import com.tinkerpop.blueprints.impls.arangodb.{ArangoDBGraphQuery, ArangoDBGraph}
import com.tinkerpop.blueprints.impls.arangodb.client.ArangoDBConfiguration
import com.tinkerpop.blueprints.impls.neo4j2.Neo4j2Graph

import org.apache.commons.configuration.BaseConfiguration
import org.json4s.JsonAST._

import scala.util.{Failure, Success, Try}
import collection.JavaConversions._

import com.tinkerpop.blueprints.{Vertex => BlueprintsVertex, Edge => BlueprintsEdge}

/**
 * Created by Jordi Aranda
 * <jordi.aranda@bsc.es>
 * 9/1/15
 */

sealed trait Storage

object Storage {
  object Db {
    val ArangoDB = "arangodb"
    val Neo4j = "neo4j"
    val Titan = "titan"
  }
}

sealed trait DocumentStorage extends Storage {
  // Dependency injection
  self: DocumentStorageComponent =>
  def saveDocument(doc: Document) : Unit = {
    storageRepository.saveDocument(doc)
  }
}

sealed trait GraphStorage extends Storage {
  // Dependency injection
  self: GraphStorageComponent =>
  protected[this] val blueprintsConfPrefix = "blueprints."
  def saveDocument(doc: Document)(implicit graph: Graph) : Unit = {
    storageRepository.saveDocument(doc)
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

  def handleBigs(value: Any) = value match {
    case bi: BigInt => bi.toDouble
    case bd: BigDecimal => bd.toDouble
    case _ => value
  }

  trait StorageRepository
}

sealed trait DocumentStorageComponent extends StorageComponent {
  def storageRepository : DocumentStorageRepository
  trait DocumentStorageRepository extends StorageRepository {
    def saveDocument(doc: Document) : Unit
    def insertOrUpdate(doc: Document) : Unit
  }
}

sealed trait GraphStorageComponent extends StorageComponent {
  /**
   * Pair of criteria remaining equal and to be updated.
   * @param doc The document to update.
   * @return
   */
  def getLookUpCriteria(doc: Document) : (Map[String, JValue], Map[String, JValue]) = doc match {
    case v: Vertex => {
      (doc.props.filter{case (k,v) => !k.startsWith(Tags.SEARCHABLE_CRITERIA) && !k.startsWith(Tags.FROM) && !k.startsWith(Tags.TO)},
        doc.props.filter{case (k,v) => k.startsWith(Tags.SEARCHABLE_CRITERIA) && !k.startsWith(Tags.FROM) && !k.startsWith(Tags.TO)})
    }
    case e: Edge => {
      (doc.props.filter{case (k,v) => !k.startsWith(Tags.SEARCHABLE_CRITERIA)},
        doc.props.filter{case (k,v) => k.startsWith(Tags.SEARCHABLE_CRITERIA) && !k.startsWith(Tags.FROM) && !k.startsWith(Tags.TO)})
    }
  }

  def storageRepository: GraphStorageRepository
  trait GraphStorageRepository extends StorageRepository {
    def saveDocument(doc: Document)(implicit graph: Graph) : Unit
    def insertOrUpdate(doc: Document)(implicit graph: Graph) : Unit
  }
}

object GraphStorageComponent {

  // Current supported implementations

  trait Neo4jComponent extends GraphStorageComponent {
    def storageRepository = new Neo4jStorageRepository
    class Neo4jStorageRepository extends GraphStorageRepository {
      override def saveDocument(doc: Document)(implicit graph: Graph): Unit = ???
      override def insertOrUpdate(doc: Document)(implicit graph: Graph): Unit = ???
    }
  }

  trait ArangoDBComponent extends GraphStorageComponent {
    def storageRepository = new ArangoDBRepository
    class ArangoDBRepository extends GraphStorageRepository {

      override def saveDocument(doc: Document)(implicit graph: Graph): Unit = doc match {
        case v: Vertex => insertOrUpdate(v)
        case e: Edge => insertOrUpdate(e)
        case _ => throw new IllegalArgumentException(s"ArangoDB can only handle either vertices or edges document types")
      }

      /**
       * Saves or updates a document (either an [[Edge]] or a [[Vertex]]) depending on its properties.
       * @param doc The document to save or update.
       * @param graph The graph where this document will be saved or updated.
       * @return
       */
      override def insertOrUpdate(doc: Document)(implicit graph: Graph) : Unit = {
        if (Parsing.updateRequired(doc.props)){
          val lookupCriteria = getLookUpCriteria(doc)
          // This is an update
          doc match {
            case v: Vertex => {
              val vertexId = getVerticesByMultipleCriteria(lookupCriteria._1).head.getId
              val retrievedVertex = graph.getVertex(vertexId)
              lookupCriteria._2.map{case (k,v) => (k.replace(Tags.SEARCHABLE_CRITERIA, ""),v)}.foreach{case (k,v) => retrievedVertex.setProperty(k, handleBigs(v.values))}
            }
            case e: Edge => {
              val edgeId = getEdgesByMultipleCriteria(lookupCriteria._1).head.getId
              val retrievedEdge = graph.getEdge(edgeId)
              lookupCriteria._2.map{case (k,v) => (k.replace(Tags.SEARCHABLE_CRITERIA, ""),v)}.foreach{case(k,v) => retrievedEdge.setProperty(k, handleBigs(v.values))}
            }
          }
        } else {
          // This is an insert
          doc match {
            case v: Vertex => {
              val vertex = graph.addVertex(null)
              v.props.foreach{case (k,v) => vertex.setProperty(k, handleBigs(v.values))}
            }
            case e: Edge => {
              // For edges, we need related vertices id's so first we have to grab them
              val fromCriteria = e.props.get(Tags.FROM) match {
                case Some(from) => from match {
                  case JObject(criteria) => criteria.toMap
                  case _ => throw new IllegalArgumentException(s"Wrong criteria located at ${Tags.FROM}, must be a map of valid key/values")
                }
                case None => throw new IllegalArgumentException(s"Missing ${Tags.FROM} property in edge document")
              }
              val toCriteria = e.props.get(Tags.TO) match {
                case Some(to) => to match {
                  case JObject(criteria) => criteria.toMap
                  case _ => throw new IllegalArgumentException(s"Wrong criteria located at ${Tags.TO}, must be a map of valid key/values")
                }
                case None => throw new IllegalArgumentException(s"Missing ${Tags.FROM} property in edge document")
              }
              val fromVertex = getVerticesByMultipleCriteria(fromCriteria).head
              val toVertex = getVerticesByMultipleCriteria(toCriteria).head
              val edge = graph.addEdge(null, fromVertex, toVertex, "")
              e.props.filter{case (k,v) => k != Tags.FROM && k != Tags.TO}
                .foreach{case(k,v) => edge.setProperty(k, handleBigs(v.values))}
            }
          }
        }
      }

      /**
       * Retrieves vertices by multiple criteria
       * @param criteria The criteria used for look-up.
       * @param graph The graph used for querying.
       * @return
       */
      def getVerticesByMultipleCriteria(criteria: Map[String, JValue])(implicit graph: Graph) : Iterable[BlueprintsVertex] = {
        // TODO: Index usage?
        val query = graph.query()
        criteria.foreach{ case (k,v) => query.has(k, handleBigs(v.values)) }
        query.vertices()
      }

      /**
       * Retrieves edges by multiple criteria
       * @param criteria The criteria used for look-up.
       * @param graph The graph used for querying.
       * @return
       */
      def getEdgesByMultipleCriteria(criteria: Map[String, JValue])(implicit graph: Graph) : Iterable[BlueprintsEdge] = {
        // TODO: Index usage?
        val query = graph.query()
        criteria.foreach{ case (k,v) => query.has(k, v.values) }
        query.edges()
      }

    }
  }

  trait TitanComponent extends GraphStorageComponent {
    def storageRepository = new TitanRepository
    class TitanRepository extends GraphStorageRepository {
      override def saveDocument(doc: Document)(implicit graph: Graph): Unit = ???
      override def insertOrUpdate(doc: Document)(implicit graph: Graph): Unit = ???
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

  private[this] val arangoDbPrefix = "arangodb."
  private[this] val arangoDbConfPrefix = "conf."
  private[this] val confPrefix = blueprintsConfPrefix ++ arangoDbPrefix ++ arangoDbConfPrefix

  private[this] object Props {
    val HOST = "host"
    val PORT = "port"
    val DB = "db"
    val NAME = "name"
    val VERTICES_COLLECTION = "verticesCollectionName"
    val EDGES_COLLECTION = "edgesCollectionName"
  }

  private[this] val requiredProps = List(Props.HOST, Props.PORT, Props.DB, Props.NAME, Props.VERTICES_COLLECTION, Props.EDGES_COLLECTION).map(prop => (prop, blueprintsConfPrefix ++ arangoDbPrefix ++ prop)).toMap

  // Drops Blueprints related properties so that only exclusive vendor db properties are left
  private[this] def getArangoDBProps : Map[String, String] = {
    props.filter(prop => !requiredProps.containsValue(prop._1))
  }

  override def checkRequiredProps: Boolean = requiredProps.forall(prop => props.contains(prop._2))

  override def checkConfigProps: Boolean = getArangoDBProps.forall(prop => prop._1.startsWith(confPrefix))

  // Graph initialization
  val requiredPropsOk = checkRequiredProps
  val configPropsOk = checkConfigProps

  val graph = Try(
    {
      if (!requiredPropsOk) throw new IllegalArgumentException(s"Wrong required parameters for ArangoDB storage component. The following parameters are required: $requiredProps.")
      else if(!configPropsOk) throw new IllegalArgumentException(s"Wrong configuration parameters for ArangoDB storage component. Please do check they all start with $confPrefix.")
      else {
        val arangoDbConfiguration = new ArangoDBConfiguration(props.get(requiredProps.get(Props.HOST)), props.get(requiredProps.get(Props.PORT)))
        arangoDbConfiguration.setDb(props.get(requiredProps.get(Props.DB)))
        new ArangoDBGraph(arangoDbConfiguration, props.get(requiredProps.get(Props.NAME)), props.get(requiredProps.get(Props.VERTICES_COLLECTION)), props.get(requiredProps.get(Props.EDGES_COLLECTION)))
      }
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
