RxQueue
=======

 RxQueue contains [RxJava][rx] subject and [relay][relay] types that queue items if no observer is subscribed.
 Once a subscriber attaches, items in the queue will be drained to that subscriber unless the subscriber is 
 disposed or another subscriber takes its place. If the queue is empty, the observer will continue to receive live events
 until the subject is terminated or the observer is disposed.
 
The RxQueue types are most similar to a [UnicastSubject][unicast], but they allow additional subscribers to take the place
 of the first subscriber without throwing an exception. Adding a subscriber while a subscription is active will dispose of the first subscriber and any existing events in the 
 queue will be forwarded to the second subscriber. Only one subscriber can be active at a time!
 
 This may be useful in situations where you have a transient observer and when that observer comes back you
 want to receive any events it may have missed while it wasn't available. You also don't want to repeat any events
 it has already seen.  
 
The RxQueue types are backed by an unbounded `ConcurrentLinkedQueue`. 
 
Usage
-----

* **`QueueSubject`**

    ```java
    // observer will receive all events
    QueueSubject<Object> subject = QueueSubject.createDefault("initial");
    subject.onNext("one");
    subject.onNext("two");
    subject.subscribe(observer);
    subject.onNext("three");
  
    // first observer will receive initial event, second observer all subsequent events
    QueueSubject<Object> subject = QueueSubject.createDefault("initial");
    subject.subscribe(firstObserver);
    subject.subscribe(secondObserver);
    subject.onNext("one");
    subject.onNext("two");
    ```

* **`QueueRelay`**

    ```java 
    // observer will receive all events
    QueueRelay<Object> relay = QueueRelay.createDefault("initial");
    relay.accept("one");
    relay.accept("two");
    relay.subscribe(observer);
    relay.accept("three");
    
    // first observer will receive initial event, second observer all subsequent events
    QueueRelay<Object> relay = QueueRelay.createDefault("initial");
    relay.subscribe(firstObserver);
    relay.subscribe(secondObserver);
    relay.accept("one");
    relay.accept("two");
    ```

Download
--------

Maven: 
```xml
TODO
```

Gradle:
```groovy
TODO
```

License
-------

    Copyright 2018 Victor Rendina

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

[rx]: https://github.com/ReactiveX/RxJava/
[relay]: https://github.com/JakeWharton/RxRelay
[unicast]: http://reactivex.io/RxJava/javadoc/io/reactivex/subjects/UnicastSubject.html