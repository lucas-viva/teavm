/*
 *  Copyright 2018 Alexey Andreev.
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
package org.teavm.runtime;

import org.teavm.interop.AsyncCallback;
import org.teavm.interop.StaticInit;
import org.teavm.interop.Unmanaged;

@StaticInit
public class Fiber {
    public static final int STATE_RUNNING = 0;
    public static final int STATE_SUSPENDING = 1;
    public static final int STATE_RESUMING = 2;
    public static int userThreadCount = 1;

    private int[] intValues;
    private int intTop;
    private long[] longValues;
    private int longTop;
    private float[] floatValues;
    private int floatTop;
    private double[] doubleValues;
    private int doubleTop;
    private Object[] objectValues;
    private int objectTop;
    private int state;
    private FiberRunner runner;
    private Object result;
    private Throwable exception;
    private boolean isPendingResume;
    private boolean daemon;
    private PendingCall deferredCall;

    private static Fiber current;
    private static PendingCall lastPendingCall;

    private Fiber(FiberRunner runner, boolean daemon) {
        this.runner = runner;
        this.daemon = daemon;
    }

    public void push(int value) {
        if (intValues == null) {
            intValues = new int[4];
        } else if (intTop + 1 == intValues.length) {
            intValues = grow(intValues);
        }
        intValues[intTop++] = value;
    }

    public void push(long value) {
        if (longValues == null) {
            longValues = new long[4];
        } else if (longTop + 1 == longValues.length) {
            longValues = grow(longValues);
        }
        longValues[longTop++] = value;
    }

    public void push(float value) {
        if (floatValues == null) {
            floatValues = new float[4];
        } else if (floatTop + 1 == floatValues.length) {
            floatValues = grow(floatValues);
        }
        floatValues[floatTop++] = value;
    }

    public void push(double value) {
        if (doubleValues == null) {
            doubleValues = new double[4];
        } else if (doubleTop + 1 == doubleValues.length) {
            doubleValues = grow(doubleValues);
        }
        doubleValues[doubleTop++] = value;
    }

    public void push(Object value) {
        if (objectValues == null) {
            objectValues = new Object[4];
        } else if (objectTop + 1 == objectValues.length) {
            objectValues = grow(objectValues);
        }
        objectValues[objectTop++] = value;
    }

    private static int[] grow(int[] array) {
        var copy = new int[array.length * 3 / 2];
        for (var i = 0; i < array.length; ++i) {
            copy[i] = array[i];
        }
        return copy;
    }

    private static long[] grow(long[] array) {
        var copy = new long[array.length * 3 / 2];
        for (var i = 0; i < array.length; ++i) {
            copy[i] = array[i];
        }
        return copy;
    }

    private static float[] grow(float[] array) {
        var copy = new float[array.length * 3 / 2];
        for (var i = 0; i < array.length; ++i) {
            copy[i] = array[i];
        }
        return copy;
    }

    private static double[] grow(double[] array) {
        var copy = new double[array.length * 3 / 2];
        for (var i = 0; i < array.length; ++i) {
            copy[i] = array[i];
        }
        return copy;
    }

    private static Object[] grow(Object[] array) {
        var copy = new Object[array.length * 3 / 2];
        for (var i = 0; i < array.length; ++i) {
            copy[i] = array[i];
        }
        return copy;
    }

    public static void reversePush(int value, Fiber fiber) {
        fiber.push(value);
    }

    public static void reversePush(long value, Fiber fiber) {
        fiber.push(value);
    }

    public static void reversePush(float value, Fiber fiber) {
        fiber.push(value);
    }

    public static void reversePush(double value, Fiber fiber) {
        fiber.push(value);
    }

    public static void reversePush(Object value, Fiber fiber) {
        fiber.push(value);
    }

    public static void reversePush(PlatformObject value, Fiber fiber) {
        fiber.push(new PlatformObjectWrapper(value));
    }

    public static void reversePush(PlatformFunction value, Fiber fiber) {
        fiber.push(new PlatformFunctionWrapper(value));
    }

    public static void reversePush(PlatformException value, Fiber fiber) {
        fiber.push(new PlatformExceptionWrapper(value));
    }

    @Unmanaged
    public int popInt() {
        return intValues[--intTop];
    }

    @Unmanaged
    public long popLong() {
        return longValues[--longTop];
    }

    @Unmanaged
    public float popFloat() {
        return floatValues[--floatTop];
    }

    @Unmanaged
    public double popDouble() {
        return doubleValues[--doubleTop];
    }

    @Unmanaged
    public Object popObject() {
        Object result = objectValues[--objectTop];
        objectValues[objectTop] = null;
        return result;
    }

    public PlatformObject popPlatformObject() {
        var wrapper = (PlatformObjectWrapper) popObject();
        return wrapper.object;
    }

    public PlatformFunction popPlatformFunction() {
        var wrapper = (PlatformFunctionWrapper) popObject();
        return wrapper.object;
    }

    public PlatformException popPlatformException() {
        var wrapper = (PlatformExceptionWrapper) popObject();
        return wrapper.object;
    }

    @Unmanaged
    public static Fiber current() {
        return current;
    }

    @Unmanaged
    public boolean isSuspending() {
        return state == STATE_SUSPENDING;
    }

    @Unmanaged
    public boolean isResuming() {
        return state == STATE_RESUMING;
    }

    @Unmanaged
    public static boolean isSuspending(Fiber fiber) {
        return fiber != null && fiber.isSuspending();
    }

    @Unmanaged
    public static boolean isResuming(Fiber fiber) {
        return fiber != null && fiber.isResuming();
    }

    @Unmanaged
    public static boolean getBoolean(Object v) {
        return v != null ? (Boolean) v : false;
    }

    @Unmanaged
    public static byte getByte(Object v) {
        return v != null ? (Byte) v : 0;
    }

    @Unmanaged
    public static short getShort(Object v) {
        return v != null ? (Short) v : 0;
    }

    @Unmanaged
    public static int getInt(Object v) {
        return v != null ? (Integer) v : 0;
    }

    @Unmanaged
    public static char getChar(Object v) {
        return v != null ? (Character) v : 0;
    }

    @Unmanaged
    public static long getLong(Object v) {
        return v != null ? (Long) v : 0;
    }

    @Unmanaged
    public static float getFloat(Object v) {
        return v != null ? (Float) v : 0;
    }

    @Unmanaged
    public static double getDouble(Object v) {
        return v != null ? (Double) v : 0;
    }

    public static Object suspend(AsyncCall call) throws Throwable {
        Fiber fiber = current();
        Thread javaThread = Thread.currentThread();
        if (fiber.isResuming()) {
            fiber.state = STATE_RUNNING;
            if (fiber.exception != null) {
                throw fiber.exception;
            }
            return fiber.result;
        }
        PendingCall pendingCall = new PendingCall(call, lastPendingCall);
        if (lastPendingCall != null) {
            lastPendingCall.next = pendingCall;
        }
        lastPendingCall = pendingCall;

        fiber.state = STATE_SUSPENDING;
        pendingCall.callback = new AsyncCallbackImpl(pendingCall, javaThread, fiber);
        fiber.deferredCall = pendingCall;
        return null;
    }

    static class AsyncCallbackImpl implements AsyncCallback<Object> {
        PendingCall pendingCall;
        Thread javaThread;
        Fiber fiber;

        AsyncCallbackImpl(PendingCall pendingCall, Thread javaThread, Fiber fiber) {
            this.pendingCall = pendingCall;
            this.javaThread = javaThread;
            this.fiber = fiber;
        }

        @Override
        public void complete(Object result) {
            if (Fiber.current == this.fiber) {
                fiber.result = result;
                fiber.isPendingResume = true;
                removePendingCall();
            } else {
                setCurrentThread(javaThread);
                javaThread = null;
                Fiber fiber = this.fiber;
                this.fiber = null;
                fiber.result = result;
                removePendingCall();
                fiber.resume();
            }
        }

        @Override
        public void error(Throwable e) {
            if (Fiber.current == this.fiber) {
                fiber.exception = e;
                fiber.isPendingResume = true;
                removePendingCall();
            } else {
                setCurrentThread(javaThread);
                javaThread = null;
                Fiber fiber = this.fiber;
                this.fiber = null;
                fiber.exception = e;
                removePendingCall();
                fiber.resume();
            }
        }

        private void removePendingCall() {
            if (pendingCall.previous != null) {
                pendingCall.previous.next = pendingCall.next;
            }
            if (pendingCall.next != null) {
                pendingCall.next.previous = pendingCall.previous;
            }
            if (pendingCall == lastPendingCall) {
                lastPendingCall = pendingCall.previous;
            }
            pendingCall = null;
        }
    }

    static native void setCurrentThread(Thread thread);

    public static void start(FiberRunner runner, boolean daemon) {
        new Fiber(runner, daemon).start();
    }

    static void startMain(String[] args) {
        start(() -> runMain(args), false);
    }

    public static native void runMain(String[] args);

    private void start() {
        Fiber former = current;
        current = this;
        while (true) {
            runner.run();
            while (deferredCall != null && !isPendingResume) {
                PendingCall callToStart = deferredCall;
                deferredCall = null;
                state = STATE_RUNNING;
                callToStart.value.run(callToStart.callback);
                if (!isPendingResume && state == STATE_RUNNING) {
                    state = STATE_SUSPENDING;
                }
            }
            if (!isPendingResume) {
                break;
            }
            isPendingResume = false;
            state = STATE_RESUMING;
        }
        current = former;
        if (!isSuspending() && !daemon && --userThreadCount == 0) {
            EventQueue.stop();
        }
    }

    void resume() {
        state = STATE_RESUMING;
        start();
    }

    public interface FiberRunner {
        void run();
    }

    public interface AsyncCall {
        void run(AsyncCallback<?> callback);
    }

    static class PendingCall {
        AsyncCall value;
        PendingCall next;
        PendingCall previous;
        AsyncCallbackImpl callback;

        PendingCall(AsyncCall value, PendingCall previous) {
            this.value = value;
            this.next = null;
            this.previous = previous;
        }
    }

    public static class PlatformObjectWrapper {
        public final PlatformObject object;

        PlatformObjectWrapper(PlatformObject object) {
            this.object = object;
        }
    }

    public static class PlatformFunctionWrapper {
        public final PlatformFunction object;

        PlatformFunctionWrapper(PlatformFunction object) {
            this.object = object;
        }
    }

    public static class PlatformExceptionWrapper {
        public final PlatformException object;

        PlatformExceptionWrapper(PlatformException object) {
            this.object = object;
        }
    }

    public static class PlatformObject {
    }

    public static class PlatformFunction {
    }

    public static class PlatformException {
    }
}
