package ninja.soroosh.hashem.lang.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayDeque;
import java.util.Deque;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.management.ExecutionEvent;
import org.graalvm.polyglot.management.ExecutionListener;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class HashemExecutionListenerTest {

    private Context context;
    private final Deque<ExecutionEvent> events = new ArrayDeque<>();

    private String expectedRootName;

    @Before
    public void setUp() {
        context = Context.create("hashemi");
    }

    @After
    public void tearDown() {
        assertTrue(events.isEmpty());
        context.close();
        context = null;
    }

    private void add(ExecutionEvent e) {
        events.add(e);
    }

    @Test
    public void testRootsAndStatements() {
        ExecutionListener.newBuilder().onEnter(this::add).onReturn(this::add).//
                        roots(true).statements(true).//
                        collectExceptions(true).collectInputValues(true).collectReturnValue(true).//
                        attach(context.getEngine());

        eval("bede 2;");

        enterRoot(rootSourceSection("bede 2;"));
        enterStatement("bede 2");
        leaveStatement("bede 2", null);
        leaveRoot(rootSourceSection("bede 2;"), 2);
    }

    @Test
    public void testStatements() {
        ExecutionListener.newBuilder().onEnter(this::add).onReturn(this::add).//
                        statements(true).//
                        collectExceptions(true).collectInputValues(true).collectReturnValue(true).//
                        attach(context.getEngine());

        eval("2 + 3;");
        enterStatement("2 + 3");
        leaveStatement("2 + 3", 5);

        eval("2 + 3; 3 + 6;");
        enterStatement("2 + 3");
        leaveStatement("2 + 3", 5);
        enterStatement("3 + 6");
        leaveStatement("3 + 6", 9);
    }

    @Test
    public void testExpressions() {
        ExecutionListener.newBuilder().onEnter(this::add).onReturn(this::add).//
                        expressions(true).//
                        collectExceptions(true).collectInputValues(true).collectReturnValue(true).//
                        attach(context.getEngine());
        eval("2 + 3;");

        enterStatement("2 + 3");
        enterExpression("2");
        leaveExpression("2", 2);
        enterExpression("3");
        leaveExpression("3", 3);
        leaveStatement("2 + 3", 5, 2, 3);
    }

    @Test
    public void testRoots() {
        ExecutionListener.newBuilder().onEnter(this::add).onReturn(this::add).//
                        roots(true).//
                        collectExceptions(true).collectInputValues(true).collectReturnValue(true).//
                        attach(context.getEngine());

        eval("bede 2;");

        enterRoot(rootSourceSection("bede 2;"));
        leaveRoot(rootSourceSection("bede 2;"), 2);
    }

    @Test
    public void testExpressionsStatementsAndRoots() {
        ExecutionListener.newBuilder().onEnter(this::add).onReturn(this::add).//
                        expressions(true).statements(true).//
                        collectExceptions(true).collectInputValues(true).collectReturnValue(true).//
                        attach(context.getEngine());

        eval("2 + 3;");

        enterStatement("2 + 3");
        enterExpression("2");
        leaveExpression("2", 2);
        enterExpression("3");
        leaveExpression("3", 3);
        leaveStatement("2 + 3", 5, 2, 3);
    }

    @Test
    public void testFactorial() {
        // @formatter:off
        String characters =
                        "fac(n) {" +
                        "  age (n <= 1) bood {" +
                        "    bede 1;" +
                        "  }" +
                        "  bede fac(n - 1) * n;" +
                        "}";
        // @formatter:on
        context.eval("hashemi", "bebin " + characters);
        Value factorial = context.getBindings("hashemi").getMember("fac");
        ExecutionListener.newBuilder().onReturn(this::add).onEnter(this::add).//
                        expressions(true).statements(true).roots(true).//
                        collectExceptions(true).collectInputValues(true).collectReturnValue(true).//
                        attach(context.getEngine());
        expectedRootName = "fac";
        assertEquals(0, events.size());
        for (int i = 0; i < 10; i++) {
            testFactorial(characters, factorial);
        }
    }

    private Value eval(String s) {
        expectedRootName = "wrapper";
        context.eval("hashemi", wrapInFunction(s));
        return context.getBindings("hashemi").getMember("wrapper").execute();
    }

    private static String wrapInFunction(String s) {
        return "bebin " + rootSourceSection(s);
    }

    private static String rootSourceSection(String s) {
        return "wrapper() {\n  " + s + " \n}";
    }

    private void testFactorial(String characters, Value factorial) {
        factorial.execute(3);
        enterRoot(characters);
        enterStatement("n <= 1");
        enterExpression("n");
        leaveExpression("n", 3);
        enterExpression("1");
        leaveExpression("1", 1);
        leaveStatement("n <= 1", false, 3, 1);
        enterStatement("bede fac(n - 1) * n");
        enterExpression("fac(n - 1) * n");
        enterExpression("fac(n - 1)");
        enterExpression("fac");
        leaveExpression("fac", factorial);
        enterExpression("n - 1");
        enterExpression("n");
        leaveExpression("n", 3);
        enterExpression("1");
        leaveExpression("1", 1);
        leaveExpression("n - 1", 2, 3, 1);

        enterRoot(characters);
        enterStatement("n <= 1");
        enterExpression("n");
        leaveExpression("n", 2);
        enterExpression("1");
        leaveExpression("1", 1);
        leaveStatement("n <= 1", false, 2, 1);
        enterStatement("bede fac(n - 1) * n");
        enterExpression("fac(n - 1) * n");
        enterExpression("fac(n - 1)");
        enterExpression("fac");
        leaveExpression("fac", factorial);
        enterExpression("n - 1");
        enterExpression("n");
        leaveExpression("n", 2);
        enterExpression("1");
        leaveExpression("1", 1);
        leaveExpression("n - 1", 1, 2, 1);

        enterRoot(characters);
        enterStatement("n <= 1");
        enterExpression("n");
        leaveExpression("n", 1);
        enterExpression("1");
        leaveExpression("1", 1);
        leaveStatement("n <= 1", true, 1, 1);
        enterStatement("bede 1");
        enterExpression("1");
        leaveExpression("1", 1);
        leaveStatement("bede 1", null, 1);
        leaveRoot(characters, 1);

        leaveExpression("fac(n - 1)", 1, factorial, 1);
        enterExpression("n");
        leaveExpression("n", 2);
        leaveExpression("fac(n - 1) * n", 2, 1, 2);
        leaveStatement("bede fac(n - 1) * n", null, 2);
        leaveRoot(characters, 2);

        leaveExpression("fac(n - 1)", 2, factorial, 2);
        enterExpression("n");
        leaveExpression("n", 3);
        leaveExpression("fac(n - 1) * n", 6, 2, 3);
        leaveStatement("bede fac(n - 1) * n", null, 6);
        leaveRoot(characters, 6);

        assertTrue(events.isEmpty());
    }

    private void enterExpression(String characters) {
        ExecutionEvent event = assertEvent(characters, null);
        assertTrue(event.isExpression());
        assertFalse(event.isStatement());
        assertFalse(event.isRoot());
    }

    private void enterStatement(String characters) {
        ExecutionEvent event = assertEvent(characters, null);
        assertTrue(event.isStatement());
        // statements are sometimes expressions
        assertFalse(event.isRoot());
    }

    private void enterRoot(String characters) {
        ExecutionEvent event = assertEvent(characters, null);
        assertTrue(event.isRoot());
        assertFalse(event.isStatement());
        assertFalse(event.isExpression());
    }

    private void leaveExpression(String characters, Object returnValue, Object... inputs) {
        ExecutionEvent event = assertEvent(characters, returnValue, inputs);
        assertTrue(event.isExpression());
        assertFalse(event.isStatement());
        assertFalse(event.isRoot());
    }

    private void leaveStatement(String characters, Object returnValue, Object... inputs) {
        ExecutionEvent event = assertEvent(characters, returnValue, inputs);
        assertTrue(event.isStatement());
        // statements are sometimes expressions
        assertFalse(event.isRoot());
    }

    private void leaveRoot(String characters, Object returnValue, Object... inputs) {
        ExecutionEvent event = assertEvent(characters, returnValue, inputs);
        assertTrue(event.isRoot());
        assertFalse(event.isStatement());
        assertFalse(event.isExpression());
    }

    private ExecutionEvent assertEvent(String characters, Object returnValue, Object... inputs) {
        ExecutionEvent event = events.pop();
        assertEquals(expectedRootName, event.getRootName());
        assertEquals(characters, event.getLocation().getCharacters());
        assertEquals(inputs.length, event.getInputValues().size());
        for (int i = 0; i < inputs.length; i++) {
            assertValue(inputs[i], event.getInputValues().get(i));
        }

        if (returnValue == null) {
            assertNull(event.getReturnValue());
        } else {
            assertValue(returnValue, event.getReturnValue());
        }

        assertNotNull(event.toString());
        return event;
    }

    private static void assertValue(Object expected, Value actual) throws AssertionError {
        if (actual.isNumber()) {
            assertEquals(expected, actual.asInt());
        } else if (actual.isBoolean()) {
            assertEquals(expected, actual.asBoolean());
        } else if (actual.canExecute()) {
            assertEquals(((Value) expected).getSourceLocation(), actual.getSourceLocation());
        } else {
            throw new AssertionError(expected.toString());
        }
    }

}
