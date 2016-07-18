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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.config.ConfigException;
import org.apache.kafka.connect.errors.ConnectException;
import org.apache.kafka.connect.errors.RetriableException;
import org.apache.kafka.connect.sink.SinkRecord;
import org.apache.kafka.connect.sink.SinkTask;
import org.voltcore.logging.VoltLogger;
import org.voltdb.client.Client;
import org.voltdb.client.ClientConfig;
import org.voltdb.client.ClientFactory;
import org.voltdb.client.ClientResponse;
import org.voltdb.client.ClientStatusListenerExt;
import org.voltdb.client.NoConnectionsException;
import org.voltdb.client.ProcedureCallback;
import org.voltdb.connect.converter.Converter;
import org.voltdb.connect.formatter.AbstractFormatterFactory;
import org.voltdb.importer.formatter.FormatException;
import org.voltdb.importer.formatter.Formatter;

import com.google_voltpatches.common.base.Throwables;
import com.google_voltpatches.common.collect.Sets;

/**
 *
 * ConnectorTaks handles the incoming data from Kafka and persist them to VoltDB.
 *
 */
public class ConnectorTask extends SinkTask {

    private static final VoltLogger LOGGER = new VoltLogger("KafkaSinkConnector");

    /**
     * <code>m_formatter</code> The formatter to converting kafka data into the data format required by Volt procedure
     */
    private Formatter<String> m_formatter;

    /**
     * <code>m_converter</code> Convert the value of SinkRecord to byte array.
     */
    private Converter m_converter;

    /**
     * <code>m_client</code> Volt client
     */
    private Client m_client;

    /**
     * <code>m_procName</code> Volt store procedure name
     */
    private String m_procName;

    /**
     * <code>m_currentBatchCnt</code>The record count pushed from Kafka from last offset flush
     */
    private AtomicLong m_currentBatchCnt = new AtomicLong(0);

    /**
     * <code>m_flushSet</code> Contains all the topic partitions and message offsets pushed from Kafka from last offset flush
     */
    private Set<String> m_flushSet = Sets.newConcurrentHashSet();

    /**
     * <code>m_serverConnected</code> A flag indicates if the VoltDb client is connected to servers.
     */
    private AtomicBoolean m_connectionLost = new AtomicBoolean(false);

    public ConnectorTask() {

    }

    @Override
    public void start(Map<String, String> props) {

        String username = getStringProperty(props, ConnectorConfig.CONNECTOR_USER, "");
        String password = getStringProperty(props, ConnectorConfig.CONNECTOR_PASSWORD, "");

        ClientConfig config = new ClientConfig(username, password, new ClientStatusListener(m_connectionLost));

        String autoReconnect = getStringProperty(props, ConnectorConfig.AUTO_RECONNECTION, "true");
        if("true".equalsIgnoreCase(autoReconnect)){
             config.setReconnectOnConnectionLoss(true);
        }

        config.setConnectionResponseTimeout(getIntProperty(props, ConnectorConfig.RESPONSE_TIMEOUT_MAX, 0));
        config.setProcedureCallTimeout(getIntProperty(props, ConnectorConfig.PROCEDURE_TIMEOUT_MAX, 0));

        String kerberos = getStringProperty(props, ConnectorConfig.KERBEROS_AUTHENTICATION, null);
        if( kerberos != null){
            config.enableKerberosAuthentication(kerberos);
        }

        m_procName = getStringProperty(props, ConnectorConfig.CONNECTOR_STORE_PROC, null);
        if(m_procName == null){
            throw new ConfigException("Missing store procesure.");
        }

        String servers = props.get(ConnectorConfig.CONNECTOR_SERVERS);
        if(servers == null){
            throw new ConfigException("Missing VoltDB hosts.");
        }

        List<String> serverList = Arrays.asList(servers.split(","));
        if (serverList == null || serverList.isEmpty()) {
            throw new ConfigException("Missing VoltDB hosts");
        }

        String formatterClass = getStringProperty(props, ConnectorConfig.CONNECTOR_DATA_FORMATTER, "org.voltdb.connect.formatter.CSVFormatterFactory");
        String formatterType =  getStringProperty(props, ConnectorConfig.CONNECTOR_DATA_FORMATTER_TYPE, "csv");
        String converterClass = getStringProperty(props, ConnectorConfig.RECORD_CONVERT_CLASS, "org.voltdb.connect.converter.JsonDataConverter");

        Properties formatProperties = new Properties();
        formatProperties.putAll(props);

        try {
            Class<?> className = Class.forName(formatterClass);
            AbstractFormatterFactory factory = (AbstractFormatterFactory) className.newInstance();
            m_formatter = factory.create(formatterType, formatProperties);

            className = Class.forName(converterClass);
            m_converter = (Converter) className.newInstance();
        } catch (ClassNotFoundException  | InstantiationException | IllegalAccessException e) {
            LOGGER.error(String.format("Can't create formatter or converter: %s", e.getMessage()));
            throw new ConnectException(e.getMessage());
        }
        m_client = ClientFactory.createClient(config);
        connect(serverList);
    }

    @Override
    public void put(Collection<SinkRecord> records) {

        if (records.isEmpty()) {
            return;
        }

        for (SinkRecord record : records) {

            if(m_connectionLost.get()){

                //trigger Kafka consumer to pause and retry.
                throw new RetriableException("All client connections to VoltDb have been lost.");
            }

            String partitionOffset = Integer.toString(record.kafkaPartition()) + Long.toString(record.kafkaOffset());
            m_currentBatchCnt.getAndIncrement();

            String data = new String(m_converter.convert(record), StandardCharsets.UTF_8);
            Object[] formattedData = null;;
            try{
                formattedData = m_formatter.transform(data);
            } catch (FormatException e) {
                LOGGER.error(String.format("Error for offset: %s",data), e);
                continue;
            }

            try {
                ConnectorProcedureCallback cb = new ConnectorProcedureCallback(m_flushSet, partitionOffset);
                if (!m_client.callProcedure(cb, m_procName, formattedData)) {
                    if(m_flushSet.contains(partitionOffset)){
                        m_flushSet.remove(partitionOffset);
                    }
                }
            } catch (Exception e){
                if(m_flushSet.contains(partitionOffset)){
                    m_flushSet.remove(partitionOffset);
                }
                LOGGER.error(String.format("Procedure error for offset %s", partitionOffset), e);
            }
        }
    }

    @Override
    public void flush(Map<TopicPartition, OffsetAndMetadata> offsets) {

        if(m_currentBatchCnt.get() == 0){
            return;
        }

        if(m_connectionLost.get()){
            throw new ConnectException("All client connections to VoltDB have been lost.");
        }

        try {
            m_client.drain();
        } catch (NoConnectionsException | InterruptedException e) {
            LOGGER.error(e);
            throw new ConnectException("ConnectorTask flush: there are uncommited records.");
        }

        if(m_flushSet.size() == 0){
            if(LOGGER.isInfoEnabled()){
                LOGGER.info(String.format("Flush offset for batch count: %d", m_currentBatchCnt.get()));
            }
            m_currentBatchCnt.set(0);
        }else{
            m_currentBatchCnt.set(0);
            m_flushSet.clear();
            //trigger the connect framework to re-put the batch records
            throw new ConnectException("ConnectorTask flush: there are uncommited records.");
        }
    }

    @Override
    public void stop() {
        if(m_client != null){
            try {
                m_client.drain();
                m_client.close();
            } catch (NoConnectionsException | InterruptedException e) {
                Throwables.propagate(e);
            }
        }
    }

    @Override
    public String version() {
        return ConnectorConfig.CONNECTOR_VERSION;
    }

    /**
     * connect to VoltDB servers
     */
    private void connect( List<String> serverList){

        for(String host : serverList){
            try {
                m_client.createConnection(host.trim());
            } catch (IOException e) {
                LOGGER.error(String.format("Could not create connection to %s", host), e);
                Throwables.propagate(e);
            }
        }

    }

    public static int getIntProperty(Map<String, String> props, String propName, int defaultValue){

        String valString = props.get(propName);
        if(valString != null){
            valString = valString.trim();
            if(!valString.isEmpty()){
                try{
                    int val = Integer.parseInt(valString);
                    if (val >= 0){
                        return val;
                    }
                } catch (NumberFormatException e){
                    throw new ConfigException(String.format("Error for property %s:", propName, e.getMessage()));
                }
            }
        }
        return defaultValue;
    }

    public static String  getStringProperty(Map<String, String> props, String propName, String defaultValue){
        String val = defaultValue;
        String valString = props.get(propName);
        if(valString != null){
            valString= valString.trim();
            if(!valString.isEmpty()){
                val = valString;
            }
        }
        return val;
    }

    /**
     * VoltDB procedure callback
     *
     */
    private final static class ConnectorProcedureCallback implements ProcedureCallback {

        /**
         * <code>m_flushSet</code>  a set of partition-message offset
         */
        private final Set<String> m_flushSet;

        /**
         * <code>m_offset</code>  partition-message offset
         */
        private final String m_offset;

        /**
         * constructor
         * @param flushSet The set of partition-message offset
         * @param offset partition-message offset
         */
        public ConnectorProcedureCallback(Set<String> flushSet, String offset) {
            super();
            m_flushSet = flushSet;
            m_offset = offset;
            m_flushSet.add(m_offset);
        }

        @Override
        public void clientCallback(ClientResponse response) throws Exception {
            if(response.getAppStatus() == ClientResponse.CONNECTION_LOST || response.getAppStatus()  == ClientResponse.CONNECTION_TIMEOUT){
                LOGGER.warn(String.format("Client response error: %s", response.getAppStatusString()));
            }
            m_flushSet.remove(m_offset);
        }
    }

    /**
     * Client connection status listener
     *
     */
    private final  static class ClientStatusListener extends ClientStatusListenerExt{

        private final AtomicBoolean m_connectionLost;

        public ClientStatusListener(AtomicBoolean connectionLost){
            super();
            m_connectionLost = connectionLost;
        }

        @Override
        public void connectionLost(String hostname, int port, int connectionsLeft, DisconnectCause cause){
            LOGGER.warn(String.format("A connection to the database has been lost. There are %d connections remaining.", connectionsLeft));
            m_connectionLost.set(connectionsLeft == 0);
        }
    }
}
