package com.calabs.dss.datasave

import com.calabs.dss.dataimport.Parsing.Tags
import com.calabs.dss.dataimport.{Parsing, Edge, Vertex, Document}
import com.calabs.dss.datasave.GraphStorageComponent.{Neo4jComponent, TitanComponent, ArangoDBComponent}
import com.thinkaurelius.titan.core.TitanFactory

import com.tinkerpop.blueprints.Graph
import com.tinkerpop.blueprints.impls.arangodb.{ArangoDBGraph}
import com.tinkerpop.blueprints.impls.arangodb.client.ArangoDBConfiguration
import com.tinkerpop.blueprints.impls.neo4j2.Neo4j2Graph

import org.apache.commons.configuration.BaseConfiguration
import org.json4s.JsonAST._

import scala.util.{Failure, Success, Try}
import collection.JavaConversions._
import scala.collection.JavaConverters._

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

  /**
   * Remaps [[Tags.ID]] keys to [[Tags.IMPORT_ID]] over [[Document]] keys.
   * @param doc The [[Document]] whose [[Tags.ID]] keys have to be remapped.
   * @return
   */
  def remapDocumentProps(doc: Document): Document = {
    def recursiveRemap(props: Map[String, JValue]) : Map[String, JValue] = props.map{case (k,v) => {
      if (k == Tags.ID) {
        v match {
          case o: JObject => (Tags.IMPORT_ID, JObject(recursiveRemap(o.obj.toMap).toList))
          case _ => (Tags.IMPORT_ID, v)
        }
      } else {
        v match {
          case o: JObject => (k, JObject(recursiveRemap(o.obj.toMap).toList))
          case _ => (k, v)
        }
      }
    }}

    doc match {
      case v: Vertex => Vertex(recursiveRemap(v.props))
      case e: Edge => Edge(recursiveRemap(e.props))
    }
  }

  // Handy implicits used when getting property values from properties map
  implicit def optionString2String(value: Option[String]) = value.get
  implicit def optionString2Int(value: Option[String]) = value.get.toInt
  implicit def mapPropsToConfiguration(props: Map[String, String]) = {
    var conf = new BaseConfiguration()
    props.foreach(prop => conf.addProperty(prop._1, prop._2))
    conf
  }

  def asJavaRecursive(value: Any) : Any = value match {
    case bi: BigInt => bi.toInt
    case bd: BigDecimal => bd.toDouble
    case array: List[Any] => array.map(x => asJavaRecursive(x)).asJava // blueprints impls only deal with java collections
    case map: Map[String,Any] => map.mapValues(x => asJavaRecursive(x)).asJava // blueprints impls only deal with java collections
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
    /**
     * Main save function
     * @param doc The document to save or update
     * @param graph Implicit graph instance which depends on the underlying graph db implementation used.
     * @return
     */
    def saveDocument(doc: Document)(implicit graph: Graph) : Unit = remapDocumentProps(doc) match {
      case v: Vertex => insertOrUpdate(v)
      case e: Edge => insertOrUpdate(e)
      case _ => throw new IllegalArgumentException(s"Neo4j can only handle either vertices or edges document types")
    }

    /**
     * Saves or updates a document (either an [[Edge]] or a [[Vertex]]) depending on its properties.
     * @param doc The document to save or update.
     * @param graph The graph where this document will be saved or updated.
     * @return
     */
    def insertOrUpdate(doc: Document)(implicit graph: Graph) : Unit = {
      if (Parsing.updateRequired(doc.props)){
        val lookupCriteria = getLookUpCriteria(doc)
        // This is an update
        doc match {
          case v: Vertex => {
            val verticesFound = getVerticesByMultipleCriteria(lookupCriteria._1)
            val vertexId = verticesFound match {
              case head::tail => head.getId
              case _ => throw new NoSuchElementException(s"No vertices matching criteria ${lookupCriteria._1} were found, exitting...")
            }
            val retrievedVertex = graph.getVertex(vertexId)
            lookupCriteria._2.map{case (k,v) => (k.replace(Tags.SEARCHABLE_CRITERIA, ""),v)}.foreach{case (k,v) => retrievedVertex.setProperty(k, asJavaRecursive(v.values))}
          }
          case e: Edge => {
            val edgesFound = getEdgesByMultipleCriteria(lookupCriteria._1)
            val edgeId = edgesFound match {
              case head::tail => head.getId
              case _ => throw new NoSuchElementException(s"No edges matching criteria ${lookupCriteria._1} were found, exitting...")
            }
            val retrievedEdge = graph.getEdge(edgeId)
            lookupCriteria._2.map{case (k,v) => (k.replace(Tags.SEARCHABLE_CRITERIA, ""),v)}.foreach{case(k,v) => retrievedEdge.setProperty(k, asJavaRecursive(v.values))}
          }
        }
      } else {
        // This is an insert
        doc match {
          case v: Vertex => {
            val vertex = graph.addVertex(null)
            v.props.foreach{case (k,v) => vertex.setProperty(k, asJavaRecursive(v.values))}
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
            val fromVerticesFound = getVerticesByMultipleCriteria(fromCriteria)
            val fromVertex = fromVerticesFound match {
              case head :: Nil => head
              case head :: tail => throw new IllegalArgumentException(s"Invalid from criteria $fromCriteria (more than one matching vertex was found)")
              case _ => throw new IllegalArgumentException(s"Invalid from criteria $fromCriteria (no matching vertex was found)")
            }
            val toVerticesFound = getVerticesByMultipleCriteria(toCriteria)
            val toVertex = toVerticesFound match {
              case head :: Nil => head
              case head :: tail => throw new IllegalArgumentException(s"Invalid to criteria $toCriteria (more than one matching vertex was found)")
              case _ => throw new IllegalArgumentException(s"Invalid to criteria $toCriteria (no matching vertex was found)")
            }

            // Neo4j impl requires label to be present when creating the edge
            val label = e.props.get(Tags.LABEL) match {
              case Some(label) => label match {
                case JString(label) => label
                case _ => throw new IllegalArgumentException(s"Wrong label, only strings are supported")
              }
              case None => ""
            }
            val edge = graph.addEdge(null, fromVertex, toVertex, label)

            e.props.filter{case (k,v) => k != Tags.FROM && k != Tags.TO && k != Tags.LABEL}
              .foreach{case(k,v) => edge.setProperty(k, asJavaRecursive(v.values))}
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
    def getVerticesByMultipleCriteria(criteria: Map[String, JValue])(implicit graph: Graph) : List[BlueprintsVertex] = {
      // TODO: Index usage?
      val query = graph.query()
      criteria.foreach{ case (k,v) => query.has(k, asJavaRecursive(v.values)) }
      query.vertices().toList
    }

    /**
     * Retrieves edges by multiple criteria
     * @param criteria The criteria used for look-up.
     * @param graph The graph used for querying.
     * @return
     */
    def getEdgesByMultipleCriteria(criteria: Map[String, JValue])(implicit graph: Graph) : List[BlueprintsEdge] = {
      // TODO: Index usage?
      val query = graph.query()
      criteria.foreach{ case (k,v) => query.has(k, asJavaRecursive(v.values)) }
      query.edges().toList
    }

  }
}

object GraphStorageComponent {

  // Current supported implementations
  trait Neo4jComponent extends GraphStorageComponent {
    def storageRepository = new Neo4jStorageRepository
    class Neo4jStorageRepository extends GraphStorageRepository
  }

  trait ArangoDBComponent extends GraphStorageComponent {
    def storageRepository = new ArangoDBRepository
    class ArangoDBRepository extends GraphStorageRepository
  }

  trait TitanComponent extends GraphStorageComponent {
    def storageRepository = new TitanRepository
    class TitanRepository extends GraphStorageRepository
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

  val graph = Try(
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

  private[this] val titanPrefix = "titan."
  private[this] val confPrefix = blueprintsConfPrefix ++ titanPrefix

  private [this] object Props {
    val DIRECTORY = "storage.directory"
    val BACKEND = "storage.backend"
  }

  private[this] val requiredProps = List(Props.DIRECTORY, Props.BACKEND).map(prop => (prop, blueprintsConfPrefix ++ titanPrefix ++ prop)).toMap

  // Drops Blueprints related properties so that only exclusive vendor db properties are left
  private[this] def getTitanProps : Map[String, String] = {
    props.filter(prop => !requiredProps.containsValue(prop._1))
  }

  override def checkRequiredProps: Boolean = requiredProps.forall(prop => props.contains(prop._2))

  override def checkConfigProps: Boolean = getTitanProps.forall(prop => prop._1.startsWith(confPrefix))

  // Graph initialization
  val requiredPropsOk = checkRequiredProps
  val configPropsOk = checkConfigProps

  val graph = Try(
    {
      if (!requiredPropsOk) throw new IllegalArgumentException(s"Wrong required parameters for Titan storage component. The following parameters are required: $requiredProps.")
      else if(!configPropsOk) throw new IllegalArgumentException(s"Wrong configuration parameters for Titan storage component. Please do check they all start with $confPrefix.")
      else {
        val titanConfiguration = new BaseConfiguration()
        titanConfiguration.addProperty(Props.DIRECTORY, props.get(requiredProps.get(Props.DIRECTORY)).get)
        titanConfiguration.addProperty(Props.BACKEND, props.get(requiredProps.get(Props.BACKEND)).get)
        props.filter{case (k,v) => k.startsWith(confPrefix) && !requiredProps.contains(k)}.foreach{case (k,v) => titanConfiguration.addProperty(k.replace(confPrefix, ""), v)}
        TitanFactory.open(titanConfiguration)
      }
    }
  ) match {
    case Success(graph) => graph
    case Failure(e) => throw new IllegalArgumentException(s"Some error occurred when trying to instantiate an Titan graph: ${e.getMessage}")
  }

}
