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
 * Fiber.push grows its save buffers while the fiber is suspending. When the growth helper is a
 * method the coroutine transformation touched (Arrays.copyOf becomes suspendable as soon as
 * something reachable through StringBuilder.append(Object)'s virtual toString dispatch can
 * suspend, which the Sleepy class here arranges the way any application using Thread does), the
 * helper observes the suspending state, starts saving its own frame into the buffer being grown,
 * and recurses until the stack overflows. A suspension with more than four live reference locals
 * forces the growth of the object buffer, whose copyOf goes through Array.newInstance and is
 * reachable from the poisoned string-building paths.
 */
@RunWith(TeaVMTestRunner.class)
@EachTestCompiledSeparately
@OnlyPlatform(TestPlatform.WEBASSEMBLY_GC)
@SkipJVM
public class WasmFiberSavePathTest {
    @Test
    public void savedStateGrowsBeyondInitialBuffer() {
        assertEquals("a1b1c1d1e13", manyLiveReferences(1));
        assertEquals("x zzz", describe(new Sleepy()));
    }

    private static String manyLiveReferences(int seed) {
        // five reference locals alive across the suspension force the object save buffer
        // (initial capacity four) to grow while the fiber is suspending
        String a = "a" + seed;
        String b = "b" + seed;
        String c = "c" + seed;
        String d = "d" + seed;
        String e = "e" + seed;
        int s = sum(1, 2);
        return a + b + c + d + e + s;
    }

    private static String describe(Object value) {
        // routes the value through StringBuilder.append(Object), whose virtual toString dispatch
        // is what makes Arrays.copyOf suspendable in any application where some toString can wait
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
