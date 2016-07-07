### VoltDB Sink Connector

The connector is for moving data from Kafka to VoltDB.

### How to build artifacts and setup Eclipse

* Install Gradle

On a Mac if you have [Homebrew](http://brew.sh/) setup then simply install the gradle bottle

```bash
brew install gradle
```

On Linux setup [SDK](http://sdkman.io/), and install gradle as follows

```bash
sdk install gradle
```

* Create `gradle.properties` file and set the `voltdbhome` property
   to the base directory where your VoltDB is installed

```bash
echo voltdbhome=/voltdb/home/dirname > gradle.properties
```

* Invoke gradle to compile artifacts

```bash
gradle shadowJar
```

* To setup an eclipse project run gradle as follows

```bash
gradle cleanEclipse eclipse
```
then import it into your eclipse workspace by using File->Import projects menu option. 
then add connect-api-0.10.0.0.jar, connect-json-0.10.0.0.jar and kafka-clients-1.10.0.0.jar to classpath.

### Installing Connector 

* All the scripts for running Kafka Connect will use the CLASSPATH environment variable if it is set when they are invoked.
* Copy the built jar from `build/libs` to a directory  where Kafka server is installed or accessible. 
  example: path-to-kafka-root/voltdb/
* copy /config/voltdb-sink-connector.properties to a folder and configure connector properties
* configure connect-standalone.properties or connect-distributed.properties under path-to-kafka-root/config/

### Distributed Connect Properties
- `bootstrap.servers` (mandatory) Kafka servers the connector will connect to
- `group.id` (mandatory, default connect-cluster) unique name for the cluster, used in forming the Connect cluster group.
- `offset.storage.topic` (mandatory, default connect-offsets) The topic to store offset data for connectors in. This must be the same with the same group.id
- `config.storage.topic` (mandatory, default connect-configs) The topic to store connector and task configuration data in. This must be the same with the same group.id.
   Manually create the topic to ensure single partition if needed.
- `status.storage.topic` (mandatory, default connect-status) Topic to use for storing statuses.
- `offset.flush.interval.ms` (default: 60000) The time interval between offset commits.
- `rest.port` the port the REST interface listens on for HTTP requests.
- `topic` Specify a list of topics to use as input for this connector
- `tasks.max` The maximum number of tasks that should be created for this connector

###Standalone Connect Properties
- `bootstrap.servers` (mandatory) Kafka servers the connector will connect to
- `offset.flush.interval.ms` (default: 60000) The time interval between offset commits.
- `offset.storage.file.filename` The file to store connector offsets in.
- `rest.port` the port the REST interface listens on for HTTP requests.
- `tasks.max` The maximum number of tasks that should be created for this connector

###Connect Properties
- `name` (default:KafkaSinkConnector) Unique name for the connector
- `connector.class` (default:org.voltdb.connect.kafka.KafkaSinkConnector) The Java class for the connector
- `voltdb.connection.user` The user name to connect VoltDb
- `voltdb.connection.password` The password to connect VoltDB
- `voltdb.servers` (mandatory) A list of Voltdb server nodes with ',' as delimiter. example: server1:21212,server2:21212
- `voltdb.procedure` Specify the procedure name to be used to insert data to VoltDB.
- `formatter.factory.class` Specify the data formatter factory used to convert Kafka data into the format required by VoltDB procedure.
   org.voltdb.connect.formatter.CSVFormatterFactory is used as default.
- `formatter.type` The type of formatter, such as csv, tsv.
- `data.converter.class` The Java class for data conversion from SinkRecord.


###Instructions for running

* Download and install kafka 0.10.0.0 or the latest
* Start VoltDB, create correct table and store procedure for the connector:

```sql
  	add a table to VoltDB for testing:
    CREATE TABLE STOCK (
		ID integer not null,
		Symbol varchar(50),
		Quantity integer,
		TradeType varchar(50),
		price varchar(50),
		constraint pk_stock PRIMARY KEY(ID)
   );
```
* Start zookeeper. Issue the following command under Kafka root directory:
	```
  	./bin/zookeeper-server-start.sh config/zookeeper.properties
  	```
* Start Kafka server.  Issue the command under Kafka root directory:
	```
   	./bin/kafka-server-start.sh config/server.properties
   	```
* start the connector
    set voltdb.procedure=stock.insert for the connector.
    Issue the following command under Kafka root directory:
    ```
  	export CLASSPATH=path-to-kafka-root/voltdb/voltdb-sink-connector-1.0-SNAPSHOT-all.jar 	
  	./bin/connect-standalone.sh  onfig/connect-standalone.properties  voltdb/voltdb-sink-connector.properties
    ```
* Start Kafka producer, Issue the following command under Kafka root directory:
   ```
  ./bin/kafka-console-producer.sh --broker-list localhost:9092 --topic connect-test  
  ```
  
  The topic must match the topic the connector is listening to.
  
  Enter "6,GOOG,1000,BUY,50000"
  
  Run a select query agaisnt VoltDB to verify a row is added to table stock.
