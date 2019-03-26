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

import java.util.concurrent.Future;
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
class ObservableTest extends LoopbackServerClientTestBase {
    private static final boolean DEBUG = false;
    private static final Logger LOGGER =
            Logger.getLogger(ObservableTest.class.getCanonicalName());

    private StartTimeCounterResource mChild = null;
    private static final int OBSERVABLE_UPDATE_PERIOD_MS = 1000;

    private LocalEndpoint mExpectedLocalEndpoint;

    @BeforeEach
    public void before() throws Exception {
        super.before();

        mChild = new StartTimeCounterResource();
        mRoot.addChild("hello", mChild);
        mExpectedLocalEndpoint = mContext.getLocalEndpointForScheme(Coap.SCHEME_LOOPBACK);

        MockitoAnnotations.initMocks(this);
    }

    @AfterEach
    public void after() throws Exception {
        super.after();
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
                            mScheduledTrigger = mExecutor.scheduleAtFixedRate(
                                    mTriggerRunnable, OBSERVABLE_UPDATE_PERIOD_MS,
                                    OBSERVABLE_UPDATE_PERIOD_MS, TimeUnit.MILLISECONDS);
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


    Transaction createObservingTransaction() throws Exception {
        Transaction transaction =
                mClient.newRequestBuilder().changePath("hello").addOption(Option.OBSERVE).send();

        transaction.registerCallback(Runnable::run, transactionCallbackMock);

        tick(1);

        Message response = transaction.getResponse(1500);

        // Verify that everything is as we expect it.
        verify(transactionCallbackMock)
                .onTransactionResponse(
                        eq(mExpectedLocalEndpoint), messageCaptor.capture());
        assertEquals(response, messageCaptor.getValue());
        verify(transactionCallbackMock, never()).onTransactionFinished();
        verify(transactionCallbackMock, never()).onTransactionException(null);
        assertTrue(response.getOptionSet().hasObserve());
        assertEquals(1, mChild.onGetObservable().getObserverCount());

        clearInvocations(transactionCallbackMock);

        return transaction;
    }


    @Test
    void triggerWithObservers() throws Exception {
        try {
            Transaction transaction = createObservingTransaction();

            clearInvocations(transactionCallbackMock);

            tick(OBSERVABLE_UPDATE_PERIOD_MS + 1);
            tick(OBSERVABLE_UPDATE_PERIOD_MS + 1);

            verify(transactionCallbackMock, atMost(3))
                    .onTransactionResponse(eq(mExpectedLocalEndpoint), any());
            verify(transactionCallbackMock, atLeast(2))
                    .onTransactionResponse(eq(mExpectedLocalEndpoint), any());
            verify(transactionCallbackMock, never()).onTransactionFinished();
            verify(transactionCallbackMock, never()).onTransactionException(null);

            clearInvocations(transactionCallbackMock);

            Message response = transaction.getResponse();

            // Verify that everything is as we expect it.
            assertTrue(response.getOptionSet().hasObserve());
            assertTrue(2 <= response.getOptionSet().getObserve());
            assertEquals(1, mChild.onGetObservable().getObserverCount());
        }
        catch (Throwable t) {
            dumpLogs();
            throw t;
        }
    }

    @Test
    void triggerWithObserversOverTime() throws Exception {
        int i = 0;
        try {
            Transaction transaction = createObservingTransaction();

            for (i = 0; i < 1500 ; i++) {
                clearInvocations(transactionCallbackMock);

                tick(OBSERVABLE_UPDATE_PERIOD_MS);
                tick(OBSERVABLE_UPDATE_PERIOD_MS);
                tick(1);

                verify(transactionCallbackMock, atMost(3))
                        .onTransactionResponse(eq(mExpectedLocalEndpoint), any());
                verify(transactionCallbackMock, atLeast(2))
                        .onTransactionResponse(eq(mExpectedLocalEndpoint), any());
                verify(transactionCallbackMock, never()).onTransactionFinished();
                verify(transactionCallbackMock, never()).onTransactionException(null);
            }

            clearInvocations(transactionCallbackMock);

            Message response = transaction.getResponse();

            // Verify that everything is as we expect it.
            assertTrue(response.getOptionSet().hasObserve());
            assertTrue(2*i <= response.getOptionSet().getObserve());
            assertEquals(1, mChild.onGetObservable().getObserverCount());
        }
        catch (Throwable t) {
            LOGGER.info("i = " + i);
            dumpLogs();
            throw t;
        }
    }

    @Test
    void explicitObservationCancellation() throws Exception {
        Transaction transaction = createObservingTransaction();

        tick(1);

        transaction.cancel();

        tick(1);

        verify(transactionCallbackMock, times(1)).onTransactionCancelled();
        verify(transactionCallbackMock, times(1)).onTransactionFinished();

        assertTrue(transaction.isCancelled());
        assertFalse(transaction.isActive());

        tick(OBSERVABLE_UPDATE_PERIOD_MS*2);

        verify(transactionCallbackMock, never()).onTransactionResponse(any(), any());

        // Verify that the observation was canceled server-side
        assertEquals(0, mChild.onGetObservable().getObserverCount());
    }

    @Test
    void implicitObservationCancellation() throws Exception {
        Transaction transaction = createObservingTransaction();

        tick(1);

        transaction.cancelWithoutUnobserve();

        tick(1);

        verify(transactionCallbackMock, times(1)).onTransactionCancelled();
        verify(transactionCallbackMock, times(1)).onTransactionFinished();

        assertTrue(transaction.isCancelled());
        assertFalse(transaction.isActive());

        tick(OBSERVABLE_UPDATE_PERIOD_MS);
        tick(OBSERVABLE_UPDATE_PERIOD_MS);

        verify(transactionCallbackMock, never()).onTransactionResponse(any(), any());

        // Verify that the observation was canceled server-side
        assertEquals(0, mChild.onGetObservable().getObserverCount());
    }
}
