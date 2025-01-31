/*
 * Copyright Kroxylicious Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.kroxylicious.proxy.internal;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

import org.apache.kafka.common.message.ApiVersionsRequestData;
import org.apache.kafka.common.message.ApiVersionsResponseData;
import org.apache.kafka.common.message.FetchRequestData;
import org.apache.kafka.common.message.FetchResponseData;
import org.apache.kafka.common.message.ProduceRequestData;
import org.apache.kafka.common.message.RequestHeaderData;
import org.apache.kafka.common.message.ResponseHeaderData;
import org.apache.kafka.common.protocol.ApiKeys;
import org.apache.kafka.common.protocol.ApiMessage;
import org.junit.jupiter.api.Test;

import io.kroxylicious.proxy.filter.ApiVersionsRequestFilter;
import io.kroxylicious.proxy.filter.ApiVersionsResponseFilter;
import io.kroxylicious.proxy.filter.KrpcFilterContext;
import io.kroxylicious.proxy.future.InternalCompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class FilterHandlerTest extends FilterHarness {

    @Test
    public void testForwardRequest() {
        ApiVersionsRequestFilter filter = (header, request, context) -> context.forwardRequest(request);
        buildChannel(filter);
        var frame = writeRequest(new ApiVersionsRequestData());
        var propagated = channel.readOutbound();
        assertEquals(frame, propagated, "Expect it to be the frame that was sent");
    }

    @Test
    public void testShouldNotDeserialiseRequest() {
        ApiVersionsRequestFilter filter = new ApiVersionsRequestFilter() {
            @Override
            public boolean shouldDeserializeRequest(ApiKeys apiKey, short apiVersion) {
                return false;
            }

            @Override
            public void onApiVersionsRequest(RequestHeaderData header, ApiVersionsRequestData request, KrpcFilterContext context) {
                fail("Should not be called");
            }
        };
        buildChannel(filter);
        var frame = writeRequest(new ApiVersionsRequestData());
        var propagated = channel.readOutbound();
        assertEquals(frame, propagated, "Expect it to be the frame that was sent");
    }

    @Test
    public void testDropRequest() {
        ApiVersionsRequestFilter filter = (header, request, context) -> {
            /* don't call forwardRequest => drop the request */
        };
        buildChannel(filter);
        var frame = writeRequest(new ApiVersionsRequestData());
    }

    @Test
    public void testForwardResponse() {
        ApiVersionsResponseFilter filter = (header, response, context) -> context.forwardResponse(response);
        buildChannel(filter);
        var frame = writeResponse(new ApiVersionsResponseData());
        var propagated = channel.readInbound();
        assertEquals(frame, propagated, "Expect it to be the frame that was sent");
    }

    @Test
    public void testShouldNotDeserializeResponse() {
        ApiVersionsResponseFilter filter = new ApiVersionsResponseFilter() {
            @Override
            public boolean shouldDeserializeResponse(ApiKeys apiKey, short apiVersion) {
                return false;
            }

            @Override
            public void onApiVersionsResponse(ResponseHeaderData header, ApiVersionsResponseData response, KrpcFilterContext context) {
                fail("Should not be called");
            }
        };
        buildChannel(filter);
        var frame = writeResponse(new ApiVersionsResponseData());
        var propagated = channel.readInbound();
        assertEquals(frame, propagated, "Expect it to be the frame that was sent");
    }

    @Test
    public void testDropResponse() {
        ApiVersionsResponseFilter filter = (header, response, context) -> {
            /* don't call forwardRequest => drop the request */
        };
        buildChannel(filter);
        var frame = writeResponse(new ApiVersionsResponseData());
    }

    @Test
    public void testSendRequest() {
        FetchRequestData body = new FetchRequestData();
        InternalCompletionStage<ApiMessage>[] fut = new InternalCompletionStage[]{ null };
        ApiVersionsRequestFilter filter = (header, request, context) -> {
            assertNull(fut[0],
                    "Expected to only be called once");
            fut[0] = (InternalCompletionStage<ApiMessage>) context.sendRequest((short) 3, body);
        };

        buildChannel(filter);

        var frame = writeRequest(new ApiVersionsRequestData());
        var propagated = channel.readOutbound();
        assertTrue(propagated instanceof InternalRequestFrame);
        assertEquals(body, ((InternalRequestFrame<?>) propagated).body(),
                "Expect the body to be the Fetch request");

        InternalCompletionStage<ApiMessage> completionStage = fut[0];
        CompletableFuture<ApiMessage> future = toCompletableFuture(completionStage);
        assertFalse(future.isDone(),
                "Future should not be finished yet");

        // test the response path
        CompletableFuture<ApiMessage> futu = new CompletableFuture<>();
        var responseFrame = writeInternalResponse(new FetchResponseData(), futu);
        assertTrue(futu.isDone(),
                "Future should be finished now");
        assertEquals(responseFrame.body(), futu.getNow(null),
                "Expect the body that was sent");
    }

    private static CompletableFuture<ApiMessage> toCompletableFuture(CompletionStage<ApiMessage> completionStage) {
        CompletableFuture<ApiMessage> future = new CompletableFuture<>();
        completionStage.whenComplete((o, throwable) -> {
            if (throwable != null) {
                future.completeExceptionally(throwable);
            }
            else {
                future.complete(o);
            }
        });
        return future;
    }

    @Test
    public void testSendRequestCompletionStageCannotBeConvertedToFuture() {
        FetchRequestData body = new FetchRequestData();
        CompletionStage<?>[] fut = { null };
        ApiVersionsRequestFilter filter = (header, request, context) -> {
            assertNull(fut[0],
                    "Expected to only be called once");
            fut[0] = context.sendRequest((short) 3, body);
        };

        buildChannel(filter);

        var frame = writeRequest(new ApiVersionsRequestData());
        var propagated = channel.readOutbound();
        assertTrue(propagated instanceof InternalRequestFrame);
        assertEquals(body, ((InternalRequestFrame<?>) propagated).body(),
                "Expect the body to be the Fetch request");

        assertThrows(UnsupportedOperationException.class, () -> {
            fut[0].toCompletableFuture();
        });
    }

    /**
     * Test the special case within {@link FilterHandler} for
     * {@link io.kroxylicious.proxy.filter.KrpcFilterContext#sendRequest(short, ApiMessage)}
     * with acks=0 Produce requests.
     */
    @Test
    public void testSendAcklessProduceRequest() throws ExecutionException, InterruptedException {
        ProduceRequestData body = new ProduceRequestData().setAcks((short) 0);
        CompletionStage<ApiMessage>[] fut = new CompletionStage[]{ null };
        ApiVersionsRequestFilter filter = (header, request, context) -> {
            assertNull(fut[0],
                    "Expected to only be called once");
            fut[0] = context.sendRequest((short) 3, body);
        };

        buildChannel(filter);

        var frame = writeRequest(new ApiVersionsRequestData());
        var propagated = channel.readOutbound();
        assertTrue(propagated instanceof InternalRequestFrame);
        assertEquals(body, ((InternalRequestFrame<?>) propagated).body(),
                "Expect the body to be the Fetch request");

        CompletableFuture<ApiMessage> future = toCompletableFuture(fut[0]);
        assertTrue(future.isDone(),
                "Future should be done");
        assertFalse(future.isCompletedExceptionally(),
                "Future should be successful");
        CompletableFuture<Object> blocking = new CompletableFuture<>();
        fut[0].thenApply(blocking::complete);
        assertNull(blocking.get(),
                "Value should be null");
    }

    @Test
    public void testSendRequestTimeout() throws InterruptedException {
        FetchRequestData body = new FetchRequestData();
        CompletionStage<ApiMessage>[] fut = new CompletionStage[]{ null };
        ApiVersionsRequestFilter filter = (header, request, context) -> {
            assertNull(fut[0],
                    "Expected to only be called once");
            fut[0] = context.sendRequest((short) 3, body);
        };

        buildChannel(filter, 50L);

        var frame = writeRequest(new ApiVersionsRequestData());
        var propagated = channel.readOutbound();
        assertTrue(propagated instanceof InternalRequestFrame);
        assertEquals(body, ((InternalRequestFrame<?>) propagated).body(),
                "Expect the body to be the Fetch request");

        CompletionStage<ApiMessage> p = fut[0];
        CompletableFuture<ApiMessage> q = toCompletableFuture(p);
        assertFalse(q.isDone(),
                "Future should not be finished yet");

        // timeout the request
        Thread.sleep(60L);
        channel.runPendingTasks();

        assertTrue(q.isDone(),
                "Future should be finished yet");
        assertTrue(q.isCompletedExceptionally(),
                "Future should be finished yet");
        assertThrows(ExecutionException.class, q::get);
    }

}
