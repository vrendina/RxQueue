/*
 * Copyright (c) 2018 Victor Rendina
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.victorrendina.rxqueue2;

import io.reactivex.Observer;
import io.reactivex.functions.Consumer;
import io.reactivex.plugins.RxJavaPlugins;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.mockito.Mockito.mock;

class TestHelper {

    /**
     * Mocks an Observer with the proper receiver type.
     *
     * @param <T> the value type
     * @return the mocked observer
     */
    @SuppressWarnings("unchecked")
    static <T> Observer<T> mockObserver() {
        return mock(Observer.class);
    }

    /**
     * Mocks a Consumer with the proper receiver type.
     *
     * @param <T> the value type
     * @return the mocked consumer
     */
    @SuppressWarnings("unchecked")
    static <T> Consumer<T> mockConsumer() {
        return mock(Consumer.class);
    }

    /**
     * Simulate a race condition by queueing up a list of on separate threads and then starting
     * them all simultaneously by releasing a latch.
     *
     * @param runnables varargs runnables to execute simultaneously
     */
    static void race(Runnable... runnables) {
        final ExecutorService executor = Executors.newFixedThreadPool(runnables.length);
        final CountDownLatch latch = new CountDownLatch(1);

        for (Runnable runnable : runnables) {
            executor.submit(awaitRunnable(runnable, latch));
        }
        latch.countDown();
    }

    /**
     * Convenience method to wrap a runnable with a latch await method.
     *
     * @param runnable runnable to execute after latch done waiting
     * @param latch    latch to await
     * @return runnable that will wait for latch
     */
    private static Runnable awaitRunnable(final Runnable runnable, final CountDownLatch latch) {
        return new Runnable() {
            @Override
            public void run() {
                try {
                    latch.await();
                    runnable.run();
                } catch (Exception e) {
                    throw new AssertionError(e);
                }
            }
        };
    }

    /**
     * Provide RxJava with an error handler to track undeliverable exceptions.
     *
     * @return list of errors that the handler caught
     */
    public static List<Throwable> trackPluginErrors() {
        final List<Throwable> list = Collections.synchronizedList(new ArrayList<Throwable>());

        RxJavaPlugins.setErrorHandler(new Consumer<Throwable>() {
            @Override
            public void accept(Throwable t) {
                list.add(t);
            }
        });

        return list;
    }

}
