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
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("ConstantConditions")
class FakeExecutorTestBase extends ExecutorTestBase {
    private static final boolean DEBUG = false;
    private static final Logger LOGGER =
            Logger.getLogger(ExecutorTestBase.class.getCanonicalName());

    public ScheduledExecutorService createNewScheduledExecutorService() {
        return new FakeScheduledExecutorService() {
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

    public void tick(int durationInMs) throws Exception {
        if (mOriginalExecutor instanceof FakeScheduledExecutorService) {
            if (DEBUG) LOGGER.info("tick(" + durationInMs + ") ENTER");
            ((FakeScheduledExecutorService) mOriginalExecutor).tick(durationInMs);
            if (DEBUG) LOGGER.info("tick(" + durationInMs + ") EXIT");
        } else {
            super.tick(durationInMs);
        }
    }
}
