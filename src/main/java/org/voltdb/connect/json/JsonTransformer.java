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

package org.voltdb.connect.json;

import java.nio.charset.StandardCharsets;

import org.apache.kafka.connect.data.SchemaAndValue;
import org.apache.kafka.connect.errors.DataException;
import org.apache.kafka.connect.json.JsonConverter;
import org.voltcore.logging.VoltLogger;

/**
 *Convert a native object to a Kafka Connect data object. If the connect data are not in
 *the valid format, send back null.
 *
 */
public class JsonTransformer extends JsonConverter {

    private static final VoltLogger LOGGER = new VoltLogger("KafkaSinkConnector");

    @Override
    public SchemaAndValue toConnectData(String topic, byte[] value) {

        if(value == null || value.length== 0){
            return new SchemaAndValue(null, null);
        }

        try{
            String val = new String(value, StandardCharsets.UTF_8);
            if(!val.startsWith("\"")){
                val = "\"" + val + "\"";
                return super.toConnectData(topic, val.getBytes());
            }else{
                return super.toConnectData(topic, value);
            }
        } catch (DataException e){
            LOGGER.error("Data conversion error", e);
            return new SchemaAndValue(null, null);
        }
    }
}

