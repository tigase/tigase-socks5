#!/bin/bash

[[ "$1" = "" ]] && \
  echo "Give me a path to the location where Tigase XMPP Server is installed" && \
  exit 1

[[ "$2" = "" ]] && \
  echo "Give me a path to the location where you want to have the database created" && \
  exit 1

java -Dij.protocol=jdbc:derby: -Dij.database="$2;create=true" \
		-Dderby.system.home=`pwd` \
		-cp $1/libs/derby.jar:$1/libs/derbytools.jar:$1/libs/tigase-socks5.jar:$1/jars/tigase-server.jar \
		org.apache.derby.tools.ij database/derby-schema.sql &> derby-db-create.txt