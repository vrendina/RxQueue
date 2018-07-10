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
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.concurrent.CountDownLatch;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class QueueRelayTest {

    @Test
    public void testSubscriberReceivesQueuedValuesAndSubsequentEvents() {
        QueueRelay<String> relay = QueueRelay.create();

        relay.accept("one");
        relay.accept("two");

        Observer<String> observer = TestHelper.mockObserver();
        relay.subscribe(observer);

        relay.accept("three");

        verify(observer, times(1)).onNext("one");
        verify(observer, times(1)).onNext("two");
        verify(observer, times(1)).onNext("three");
        verify(observer, Mockito.never()).onError(any(Throwable.class));
        verify(observer, Mockito.never()).onComplete();

        assertTrue(relay.hasObservers());
    }

    @Test
    public void testSubscriberReceivesDefaultAndQueuedValuesAndSubsequentEvents() {
        QueueRelay<String> relay = QueueRelay.createDefault("default");

        relay.accept("one");
        relay.accept("two");

        Observer<String> observer = TestHelper.mockObserver();
        relay.subscribe(observer);

        relay.accept("three");

        verify(observer, times(1)).onNext("default");
        verify(observer, times(1)).onNext("one");
        verify(observer, times(1)).onNext("two");
        verify(observer, times(1)).onNext("three");
        verify(observer, Mockito.never()).onError(any(Throwable.class));
        verify(observer, Mockito.never()).onComplete();

        assertTrue(relay.hasObservers());
    }

    @Test
    public void testTakeOneSubscriber() {
        QueueRelay<Integer> relay = QueueRelay.createDefault(1);
        final Observer<Object> observer = TestHelper.mockObserver();

        relay.take(1).subscribe(observer);

        verify(observer, times(1)).onNext(1);
        verify(observer).onComplete();
        verify(observer, never()).onError(any(Throwable.class));

        assertFalse(relay.hasObservers());
    }

    @Test
    public void disposedSubscriberQueueContinuesToFill() throws Exception {
        QueueRelay<String> relay = QueueRelay.create();

        relay.accept("one");
        relay.accept("two");

        Consumer<String> consumer = TestHelper.mockConsumer();
        Disposable disposable = relay.subscribe(consumer);

        assertTrue(relay.hasObservers());

        disposable.dispose();

        relay.accept("three");

        verify(consumer, times(2)).accept(Mockito.anyString());
        assertFalse(relay.hasObservers());

        Observer<String> observer = TestHelper.mockObserver();
        relay.subscribe(observer);

        verify(observer, times(1)).onNext("three");
        assertTrue(relay.hasObservers());
    }

    @Test
    public void dataHandedOffWhenSubscriberChanges() {
        final int items = 50;
        final QueueRelay<Integer> relay = QueueRelay.create();
        final CountDownLatch latch = new CountDownLatch(items);

        final Thread emitter = new Thread() {
            @Override
            public void run() {
                for (int i = 0; i < items; i++) {
                    relay.accept(i);
                    try {
                        Thread.sleep(5);
                    } catch (Exception e) {
                        fail();
                    }
                }
            }
        };
        emitter.start();

        for (int i = 0; i < items; i++) {
            relay.observeOn(Schedulers.computation())
                    .subscribe(new Consumer<Integer>() {
                        @Override
                        public void accept(Integer s) {
                            latch.countDown();
                        }
                    });

            try {
                Thread.sleep(1);
            } catch (Exception e) {
                fail();
            }
        }

        try {
            latch.await();
            assertEquals(0, latch.getCount());
        } catch (Exception e) {
            fail();
        }
    }

    @Test
    public void innerDisposedWhenAnotherSubscribes() {
        final QueueRelay<Integer> relay = QueueRelay.create();
        final CountDownLatch firstLatch = new CountDownLatch(1);
        final CountDownLatch secondLatch = new CountDownLatch(1);
        final CountDownLatch thirdLatch = new CountDownLatch(1);

        Thread first = new Thread() {
            @Override
            public void run() {
                TestObserver observer = new TestObserver();
                relay.subscribe(observer);
                assertFalse(observer.d.isDisposed());
                firstLatch.countDown();
                try {
                    secondLatch.await();
                    assertTrue(observer.d.isDisposed());
                    thirdLatch.countDown();
                } catch (Exception e) {
                    fail();
                }
            }
        };

        first.start();

        try {
            firstLatch.await();
            relay.subscribe();
            secondLatch.countDown();
            thirdLatch.await();
        } catch (Exception e) {
            fail();
        }
    }

    @Test
    public void onNextNullThrowsException() {
        final QueueRelay<Object> s = QueueRelay.create();

        try {
            s.accept(null);
            fail();
        } catch (NullPointerException e) {
            assertEquals("value == null", e.getMessage());
        }
    }

    @Test
    public void nullConstructorArgumentThrowsException() {
        try {
            QueueRelay.createDefault(1, 2, null, 4);
        } catch (NullPointerException e) {
            assertEquals("item == null", e.getMessage());
        }
    }

}
