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

import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Object representing an inbound request, providing methods for examining the request and
 * responding to it. InboundRequests are handled by {@link InboundRequestHandler
 * InboundRequestHandlers}.
 *
 * <p>These objects are only valid within the initial call stack from which they are provided,
 * UNLESS {@link #responsePending()} has been called. In other words, it is a runtime error to make
 * a call to a method of an instance of this class from outside of the initial call stack (such as
 * from a separate thread) unless {@link #responsePending()} was called from that initial call stack
 * first. Effectively this means that if you want to defer responding to a request until a later
 * point in time, you MUST call {@link #responsePending()} before returning.
 *
 * <p>The {@link InboundRequest} interface also has a mechanism for reading {@link Option Options}
 * in a way that keeps track of the index in the option list, much like an iterator. This mechanism
 * is used to read the request's options (like {@link Option#URI_PATH}) and process them as
 * necessary.
 *
 * <p>This mechanism is also used to indicate to the {@link InboundRequestHandler} at what position
 * in the path that it should consider itself hosted at. Take the following example using {@link
 * Resource} instances:
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
 * <p>When an inbound request for "/test/hello" arrives, {@code rootResource} looks at the first
 * {@link Option#URI_PATH} and uses that to look up the child: {@code testFolder} in this case. It
 * then calls {@link Resource#onInboundRequest(InboundRequest) testFolder.onInboundRequest(...)}. At
 * that point, {@code testFolder} needs to look up the next {@link Option#URI_PATH}, but how does it
 * know what the next {@link Option#URI_PATH} would be? {@code testFolder} has no information about
 * where it is rooted in the resource tree— it just gets a called to handle an inbound request,
 * which could be from anywhere.
 *
 * <p>{@link #nextOption()} provides the necessary context to make this arrangement work. {@code
 * rootResource} "consumes" all of the options up to and including the option that describes the
 * name the child in question— <em>before</em> calling {@link
 * Resource#onInboundRequest(InboundRequest) testFolder.onInboundRequest(...)}. {@code testResource}
 * uses that starting point as its own "root" for looking up its own children.
 *
 * @see InboundRequestHandler
 * @see Server#setRequestHandler(InboundRequestHandler)
 */
public interface InboundRequest {
    /**
     * Callback interface for receiving additional asynchronous events related to a specific {@link
     * InboundRequest}.
     *
     * @see #registerCallback(Callback)
     */
    interface Callback {
        /**
         * Hook called from {@link InboundRequest#sendResponse}. Gives the receiver an opportunity
         * to mutate the message before it is sent, or drop the message outright.
         *
         * <p>The need to use this hook is rare, but it is especially useful in the implementation
         * of {@link Observable}.
         *
         * @param message a mutable message object which can be optionally changed
         * @return true if the message should be sent, false if the message should be dropped.
         */
        default boolean onInboundRequestSendResponseHook(MutableMessage message) {
            return true;
        }

        /**
         * Called if there is an indication that either the outbound response wasn't sent properly
         * or was rejected by the requester. There are generally three types of exceptions that
         * might be reported here:
         *
         * <ul>
         *   <li>{@link java.io.IOException} if something went wrong at the transport layer
         *   <li>{@link ResetException} if a reset message was received for a confirmable response
         *   <li>{@link CoapRuntimeException} if there was something wrong with the response itself
         * </ul>
         *
         * @param exception an exception object describing the root cause of the failure
         */
        default void onInboundRequestResponseFailure(Exception exception) {}
    }

    /**
     * Returns {@link LocalEndpoint} which received this request.
     *
     * @throws InboundRequestOutOfScopeException if this method was called from an unexpected thread
     *     without having called {@link #responsePending()} from the original thread
     */
    LocalEndpoint getLocalEndpoint();

    /**
     * Returns the {@link Message} that contains the request.
     *
     * @throws InboundRequestOutOfScopeException if this method was called from an unexpected thread
     *     without having called {@link #responsePending()} from the original thread
     */
    Message getMessage();

    /**
     * Indicates if this response has been fully handled or not.
     *
     * @return true if this request has been fully handled, false if it is still in play
     */
    boolean isDone();

    /**
     * Indicates if {@link #responsePending()} has been called, allowing the request to be responded
     * to at a later point in time.
     *
     * @return true if {@link #responsePending()} has been called, false otherwise
     * @see #responsePending()
     */
    boolean isResponsePending();

    /**
     * Indicates if this request has been acknowledged (piggy-backed or empty).
     *
     * <p>Most responses are sent piggy-backed with an {@link Type#ACK} packet, but an empty {@link
     * Type#ACK} packet may be emitted by calling {@link #acknowledge()} (or it may be spontaneously
     * emitted after a call to {@link #responsePending()}).
     *
     * <p>This method will return true in either case.
     *
     * @return true if any sort of response has been sent, false otherwise.
     */
    boolean didAcknowledge();

    /**
     * Drops the request. No response will be sent to the source of the request. Calling this method
     * will "finish" this InboundRequest, meaning {@link #isDone()} will subsequently return true.
     *
     * @throws InboundRequestOutOfScopeException if this method was called from an unexpected thread
     *     without having called {@link #responsePending()} from the original thread
     */
    void drop();

    /**
     * Acknowledges the request. Sends an empty acknowledgement packet to the source of the request,
     * indicating to the source that they do not need to retransmit the request but should keep
     * waiting for a full response.
     *
     * @throws InboundRequestOutOfScopeException if this method was called from an unexpected thread
     *     without having called {@link #responsePending()} from the original thread
     */
    void acknowledge();

    /**
     * Sends a response to the source of the request. This will usually "finish" this InboundRequest
     * (meaning {@link #isDone()} will subsequently return true), but there are circumstances where
     * that might not be the case.
     *
     * @throws InboundRequestOutOfScopeException if this method was called from an unexpected thread
     *     without having called {@link #responsePending()} from the original thread
     */
    void sendResponse(MutableMessage response);

    /**
     * Allows you to register a {@link Callback} instance.
     *
     * @throws InboundRequestOutOfScopeException if this method was called from an unexpected thread
     *     without having called {@link #responsePending()} from the original thread
     */
    void registerCallback(Callback callback);

    /**
     * Allows this request to remain valid after the initial call to {@link
     * InboundRequestHandler#onInboundRequest(InboundRequest)} has returned. This method MUST be
     * called before this instance can be used from outside of that initial call, such as from
     * another thread or from being scheduled for execution on an {@link
     * java.util.concurrent.Executor}.
     *
     * @throws InboundRequestOutOfScopeException if this method was called from an unexpected thread
     *     without having called {@link #responsePending()} from the original thread
     */
    void responsePending();

    /**
     * Gets the value of the "next option index" used internally by {@link #nextOption()}.
     *
     * @return the value of the "next option index"
     * @see #setCurrentOptionIndex(int)
     * @see #nextOption()
     */
    int getCurrentOptionIndex();

    /**
     * Changes the value of the "next option index", affecting which option is next returned by
     * {@link #nextOption()}.
     *
     * @param optionIndex the index of the option to be returned the next time {@link #nextOption()}
     *     is called
     * @throws IndexOutOfBoundsException if {@code optionIndex} is less than 0 or greater than the
     *     number of options in {@link #getMessage()}.
     * @throws InboundRequestOutOfScopeException if this method was called from an unexpected thread
     *     without having called {@link #responsePending()} from the original thread
     * @see #getCurrentOptionIndex()
     * @see #nextOption()
     */
    void setCurrentOptionIndex(int optionIndex);

    /**
     * Gets the next {@link Option} in the message.
     *
     * @return the next option in the message, or null if there are no more options.
     * @throws InboundRequestOutOfScopeException if this method was called from an unexpected thread
     *     without having called {@link #responsePending()} from the original thread
     * @see #getCurrentOptionIndex()
     * @see #setCurrentOptionIndex(int)
     * @see #nextOptionWithNumber(int)
     * @see #rewindOneOption()
     * @see #resetNextOption()
     */
    @Nullable
    Option nextOption();

    /**
     * Gets the next {@link Option} in the message that has a number equal to {@code number}.
     *
     * <p>This method will return {@code null} if there are no more options with {@code number}.
     * When that happens, calling {@link #nextOption()} will return the next option with a number
     * greater than {@code number}.
     *
     * @return the next option matching {@code number}, or null if there are no more options
     *     matching {@code number}.
     * @throws InboundRequestOutOfScopeException if this method was called from an unexpected thread
     *     without having called {@link #responsePending()} from the original thread
     * @see #nextOption()
     * @see #getCurrentOptionIndex()
     * @see #setCurrentOptionIndex(int)
     * @see #rewindOneOption()
     * @see #resetNextOption()
     */
    @Nullable
    Option nextOptionWithNumber(int number);

    /**
     * Resets the behavior of {@link #nextOption()} to make it return the prior option. Does nothing
     * if the prior option was the first option. This is similar calling {@code
     * setCurrentOptionIndex(getCurrentOptionIndex()-1)}, except that it doesn't throw an {@link
     * IndexOutOfBoundsException} if you are already at the initial option.
     *
     * @throws InboundRequestOutOfScopeException if this method was called from an unexpected thread
     *     without having called {@link #responsePending()} from the original thread
     */
    void rewindOneOption();

    /**
     * Resets the behavior of {@link #nextOption()} to return the first option. This is equivalent
     * to calling {@code setCurrentOptionIndex(0)}.
     *
     * @throws InboundRequestOutOfScopeException if this method was called from an unexpected thread
     *     without having called {@link #responsePending()} from the original thread
     */
    void resetNextOption();

    /**
     * Finishes this request with a successful response code (appropriate for the request method)
     * and an empty payload.
     *
     * @throws InboundRequestOutOfScopeException if this method was called from an unexpected thread
     *     without having called {@link #responsePending()} from the original thread
     */
    void success();

    /**
     * Sends a {@link Code#RESPONSE_CREATED} message with {@code locationPath} indicating the
     * location of the created resource. The payload is empty.
     *
     * @param locationPath the location of the created resource relative to the request's {@link
     *     Option#URI_PATH}.
     * @throws InboundRequestOutOfScopeException if this method was called from an unexpected thread
     *     without having called {@link #responsePending()} from the original thread
     */
    default void sendCreated(@Nullable String locationPath) {
        sendCreated(locationPath, null);
    }

    /**
     * Sends a {@link Code#RESPONSE_CREATED} message with {@code locationPath} indicating the
     * location of the created resource and the given text payload.
     *
     * @param locationPath the location of the created resource relative to the request's {@link
     *     Option#URI_PATH}.
     * @throws InboundRequestOutOfScopeException if this method was called from an unexpected thread
     *     without having called {@link #responsePending()} from the original thread
     */
    default void sendCreated(@Nullable String locationPath, @Nullable String payload) {
        MutableMessage msg = getMessage().createResponse(Code.RESPONSE_CREATED);
        if (payload != null) {
            msg.setPayload(payload);
        }
        msg.getOptionSet().setLocation(locationPath);
        sendResponse(msg);
    }

    /**
     * Sends a simple response with the given {@link Code} and a payload containing a text string
     * describing the code. This is useful for things like {@link Code#RESPONSE_NOT_FOUND}
     * responses. The content format is unspecified.
     *
     * @param code the {@link Code} to respond with
     * @throws InboundRequestOutOfScopeException if this method was called from an unexpected thread
     *     without having called {@link #responsePending()} from the original thread
     */
    default void sendSimpleResponse(int code) {
        sendSimpleResponse(code, Code.toString(code));
    }

    /**
     * Sends a simple response with the given {@link Code} and specified payload {@link String}. The
     * content format is unspecified.
     *
     * @param code the {@link Code} to respond with
     * @param payload the string to use as the payload
     * @throws InboundRequestOutOfScopeException if this method was called from an unexpected thread
     *     without having called {@link #responsePending()} from the original thread
     */
    default void sendSimpleResponse(int code, String payload) {
        if (getMessage().isMulticast() && (Code.classValue(code) != Code.Class.SUCCESS)) {
            drop();
        } else {
            MutableMessage msg = getMessage().createResponse(code).setPayload(payload);
            sendResponse(msg);
        }
    }
}
