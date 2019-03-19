/*
 * Copyright 2016 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.iot.coap;

import com.google.common.primitives.Ints;
import com.google.common.util.concurrent.SettableFuture;
import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.joda.time.Duration;
import org.joda.time.MutableDateTime;

/**
 * Fake implementation of {@link ScheduledExecutorService} that allows tests control the reference
 * time of the executor and decide when to execute any outstanding task.
 */
@SuppressWarnings("ALL")
public class FakeScheduledExecutorService extends AbstractExecutorService
        implements ScheduledExecutorService {
    private static final boolean DEBUG = false;
    private static final Logger LOGGER =
            Logger.getLogger(FakeScheduledExecutorService.class.getCanonicalName());

    private final AtomicBoolean mShutdown = new AtomicBoolean(false);
    private final PriorityQueue<PendingCallable<?>> mPendingCallables = new PriorityQueue<>();
    private final MutableDateTime mCurrentTime = MutableDateTime.now();
    private final List<Runnable> mPendingCommands = new ArrayList<>();
    final AtomicBoolean mInExecuteMethod = new AtomicBoolean(false);

    public FakeScheduledExecutorService() {
        DateTimeUtils.setCurrentMillisFixed(mCurrentTime.getMillis());
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        return schedulePendingCallable(
                new PendingCallable<>(
                        new Duration(unit.toMillis(delay)), command, PendingCallableType.NORMAL));
    }

    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        return schedulePendingCallable(
                new PendingCallable<>(
                        new Duration(unit.toMillis(delay)), callable, PendingCallableType.NORMAL));
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(
            Runnable command, long initialDelay, long period, TimeUnit unit) {
        return schedulePendingCallable(
                new PendingCallable<>(
                        new Duration(unit.toMillis(initialDelay)),
                        command,
                        PendingCallableType.FIXED_RATE));
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(
            Runnable command, long initialDelay, long delay, TimeUnit unit) {
        return schedulePendingCallable(
                new PendingCallable<>(
                        new Duration(unit.toMillis(initialDelay)),
                        command,
                        PendingCallableType.FIXED_DELAY));
    }

    public void tick(long time, TimeUnit unit) {
        advanceTime(Duration.millis(unit.toMillis(time)));
    }

    public long nanoTime() {
        return TimeUnit.MILLISECONDS.toNanos(mCurrentTime.getMillis());
    }

    /**
     * This will advance the reference time of the executor and execute (in the same thread) any
     * outstanding callable which execution time has passed.
     */
    public void advanceTime(Duration toAdvance) {
        dispatchPendingCommands();
        mCurrentTime.add(toAdvance);

        synchronized (mPendingCallables) {
            while (!mPendingCallables.isEmpty()
                    && mPendingCallables.peek().getScheduledTime().compareTo(mCurrentTime) <= 0) {
                PendingCallable<?> callable = mPendingCallables.poll();
                execute(() -> callable.call());
            }
            if (mShutdown.get() && mPendingCallables.isEmpty()) {
                mPendingCallables.notifyAll();
            }
            if (DEBUG) {
                if (mPendingCallables.isEmpty()) {
                    LOGGER.info("No remaining scheduled commands");
                } else {
                    LOGGER.info("Next scheduled command in "
                            + (mPendingCallables.peek().getScheduledTime().getMillis()- mCurrentTime.getMillis())
                            + "ms");
                }
            }
        }
    }

    public void tick(long durationInMs) {
        advanceTime(Duration.millis(durationInMs));
    }

    @Override
    public void shutdown() {
        if (mShutdown.getAndSet(true)) {
            throw new IllegalStateException("This executor has been shutdown already");
        }
    }

    @Override
    public List<Runnable> shutdownNow() {
        if (mShutdown.getAndSet(true)) {
            throw new IllegalStateException("This executor has been shutdown already");
        }
        List<Runnable> pending = new ArrayList<>();
        for (final PendingCallable<?> pendingCallable : mPendingCallables) {
            pending.add(
                    new Runnable() {
                        @Override
                        public void run() {
                            Object ignore = pendingCallable.call();
                        }
                    });
        }
        synchronized (mPendingCallables) {
            mPendingCallables.notifyAll();
            mPendingCallables.clear();
        }
        return pending;
    }

    @Override
    public boolean isShutdown() {
        return mShutdown.get();
    }

    @Override
    public boolean isTerminated() {
        return mPendingCallables.isEmpty();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) {
        synchronized (mPendingCallables) {
            if (mPendingCallables.isEmpty()) {
                return true;
            }
            tick(timeout, unit);
            return true;
        }
    }

    @Override
    public void execute(Runnable command) {
        if (mShutdown.get()) {
            return;
        }

        synchronized (mPendingCommands) {
            mPendingCommands.add(command);
        }

        if (mInExecuteMethod.compareAndSet(false, true)) {
            try {
                dispatchPendingCommands();
            } finally {
                mInExecuteMethod.set(false);
            }
        }
    }

    private void dispatchPendingCommands() {
        while (!mPendingCommands.isEmpty()) {
            final List<Runnable> commands;

            synchronized (mPendingCommands) {
                commands = new ArrayList<>(mPendingCommands);
                mPendingCommands.clear();
            }

            for (Runnable command : commands) {
                Throwable t = null;
                try {
                    command.run();
                } catch (Throwable x) {
                    t = x;
                }
                afterExecute(command, t);
            }
        }
    }

    protected void afterExecute(Runnable r, @Nullable Throwable t) {
        if (t != null) {
            throw new AssertionError(t);
        }
    }

    <V> ScheduledFuture<V> schedulePendingCallable(PendingCallable<V> callable) {
        if (mShutdown.get()) {
            throw new IllegalStateException("This executor has been shutdown");
        }
        synchronized (mPendingCallables) {
            mPendingCallables.add(callable);
        }
        return callable.getScheduledFuture();
    }

    enum PendingCallableType {
        NORMAL,
        FIXED_RATE,
        FIXED_DELAY
    }

    /** Class that saves the state of an scheduled pending callable. */
    @SuppressWarnings("Convert2Lambda")
    class PendingCallable<T> implements Comparable<PendingCallable<T>> {
        DateTime creationTime = mCurrentTime.toDateTime();
        final Duration delay;
        final Callable<T> pendingCallable;
        final SettableFuture<T> future = SettableFuture.create();
        final AtomicBoolean cancelled = new AtomicBoolean(false);
        final AtomicBoolean done = new AtomicBoolean(false);
        final PendingCallableType type;

        PendingCallable(Duration delay, final Runnable runnable, PendingCallableType type) {
            pendingCallable =
                    new Callable<T>() {
                        @Override
                        public T call() {
                            runnable.run();
                            return null;
                        }
                    };
            this.type = type;
            this.delay = delay;
        }

        PendingCallable(Duration delay, Callable<T> callable, PendingCallableType type) {
            pendingCallable = callable;
            this.type = type;
            this.delay = delay;
        }

        private DateTime getScheduledTime() {
            return creationTime.plus(delay);
        }

        ScheduledFuture<T> getScheduledFuture() {
            return new ScheduledFuture<T>() {
                @Override
                public long getDelay(TimeUnit unit) {
                    return unit.convert(
                            new Duration(mCurrentTime, getScheduledTime()).getMillis(),
                            TimeUnit.MILLISECONDS);
                }

                @Override
                public int compareTo(Delayed o) {
                    return Ints.saturatedCast(
                            getDelay(TimeUnit.MILLISECONDS) - o.getDelay(TimeUnit.MILLISECONDS));
                }

                @Override
                public boolean cancel(boolean mayInterruptIfRunning) {
                    synchronized (this) {
                        cancelled.set(true);
                        return !done.get();
                    }
                }

                @Override
                public boolean isCancelled() {
                    return cancelled.get();
                }

                @Override
                public boolean isDone() {
                    return done.get();
                }

                @Override
                public T get() throws InterruptedException, ExecutionException {
                    return future.get();
                }

                @Override
                public T get(long timeout, TimeUnit unit)
                        throws InterruptedException, ExecutionException, TimeoutException {
                    return future.get(timeout, unit);
                }
            };
        }

        @CanIgnoreReturnValue
        T call() {
            T result = null;
            synchronized (this) {
                if (cancelled.get()) {
                    return null;
                }
                try {
                    result = pendingCallable.call();
                    future.set(result);
                } catch (Exception e) {
                    future.setException(e);
                } finally {
                    Object ignore = null;
                    switch (type) {
                        case NORMAL:
                            done.set(true);
                            break;
                        case FIXED_DELAY:
                            this.creationTime = mCurrentTime.toDateTime();
                            ignore = schedulePendingCallable(this);
                            break;
                        case FIXED_RATE:
                            this.creationTime = this.creationTime.plus(delay);
                            ignore = schedulePendingCallable(this);
                            break;
                    }
                }
            }
            return result;
        }

        @Override
        public int compareTo(PendingCallable<T> other) {
            return getScheduledTime().compareTo(other.getScheduledTime());
        }
    }
}
