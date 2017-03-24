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

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
import org.apache.kafka.connect.data.Date;
import org.apache.kafka.connect.data.Decimal;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.data.Time;
import org.apache.kafka.connect.data.Timestamp;
import org.apache.kafka.connect.errors.ConnectException;
import org.apache.kafka.connect.errors.DataException;
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

import com.google_voltpatches.common.base.Splitter;
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
     * <code>m_formatter</code> The formatter to converting kafka data into the data format required by VoltDB procedure
     */
    private Formatter m_formatter;

    /**
     * <code>m_converter</code> Convert the value of SinkRecord to byte array.
     */
    private Converter m_converter;

    /**
     * <code>m_client</code> VoltDB client
     */
    private Client m_client;

    /**
     * <code>m_procName</code> VoltDB store procedure name
     */
    private String m_procName;

    /**
     * <code>m_currentBatchCnt</code> The record count pushed from Kafka from last offset flush
     */
    private AtomicLong m_currentBatchCnt = new AtomicLong(0);

    /**
     * <code>m_flushSet</code> The temporary storage of the topic partitions and message offsets. Entries are removed when
     * the data from Kafaka are processed. The set must be empty when the offset is allowed to committed.
     */
    private Set<KafkaPartitionOffset> m_flushSet = Sets.newConcurrentHashSet();

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

        Splitter splitter = Splitter.on(',').omitEmptyStrings().trimResults();
        List<String> serverList = splitter.splitToList(servers);
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
            LOGGER.error(String.format("Can't create formatter or converter: %s", e.getMessage()), e);
            throw new ConnectException(e.getMessage());
        }
        m_client = ClientFactory.createClient(config);
        connect(serverList);
    }

    @Override
    public void put(Collection<SinkRecord> records) {

        for (SinkRecord record : records) {

            if (m_connectionLost.get()) {
                m_currentBatchCnt.set(0);
                m_flushSet.clear();
                //trigger Kafka consumer to pause and retry.
                throw new RetriableException("All client connections to VoltDB have been lost.");
            }

            if (record.value() == null) {
                LOGGER.error("The SinkRecord does not have schema or value defined.");
                continue;
            }

            KafkaPartitionOffset partitionOffset = new KafkaPartitionOffset(record.kafkaPartition(), record.kafkaOffset());
            m_currentBatchCnt.getAndIncrement();

            Object[] formattedData = null;
            if (record.valueSchema() != null && record.valueSchema().type().equals(Schema.Type.STRUCT)) {
                try {
                    formattedData = getDataFromSchemaRecord(record);
                } catch (ConnectException e) {
                    LOGGER.error("Failed processing schema records: ", e);
                    continue;
                }
            }
            else {
                try{
                    formattedData = m_formatter.transform(ByteBuffer.wrap(m_converter.convert(record)));
                } catch (FormatException e) {
                    LOGGER.error("Error transforming record: ", e);
                    continue;
                }
            }
            try {
                ConnectorProcedureCallback cb = new ConnectorProcedureCallback(m_flushSet, partitionOffset);
                if (!m_client.callProcedure(cb, m_procName, formattedData)) {
                    m_flushSet.remove(partitionOffset);
                }
            } catch (NoConnectionsException e){
                m_currentBatchCnt.set(0);
                m_flushSet.clear();
                LOGGER.error(String.format("Procedure error for offset %s", partitionOffset), e);
                throw new RetriableException("Connection to VoltDB has been lost.");
            } catch (Exception e){
                m_flushSet.remove(partitionOffset);
                LOGGER.error(String.format("Procedure error for offset %s", partitionOffset), e);
            }
        }
    }

    @Override
    public void flush(Map<TopicPartition, OffsetAndMetadata> offsets) {

        if (m_currentBatchCnt.get() > 0) {
            try {
                m_client.drain();
                if (LOGGER.isInfoEnabled()) {
                    LOGGER.info(String.format("Flush offset for batch count: %d", m_currentBatchCnt.get()));
                }
            } catch (NoConnectionsException | InterruptedException e) {
                LOGGER.error(e);
                throw new ConnectException("ConnectorTask flush: there are uncommited records.");
            } finally {
                m_currentBatchCnt.set(0);
                if (!m_flushSet.isEmpty()) {
                    m_flushSet.clear();

                    //there are still records unaccounted for, let framework to re-put these records.
                    throw new ConnectException("ConnectorTask flush: there are uncommited records.");
                }
            }
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
                m_client.createConnection(host);
            } catch (IOException e) {
                LOGGER.error(String.format("Could not create connection to %s", host), e);
                Throwables.propagate(e);
            }
        }

    }

    public static int getIntProperty(Map<String, String> props, String propName, int defaultValue){

        String valString = props.get(propName);
        valString = (valString == null) ? "" : valString.trim();
        try{
            return (valString.isEmpty() ?  defaultValue : Integer.parseInt(valString));
        } catch (NumberFormatException e){
            throw new ConfigException(String.format("Error: %s for property %s.", e.getMessage(), propName));
        }

    }

    public static String getStringProperty(Map<String, String> props, String propName, String defaultValue){

        String valString = props.get(propName);
        valString = (valString == null) ? "" : valString.trim();
        return (valString.isEmpty() ? defaultValue : valString);

    }

    private Object[] getDataFromSchemaRecord(SinkRecord  record) {
        List<Object> dataList = new ArrayList<Object>();
        for(org.apache.kafka.connect.data.Field field : record.valueSchema().fields()) {
            Struct valueStruct = (Struct) record.value();
            Object value = valueStruct.get(field);
            value = getSchemaFieldValue(field.schema(), value);
            dataList.add(value);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Schema name: " + record.valueSchema().name() + ", field: " + field.name() +
                        ", type: " + field.schema().type() + ", value: " + value);
            }
        }
        return dataList.toArray();
    }

    private interface LogicalTypeConverter {
        Object convert(Schema schema, Object value);
    }

    private Object getValueFromLogicalType(Schema schema, Object value) {
        if (schema.name().equals(Decimal.LOGICAL_NAME)) {
            if (!(value instanceof BigDecimal)) {
                throw new DataException("Invalid type for Decimal, underlying representation "
                        + "should be BigDecimal but was " + value.getClass());
            }
            return Decimal.fromLogical(schema, (BigDecimal) value);
        }
        if (schema.name().equals(Date.LOGICAL_NAME)) {
            if (!(value instanceof java.util.Date)) {
                throw new DataException("Invalid type for Date, underlying representation should "
                        + "be java.util.Date but was " + value.getClass());
            }
            return Date.fromLogical(schema, (java.util.Date) value);
        }

        if (schema.name().equals(Time.LOGICAL_NAME)) {
            if (!(value instanceof java.util.Date)) {
                throw new DataException("Invalid type for Time, underlying representation should "
                        + "be java.util.Date but was " + value.getClass());
            }
            return Time.fromLogical(schema, (java.util.Date) value);
        }

        if (schema.name().equals(Timestamp.LOGICAL_NAME)) {
            if (!(value instanceof java.util.Date)) {
                throw new DataException("Invalid type for Timestamp, underlying representation should "
                        + "be java.util.Date but was " + value.getClass());
            }
            return Timestamp.fromLogical(schema, (java.util.Date) value);
        }
        return value;
    }
    private Object getSchemaFieldValue(Schema schema, Object value) {
        if (value == null) {
            return null;
        }
        else {
            if (schema.name() != null) {
                value = getValueFromLogicalType(schema, value);
            }
            switch (schema.type()) {
            case INT8:
                return (Byte) value;
            case INT16:
                return (Short) value;
            case INT32:
                return (Integer) value;
            case INT64:
                return (Long) value;
            case FLOAT32:
                return (Float) value;
            case FLOAT64:
                return (Double) value;
            case STRING:
                return (String) value;
            case BYTES:
                final byte[] bytes;
                if (value instanceof ByteBuffer) {
                    final ByteBuffer buffer = ((ByteBuffer) value).slice();
                    bytes = new byte[buffer.remaining()];
                    buffer.get(bytes);
                }
                else {
                    bytes = (byte[]) value;
                }
                return bytes;
            case BOOLEAN:
                // in kafka this is represented as 1 or 0; though boolean is not supported in voltdb
                // so treat as error case for VoltDB at present
            default:
                throw new ConnectException("Unsupported data type read from kafka source: " + schema.type());
            }
        }
    }

    /**
     * VoltDB procedure callback
     *
     */
    private final static class ConnectorProcedureCallback implements ProcedureCallback {

        /**
         * <code>m_flushSet</code>  a set of partition-message offset
         */
        private final Set<KafkaPartitionOffset> m_flushSet;

        /**
         * <code>m_offset</code>  partition-message offset
         */
        private final KafkaPartitionOffset m_offset;

        /**
         * constructor
         * @param flushSet The set of partition-message offset
         * @param offset partition-message offset
         */
        public ConnectorProcedureCallback(Set<KafkaPartitionOffset> flushSet, KafkaPartitionOffset offset) {
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

    /**
     * A container for Kafka partition id and offset
     *
     */
    private final static class KafkaPartitionOffset{

        final int m_partition;
        final long m_offset;

        public KafkaPartitionOffset(int partition, long offset){
            m_partition = partition;
            m_offset = offset;
        }

        @Override
        public boolean equals (Object obj){
            if(obj instanceof KafkaPartitionOffset){
                return (m_partition == ((KafkaPartitionOffset)obj).m_partition && m_offset == ((KafkaPartitionOffset)obj).m_offset);
            }

            return false;
        }
    }
}
