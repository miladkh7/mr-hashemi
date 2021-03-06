
package ninja.soroosh.hashem.lang.test;

import static ninja.soroosh.hashem.lang.test.HashemJavaInteropTest.toUnixString;
import static com.oracle.truffle.tck.DebuggerTester.getSourceImpl;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedList;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.debug.Breakpoint;
import com.oracle.truffle.api.debug.DebugScope;
import com.oracle.truffle.api.debug.DebugStackFrame;
import com.oracle.truffle.api.debug.DebugValue;
import com.oracle.truffle.api.debug.Debugger;
import com.oracle.truffle.api.debug.DebuggerSession;
import com.oracle.truffle.api.debug.SuspendAnchor;
import com.oracle.truffle.api.debug.SuspendedEvent;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.source.SourceSection;

public class HashemDebugDirectTest {
    private static final Object UNASSIGNED = new Object();

    private Debugger debugger;
    private final LinkedList<Runnable> run = new LinkedList<>();
    private SuspendedEvent suspendedEvent;
    private Throwable ex;
    private Engine engine;
    private Context context;
    protected final ByteArrayOutputStream out = new ByteArrayOutputStream();
    protected final ByteArrayOutputStream err = new ByteArrayOutputStream();
    private DebuggerSession session;

    @Before
    public void before() {
        suspendedEvent = null;
        engine = Engine.newBuilder().out(out).err(err).build();
        debugger = engine.getInstruments().get("debugger").lookup(Debugger.class);
        session = debugger.startSession((event) -> {
            suspendedEvent = event;
            performWork();
            suspendedEvent = null;

        });
        context = Context.newBuilder().engine(engine).build();
        run.clear();
    }

    @After
    public void dispose() {
        session.close();
        context.close();
        engine.close();
    }

    private static Source createFactorial() {
        return Source.newBuilder("hashemi", "bebin test() {\n" +
                        "  res = fac(2);\n" + "  bechap(res);\n" +
                        "  bede res;\n" +
                        "}\n" +
                        "bebin fac(n) {\n" +
                        "  age (n <= 1) bood {\n" +
                        "    bede 1;\n" + "  }\n" +
                        "  nMinusOne = n - 1;\n" +
                        "  nMOFact = fac(nMinusOne);\n" +
                        "  res = n * nMOFact;\n" +
                        "  bede res;\n" + "}\n", "factorial.hashem").buildLiteral();
    }

    private static Source createFactorialWithDebugger() {
        return Source.newBuilder("hashemi", "bebin test() {\n" +
                        "  res = fac(2);\n" +
                        "  bechap(res);\n" +
                        "  bede res;\n" +
                        "}\n" +
                        "bebin fac(n) {\n" +
                        "  age (n <= 1) bood {\n" +
                        "    bede 1;\n" +
                        "  }\n" +
                        "  nMinusOne = n - 1;\n" +
                        "  nMOFact = fac(nMinusOne);\n" +
                        "  debugger;\n" +
                        "  res = n * nMOFact;\n" +
                        "  bede res;\n" +
                        "}\n", "factorial.hashem").buildLiteral();
    }

    private static Source createInteropComputation() {
        return Source.newBuilder("hashemi", "bebin test() {\n" +
                        "}\n" +
                        "bebin interopFunction(notifyHandler) {\n" +
                        "  executing = true;\n" +
                        "  ta (executing == true || executing) bood {\n" +
                        "    executing = notifyHandler.isExecuting;\n" +
                        "  }\n" +
                        "  bede executing;\n" +
                        "}\n", "interopComputation.hashem").buildLiteral();
    }

    protected final String getOut() {
        return toUnixString(out);
    }

    protected final String getErr() {
        try {
            err.flush();
        } catch (IOException e) {
        }
        return toUnixString(err);
    }

    @Test
    public void testBreakpoint() throws Throwable {
        final Source factorial = createFactorial();

        session.install(Breakpoint.newBuilder(getSourceImpl(factorial)).lineIs(8).build());
        context.eval(factorial);
        assertExecutedOK();

        assertLocation("fac", 8, true,
                        "bede 1", "n",
                        "1", "nMinusOne",
                        UNASSIGNED, "nMOFact",
                        UNASSIGNED, "res", UNASSIGNED);
        continueExecution();

        Value value = context.getBindings("hashemi").getMember("test").execute();
        assertExecutedOK();
        Assert.assertEquals("2\n", getOut());
        Assert.assertTrue(value.isNumber());
        int n = value.asInt();
        assertEquals("Factorial computed OK", 2, n);
    }

    @Test
    public void testDebuggerBreakpoint() throws Throwable {
        final Source factorial = createFactorialWithDebugger();

        context.eval(factorial);
        assertExecutedOK();

        assertLocation("fac", 12, true,
                        "debugger", "n",
                        "2", "nMinusOne",
                        "1", "nMOFact",
                        "1", "res", UNASSIGNED);
        continueExecution();

        Value value = context.getBindings("hashemi").getMember("test").execute();
        assertExecutedOK();
        Assert.assertEquals("2\n", getOut());
        Assert.assertTrue(value.isNumber());
        int n = value.asInt();
        assertEquals("Factorial computed OK", 2, n);
    }

    @Test
    public void stepInStepOver() throws Throwable {
        final Source factorial = createFactorial();
        context.eval(factorial);

        session.suspendNextExecution();

        assertLocation("test", 2, true, "res = fac(2)", "res", UNASSIGNED);
        stepInto(1);
        assertLocation("fac", 7, true,
                        "n <= 1", "n",
                        "2", "nMinusOne",
                        UNASSIGNED, "nMOFact",
                        UNASSIGNED, "res", UNASSIGNED);
        stepOver(1);
        assertLocation("fac", 10, true,
                        "nMinusOne = n - 1", "n",
                        "2", "nMinusOne",
                        UNASSIGNED, "nMOFact",
                        UNASSIGNED, "res", UNASSIGNED);
        stepOver(1);
        assertLocation("fac", 11, true,
                        "nMOFact = fac(nMinusOne)", "n",
                        "2", "nMinusOne",
                        "1", "nMOFact",
                        UNASSIGNED, "res", UNASSIGNED);
        stepOver(1);
        assertLocation("fac", 12, true,
                        "res = n * nMOFact", "n", "2", "nMinusOne",
                        "1", "nMOFact",
                        "1", "res", UNASSIGNED);
        stepOver(1);
        assertLocation("fac", 13, true,
                        "bede res", "n",
                        "2", "nMinusOne",
                        "1", "nMOFact",
                        "1", "res", "2");
        stepOver(1);
        assertLocation("test", 2, false, "fac(2)", "res", UNASSIGNED);
        stepOver(1);
        assertLocation("test", 3, true, "bechap(res)", "res", "2");
        stepOut();

        Value value = context.getBindings("hashemi").getMember("test");
        assertTrue(value.canExecute());
        Value resultValue = value.execute();
        String resultStr = resultValue.toString();
        Number result = resultValue.asInt();
        assertExecutedOK();

        assertNotNull(result);
        assertEquals("Factorial computed OK", 2, result.intValue());
        assertEquals("Factorial computed OK", "2", resultStr);
    }

    @Test
    public void testPause() throws Throwable {
        final Source interopComp = createInteropComputation();

        context.eval(interopComp);
        assertExecutedOK();

        final ExecNotifyHandler nh = new ExecNotifyHandler();

        // Do pause after execution has really started
        new Thread() {
            @Override
            public void run() {
                nh.waitTillCanPause();
                session.suspendNextExecution();
            }
        }.start();

        run.addLast(() -> {
            // paused
            assertNotNull(suspendedEvent);
            int line = suspendedEvent.getSourceSection().getStartLine();
            Assert.assertTrue("Unexpected line: " + line, 5 <= line && line <= 6);
            final DebugStackFrame frame = suspendedEvent.getTopStackFrame();
            DebugScope scope = frame.getScope();
            DebugValue slot = scope.getDeclaredValue("executing");
            if (slot == null) {
                slot = scope.getParent().getDeclaredValue("executing");
            }
            Assert.assertNotNull(slot);
            Assert.assertNotNull("Value is null", slot.toString());
            suspendedEvent.prepareContinue();
            nh.pauseDone();
        });

        Value value = context.getBindings("hashemi").getMember("interopFunction").execute(nh);

        assertExecutedOK();
        assertTrue(value.isBoolean());
        boolean n = value.asBoolean();
        assertTrue("Interop computation OK", !n);
    }

    private static Source createNull() {
        return Source.newBuilder("hashemi", "bebin nullTest() {\n" +
                        "  res = doNull();\n" +
                        "  bede res;\n" +
                        "}\n" +
                        "bebin doNull() {\n" +
                        "}\n", "nullTest.hashem").buildLiteral();
    }

    @Test
    public void testNull() throws Throwable {
        final Source nullTest = createNull();
        context.eval(nullTest);

        session.suspendNextExecution();

        assertLocation("nullTest", 2, true, "res = doNull()", "res", UNASSIGNED);
        stepInto(1);
        assertLocation("nullTest", 3, true, "bede res", "res", "POOCH");
        continueExecution();

        Value value = context.getBindings("hashemi").getMember("nullTest").execute();
        assertExecutedOK();

        String val = value.toString();
        assertNotNull(val);
        assertEquals("Hashemi displays null as POOCH", "POOCH", val);
    }

    private void performWork() {
        try {
            if (ex == null && !run.isEmpty()) {
                Runnable c = run.removeFirst();
                c.run();
            }
        } catch (Throwable e) {
            ex = e;
        }
    }

    private void stepOver(final int size) {
        run.addLast(() -> {
            suspendedEvent.prepareStepOver(size);
        });
    }

    private void stepOut() {
        run.addLast(() -> {
            suspendedEvent.prepareStepOut(1);
        });
    }

    private void continueExecution() {
        run.addLast(() -> {
            suspendedEvent.prepareContinue();
        });
    }

    private void stepInto(final int size) {
        run.addLast(() -> {
            suspendedEvent.prepareStepInto(size);
        });
    }

    private void assertLocation(final String name, final int line, final boolean isBefore, final String code, final Object... expectedFrame) {
        run.addLast(() -> {
            assertNotNull(suspendedEvent);

            final SourceSection suspendedSourceSection = suspendedEvent.getSourceSection();
            Assert.assertEquals(line, suspendedSourceSection.getStartLine());
            Assert.assertEquals(code, suspendedSourceSection.getCharacters());

            Assert.assertEquals(isBefore, suspendedEvent.getSuspendAnchor() == SuspendAnchor.BEFORE);
            final DebugStackFrame frame = suspendedEvent.getTopStackFrame();
            assertEquals(name, frame.getName());

            for (int i = 0; i < expectedFrame.length; i = i + 2) {
                final String expectedIdentifier = (String) expectedFrame[i];
                final Object expectedValue = expectedFrame[i + 1];
                DebugScope scope = frame.getScope();
                DebugValue slot = scope.getDeclaredValue(expectedIdentifier);
                while (slot == null && (scope = scope.getParent()) != null) {
                    slot = scope.getDeclaredValue(expectedIdentifier);
                }
                if (expectedValue != UNASSIGNED) {
                    Assert.assertNotNull(expectedIdentifier, slot);
                    final String slotValue = slot.as(String.class);
                    Assert.assertEquals(expectedValue, slotValue);
                } else {
                    Assert.assertNull(expectedIdentifier, slot);
                }
            }
            run.removeFirst().run();
        });
    }

    private void assertExecutedOK() throws Throwable {
        Assert.assertTrue(getErr(), getErr().isEmpty());
        if (ex != null) {
            if (ex instanceof AssertionError) {
                throw ex;
            } else {
                throw new AssertionError("Error during execution", ex);
            }
        }
        assertTrue("Assuming all requests processed: " + run, run.isEmpty());
    }

    @ExportLibrary(InteropLibrary.class)
    @SuppressWarnings({"static-method", "unused"})
    static class ExecNotifyHandler implements TruffleObject {

        private final Object pauseLock = new Object();
        private boolean canPause;
        private volatile boolean pauseDone;

        @ExportMessage
        final Object readMember(String member) {
            setCanPause();
            return !isPauseDone();
        }

        @ExportMessage
        final boolean isMemberReadable(String member) {
            return true;
        }

        @ExportMessage
        final boolean hasMembers() {
            return true;
        }

        @ExportMessage
        final Object getMembers(boolean includeInternal) {
            throw new AssertionError();
        }

        private void waitTillCanPause() {
            synchronized (pauseLock) {
                while (!canPause) {
                    try {
                        pauseLock.wait();
                    } catch (InterruptedException iex) {
                    }
                }
            }
        }

        void setCanPause() {
            synchronized (pauseLock) {
                canPause = true;
                pauseLock.notifyAll();
            }
        }

        private void pauseDone() {
            pauseDone = true;
        }

        boolean isPauseDone() {
            return pauseDone;
        }

    }

}
