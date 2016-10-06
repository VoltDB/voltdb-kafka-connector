#### VoltDB Sink Connector

The connector is for moving data from Kafka to VoltDB.

#### Setup

* Install Gradle

	On a Mac if you have [Homebrew](http://brew.sh/) setup then simply install the gradle bottle

	```bash
	brew install gradle
	```
	On Linux setup [SDK](http://sdkman.io/), and install gradle as follows
	
	```bash
	sdk install gradle
	```
* Create `gradle.properties` file

	```bash
	echo voltdbhome=/voltdb/home/dirname > gradle.properties
    set the `voltdbhome` property to the base directory where your VoltDB is installed
    set the `dbversion` property to the VoltDB build version
	```

* Compile artifacts

	```bash
    gradle prepare zip shadowJar
	```

* To setup an eclipse project run gradle as follows

	```bash
    gradle cleanEclipse eclipse
	```
	then import it into your eclipse workspace by using File->Import projects menu option, and add connect-api-0.10.0.0.jar, connect-json-0.10.0.0.jar and kafka-clients-1.10.0.0.jar to classpath.

#### Installing Connector 

* All the scripts for running Kafka Connect will use the CLASSPATH environment variable if it is set when they are invoked.
* Copy the built jar from `build/libs` to a directory  where Kafka server is installed or accessible. 
  example: path-to-kafka-root/voltdb/
* copy /config/voltdb-sink-connector.properties, voltdb-sink-connector.json to a folder and configure connector properties
* configure connect-standalone.properties or connect-distributed.properties under path-to-kafka-root/config/

#### Distributed Connect Properties (connect-distributed.properties)
- **bootstrap.servers** (mandatory) Kafka servers the connector will connect to.
- **group.id** (mandatory) The unique name for the cluster, used in forming the Connect cluster group.
- **offset.storage.topic** (mandatory) The Kafka topic to store connector offset state in. 
  Although this topic can be auto-created if your cluster has auto topic creation enabled, 
  it is highly recommended that you create it before starting the Kafka Connect cluster. 
  To support large Kafka Connect clusters, this topic should have a large number of partitions and highly replicated.
  This must be the same with the same group.id
- **config.storage.topic** (mandatory) The Kafka topic to store connector and task configuration state in. 
  Although this topic can be auto-created if your cluster has auto topic creation enabled, 
  it is highly recommended that you create it before starting the Kafka Connect cluster. 
  This topic should always have a single partition and be highly replicated.
  This must be the same for with the same group.id
- **status.storage.topic** (mandatory) Topic used for the storage of connector statuses will be created by Kafka.
- **offset.flush.interval.ms** (default: 60000) The time interval between offset commits.
- **topics** A list of topics to use as input for this connector.
- **tasks.max** The maximum number of tasks that should be created for this connector.
- **rest.port** (default: 8083) The port of the Kafka's REST interface listens on for HTTP requests
- **key.converter**=org.voltdb.connect.json.JsonTransformer
- **value.converter**=org.voltdb.connect.json.JsonTransformer
- **internal.key.converter**=org.voltdb.connect.json.JsonTransformer
- **internal.value.converter**=org.voltdb.connect.json.JsonTransformer

  Current release of VoltDB Kafka sink connector does not support schema. Set the following properties to false:
  *key.converter.schemas.enable*, *value.converter.schemas.enable*, *internal.key.converter.schemas.enable* and
  *internal.value.converter.schemas.enable*.


#### Standalone Connect Properties (connect-standalone.properties)
- **bootstrap.servers** (mandatory) Kafka servers the connector will connect to.
- **offset.flush.interval.ms** (default: 60000) The time interval between offset commits.
- **offset.storage.file.filename** The file to store connector offsets.
- **tasks.max** The maximum number of tasks that should be created for this connector
- **rest.port** (default: 8083) The port the REST interface listens on for HTTP requests.
- **key.converter**=org.voltdb.connect.json.JsonTransformer
- **value.converter**=org.voltdb.connect.json.JsonTransformer
- **internal.key.converter**=org.voltdb.connect.json.JsonTransformer
- **internal.value.converter**=org.voltdb.connect.json.JsonTransformer

  Current release of VoltDB Kafka sink connector does not support schema. Set the following properties to false:
  *key.converter.schemas.enable*, *value.converter.schemas.enable*, *internal.key.converter.schemas.enable* and
  *internal.value.converter.schemas.enable*.


#### Connect Properties (voltdb-sink-connector.properties)
- **name** (default:KafkaSinkConnector) Unique name for the connector
- **connector.class** (default:org.voltdb.connect.kafka.KafkaSinkConnector) The Java class for the connector
- **voltdb.connection.user** The user name to connect VoltDB
- **voltdb.connection.password** The password to connect VoltDB
- **voltdb.servers** (mandatory) A list of Voltdb server nodes with ',' as delimiter. example: server1:21212,server2:21212
- **voltdb.procedure** (mandatory) The procedure name to be used to insert data to VoltDB.
- **formatter.factory.class** The data formatter factory used to convert non-schema Kafka data into the format required by VoltDB procedure.
   *org.voltdb.connect.formatter.CSVFormatterFactory* is used as default.
- **formatter.type** The type of formatter, such as csv, tsv.
- **data.converter.class** The Java class for data conversion from SinkRecord. *org.voltdb.connect.converter.JsonDataConverter* as default.
- **kerberos.authentication** The authentication module if enabled.

#### Connect JSON Properties (voltdb-sink-connector.json)

The json file contains the same properties as voltdb-sink-connector.properties. Example:
```
	{
   		"name":"KafkaSinkConnector",
   		"config":{
      		"connector.class":"org.voltdb.connect.kafka.KafkaSinkConnector",
      		"tasks.max":"1",
      		"voltdb.servers":"localhost:21212",
      		"voltdb.procedure":"TestTable.insert",
      		"formatter.factory.class":"org.voltdb.connect.formatter.CSVFormatterFactory",
      		"voltdb.connection.user":"",
      		"voltdb.connection.password":"",
      		"formatter.type":"csv",
      		"data.converter.class":"org.voltdb.connect.converter.JsonDataConverter",
      		"topics":"testTopic",
      		"kerberos.authentication":"false"
   		}
	}
```

#### Instructions for running

* Download and install [Kafka](http://kafka.apache.org/downloads.html) 0.10.0.0 or the latest
* Start VoltDB, create correct table and store procedure for the connector:

```sql
  	A sample table to VoltDB for testing:
    CREATE TABLE STOCK (
		ID integer not null,
		Symbol varchar(50),
		Quantity integer,
		TradeType varchar(50),
		price float,
		constraint pk_stock PRIMARY KEY(ID)
   );
```
* Start zookeeper

	```
  	$./bin/zookeeper-server-start.sh config/zookeeper.properties
  	```
* Start Kafka server

	```
   	$./bin/kafka-server-start.sh config/server.properties
   	```
* create Kafka topic for the connector

    ```
   $./bin/kafka-topics.sh --create --zookeeper localhost:2181 --replication-factor 1 --partitions 1 --topic connect-test
    ```
* Start connector

    set **voltdb.procedure=stock.insert** for the connector.
   ```
	standalone mode:
    
  	$export CLASSPATH=path-to-kafka-root/voltdb/voltdb-sink-connector-1.0-SNAPSHOT-all.jar 	
  	
  	$./bin/connect-standalone.sh  config/connect-standalone.properties  voltdb/voltdb-sink-connector.properties
    ```
    
    ```
    distributed mode:
    
    Unlike standalone mode where connector configurations are passed in via the command line, interaction with a distributed-mode cluster is via the REST API. 
 
  	$export CLASSPATH=path-to-kafka-root/voltdb/voltdb-sink-connector-1.0-SNAPSHOT-all.jar 	 	
  	
  	$./bin/connect-distributed.sh  config/connect-distributed.properties
    
    Connector registration:
    $curl -X POST -H "Content-Type: application/json" --data @voltdb/voltdb-sink-connector.json http://localhost:8083/connectors
    
    Connector verification:
    $curl -i -X GET "http://localhost:8083/connectors/KafkaSinkConnector"
    
    Connector removal:
    $curl -i -X DELETE "http://localhost:8083/connectors/KafkaSinkConnector"
    
	 ```
* Start Kafka producer:

   ```
   $./bin/kafka-console-producer.sh --broker-list localhost:9092 --topic connect-test  
   ```
  
  The topic must match the topic the connector is listening to.
  
  Enter "6,GOOG,1000,BUY,50000"
  
  Run a select query against VoltDB to verify a row is added to table stock.
