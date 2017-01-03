/*
 * The MIT License (MIT)
 *
 * Copyright (C) 2008-2017 VoltDB Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.voltdb.connect.kafka;

import java.util.Map;

import org.apache.kafka.common.config.AbstractConfig;
import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.common.config.ConfigDef.Importance;
import org.apache.kafka.common.config.ConfigDef.Type;

/**
 * Connector configurations
 *
 */
public class ConnectorConfig extends AbstractConfig {

    /**
     * <code>CONNECTOR_VERSION</code> The connector version
     */
    public static final String CONNECTOR_VERSION = "KafkaConnect-1.0.0";

    /**
     * <code>CONNECTOR_NAME</code> Property for unique name for the connector
     */
    public static final String CONNECTOR_NAME = "name";

    /**
     * <code>CONNECTOR_CLASS</code> Property for Java class for the connector
     */
    public static final String CONNECTOR_CLASS ="connector.class";

    /**
     * <code>CONNECTOR_TASKS_MAX</code> Property for the maximum number of tasks that should be created for this connector
     */
    public static final String CONNECTOR_TASKS_MAX = "tasks.max";

    /**
     * <code>CONNECTOR_USER</code> Property for the user name to connect VoltDB
     */
    public static final String CONNECTOR_USER = "voltdb.connection.user";

    /**
     * <code>CONNECTOR_PASSWORD</code> Property for the password to connect VoltDB
     */
    public static final String CONNECTOR_PASSWORD ="voltdb.connection.password";

    /**
     * <code>CONNECTOR_SERVERS</code> Property for VoltDB server nodes with ',' as delimiter. example: server1:21212,server2:21212
     */
    public static final String CONNECTOR_SERVERS = "voltdb.servers";

    /**
     * <code>CONNECTOR_STORE_PROC</code> Property for the procedure name to be used to insert data to VoltDB.
     */
    public static final String CONNECTOR_STORE_PROC = "voltdb.procedure";

    /**
     * <code>CONNECTOR_DATA_FORMATTER</code> Property for the data formatter factory used to convert Kafka data into the format required by VoltDB procedure.
     * org.voltdb.connect.formatter.CSVFormatterFactory is the default for data transformation in CSV format.
     * Implement AbstractFormatterFactory for the data format other than CSV.
     */
    public static final String CONNECTOR_DATA_FORMATTER = "formatter.factory.class";

    /**
     * <code>CONNECTOR_DATA_FORMATTER_TYPE</code> Property for the type of data format
     */
    public static final String CONNECTOR_DATA_FORMATTER_TYPE = "formatter.type";

    /**
     * <code>RECORD_CONVERT_CLASS</code> Property for the Java class for data conversion from SinkRecord to byte stream. Application may utilize custom converter class if needed
     * org.voltdb.connect.converter.JsonDataConverter is the default.
     */
    public static final String RECORD_CONVERT_CLASS = "data.converter.class";

    /**
     * <code>KERBEROS_AUTHENTICATION</code> If true, use Kerberos authentication module
     */
    public static final String KERBEROS_AUTHENTICATION = "kerberos.authentication";

    /**
     * <code>AUTO_RECONNECTION</code> Property for auto-reconnect on connection loss
     */
    public static final String AUTO_RECONNECTION = "auto.reconnect.onloss";

    /**
     * <code>RESPONSE_TIMEOUT_MAX</code> Property for the maximal timeout for VoltDB procedure response
     */
    public static final String RESPONSE_TIMEOUT_MAX = "response.timeout.max";

    /**
     * <code>PROCEDURE_TIMEOUT_MAX</code> Property for the maximal timeout for VoltDB procedure
     */
    public static final String PROCEDURE_TIMEOUT_MAX = "procedure.timeout.max";


    private static ConfigDef CONNFIG = new ConfigDef();
    static {
        CONNFIG.define(CONNECTOR_NAME, Type.STRING, Importance.HIGH, "Unique connector name.");
        CONNFIG.define(CONNECTOR_CLASS, Type.STRING, Importance.HIGH, "The Java class for the connector.");
        CONNFIG.define(CONNECTOR_TASKS_MAX, Type.INT, Importance.HIGH, "The maximum number of tasks that should be created for this connector");
        CONNFIG.define(CONNECTOR_USER, Type.STRING, Importance.LOW, "The user name to connect VoltDb");
        CONNFIG.define(CONNECTOR_PASSWORD, Type.STRING, Importance.LOW, "The password to connect VoltDB");
        CONNFIG.define(CONNECTOR_SERVERS, Type.STRING, Importance.HIGH, "A list of Voltdb server nodes");
        CONNFIG.define(CONNECTOR_STORE_PROC, Type.STRING, Importance.HIGH, "The procedure name to be used to insert data to VoltDB.");
        CONNFIG.define(CONNECTOR_DATA_FORMATTER, Type.STRING, "org.voltdb.connect.formatter.CSVFormatterFactory", Importance.LOW, "The data formatter factory used to convert Kafka data");
        CONNFIG.define(CONNECTOR_DATA_FORMATTER_TYPE, Type.STRING, "csv", Importance.LOW, "The type of formatter, such as csv, tsv.");
        CONNFIG.define(RECORD_CONVERT_CLASS, Type.STRING, "org.voltdb.connect.converter.JsonDataConverter", Importance.LOW, "The Java class for data conversion from SinkRecord");
    }


    public static  ConfigDef getConfig() {
        return CONNFIG;
    }

    public ConnectorConfig(Map<String, String> props) {
        super(CONNFIG, props);
    }

}
