/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016 KuiGang Wang
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package lt.compiler.cases;

import lt.compiler.*;
import lt.compiler.IndentScanner;
import lt.compiler.Properties;
import lt.compiler.Scanner;
import lt.compiler.semantic.SModifier;
import lt.compiler.semantic.STypeDef;
import lt.compiler.syntactic.Expression;
import lt.compiler.syntactic.Statement;
import lt.compiler.syntactic.literal.NumberLiteral;
import lt.compiler.syntactic.operation.TwoVariableOperation;
import lt.generator.SourceGenerator;
import lt.lang.*;
import lt.lang.function.Function0;
import lt.lang.function.Function1;
import lt.lang.function.Function3;
import lt.repl.ScriptCompiler;
import org.junit.Test;

import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.*;
import java.math.BigInteger;
import java.util.*;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

/**
 * test code generator
 */
public class TestCodeGen {
        public static Class<?> retrieveClass(String code, String clsName) throws IOException, SyntaxException, ClassNotFoundException {
                ErrorManager err = new ErrorManager(true);
                Scanner lexicalProcessor = new ScannerSwitcher("test.lt", new StringReader(code), new Properties(), err);
                Parser syntacticProcessor = new Parser(lexicalProcessor.scan(), err);
                Map<String, List<Statement>> map = new HashMap<>();
                map.put("test.lt", syntacticProcessor.parse());
                SemanticProcessor semanticProcessor = new SemanticProcessor(map, Thread.currentThread().getContextClassLoader(), err);
                Set<STypeDef> types = semanticProcessor.parse();

                CodeGenerator codeGenerator = new CodeGenerator(types, semanticProcessor.getTypes());
                Map<String, byte[]> list = codeGenerator.generate();

                ClassLoader classLoader = new ClassLoader() {
                        @Override
                        protected Class<?> findClass(String name)
                                throws ClassNotFoundException {
                                byte[] bs = list.get(name);
                                if (bs == null) throw new ClassNotFoundException(name);
                                return defineClass(name, bs, 0, bs.length);
                        }
                };

                return classLoader.loadClass(clsName);
        }

        @Test
        public void testPkg() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "package my::test\n" +
                                "class TestPkg",
                        "my.test.TestPkg");
                assertEquals("my.test.TestPkg", cls.getName());
        }

        @Test
        public void testClass() throws Exception {
                Class<?> cls = retrieveClass("class TestClass", "TestClass");
                assertEquals("TestClass", cls.getName());
                assertEquals(Modifier.PUBLIC, cls.getModifiers());
                assertEquals(1, cls.getDeclaredConstructors().length);
                assertEquals(0, cls.getDeclaredConstructors()[0].getParameterCount());
        }

        @Test
        public void testConstructorParam() throws Exception {
                Class<?> cls = retrieveClass("class TestConstructorParam(a,b)\n", "TestConstructorParam");
                assertEquals("TestConstructorParam", cls.getName());
                assertEquals(Modifier.PUBLIC, cls.getModifiers());
                assertEquals(1, cls.getDeclaredConstructors().length);
                assertEquals(2, cls.getDeclaredConstructors()[0].getParameterCount());
        }

        @Test
        public void testField() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "class TestField\n" +
                                "    a=1\n" +
                                "    b:int\n" +
                                "    c:Integer",
                        "TestField");
                assertEquals(3, cls.getDeclaredFields().length);
                assertEquals(Object.class, cls.getDeclaredField("a").getType());
                assertEquals(int.class, cls.getDeclaredField("b").getType());
                assertEquals(Integer.class, cls.getDeclaredField("c").getType());
        }

        @Test
        public void testMethod() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "class TestMethod\n" +
                                "    method():Unit",
                        "TestMethod");
                assertEquals(1, cls.getDeclaredMethods().length);
                Method m = cls.getDeclaredMethods()[0];
                assertEquals("method", m.getName());
                assertEquals(void.class, m.getReturnType());
                assertEquals(0, m.getParameterCount());
                assertEquals(Modifier.PUBLIC, m.getModifiers());
        }

        @Test
        public void testModifier() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "private class TestModifier\n" +
                                "    public field\n" +
                                "    protected method():Unit",
                        "TestModifier");
                assertEquals(Modifier.PUBLIC, cls.getModifiers());

                assertEquals(1, cls.getDeclaredConstructors().length);
                assertEquals(Modifier.PRIVATE, cls.getDeclaredConstructors()[0].getModifiers());

                assertEquals(1, cls.getDeclaredFields().length);
                assertEquals(Modifier.PUBLIC, cls.getDeclaredFields()[0].getModifiers());

                assertEquals(1, cls.getDeclaredMethods().length);
                assertEquals(Modifier.PROTECTED, cls.getDeclaredMethods()[0].getModifiers());
        }

        @Test
        public void testReturnStr() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "class TestReturnStr\n" +
                                "    static\n" +
                                "        method()='abc'",
                        "TestReturnStr");
                Method method = cls.getMethod("method");
                String str = (String) method.invoke(null);
                assertEquals("abc", str);
        }

        @Test
        public void testInvokeStatic() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "class TestInvokeStatic\n" +
                                "    static\n" +
                                "        method()=System.getProperties()",
                        "TestInvokeStatic");
                Method method = cls.getMethod("method");
                Object o = method.invoke(null);
                assertTrue(o instanceof java.util.Properties);
        }

        @Test
        public void testInvokeVirtual() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "class TestInvokeVirtual\n" +
                                "    static\n" +
                                "        method()=System.getProperties().get('java.version')",
                        "TestInvokeVirtual");

                Method method = cls.getMethod("method");
                Object o = method.invoke(null);
                assertEquals(System.getProperties().get("java.version"), o);
        }

        @Test
        public void testInvokeInterface() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "import java::util::_\n" +
                                "class TestInvokeInterface\n" +
                                "    static\n" +
                                "        def method()\n" +
                                "            return Collections.singletonList('abc').size()",
                        "TestInvokeInterface");

                Method method = cls.getMethod("method");
                Object o = method.invoke(null);
                assertEquals(1, o);
        }

        @Test
        public void testInvokeSpecial() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "class TestInvokeSpecial\n" +
                                "    private priMethod()\n" +
                                "        return 1\n" +
                                "    def method()\n" +
                                "        return priMethod()",
                        "TestInvokeSpecial");

                Object o = cls.newInstance();
                Method method = cls.getMethod("method");
                Object res = method.invoke(o);
                assertEquals(1, res);
        }

        @Test
        public void testInvokeDynamic() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "class TestInvokeDynamic\n" +
                                "    static\n" +
                                "        def method(o)\n" +
                                "            return o.size()",
                        "TestInvokeDynamic");

                Method method = cls.getMethod("method", Object.class);
                List<Integer> l = new ArrayList<>();
                l.add(1);
                l.add(2);
                Object o = method.invoke(null, l);
                assertEquals(2, o);
        }

        @Test
        public void testInvokeDynamic_WithArgs() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "class TestInvokeDynamic\n" +
                                "    static\n" +
                                "        def method(o)\n" +
                                "            return o.add(3)",
                        "TestInvokeDynamic");

                Method method = cls.getMethod("method", Object.class);
                List<Integer> l = new ArrayList<>();
                l.add(1);
                l.add(2);
                method.invoke(null, l);
                assertEquals(Arrays.asList(1, 2, 3), l);
        }

        @Test
        public void testInvokeDynamic_WithArgs_Method_Primitive() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "class TestInvokeDynamic\n" +
                                "    static\n" +
                                "        def method(o)\n" +
                                "            return o.add(0,3)",
                        "TestInvokeDynamic");

                Method method = cls.getMethod("method", Object.class);
                List<Integer> l = new ArrayList<>();
                l.add(1);
                l.add(2);
                method.invoke(null, l);
                assertEquals(Arrays.asList(3, 1, 2), l);
        }

        @Test
        public void testTstore() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "class TestInvokeDynamic\n" +
                                "    static\n" +
                                "        def method()\n" +
                                "            i=1\n" +
                                "            return i",
                        "TestInvokeDynamic");

                Method method = cls.getMethod("method");
                assertEquals(1, method.invoke(null));
        }

        @Test
        public void testNew() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "import java::util::_\n" +
                                "class TestNew\n" +
                                "    static\n" +
                                "        def method()\n" +
                                "            return ArrayList()",
                        "TestNew");

                Method method = cls.getMethod("method");
                Object o = method.invoke(null);
                assertTrue(o instanceof ArrayList);
        }

        @Test
        public void testTwoVarOp() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "import java::util::_\n" +
                                "class TestTwoVarOp\n" +
                                "    static\n" +
                                "        def method(a,b)\n" +
                                "            return a|b",
                        "TestTwoVarOp");

                Method method = cls.getMethod("method", Object.class, Object.class);
                assertEquals(3, method.invoke(null, 1, 2));
                assertEquals(true, method.invoke(null, false, true));
                BigInteger a = new BigInteger("1");
                BigInteger b = new BigInteger("2");
                assertEquals(new BigInteger("3"), method.invoke(null, a, b)); // BigInteger.or(BigInteger)
        }

        @Test
        public void testRange() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "class TestRange\n" +
                                "    static\n" +
                                "        def method1()\n" +
                                "            return 1..10\n" +
                                "        def method2()\n" +
                                "            return 1.:10\n" +
                                "        def method3()\n" +
                                "            return 10..1\n" +
                                "        def method4()\n" +
                                "            return 10.:1",
                        "TestRange");

                Method method1 = cls.getMethod("method1");
                List range1 = (RangeList) method1.invoke(null);
                assertEquals(Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10), range1);

                Method method2 = cls.getMethod("method2");
                List range2 = (RangeList) method2.invoke(null);
                assertEquals(Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9), range2);

                Method method3 = cls.getMethod("method3");
                List range3 = (RangeList) method3.invoke(null);
                assertEquals(Arrays.asList(10, 9, 8, 7, 6, 5, 4, 3, 2, 1), range3);

                Method method4 = cls.getMethod("method4");
                List range4 = (RangeList) method4.invoke(null);
                assertEquals(Arrays.asList(10, 9, 8, 7, 6, 5, 4, 3, 2), range4);
        }

        @Test
        public void testPow() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "class TestPow\n" +
                                "    static\n" +
                                "        def method()\n" +
                                "            return 2^^4\n",
                        "TestPow");
                Method method = cls.getMethod("method");
                assertEquals(16.0, method.invoke(null));
        }

        @Test
        public void testPowObj() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "class TestPowObj\n" +
                                "    static\n" +
                                "        def method(a,b)\n" +
                                "            return a^^b\n",
                        "TestPowObj");
                Method method = cls.getMethod("method", Object.class, Object.class);
                assertEquals(new BigInteger("16"), method.invoke(null, new BigInteger("2"), 4));
        }

        @Test
        public void testIn() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "import java::util::_\n" +
                                "class TestIn\n" +
                                "    static\n" +
                                "        def method(a,ls)\n" +
                                "            return a in ls\n",
                        "TestIn");
                Method method = cls.getMethod("method", Object.class, Object.class);
                assertEquals(true, method.invoke(null, 1, Arrays.asList(1, 2)));
                assertEquals(false, method.invoke(null, 3, Arrays.asList(1, 2)));
        }

        @Test
        public void testInRange() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "import java::util::_\n" +
                                "class TestInRange\n" +
                                "    static\n" +
                                "        def method()\n" +
                                "            return 2 in 1..2\n",
                        "TestInRange");
                Method method = cls.getMethod("method");
                assertEquals(true, method.invoke(null));
        }

        @Test
        public void testIf() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "class TestIf\n" +
                                "    static\n" +
                                "        def method(a)\n" +
                                "            if a\n" +
                                "                return 1\n" +
                                "            else\n" +
                                "                return 2",
                        "TestIf");

                Method method = cls.getMethod("method", Object.class);
                assertEquals(1, method.invoke(null, true));
                assertEquals(2, method.invoke(null, false));

                assertEquals(1, method.invoke(null, 1)); // if 1
                assertEquals(2, method.invoke(null, 0)); // if 0

                assertEquals(2, method.invoke(null, (Object) null)); // if null

                assertEquals(2, method.invoke(null, Unit.get())); // unit
        }

        @Test
        public void testIfComplex() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "class TestIfComplex\n" +
                                "    static\n" +
                                "        def method(a,b,c)\n" +
                                "            i=0\n" +
                                "            if a\n" +
                                "                i=1\n" +
                                "            elseif b\n" +
                                "                i=2\n" +
                                "            elseif c\n" +
                                "                i=3\n" +
                                "            else\n" +
                                "                i=4\n" +
                                "            return i",
                        "TestIfComplex");

                Method method = cls.getMethod("method", Object.class, Object.class, Object.class);
                assertEquals(1, method.invoke(null, true, false, false));
                assertEquals(1, method.invoke(null, true, true, false));
                assertEquals(1, method.invoke(null, true, false, true));
                assertEquals(1, method.invoke(null, true, true, true));

                assertEquals(2, method.invoke(null, false, true, false));
                assertEquals(2, method.invoke(null, false, true, true));

                assertEquals(3, method.invoke(null, false, false, true));

                assertEquals(4, method.invoke(null, false, false, false));
        }

        @Test
        public void testWhile() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "class TestWhile\n" +
                                "    static\n" +
                                "        def method(a:int)\n" +
                                "            s=StringBuilder()\n" +
                                "            while a\n" +
                                "                s.append(a)\n" +
                                "                a=a-1\n" +
                                "            return s.toString()",
                        "TestWhile");

                Method method = cls.getMethod("method", int.class);
                assertEquals("321", method.invoke(null, 3));
        }

        @Test
        public void testDoWhile() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "class TestWhile\n" +
                                "    static\n" +
                                "        def method(a:int)\n" +
                                "            s=StringBuilder()\n" +
                                "            do\n" +
                                "                s.append(a)\n" +
                                "                a=a-1\n" +
                                "            while a\n" +
                                "            return s.toString()",
                        "TestWhile");

                Method method = cls.getMethod("method", int.class);
                assertEquals("321", method.invoke(null, 3));
        }

        @Test
        public void testFor() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "class TestFor\n" +
                                "    static\n" +
                                "        def method()\n" +
                                "            s=StringBuilder()\n" +
                                "            for i in 1..3\n" +
                                "                s.append(i)\n" +
                                "            return s.toString()",
                        "TestFor");

                Method method = cls.getMethod("method");
                assertEquals("123", method.invoke(null));
        }

        @Test
        public void testThrow() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "class TestThrow\n" +
                                "    static\n" +
                                "        def method()\n" +
                                "            throw RuntimeException('ex')",
                        "TestThrow");

                Method method = cls.getMethod("method");
                try {
                        method.invoke(null);
                        fail("method should throw an exception");
                } catch (InvocationTargetException e) {
                        assertTrue(e.getCause() instanceof RuntimeException);
                        assertEquals("ex", e.getCause().getMessage());
                }
        }

        @Test
        public void testTryCatch() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "class TestTryCatch\n" +
                                "    static\n" +
                                "        def method(func)\n" +
                                "            try\n" +
                                "                func.apply()\n" +
                                "            catch e\n" +
                                "                if e is type NullPointerException or e is type ClassCastException\n" +
                                "                    return 1\n" +
                                "                elseif e is type Error\n" +
                                "                    return e.getMessage()\n" +
                                "                elseif e is type Throwable\n" +
                                "                    return 3\n" +
                                "            return 4",
                        "TestTryCatch");

                Method method = cls.getMethod("method", Object.class);
                // null pointer exception
                assertEquals(1, method.invoke(null, (I) () -> {
                        throw new NullPointerException();
                }));
                // class cast exception
                assertEquals(1, method.invoke(null, (I) () -> {
                        throw new ClassCastException();
                }));
                // error
                assertEquals("msg", method.invoke(null, (I) () -> {
                        throw new Error("msg");
                }));
                // throwable
                assertEquals(3, method.invoke(null, (I) () -> {
                        throw new Throwable();
                }));
                // none
                assertEquals(4, method.invoke(null, (I) () -> {
                }));
        }

        @Test
        public void testTryCatchTemp() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "class TestTryCatch\n" +
                                "    static\n" +
                                "        def method(func)\n" +
                                "            try\n" +
                                "                func.apply()\n" +
                                "            catch e\n" +
                                "                if 1===1\n" +
                                "                    return 1\n" +
                                "            finally\n" +
                                "            return 4",
                        "TestTryCatch");

                Method method = cls.getMethod("method", Object.class);
                // null pointer exception
                assertEquals(1, method.invoke(null, (I) () -> {
                        throw new NullPointerException();
                }));
        }

        interface I {
                @SuppressWarnings("unused")
                void apply() throws Throwable;
        }

        @Test
        public void testContinualAssign() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "class TestContinualAssign\n" +
                                "    static\n" +
                                "        def method()\n" +
                                "            b=a=2\n" +
                                "            return b",
                        "TestContinualAssign");

                Method method = cls.getMethod("method");
                assertEquals(2, method.invoke(null));
        }

        @Test
        public void testUnit() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "class TestUnit\n" +
                                "    static\n" +
                                "        def method()\n" +
                                "            return Unit",
                        "TestUnit");

                Method method = cls.getMethod("method");
                assertEquals(Unit.get(), method.invoke(null));
        }

        @Test
        public void testStringAdd() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "class TestStringAdd\n" +
                                "    static\n" +
                                "        def method()\n" +
                                "            return 'abc'+'d'+1",
                        "TestStringAdd");

                Method method = cls.getMethod("method");
                assertEquals("abcd1", method.invoke(null));
        }

        @Test
        public void testLogicAnd() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "class TestLogicAnd\n" +
                                "    static\n" +
                                "        def method(a,b)\n" +
                                "            return a&&b",
                        "TestLogicAnd");

                Method method = cls.getMethod("method", Object.class, Object.class);
                assertEquals(true, method.invoke(null, true, true));
                assertEquals(false, method.invoke(null, false, true));
                assertEquals(false, method.invoke(null, true, false));
                assertEquals(false, method.invoke(null, false, false));
        }

        @Test
        public void testLogicAnd2() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "class TestLogicAnd\n" +
                                "    static\n" +
                                "        def method(a,ls)\n" +
                                "            a&&ls.add(1)",
                        "TestLogicAnd");

                Method method = cls.getMethod("method", Object.class, Object.class);

                List<Integer> ls = new ArrayList<>();
                method.invoke(null, true, ls);
                assertEquals(Collections.singletonList(1), ls);

                ls.clear();
                method.invoke(null, false, ls);
                assertTrue(ls.isEmpty());
        }

        @Test
        public void testLogicAnd3() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "class TestLogicAnd\n" +
                                "    static\n" +
                                "        def method(a,b,c)\n" +
                                "            return a&&b&&c",
                        "TestLogicAnd");

                Method method = cls.getMethod("method", Object.class, Object.class, Object.class);

                assertEquals(true, method.invoke(null, true, true, true));
                assertEquals(false, method.invoke(null, false, true, true));
                assertEquals(false, method.invoke(null, true, false, true));
                assertEquals(false, method.invoke(null, true, true, false));
                assertEquals(false, method.invoke(null, false, false, true));
                assertEquals(false, method.invoke(null, false, true, false));
                assertEquals(false, method.invoke(null, true, false, false));
                assertEquals(false, method.invoke(null, false, false, false));
        }

        @Test
        public void testLogicOr() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "class TestLogicOr\n" +
                                "    static\n" +
                                "        def method(a,b)\n" +
                                "            return a||b",
                        "TestLogicOr");

                Method method = cls.getMethod("method", Object.class, Object.class);
                assertEquals(true, method.invoke(null, true, true));
                assertEquals(true, method.invoke(null, false, true));
                assertEquals(true, method.invoke(null, true, false));
                assertEquals(false, method.invoke(null, false, false));
        }

        @Test
        public void testLogicOr2() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "class TestLogicOr\n" +
                                "    static\n" +
                                "        def method(a,ls)\n" +
                                "            return a||(\n" +
                                "                ls.add(1)\n" +
                                "                return 10\n" +
                                "            )",
                        "TestLogicOr");

                Method method = cls.getMethod("method", Object.class, Object.class);

                List<Integer> ls = new ArrayList<>();
                assertEquals(10, method.invoke(null, false, ls));
                assertEquals(Collections.singletonList(1), ls);

                ls.clear();
                assertEquals(true, method.invoke(null, true, ls));
                assertTrue(ls.isEmpty());
        }

        @Test
        public void testLogicOr3() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "class TestLogicOr\n" +
                                "    static\n" +
                                "        def method(a,b,c)\n" +
                                "            return a||b||c",
                        "TestLogicOr");

                Method method = cls.getMethod("method", Object.class, Object.class, Object.class);

                assertEquals(true, method.invoke(null, true, true, true));
                assertEquals(true, method.invoke(null, false, true, true));
                assertEquals(true, method.invoke(null, true, false, true));
                assertEquals(true, method.invoke(null, true, true, false));
                assertEquals(true, method.invoke(null, false, false, true));
                assertEquals(true, method.invoke(null, false, true, false));
                assertEquals(true, method.invoke(null, true, false, false));
                assertEquals(false, method.invoke(null, false, false, false));
        }

        @Test
        public void testLogicAndOr() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "class TestLogicAndOr\n" +
                                "    static\n" +
                                "        def method(a,b,c)\n" +
                                "            return a or b and c",
                        "TestLogicAndOr");

                // && has higher priority than ||
                // a or b and c
                // ==
                // a or (b and c)
                Method method = cls.getMethod("method", Object.class, Object.class, Object.class);

                //                                       a  or ( b  and  c )
                assertEquals(true, method.invoke(null, true, false, false));
                assertEquals(false, method.invoke(null, false, false, true));
                assertEquals(true, method.invoke(null, false, true, true));
        }

        @Test
        public void testUnaryInc() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "class TestUnaryInc\n" +
                                "    static\n" +
                                "        def method(a:int)\n" +
                                "            return ++a",
                        "TestUnaryInc");
                Method method = cls.getMethod("method", int.class);
                assertEquals(2, method.invoke(null, 1));
        }

        @Test
        public void testSelfInc() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "class TestSelfInc\n" +
                                "    static\n" +
                                "        def method(a:int)\n" +
                                "            return a++",
                        "TestSelfInc");
                Method method = cls.getMethod("method", int.class);
                assertEquals(1, method.invoke(null, 1));
        }

        @Test
        public void testInvokeVoidMethodReturnUnit() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "class TestInvokeVoidMethodReturnUnit\n" +
                                "    static\n" +
                                "        private m():Unit\n" +
                                "        def method()\n" +
                                "            return m()",
                        "TestInvokeVoidMethodReturnUnit");
                Method method = cls.getMethod("method");
                assertEquals(Unit.get(), method.invoke(null));
        }

        @Test
        public void testVoidMethodLogicAnd() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "class TestVoidMethodLogicAnd\n" +
                                "    static\n" +
                                "        i:int\n" +
                                "        private m():Unit\n" +
                                "            i=100\n" +
                                "        def method(a)\n" +
                                "            return a&&m()\n" +
                                "        getI()=i",
                        "TestVoidMethodLogicAnd");
                Method method = cls.getMethod("method", Object.class);
                Method getI = cls.getMethod("getI");

                assertEquals(false, method.invoke(null, false));
                assertEquals(0, getI.invoke(null));

                assertEquals(false, method.invoke(null, true));
                assertEquals(100, getI.invoke(null));
        }

        @Test
        public void testTAStore() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "class TestTAStore\n" +
                                "    static\n" +
                                "        def method(a:[]int)\n" +
                                "            return a[1]=100",
                        "TestTAStore");
                Method method = cls.getMethod("method", int[].class);

                int[] arr = new int[]{1, 2};
                method.invoke(null, (Object) arr);
                assertEquals(100, arr[1]);
        }

        @Test
        public void testIndexAccessObject() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "class TestIndexAccessObject\n" +
                                "    static\n" +
                                "        def method(a)\n" +
                                "            return a[1]=100",
                        "TestIndexAccessObject");
                Method method = cls.getMethod("method", Object.class);

                List<Integer> arr = new ArrayList<>();
                arr.add(1);
                arr.add(2);
                method.invoke(null, arr);
                assertEquals(Integer.valueOf(100), arr.get(1));

                Map<Integer, Integer> map = new HashMap<>();
                map.put(1, 1);
                map.put(2, 2);
                method.invoke(null, map);
                assertEquals(Integer.valueOf(100), map.get(1));
        }

        @Test
        public void testNull() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "class TestNull\n" +
                                "    static\n" +
                                "        method()=null",
                        "TestNull");
                Method method = cls.getMethod("method");
                assertNull(method.invoke(null));
        }

        @Test
        public void testArrayLength() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "class TestArrayLength\n" +
                                "    static\n" +
                                "        method(arr:[]int)=arr.length",
                        "TestArrayLength");
                Method method = cls.getMethod("method", int[].class);
                int[] arr = new int[]{1, 1, 1, 1};
                assertEquals(4, method.invoke(null, (Object) arr));
        }

        @Test
        public void testNot() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "class TestNot\n" +
                                "    static\n" +
                                "        method(i:int)=~i",
                        "TestNot");
                Method method = cls.getMethod("method", int.class);

                assertEquals(-3, method.invoke(null, 2));
        }

        @Test
        public void testNot2() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "class TestNot\n" +
                                "    static\n" +
                                "        method(arr)=~arr",
                        "TestNot");
                Method method = cls.getMethod("method", Object.class);

                BigInteger bigInteger = new BigInteger("2");
                BigInteger result = (BigInteger) method.invoke(null, bigInteger); // invoke bigInteger.not();
                assertEquals(new BigInteger("-3"), result);
        }

        @Test
        public void testNeg() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "class TestNeg\n" +
                                "    static\n" +
                                "        method(i:int)=-i",
                        "TestNeg");
                Method method = cls.getMethod("method", int.class);
                assertEquals(-2, method.invoke(null, 2));
        }

        @Test
        public void testNeg2() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "class TestNeg\n" +
                                "    static\n" +
                                "        method(i)=-i",
                        "TestNeg");
                Method method = cls.getMethod("method", Object.class);
                assertEquals(new BigInteger("-2"), method.invoke(null, new BigInteger("2")));
        }

        @Test
        public void testNewArray() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "class TestNewArray\n" +
                                "    static\n" +
                                "        method():[]int=[10,20]",
                        "TestNewArray");
                Method method = cls.getMethod("method");
                int[] arr = (int[]) method.invoke(null);
                assertEquals(2, arr.length);
                assertEquals(10, arr[0]);
                assertEquals(20, arr[1]);
        }

        @Test
        public void testANewArray() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "class TestANewArray\n" +
                                "    static\n" +
                                "        method():[]Object=[10,20]",
                        "TestANewArray");
                Method method = cls.getMethod("method");
                Object[] arr = (Object[]) method.invoke(null);
                assertEquals(2, arr.length);
                assertEquals(10, arr[0]);
                assertEquals(20, arr[1]);
        }

        @Test
        public void testNewList() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "class TestANewArray\n" +
                                "    static\n" +
                                "        method()=[10,20]",
                        "TestANewArray");
                Method method = cls.getMethod("method");
                @SuppressWarnings("unchecked")
                List<Integer> list = (List<Integer>) method.invoke(null);

                assertEquals(lt.util.List.class, list.getClass());

                assertEquals(10, list.get(0).intValue());
                assertEquals(20, list.get(1).intValue());
        }

        @Test
        public void test2DArray() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "class Test2DArray\n" +
                                "    static\n" +
                                "        method():[][]int=[[1,2],[],[3]]",
                        "Test2DArray");
                Method method = cls.getMethod("method");
                int[][] arr = (int[][]) method.invoke(null);
                assertArrayEquals(new int[]{1, 2}, arr[0]);
                assertArrayEquals(new int[]{}, arr[1]);
                assertArrayEquals(new int[]{3}, arr[2]);
        }

        @Test
        public void testNewMap() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "class TestNewMap\n" +
                                "    static\n" +
                                "        method()={\n" +
                                "            \"a\":1\n" +
                                "            \"b\":2" +
                                "        }",
                        "TestNewMap");
                Method method = cls.getMethod("method");
                LinkedHashMap<String, Integer> expected = new LinkedHashMap<>();
                expected.put("a", 1);
                expected.put("b", 2);
                Object o = expected;
                Object res = method.invoke(null);
                assertEquals(o, res);
                assertEquals(lt.util.Map.class, res.getClass());
        }

        @Test
        public void testAnnotation() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "import lt::compiler::_\n" +
                                "" +
                                "class TestAnnotation\n" +
                                "    static\n" +
                                "        @MyAnno(str='abc')\n" +
                                "        method():Unit",
                        "TestAnnotation");
                Method method = cls.getMethod("method");
                MyAnno myAnno = method.getDeclaredAnnotation(MyAnno.class);
                assertNotNull(myAnno);
                assertEquals("abc", myAnno.str());
                assertEquals(100, myAnno.i());
        }

        @Test
        public void testDefineSQL_DSL() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "define 'CREATE TABLE' as 'class'\n" +
                                "define 'VARCHAR' as ':String'\n" +
                                "define 'NUMBER' as ':int'\n" +
                                "CREATE TABLE User(\n" +
                                "    id NUMBER\n" +
                                "    name VARCHAR\n" +
                                ")",
                        "User");
                assertEquals("User", cls.getName());
                Constructor<?> con = cls.getDeclaredConstructors()[0];
                assertEquals(2, con.getParameterCount());
                assertEquals(int.class, con.getParameterTypes()[0]);
                assertEquals(String.class, con.getParameterTypes()[1]);
        }

        @Test
        public void testTryCatchCanLoad() throws Exception {
                retrieveClass(
                        "" +
                                "class TestTryCatchCanLoad\n" +
                                "    method():int\n" +
                                "        i:int=1\n" +
                                "        try\n" +
                                "            i=2\n" +
                                "            if i==3\n" +
                                "                i=10\n" +
                                "                return 1\n" +
                                "            else\n" +
                                "                i=11\n" +
                                "                return 2\n" +
                                "        catch e\n" +
                                "            if e is type RuntimeException\n" +
                                "                i=4\n" +
                                "                if i==5\n" +
                                "                    i=12\n" +
                                "                    return 3\n" +
                                "                else\n" +
                                "                    i=13\n" +
                                "                    return 4\n" +
                                "        finally\n" +
                                "            i=6",
                        "TestTryCatchCanLoad"
                );
        }

        @Test
        public void testInnerMethod1() throws Exception {
                Class<?> cls = retrieveClass("" +
                                "class TestInnerMethod\n" +
                                "    static\n" +
                                "        def method()\n" +
                                "            inner():int=1\n" +
                                "            return inner()",
                        "TestInnerMethod");
                assertEquals(2, cls.getDeclaredMethods().length);
                Method m = cls.getDeclaredMethod("method");
                assertEquals(1, m.invoke(null));
        }

        @Test
        public void testInnerMethod2() throws Exception {
                Class<?> cls = retrieveClass("" +
                                "class TestInnerMethod\n" +
                                "    static\n" +
                                "        def method()\n" +
                                "            i:int=1\n" +
                                "            j:int=2\n" +
                                "            inner():int=i+j\n" +
                                "            return inner()",
                        "TestInnerMethod");
                assertEquals(2, cls.getDeclaredMethods().length);
                Method m = cls.getDeclaredMethod("method");
                assertEquals(3, m.invoke(null));
        }

        @Test
        public void testInnerMethod3() throws Exception {
                Class<?> cls = retrieveClass("" +
                                "class TestInnerMethod\n" +
                                "    def method()\n" +
                                "        i:int=1\n" +
                                "        j:int=2\n" +
                                "        inner():int=i+j\n" +
                                "        return inner()",
                        "TestInnerMethod");
                assertEquals(2, cls.getDeclaredMethods().length);
                Method m = cls.getDeclaredMethod("method");
                assertEquals(3, m.invoke(cls.newInstance()));
        }

        @Test
        public void testInnerMethod4() throws Exception {
                Class<?> cls = retrieveClass("" +
                                "class TestInnerMethod\n" +
                                "    static\n" +
                                "        def method()\n" +
                                "            i:int=1\n" +
                                "            j:int=2\n" +
                                "            inner(k:int):int=i+j+k\n" +
                                "            return inner(3)",
                        "TestInnerMethod");
                assertEquals(2, cls.getDeclaredMethods().length);
                Method m = cls.getDeclaredMethod("method");
                assertEquals(6, m.invoke(null));
        }

        @Test
        public void testLambdaStatic1() throws Exception {
                Class<?> cls = retrieveClass("" +
                                "import java::util::function::_\n" +
                                "class TestLambdaStatic1\n" +
                                "    static\n" +
                                "        method():Function\n" +
                                "            return (o)->o+1",
                        "TestLambdaStatic1");
                Method m = cls.getDeclaredMethod("method");
                @SuppressWarnings("unchecked")
                Function<Object, Object> f = (Function<Object, Object>) m.invoke(null);
                assertEquals(2, f.apply(1));
        }

        @Test
        public void testLambdaStatic2() throws Exception {
                Class<?> cls = retrieveClass("" +
                                "import java::util::function::_\n" +
                                "class TestLambdaStatic2\n" +
                                "    static\n" +
                                "        method():Function\n" +
                                "            i=1\n" +
                                "            return (o)->o+1+i",
                        "TestLambdaStatic2");
                Method m = cls.getDeclaredMethod("method");
                @SuppressWarnings("unchecked")
                Function<Object, Object> f = (Function<Object, Object>) m.invoke(null);
                assertEquals(3, f.apply(1));
        }

        @Test
        public void testLambdaStatic3() throws Throwable {
                Class<?> cls = retrieveClass("" +
                                "import java::util::function::_\n" +
                                "class TestLambdaStatic3\n" +
                                "    static\n" +
                                "        def method()\n" +
                                "            i=1\n" +
                                "            return (o)->o+1+i",
                        "TestLambdaStatic3");
                Method m = cls.getDeclaredMethod("method");
                @SuppressWarnings("unchecked")
                Function1 f = (Function1) m.invoke(null);
                assertEquals(3, f.apply(1));
        }

        @Test
        public void testLambdaLT1() throws Exception {
                ErrorManager err = new ErrorManager(true);
                IndentScanner lexicalProcessor = new IndentScanner("test.lt", new StringReader("" +
                        "import lt::compiler::_\n" +
                        "class TestLambdaLT\n" +
                        "    static\n" +
                        "        method():TestLambdaFunc\n" +
                        "            i=1\n" +
                        "            return (o)->o+1+i"), new Properties(), err);
                Parser syntacticProcessor = new Parser(lexicalProcessor.scan(), err);
                Map<String, List<Statement>> map = new HashMap<>();
                map.put("test.lt", syntacticProcessor.parse());
                SemanticProcessor semanticProcessor = new SemanticProcessor(map, Thread.currentThread().getContextClassLoader(), err);
                Set<STypeDef> types = semanticProcessor.parse();

                CodeGenerator codeGenerator = new CodeGenerator(types, semanticProcessor.getTypes());
                Map<String, byte[]> list = codeGenerator.generate();

                byte[] b1 = list.get("TestLambdaLT");
                byte[] b2 = list.get("TestLambdaLT$Latte$Lambda$0");
                ClassLoader classLoader = new ClassLoader() {
                        @Override
                        protected Class<?> findClass(String name)
                                throws ClassNotFoundException {
                                if (name.equals("TestLambdaLT")) {
                                        return defineClass(name, b1, 0, b1.length);
                                } else {
                                        return defineClass(name, b2, 0, b2.length);
                                }
                        }
                };

                Class<?> TestLambdaLT = classLoader.loadClass("TestLambdaLT");
                Class<?> lambda = classLoader.loadClass("TestLambdaLT$Latte$Lambda$0");

                TestLambdaFunc func = (TestLambdaFunc) TestLambdaLT.getDeclaredMethod("method").invoke(null);
                assertEquals(3, func.apply(1));

                assertEquals(3, lambda.getDeclaredFields().length);
        }

        @Test
        public void testLambdaLT2() throws Exception {
                ErrorManager err = new ErrorManager(true);
                IndentScanner lexicalProcessor = new IndentScanner("test.lt", new StringReader("" +
                        "import lt::compiler::_\n" +
                        "class TestLambdaLT\n" +
                        "    method():TestLambdaFunc\n" +
                        "        i=1\n" +
                        "        return (o)->o+1+i"), new Properties(), err);
                Parser syntacticProcessor = new Parser(lexicalProcessor.scan(), err);
                Map<String, List<Statement>> map = new HashMap<>();
                map.put("test.lt", syntacticProcessor.parse());
                SemanticProcessor semanticProcessor = new SemanticProcessor(map, Thread.currentThread().getContextClassLoader(), err);
                Set<STypeDef> types = semanticProcessor.parse();

                CodeGenerator codeGenerator = new CodeGenerator(types, semanticProcessor.getTypes());
                Map<String, byte[]> list = codeGenerator.generate();

                byte[] b1 = list.get("TestLambdaLT");
                byte[] b2 = list.get("TestLambdaLT$Latte$Lambda$0");
                ClassLoader classLoader = new ClassLoader() {
                        @Override
                        protected Class<?> findClass(String name)
                                throws ClassNotFoundException {
                                if (name.equals("TestLambdaLT")) {
                                        return defineClass(name, b1, 0, b1.length);
                                } else {
                                        return defineClass(name, b2, 0, b2.length);
                                }
                        }
                };

                Class<?> TestLambdaLT = classLoader.loadClass("TestLambdaLT");
                Class<?> lambda = classLoader.loadClass("TestLambdaLT$Latte$Lambda$0");

                TestLambdaFunc func = (TestLambdaFunc) TestLambdaLT.getDeclaredMethod("method").invoke(TestLambdaLT.newInstance());
                assertEquals(3, func.apply(1));

                assertEquals(4, lambda.getDeclaredFields().length);
        }

        @Test
        public void testLambdaLT3() throws Throwable {
                ErrorManager err = new ErrorManager(true);
                IndentScanner lexicalProcessor = new IndentScanner("test.lt", new StringReader("" +
                        "import java::util::function::_\n" +
                        "class TestLambdaLT\n" +
                        "    method():Function\n" +
                        "        i=1\n" +
                        "        return (o)->o+1+i"), new Properties(), err);
                Parser syntacticProcessor = new Parser(lexicalProcessor.scan(), err);
                Map<String, List<Statement>> map = new HashMap<>();
                map.put("test.lt", syntacticProcessor.parse());
                SemanticProcessor semanticProcessor = new SemanticProcessor(map, Thread.currentThread().getContextClassLoader(), err);
                Set<STypeDef> types = semanticProcessor.parse();

                CodeGenerator codeGenerator = new CodeGenerator(types, semanticProcessor.getTypes());
                Map<String, byte[]> list = codeGenerator.generate();

                byte[] b1 = list.get("TestLambdaLT");
                byte[] b2 = list.get("TestLambdaLT$Latte$Lambda$0");
                ClassLoader classLoader = new ClassLoader() {
                        @Override
                        protected Class<?> findClass(String name)
                                throws ClassNotFoundException {
                                if (name.equals("TestLambdaLT")) {
                                        return defineClass(name, b1, 0, b1.length);
                                } else {
                                        return defineClass(name, b2, 0, b2.length);
                                }
                        }
                };

                Class<?> TestLambdaLT = classLoader.loadClass("TestLambdaLT");
                Class<?> lambda = classLoader.loadClass("TestLambdaLT$Latte$Lambda$0");

                Function func = (Function) TestLambdaLT.getDeclaredMethod("method").invoke(TestLambdaLT.newInstance());
                assertEquals(3, func.apply(1));

                assertEquals(4, lambda.getDeclaredFields().length);
        }

        @Test
        public void testLambdaMultipleArguments() throws Exception {
                ErrorManager err = new ErrorManager(true);
                IndentScanner lexicalProcessor = new IndentScanner("test.lt", new StringReader("" +
                        "import java::util::function::_\n" +
                        "class TestLambdaMultipleArguments\n" +
                        "    method() = (a,b,c)->a+b+c"), new Properties(), err);
                Parser syntacticProcessor = new Parser(lexicalProcessor.scan(), err);
                Map<String, List<Statement>> map = new HashMap<>();
                map.put("test.lt", syntacticProcessor.parse());
                SemanticProcessor semanticProcessor = new SemanticProcessor(map, Thread.currentThread().getContextClassLoader(), err);
                Set<STypeDef> types = semanticProcessor.parse();

                CodeGenerator codeGenerator = new CodeGenerator(types, semanticProcessor.getTypes());
                Map<String, byte[]> list = codeGenerator.generate();

                byte[] b1 = list.get("TestLambdaMultipleArguments");
                byte[] b2 = list.get("TestLambdaMultipleArguments$Latte$Lambda$0");
                ClassLoader classLoader = new ClassLoader() {
                        @Override
                        protected Class<?> findClass(String name)
                                throws ClassNotFoundException {
                                if (name.equals("TestLambdaMultipleArguments")) {
                                        return defineClass(name, b1, 0, b1.length);
                                } else {
                                        return defineClass(name, b2, 0, b2.length);
                                }
                        }
                };

                Class<?> TestLambdaLT = classLoader.loadClass("TestLambdaMultipleArguments");
                Class<?> lambda = classLoader.loadClass("TestLambdaMultipleArguments$Latte$Lambda$0");

                Function3 func = (Function3) TestLambdaLT.getDeclaredMethod("method").invoke(TestLambdaLT.newInstance());
                assertEquals(6, func.apply(1, 2, 3));

                assertEquals(4, lambda.getDeclaredFields().length);
        }

        @Test
        public void testListRemove() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "import java::util::_\n" +
                                "class TestInvokeInterface\n" +
                                "    static\n" +
                                "        def methodRemoveInt1(ls)\n" +
                                "            ls.remove(1)\n" +
                                "        def methodRemoveInteger1(ls)\n" +
                                "            ls.remove(Integer(1))",
                        "TestInvokeInterface");

                List<Integer> list = new ArrayList<>();
                list.add(1);
                list.add(2);
                list.add(3);
                Method methodRemoveInt1 = cls.getMethod("methodRemoveInt1", Object.class);
                methodRemoveInt1.invoke(null, list);
                assertEquals(Arrays.asList(1, 3), list);

                list.clear();
                list.add(1);
                list.add(2);
                list.add(3);
                Method methodRemoveInteger1 = cls.getMethod("methodRemoveInteger1", Object.class);
                methodRemoveInteger1.invoke(null, list);
                assertEquals(Arrays.asList(2, 3), list);
        }

        @Test
        public void testArrayAccess() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "class TestArrayAccess\n" +
                                "    static\n" +
                                "        arr:[]String = ['test1','test2']\n" +
                                "        def method(i,o)\n" +
                                "            arr[i]=o",
                        "TestArrayAccess");
                Field f = cls.getDeclaredField("arr");
                f.setAccessible(true);
                Method method = cls.getMethod("method", Object.class, Object.class);
                method.invoke(null, 1, "changed");
                String[] arr = (String[]) f.get(null);
                assertEquals("test1", arr[0]);
                assertEquals("changed", arr[1]);
        }

        @Test
        public void testPrimitiveType() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "class TestPrimitiveType\n" +
                                "    static\n" +
                                "        typeInt=type int\n" +
                                "        typeLong=type long\n" +
                                "        typeShort=type short\n" +
                                "        typeByte=type byte\n" +
                                "        typeChar=type char\n" +
                                "        typeBool=type bool\n" +
                                "        typeFloat=type float\n" +
                                "        typeDouble=type double\n" +
                                "        typeUnit=type Unit",
                        "TestPrimitiveType");
                Field typeInt = cls.getDeclaredField("typeInt");
                typeInt.setAccessible(true);
                Field typeLong = cls.getDeclaredField("typeLong");
                typeLong.setAccessible(true);
                Field typeShort = cls.getDeclaredField("typeShort");
                typeShort.setAccessible(true);
                Field typeByte = cls.getDeclaredField("typeByte");
                typeByte.setAccessible(true);
                Field typeChar = cls.getDeclaredField("typeChar");
                typeChar.setAccessible(true);
                Field typeBool = cls.getDeclaredField("typeBool");
                typeBool.setAccessible(true);
                Field typeFloat = cls.getDeclaredField("typeFloat");
                typeFloat.setAccessible(true);
                Field typeDouble = cls.getDeclaredField("typeDouble");
                typeDouble.setAccessible(true);
                Field typeUnit = cls.getDeclaredField("typeUnit");
                typeUnit.setAccessible(true);

                assertEquals(int.class, typeInt.get(null));
                assertEquals(long.class, typeLong.get(null));
                assertEquals(short.class, typeShort.get(null));
                assertEquals(byte.class, typeByte.get(null));
                assertEquals(char.class, typeChar.get(null));
                assertEquals(boolean.class, typeBool.get(null));
                assertEquals(float.class, typeFloat.get(null));
                assertEquals(double.class, typeDouble.get(null));
                assertEquals(void.class, typeUnit.get(null));
        }

        @Test
        public void testThrowAnyObject() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "class TestThrowAnyObject\n" +
                                "    static\n" +
                                "        def testThrow()\n" +
                                "            throw 'abc'\n" +
                                "        def testCatch(func)\n" +
                                "            try\n" +
                                "                func.apply()\n" +
                                "            catch e\n" +
                                "                return e",
                        "TestThrowAnyObject");
                Method testThrow = cls.getMethod("testThrow");
                try {
                        testThrow.invoke(null);
                        fail();
                } catch (InvocationTargetException in) {
                        Wrapper w = (Wrapper) in.getCause();
                        assertEquals("abc", w.object);
                }

                Method testCatch = cls.getMethod("testCatch", Object.class);
                assertEquals("abc", testCatch.invoke(null, (I) () -> {
                        throw new Wrapper("abc");
                }));
                assertEquals(1, testCatch.invoke(null, (I) () -> {
                        throw new Wrapper(1);
                }));
        }

        @Test
        public void testForBreak() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "class TestForBreak\n" +
                                "    static\n" +
                                "        def method()\n" +
                                "            sum=0\n" +
                                "            for i in 1..10\n" +
                                "                if i==7\n" +
                                "                    break\n" +
                                "                sum+=i\n" +
                                "            return sum",
                        "TestForBreak");
                Method method = cls.getMethod("method");
                assertEquals(21, method.invoke(null));
        }

        @Test
        public void testForContinue() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "class TestForContinue\n" +
                                "    static\n" +
                                "        def method()\n" +
                                "            sum=0\n" +
                                "            for i in 1..10\n" +
                                "                if i==7\n" +
                                "                    continue\n" +
                                "                sum+=i\n" +
                                "            return sum",
                        "TestForContinue");
                Method method = cls.getMethod("method");
                assertEquals(48, method.invoke(null));
        }

        @Test
        public void testWhileBreak() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "class TestWhileBreak\n" +
                                "    static\n" +
                                "        def method()\n" +
                                "            sum=0\n" +
                                "            i=1\n" +
                                "            while i<=10\n" +
                                "                if i==7\n" +
                                "                    break\n" +
                                "                sum+=(i++)\n" +
                                "            return sum",
                        "TestWhileBreak");
                Method method = cls.getMethod("method");
                assertEquals(21, method.invoke(null));
        }

        @Test
        public void testWhileContinue() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "class TestWhileContinue\n" +
                                "    static\n" +
                                "        def method()\n" +
                                "            sum=0\n" +
                                "            i=1\n" +
                                "            while i<=10\n" +
                                "                if i==7\n" +
                                "                    ++i\n" +
                                "                    continue\n" +
                                "                sum+=(i++)\n" +
                                "            return sum",
                        "TestWhileContinue");
                Method method = cls.getMethod("method");
                assertEquals(48, method.invoke(null));
        }

        @Test
        public void testDoWhileBreak() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "class TestWhileBreak\n" +
                                "    static\n" +
                                "        def method()\n" +
                                "            sum=0\n" +
                                "            i=1\n" +
                                "            do\n" +
                                "                if i==7\n" +
                                "                    break\n" +
                                "                sum+=(i++)\n" +
                                "            while i<=10\n" +
                                "            return sum",
                        "TestWhileBreak");
                Method method = cls.getMethod("method");
                assertEquals(21, method.invoke(null));
        }

        @Test
        public void testDoWhileContinue() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "class TestWhileContinue\n" +
                                "    static\n" +
                                "        def method()\n" +
                                "            sum=0\n" +
                                "            i=1\n" +
                                "            do\n" +
                                "                if i==7\n" +
                                "                    ++i\n" +
                                "                    continue\n" +
                                "                sum+=(i++)\n" +
                                "            while i<=10\n" +
                                "            return sum",
                        "TestWhileContinue");
                Method method = cls.getMethod("method");
                assertEquals(48, method.invoke(null));
        }

        @Test
        public void testForTryBreak() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "class TestForTryBreak\n" +
                                "    static\n" +
                                "        def method()\n" +
                                "            n=0\n" +
                                "            for i in 1..10\n" +
                                "                try\n" +
                                "                    if i==3\n" +
                                "                        break\n" +
                                "                    n+=i\n" +
                                "                finally\n" +
                                "                    ++n\n" +
                                "            return n",
                        "TestForTryBreak");
                Method method = cls.getMethod("method");
                assertEquals(6, method.invoke(null));
        }

        @Test
        public void testConsParamUseField() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "class TestConsParamUseField(i)\n" +
                                "    i=10",
                        "TestConsParamUseField");
                Object o = cls.getConstructor(Object.class).newInstance(1);
                Field i = cls.getDeclaredField("i");
                i.setAccessible(true);

                assertEquals(10, i.get(o));
        }

        @Test
        public void testMethodDefaultParam() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "class TestMethodDefaultParam\n" +
                                "    def methodNonStatic(a,b=1)\n" +
                                "        return a+b\n" +
                                "    static\n" +
                                "        def methodStatic(a,b=1)\n" +
                                "            return a-b",
                        "TestMethodDefaultParam");
                Object o = cls.newInstance();

                assertEquals(3, cls.getMethod("methodNonStatic", Object.class).invoke(o, 2));
                assertEquals(3, cls.getMethod("methodNonStatic", Object.class, Object.class).invoke(o, 2, 1));

                assertEquals(1, cls.getMethod("methodStatic", Object.class).invoke(null, 2));
                assertEquals(1, cls.getMethod("methodStatic", Object.class, Object.class).invoke(null, 2, 1));
        }

        @Test
        public void testConcatOp() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "class TestConcatOp\n" +
                                "    static\n" +
                                "        def method(a,b)\n" +
                                "            return a:::b\n" +
                                "        def method2()\n" +
                                "            return [\"a\",\"b\"]:::[\"c\"]",
                        "TestConcatOp");

                Method method = cls.getMethod("method", Object.class, Object.class);
                assertEquals("ab", method.invoke(null, "a", "b"));
                assertEquals(
                        Arrays.asList(1, 2, 3),
                        method.invoke(null, new lt.util.List(Arrays.asList(1, 2)), Collections.singletonList(3))
                );
                assertEquals(Arrays.asList("a", "b", "c"), cls.getMethod("method2").invoke(null));
        }

        @Test
        public void testInvokeMethodWithoutPar() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "class TestInvokeMethodWithoutPar\n" +
                                "    static\n" +
                                "        def method(o)\n" +
                                "            return o.toString",
                        "TestInvokeMethodWithoutPar");

                Method method = cls.getMethod("method", Object.class);
                assertEquals("[]", method.invoke(null, Collections.emptyList()));
        }

        @Test
        public void testFunctionalInterfaces() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "class TestFunctionalInterfaces\n" +
                                "    static\n" +
                                "        def method(list)\n" +
                                "            return list.stream().map((e)->e.toString).collect(java::util::stream::Collectors.toList())",
                        "TestFunctionalInterfaces");
                Method method = cls.getMethod("method", Object.class);
                assertEquals(Arrays.asList("1", "2", "3"), method.invoke(null, Arrays.asList(1, 2, 3)));
        }

        @Test
        public void testImplicitArray() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "class TestImplicitArray\n" +
                                "    static\n" +
                                "        def method()\n" +
                                "            return (type TestImplicitArray).getMethod('method', [])",
                        "TestImplicitArray");
                Method method = cls.getMethod("method");
                assertEquals(method, method.invoke(null));
        }

        @Test
        public void testFunctionalAbstractClass() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "class TestFunctionalAbstractClass\n" +
                                "    m(c:lt::compiler::F)=c\n" +
                                "    def method()\n" +
                                "        return m((e)->e)",
                        "TestFunctionalAbstractClass");
                Object TestFunctionalAbstractClass_inst = cls.newInstance();
                Method method = cls.getMethod("method");
                Object o = method.invoke(TestFunctionalAbstractClass_inst);
                assertTrue(o instanceof F);
                F f = (F) o;
                assertEquals("test", f.func("test"));
        }

        @Test
        public void testInvokeDynamicThisAndStatic() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "class TestInvokeDynamicThisAndStatic\n" +
                                "    def m(list:java::util::List)\n" +
                                "        list add 1\n" +
                                "        return list\n" +
                                "    indyThis(ls)=m(ls)\n" +
                                "    static\n" +
                                "        def mm(list:java::util::List)\n" +
                                "            list add 0\n" +
                                "            return list\n" +
                                "        indyStatic(ls)=mm(ls)",
                        "TestInvokeDynamicThisAndStatic");
                Object TestInvokeDynamicThisAndStatic_inst = cls.newInstance();
                Method indyThis = cls.getMethod("indyThis", Object.class);
                List<Integer> list = new ArrayList<>();
                list.add(3);
                list.add(2);
                assertEquals(Arrays.asList(3, 2, 1), indyThis.invoke(TestInvokeDynamicThisAndStatic_inst, list));

                Method indyStatic = cls.getMethod("indyStatic", Object.class);
                assertEquals(Arrays.asList(3, 2, 1, 0), indyStatic.invoke(null, list));
        }

        @Test
        public void testAccessElementVia$_$() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "class TestAccessElementVia$_$\n" +
                                "    static\n" +
                                "        method(list)=list._1",
                        "TestAccessElementVia$_$");

                assertEquals(2, cls.getMethod("method", Object.class).invoke(null, Arrays.asList(1, 2, 3)));
        }

        @Test
        public void testInnerMethodRecursive() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "class TestInnerMethodRecursive\n" +
                                "    static\n" +
                                "        def method(a:int,b:int)\n" +
                                "            def gcd(i:int, j:int)\n" +
                                "                if j\n" +
                                "                    return gcd(j, i % j)\n" +
                                "                else\n" +
                                "                    return i\n" +
                                "            return gcd(a,b)",
                        "TestInnerMethodRecursive");

                Method method = cls.getMethod("method", int.class, int.class);
                assertEquals(2, method.invoke(null, 8, 2));
                assertEquals(1, method.invoke(null, 1, 2));
        }

        @Test
        public void testDataClass() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "data class TestDataClass(a:int,b=1,c=2)",
                        "TestDataClass");

                Constructor<?> con = cls.getConstructor(int.class, Object.class, Object.class);
                Object instance = con.newInstance(3, 4, 5);
                Constructor<?> con2 = cls.getConstructor(int.class);
                Object instance2 = con2.newInstance(6);

                Method toStringMethod = cls.getMethod("toString");
                assertEquals("TestDataClass(a=3, b=4, c=5)", toStringMethod.invoke(instance));

                Method hashCodeMethod = cls.getMethod("hashCode");
                assertEquals(12, hashCodeMethod.invoke(instance));

                assertNotEquals(instance, instance2);
                assertEquals(con.newInstance(3, 4, 5), instance);
                assertEquals(con.newInstance(6, 1, 2), instance2);

                Constructor<?> con3 = cls.getConstructor();
                Object instance3 = con3.newInstance();
                assertEquals(con.newInstance(0, 1, 2), instance3);

                Method getA = cls.getMethod("getA");
                Method getB = cls.getMethod("getB");
                Method getC = cls.getMethod("getC");
                Method setA = cls.getMethod("setA", int.class);
                Method setB = cls.getMethod("setB", Object.class);
                Method setC = cls.getMethod("setC", Object.class);

                assertEquals(3, getA.invoke(instance));
                assertEquals(4, getB.invoke(instance));
                assertEquals(5, getC.invoke(instance));

                setA.invoke(instance, 6);
                setB.invoke(instance, 7);
                setC.invoke(instance, 8);

                assertEquals(6, getA.invoke(instance));
                assertEquals(7, getB.invoke(instance));
                assertEquals(8, getC.invoke(instance));
        }

        @Test
        public void testConstructingDataClass() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "data class User(id:int, name)\n" +
                                "class TestConstructingDataClass\n" +
                                "    static\n" +
                                "        method()=User(id=1, name='cass')",
                        "TestConstructingDataClass");
                Method method = cls.getMethod("method");
                Object o = method.invoke(null);
                Class<?> User = o.getClass();
                assertEquals("User", User.getName());

                Method getId = User.getMethod("getId");
                assertEquals(1, getId.invoke(o));

                Method getName = User.getMethod("getName");
                assertEquals("cass", getName.invoke(o));
        }

        @Test
        public void testGetterSetter() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "class User\n" +
                                "    a=1\n" +
                                "    b='cass'\n" +
                                "    getId()=a\n" +
                                "    setId(id)=a=id\n" +
                                "    getName()=b\n" +
                                "    setName(name)=b=name\n" +
                                "class TestGetterSetter\n" +
                                "    static\n" +
                                "        getUser()=User()\n" +
                                "        testIdGetter(u)=u.id\n" +
                                "        testNameGetter(u)=u.name\n" +
                                "        testIdSetter(u)=u.id=2\n" +
                                "        testNameSetter(u)=u.name='abc'",
                        "TestGetterSetter");
                Method testIdGetter = cls.getMethod("testIdGetter", Object.class);
                Method testNameGetter = cls.getMethod("testNameGetter", Object.class);

                Method getUser = cls.getMethod("getUser");
                Object u = getUser.invoke(null);

                assertEquals(1, testIdGetter.invoke(null, u));
                assertEquals("cass", testNameGetter.invoke(null, u));

                Method testIdSetter = cls.getMethod("testIdSetter", Object.class);
                testIdSetter.invoke(null, u);
                assertEquals(2, testIdGetter.invoke(null, u));

                Method testNameSetter = cls.getMethod("testNameSetter", Object.class);
                testNameSetter.invoke(null, u);
                assertEquals("abc", testNameGetter.invoke(null, u));
        }

        @Test
        public void testAllKindsOfAnnotations() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "import lt::compiler::_\n" +
                                "@lt::compiler::TestAllKindsOfAnnos(\n" +
                                "    str='theStr'\n" +
                                "    strArr=['a','b']\n" +
                                "    i=1\n" +
                                "    iArr=[1,2]\n" +
                                "    s=1\n" +
                                "    sArr=[1,2]\n" +
                                "    b=1\n" +
                                "    bArr=[1,2]\n" +
                                "    c='a'\n" +
                                "    cArr=['a','b']\n" +
                                "    bo=true\n" +
                                "    boArr=[true,false]\n" +
                                "    l=1\n" +
                                "    lArr=[1,2]\n" +
                                "    f=1\n" +
                                "    fArr=[1,2]\n" +
                                "    d=1\n" +
                                "    dArr=[1,2]\n" +
                                "    cls=type Class\n" +
                                "    clsArr=[type Class,type Object]\n" +
                                "    en=lt::compiler::semantic::SModifier.PUBLIC\n" +
                                "    enArr=[\n" +
                                "        lt::compiler::semantic::SModifier.PUBLIC\n" +
                                "        lt::compiler::semantic::SModifier.PRIVATE" +
                                "    ]\n" +
                                "    anno=@MyAnno(str='a')\n" +
                                "    annos=[@MyAnno(str='b', i=200),@MyAnno(str='c')]\n" +
                                ")\n" +
                                "class TestAllKindsOfAnnotations",
                        "TestAllKindsOfAnnotations");
                TestAllKindsOfAnnos test = cls.getAnnotation(TestAllKindsOfAnnos.class);
                assertEquals("theStr", test.str());
                assertArrayEquals(new String[]{"a", "b"}, test.strArr());
                assertEquals(1, test.i());
                assertArrayEquals(new int[]{1, 2}, test.iArr());
                assertEquals((short) 1, test.s());
                assertArrayEquals(new short[]{1, 2}, test.sArr());
                assertEquals((byte) 1, test.b());
                assertArrayEquals(new byte[]{1, 2}, test.bArr());
                assertEquals('a', test.c());
                assertArrayEquals(new char[]{'a', 'b'}, test.cArr());
                assertEquals(true, test.bo());
                assertArrayEquals(new boolean[]{true, false}, test.boArr());
                assertEquals((long) 1, test.l());
                assertArrayEquals(new long[]{1, 2}, test.lArr());
                assertEquals((float) 1, test.f(), 0);
                assertArrayEquals(new float[]{1, 2}, test.fArr(), 0);
                assertEquals((double) 1, test.d(), 0);
                assertArrayEquals(new double[]{1, 2}, test.dArr(), 0);
                assertEquals(Class.class, test.cls());
                assertArrayEquals(new Class[]{Class.class, Object.class}, test.clsArr());
                assertEquals(SModifier.PUBLIC, test.en());
                assertArrayEquals(new SModifier[]{SModifier.PUBLIC, SModifier.PRIVATE}, test.enArr());
                MyAnno myAnno = test.anno();
                assertEquals("a", myAnno.str());
                MyAnno[] myAnnos = test.annos();
                assertEquals(2, myAnnos.length);
                assertEquals("b", myAnnos[0].str());
                assertEquals(200, myAnnos[0].i());
                assertEquals("c", myAnnos[1].str());
        }

        @Test
        public void testAnnotationAnnotation() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "import lt::compiler::_\n" +
                                "@AnnoAnno(myAnno=@MyAnno(str='a'))\n" +
                                "class TestAnnotationAnnotation",
                        "TestAnnotationAnnotation");
                AnnoAnno annoAnno = cls.getAnnotation(AnnoAnno.class);
                assertEquals("a", annoAnno.myAnno().str());
        }

        @Test
        public void testAssignOp() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "data class X(x)\n" +
                                "    assign(x)=x+1\n" +
                                "class TestAssignOp\n" +
                                "    static\n" +
                                "        def method()\n" +
                                "            return X():=1\n",
                        "TestAssignOp");
                Method method = cls.getMethod("method");
                assertEquals(2, method.invoke(null));
        }

        @Test
        public void testIndexAccessAssign() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "class TestIndexAccessAssign\n" +
                                "    static\n" +
                                "        def method(arr)\n" +
                                "            i=0\n" +
                                "            a=0\n" +
                                "            while i<arr.size\n" +
                                "                a+=arr[i++]\n" +
                                "            def m()\n" +
                                "                return 1+1\n" +
                                "            return a"
                        , "TestIndexAccessAssign");
                Method method = cls.getMethod("method", Object.class);
                assertEquals(3, method.invoke(null, Arrays.asList(1, 2)));
        }

        @Test
        public void testDynamicConstruct() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "import lt::util::List\n" +
                                "class TestDynamicConstruct\n" +
                                "    static\n" +
                                "        def method()\n" +
                                "            a=[1]\n" +
                                "            return List(a)"
                        , "TestDynamicConstruct"
                );
                Method method = cls.getMethod("method");
                assertEquals(Collections.singletonList(1), method.invoke(null));
        }

        @Test
        public void testFun() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "fun TestFun(o):lt::compiler::F\n" +
                                "    return o+1"
                        , "TestFun"
                );
                F f = (F) cls.newInstance();
                assertEquals(3, f.func(2));
        }

        @Test
        public void testFunAnno() throws Exception {
                Class<?> cls = retrieveClass("" +
                                "fun TestFunAnno(o)\n" +
                                "    return o+1"
                        , "TestFunAnno");
                assertTrue(cls.isAnnotationPresent(LatteFun.class));
        }

        @Test
        public void testGetFunWithTypeName() throws Exception {
                Class<?> cls = retrieveClass("" +
                                "fun TheFun(o)\n" +
                                "    return o + 1\n" +
                                "class TestGetFunWithTypeName\n" +
                                "    static\n" +
                                "        method()=TheFun"
                        , "TestGetFunWithTypeName");
                Method method = cls.getMethod("method");
                Function1 f1 = (Function1) method.invoke(null);
                assertEquals(3, f1.apply(2));
        }

        @Test
        public void testCallFunWithTypeName() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "fun TheFun(o)\n" +
                                "    return o+1\n" +
                                "class TestCallFunWithTypeName\n" +
                                "    static\n" +
                                "        method()=TheFun(3)"
                        , "TestCallFunWithTypeName"
                );
                Method method = cls.getMethod("method");
                assertEquals(4, method.invoke(null));
        }

        @Test
        public void testTypeAccess() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "class A\n" +
                                "    static\n" +
                                "        a=1\n" +
                                "    b = 2\n" +
                                "    static\n" +
                                "        m1()=A + 1\n" +
                                "        m2()=A() + 1\n" +
                                "        m3()=A.a + 1\n" +
                                "        m4()=A().b + 1\n" +
                                "        m5()=a + 1\n" +
                                "\n" +
                                "    add(o)=10+o"
                        , "A");
                Method m1 = cls.getMethod("m1");
                Method m2 = cls.getMethod("m2");
                Method m3 = cls.getMethod("m3");
                Method m4 = cls.getMethod("m4");
                Method m5 = cls.getMethod("m5");

                assertEquals(11, m1.invoke(null));
                assertEquals(11, m2.invoke(null));
                assertEquals(2, m3.invoke(null));
                assertEquals(3, m4.invoke(null));
                assertEquals(2, m5.invoke(null));
        }

        @Test
        public void testRequire() throws Throwable {
                ScriptCompiler scriptCompiler = new ScriptCompiler(ClassLoader.getSystemClassLoader());
                ScriptCompiler.Script script = scriptCompiler.compile(
                        "script",
                        "return require('cp:test_require'+'.lts')"
                );
                Object res = script.run().getResult();
                assertEquals(2, res);
        }

        @Test
        public void test3DArray() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "class Test3DArray\n" +
                                "    static\n" +
                                "        get(o,i,j,k)=o[i,j,k]\n" +
                                "        set(o,i,j,k,v)=o[i,j,k]=v"
                        , "Test3DArray"
                );

                int[][][] arr = {
                        {
                                {1, 2, 3},
                                {4, 5}
                        },
                        {
                                {6, 7, 8, 9},
                                {},
                                {10}
                        },
                        {
                                {},
                                {11, 12}
                        }
                };

                Method get = cls.getMethod("get", Object.class, Object.class, Object.class, Object.class);
                assertEquals(5, get.invoke(null, arr, 0, 1, 1));
                assertEquals(10, get.invoke(null, arr, 1, 2, 0));
                assertEquals(11, get.invoke(null, arr, 2, 1, 0));
                assertEquals(7, get.invoke(null, arr, 1, 0, 1));
                Method set = cls.getMethod("set", Object.class, Object.class, Object.class, Object.class, Object.class);
                set.invoke(null, arr, 0, 1, 1, 13);
                assertEquals(13, arr[0][1][1]);

                set.invoke(null, arr, 1, 2, 0, 14);
                assertEquals(14, arr[1][2][0]);

                set.invoke(null, arr, 2, 1, 0, 15);
                assertEquals(15, arr[2][1][0]);

                set.invoke(null, arr, 1, 0, 1, 16);
                assertEquals(16, arr[1][0][1]);

                Object[][] arr2 = {
                        {new lt.util.List($this -> {
                                List ls = (List) $this;
                                ls.add(1);
                                ls.add(2);
                                ls.add(3);
                                return null;
                        })}
                };

                assertEquals(2, get.invoke(null, arr2, 0, 0, 1));
                set.invoke(null, arr2, 0, 0, 1, 10);
                assertEquals(10, get.invoke(null, arr2, 0, 0, 1));
        }

        @Test
        public void testRegex() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "class TestRegex\n" +
                                "    static\n" +
                                "        def method1()\n" +
                                "            return //a\\bc//\n" +
                                "        def method2()\n" +
                                "            return //a\\//b//"
                        , "TestRegex"
                );
                Method method1 = cls.getMethod("method1");
                Pattern pattern1 = (Pattern) method1.invoke(null);
                assertEquals("a\\bc", pattern1.pattern());

                Method method2 = cls.getMethod("method2");
                Pattern pattern2 = (Pattern) method2.invoke(null);
                assertEquals("a//b", pattern2.pattern());
        }

        @Test
        public void testCall() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "import java::util::Arrays\n" +
                                "class C\n" +
                                "    static\n" +
                                "        def call(o,m:String,b:[]bool,args:[]Object)\n" +
                                "            return '' + (o==null) +\n" +
                                "            m +\n" +
                                "            Arrays.toString(b) +\n" +
                                "            Arrays.toString(args)\n" +
                                "class TestCall\n" +
                                "    static\n" +
                                "        def method()\n" +
                                "            c=C\n" +
                                "            return c.go(1,1.2,'abc')"
                        , "TestCall"
                );
                // whether o == null , method name , is primitive , arguments
                assertEquals("falsego[true, true, false][1, 1.2, abc]", cls.getMethod("method").invoke(null));
        }

        @Test
        public void testHandlingVoidValues() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "class TestHandlingVoidValues\n" +
                                "    println().a\n" + // get field
                                "    println().invoke()\n" + // method
                                "    println() + 1\n" + // operator
                                "    invoke(println())\n" + // arg
                                "    1 + println()" // operator right
                        , "TestHandlingVoidValues"
                );
                cls.getConstructors();
                // compiling pass
        }

        @Test
        public void testKeyNew() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "class C(public i:int=1)\n" +
                                "" +
                                "class TestKeyNew\n" +
                                "    static\n" +
                                "        test1()=new C\n" +
                                "        test2()=new C(2)\n" +
                                "        test3(o)=new C(o)"
                        , "TestKeyNew");
                Method test1 = cls.getMethod("test1");
                Object test1obj = test1.invoke(null);
                assertEquals("C", test1obj.getClass().getName());
                Field i = test1obj.getClass().getField("i");
                assertEquals(1, i.getInt(test1obj));

                Method test2 = cls.getMethod("test2");
                Object test2obj = test2.invoke(null);
                assertEquals(2, i.getInt(test2obj));

                Method test3 = cls.getMethod("test3", Object.class);
                Object test3obj = test3.invoke(null, 3);
                assertEquals(3, i.getInt(test3obj));
        }

        @Test
        public void testGeneratorSpec() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "class TestGeneratorSpec\n" +
                                "    static\n" +
                                "        method1()=#js\n" +
                                "            a=1\n" +
                                "            b=2\n" +
                                "        method2(i)=(\n" +
                                "            #js\n" +
                                "                a=1\n" +
                                "        ).charAt(i)"
                        , "TestGeneratorSpec");
                Method method1 = cls.getMethod("method1");
                assertEquals("var a = 1;\nvar b = 2;", method1.invoke(null));

                Method method2 = cls.getMethod("method2", Object.class);
                assertEquals('v', (char) method2.invoke(null, 0));
                assertEquals('a', (char) method2.invoke(null, 1));
                assertEquals('r', (char) method2.invoke(null, 2));
        }

        @Test
        public void testFunctionalObject() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "class TestFunctionalObject\n" +
                                "    static\n" +
                                "        b = ()->2\n" +
                                "        def method1()\n" +
                                "            a = ()->1\n" +
                                "            return a()\n" +
                                "        def method2()\n" +
                                "            return b()\n" +
                                "    c = ()->3\n" +
                                "    def method3()\n" +
                                "        return c()"
                        , "TestFunctionalObject");
                Method method1 = cls.getMethod("method1");
                assertEquals(1, method1.invoke(null));

                Method method2 = cls.getMethod("method2");
                assertEquals(2, method2.invoke(null));

                Method method3 = cls.getMethod("method3");
                assertEquals(3, method3.invoke(cls.newInstance()));
        }

        @Test
        public void testFunctionalObjectFromOtherClass() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "class TestFunctionalObjectFromOtherClass\n" +
                                "    static\n" +
                                "        method1()=(new Functions).a()\n" +
                                "        method2()=Functions.b()\n" +
                                "class Functions\n" +
                                "    public a = ()->1\n" +
                                "    static\n" +
                                "        public b = ()->2"
                        , "TestFunctionalObjectFromOtherClass");

                Method method1 = cls.getMethod("method1");
                assertEquals(1, method1.invoke(null));

                Method method2 = cls.getMethod("method2");
                assertEquals(2, method2.invoke(null));
        }

        @Test
        public void testFunctionalObjectWithArgs() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "class TestFunctionalObject\n" +
                                "    static\n" +
                                "        def method(n)\n" +
                                "            a = (x,y)->x+1+y\n" +
                                "            return a(n,3)"
                        , "TestFunctionalObject");
                Method method = cls.getMethod("method", Object.class);
                assertEquals(6, method.invoke(null, 2));
        }

        @Test
        public void testCallingReverse() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "class X\n" +
                                "    reverse_add(n) = n + 10\n" +
                                "class TestCallingReverse\n" +
                                "    static\n" +
                                "        def method()\n" +
                                "            return 1 + X()"
                        , "TestCallingReverse"
                );
                Method method = cls.getMethod("method");
                assertEquals(11, method.invoke(null));
        }

        @Test
        public void testFunctionalObjectUpgradeVersion() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "class TestFunctionalObjectUpgradeVersion\n" +
                                "    static\n" +
                                "        def method1(n)\n" +
                                "            a = (x,y)->(z)->x+y+z\n" +
                                "            return a(n,3)(2)\n" +
                                "        def method2(o)\n" +
                                "            return o[0](3)"
                        , "TestFunctionalObjectUpgradeVersion");
                Method method1 = cls.getMethod("method1", Object.class);
                assertEquals(7, method1.invoke(null, 2));
                Method method2 = cls.getMethod("method2", Object.class);
                assertEquals(4, method2.invoke(null,
                        (Object) new Function[]{(Function<Integer, Object>) o -> o + 1}));
        }

        @Test
        public void testOperatorAssign() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "class TestOperatorAssign\n" +
                                "    static\n" +
                                "        def method()\n" +
                                "            i = 2\n" +
                                "            i <<= 3\n" +
                                "            return i"
                        , "TestOperatorAssign");
                Method method1 = cls.getMethod("method");
                assertEquals(16, method1.invoke(null));
        }

        @Test
        public void testInternalSyntaxLambda() throws Exception {
                Class<?> cls = retrieveClass(
                        "" +
                                "class TestInternalSyntaxLambda\n" +
                                "    static\n" +
                                "        def method()\n" +
                                "            return T()\n" +
                                "                return 1 + 2\n" +
                                "class T\n" +
                                "    apply(o)=o(this)"
                        , "TestInternalSyntaxLambda");
                Method method = cls.getMethod("method");
                assertEquals(3, method.invoke(null));
        }

        public static class ASTGen implements SourceGenerator {
                private Statement stmt;

                @Override
                public void init(List<Statement> ast, SemanticProcessor processor, SemanticScope scope, LineCol lineCol, ErrorManager err) {
                        this.stmt = ast.get(0);
                }

                @Override
                public Object generate() throws SyntaxException {
                        return new TwoVariableOperation(
                                "+",
                                new NumberLiteral("1", LineCol.SYNTHETIC),
                                (Expression) stmt,
                                LineCol.SYNTHETIC
                        );
                }

                @Override
                public int resultType() {
                        return EXPRESSION;
                }
        }

        @Test
        public void testGenerator_AST() throws Exception {
                Class<?> cls = retrieveClass("" +
                                "class TestGenerator_AST\n" +
                                "    static\n" +
                                "        method()=#" + ASTGen.class.getName().replace(".", "::") + "\n" +
                                "            123"
                        , "TestGenerator_AST");
                Method method = cls.getMethod("method");
                assertEquals(124, method.invoke(null));
        }

        @Test
        public void testGenerator_Serialize() throws Throwable {
                Class<?> cls = retrieveClass("" +
                                "class TestGenerator_Serialize\n" +
                                "    static\n" +
                                "        method()=#ast\n" +
                                "            1+2"
                        , "TestGenerator_Serialize");
                Method method = cls.getMethod("method");
                assertEquals(Collections.singletonList(new TwoVariableOperation("+",
                        new NumberLiteral("1", LineCol.SYNTHETIC),
                        new NumberLiteral("2", LineCol.SYNTHETIC),
                        LineCol.SYNTHETIC)), method.invoke(null));
        }

        @Test
        public void testGenerator_in_one_line() throws Exception {
                Class<?> cls = retrieveClass("" +
                                "class TestGenerator_in_one_line\n" +
                                "    static\n" +
                                "        method()=#ast#1+2"
                        , "TestGenerator_in_one_line");
                Method method = cls.getMethod("method");
                assertEquals(Collections.singletonList(new TwoVariableOperation("+",
                        new NumberLiteral("1", LineCol.SYNTHETIC),
                        new NumberLiteral("2", LineCol.SYNTHETIC),
                        LineCol.SYNTHETIC)), method.invoke(null));
        }

        @Test
        public void testArrayFieldIndex() throws Exception {
                Class<?> cls = retrieveClass("" +
                                "class TestArrayFieldIndex\n" +
                                "    static\n" +
                                "        method(o)=o._1"
                        , "TestArrayFieldIndex");
                Method method = cls.getMethod("method", Object.class);
                assertEquals(2, method.invoke(null, (Object) new int[]{1, 2, 3}));
        }

        @Test
        public void testStringExpression() throws Exception {
                Class<?> cls = retrieveClass("" +
                                ";; :scanner-brace\n" +
                                "class TestStringExpression {\n" +
                                "  static {\n" +
                                "    test1()=\"abc\"\n" + // normal string
                                "    test2()=\"abc${1}\"\n" + // exp at the end
                                "    test3()=\"${1}abc\"\n" + // exp at the start
                                "    test4()=\"ab${1}c\"\n" + // exp at the middle
                                "    test5()=\"ab${test1()}c\"\n" + // exp outside
                                "    def test6() {\n" +
                                "      \"ab${x=1}c\"\n" +
                                "      return x\n" +
                                "    }\n" + // exp define a variable
                                "  }\n" +
                                "}"
                        , "TestStringExpression");
                Method test = cls.getMethod("test1");
                assertEquals("abc", test.invoke(null));
                test = cls.getMethod("test2");
                assertEquals("abc1", test.invoke(null));
                test = cls.getMethod("test3");
                assertEquals("1abc", test.invoke(null));
                test = cls.getMethod("test4");
                assertEquals("ab1c", test.invoke(null));
                test = cls.getMethod("test5");
                assertEquals("ababcc", test.invoke(null));
                test = cls.getMethod("test6");
                assertEquals(1, test.invoke(null));
        }

        @Test
        public void testOrReturnsObject() throws Exception {
                Class<?> cls = retrieveClass("" +
                                "class TestOrReturnsObject\n" +
                                "    static" +
                                "        method(a, b, c)=a || b || c"
                        , "TestOrReturnsObject");
                Method method = cls.getMethod("method", Object.class, Object.class, Object.class);
                assertEquals(10, method.invoke(null, 10, 20, 30));
                assertEquals(20, method.invoke(null, 0, 20, 30));
                assertEquals(30, method.invoke(null, 0, 0, 30));
        }

        @Test
        public void testCompileMultipleIndexAccess() throws Exception {
                Class<?> cls = retrieveClass("" +
                                "class TestCompileMultipleIndexAccess\n" +
                                "    static\n" +
                                "        def method()\n" +
                                "            arr:[][]int = [[1,2],[3,4]]\n" +
                                "            return arr[1,0]"
                        , "TestCompileMultipleIndexAccess");
                Method method = cls.getMethod("method");
                assertEquals(3, method.invoke(null));
        }

        @Test
        public void testArrayMap() throws Exception {
                Class<?> cls = retrieveClass("" +
                                "class TestArrayMap\n" +
                                "    static\n" +
                                "        method()=[\"a\":1, \"b\":2]"
                        , "TestArrayMap");
                Method method = cls.getMethod("method");
                LinkedHashMap<String, Integer> map = new LinkedHashMap<>();
                map.put("a", 1);
                map.put("b", 2);
                assertEquals(map, method.invoke(null));
        }

        @Test
        public void testArrayMap2() throws Exception {
                Class<?> cls = retrieveClass("" +
                                ";; :scanner-brace\n" +
                                "class TestArrayMap2 {\n" +
                                "    static {\n" +
                                "        method()=[\"a\":1, \"b\":2]\n" +
                                "    }\n" +
                                "}"
                        , "TestArrayMap2");
                Method method = cls.getMethod("method");
                LinkedHashMap<String, Integer> map = new LinkedHashMap<>();
                map.put("a", 1);
                map.put("b", 2);
                assertEquals(map, method.invoke(null));
        }

        @Test
        public void testAutoReturn() throws Exception {
                Class<?> cls = retrieveClass("" +
                                "class TestAutoReturn\n" +
                                "    static\n" +
                                "        def method()\n" +
                                "            1"
                        , "TestAutoReturn");
                Method method = cls.getMethod("method");
                assertEquals(1, method.invoke(null));
        }

        @Test
        public void testAutoReturnWithType() throws Exception {
                Class<?> cls = retrieveClass("" +
                                "class TestAutoReturnWithType\n" +
                                "    static\n" +
                                "        method1():int\n" +
                                "            1\n" +
                                "        method2():Unit\n" +
                                "            Object()"
                        , "TestAutoReturnWithType");
                Method method1 = cls.getMethod("method1");
                assertEquals(1, method1.invoke(null));

                Method method2 = cls.getMethod("method2");
                assertEquals(null, method2.invoke(null));
        }

        @Test
        public void testAutoReturnIf() throws Exception {
                Class<?> cls = retrieveClass("" +
                                ";; :scanner-brace\n" +
                                "class TestAutoReturnIf {\n" +
                                "    static {\n" +
                                "        def method(a) {\n" +
                                "            if a { 1 } else { 2 }\n" +
                                "        }\n" +
                                "    }\n" +
                                "}"
                        , "TestAutoReturnIf");
                Method method = cls.getMethod("method", Object.class);
                assertEquals(1, method.invoke(null, true));
                assertEquals(2, method.invoke(null, false));
        }

        @Test
        public void testBraceLambda() throws Exception {
                Class<?> cls = retrieveClass("" +
                                ";; :scanner-brace\n" +
                                "class TestBraceLambda {\n" +
                                "    static {\n" +
                                "        def method() {\n" +
                                "            x = 1\n" +
                                "            f = ()->2\n" +
                                "            x + f()\n" +
                                "        }\n" +
                                "    }\n" +
                                "}"
                        , "TestBraceLambda");
                Method method = cls.getMethod("method");
                assertEquals(3, method.invoke(null));
        }

        @Test
        public void testBraceInternalSyntaxLambda() throws Exception {
                Class<?> cls = retrieveClass("" +
                                ";; :scanner-brace\n" +
                                "import java::util::stream::Collectors._\n" +
                                "class TestBraceInternalSyntaxLambda {\n" +
                                "    static {\n" +
                                "        method()= [1, 2, 3, 4].stream.filter{it > 2}.map{it + 1}.collect(toList())" +
                                "    }\n" +
                                "}"
                        , "TestBraceInternalSyntaxLambda");
                Method method = cls.getMethod("method");
                assertEquals(Arrays.asList(4, 5), method.invoke(null));
        }

        @Test
        public void testIndentInternalSyntaxLambda() throws Exception {
                Class<?> cls = retrieveClass("" +
                                "import java::util::stream::Collectors._\n" +
                                "class TestIndentInternalSyntaxLambda\n" +
                                "    static\n" +
                                "        method()=\n" +
                                "        [1, 2, 3, 4].stream.filter|-it > 2-|.map|-it + 1-|.collect(toList())"
                        , "TestIndentInternalSyntaxLambda");
                Method method = cls.getMethod("method");
                assertEquals(Arrays.asList(4, 5), method.invoke(null));
        }

        @Test
        public void testInnerMethodChangeParam() throws Exception {
                Class<?> cls = retrieveClass("" +
                                "class TestInnerMethodChangeParam\n" +
                                "    static\n" +
                                "        def method(a)\n" +
                                "            def inner()\n" +
                                "                a=2\n" +
                                "            inner()\n" +
                                "            return a"
                        , "TestInnerMethodChangeParam");
                Method method = cls.getMethod("method", Object.class);
                assertEquals(2, method.invoke(null, new Object()));
        }

        @Test
        public void testInnerMethodContainRealType() throws Exception {
                Class<?> cls = retrieveClass("" +
                                "class TestInnerMethodContainRealType\n" +
                                "    static\n" +
                                "        def method(a:int)\n" +
                                "            def inner()\n" +
                                "                a=Object()\n" +
                                "            inner()\n" +
                                "            return a"
                        , "TestInnerMethodContainRealType");
                Method method = cls.getMethod("method", int.class);
                try {
                        method.invoke(null, 1);
                        fail();
                } catch (Exception e) {
                        if (e instanceof InvocationTargetException) {
                                InvocationTargetException i = (InvocationTargetException) e;
                                Throwable t = i.getTargetException();
                                if (!(t instanceof ClassCastException)) {
                                        fail();
                                } // cast object to int fail
                        } else {
                                fail();
                        }
                }
        }

        @Test
        public void testLambdaCallSelf() throws Exception {
                Class<?> cls = retrieveClass("" +
                                "class TestLambdaCallSelf\n" +
                                "    static\n" +
                                "        def method(x)\n" +
                                "            var count = 0\n" +
                                "            var f = a->\n" +
                                "                if a > 2\n" +
                                "                    return null\n" +
                                "                count ++\n" +
                                "                f(a+1)\n" +
                                "            f(x)\n" +
                                "            return count"
                        , "TestLambdaCallSelf");
                Method method = cls.getMethod("method", Object.class);
                assertEquals(3, method.invoke(null, 0));
        }

        @Test
        public void testLambdaCallSelfVal() throws Exception {
                Class<?> cls = retrieveClass("" +
                                "class TestLambdaCallSelfVal\n" +
                                "    static\n" +
                                "        def method(x)\n" +
                                "            var count = 0\n" +
                                "            val f = a->\n" +
                                "                if a > 2\n" +
                                "                    return null\n" +
                                "                count ++\n" +
                                "                f(a+1)\n" +
                                "            f(x)\n" +
                                "            return count"
                        , "TestLambdaCallSelfVal");
                Method method = cls.getMethod("method", Object.class);
                assertEquals(2, method.invoke(null, 1));
        }

        @Test
        public void testLambdaCallSelfField() throws Exception {
                Class<?> cls = retrieveClass("" +
                                "class TestLambdaCallSelfField\n" +
                                "    static\n" +
                                "        count = 0\n" +
                                "        f = a->\n" +
                                "            if a > 2\n" +
                                "                return null\n" +
                                "            count ++\n" +
                                "            f(a+1)\n" +
                                "        def method(x)\n" +
                                "            f(x)\n" +
                                "            return count"
                        , "TestLambdaCallSelfField");
                Method method = cls.getMethod("method", Object.class);
                assertEquals(4, method.invoke(null, -1));
        }

        @Test
        public void testMethodWithoutPar() throws Exception {
                Class<?> cls = retrieveClass("" +
                        "class TestMethodWithoutPar\n" +
                        "    def m\n" +
                        "    def n\n" +
                        "        ...\n" +
                        "    def o:int\n" +
                        "    def p:int\n" +
                        "        ...\n" +
                        "    def q=1\n" +
                        "    def r:int=1", "TestMethodWithoutPar");
                Method m = cls.getMethod("m");
                Method n = cls.getMethod("n");
                Method o = cls.getMethod("o");
                Method p = cls.getMethod("p");
                Method q = cls.getMethod("q");
                Method r = cls.getMethod("r");

                Consumer<Method> noParam = method -> assertEquals(0, method.getParameterCount());
                noParam.accept(m);
                noParam.accept(n);
                noParam.accept(o);
                noParam.accept(p);
                noParam.accept(q);
                noParam.accept(r);

                Consumer<Method> returnInt = method -> assertEquals(int.class, method.getReturnType());
                returnInt.accept(o);
                returnInt.accept(p);
                returnInt.accept(r);
        }

        @Test
        public void testNotNull() throws Exception {
                Class<?> cls = retrieveClass("" +
                                "class TestNotNull\n" +
                                "    static\n" +
                                "        def method(nonnull a, nonnull b)= a + b"
                        , "TestNotNull");
                Method method = cls.getMethod("method", Object.class, Object.class);
                assertEquals(3, method.invoke(null, 1, 2));
                try {
                        method.invoke(null, null, 1);
                        fail();
                } catch (InvocationTargetException e) {
                        assertTrue(e.getTargetException() instanceof NullPointerException);
                }
                try {
                        method.invoke(null, 1, null);
                        fail();
                } catch (InvocationTargetException e) {
                        assertTrue(e.getTargetException() instanceof NullPointerException);
                }
                try {
                        method.invoke(null, null, null);
                        fail();
                } catch (InvocationTargetException e) {
                        assertTrue(e.getTargetException() instanceof NullPointerException);
                }
                try {
                        method.invoke(null, Unit.get(), 1);
                        fail();
                } catch (InvocationTargetException e) {
                        assertTrue(e.getTargetException() instanceof IllegalArgumentException);
                }
                try {
                        method.invoke(null, 1, Unit.get());
                        fail();
                } catch (InvocationTargetException e) {
                        assertTrue(e.getTargetException() instanceof IllegalArgumentException);
                }
                try {
                        method.invoke(null, Unit.get(), Unit.get());
                        fail();
                } catch (InvocationTargetException e) {
                        assertTrue(e.getTargetException() instanceof IllegalArgumentException);
                }
        }

        @Test
        public void testNotEmpty() throws Exception {
                Class<?> cls = retrieveClass("" +
                                "class TestNotEmpty\n" +
                                "    static\n" +
                                "        def method(nonempty a, nonempty b)= a + b"
                        , "TestNotEmpty");
                Method method = cls.getMethod("method", Object.class, Object.class);
                assertEquals(3, method.invoke(null, 1, 2));
                try {
                        method.invoke(null, 0, 1);
                        fail();
                } catch (InvocationTargetException e) {
                        assertTrue(e.getTargetException() instanceof IllegalArgumentException);
                }
                try {
                        method.invoke(null, 1, 0);
                        fail();
                } catch (InvocationTargetException e) {
                        assertTrue(e.getTargetException() instanceof IllegalArgumentException);
                }
                try {
                        method.invoke(null, 0, 0);
                        fail();
                } catch (InvocationTargetException e) {
                        assertTrue(e.getTargetException() instanceof IllegalArgumentException);
                }
        }

        @Test
        public void testNotNullCons() throws Exception {
                Class<?> cls = retrieveClass("" +
                                "class TestNotNullCons(nonnull a, nonnull b)"
                        , "TestNotNullCons");
                Constructor<?> cons = cls.getConstructor(Object.class, Object.class);
                cons.newInstance(1, 2); // pass
                try {
                        cons.newInstance(null, 1);
                        fail();
                } catch (InvocationTargetException e) {
                        assertTrue(e.getTargetException() instanceof NullPointerException);
                }
                try {
                        cons.newInstance(1, null);
                        fail();
                } catch (InvocationTargetException e) {
                        assertTrue(e.getTargetException() instanceof NullPointerException);
                }
                try {
                        cons.newInstance(null, null);
                        fail();
                } catch (InvocationTargetException e) {
                        assertTrue(e.getTargetException() instanceof NullPointerException);
                }
                try {
                        cons.newInstance(Unit.get(), 1);
                        fail();
                } catch (InvocationTargetException e) {
                        assertTrue(e.getTargetException() instanceof IllegalArgumentException);
                }
                try {
                        cons.newInstance(1, Unit.get());
                        fail();
                } catch (InvocationTargetException e) {
                        assertTrue(e.getTargetException() instanceof IllegalArgumentException);
                }
                try {
                        cons.newInstance(Unit.get(), Unit.get());
                        fail();
                } catch (InvocationTargetException e) {
                        assertTrue(e.getTargetException() instanceof IllegalArgumentException);
                }
        }

        @Test
        public void testNotEmptyCons() throws Exception {
                Class<?> cls = retrieveClass("" +
                                "class TestNotEmptyCons(nonempty a, nonempty b)"
                        , "TestNotEmptyCons");
                Constructor<?> cons = cls.getConstructor(Object.class, Object.class);
                cons.newInstance(1, 2); // pass
                try {
                        cons.newInstance(0, 1);
                        fail();
                } catch (InvocationTargetException e) {
                        assertTrue(e.getTargetException() instanceof IllegalArgumentException);
                }
                try {
                        cons.newInstance(1, 0);
                        fail();
                } catch (InvocationTargetException e) {
                        assertTrue(e.getTargetException() instanceof IllegalArgumentException);
                }
                try {
                        cons.newInstance(0, 0);
                        fail();
                } catch (InvocationTargetException e) {
                        assertTrue(e.getTargetException() instanceof IllegalArgumentException);
                }
        }

        @Test
        public void testNullEmptyCapability() throws Exception {
                retrieveClass("" +
                                "fun TestNullEmptyCapability(nonnull a, nonempty b)\n" +
                                "    (nonnull c, nonempty d)->a+b+c+d\n" +
                                "    def xx(nonnull e, nonempty f)=1"
                        , "TestNullEmptyCapability");
        }

        @Test
        public void testObject() throws Exception {
                Class<?> cls = retrieveClass("" +
                                "object TestObject\n" +
                                "    public a=1\n" +
                                "    def method=2\n" +
                                "    static\n" +
                                "        public val b=3\n" +
                                "        def staticMethod=4"
                        , "TestObject");
                Constructor<?> cons = cls.getDeclaredConstructor();
                assertTrue(Modifier.isPrivate(cons.getModifiers()));
                Field singletonInstance = cls.getField("singletonInstance");
                Object o = singletonInstance.get(null);

                Field a = cls.getField("a");
                Method method = cls.getMethod("method");
                Field b = cls.getField("b");
                Method staticMethod = cls.getMethod("staticMethod");
                assertEquals(1, a.get(o));
                assertEquals(2, method.invoke(o));
                assertEquals(3, b.get(null));
                assertEquals(4, staticMethod.invoke(null));
        }

        @Test
        public void testConstructSingletonObject() throws Exception {
                Class<?> cls = retrieveClass("" +
                                "object Test\n" +
                                "class TestConstructSingletonObject\n" +
                                "    static\n" +
                                "        def method=Test\n" +
                                "        def getCls = type Test"
                        , "TestConstructSingletonObject");
                Method method = cls.getMethod("method");

                Object o1 = method.invoke(null);

                Method getCls = cls.getMethod("getCls");
                Class<?> Test = (Class<?>) getCls.invoke(null);
                Field f = Test.getField("singletonInstance");
                Object o = f.get(null);

                assertTrue(o1 == o);
        }

        @Test
        public void testVarNonnull() throws Exception {
                Class<?> cls = retrieveClass("" +
                                "class TestVarNonnull\n" +
                                "    static\n" +
                                "        def method(a, b)\n" +
                                "            nonnull c = a\n" +
                                "            nonnull d = b"
                        , "TestVarNonnull");
                Method method = cls.getMethod("method", Object.class, Object.class);
                method.invoke(null, 1, 2);

                try {
                        method.invoke(null, null, 2);
                        fail();
                } catch (InvocationTargetException e) {
                        assertTrue(e.getTargetException() instanceof NullPointerException);
                }
                try {
                        method.invoke(null, 1, null);
                        fail();
                } catch (InvocationTargetException e) {
                        assertTrue(e.getTargetException() instanceof NullPointerException);
                }
                try {
                        method.invoke(null, Unit.get(), 2);
                        fail();
                } catch (InvocationTargetException e) {
                        assertTrue(e.getTargetException() instanceof IllegalArgumentException);
                }
                try {
                        method.invoke(null, 1, Unit.get());
                        fail();
                } catch (InvocationTargetException e) {
                        assertTrue(e.getTargetException() instanceof IllegalArgumentException);
                }
        }

        @Test
        public void testVarNonempty() throws Exception {
                Class<?> cls = retrieveClass("" +
                                "class TestVarNonempty\n" +
                                "    static\n" +
                                "        def method(a, b)\n" +
                                "            nonempty c = a\n" +
                                "            nonempty d = b"
                        , "TestVarNonempty");
                Method method = cls.getMethod("method", Object.class, Object.class);
                method.invoke(null, 1, 2);

                try {
                        method.invoke(null, null, 2);
                        fail();
                } catch (InvocationTargetException e) {
                        assertTrue(e.getTargetException() instanceof IllegalArgumentException);
                }
                try {
                        method.invoke(null, 1, null);
                        fail();
                } catch (InvocationTargetException e) {
                        assertTrue(e.getTargetException() instanceof IllegalArgumentException);
                }
                try {
                        method.invoke(null, Unit.get(), 2);
                        fail();
                } catch (InvocationTargetException e) {
                        assertTrue(e.getTargetException() instanceof IllegalArgumentException);
                }
                try {
                        method.invoke(null, 1, Unit.get());
                        fail();
                } catch (InvocationTargetException e) {
                        assertTrue(e.getTargetException() instanceof IllegalArgumentException);
                }
        }

        @Test
        public void testLambdaGetSelf() throws Exception {
                Class<?> cls = retrieveClass("" +
                                "class TestLambdaGetSelf\n" +
                                "    static\n" +
                                "        method1()=()->$\n" +
                                "    method2()=()->$"
                        , "TestLambdaGetSelf");
                Method method1 = cls.getMethod("method1");
                Function0 lambda1 = (Function0) method1.invoke(null);
                Class<?> lambdaClass1 = lambda1.getClass();
                assertEquals(3, lambdaClass1.getDeclaredFields().length);

                Field field_self = lambdaClass1.getField("self");
                assertTrue(lambda1 == field_self.get(lambda1));
                assertTrue(lambda1 == lambda1.apply());

                Method method2 = cls.getMethod("method2");
                Object o = cls.newInstance();
                Function0 lambda2 = (Function0) method2.invoke(o);
                Class<?> lambdaClass2 = lambda2.getClass();

                assertEquals(4, lambdaClass2.getDeclaredFields().length);

                field_self = lambdaClass2.getField("self");

                assertTrue(lambda2 == field_self.get(lambda2));
                assertTrue(lambda2 == lambda2.apply());
        }

        @Test
        public void testLambdaSelfName() throws Exception {
                Class<?> cls = retrieveClass("" +
                                "class TestLambdaSelfName\n" +
                                "    static\n" +
                                "        def method\n" +
                                "            $=1\n" +
                                "            ()->$"
                        , "TestLambdaSelfName");
                Method method = cls.getMethod("method");
                Function0 func = (Function0) method.invoke(null);
                assertTrue(func == func.apply());
        }

        @Test
        public void testLambdaSelfChange() throws Exception {
                Class<?> cls = retrieveClass("" +
                                "class TestLambdaSelfChange\n" +
                                "    static\n" +
                                "        def method\n" +
                                "            invoke(()->$ defaultMethod)\n" +
                                "        def invoke(f:F)=f()\n" +
                                "@FunctionalInterface\n" +
                                "interface F\n" +
                                "    def method\n" +
                                "    def defaultMethod=1"
                        , "TestLambdaSelfChange");
                Method method = cls.getMethod("method");
                Object res = method.invoke(null);
                assertEquals(1, res);
        }

        @Test
        public void testInvokeWithNameParams() throws Exception {
                Class<?> cls = retrieveClass("" +
                                "class TestInvokeWithNameParams\n" +
                                "    static\n" +
                                "        def method = open('file', \"w\", encoding='utf-8')\n" +
                                "class open(file, mode)\n" +
                                "    public encoding"
                        , "TestInvokeWithNameParams");
                Method method = cls.getMethod("method");
                Object result = method.invoke(null);
                Field field_file = result.getClass().getDeclaredField("file");
                field_file.setAccessible(true);
                Field field_mode = result.getClass().getDeclaredField("mode");
                field_mode.setAccessible(true);
                Field field_encoding = result.getClass().getDeclaredField("encoding");

                assertEquals("file", field_file.get(result));
                assertEquals("w", field_mode.get(result));
                assertEquals("utf-8", field_encoding.get(result));
        }

        @Test
        public void testIndentBrace() throws Exception {
                Class<?> cls = retrieveClass("" +
                                ";; :scanner-indent\n" +
                                "class TestIndentBrace\n" +
                                "    static\n" +
                                "        def method1 = {}\n" +
                                "        def method2 {1}\n" +
                                "        def method3 = {\"a\":1, \"b\":2}\n" +
                                "        def method4(a,b) = (if a > b {1} else {2})\n" +
                                "        def method5 {" +
                                "            {   \"a\": 1\n" +
                                "                \"b\": 2}\n" +
                                "        }"
                        , "TestIndentBrace");
                Method method1 = cls.getMethod("method1");
                Method method2 = cls.getMethod("method2");
                Method method3 = cls.getMethod("method3");
                Method method4 = cls.getMethod("method4", Object.class, Object.class);
                Method method5 = cls.getMethod("method5");

                assertEquals(new LinkedHashMap<>(), method1.invoke(null));
                assertEquals(1, method2.invoke(null));
                Map<Object, Object> map = new LinkedHashMap<Object, Object>() {{
                        put("a", 1);
                        put("b", 2);
                }};
                assertEquals(map, method3.invoke(null));
                assertEquals(1, method4.invoke(null, 3, 2));
                assertEquals(2, method4.invoke(null, 2, 3));
                assertEquals(map, method5.invoke(null));
        }

        @Test
        public void testMapCast() throws Exception {
                Class<?> cls = retrieveClass("" +
                                "class TestMapCast\n" +
                                "    static\n" +
                                "        def method\n" +
                                "            {'hello': 'world', 'foo': 'bar'} as Bean\n" +
                                "data class Bean(hello, foo)"
                        , "TestMapCast");
                Method method = cls.getMethod("method");
                assertEquals("Bean(hello=world, foo=bar)", method.invoke(null).toString());
        }

        @Test
        public void testMapCastInvokeMethod() throws Exception {
                Class<?> cls = retrieveClass("" +
                                "class TestMapCastInvokeMethod\n" +
                                "    static\n" +
                                "        def method=invoke(1, {'foo':'bar'})\n" +
                                "        private invoke(i, x:Bean)=[i,x]\n" +
                                "data class Bean(foo)"
                        , "TestMapCastInvokeMethod");
                Method method = cls.getMethod("method");
                List list = (List) method.invoke(null);
                Object i = list.get(0);
                Object x = list.get(1);
                assertEquals(1, i);
                assertEquals("Bean(foo=bar)", x.toString());
        }

        @Test
        public void testMapCastInvokeConstructor() throws Exception {
                Class<?> cls = retrieveClass("" +
                                "class TestMapCastInvokeConstructor\n" +
                                "    static\n" +
                                "        def method = Container(1, {'foo':'bar'})\n" +
                                "data class Bean(foo)\n" +
                                "data class Container(i, x:Bean)"
                        , "TestMapCastInvokeConstructor");
                Method method = cls.getMethod("method");
                assertEquals("Container(i=1, x=Bean(foo=bar))", method.invoke(null).toString());
        }

        @Test
        public void testListCast() throws Exception {
                Class<?> cls = retrieveClass("" +
                                "class TestListCast\n" +
                                "    static\n" +
                                "        def method:Bean=[1,2,3]\n" +
                                "class Bean\n" +
                                "    public list = []\n" +
                                "    def add(o)=list.add(o)"
                        , "TestListCast");
                Method method = cls.getMethod("method");
                Object bean = method.invoke(null);
                assertEquals("Bean", bean.getClass().getName());
                List list = (List) bean.getClass().getField("list").get(bean);
                assertEquals(Arrays.asList(1, 2, 3), list);
        }

        @Test
        public void testListCastInvokeMethod() throws Exception {
                Class<?> cls = retrieveClass("" +
                                "class TestListCastInvokeMethod\n" +
                                "    static\n" +
                                "        def method=invoke(10, [1,2,3])\n" +
                                "        private invoke(i,b:Bean)=[i,b]\n" +
                                "data class Bean\n" +
                                "    public list = []\n" +
                                "    def add(o)=list.add(o)"
                        , "TestListCastInvokeMethod");
                Method method = cls.getMethod("method");
                List resultPair = (List) method.invoke(null);
                Object result = resultPair.get(1);
                List list = (List) result.getClass().getField("list").get(result);
                assertEquals(Arrays.asList(1, 2, 3), list);
                assertEquals(10, resultPair.get(0));
        }

        @Test
        public void testListCastInvokeConstructor() throws Exception {
                Class<?> cls = retrieveClass("" +
                                "class TestListCastInvokeMethod\n" +
                                "    static\n" +
                                "        def method=Container(10, [1,2,3])\n" +
                                "data class Bean\n" +
                                "    public list = []\n" +
                                "    def add(o)=list.add(o)\n" +
                                "data class Container(i, x:Bean)"
                        , "TestListCastInvokeMethod");
                Method method = cls.getMethod("method");
                Object result = method.invoke(null);
                Object i = result.getClass().getMethod("getI").invoke(result);
                assertEquals(10, i);

                Object bean = result.getClass().getMethod("getX").invoke(result);
                List list = (List) bean.getClass().getField("list").get(bean);
                assertEquals(Arrays.asList(1, 2, 3), list);
        }

        @Test
        public void testAwaitCallback0() throws Exception {
                Class<?> cls = retrieveClass("" +
                                "import lt::lang::function::Callback0\n" +
                                "class TestAwaitCallback0\n" +
                                "    static\n" +
                                "        def method1\n" +
                                "            x = X\n" +
                                "            await x.perform(1, 2)\n" +
                                "        def method2\n" +
                                "            x = X\n" +
                                "            await x.perform(2, 1)\n" +
                                "class X\n" +
                                "    def perform(a, b, cb:Callback0)\n" +
                                "        Thread(()->" +
                                "                Thread.sleep(100)\n" +
                                "                if a > b\n" +
                                "                    cb(IllegalArgumentException('a > b'))\n" +
                                "                else\n" +
                                "                    cb(null)\n" +
                                "        ).start()"
                        , "TestAwaitCallback0");
                Method method1 = cls.getMethod("method1");
                method1.invoke(null);
                Method method2 = cls.getMethod("method2");
                try {
                        method2.invoke(null);
                        fail();
                } catch (InvocationTargetException e) {
                        Throwable t = e.getTargetException();
                        assertTrue(t instanceof IllegalArgumentException);
                        assertEquals("a > b", t.getMessage());
                }
        }

        @Test
        public void testAwaitCallback1() throws Exception {
                Class<?> cls = retrieveClass("" +
                                "import lt::lang::function::Callback1\n" +
                                "class TestAwaitCallback1\n" +
                                "    static\n" +
                                "        def method1\n" +
                                "            x = X\n" +
                                "            await x.perform(1, 2)\n" +
                                "        def method2\n" +
                                "            x = X\n" +
                                "            await x.perform(2, 1)\n" +
                                "class X\n" +
                                "    def perform(a, b, cb:Callback1)\n" +
                                "        Thread(()->" +
                                "                Thread.sleep(100)\n" +
                                "                if a > b\n" +
                                "                    cb(IllegalArgumentException('a > b'), null)\n" +
                                "                else\n" +
                                "                    cb(null, a + b)\n" +
                                "        ).start()"
                        , "TestAwaitCallback1");
                Method method1 = cls.getMethod("method1");
                assertEquals(3, method1.invoke(null));
                Method method2 = cls.getMethod("method2");
                try {
                        method2.invoke(null);
                        fail();
                } catch (InvocationTargetException e) {
                        Throwable t = e.getTargetException();
                        assertTrue(t instanceof IllegalArgumentException);
                        assertEquals("a > b", t.getMessage());
                }
        }

        @Test
        public void testAwaitAsyncResult() throws Exception {
                Class<?> cls = retrieveClass("" +
                                "import lt::lang::function::Function1\n" +
                                "class TestAwaitCallback1\n" +
                                "    static\n" +
                                "        def method1\n" +
                                "            x = X\n" +
                                "            await x.perform(1, 2)\n" +
                                "        def method2\n" +
                                "            x = X\n" +
                                "            await x.perform(2, 1)\n" +
                                "class X\n" +
                                "    def perform(a, b, cb:Function1)\n" +
                                "        Thread(()->" +
                                "                Thread.sleep(100)\n" +
                                "                if a > b\n" +
                                "                    cb({'err': IllegalArgumentException('a > b'), 'result': null})\n" +
                                "                else\n" +
                                "                    cb({'err': null, 'result': a + b})\n" +
                                "        ).start()"
                        , "TestAwaitCallback1");
                Method method1 = cls.getMethod("method1");
                Map res1 = (Map) method1.invoke(null);
                Throwable t1 = (Throwable) res1.get("err");
                assertNull(t1);
                Object r1 = res1.get("result");
                assertEquals(3, r1);

                Method method2 = cls.getMethod("method2");
                Map res2 = (Map) method2.invoke(null);
                Throwable t2 = (Throwable) res2.get("err");
                Object r2 = res2.get("result");

                assertTrue(t2 instanceof IllegalArgumentException);
                assertEquals("a > b", t2.getMessage());
                assertNull(r2);
        }

        @Test
        public void testUnitReturnValue() throws Exception {
                Class<?> cls = retrieveClass("" +
                                "class TestUnitReturnValue\n" +
                                "    static\n" +
                                "        public field1 = 0\n" +
                                "        private def x()\n" +
                                "            field1 = 1\n" +
                                "        def method1:Unit\n" +
                                "            return x()\n" +
                                "        def method2\n" +
                                "            return"
                        , "TestUnitReturnValue");
                Method method1 = cls.getMethod("method1");
                assertEquals(void.class, method1.getReturnType());
                assertEquals(null, method1.invoke(null));
                Field field1 = cls.getField("field1");
                assertEquals(1, field1.get(null));

                Method method2 = cls.getMethod("method2");
                assertEquals(Unit.get(), method2.invoke(null));
        }
}
