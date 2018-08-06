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
import io.reactivex.plugins.RxJavaPlugins;
import io.reactivex.subjects.Subject;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Subject that has a maximum of one simultaneous observer and queues items to be emitted if no observer is subscribed.
 * Once a subscriber is connected all items in the queue will be drained to that subscriber unless the subscriber is
 * disposed or another subscriber takes its place. Adding a subscriber while a subscription is active will dispose of
 * the first subscriber and any existing events in the queue will be forwarded to the second subscriber.
 * <p>
 * Example usage:
 * <p>
 * <pre> {@code
 *
 * // observer will receive all events
 * QueueSubject<Object> subject = QueueSubject.createDefault("initial");
 * subject.onNext("one");
 * subject.onNext("two");
 * subject.subscribe(observer);
 * subject.onNext("three");
 *
 * // first observer will receive initial event, second observer all subsequent events
 * QueueSubject<Object> subject = QueueSubject.createDefault("initial");
 * subject.subscribe(firstObserver);
 * subject.subscribe(secondObserver);
 * subject.onNext("one");
 * subject.onNext("two");
 *
 * } </pre>
 *
 * @param <T> the type of item expected to be observed
 */
public class QueueSubject<T> extends Subject<T> {

    private final Queue<T> queue = new ConcurrentLinkedQueue<>();
    private final AtomicReference<QueueDisposable<T>> subscriber = new AtomicReference<>();

    private final Object lock = new Object();

    private Throwable error;
    private volatile boolean done;

    /**
     * Creates a {@link QueueSubject} without any initial items.
     *
     * @param <T> type of items emitted by the relay
     * @return the constructed {@link QueueSubject}
     */
    public static <T> QueueSubject<T> create() {
        return new QueueSubject<>();
    }

    /**
     * Creates a {@link QueueSubject} with the given initial items.
     *
     * @param initialItems varargs initial items in the queue
     * @param <T>          type of items emitted by the relay
     * @return the constructed {@link QueueSubject}
     */
    @SafeVarargs
    public static <T> QueueSubject<T> createDefault(T... initialItems) {
        return new QueueSubject<>(initialItems);
    }

    @SafeVarargs
    private QueueSubject(T... initialItems) {
        for (T item : initialItems) {
            if (item == null) throw new NullPointerException("item == null");
            queue.offer(item);
        }
    }

    @Override
    public boolean hasObservers() {
        return subscriber.get() != null;
    }

    @Override
    public boolean hasThrowable() {
        return done && error != null;
    }

    @Override
    public boolean hasComplete() {
        return done && error == null;
    }

    @Override
    public Throwable getThrowable() {
        if (done) {
            return error;
        }
        return null;
    }

    @Override
    protected void subscribeActual(Observer<? super T> observer) {
        QueueDisposable<T> qs = new QueueDisposable<>(observer, this);
        observer.onSubscribe(qs);
        if (!done) {
            set(qs);
            qs.drain(queue);
        } else {
            Throwable ex = error;
            if (ex != null) {
                qs.onError(ex);
            } else {
                qs.onComplete();
            }
        }
    }

    @Override
    public void onSubscribe(Disposable d) {
        if (done) {
            d.dispose();
        }
    }

    @Override
    public void onNext(T value) {
        if (value == null) {
            onError(new NullPointerException("value == null"));
            return;
        }
        if (done) {
            return;
        }

        queue.add(value);

        QueueDisposable<T> qs = subscriber.get();
        if (qs != null && !qs.isDisposed()) {
            qs.drain(queue);
        }
    }

    @Override
    public void onError(Throwable e) {
        if (done) {
            RxJavaPlugins.onError(e);
            return;
        }

        if (e == null) {
            e = new NullPointerException("onError called with null. Null values are generally not allowed in 2.x operators and sources.");
        }

        error = e;
        done = true;

        QueueDisposable<T> qs = subscriber.get();
        if (qs != null) {
            qs.drain(queue);
            qs.onError(e);
        }
    }

    @Override
    public void onComplete() {
        if (done) {
            return;
        }
        done = true;

        QueueDisposable<T> qs = subscriber.get();
        if (qs != null) {
            qs.drain(queue);
            qs.onComplete();
        }
    }

    private void set(QueueDisposable<T> qs) {
        QueueDisposable<T> current = subscriber.get();
        if (current != null) {
            current.dispose();
        }
        subscriber.set(qs);
    }

    private void remove() {
        QueueDisposable<T> current = subscriber.get();
        if (current != null && current.cancelled.get()) {
            subscriber.compareAndSet(current, null);
        }
    }

    static final class QueueDisposable<T> extends AtomicInteger implements Disposable {

        final Observer<? super T> actual;
        final QueueSubject<T> state;

        final AtomicBoolean cancelled = new AtomicBoolean();

        QueueDisposable(Observer<? super T> actual, QueueSubject<T> state) {
            this.actual = actual;
            this.state = state;
        }

        public void onError(Throwable e) {
            if (cancelled.get()) {
                RxJavaPlugins.onError(e);
            } else {
                actual.onError(e);
            }
        }

        public void onComplete() {
            if (!cancelled.get()) {
                actual.onComplete();
            }
        }

        @Override
        public void dispose() {
            synchronized (state.lock) {
                if (cancelled.compareAndSet(false, true)) {
                    state.remove();
                }
            }
        }

        @Override
        public boolean isDisposed() {
            return cancelled.get();
        }

        void drain(Queue<T> queue) {
            if (getAndIncrement() != 0) {
                return;
            }

            int missed = 1;

            while (!cancelled.get()) {
                for (; ; ) {
                    if (cancelled.get()) {
                        return;
                    }

                    synchronized (state.lock) {
                        if (cancelled.get()) {
                            return;
                        }

                        T item = queue.poll();
                        if (item == null) {
                            break;
                        }
                        actual.onNext(item);
                    }
                }

                missed = addAndGet(-missed);
                if (missed == 0) {
                    break;
                }
            }
        }

    }

}
