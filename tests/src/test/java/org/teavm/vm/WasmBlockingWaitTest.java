/*
 *  Copyright 2026 Alexey Andreev.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.teavm.vm;

import static org.junit.Assert.assertEquals;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.teavm.interop.Async;
import org.teavm.interop.AsyncCallback;
import org.teavm.jso.browser.Window;
import org.teavm.junit.EachTestCompiledSeparately;
import org.teavm.junit.OnlyPlatform;
import org.teavm.junit.SkipJVM;
import org.teavm.junit.TeaVMTestRunner;
import org.teavm.junit.TestPlatform;

/**
 * A green thread blocks on ArrayBlockingQueue.poll and another thread offers the element. The
 * registration half of the wait (the async call's companion, waitForChange) is ordinary
 * coroutine-transformed code as soon as its call graph can suspend, which the Sleepy class
 * arranges the way any threads-using application does. Fiber.suspend used to invoke the
 * companion while the fiber was already in the suspending state, so the companion mistook that
 * state for its own suspension and returned before registering the wait handler and the timeout
 * timer: the poll never woke, on the signal or on the timeout.
 */
@RunWith(TeaVMTestRunner.class)
@EachTestCompiledSeparately
@OnlyPlatform(TestPlatform.WEBASSEMBLY_GC)
@SkipJVM
public class WasmBlockingWaitTest {
    private static String result;

    @Test
    public void offerWakesWaitingPoll() {
        result = null;
        var queue = new ArrayBlockingQueue<String>(1);
        new Thread(() -> {
            try {
                result = queue.poll(5, TimeUnit.SECONDS) + "!";
            } catch (InterruptedException e) {
                result = "interrupted";
            }
        }).start();
        awaitTick();
        queue.offer("item");
        for (var i = 0; i < 100 && result == null; ++i) {
            awaitTick();
        }
        assertEquals("item!", result);
        assertEquals("x zzz", describe(new Sleepy()));
    }

    // a plain-companion async call: its registration is not transformed, so it works even when
    // blocking waits are broken, and each call yields at least one timer turn
    private static void awaitTick() {
        sum(0, 0);
    }

    private static String describe(Object value) {
        // routes the value through StringBuilder.append(Object), whose virtual toString dispatch
        // is what makes queue internals suspendable in any application where some toString waits
        return new StringBuilder("x ").append(value).toString();
    }

    static class Sleepy {
        @Override
        public String toString() {
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                // ignore
            }
            return "zzz";
        }
    }

    @Async
    private static native int sum(int a, int b);

    private static void sum(int a, int b, AsyncCallback<Integer> callback) {
        Window.setTimeout(() -> callback.complete(a + b), 0);
    }
}
