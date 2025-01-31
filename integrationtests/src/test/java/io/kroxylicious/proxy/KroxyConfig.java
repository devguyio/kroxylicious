/*
 * Copyright Kroxylicious Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.kroxylicious.proxy;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;

import io.sundr.builder.annotations.Buildable;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@Buildable(editableEnabled = false)

public class KroxyConfig {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper(new YAMLFactory().disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER));

    private final Proxy proxy;
    private final Map<String, Cluster> clusters;
    private final List<Filter> filters;

    @JsonInclude(NON_NULL)
    private final AdminHttp adminHttp;

    @JsonInclude(NON_NULL)
    private final List<MicrometerConfig> micrometer;

    public static KroxyConfigBuilder builder() {
        return new KroxyConfigBuilder();
    }

    public KroxyConfig(Proxy proxy, Map<String, Cluster> clusters, List<Filter> filters, AdminHttp adminHttp, List<MicrometerConfig> micrometer) {
        this.proxy = proxy;
        this.clusters = clusters;
        this.filters = filters;
        this.adminHttp = adminHttp;
        this.micrometer = micrometer;
    }

    public String toYaml() {
        try {
            return OBJECT_MAPPER.writeValueAsString(this);
        }
        catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public Proxy getProxy() {
        return proxy;
    }

    public AdminHttp getAdminHttp() {
        return adminHttp;
    }

    public Map<String, Cluster> getClusters() {
        return clusters;
    }

    public List<Filter> getFilters() {
        return filters;
    }

    public List<MicrometerConfig> getMicrometer() {
        return micrometer;
    }
}
