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
import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;
import org.teavm.jso.browser.Window;
import org.teavm.junit.EachTestCompiledSeparately;
import org.teavm.junit.OnlyPlatform;
import org.teavm.junit.SkipJVM;
import org.teavm.junit.TeaVMTestRunner;
import org.teavm.junit.TestPlatform;

/**
 * A plain JS callback (setTimeout) calls into code the coroutine transformation touched, while no
 * fiber is current. The generated prologue used to call Fiber.isResuming on the null current
 * fiber and trap with "dereferencing a null pointer"; it has to run as ordinary synchronous code
 * instead. Any method whose call graph can reach a suspension point is transformed, so this
 * happens for module initializers and for every JS event handler of a real application.
 */
@RunWith(TeaVMTestRunner.class)
@EachTestCompiledSeparately
@OnlyPlatform(TestPlatform.WEBASSEMBLY_GC)
@SkipJVM
public class WasmCallbackWithoutFiberTest {
    private static int observed;

    @Test
    public void transformedCodeRunsInPlainJsCallback() {
        observed = 0;
        schedule(() -> observed = transformedComputation());
        // sum completes through a later timeout, so the callback above has run by now
        assertEquals(0, sum(0, 0));
        assertEquals(42, observed);
    }

    static int transformedComputation() {
        // reaches sum() through maybeSuspend, so the whole chain is coroutine-transformed,
        // but this invocation never actually suspends
        return maybeSuspend(false) + 40;
    }

    static int maybeSuspend(boolean suspend) {
        if (suspend) {
            return sum(1, 1);
        }
        return 2;
    }

    @JSFunctor
    interface Callback extends JSObject {
        void run();
    }

    @JSBody(params = "callback", script = "setTimeout(function() { callback(); }, 0);")
    private static native void schedule(Callback callback);

    @Async
    private static native int sum(int a, int b);

    private static void sum(int a, int b, AsyncCallback<Integer> callback) {
        Window.setTimeout(() -> callback.complete(a + b), 0);
    }
}
