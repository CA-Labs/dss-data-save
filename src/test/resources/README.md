## Data migration examples

In the current directory can be found different usage examples of our tool DSS Data Save module:
* An example of [JSON input](https://github.com/CA-Labs/dss-data-save/tree/master/src/test/resources/example.json) following the required specification. This example consists of all [Dr Who TV show](http://en.wikipedia.org/wiki/Doctor_Who) character relationships.
* Examples of property files for Neo4j, ArangoDB and Titan backends.

In order to start the data migration using the above mentioned JSON input, the corresponding database instance should be up and running beforehand. To start the migration, use the following command:
* Neo4j migration: `dss-data-save -d neo4j -p neo4j.props`
* ArangoDB migration: `dss-data-save -d arangodb -p arangodb.props`
* Titan migration: `dss-data-save -d titan -p titan.props`