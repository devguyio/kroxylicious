/*
 * Copyright Kroxylicious Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */

package io.kroxylicious.proxy.internal.filter;

import io.kroxylicious.proxy.config.ProxyConfig;
import io.kroxylicious.proxy.filter.KrpcFilterContext;

class SniAddressMapping implements AddressMapping {

    private final String targetHost;
    private final int targetPort;

    SniAddressMapping(ProxyConfig config) {
        String proxyAddress = config.address();
        String[] proxyAddressParts = proxyAddress.split(":");

        this.targetHost = proxyAddressParts[0];
        this.targetPort = Integer.parseInt(proxyAddressParts[1]);
    }

    @Override
    public String downstreamHost(KrpcFilterContext context, String host, int port) {
        return context.sniHostname() != null ? context.sniHostname() : targetHost;
    }

    @Override
    public int downstreamPort(KrpcFilterContext context, String host, int port) {
        return targetPort;
    }
}
