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

import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("ConstantConditions")
class ExecutorTestBase {
    private static final boolean DEBUG = false;
    private static final Logger LOGGER =
            Logger.getLogger(ExecutorTestBase.class.getCanonicalName());

    ScheduledExecutorService mOriginalExecutor = null;
    ListeningScheduledExecutorService mExecutor = null;
    volatile Throwable mThrowable = null;

    public void rethrow() {
        if (mExecutor != null) {
            try {
                mExecutor.shutdown();
                if (!mExecutor.awaitTermination(50, TimeUnit.MILLISECONDS)) {
                    List<Runnable> naughtyList = mExecutor.shutdownNow();

                    if (DEBUG) {
                        LOGGER.warning(
                                "Call to awaitTermination() is taking a long time"
                                        + " to finish because of " + naughtyList);
                    }

                    if (mThrowable != null) {
                        mThrowable.printStackTrace();
                    }

                    assertTrue(
                            mExecutor.awaitTermination(10, TimeUnit.SECONDS),
                            "Call to awaitTermination() failed waiting for jobs to finish");
                }
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
            dumpLogs();
            LOGGER.info("Rethrowing throwable: " + x);
            if (x instanceof Error) throw (Error) x;
            if (x instanceof RuntimeException) throw (RuntimeException) x;
            throw new RuntimeException(x);
        }
    }

    public void dumpLogs() {
    }

    public ScheduledExecutorService createNewScheduledExecutorService() {
        return new ScheduledThreadPoolExecutor(4) {
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
    }

    @BeforeEach
    public void before() throws Exception {
        mThrowable = null;
        mOriginalExecutor = createNewScheduledExecutorService();
        mExecutor = MoreExecutors.listeningDecorator(mOriginalExecutor);
    }

    public void tick(int durationInMs) throws Exception {
        if (DEBUG) LOGGER.info("tick(" + durationInMs + ") ENTER");
        Thread.sleep(durationInMs);
        if (DEBUG) LOGGER.info("tick(" + durationInMs + ") EXIT");
    }

    @AfterEach
    public void after() throws Exception {
        rethrow();
    }
}
