#### Building and Installing VoltDB Kafka Sink Connector

The VoltDB Kafka Sink Connector provides a simple, continuous link from a Kafka topic or set of topics to a VoltDB table or set of VoltDB tables.

Build and installation instructions follow.

See the [README](https://github.com/VoltDB/voltdb-kafka-connector) for configuration steps and instructions for running a simple demonstation application.

The VoltDB Kafka Sink Connector is also available as a [download](https://www.voltdb.com/download-confluentconnector) 

#### Building the Connector from source

* Ensure `gradle.properties` file contains valid values

	```bash
	echo voltdbhome=/voltdb/home/dirname > gradle.properties
    set the `voltdbhome` property to the base directory where your VoltDB is installed
    set the `dbversion` property to the VoltDB build version
	```

* Compile artifacts

	```bash
    ./gradlew clean shadowJar
	```

* To setup an Eclipse project, run gradle as follows

	```bash
    ./gradlew cleanEclipse eclipse
	```
	then import it into your Eclipse workspace by using File->Import projects menu option, and add connect-api-0.10.0.0.jar, connect-json-0.10.0.0.jar and kafka-clients-1.10.0.0.jar to the classpath.

#### Installing the Connector 

All the scripts for running Kafka Connect use the CLASSPATH environment variable if it is set.
* copy the built jar from `build/libs` to a directory where the Kafka server is installed or accessible, for example: path-to-kafka-root/voltdb/
* copy config/voltdb-sink-connector.properties, voltdb-sink-connector.json to a folder and configure the connector properties as described in the next section
* configure connect-standalone.properties or connect-distributed.properties under path-to-kafka-root/config. Note that Apache or Confluent Kafka distributions may have configuration files in different locations.
For more details please see http://docs.confluent.io/3.0.1/connect/index.html
