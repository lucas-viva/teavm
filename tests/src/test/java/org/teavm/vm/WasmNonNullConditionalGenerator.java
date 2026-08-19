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

import org.teavm.backend.wasm.intrinsics.WasmGCBodyIntrinsic;
import org.teavm.backend.wasm.intrinsics.WasmGCCodeGenContext;
import org.teavm.backend.wasm.model.WasmFunction;
import org.teavm.backend.wasm.model.WasmLocal;
import org.teavm.backend.wasm.model.WasmType;
import org.teavm.backend.wasm.model.instruction.WasmInstructionBuilder;
import org.teavm.model.MethodReference;

public class WasmNonNullConditionalGenerator implements WasmGCBodyIntrinsic {
    private WasmGCCodeGenContext context;

    public WasmNonNullConditionalGenerator(WasmGCCodeGenContext context) {
        this.context = context;
    }

    @Override
    public void apply(MethodReference method, WasmFunction function) {
        if (!method.getName().equals("conditionalOverNonNull")) {
            return;
        }

        var param = new WasmLocal(WasmType.INT32, "n");
        function.add(param);
        var stringType = (WasmType.Reference) context.classInfoProvider()
                .getClassInfo("java.lang.String").getType();

        generate(function.getBody().builder(), param, stringType.asNonNull());
    }

    private void generate(WasmInstructionBuilder builder, WasmLocal param,
            WasmType.Reference nonNullString) {
        // a non-null value beneath the condition when the suspending conditional is flattened
        builder
                .call(newStringFn(), false)
                .cast(nonNullString)
                .getLocal(param);
        var conditional = builder.conditional();

        // the suspension point in here is what makes the transformation flatten the conditional
        conditional.getThenBlock().builder()
                .i32Const(1)
                .i32Const(2)
                .call(sumFn(), true)
                .drop();

        builder.call(tagFn(), false);
    }

    private WasmFunction sumFn() {
        return context.functions().forStaticMethod(new MethodReference(WasmNonNullConditionalTest.class, "sum",
                int.class, int.class, int.class));
    }

    private WasmFunction newStringFn() {
        return context.functions().forStaticMethod(new MethodReference(WasmNonNullConditionalTest.class,
                "newString", String.class));
    }

    private WasmFunction tagFn() {
        return context.functions().forStaticMethod(new MethodReference(WasmNonNullConditionalTest.class, "tag",
                Object.class, int.class));
    }
}
