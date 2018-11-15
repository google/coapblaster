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

import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;

@SuppressWarnings("ConstantConditions")
class BlockTest {
    private static final boolean DEBUG = false;
    private static final Logger LOGGER =
            Logger.getLogger(ClientServerTest.class.getCanonicalName());

    ScheduledExecutorService mExecutor = null;
    volatile Throwable mThrowable = null;

    private LocalEndpointManager mContext = null;
    private Server mServer = null;
    private Client mClient = null;
    private Resource<InboundRequestHandler> mRoot = null;

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
        mRoot = new Resource<>();
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

    final String mLotsOfJunk =
            "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Vivamus vel odio vitae "
                    + "leo pellentesque finibus vitae in enim. Class aptent taciti sociosqu ad litora torquent "
                    + "per conubia nostra, per inceptos himenaeos. Integer leo ex, dignissim efficitur lorem ut, "
                    + "elementum gravida nibh. Integer leo magna, sollicitudin nec vestibulum vel, malesuada sit "
                    + "amet nibh. Nunc lobortis, lectus vitae ornare sagittis, lorem enim imperdiet nisl, non aliquam "
                    + "enim augue ut nisi. Nullam feugiat tristique tempus. Duis maximus id purus ultricies posuere. "
                    + "Quisque rutrum rutrum magna id sodales. Integer suscipit, mi ut aliquam tempor, sem urna maximus "
                    + "massa, in facilisis turpis purus vitae elit. Quisque lorem lectus, imperdiet eget tincidunt nec, "
                    + "ultrices eget orci.\n"
                    + "\n"
                    + "Donec ipsum eros, aliquet vel consequat a, gravida nec felis. Suspendisse mollis mauris sit amet "
                    + "sapien euismod, eget laoreet diam placerat. Morbi in tellus vitae leo mollis mattis. Quisque "
                    + "ullamcorper magna non efficitur consequat. Sed quam arcu, vehicula vitae sollicitudin id, "
                    + "lacinia id tellus. In viverra, risus scelerisque fermentum tincidunt, felis lorem commodo "
                    + "turpis, ut posuere sem felis eget risus. Aliquam in aliquam lacus. Suspendisse ornare dictum "
                    + "elit in consequat. In hac habitasse platea dictumst. Nam dictum imperdiet eros, id rutrum "
                    + "orci venenatis nec. Duis maximus sodales blandit. Aliquam iaculis sodales leo sit amet "
                    + "placerat.\n"
                    + "\n"
                    + "Nunc eget suscipit massa, quis fermentum dui. Nulla efficitur enim mauris, nec tempus magna "
                    + "tempor eu. Duis sollicitudin lacus sed ante consectetur luctus. Morbi interdum ac quam quis "
                    + "pharetra. Integer dictum nunc metus, at rutrum quam vehicula id. In hac habitasse platea "
                    + "dictumst. Donec auctor, nibh sit amet commodo porttitor, libero nunc hendrerit ipsum, a "
                    + "ultrices magna massa a arcu.";

    class LoremIpsum implements InboundRequestHandler {

        @Override
        public void onInboundRequest(InboundRequest inboundRequest) {
            inboundRequest.sendSimpleResponse(Code.RESPONSE_CONTENT, mLotsOfJunk);
        }

        @Override
        public void onInboundRequestCheck(InboundRequest inboundRequest) {}
    }

    @Test
    void blockTest() throws Exception {

        mRoot.addChild("LoremIpsum", new LoremIpsum());

        Transaction transaction = mClient.newRequestBuilder().changePath("LoremIpsum").send();

        tick(1);

        Message response = transaction.getResponse(1500);

        if (DEBUG) LOGGER.info("Got response: " + response);

        assertEquals(mLotsOfJunk, response.getPayloadAsString());
        assertEquals(Code.toString(Code.RESPONSE_CONTENT), Code.toString(response.getCode()));
    }
}
