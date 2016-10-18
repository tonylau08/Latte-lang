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

package lt.compiler.syntactic.pre;

import lt.compiler.LineCol;
import lt.compiler.syntactic.Pre;

/**
 * modifier
 */
public class Modifier implements Pre {
        public enum Available {
                PRIVATE, PUBLIC, PROTECTED, PKG, SYNCHRONIZED, VAL,
                NATIVE, ABSTRACT, TRANSIENT, VOLATILE, STRICTFP, DATA,
                VAR, DEF, NONNULL, NONEMPTY
        }

        public final Available modifier;
        private final LineCol lineCol;

        public Modifier(Available modifier, LineCol lineCol) {
                this.modifier = modifier;
                this.lineCol = lineCol;
        }

        @Override
        public boolean equals(Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;

                Modifier modifier1 = (Modifier) o;

                return !(modifier != null ? !modifier.equals(modifier1.modifier) : modifier1.modifier != null);
        }

        @Override
        public int hashCode() {
                return modifier != null ? modifier.hashCode() : 0;
        }

        @Override
        public String toString() {
                return "(" + modifier + ")";
        }

        @Override
        public LineCol line_col() {
                return lineCol;
        }
}
