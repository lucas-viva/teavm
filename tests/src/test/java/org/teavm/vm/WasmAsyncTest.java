/*
 *  Copyright 2025 Alexey Andreev.
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
import static org.junit.Assert.fail;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.teavm.classlib.support.Reflectable;
import org.teavm.interop.Async;
import org.teavm.interop.AsyncCallback;
import org.teavm.interop.Intrinsified;
import org.teavm.interop.NativeAsync;
import org.teavm.jso.browser.Window;
import org.teavm.junit.EachTestCompiledSeparately;
import org.teavm.junit.OnlyPlatform;
import org.teavm.junit.SkipJVM;
import org.teavm.junit.TeaVMTestRunner;
import org.teavm.junit.TestPlatform;

@RunWith(TeaVMTestRunner.class)
@EachTestCompiledSeparately
@OnlyPlatform(TestPlatform.WEBASSEMBLY_GC)
@SkipJVM
public class WasmAsyncTest {
    @Test
    public void breakAsyncBlockFromNonAsync() {
        assertEquals(1001, generatedMethod(1));
        assertEquals(2011, generatedMethod(2));
        assertEquals(3111, generatedMethod(3));
        assertEquals(4111, generatedMethod(4));
    }

    @Test
    public void suspendingLoopAfterBranch() {
        assertEquals(100, loopAfterBranch(1));
        assertEquals(7, loopAfterBranch(0));
    }

    @Test
    public void suspendingLoopAfterThrow() {
        assertEquals(100, loopAfterThrow(1));
        try {
            loopAfterThrow(0);
            fail("Exception expected");
        } catch (RuntimeException e) {
            assertEquals("generated", e.getMessage());
        }
    }
    
    @Test
    public void suspendWithValueTeedIntoWiderLocal() {
        // sum(20, 25) plus the length of "generated"
        assertEquals(54, teeThenSuspend());
    }

    @Test
    public void suspendingReflectiveCall() throws Exception {
        // Two arguments matter here: with more than one argument the generated caller reads
        // the argument array multiple times, and the method being suspendable means the caller
        // goes through the coroutine transformation.
        var method = ReflectiveTarget.class.getMethod("concatAfterSleep", String.class, String.class);
        var result = method.invoke(null, "left", "right");
        assertEquals("left:right", result);
    }

    @Async
    @NativeAsync
    @Intrinsified
    private static native int generatedMethod(int n);

    @Async
    @NativeAsync
    @Intrinsified
    private static native int loopAfterBranch(int n);

    @Async
    @NativeAsync
    @Intrinsified
    private static native int loopAfterThrow(int n);

    @Async
    @NativeAsync
    @Intrinsified
    private static native int teeThenSuspend();

    static RuntimeException newException() {
        return new RuntimeException("generated");
    }

    static int lengthOf(Throwable t) {
        return t.getMessage().length();
    }

    @Async
    private static native int sum(int a, int b);

    private static void sum(int a, int b, AsyncCallback<Integer> callback) {
        Window.setTimeout(() -> callback.complete(a + b), 0);
    }

    static class ReflectiveTarget {
        @Reflectable
        public static String concatAfterSleep(String a, String b) throws InterruptedException {
            Thread.sleep(1);
            return a + ":" + b;
        }
    }
}
