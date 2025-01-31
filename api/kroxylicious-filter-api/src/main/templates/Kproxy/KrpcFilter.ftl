<#--

    Copyright Kroxylicious Authors.

    Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0

-->
/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ${outputPackage};

<#list messageSpecs as messageSpec>
import org.apache.kafka.common.message.${messageSpec.name}Data;
</#list>
import org.apache.kafka.common.message.RequestHeaderData;
import org.apache.kafka.common.message.ResponseHeaderData;
import org.apache.kafka.common.protocol.ApiKeys;
import org.apache.kafka.common.protocol.ApiMessage;

/**
 * <p>Interface for {@code *RequestFilter}s.
 * This interface is not usually implemented directly.
 * Instead filter classes can (multiply) implement one of the RPC-specific subinterfaces such
 * as {@link ProduceRequestFilter} for a type-safe API.
 *
 * <p>When implementing one or more of the {@code *RequestFilter} subinterfaces you need only implement
 * the {@code on*Request} method(s), unless your filter can avoid deserialization in which case
 * you can override {@link #shouldDeserializeRequest(ApiKeys, short)} as well.</p>
 *
 * <h2>Guarantees</h2>
 * <p>Implementors of this API may assume the following:</p>
 * <ol>
 *     <li>That each instance of the filter is associated with a single channel</li>
 *     <li>That {@link #shouldDeserializeRequest(ApiKeys, short)} and
 *     {@link #onRequest(ApiKeys, RequestHeaderData, ApiMessage, KrpcFilterContext)} (or {@code on*Request} as appropriate)
 *     will always be invoked on the same thread.</li>
 *     <li>That filters are applied in the order they were configured.</li>
 * </ol>
 * <p>From 1. and 2. it follows that you can use member variables in your filter to
 * store channel-local state.</p>
 *
 * <p>Implementors should <strong>not</strong> assume:</p>
 * <ol>
 *     <li>That filters in the same chain execute on the same thread. Thus inter-filter communication/state
 *     transfer needs to be thread-safe</li>
 * </ol>
 */
public /* sealed */ interface KrpcFilter /* TODO permits ... */ {

    /**
     * Apply the filter to the given {@code header} and {@code body} using the given {@code filterContext}.
     * @param apiKey The request api key.
     * @param header The request header.
     * @param body The request body.
     * @param filterContext The filter context.
     */
    default void onRequest(ApiKeys apiKey,
                                  RequestHeaderData header,
                                  ApiMessage body,
                                  KrpcFilterContext filterContext) {
        switch (apiKey) {
<#list messageSpecs as messageSpec>
<#if messageSpec.type?lower_case == 'request'>
            case ${retrieveApiKey(messageSpec)}:
                ((${messageSpec.name}Filter) this).on${messageSpec.name}(header, (${messageSpec.name}Data) body, filterContext);
                break;
</#if>
</#list>
            default:
                throw new IllegalStateException("Unsupported RPC " + apiKey);
        }
    }

    /**
     * Apply the filter to the given {@code header} and {@code body} using the given {@code filterContext}.
     * @param apiKey The request api key.
     * @param header The request header.
     * @param body The request body.
     * @param filterContext The filter context.
     */
    default void onResponse(ApiKeys apiKey,
                                   ResponseHeaderData header,
                                   ApiMessage body,
                                   KrpcFilterContext filterContext) {
        switch (apiKey) {
<#list messageSpecs as messageSpec>
<#if messageSpec.type?lower_case == 'response'>
            case ${retrieveApiKey(messageSpec)}:
                ((${messageSpec.name}Filter) this).on${messageSpec.name}(header, (${messageSpec.name}Data) body, filterContext);
                break;
</#if>
</#list>
            default:
                throw new IllegalStateException("Unsupported RPC " + apiKey);
        }
    }


    /**
     * <p>Determines whether a request with the given {@code apiKey} and {@code apiVersion} should be deserialized.
     * Note that it is not guaranteed that this method will be called once per request,
     * or that two consecutive calls refer to the same request.
     * That is, the sequences of invocations like the following are allowed:</p>
     * <ol>
     *     <li>{@code shouldDeserializeRequest} on request A</li>
     *     <li>{@code shouldDeserializeRequest} on request B</li>
     *     <li>{@code shouldDeserializeRequest} on request A</li>
     *     <li>{@code onRequest} on request A</li>
     *     <li>{@code onRequest} on request B</li>
     * </ol>
     * @param apiKey The API key
     * @param apiVersion The API version
     * @return true if request should be deserialized
     */
    default boolean shouldDeserializeRequest(ApiKeys apiKey, short apiVersion) {
        switch (apiKey) {
<#list messageSpecs as messageSpec>
<#if messageSpec.type?lower_case == 'request'>
            case ${retrieveApiKey(messageSpec)}:
                return this instanceof ${messageSpec.name}Filter;
</#if>
</#list>
            default:
                throw new IllegalStateException("Unsupported API key " + apiKey);
        }
    }

    /**
     * <p>Determines whether a response with the given {@code apiKey} and {@code apiVersion} should be deserialized.
     * Note that it is not guaranteed that this method will be called once per response,
     * or that two consecutive calls refer to the same response.
     * That is, the sequences of invocations like the following are allowed:</p>
     * <ol>
     *     <li>{@code shouldDeserializeResponse} on response A</li>
     *     <li>{@code shouldDeserializeResponse} on response B</li>
     *     <li>{@code shouldDeserializeResponse} on response A</li>
     *     <li>{@code apply} on response A</li>
     *     <li>{@code apply} on response B</li>
     * </ol>
     * @param apiKey The API key
     * @param apiVersion The API version
     * @return true if response should be deserialized
     */
    default boolean shouldDeserializeResponse(ApiKeys apiKey, short apiVersion) {
        switch (apiKey) {
<#list messageSpecs as messageSpec>
<#if messageSpec.type?lower_case == 'response'>
            case ${retrieveApiKey(messageSpec)}:
                return this instanceof ${messageSpec.name}Filter;
</#if>
</#list>
            default:
                throw new IllegalStateException("Unsupported API key " + apiKey);
        }
    }

}
