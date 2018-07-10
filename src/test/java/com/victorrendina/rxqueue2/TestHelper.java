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

import static org.mockito.Mockito.mock;

public class TestHelper {

    /**
     * Mocks an Observer with the proper receiver type.
     *
     * @param <T> the value type
     * @return the mocked observer
     */
    @SuppressWarnings("unchecked")
    public static <T> Observer<T> mockObserver() {
        return mock(Observer.class);
    }

    /**
     * Mocks a Consumer with the proper receiver type.
     *
     * @param <T> the value type
     * @return the mocked consumer
     */
    @SuppressWarnings("unchecked")
    public static <T> Consumer<T> mockConsumer() {
        return mock(Consumer.class);
    }

}
