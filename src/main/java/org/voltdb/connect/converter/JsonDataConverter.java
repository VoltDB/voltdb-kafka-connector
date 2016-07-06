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

package org.voltdb.connect.converter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.connect.json.JsonConverter;
import org.apache.kafka.connect.sink.SinkRecord;
import org.voltdb.connect.kafka.ConnectorLogger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.google_voltpatches.common.base.Throwables;

/**
 * Convert SinkRecord value pay load to string bytes.
 *
 */
public class JsonDataConverter implements Converter{

    private static final ConnectorLogger LOGGER = new ConnectorLogger();

    private final JsonConverter m_converter;
    private final ObjectMapper m_mapper;
    private final ObjectReader m_reader;


    public JsonDataConverter() {

        m_converter = new JsonConverter();

        m_mapper = new ObjectMapper();
        m_reader = m_mapper.reader();

        Map<String, String> props = new HashMap<String, String>();
        props.put("schemas.enable", Boolean.FALSE.toString());
        m_converter.configure(props, false);
    }

    @Override
    public byte[] convert(SinkRecord record)  {

        byte[] jsonByte = null;
        try{
            byte[] stream = m_converter.fromConnectData(record.topic(), record.valueSchema(), record.value());
            if(stream != null){
                JsonNode node = m_reader.readTree(new ByteArrayInputStream(stream));
                if(node != null){
                    String jsonMap = m_mapper.convertValue(node, String.class);
                    if(jsonMap != null){
                        jsonByte =  jsonMap.getBytes();
                    }
                }
            }
        } catch (IOException e){
            LOGGER.error("Data conversion error", e);
            Throwables.propagate(e);
        }

        return jsonByte;
    }
}
