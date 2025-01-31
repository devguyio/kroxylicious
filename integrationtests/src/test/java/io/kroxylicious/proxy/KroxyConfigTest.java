/*
 * Copyright Kroxylicious Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.kroxylicious.proxy;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import static io.kroxylicious.proxy.KroxyConfig.builder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class KroxyConfigTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper(new YAMLFactory());

    @Test
    public void testBareConfig() throws Exception {
        ObjectNode deserializedConfig = serializeAndDeserialize(builder().withNewProxy().withAddress("localhost:9192").endProxy().build());
        ObjectNode proxyObj = assertObjectField(deserializedConfig, "proxy");
        assertTextField(proxyObj, "address", "localhost:9192");
    }

    @Test
    public void testSslConfig() throws Exception {
        ObjectNode deserializedConfig = serializeAndDeserialize(
                builder().withNewProxy().withAddress("localhost:9192").withKeyPassword("pass").withKeyStoreFile("file").endProxy().build());
        ObjectNode proxyObj = assertObjectField(deserializedConfig, "proxy");
        assertTextField(proxyObj, "address", "localhost:9192");
        assertTextField(proxyObj, "keyStoreFile", "file");
        assertTextField(proxyObj, "keyPassword", "pass");
    }

    @Test
    public void testClusterConfig() throws Exception {
        ObjectNode deserializedConfig = serializeAndDeserialize(
                builder().addToClusters("demo", new ClusterBuilder().withBootstrapServers("localhost:9092").build()).build());
        ObjectNode clusterObj = assertObjectField(deserializedConfig, "clusters");
        ObjectNode demoObj = assertObjectField(clusterObj, "demo");
        assertTextField(demoObj, "bootstrap_servers", "localhost:9092");
    }

    @Test
    public void testTypeOnlyFilter() throws Exception {
        ObjectNode deserializedConfig = serializeAndDeserialize(builder().addNewFilter().withType("FilterType").endFilter().build());
        ArrayNode filters = assertArrayField(deserializedConfig, "filters", 1);
        ObjectNode filterObj = assertOnlyElementIsObject(filters);
        assertTextField(filterObj, "type", "FilterType");
    }

    @Test
    public void testFilterWithSingleParam() throws Exception {
        ObjectNode deserializedConfig = serializeAndDeserialize(builder().addNewFilter().withType("FilterType").withConfig(Map.of("a", "b")).endFilter().build());
        ArrayNode filters = assertArrayField(deserializedConfig, "filters", 1);
        ObjectNode filterObj = assertOnlyElementIsObject(filters);
        assertTextField(filterObj, "type", "FilterType");
        ObjectNode filterConfig = assertObjectField(filterObj, "config");
        assertTextField(filterConfig, "a", "b");
    }

    @Test
    public void testPrometheusEndpointConfig() throws Exception {
        ObjectNode deserializedConfig = serializeAndDeserialize(
                builder().withNewAdminHttp().withNewEndpoints().withPrometheusEndpointConfig(Map.of()).endEndpoints().endAdminHttp().build());
        ObjectNode adminHttp = assertObjectField(deserializedConfig, "adminHttp");
        ObjectNode endpoints = assertObjectField(adminHttp, "endpoints");
        ObjectNode prometheus = assertObjectField(endpoints, "prometheus");
        assertTrue(prometheus.isEmpty(), "expect prometheus endpoint to have an empty object serialized");
    }

    private static ObjectNode serializeAndDeserialize(KroxyConfig builder) throws Exception {
        String config = builder.toYaml();
        return OBJECT_MAPPER.reader().readValue(config, ObjectNode.class);
    }

    private static ObjectNode assertObjectField(ObjectNode o, String fieldName) {
        assertTrue(o.has(fieldName), "config should have a " + fieldName + " field");
        JsonNode field = o.get(fieldName);
        assertTrue(field.isObject(), fieldName + " should be an object");
        return (ObjectNode) field;
    }

    private static ArrayNode assertArrayField(ObjectNode o, String fieldName, int size) {
        assertTrue(o.has(fieldName), "config should have a " + fieldName + " field");
        JsonNode field = o.get(fieldName);
        assertTrue(field.isArray(), fieldName + " should be an array");
        ArrayNode array = (ArrayNode) field;
        assertEquals(size, array.size(), fieldName + " was unexpected size");
        return array;
    }

    private static ObjectNode assertOnlyElementIsObject(ArrayNode o) {
        assertEquals(1, o.size(), "array had more than one element");
        JsonNode jsonNode = o.get(0);
        assertTrue(jsonNode.isObject(), "only array element was not an object");
        return (ObjectNode) jsonNode;
    }

    private static void assertTextField(ObjectNode object, String fieldName, String expected) {
        assertTrue(object.has(fieldName), "expect " + fieldName + " to be defined");
        JsonNode field = object.get(fieldName);
        assertTrue(field.isTextual(), fieldName + " should be text");
        assertEquals(expected, field.asText(), "expect " + fieldName + " to be set");
    }

}
