#!/bin/bash
#
# Tigase Socks5 Component - SOCKS5 proxy component for Tigase
# Copyright (C) 2011 Tigase, Inc. (office@tigase.com)
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU Affero General Public License as published by
# the Free Software Foundation, version 3 of the License.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU Affero General Public License for more details.
#
# You should have received a copy of the GNU Affero General Public License
# along with this program. Look for COPYING file in the top folder.
# If not, see http://www.gnu.org/licenses/.
#


[[ "$1" = "" ]] && \
  echo "Give me a path to the location where Tigase XMPP Server is installed" && \
  exit 1

[[ "$2" = "" ]] && \
  echo "Give me a path to the location where you want to have the database created" && \
  exit 1

java -Dij.protocol=jdbc:derby: -Dij.database="$2;create=true" \
		-Dderby.system.home=`pwd` \
		-cp $1/jars/derby.jar:$1/jars/derbytools.jar:$1/jars/tigase-socks5.jar:$1/jars/tigase-server.jar \
		org.apache.derby.tools.ij database/derby-socks5-schema.sql &> derby-db-create.txt
