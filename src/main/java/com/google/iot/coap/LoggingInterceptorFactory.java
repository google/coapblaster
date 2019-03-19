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

import java.io.PrintStream;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.checkerframework.checker.nullness.qual.Nullable;

/** {@hide} */
public class LoggingInterceptorFactory {
    private long mStartTime = 0;
    private PrintStream mPrintStream = null;
    private final List<LoggingInterceptor> mInterceptors = new LinkedList<>();

    static class Entry {
        long mTimestamp;
        Message mMessage;

        Entry(long ts, Message msg) {
            mTimestamp = ts;
            mMessage = msg;
        }

        @Override
        public String toString() {
            return String.format("%08d: %s", TimeUnit.NANOSECONDS.toMillis(mTimestamp), mMessage);
        }
    }

    public interface NanoTimeGetter {
        long nanoTime();
    }

    private NanoTimeGetter mNanoTimeGetter = System::nanoTime;

    long nanoTime() {
        return mNanoTimeGetter.nanoTime();
    }

    public void setNanoTimeGetter(NanoTimeGetter getter) {
        mNanoTimeGetter = getter;
    }

    class LoggingInterceptor implements Interceptor {
        private final List<Entry> mLog = new LinkedList<>();
        private final String mTag;

        public LoggingInterceptor(@Nullable String tag) {
            mTag = tag;
        }

        @Override
        public boolean onInterceptOutbound(Message message) {
            long ts = nanoTime();

            if (mStartTime == 0) {
                mStartTime = ts;
            }

            Entry entry = new Entry(ts - mStartTime, message.copy());

            synchronized (mLog) {
                mLog.add(entry);
            }

            if (mPrintStream != null) {
                if (mTag != null) {
                    mPrintStream.println(mTag + ": " + entry);
                } else {
                    mPrintStream.println(entry);
                }
            }

            return false;
        }

        @Override
        public boolean onInterceptInbound(Message message) {
            return onInterceptOutbound(message);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();

            for (Entry entry : mLog) {
                if (mTag != null) {
                    sb.append(mTag);
                    sb.append(": ");
                }
                sb.append(entry.toString());
                sb.append("\n");
            }

            if (mTag != null) {
                sb.append(mTag);
                sb.append(": ");
            }
            sb.append(
                    String.format(
                            "%08d: END\n",
                            TimeUnit.NANOSECONDS.toMillis(nanoTime() - mStartTime)));

            return sb.toString();
        }
    }

    public LoggingInterceptorFactory() {}

    public void setPrintStream(PrintStream printStream) {
        mPrintStream = printStream;
    }

    public Interceptor create(String tag) {
        LoggingInterceptor ret = new LoggingInterceptor(tag);
        mInterceptors.add(ret);
        return ret;
    }

    public Interceptor create() {
        LoggingInterceptor ret = new LoggingInterceptor(null);
        mInterceptors.add(ret);
        return ret;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        for (LoggingInterceptor interceptor : mInterceptors) {
            sb.append(interceptor);
        }

        return sb.toString();
    }
}
