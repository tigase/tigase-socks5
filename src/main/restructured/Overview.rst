
Welcome to Tigase Socks5 Proxy guide

Tigase SOCKS5 component allows for file transfers to be made over a SOCKS5 proxy in accordance with `XEP-0065 SOCKS5 Bytestreams <http://xmpp.org/extensions/xep-0065.html>`__. This allows for some useful features such as: - transfer limits per user, domain, or global - recording transfers between users - quotas and credits system implementation

Overview
=========
Tigase Socks5 Proxy is implementation of Socks5 proxy described in `XEP-0065: SOCKS5 Bytestreams, in section 6. Mediated Connection <https://xmpp.org/extensions/xep-0065.html#mediated:>`__ which provides support for Socks5 proxy for file transfers between XMPP client behind NATs to Tigase XMPP Server.

Installation
---------------

Tigase SOCKS5 component comes built into the dist-max archives for Tigase XMPP server, and requires the component to be listed in config.tdsl file:

.. code:: dsl

   proxy {}

You will also need to decide if you wish to use database-based features or not. If you wish to simply run the socks5 proxy without features such as quotas, limits add the following line:

.. code:: dsl

   proxy {
       'verifier-class' = 'tigase.socks5.verifiers.DummyVerifier'
   }

This will enable the SOCKS5 Proxy without any advanced features. If you wish to use those features, see the configuration section below.


Database Preparation
----------------------

In order to use the more advanced features of the SOCKS5 Proxy Component, your database needs to be prepared with the proper schema prior to running the server.

You may either edit an existing database, or create a new database for specific use with the Proxy.

Edit Existing Database
^^^^^^^^^^^^^^^^^^^^^^^^^^^

You can add the proper schema to your existing database using the DBSchemaLoader utility included with Tigase. The database folder contains the schema file for your type of database.

First, backup your database before performing any actions and shut down Tigase XMPP Server.

Then from the Tigase installation directory run the following command:

.. code:: bash

   java -cp "jars/*" tigase.db.util.DBSchemaLoader -dbType {derby,mysql,postgresql,sqlserver} - dbHostname {db address} -dbName {dbname} -rootUser root -rootPass root -file database/{dbtype}-socks5-schema.sql

You should see the following dialogue

::

   LogLevel: CONFIG
   tigase.db.util.DBSchemaLoader        <init>            CONFIG     Properties: [{dbHostname=localhost, logLevel=CONFIG, dbType=derby, file=database/derby-socks5-schema.sql, rootUser=root, dbPass=tigase_pass, dbName=tigasedb, schemaVersion=7-1, rootPass=root, dbUser=tigase_user}]
   tigase.db.util.DBSchemaLoader        validateDBConnection    INFO       Validating DBConnection, URI: jdbc:derby:tigasedb;create=true
   tigase.db.util.DBSchemaLoader        validateDBConnection    CONFIG     DriverManager (available drivers): [[jTDS 1.3.1, org.apache.derby.jdbc.AutoloadedDriver@34a245ab, com.mysql.jdbc.Driver@3941a79c, org.postgresql.Driver@6e2c634b]]
   tigase.db.util.DBSchemaLoader        validateDBConnection    INFO       Connection OK
   tigase.db.util.DBSchemaLoader        validateDBExists    INFO       Validating whether DB Exists, URI: jdbc:derby:tigasedb;create=true
   tigase.db.util.DBSchemaLoader        validateDBExists    INFO       Exists OK
   tigase.db.util.DBSchemaLoader        loadSchemaFile      INFO       Loading schema from file: database/derby-socks5-schema.sql, URI: jdbc:derby:tigasedb;create=true
   tigase.db.util.DBSchemaLoader        loadSchemaFile      INFO        completed OK
   tigase.db.util.DBSchemaLoader        shutdownDerby       INFO       Validating DBConnection, URI: jdbc:derby:tigasedb;create=true
   tigase.db.util.DBSchemaLoader        shutdownDerby       WARNING    Database 'tigasedb' shutdown.
   tigase.db.util.DBSchemaLoader        printInfo           INFO

One this process is complete, you may begin using SOCKS5 proxy component.

Create New Database
^^^^^^^^^^^^^^^^^^^^^

If you want to create a new database for the proxy component and use it as a separate socks5 database, create the database using the appropriate schema file in the database folder. Once this is created, add the following line to your config.tdsl folder.

.. code:: dsl

   proxy {}

For example, a mysql database will have this type of URL: jdbc:mysql://localhost/SOCKS?user=root&password=root to replace database URL. For more options, check the database section of `this documentation <#databasePreperation>`__.
