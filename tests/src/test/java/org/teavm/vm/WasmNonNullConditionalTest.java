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
import org.teavm.interop.Intrinsified;
import org.teavm.interop.NativeAsync;
import org.teavm.jso.browser.Window;
import org.teavm.junit.EachTestCompiledSeparately;
import org.teavm.junit.OnlyPlatform;
import org.teavm.junit.SkipJVM;
import org.teavm.junit.TeaVMTestRunner;
import org.teavm.junit.TestPlatform;

/**
 * A non-null value stays on the stack across a suspending conditional. The labels the coroutine
 * transformation creates for the flattened conditional recorded the value at its non-null type,
 * but the restore path rebuilds it at the nullable type, so the branch across the wrapper failed
 * to validate: "type error in branch (expected (ref $T), got (ref null $T))".
 */
@RunWith(TeaVMTestRunner.class)
@EachTestCompiledSeparately
@OnlyPlatform(TestPlatform.WEBASSEMBLY_GC)
@SkipJVM
public class WasmNonNullConditionalTest {
    @Test
    public void nonNullValueCrossesSuspendingConditional() {
        assertEquals(1, conditionalOverNonNull(1));
        assertEquals(1, conditionalOverNonNull(0));
    }

    @Async
    @NativeAsync
    @Intrinsified
    private static native int conditionalOverNonNull(int n);

    static String newString() {
        return "s";
    }

    static int tag(Object o) {
        return o instanceof String ? 1 : 2;
    }

    @Async
    private static native int sum(int a, int b);

    private static void sum(int a, int b, AsyncCallback<Integer> callback) {
        Window.setTimeout(() -> callback.complete(a + b), 0);
    }
}
