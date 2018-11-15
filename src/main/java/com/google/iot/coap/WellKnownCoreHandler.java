/*
 * Copyright (C) 2018 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.iot.coap;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.net.URI;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * This class is similar to {@link Resource}, but it is specifically written just for handling
 * ".well-known/core".
 *
 * <p>Typical usage:
 *
 * <pre><code>
 *     // Set up our root ("/") resource
 *     Resource rootResource = new Resource();
 *     server.setRequestHandler(rootResource);
 *
 *     // Add the folder "/.well-known/"
 *     Resource wellKnownFolder = new Resource();
 *     rootResource.addChild(".well-known", wellKnownFolder);
 *
 *     // Add the "/.well-known/core" handler
 *     WellKnownCoreHandler wellKnownCore = new WellKnownCoreHandler();
 *     wellKnownFolder.addChild("core", wellKnownCore);
 *
 *     // Add the folder "/test/"
 *     Resource testFolder = new Resource();
 *     rootResource.addChild("test", testFolder);
 *
 *     // Add a handler for "/test/hello"
 *     InboundRequestHandler helloHandler = new InboundRequestHandler() {
 *         public void onInboundRequest(InboundRequest inboundRequest) {
 *             inboundRequest.sendSimpleResponse(Code.RESPONSE_CONTENT, "Hello, World!");
 *         }
 *     }
 *     testFolder.addChild("hello", helloHandler);
 *
 *     // Add "/test/hello" to our "/.well-known/core" directory
 *     wellKnownCore.addResource("/test/hello", helloHandler);
 * </code></pre>
 *
 * <p>Handlers added to WellKnownCoreHandler can implement {@link LinkFormat.Provider} to populate
 * the link format listing with additional attributes.
 */
public final class WellKnownCoreHandler implements InboundRequestHandler, LinkFormat.Provider {
    private final HashMap<String, InboundRequestHandler> mChildren = new LinkedHashMap<>();

    private volatile Observable mObservable;

    public WellKnownCoreHandler() {}

    private synchronized Observable getObservable() {
        if (mObservable == null) {
            mObservable = new Observable();
        }
        return mObservable;
    }

    private Observable getObservableDependentForChild(InboundRequestHandler child) {
        if ((child instanceof Observable.Provider) && (child instanceof LinkFormat.Provider)) {
            LinkFormat.Builder builder = new LinkFormat.Builder();
            LinkFormat.LinkBuilder linkBuilder = builder.addLink(URI.create("/bogus"));
            ((LinkFormat.Provider) child).onBuildLinkParams(linkBuilder);

            if (linkBuilder.hasKey(LinkFormat.PARAM_VALUE)) {
                return ((Observable.Provider) child).onGetObservable();
            }
        }
        return null;
    }

    public void addResource(String path, InboundRequestHandler rh) {
        if (!path.startsWith("/")) {
            // We only deal with absolute paths.
            path = "/" + path;
        }

        mChildren.put(path, rh);

        Observable dependent = getObservableDependentForChild(rh);

        if (dependent != null) {
            getObservable().addDependency(dependent);
        }
    }

    public void removeResource(String name) {
        InboundRequestHandler rh = mChildren.get(name);
        if (rh != null) {
            mChildren.remove(name);
            Observable dependent = getObservableDependentForChild(rh);
            if (dependent != null) {
                getObservable().removeDependency(dependent);
            }
        }
    }

    public void removeResource(InboundRequestHandler rh) {
        for (Map.Entry<String, InboundRequestHandler> entry : new HashMap<>(mChildren).entrySet()) {
            removeResource(entry.getKey());
        }
    }

    @Override
    public void onBuildLinkParams(LinkFormat.LinkBuilder builder) {
        if (mObservable != null) {
            builder.put(LinkFormat.PARAM_OBSERVABLE, "1");
        }
    }

    private String getPathForChild(InboundRequestHandler rh) {
        synchronized (mChildren) {
            for (Map.Entry<String, InboundRequestHandler> entry : mChildren.entrySet()) {
                if (entry.getValue().equals(rh)) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    @CanIgnoreReturnValue
    private boolean checkForUnsupportedOptions(InboundRequest inboundRequest) {
        Option nextOption = inboundRequest.nextOption();
        for (; nextOption != null; nextOption = inboundRequest.nextOption()) {
            int number = nextOption.getNumber();
            switch (number) {
                case Option.URI_PATH:
                case Option.URI_HOST:
                case Option.URI_PORT:
                case Option.URI_QUERY:
                case Option.ACCEPT:
                    continue;
                default:
                    break;
            }
            if (nextOption.isCritical()) {
                inboundRequest.sendSimpleResponse(
                        Code.RESPONSE_BAD_OPTION,
                        "Unsupported option: " + Option.toString(nextOption.getNumber()));
                return true;
            }
        }
        return false;
    }

    private void onSpecificRequest(InboundRequest inboundRequest) {
        Message request = inboundRequest.getMessage();
        if (request.getCode() != Code.METHOD_GET) {
            inboundRequest.sendSimpleResponse(Code.RESPONSE_METHOD_NOT_ALLOWED);
            return;
        }

        LinkFormat.Builder builder = new LinkFormat.Builder();

        if (request.getOptionSet().hasUriQuery()) {
            builder.setQueryFilter(request.getOptionSet().getUriQueriesAsMap());
        }

        mChildren.forEach(
                (k, v) -> {
                    final LinkFormat.LinkBuilder item;
                    final LinkFormat.Provider provider;

                    if (v instanceof LinkFormat.Provider) {
                        provider = (LinkFormat.Provider) v;
                    } else {
                        provider = null;
                    }

                    if (v instanceof Resource) {
                        item = builder.addLink(URI.create(k + "/"));
                    } else {
                        item = builder.addLink(URI.create(k));
                    }

                    if (provider != null) {
                        item.useParamProvider(provider);
                    }
                });

        Option nextOption = inboundRequest.nextOption();
        for (; nextOption != null; nextOption = inboundRequest.nextOption()) {
            int number = nextOption.getNumber();
            switch (number) {
                case Option.ACCEPT:
                    if (nextOption.intValue() != ContentFormat.APPLICATION_LINK_FORMAT) {
                        inboundRequest.sendSimpleResponse(Code.RESPONSE_NOT_ACCEPTABLE);
                        return;
                    }
                    continue;
                case Option.URI_QUERY:
                case Option.URI_PATH:
                case Option.URI_HOST:
                case Option.URI_PORT:
                    continue;
                default:
                    break;
            }

            if (nextOption.isCritical()) {
                inboundRequest.sendSimpleResponse(
                        Code.RESPONSE_BAD_OPTION,
                        "Unsupported option: " + Option.toString(nextOption.getNumber()));
                return;
            }
        }

        MutableMessage response =
                request.createResponse(Code.RESPONSE_CONTENT)
                        .setPayload(builder.toString())
                        .addOption(Option.CONTENT_FORMAT, ContentFormat.APPLICATION_LINK_FORMAT);

        inboundRequest.sendResponse(response);
    }

    private void onSpecificRequestCheck(InboundRequest inboundRequest) {
        Message request = inboundRequest.getMessage();

        if (request.getCode() != Code.METHOD_GET) {
            inboundRequest.sendSimpleResponse(Code.RESPONSE_METHOD_NOT_ALLOWED);
        }

        checkForUnsupportedOptions(inboundRequest);
    }

    private void onUnknownRequest(InboundRequest inboundRequest) {
        inboundRequest.sendSimpleResponse(Code.RESPONSE_NOT_FOUND);
    }

    @Override
    public void onInboundRequest(InboundRequest inboundRequest) {
        Option nextOption = inboundRequest.nextOptionWithNumber(Option.URI_PATH);
        if (nextOption != null && nextOption.getNumber() == Option.URI_PATH) {
            if (nextOption.byteArrayLength() != 0) {
                inboundRequest.rewindOneOption();
                onUnknownRequest(inboundRequest);
                return;
            }
        }

        if (mObservable != null && mObservable.handleInboundRequest(inboundRequest)) {
            // Handled by the observable
            return;
        }

        onSpecificRequest(inboundRequest);
    }

    @Override
    public void onInboundRequestCheck(InboundRequest inboundRequest) {
        Option nextOption = inboundRequest.nextOptionWithNumber(Option.URI_PATH);
        if (nextOption != null && nextOption.getNumber() == Option.URI_PATH) {
            if (nextOption.byteArrayLength() != 0) {
                inboundRequest.rewindOneOption();
                onUnknownRequest(inboundRequest);
                return;
            }
        }

        onSpecificRequestCheck(inboundRequest);
    }
}
