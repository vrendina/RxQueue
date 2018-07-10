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

import com.jakewharton.rxrelay2.Relay;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Relay that has a maximum of one simultaneous observer and queues items to be emitted to the next observer
 * if no observers are subscribed. Once a subscriber is connected all items in the queue will be drained
 * to that subscriber unless the subscriber is disposed or another subscriber takes its place. Adding a subscriber
 * while a subscription is active will dispose of the first subscriber and any events in the queue or new events
 * will be forwarded to the second subscriber.
 * <p>
 * Example usage:
 * <p>
 * <pre> {@code

    // observer will receive all events
    QueueRelay<Object> relay = QueueRelay.create("initial")
    relay.accept("one")
    relay.accept("two")
    relay.subscribe(observer)

    // first observer will receive initial event, second observer all subsequent events
    QueueRelay<Object> relay = QueueRelay.create("initial")
    relay.subscribe(firstObserver)
    relay.subscribe(secondObserver)
    relay.accept("one")
    relay.accept("two")

 * } </pre>
 *
 * @param <T> the type of item expected to be observed
 */
public class QueueRelay<T> extends Relay<T> {

    private final ConcurrentLinkedQueue<T> queue = new ConcurrentLinkedQueue<>();
    private final AtomicReference<QueueDisposable<T>> subscriber = new AtomicReference<>();

    /**
     * Creates a {@link QueueRelay} without any initial items.
     *
     * @param <T> type of items emitted by the relay
     * @return the constructed {@link QueueRelay}
     */
    public static <T> QueueRelay<T> create() {
        return new QueueRelay<>();
    }

    /**
     * Creates a {@link QueueRelay} with the given initial items.
     *
     * @param initialItems varargs initial items in the queue
     * @param <T>          type of items emitted by the relay
     * @return the constructed {@link QueueRelay}
     */
    @SafeVarargs
    public static <T> QueueRelay<T> createDefault(T... initialItems) {
        return new QueueRelay<>(initialItems);
    }

    @SafeVarargs
    private QueueRelay(T... initialItems) {
        for (T item : initialItems) {
            if (item == null) throw new NullPointerException("item == null");
            queue.add(item);
        }
    }

    @Override
    public void accept(T value) {
        if (value == null) throw new NullPointerException("value == null");

        queue.add(value);

        QueueDisposable<T> qs = subscriber.get();
        if (qs != null && !qs.isDisposed()) {
            qs.drain(queue);
        }
    }

    @Override
    public boolean hasObservers() {
        return subscriber.get() != null;
    }

    @Override
    protected void subscribeActual(Observer<? super T> observer) {
        QueueDisposable<T> qs = new QueueDisposable<>(observer, this);
        observer.onSubscribe(qs);
        set(qs);
        qs.drain(queue);
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
        final QueueRelay<T> state;

        final AtomicBoolean cancelled = new AtomicBoolean();

        QueueDisposable(Observer<? super T> actual, QueueRelay<T> state) {
            this.actual = actual;
            this.state = state;
        }

        @Override
        public void dispose() {
            if (cancelled.compareAndSet(false, true)) {
                state.remove();
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

                    T item = queue.poll();
                    if (item == null) {
                        break;
                    }
                    actual.onNext(item);
                }

                missed = addAndGet(-missed);
                if (missed == 0) {
                    break;
                }
            }
        }

    }

}
