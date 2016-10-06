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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.common.config.ConfigException;
import org.apache.kafka.connect.connector.Connector;
import org.apache.kafka.connect.connector.Task;
import org.apache.kafka.connect.errors.ConnectException;

/**
 * KafkaSinkConnector is a Kafka Connector implementation that moves data from Kafka to VoltDB.
 */
public class KafkaSinkConnector extends Connector {

    private Map<String, String> m_configProperties;

    @Override
    public String version() {
       return  ConnectorConfig.CONNECTOR_VERSION;
    }

    @Override
    public void start(Map<String, String> props) throws ConnectException {

        try {
            m_configProperties = props;
            String connectorName = ConnectorTask.getStringProperty(props, ConnectorConfig.CONNECTOR_NAME, null);
            if(connectorName == null){
                m_configProperties.put(ConnectorConfig.CONNECTOR_NAME,"KafkaSinkConnector");
            }

            int maxTasks = ConnectorTask.getIntProperty(props, ConnectorConfig.CONNECTOR_TASKS_MAX, 0);
            if(maxTasks == 0){
                m_configProperties.put(ConnectorConfig.CONNECTOR_TASKS_MAX, "1");
            }

            String connectorClass = ConnectorTask.getStringProperty(props, ConnectorConfig.CONNECTOR_CLASS, null);
            if(connectorClass == null){
                m_configProperties.put(ConnectorConfig.CONNECTOR_CLASS, "org.voltdb.connect.kafka.KafkaSinkConnector");
            }

            new ConnectorConfig(m_configProperties);
        } catch (ConfigException e) {
            throw new ConnectException("Couldn't start Connector due to configuration error", e);
        }
    }

    @Override
    public Class<? extends Task> taskClass() {
        return ConnectorTask.class;
    }

    @Override
    public List<Map<String, String>> taskConfigs(int maxTasks) {
        List<Map<String, String>> taskConfigs = new ArrayList<>();
        Map<String, String> taskProps = new HashMap<>();
        taskProps.putAll(m_configProperties);
        for (int i = 0; i < maxTasks; i++) {
            taskConfigs.add(taskProps);
        }
        return taskConfigs;
    }

    @Override
    public void stop() throws ConnectException {

    }

    @Override
    public ConfigDef config() {
        return ConnectorConfig.getConfig();
    }
}
