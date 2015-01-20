#Decistion Support System - Data Save Module

##Overview
In the context of a project funded by the European Union called MODAClouds ([www.modaclouds.eu](http://modaclouds.eu)), part of the project objective was to design a Decision Support System to analyze offerings of different cloud provider and compare them across different dimensions.

Data Save module is part of the data gathering process of the MODAClouds DSS. This module is designed as a standalone CLI based application and can be implemented across different types of uses, not necessary connected to the Cloud Service providers scenario. 

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
DSS Data Save module is designed to consume standard input in JSON format. Within the current implementation the module is capable on saving the data to [Blueprints](https://github.com/tinkerpop/blueprints/wiki) capable graph databases. This functionality can be easily extended as the interfaces are designed in a generic fashion. 

As the DSS for the MODAClouds project was designed on top of [arangoDB](https://www.arangodb.com) database, most of the testing was performed using this default back end.

JSON input should consist of two main sections: 

* edges
* vertices

Example of the input: 
```json
{
    "edges": [
        {
            "__from": {
                "name": "value",
                "property2": "value2"
            },
            "__to": {
                "name": "value",
                "property1": "value1"
            },
            "metric": 5
        }
    ],
    "vertices": [
        {
            "__property1": "value1",
            "property2": "value2"
        }
    ]
}
```



##Instructions

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
