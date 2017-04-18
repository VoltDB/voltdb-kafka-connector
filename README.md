#### VoltDB Kafka Sink Connector

The VoltDB Kafka Sink Connector provides a simple, continuous link from a Kafka topic or set of topics to a VoltDB table or set of VoltDB tables.

The connector consumes Kafka log messages and invokes VoltDB stored procedures, either custom or default, to parse and insert the messages into VoltDB tables.

When the Kafka messages have schema metadata, the schema is used to convert the values. If no schema metadata is present, the message will be converted as configured. 

The VoltDB Kafka Sink Connector is available as source at [Github](https://github.com/VoltDB/voltdb-kafka-connector) or as binary [HERE](https://www.voltdb.com/download-confluentconnector)

Following sections discuss installing and configuring the Connector and offer an example application that shows the Connector in action with VoltDB and a Kafka installation.

#### Extract files from the download file
* Untar the download file:
```
    $ tar xzvf voltdb-kafka-sink-connector-1.0.tar.gz
```
* add the directory to the Java CLASSPATH
```
    $ CLASSPATH=<dir that contains voltdb-sink-connector-1.0-all.jar>:$CLASSPATH
```
* template configuration files are in the "config" directory.

#### Installing the Connector 

* configure voltdb-sink-connector.properties in the "config" directory. You may copy the "config" directory to a location within the Kafka installation. Note that Apache or Confluent Kafka distributions may have configuration files in different locations.
For more details please see http://docs.confluent.io/3.0.1/connect/index.html

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
   * Formatter and converter properties are used if the kafka record does not have valid value schema. 
   * Also when kafka records does not have schema you must modify following connector properties (standalone or distributed)
       * **key.converter** Update it to org.apache.kafka.connect.storage.StringConverter
       * **value.converter** Update it to org.apache.kafka.connect.storage.StringConverter
       * **key.converter.schemas.enable** Disable schema by setting it to false
       * **value.converter.schemas.enable** Disable schema by setting it to false
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
            "kerberos.authentication":""
        }
    }
```
When kafka records does not have schema you must add following connector properties to config object
```
    {
        "name":"KafkaSinkConnector",
        "config":{
            ...
            "key.converter":"org.apache.kafka.connect.storage.StringConverter",
            "value.converter":"org.apache.kafka.connect.storage.StringConverter",
            "key.converter.schemas.enable":"false",
            "value.converter.schemas.enable":"false",
            ...
        }
    }
```

#### Running a Sample Application using VoltDB kafka Sink connector

* Download and install [Kafka](http://kafka.apache.org/downloads.html) 0.10.0.0 or later
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


    ```
   For distributed mode, create topics for shared storage offset, connector config and status.

   $./bin/kafka-topics.sh --create --zookeeper localhost:2181 --topic connect-configs --replication-factor 1 --partitions 1
   $./bin/kafka-topics.sh --create --zookeeper localhost:2181 --topic connect-status --replication-factor 1 --partitions 10
   $./bin/kafka-topics.sh --create --zookeeper localhost:2181 --topic connect-offsets --replication-factor 1 --partitions 50
    ```

* Start connector

    set **voltdb.procedure=stock.insert** for the connector.
	set **topics=connect-test** for the kafka topic to consume from.
   ```
	standalone mode:
    
  	$export CLASSPATH=path-to-kafka-root/voltdb/voltdb-sink-connector-1.0-all.jar 	
  	
  	$./bin/connect-standalone.sh  config/connect-standalone.properties  voltdb/voltdb-sink-connector.properties
    ```
    
    ```
    distributed mode:
    
    Unlike standalone mode where connector configurations are passed in via the command line, interaction with a distributed-mode cluster is via the REST API. 
 
  	$export CLASSPATH=path-to-kafka-root/voltdb/voltdb-sink-connector-1.0-all.jar 	 	
  	
  	$./bin/connect-distributed.sh  config/connect-distributed.properties
    
    Connector registration:
    $curl -X POST -H "Content-Type: application/json" --data @voltdb/voltdb-sink-connector.json http://localhost:8083/connectors
    
    Connector verification:
    $curl -i -X GET "http://localhost:8083/connectors/KafkaSinkConnector"
    
    Connector removal (remove sink connector once ingesting of data is completed):
    $curl -i -X DELETE "http://localhost:8083/connectors/KafkaSinkConnector"
    
	 ```
* Start Kafka producer:

   ```
   $./bin/kafka-console-producer.sh --broker-list localhost:9092 --topic connect-test  
   ```
  
  The topic must match the topic the connector is listening to.
  
  Enter "6,GOOG,1000,BUY,50000"
  
  Run a select query against VoltDB to verify a row is added to table stock.
