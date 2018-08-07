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
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class QueueSubjectTest {

    @Test
    public void testSubscriberReceivesQueuedValuesAndSubsequentEvents() {
        QueueSubject<String> subject = QueueSubject.create();

        subject.onNext("one");
        subject.onNext("two");

        Observer<String> observer = TestHelper.mockObserver();
        subject.subscribe(observer);

        subject.onNext("three");

        verify(observer, times(1)).onNext("one");
        verify(observer, times(1)).onNext("two");
        verify(observer, times(1)).onNext("three");
        verify(observer, never()).onError(any(Throwable.class));
        verify(observer, never()).onComplete();

        assertTrue(subject.hasObservers());
    }

    @Test
    public void testSubscriberReceivesDefaultAndQueuedValuesAndSubsequentEvents() {
        QueueSubject<String> subject = QueueSubject.createDefault("default");

        subject.onNext("one");
        subject.onNext("two");

        Observer<String> observer = TestHelper.mockObserver();
        subject.subscribe(observer);

        subject.onNext("three");

        verify(observer, times(1)).onNext("default");
        verify(observer, times(1)).onNext("one");
        verify(observer, times(1)).onNext("two");
        verify(observer, times(1)).onNext("three");
        verify(observer, never()).onError(any(Throwable.class));
        verify(observer, never()).onComplete();

        assertTrue(subject.hasObservers());
    }

    @Test
    public void testSubscribeThenOnComplete() {
        QueueSubject<String> subject = QueueSubject.createDefault("default");

        Observer<String> observer = TestHelper.mockObserver();
        subject.subscribe(observer);

        subject.onNext("one");
        subject.onComplete();

        verify(observer, times(1)).onNext("default");
        verify(observer, times(1)).onNext("one");
        verify(observer, never()).onError(any(Throwable.class));
        verify(observer, times(1)).onComplete();
    }

    @Test
    public void testTakeOneSubscriber() {
        QueueSubject<Integer> subject = QueueSubject.createDefault(1);
        final Observer<Object> observer = TestHelper.mockObserver();

        subject.take(1).subscribe(observer);

        verify(observer, times(1)).onNext(1);
        verify(observer).onComplete();
        verify(observer, never()).onError(any(Throwable.class));

        assertFalse(subject.hasObservers());
    }

    @Test
    public void disposedSubscriberQueueContinuesToFill() throws Exception {
        QueueSubject<String> subject = QueueSubject.create();

        subject.onNext("one");
        subject.onNext("two");

        Consumer<String> consumer = TestHelper.mockConsumer();
        Disposable disposable = subject.subscribe(consumer);

        assertTrue(subject.hasObservers());

        disposable.dispose();

        subject.onNext("three");

        verify(consumer, times(2)).accept(Mockito.anyString());
        assertFalse(subject.hasObservers());

        Observer<String> observer = TestHelper.mockObserver();
        subject.subscribe(observer);

        verify(observer, times(1)).onNext("three");
        assertTrue(subject.hasObservers());
    }

    @Test
    public void testSubscribeToCompletedOnlyEmitsOnComplete() {
        QueueSubject<String> subject = QueueSubject.createDefault("default");
        subject.onNext("one");
        subject.onComplete();

        Observer<String> observer = TestHelper.mockObserver();
        subject.subscribe(observer);

        verify(observer, never()).onNext("default");
        verify(observer, never()).onNext("one");
        verify(observer, never()).onError(any(Throwable.class));
        verify(observer, times(1)).onComplete();
    }

    @Test
    public void itemsAreEmittedSequentiallyWhenChangingSubscribers() {
        for (int i = 0; i < 25; i++) {
            final QueueSubject<Integer> subject = QueueSubject.create();

            int items = 50;
            for (int j = 0; j < items; j++) {
                subject.onNext(j);
            }

            final CountDownLatch latch = new CountDownLatch(items);
            final ConcurrentLinkedQueue<Integer> emissions = new ConcurrentLinkedQueue<Integer>();

            final Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    subject.subscribe(new Consumer<Integer>() {
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
            final QueueSubject<Integer> subject = QueueSubject.create();

            int items = 50;
            for (int j = 0; j < items; j++) {
                subject.onNext(j);
            }

            final CountDownLatch latch = new CountDownLatch(items);
            final AtomicInteger count = new AtomicInteger(0);
            final Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    subject.subscribe(new Consumer<Integer>() {
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
    public void testSubscribeToErrorOnlyEmitsOnError() {
        QueueSubject<String> subject = QueueSubject.createDefault("default");
        subject.onNext("one");
        RuntimeException re = new RuntimeException("test error");
        subject.onError(re);

        Observer<String> observer = TestHelper.mockObserver();
        subject.subscribe(observer);

        verify(observer, never()).onNext("default");
        verify(observer, never()).onNext("one");
        verify(observer, times(1)).onError(re);
        verify(observer, never()).onComplete();
    }

    @Test
    public void testCompletedAfterErrorIsNotSent() {
        QueueSubject<String> subject = QueueSubject.createDefault("default");

        Throwable testException = new Throwable();

        Observer<String> observer = TestHelper.mockObserver();
        subject.subscribe(observer);

        subject.onNext("one");
        subject.onError(testException);
        subject.onNext("two");
        subject.onComplete();

        verify(observer, times(1)).onNext("default");
        verify(observer, times(1)).onNext("one");
        verify(observer, times(1)).onError(testException);
        verify(observer, never()).onNext("two");
        verify(observer, never()).onComplete();
    }

    @Test
    public void testUnsubscriptionCase() {
        QueueSubject<String> subject = QueueSubject.create();

        for (int i = 0; i < 10; i++) {
            final Observer<Object> o = TestHelper.mockObserver();
            InOrder inOrder = inOrder(o);
            String v = "" + i;
            subject.onNext(v);
            subject.firstElement()
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
    public void testStartEmpty() {
        QueueSubject<Integer> subject = QueueSubject.create();
        final Observer<Object> o = TestHelper.mockObserver();
        InOrder inOrder = inOrder(o);

        subject.subscribe(o);

        inOrder.verify(o, never()).onNext(any());
        inOrder.verify(o, never()).onComplete();

        subject.onNext(1);

        subject.onComplete();

        subject.onNext(2);

        verify(o, never()).onError(any(Throwable.class));

        inOrder.verify(o).onNext(1);
        inOrder.verify(o).onComplete();
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testCurrentStateMethodsNormalEmptyStart() {
        QueueSubject<Object> subject = QueueSubject.create();

        assertFalse(subject.hasThrowable());
        assertFalse(subject.hasComplete());
        assertNull(subject.getThrowable());

        subject.onNext(1);

        assertFalse(subject.hasThrowable());
        assertFalse(subject.hasComplete());
        assertNull(subject.getThrowable());

        subject.onComplete();

        assertFalse(subject.hasThrowable());
        assertTrue(subject.hasComplete());
        assertNull(subject.getThrowable());
    }

    @Test
    public void testCurrentStateMethodsNormalSomeStart() {
        QueueSubject<Object> subject = QueueSubject.createDefault((Object) 1);

        assertFalse(subject.hasThrowable());
        assertFalse(subject.hasComplete());
        assertNull(subject.getThrowable());

        subject.onNext(2);

        assertFalse(subject.hasThrowable());
        assertFalse(subject.hasComplete());
        assertNull(subject.getThrowable());

        subject.onComplete();
        assertFalse(subject.hasThrowable());
        assertTrue(subject.hasComplete());
        assertNull(subject.getThrowable());
    }

    @Test
    public void testCurrentStateMethodsEmpty() {
        QueueSubject<Object> subject = QueueSubject.create();

        assertFalse(subject.hasThrowable());
        assertFalse(subject.hasComplete());
        assertNull(subject.getThrowable());

        subject.onComplete();

        assertFalse(subject.hasThrowable());
        assertTrue(subject.hasComplete());
        assertNull(subject.getThrowable());
    }

    @Test
    public void innerDisposedWhenAnotherSubscribes() {
        final QueueSubject<Integer> subject = QueueSubject.create();
        TestDisposableObserver o1 = new TestDisposableObserver();
        TestDisposableObserver o2 = new TestDisposableObserver();

        subject.subscribe(o1);
        assertFalse(o1.d.isDisposed());

        subject.subscribe(o2);
        assertTrue(o1.d.isDisposed());
        assertFalse(o2.d.isDisposed());
    }

    @Test
    public void innerDisposed() {
        QueueSubject.create()
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
    public void testCurrentStateMethodsError() {
        QueueSubject<Object> subject = QueueSubject.create();

        assertFalse(subject.hasThrowable());
        assertFalse(subject.hasComplete());
        assertNull(subject.getThrowable());

        subject.onError(new TestException());

        assertTrue(subject.hasThrowable());
        assertFalse(subject.hasComplete());
        assertTrue(subject.getThrowable() instanceof TestException);
    }

    @Test
    public void onNextNullCallsOnError() {
        final QueueSubject<Object> subject = QueueSubject.create();

        subject.onNext(null);
        assertTrue(subject.hasThrowable());
        assertTrue(subject.getThrowable() instanceof NullPointerException);
        assertEquals("value == null", subject.getThrowable().getMessage());
    }

    @Test
    public void nullConstructorArgumentThrowsException() {
        try {
            QueueSubject.createDefault(1, 2, null, 4);
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
            final QueueSubject<Integer> subject = QueueSubject.create();

            final CountDownLatch latch = new CountDownLatch(concurrent);
            final Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    for (int j = 0; j < items; j++) {
                        subject.onNext(j);
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
            subject.subscribe(observer);

            verify(observer, times(totalItems)).onNext(anyInt());
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    public void subscribeOnNextRace() {
        for (int i = 0; i < 25; i++) {
            final QueueSubject<Integer> subject = QueueSubject.createDefault(1);
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
                    subject.onNext(2);
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
