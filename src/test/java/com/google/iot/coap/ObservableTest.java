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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@SuppressWarnings("ConstantConditions")
class ObservableTest {
    private static final boolean DEBUG = false;
    private static final Logger LOGGER =
            Logger.getLogger(ClientServerTest.class.getCanonicalName());

    ScheduledExecutorService mExecutor = null;
    volatile Throwable mThrowable = null;

    private LocalEndpointManager mContext = null;
    private Server mServer = null;
    private Client mClient = null;
    private StartTimeCounterResource mChild = null;
    private Resource mRoot = null;

    public void rethrow() {
        if (mExecutor != null) {
            try {
                mExecutor.shutdown();
                assertTrue(mExecutor.awaitTermination(1, TimeUnit.SECONDS));
            } catch (Exception x) {
                if (mThrowable == null) {
                    mThrowable = x;
                } else {
                    LOGGER.info("Got exception while flushing queue: " + x);
                    x.printStackTrace();
                }
            }
            mExecutor = null;
        }
        if (mThrowable != null) {
            Throwable x = mThrowable;
            mThrowable = null;
            LOGGER.info("Rethrowing throwable: " + x);
            if (x instanceof Error) throw (Error) x;
            if (x instanceof RuntimeException) throw (RuntimeException) x;
            throw new RuntimeException(x);
        }
    }

    @BeforeEach
    public void before() throws IOException {
        MockitoAnnotations.initMocks(this);
        mThrowable = null;

        mExecutor =
                new FakeScheduledExecutorService() {
                    // mExecutor = new ScheduledThreadPoolExecutor(1) {
                    @Override
                    public void execute(Runnable command) {
                        super.execute(
                                () -> {
                                    try {
                                        command.run();
                                    } catch (Throwable x) {
                                        LOGGER.info("Caught throwable: " + x);
                                        x.printStackTrace();
                                        mThrowable = x;
                                    }
                                });
                    }
                };

        mContext =
                new LocalEndpointManager() {

                    @Override
                    public ScheduledExecutorService getExecutor() {
                        return mExecutor;
                    }
                };
        mServer = new Server(mContext);
        mClient = new Client(mContext, "loop://localhost/");
        mServer.addLocalEndpoint(mContext.getLocalEndpointForScheme("loop"));
        mRoot = new Resource();
        mChild = new StartTimeCounterResource();
        mRoot.addChild("hello", mChild);
        mServer.setRequestHandler(mRoot);
        mServer.start();
    }

    private void tick(int durationInMs) throws InterruptedException {
        if (mExecutor instanceof FakeScheduledExecutorService) {
            ((FakeScheduledExecutorService) mExecutor).tick(durationInMs);
        } else {
            Thread.sleep(durationInMs);
        }
    }

    @AfterEach
    public void after() throws IOException {
        mClient.cancelAllTransactions();
        mServer.close();
        mContext.close();
        rethrow();
    }

    class StartTimeCounterResource
            implements InboundRequestHandler, LinkFormat.Provider, Observable.Provider {
        private final Observable mObservable = new Observable();
        private final long mStartTime = System.nanoTime();
        private final Runnable mTriggerRunnable = mObservable::trigger;
        private Future<?> mScheduledTrigger = null;

        StartTimeCounterResource() {
            mObservable.registerCallback(
                    mExecutor,
                    new Observable.Callback() {
                        @Override
                        public void onHasRemoteObservers(Observable observable) {
                            mScheduledTrigger =
                                    mExecutor.scheduleAtFixedRate(
                                            mTriggerRunnable, 20, 20, TimeUnit.MILLISECONDS);
                        }

                        @Override
                        public void onNoRemoteObservers(Observable observable) {
                            Future<?> scheduledTrigger = mScheduledTrigger;
                            if (scheduledTrigger != null) {
                                scheduledTrigger.cancel(false);
                            }
                        }
                    });
        }

        String getValue() {
            return Long.toString(
                    TimeUnit.MILLISECONDS.convert(
                            System.nanoTime() - mStartTime, TimeUnit.NANOSECONDS));
        }

        @Override
        public void onBuildLinkParams(LinkFormat.LinkBuilder builder) {
            builder.setObservable(true);
            builder.setValue(getValue());
        }

        @Override
        public Observable onGetObservable() {
            return mObservable;
        }

        @Override
        public void onInboundRequest(InboundRequest inboundRequest) {
            if (inboundRequest.getMessage().getCode() != Code.METHOD_GET) {
                inboundRequest.sendSimpleResponse(Code.RESPONSE_METHOD_NOT_ALLOWED);
            }

            if (inboundRequest.nextOptionWithNumber(Option.URI_PATH) != null) {
                inboundRequest.sendSimpleResponse(Code.RESPONSE_NOT_FOUND);
            }

            if (mObservable.handleInboundRequest(inboundRequest)) {
                return;
            }

            inboundRequest.sendSimpleResponse(Code.RESPONSE_CONTENT, getValue());
        }

        @Override
        public void onInboundRequestCheck(InboundRequest inboundRequest) {
            if (inboundRequest.getMessage().getCode() != Code.METHOD_GET) {
                inboundRequest.sendSimpleResponse(Code.RESPONSE_METHOD_NOT_ALLOWED);
            }
            if (inboundRequest.nextOptionWithNumber(Option.URI_PATH) != null) {
                inboundRequest.sendSimpleResponse(Code.RESPONSE_NOT_FOUND);
            }
        }
    }

    @Test
    void handleInboundRequest() throws Exception {
        Transaction transaction = mClient.newRequestBuilder().changePath("hello").send();

        tick(1);

        Message response = transaction.getResponse(1500);

        if (DEBUG) LOGGER.info("Got response: " + response);

        assertEquals(Code.toString(Code.RESPONSE_CONTENT), Code.toString(response.getCode()));
    }

    @Test
    void triggerNoObservers() throws Exception {
        mChild.onGetObservable().trigger();

        Transaction transaction = mClient.newRequestBuilder().changePath("hello").send();

        tick(1);

        Message response = transaction.getResponse(1500);

        assertEquals(Code.toString(Code.RESPONSE_CONTENT), Code.toString(response.getCode()));
    }

    @Test
    void triggerWithMessageNoObservers() throws Exception {
        mChild.onGetObservable()
                .triggerWithMessage(MutableMessage.create().setPayload("FORCED MESSAGE"));

        Transaction transaction = mClient.newRequestBuilder().changePath("hello").send();

        tick(1);

        Message response = transaction.getResponse(1500);

        assertEquals(Code.toString(Code.RESPONSE_CONTENT), Code.toString(response.getCode()));
    }

    @Test
    void getObserverCount() throws Exception {
        // First, add an observer.
        Transaction transaction =
                mClient.newRequestBuilder().changePath("hello").addOption(Option.OBSERVE).send();

        tick(1);

        Message response = transaction.getResponse(1500);

        // Verify that everything is as we expect it.
        assertTrue(response.getOptionSet().hasObserve());
        assertEquals(1, mChild.onGetObservable().getObserverCount());

        // Now remove the observer.
        transaction =
                mClient.newRequestBuilder()
                        .changePath("hello")
                        .setToken(response.getToken())
                        .send();

        tick(1);

        response = transaction.getResponse(1500);

        // Verify that everything is as we expect it.
        assertFalse(response.getOptionSet().hasObserve());
        assertEquals(0, mChild.onGetObservable().getObserverCount());
    }

    @Test
    void ejectObservers() throws Exception {
        // First, add an observer.
        Transaction transaction =
                mClient.newRequestBuilder().changePath("hello").addOption(Option.OBSERVE).send();

        tick(1);

        Message response = transaction.getResponse(1500);

        // Verify that everything is as we expect it.
        assertTrue(response.getOptionSet().hasObserve());
        assertEquals(1, mChild.onGetObservable().getObserverCount());

        // Now eject the observers.
        mChild.onGetObservable().ejectObservers();

        // Verify that everything is as we expect it.
        assertEquals(0, mChild.onGetObservable().getObserverCount());

        // TODO: Verify that the ejection message was sent out
    }

    @Captor ArgumentCaptor<Message> messageCaptor;

    @Mock Transaction.Callback transactionCallbackMock;

    @Test
    Transaction triggerWithObservers() throws Exception {

        Transaction transaction =
                mClient.newRequestBuilder().changePath("hello").addOption(Option.OBSERVE).send();

        transaction.registerCallback(Runnable::run, transactionCallbackMock);

        tick(1);

        Message response = transaction.getResponse(1500);

        // Verify that everything is as we expect it.
        verify(transactionCallbackMock)
                .onTransactionResponse(
                        eq(mContext.getLocalEndpointForScheme("loop")), messageCaptor.capture());
        assertEquals(response, messageCaptor.getValue());
        verify(transactionCallbackMock, never()).onTransactionFinished();
        verify(transactionCallbackMock, never()).onTransactionException(null);
        assertTrue(response.getOptionSet().hasObserve());
        assertEquals(1, mChild.onGetObservable().getObserverCount());

        clearInvocations(transactionCallbackMock);

        tick(40);

        verify(transactionCallbackMock, atMost(3))
                .onTransactionResponse(eq(mContext.getLocalEndpointForScheme("loop")), any());
        verify(transactionCallbackMock, atLeast(2))
                .onTransactionResponse(eq(mContext.getLocalEndpointForScheme("loop")), any());
        verify(transactionCallbackMock, never()).onTransactionFinished();
        verify(transactionCallbackMock, never()).onTransactionException(null);

        clearInvocations(transactionCallbackMock);

        response = transaction.getResponse();

        // Verify that everything is as we expect it.
        assertTrue(response.getOptionSet().hasObserve());
        assertEquals((Integer) 2, response.getOptionSet().getObserve());
        assertEquals(1, mChild.onGetObservable().getObserverCount());

        return transaction;
    }

    @Test
    void explicitObservationCancellation() throws Exception {
        Transaction transaction = triggerWithObservers();

        tick(1);

        transaction.cancel();

        tick(1);

        verify(transactionCallbackMock, times(1)).onTransactionCancelled();
        verify(transactionCallbackMock, times(1)).onTransactionFinished();

        assertTrue(transaction.isCancelled());
        assertFalse(transaction.isActive());

        tick(40);

        verify(transactionCallbackMock, never()).onTransactionResponse(any(), any());

        // Verify that the observation was canceled server-side
        assertEquals(0, mChild.onGetObservable().getObserverCount());
    }

    @Test
    void implicitObservationCancellation() throws Exception {
        Transaction transaction = triggerWithObservers();

        tick(1);

        transaction.cancelWithoutUnobserve();

        tick(1);

        verify(transactionCallbackMock, times(1)).onTransactionCancelled();
        verify(transactionCallbackMock, times(1)).onTransactionFinished();

        assertTrue(transaction.isCancelled());
        assertFalse(transaction.isActive());

        tick(40);

        verify(transactionCallbackMock, never()).onTransactionResponse(any(), any());

        // Verify that the observation was canceled server-side
        assertEquals(0, mChild.onGetObservable().getObserverCount());
    }
}
