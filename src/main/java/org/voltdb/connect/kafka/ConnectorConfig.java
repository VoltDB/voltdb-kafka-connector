/*
 * The MIT License (MIT)
 *
 * Copyright (C) 2008-2016 VoltDB Inc.
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

    public static final String CONNECTOR_VERSION = "KafkaConnect-1.0.0";
    public static final String CONNECTOR_NAME = "name";
    public static final String CONNECTOR_CLASS ="connector.class";
    public static final String CONNECTOR_TASKS_MAX = "tasks.max";
    public static final String CONNECTOR_USER= "voltdb.connection.user";
    public static final String CONNECTOR_PASSWORD="voltdb.connection.password";
    public static final String CONNECTOR_SERVERS = "voltdb.servers";
    public static final String CONNECTOR_STORE_PROC = "voltdb.procedure";
    public static final String CONNECTOR_DATA_FORMATTER = "formatter.factory.class";
    public static final String CONNECTOR_DATA_FORMATTER_TYPE = "formatter.type";
    public static final String RECORD_CONVERT_CLASS = "data.converter.class";
    public static final String KERBEROS_AUTHENTICATION = "kerberos.authentication";
    public static final String AUTO_RECONNECTION = "auto.reconnect.onloss";
    public static final String FLUSH_RETRY_MAX = "flush.retry.max";
    public static final String RESPONSE_TIMEOUT_MAX = "response.timeout.max";
    public static final String PROCEDURE_TIMEOUT_MAX = "procedure.timeout.max";


    private static ConfigDef CONNFIG = new ConfigDef();
    static{
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
