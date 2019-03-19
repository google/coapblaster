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

import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("ConstantConditions")
class LoopbackServerClientTestBase extends FakeExecutorTestBase {
    private static final boolean DEBUG = true;
    private static final Logger LOGGER =
            Logger.getLogger(LoopbackServerClientTestBase.class.getCanonicalName());

    LocalEndpointManager mContext = null;
    Server mServer = null;
    Client mClient = null;
    Resource<InboundRequestHandler> mRoot = null;
    LoggingInterceptorFactory mInterceptorFactory = null;

    private boolean mDidDump = false;

    public void dumpLogs() {
        if (!mDidDump) {
            if (DEBUG) {
                LOGGER.warning("Failure Detected Here");
            } else {
                /* We only dump logs here if DEBUG isn't set: because otherwise we are
                 * dumping logs continuously.
                 */
                System.err.println(mInterceptorFactory.toString());
            }

        }
        mDidDump = true;
    }

    @BeforeEach
    public void before() throws Exception {
        super.before();

        mDidDump = false;

        mContext = new LocalEndpointManager(mExecutor);

        BehaviorContext behaviorContext = mContext.getDefaultBehaviorContext();

        behaviorContext =
                new BehaviorContextPassthru(behaviorContext) {
                    @Override
                    public int getMulticastResponseAverageDelayMs() {
                        // Make the multicast response delay predictable.

                        return 0;
                    }
                };

        mContext.setDefaultBehaviorContext(behaviorContext);

        mInterceptorFactory = new LoggingInterceptorFactory();
        if (DEBUG) {
            mInterceptorFactory.setPrintStream(System.err);
        }
        if (mOriginalExecutor instanceof FakeScheduledExecutorService) {
            mInterceptorFactory.setNanoTimeGetter(((FakeScheduledExecutorService)mOriginalExecutor)::nanoTime);
        }
        mContext.setDefaultInterceptor(mInterceptorFactory.create("Context"));

        mClient = new Client(mContext, "loop://localhost/");

        mServer = new Server(mContext);
        mServer.addLocalEndpoint(mContext.getLocalEndpointForScheme("loop"));

        mRoot = new Resource<>();

        mServer.setRequestHandler(mRoot);
        mServer.start();
    }

    @AfterEach
    public void after() throws Exception {
        try {
            mClient.cancelAllTransactions();
            mServer.close();
            mContext.close();
        } finally {
            super.after();
        }
    }
}
