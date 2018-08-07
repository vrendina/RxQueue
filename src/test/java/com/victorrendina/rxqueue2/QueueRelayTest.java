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

import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.reactivex.observers.DefaultObserver;
import io.reactivex.observers.TestObserver;
import io.reactivex.schedulers.Schedulers;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

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
    public void itemsAreEmittedSequentiallyWhenChangingSubscribers() {
        for (int i = 0; i < 25; i++) {
            final QueueRelay<Integer> relay = QueueRelay.create();

            int items = 50;
            for (int j = 0; j < items; j++) {
                relay.accept(j);
            }

            final CountDownLatch latch = new CountDownLatch(items);
            final ConcurrentLinkedQueue<Integer> emissions = new ConcurrentLinkedQueue<Integer>();

            final Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    relay.subscribe(new Consumer<Integer>() {
                        @Override
                        public void accept(Integer integer) {
                            emissions.add(integer);
                            latch.countDown();
                        }
                    });
                }
            };

            TestHelper.race(runnable, runnable, runnable, runnable);

            try {
                latch.await();
            } catch (Exception e) {
                fail();
            }

            // Ensure that all items were emitted sequentially
            int lastItem = -1;
            while (true) {
                Integer item = emissions.poll();
                if (item == null) {
                    break;
                }
                assertTrue(item > lastItem);
                assertEquals(1, item - lastItem);
                lastItem = item;
            }
        }
    }

    @Test
    public void noItemsAreLostWhenChangingSubscribers() {
        for (int i = 0; i < 25; i++) {
            final QueueRelay<Integer> relay = QueueRelay.create();

            int items = 50;
            for (int j = 0; j < items; j++) {
                relay.accept(j);
            }

            final CountDownLatch latch = new CountDownLatch(items);
            final AtomicInteger count = new AtomicInteger(0);
            final Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    relay.subscribe(new Consumer<Integer>() {
                        @Override
                        public void accept(Integer integer) {
                            count.getAndIncrement();
                            latch.countDown();
                        }
                    });
                }
            };

            TestHelper.race(runnable, runnable, runnable, runnable);

            try {
                latch.await();
            } catch (Exception e) {
                fail();
            }

            assertEquals(items, count.get());
        }
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
        TestDisposableObserver o1 = new TestDisposableObserver();
        TestDisposableObserver o2 = new TestDisposableObserver();

        relay.subscribe(o1);
        assertFalse(o1.d.isDisposed());

        relay.subscribe(o2);
        assertTrue(o1.d.isDisposed());
        assertFalse(o2.d.isDisposed());
    }

    @Test
    public void innerDisposed() {
        QueueRelay.create()
                .subscribe(new Observer<Object>() {
                    @Override
                    public void onSubscribe(Disposable d) {
                        assertFalse(d.isDisposed());
                        d.dispose();
                        assertTrue(d.isDisposed());
                    }

                    @Override
                    public void onNext(Object value) {

                    }

                    @Override
                    public void onError(Throwable e) {

                    }

                    @Override
                    public void onComplete() {

                    }
                });
    }

    @Test
    public void testUnsubscriptionCase() {
        QueueRelay<String> relay = QueueRelay.create();

        for (int i = 0; i < 10; i++) {
            final Observer<Object> o = TestHelper.mockObserver();
            InOrder inOrder = inOrder(o);
            String v = "" + i;
            relay.accept(v);
            relay.firstElement()
                    .toObservable()
                    .flatMap(new Function<String, Observable<String>>() {
                        @Override
                        public Observable<String> apply(String t1) {
                            return Observable.just(t1 + ", " + t1);
                        }
                    })
                    .subscribe(new DefaultObserver<String>() {
                        @Override
                        public void onNext(String t) {
                            o.onNext(t);
                        }

                        @Override
                        public void onError(Throwable e) {
                            o.onError(e);
                        }

                        @Override
                        public void onComplete() {
                            o.onComplete();
                        }
                    });
            inOrder.verify(o).onNext(v + ", " + v);
            inOrder.verify(o).onComplete();
            verify(o, never()).onError(any(Throwable.class));
        }
    }


    @Test
    public void onNextNullThrowsException() {
        final QueueRelay<Object> relay = QueueRelay.create();

        try {
            relay.accept(null);
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

    @Test
    public void queueContainsRightCountWhenWrittenFromMultipleThreads() {
        final int items = 50;
        final int concurrent = 5;
        final int totalItems = concurrent * items;

        for (int i = 0; i < 10; i++) {
            final QueueRelay<Integer> relay = QueueRelay.create();

            final CountDownLatch latch = new CountDownLatch(concurrent);
            final Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    for (int j = 0; j < items; j++) {
                        relay.accept(j);
                    }
                    latch.countDown();
                }
            };

            TestHelper.race(runnable, runnable, runnable, runnable, runnable);

            try {
                latch.await();
            } catch (Exception e) {
                fail();
            }

            final Observer<Object> observer = TestHelper.mockObserver();
            relay.subscribe(observer);

            verify(observer, times(totalItems)).onNext(anyInt());
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    public void subscribeOnNextRace() {
        for (int i = 0; i < 25; i++) {
            final QueueRelay<Integer> subject = QueueRelay.createDefault(1);
            final TestObserver[] to = {null};

            final CountDownLatch latch = new CountDownLatch(2);

            Runnable r1 = new Runnable() {
                @Override
                public void run() {
                    to[0] = subject.test();
                    latch.countDown();
                }
            };

            Runnable r2 = new Runnable() {
                @Override
                public void run() {
                    subject.accept(2);
                    latch.countDown();
                }
            };

            TestHelper.race(r1, r2);

            try {
                latch.await();
            } catch (Exception e) {
                fail();
            }

            if (to[0].valueCount() == 1) {
                to[0].assertValue(2).assertNoErrors().assertNotComplete();
            } else {
                to[0].assertValues(1, 2).assertNoErrors().assertNotComplete();
            }
        }
    }
}
