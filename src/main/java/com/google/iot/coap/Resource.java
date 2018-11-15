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
import java.util.*;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * A {@link InboundRequestHandler} that approximates a directory or folder. Child request handlers
 * can be registered with instances of this class to create a hierarchical path structure. An
 * instance of this class is typically used as the root ("/") {@link InboundRequestHandler} for a
 * {@link Server}.
 *
 * <p>Subclassing this class is allowed, and it includes many hooks that can be overridden to
 * implement subclasses with complex alternative behaviors.
 *
 * <p>Typical usage:
 *
 * <pre><code>
 *     // Set up our root ("/") resource
 *     Resource rootResource = new Resource();
 *     server.setRequestHandler(rootResource);
 *
 *     // Add the folder "/test/"
 *     Resource&lt;InboundRequestHandler&gt; testFolder = new Resource&lt;&gt;();
 *     rootResource.addChild("test", testFolder);
 *
 *     // Add a handler for "/test/hello"
 *     InboundRequestHandler helloHandler = new InboundRequestHandler() {
 *         public void onInboundRequest(InboundRequest inboundRequest) {
 *             inboundRequest.sendSimpleResponse(Code.RESPONSE_CONTENT, "Hello, World!");
 *         }
 *     }
 *     testFolder.addChild("hello", helloHandler);
 * </code></pre>
 *
 * <p>Children added to a Resource can implement {@link LinkFormat.Provider} to populate the link
 * format listing with additional attributes.
 *
 * @see Server
 * @see Server#setRequestHandler(InboundRequestHandler)
 * @see WellKnownCoreHandler
 * @see InboundRequestHandler
 * @see LinkFormat.Provider
 */
public class Resource<T extends InboundRequestHandler>
        implements InboundRequestHandler, LinkFormat.Provider {
    private final HashMap<String, T> mChildren = new LinkedHashMap<>();
    private final Set<String> mHiddenChildren = new HashSet<>();

    private final Observable mObservable = new Observable();

    public Resource() {}

    public final Map<String, T> getChildren() {
        return mChildren;
    }

    /**
     * Returns the {@link Observable} instance associated with this Resource.
     *
     * @return the {@link Observable} instance associated with this Resource.
     */
    public final Observable getObservable() {
        return mObservable;
    }

    private Observable getObservableDependentForChild(T child) {
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

    /**
     * Adds a child to this resource.
     *
     * @param name the name that this child will be available at
     * @param rh the request handler to add
     * @see LinkFormat.Provider
     * @see #addHiddenChild(String, InboundRequestHandler)
     */
    public final void addChild(String name, T rh) {
        mChildren.put(Objects.requireNonNull(name), Objects.requireNonNull(rh));

        getObservable().trigger();

        mHiddenChildren.remove(name);
    }

    /**
     * Adds a child to this resource that will be hidden from the link format listing.
     *
     * @param name the name that this child will be available at
     * @param rh the request handler to add
     * @see #addChild(String, InboundRequestHandler)
     */
    public final void addHiddenChild(String name, T rh) {
        mChildren.put(Objects.requireNonNull(name), Objects.requireNonNull(rh));
        mHiddenChildren.add(name);
    }

    /**
     * Removes the child with the given name from this Resource.
     *
     * @param name the name of the child to remove
     */
    public final void removeChild(String name) {
        T rh = mChildren.get(name);
        if (rh != null) {
            mChildren.remove(name);
            mHiddenChildren.remove(name);
            Observable dependent = getObservableDependentForChild(rh);
            if (dependent != null) {
                getObservable().removeDependency(dependent);
            }
            mObservable.trigger();
        }
    }

    /**
     * Removes all children with the given {@link InboundRequestHandler}.
     *
     * @param rh the {@link InboundRequestHandler} to match on children to remove
     */
    public final void removeChild(T rh) {
        for (Map.Entry<String, T> entry : new HashMap<>(mChildren).entrySet()) {
            if (entry.getValue().equals(rh)) {
                removeChild(entry.getKey());
            }
        }
    }

    /**
     * Returns the {@link InboundRequestHandler} for the child with the given name.
     *
     * @param name the name of the child {@link InboundRequestHandler} to return
     * @return the {@link InboundRequestHandler} associated with {@code name} or <i>null</i> if
     *     there is no matching child
     */
    @Nullable
    public final T getChild(String name) {
        return mChildren.get(name);
    }

    /**
     * Returns a name for a child for a given {@link InboundRequestHandler} instance.
     *
     * @param rh the {@link InboundRequestHandler} instance associated with a child
     * @return a name for a child that uses {@link InboundRequestHandler} or <i>null</i> if there is
     *     no matching child
     */
    @Nullable
    public final String getNameForChild(T rh) {
        synchronized (mChildren) {
            for (Map.Entry<String, T> entry : mChildren.entrySet()) {
                if (entry.getValue().equals(rh)) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    /**
     * Checks the inbound request for unsupported options, responding with {@link
     * Code#RESPONSE_BAD_OPTION} if so. If no unsupported options are found the inbound request is
     * not modified.
     *
     * @return true if {@code inboundRequest} contained unsupported options, false otherwise.
     */
    @CanIgnoreReturnValue
    protected boolean checkForUnsupportedOptions(InboundRequest inboundRequest) {
        // Save the option index pointer
        final int optionIndex = inboundRequest.getCurrentOptionIndex();

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

                // Restore the option index pointer
                inboundRequest.setCurrentOptionIndex(optionIndex);
                return true;
            }
        }

        // Restore the option index pointer
        inboundRequest.setCurrentOptionIndex(optionIndex);
        return false;
    }

    /**
     * Hook to be overridden to add links to the link format for this Resource.
     *
     * <p>Failure to call the super method will result in the link format having no child links.
     *
     * @param builder the link format builder instance to use to add additional links
     */
    public void onBuildLinkFormat(LinkFormat.Builder builder) {
        mChildren.forEach(
                (k, v) -> {
                    final LinkFormat.LinkBuilder item;
                    final LinkFormat.Provider provider;

                    // Skip listing hidden children unless there is a query.
                    if (mHiddenChildren.contains(k)) {
                        return;
                    }

                    if (v instanceof LinkFormat.Provider) {
                        provider = (LinkFormat.Provider) v;
                        if (provider.isHiddenFromLinkList()) {
                            return;
                        }
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
    }

    /**
     * Primary handler for when the request is for this instance (not a child) and the method is
     * unknown.
     *
     * <p>Overriding this method is allowed as long as {@link
     * #onParentMethodUnknownCheck(InboundRequest, boolean)} is also overridden.
     *
     * <p>The default implementation calls {@link InboundRequest#sendSimpleResponse(int)} with a
     * code of {@link Code#RESPONSE_METHOD_NOT_ALLOWED}.
     *
     * @param inboundRequest the inbound request object
     * @param trailingSlash true if the request included a trailing slash, false otherwise.
     * @see #onParentMethodUnknownCheck(InboundRequest, boolean)
     */
    public void onParentMethodUnknown(InboundRequest inboundRequest, boolean trailingSlash) {
        inboundRequest.sendSimpleResponse(Code.RESPONSE_METHOD_NOT_ALLOWED);
    }

    /**
     * Check handler for when the request is for this instance (not a child) and the method is
     * unknown.
     *
     * <p>Overriding this method is allowed. See {@link
     * InboundRequestHandler#onInboundRequestCheck(InboundRequest)} for more information about check
     * handlers.
     *
     * <p>The default implementation calls {@link InboundRequest#sendSimpleResponse(int)} with a
     * code of {@link Code#RESPONSE_METHOD_NOT_ALLOWED}.
     *
     * @param inboundRequest the inbound request object
     * @param trailingSlash true if the request included a trailing slash, false otherwise.
     * @see #onParentMethodUnknown(InboundRequest, boolean)
     */
    public void onParentMethodUnknownCheck(InboundRequest inboundRequest, boolean trailingSlash) {
        inboundRequest.sendSimpleResponse(Code.RESPONSE_METHOD_NOT_ALLOWED);
    }

    /**
     * Primary handler for when the request is for this instance (not a child) and the method is
     * {@link Code#METHOD_DELETE}.
     *
     * <p>Overriding this method is allowed as long as {@link
     * #onParentMethodDeleteCheck(InboundRequest, boolean)} is also overridden.
     *
     * <p>The default implementation calls {@link #onParentMethodUnknown(InboundRequest, boolean)}.
     *
     * @param inboundRequest the inbound request object
     * @param trailingSlash true if the request included a trailing slash, false otherwise.
     * @see #onParentMethodDeleteCheck(InboundRequest, boolean)
     */
    public void onParentMethodDelete(InboundRequest inboundRequest, boolean trailingSlash) {
        onParentMethodUnknown(inboundRequest, trailingSlash);
    }

    /**
     * Check handler for when the request is for this instance (not a child) and the method is
     * {@link Code#METHOD_DELETE}.
     *
     * <p>Overriding this method is allowed. See {@link
     * InboundRequestHandler#onInboundRequestCheck(InboundRequest)} for more information about check
     * handlers.
     *
     * <p>The default implementation calls {@link #onParentMethodUnknownCheck(InboundRequest,
     * boolean)}.
     *
     * @param inboundRequest the inbound request object
     * @param trailingSlash true if the request included a trailing slash, false otherwise.
     * @see #onParentMethodDelete(InboundRequest, boolean)
     */
    public void onParentMethodDeleteCheck(InboundRequest inboundRequest, boolean trailingSlash) {
        onParentMethodUnknownCheck(inboundRequest, trailingSlash);
    }

    /**
     * Primary handler for when the request is for this instance (not a child) and the method is
     * {@link Code#METHOD_PUT}.
     *
     * <p>Overriding this method is allowed as long as {@link
     * #onParentMethodPutCheck(InboundRequest, boolean)} is also overridden.
     *
     * <p>The default implementation calls {@link #onParentMethodUnknown(InboundRequest, boolean)}.
     *
     * @param inboundRequest the inbound request object
     * @param trailingSlash true if the request included a trailing slash, false otherwise.
     * @see #onParentMethodPutCheck(InboundRequest, boolean)
     */
    public void onParentMethodPut(InboundRequest inboundRequest, boolean trailingSlash) {
        onParentMethodUnknown(inboundRequest, trailingSlash);
    }

    /**
     * Check handler for when the request is for this instance (not a child) and the method is
     * {@link Code#METHOD_PUT}.
     *
     * <p>Overriding this method is allowed. See {@link
     * InboundRequestHandler#onInboundRequestCheck(InboundRequest)} for more information about check
     * handlers.
     *
     * <p>The default implementation calls {@link #onParentMethodUnknownCheck(InboundRequest,
     * boolean)}.
     *
     * @param inboundRequest the inbound request object
     * @param trailingSlash true if the request included a trailing slash, false otherwise.
     * @see #onParentMethodPut(InboundRequest, boolean)
     */
    public void onParentMethodPutCheck(InboundRequest inboundRequest, boolean trailingSlash) {
        onParentMethodUnknownCheck(inboundRequest, trailingSlash);
    }

    /**
     * Primary handler for when the request is for this instance (not a child) and the method is
     * {@link Code#METHOD_POST}.
     *
     * <p>Overriding this method is allowed as long as {@link
     * #onParentMethodPostCheck(InboundRequest, boolean)} is also overridden.
     *
     * <p>The default implementation calls {@link #onParentMethodUnknown(InboundRequest, boolean)}.
     *
     * @param inboundRequest the inbound request object
     * @param trailingSlash true if the request included a trailing slash, false otherwise.
     * @see #onParentMethodPostCheck(InboundRequest, boolean)
     */
    public void onParentMethodPost(InboundRequest inboundRequest, boolean trailingSlash) {
        onParentMethodUnknown(inboundRequest, trailingSlash);
    }

    /**
     * Check handler for when the request is for this instance (not a child) and the method is
     * {@link Code#METHOD_POST}.
     *
     * <p>Overriding this method is allowed. See {@link
     * InboundRequestHandler#onInboundRequestCheck(InboundRequest)} for more information about check
     * handlers.
     *
     * <p>The default implementation calls {@link #onParentMethodUnknownCheck(InboundRequest,
     * boolean)}.
     *
     * @param inboundRequest the inbound request object
     * @param trailingSlash true if the request included a trailing slash, false otherwise.
     * @see #onParentMethodPost(InboundRequest, boolean)
     */
    public void onParentMethodPostCheck(InboundRequest inboundRequest, boolean trailingSlash) {
        onParentMethodUnknownCheck(inboundRequest, trailingSlash);
    }

    /**
     * Primary handler for when the request is for this instance (not a child) and the method is
     * {@link Code#METHOD_GET}.
     *
     * <p>Overriding this method is allowed as long as {@link
     * #onParentMethodGetCheck(InboundRequest, boolean)} is also overridden.
     *
     * <p>The default implementation will construct a RFC6690 link-format listing of all of the
     * non-hidden children that satisfy the query, unless {@code trailingSlash} is false: in which
     * case it will respond with {@link Code#RESPONSE_BAD_REQUEST}.
     *
     * @param inboundRequest the inbound request object
     * @param trailingSlash true if the request included a trailing slash, false otherwise.
     * @see #onParentMethodGetCheck(InboundRequest, boolean)
     */
    public void onParentMethodGet(InboundRequest inboundRequest, boolean trailingSlash) {
        if (!trailingSlash) {
            inboundRequest.sendSimpleResponse(Code.RESPONSE_BAD_REQUEST, "Trailing slash required");
            return;
        }

        Message request = inboundRequest.getMessage();
        LinkFormat.Builder builder = new LinkFormat.Builder();

        if (request.getOptionSet().hasUriQuery()) {
            builder.setQueryFilter(request.getOptionSet().getUriQueriesAsMap());
        }

        onBuildLinkFormat(builder);

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

    /**
     * Check handler for when the request is for this instance (not a child) and the method is
     * {@link Code#METHOD_GET}.
     *
     * <p>Overriding this method is allowed. See {@link
     * InboundRequestHandler#onInboundRequestCheck(InboundRequest)} for more information about check
     * handlers.
     *
     * <p>The default implementation will respond with {@link Code#RESPONSE_BAD_REQUEST} if {@code
     * trailingSlash} is not {@code true}. Otherwise, it simply calls {@link
     * #checkForUnsupportedOptions(InboundRequest)}.
     *
     * @param inboundRequest the inbound request object
     * @param trailingSlash true if the request included a trailing slash, false otherwise.
     * @see #onParentMethodGet(InboundRequest, boolean)
     */
    public void onParentMethodGetCheck(InboundRequest inboundRequest, boolean trailingSlash) {
        if (!trailingSlash) {
            inboundRequest.sendSimpleResponse(Code.RESPONSE_BAD_REQUEST, "Trailing slash required");
        }

        checkForUnsupportedOptions(inboundRequest);
    }

    /**
     * Primary handler for when the request is for this instance rather than for a child.
     *
     * <p>Overriding this method is allowed as long as {@link #onParentRequestCheck(InboundRequest,
     * boolean)} is also overridden.
     *
     * <p>The default implementation will examine the method of the inbound request and dispatch it
     * accordingly:
     *
     * <ul>
     *   <li>{@code GET}: {@link #onParentMethodGet(InboundRequest, boolean)}
     *   <li>{@code PUT}: {@link #onParentMethodPut(InboundRequest, boolean)}
     *   <li>{@code POST}: {@link #onParentMethodPost(InboundRequest, boolean)}
     *   <li>{@code DELETE}: {@link #onParentMethodDelete(InboundRequest, boolean)}
     *   <li>Anything else: {@link #onParentMethodUnknown(InboundRequest, boolean)}
     * </ul>
     *
     * @param inboundRequest the inbound request object
     * @param trailingSlash true if the request included a trailing slash, false otherwise.
     * @see #onParentRequestCheck(InboundRequest, boolean)
     */
    public void onParentRequest(InboundRequest inboundRequest, boolean trailingSlash) {
        Message request = inboundRequest.getMessage();
        switch (request.getCode()) {
            case Code.METHOD_GET:
                onParentMethodGet(inboundRequest, trailingSlash);
                break;
            case Code.METHOD_PUT:
                onParentMethodPut(inboundRequest, trailingSlash);
                break;
            case Code.METHOD_POST:
                onParentMethodPost(inboundRequest, trailingSlash);
                break;
            case Code.METHOD_DELETE:
                onParentMethodDelete(inboundRequest, trailingSlash);
                break;
            default:
                onParentMethodUnknown(inboundRequest, trailingSlash);
                break;
        }
    }

    /**
     * Primary handler for when the request is for this instance rather than for a child.
     *
     * <p>Overriding this method is allowed. See {@link
     * InboundRequestHandler#onInboundRequestCheck(InboundRequest)} for more information about check
     * handlers.
     *
     * <p>The default implementation will examine the method of the inbound request and dispatch it
     * accordingly:
     *
     * <ul>
     *   <li>{@code GET}: {@link #onParentMethodGetCheck(InboundRequest, boolean)}
     *   <li>{@code PUT}: {@link #onParentMethodPutCheck(InboundRequest, boolean)}
     *   <li>{@code POST}: {@link #onParentMethodPostCheck(InboundRequest, boolean)}
     *   <li>{@code DELETE}: {@link #onParentMethodDeleteCheck(InboundRequest, boolean)}
     *   <li>Anything else: {@link #onParentMethodUnknownCheck(InboundRequest, boolean)}
     * </ul>
     *
     * @param inboundRequest the inbound request object
     * @param trailingSlash true if the request included a trailing slash, false otherwise.
     * @see #onParentRequest(InboundRequest, boolean)
     */
    public void onParentRequestCheck(InboundRequest inboundRequest, boolean trailingSlash) {
        Message request = inboundRequest.getMessage();
        switch (request.getCode()) {
            case Code.METHOD_GET:
                onParentMethodGetCheck(inboundRequest, trailingSlash);
                break;
            case Code.METHOD_PUT:
                onParentMethodPutCheck(inboundRequest, trailingSlash);
                break;
            case Code.METHOD_POST:
                onParentMethodPostCheck(inboundRequest, trailingSlash);
                break;
            case Code.METHOD_DELETE:
                onParentMethodDeleteCheck(inboundRequest, trailingSlash);
                break;
            default:
                onParentMethodUnknownCheck(inboundRequest, trailingSlash);
                break;
        }
    }

    /**
     * Primary handler for when the request is for a specific child when the method was not
     * explicitly handled earlier.
     *
     * <p>Overriding this method is allowed as long as {@link
     * #onChildMethodUnknownCheck(InboundRequest, InboundRequestHandler)} is also overridden.
     *
     * <p>The default implementation calls {@code child.onInboundRequest(inboundRequest)}, which
     * effectively gives responsibility for handling this request to the child.
     *
     * @param inboundRequest the inbound request object
     * @param child the inbound request handler of the child
     * @see #onChildMethodUnknownCheck(InboundRequest, InboundRequestHandler)
     */
    public void onChildMethodUnknown(InboundRequest inboundRequest, T child) {
        child.onInboundRequest(inboundRequest);
    }

    /**
     * Check handler for when the request is for a specific child when the method was not explicitly
     * handled earlier.
     *
     * <p>Overriding this method is allowed. See {@link
     * InboundRequestHandler#onInboundRequestCheck(InboundRequest)} for more information about check
     * handlers.
     *
     * <p>The default implementation calls {@code child.onInboundRequestCheck(inboundRequest)},
     * which effectively gives responsibility for handling this request to the child.
     *
     * @param inboundRequest the inbound request object
     * @param child the inbound request handler of the child
     * @see #onChildMethodUnknown(InboundRequest, T)
     */
    public void onChildMethodUnknownCheck(InboundRequest inboundRequest, T child) {
        child.onInboundRequestCheck(inboundRequest);
    }

    public void onChildMethodGet(InboundRequest inboundRequest, T child) {
        onChildMethodUnknown(inboundRequest, child);
    }

    public void onChildMethodGetCheck(InboundRequest inboundRequest, T child) {
        onChildMethodUnknownCheck(inboundRequest, child);
    }

    public void onChildMethodDelete(InboundRequest inboundRequest, T child) {
        onChildMethodUnknown(inboundRequest, child);
    }

    public void onChildMethodDeleteCheck(InboundRequest inboundRequest, T child) {
        onChildMethodUnknownCheck(inboundRequest, child);
    }

    public void onChildMethodPut(InboundRequest inboundRequest, T child) {
        onChildMethodUnknown(inboundRequest, child);
    }

    public void onChildMethodPutCheck(InboundRequest inboundRequest, T child) {
        onChildMethodUnknownCheck(inboundRequest, child);
    }

    public void onChildMethodPost(InboundRequest inboundRequest, T child) {
        onChildMethodUnknown(inboundRequest, child);
    }

    public void onChildMethodPostCheck(InboundRequest inboundRequest, T child) {
        onChildMethodUnknownCheck(inboundRequest, child);
    }

    /**
     * Primary handler for when the request is for a specific child.
     *
     * <p>Overriding this method is allowed as long as {@link #onChildRequestCheck(InboundRequest,
     * InboundRequestHandler)} is also overridden.
     *
     * <p>The default implementation will examine the method of the inbound request and dispatch it
     * accordingly:
     *
     * <ul>
     *   <li>{@code GET}: {@link #onChildMethodGet}
     *   <li>{@code PUT}: {@link #onChildMethodPut}
     *   <li>{@code POST}: {@link #onChildMethodPost}
     *   <li>{@code DELETE}: {@link #onChildMethodDelete}
     *   <li>Anything else: {@link #onChildMethodUnknown}
     * </ul>
     *
     * @param inboundRequest the inbound request object
     * @param child the inbound request handler of the child
     * @see #onChildRequestCheck(InboundRequest, T)
     */
    public void onChildRequest(InboundRequest inboundRequest, T child) {
        Message request = inboundRequest.getMessage();
        switch (request.getCode()) {
            case Code.METHOD_GET:
                onChildMethodGet(inboundRequest, child);
                break;
            case Code.METHOD_PUT:
                onChildMethodPut(inboundRequest, child);
                break;
            case Code.METHOD_POST:
                onChildMethodPost(inboundRequest, child);
                break;
            case Code.METHOD_DELETE:
                onChildMethodDelete(inboundRequest, child);
                break;
            default:
                onChildMethodUnknown(inboundRequest, child);
                break;
        }
    }

    /**
     * Check handler for when the request is for a specific child.
     *
     * <p>Overriding this method is allowed. See {@link
     * InboundRequestHandler#onInboundRequestCheck(InboundRequest)} for more information about check
     * handlers.
     *
     * <p>The default implementation will examine the method of the inbound request and dispatch it
     * accordingly:
     *
     * <ul>
     *   <li>{@code GET}: {@link #onChildMethodGetCheck}
     *   <li>{@code PUT}: {@link #onChildMethodPutCheck}
     *   <li>{@code POST}: {@link #onChildMethodPostCheck}
     *   <li>{@code DELETE}: {@link #onChildMethodDeleteCheck}
     *   <li>Anything else: {@link #onChildMethodUnknownCheck}
     * </ul>
     *
     * @param inboundRequest the inbound request object
     * @param child the inbound request handler of the child
     * @see #onChildRequest(InboundRequest, T)
     */
    public void onChildRequestCheck(InboundRequest inboundRequest, T child) {
        Message request = inboundRequest.getMessage();
        switch (request.getCode()) {
            case Code.METHOD_GET:
                onChildMethodGetCheck(inboundRequest, child);
                break;
            case Code.METHOD_PUT:
                onChildMethodPutCheck(inboundRequest, child);
                break;
            case Code.METHOD_POST:
                onChildMethodPostCheck(inboundRequest, child);
                break;
            case Code.METHOD_DELETE:
                onChildMethodDeleteCheck(inboundRequest, child);
                break;
            default:
                onChildMethodUnknownCheck(inboundRequest, child);
                break;
        }
    }

    /**
     * Primary handler for when the request is for an unknown child.
     *
     * <p>The default implementation calls {@code
     * inboundRequest.sendSimpleResponse(Code.RESPONSE_NOT_FOUND)}.
     *
     * <p>Overload this method to get requests destined for unknown child resources.
     */
    public void onUnknownChildRequest(InboundRequest inboundRequest, String childName) {
        inboundRequest.sendSimpleResponse(Code.RESPONSE_NOT_FOUND);
    }

    /**
     * Check handler for when the request is for an unknown child.
     *
     * <p>The default implementation calls {@code
     * inboundRequest.sendSimpleResponse(Code.RESPONSE_NOT_FOUND)}.
     *
     * <p>Overload this method to check block requests destined for unknown child resources.
     */
    public void onUnknownChildRequestCheck(InboundRequest inboundRequest, String childName) {
        inboundRequest.sendSimpleResponse(Code.RESPONSE_NOT_FOUND);
    }

    /**
     * Primary handler for when the request is for a specific child that hasn't been looked up yet.
     *
     * <p>Overriding this method is allowed as long as {@link #onChildRequestCheck(InboundRequest,
     * String)} is also overridden.
     *
     * <p>The default implementation will try to look up the {@link InboundRequestHandler} for
     * {@code childName}. If it is successful, it will call {@link #onChildRequest(InboundRequest,
     * InboundRequestHandler)}. If not successful, it will call {@link
     * InboundRequest#rewindOneOption()}} (to move the option pointer back to before the child name)
     * and then call {@link #onUnknownChildRequest(InboundRequest, String)}.
     *
     * @param inboundRequest the inbound request object
     * @param childName the path name of the child in question
     * @see #onChildRequestCheck(InboundRequest, String)
     */
    public void onChildRequest(InboundRequest inboundRequest, String childName) {
        T child = mChildren.get(childName);
        if (child != null) {
            onChildRequest(inboundRequest, child);

        } else {
            onUnknownChildRequest(inboundRequest, childName);
        }
    }

    /**
     * Check handler for when the request is for a specific child that hasn't been looked up yet.
     *
     * <p>Overriding this method is allowed. See {@link
     * InboundRequestHandler#onInboundRequestCheck(InboundRequest)} for more information about check
     * handlers.
     *
     * <p>The default implementation will try to look up the {@link InboundRequestHandler} for
     * {@code childName}. If it is successful, it will call {@link
     * #onChildRequestCheck(InboundRequest, InboundRequestHandler)}. If not successful, it will call
     * {@link InboundRequest#rewindOneOption()}} (to move the option pointer back to before the
     * child name) and then call {@link #onUnknownChildRequest(InboundRequest, String)}.
     *
     * @param inboundRequest the inbound request object
     * @param childName the path name of the child in question
     * @see #onChildRequest(InboundRequest, String)
     */
    public void onChildRequestCheck(InboundRequest inboundRequest, String childName) {
        T child = mChildren.get(childName);
        if (child != null) {
            onChildRequestCheck(inboundRequest, child);

        } else {
            onUnknownChildRequest(inboundRequest, childName);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation will read the next {@link Option#URI_PATH} from {@code
     * inboundRequest} in order to determine if this is a request for a child or if it is a request
     * for this specific resource. Based on that determination, it will call either {@link
     * #onChildRequest(InboundRequest, String)} or {@link #onParentRequest(InboundRequest,
     * boolean)}.
     */
    @Override
    public void onInboundRequest(InboundRequest inboundRequest) {
        Option nextOption = inboundRequest.nextOptionWithNumber(Option.URI_PATH);
        boolean trailingSlash = true;

        if (nextOption != null && nextOption.getNumber() == Option.URI_PATH) {
            String childName = nextOption.stringValue();
            if (!childName.isEmpty()) {
                onChildRequest(inboundRequest, childName);
                return;
            }

        } else {
            inboundRequest.rewindOneOption();
            trailingSlash = !inboundRequest.getMessage().getOptionSet().hasUriPath();
        }

        if (mObservable.handleInboundRequest(inboundRequest)) {
            // Handled by the observable
            return;
        }

        onParentRequest(inboundRequest, trailingSlash);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation will read the next {@link Option#URI_PATH} from {@code
     * inboundRequest} in order to determine if this is a request for a child or if it is a request
     * for this specific resource. Based on that determination, it will call either {@link
     * #onChildRequestCheck(InboundRequest, String)} or {@link #onParentRequestCheck(InboundRequest,
     * boolean)}.
     */
    @Override
    public void onInboundRequestCheck(InboundRequest inboundRequest) {
        Option nextOption = inboundRequest.nextOptionWithNumber(Option.URI_PATH);
        boolean trailingSlash = true;

        if (nextOption != null && nextOption.getNumber() == Option.URI_PATH) {
            String childName = nextOption.stringValue();
            if (!childName.isEmpty()) {
                onChildRequestCheck(inboundRequest, childName);
                return;
            }

        } else {
            inboundRequest.rewindOneOption();
            trailingSlash = !inboundRequest.getMessage().getOptionSet().hasUriPath();
        }

        onParentRequestCheck(inboundRequest, trailingSlash);
    }

    /**
     * {@inheritDoc} If you override this method, you MUST call {@code
     * super.onBuildLinkParams(builder)}.
     */
    @Override
    public void onBuildLinkParams(LinkFormat.LinkBuilder builder) {
        builder.addContentFormat(ContentFormat.APPLICATION_LINK_FORMAT)
                .put(LinkFormat.PARAM_OBSERVABLE, "1");
    }
}
