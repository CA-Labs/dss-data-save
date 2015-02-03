#!/bin/sh
NEO4JSH=`which neo4j-shell` 
ARANGOSH=`which arangosh`

# Drop Neo4j data
${NEO4JSH} << EOF
  match (n) optional match(n)-[r]-() delete n,r;
EOF

# Drop ArangoDB data
${ARANGOSH} << EOF
db._useDatabase('test')
db._collection('testVertices').truncate()
db._collection('testEdges').truncate()
EOF
