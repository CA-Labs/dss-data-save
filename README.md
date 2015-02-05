#Decistion Support System - Data Save Module

##Overview

In the context of a project funded by the European Union called MODAClouds ([www.modaclouds.eu](http://modaclouds.eu)), part of the project objective was to design a Decision Support System to analyze offerings of different cloud provider and compare them across different dimensions.<br>

Data Save module is part of the data gathering process of the MODAClouds DSS. This module is designed as a standalone CLI based application and can be implemented across different types of uses, not necessary connected to the Cloud Service providers scenario.<br>

One of the most prominent use cases apart for the MODAClouds context is to provide a subsystem of migration of the data between different types of the databases with possibility to "graphiphy" the dataset in order to explore graph exploration mechanisms to analyze the data. 

##Installation

###From source

*Please keep in mind that in order to compile this program from source, you need to have your environment ready for Scala development. You can download scala from [http://www.scala-lang.org/download](http://www.scala-lang.org/download)*

1. Clone the repository with `git clone git@github.com:CA-Labs/dss-data-save.git`
1. Navigate to the cloned repository and execute `sbt` command in the repository root folder
1. Execute `pack` task 
1. Type `exit` to come back to your standard shell

###Standalone Realease

*Please keep in mind that release assumes that you have Java JRE installed in your system. You can download Java from [http://www.oracle.com/technetwork/java/javase/downloads/index.html](http://www.oracle.com/technetwork/java/javase/downloads/index.html)*

1. Download the latest release from [https://github.com/CA-Labs/dss-data-save/releases/](https://github.com/CA-Labs/dss-data-save/releases/)
1. Once extracted you can execute the CLI `./bin/dss-data-save` with necessary arguments.

##Concept

DSS Data Save module is designed to consume standard input in JSON format. Within the current implementation the module is capable on saving the data to [Blueprints](https://github.com/tinkerpop/blueprints/wiki) capable graph databases. This functionality can be easily extended as the interfaces are designed in a generic fashion.<br>

As the DSS for the MODAClouds project was designed on top of [ArangoDB](https://www.arangodb.com) database, most of the testing was performed using this default back end. The currently supported graph database backends are [ArangoDB](https://www.arangodb.com), [Neo4j](http://neo4j.com) and [Titan](http://s3.thinkaurelius.com/docs/titan/current/) and each of these may require different configuration parameters.<br>

JSON input should consist of two main sections: 

* edges
* vertices

Example of the input: 

```json
{
    "edges": [
        {
            "__from": {
                "name": "A",
                "value": 1
            },
            "__to": {
                "name": "B",
                "value": 2
            },
            "label": "CONNECTED_TO"
        }
    ],
    "vertices": [
        {
            "name": "A",
            "value": 1
        },
        {
            "name": "B",
            "value": 2
        }
    ]
}
```

We should distinguish between `vertices` and `edges` arrays and the keys these objects use.

### Vertices
Each vertex is represented by a set of key/values following the [graph property model](https://github.com/tinkerpop/blueprints/wiki/Property-Graph-Model) specification. Keys starting with **`__`** string are meant to indicate an update action has to be carried out on that particular document. For instance:

```json
{
    "name": "A",
    "__value": 2
}
```

This example would try an *update* on field `value` (setting its property `value` to value `2`) for a  vertex which would be retrieved from a lookup by `name` field (if multiple vertices match this query, the update is only applied to the first one, so please **be sure** lookup criteria match exactly one vertex e.g. query by some unique field). On the other hand, the following example would mean an *insert* action:

```json
{
    "name": "A",
    "value": 2
}
```

### Edges

Same logic applies to edges objects with two concrete particularities, **`__from`** / **`__to`** and **`label`** fields. For instance, the following example would indicate an *insert* action has to be carried out:

```json
{
    "property": "C",
    "__from": {
        "name": "A"
    },
    "__to": {
        "name": "B"
    },
    "label": "CONNECTED_TO"
}
```

**`__from`** and **`__to`** fields are again a set of key/values which are used to uniquely identify two vertices and create a relationship between them with a certain **`label`**. Hence, this `label` value must be also specified (depending on the Blueprints implementation, it can be an empty string but we encourage you to use meaningful values).<br>

In the example above, we would query first two vertices (one with `name` equal to "A" and the other one with `name` equal to "B" and create a new edge between them with a `label` called "CONNECTED_TO" and a property `property` with value "C". For updates, any field other than **`__from`**, **`__to`** and **`label`** can be marked properly (in this case, property `property` would be updated):

```json
{
    "__property": "newC",
    "__from": {
        "name": "A"
    },
    "__to": {
        "name": "B"
    },
    "label": "CONNECTED_TO"
}
```

## Instructions

As commented above, this tool expects the JSON input from stdin and requires two options:
* `-d` or `--db`: the backend used for the data graph storage. Current supported options are `arangodb`, `neo4j` and `titan`.
* `-p` or `--properties`: the absolute path to the file containing the concrete underlying graph database options in a key/value fashion.

Regarding graph database properties, they should follow some specification. We should distinguish between **required** and **optional** options:
* **Required** options will always start with `blueprints.{backend}.{requiredProperty}` where `{backend}` is the chosen underlying graph database, and `{requiredProperty}` is a **required** property for such database.
* **Optional** options will always start with `blueprints.{backend}.conf` where `{backend}` is the chosen underlying graph database and `{optionalProperty} is an **optional** property for such database.

The following list specifies which properties are mandatory and which ones are optional for each currently supported graph database:
* ArangoDB:
    * **Required** (*blueprints.arangodb.**): `host`, `port`, `db`, `name`, `verticesCollection`, `edgesCollction`
    * **Optional** (*blueprints.arangodb.conf.\**): At this point, no further optional properties are supported.
* Neo4j:
    * **Required** (*blueprints.neo4j.**): `directory`
    * **Optional** (*blueprints.neo4j.conf.**): see custom [Neo4j options](https://github.com/tinkerpop/blueprints/wiki/Neo4j-Implementation)
* Titan
    * **Required** (*blueprints.titan.**): `storage.backend`, `storage.directory`
    * **Optional** (*blueprints.titan.conf.**): see custom [Titan options](https://github.com/thinkaurelius/titan/wiki/Graph-Configuration)

## Other comments

The following Blueprints implementations support transactions:
* Neo4j
* Titan

This means that if any error occurs when populating the graph database, graph changes can be rolled back and leave the graph in a clean state. Implementations which do not support it (currently ArangoDB) might not complete the graph population process properly.

##License
Copyright 2014-2015 CA Technologies - CA Labs EMEA

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.