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

package lt.compiler.semantic;

import lt.compiler.LineCol;

/**
 * field definition
 */
public class SFieldDef extends SMember implements LeftValue {
        private String name;
        private STypeDef type;
        private boolean alreadyAssigned = false;

        public SFieldDef(LineCol lineCol) {
                super(lineCol);
        }

        public void setName(String name) {
                this.name = name;
        }

        public void setType(STypeDef type) {
                this.type = type;
        }

        public String name() {
                return name;
        }

        @Override
        public boolean canChange() {
                return !modifiers().contains(SModifier.FINAL);
        }

        @Override
        public boolean alreadyAssigned() {
                return alreadyAssigned;
        }

        @Override
        public void assign() {
                alreadyAssigned = true;
        }

        @Override
        public STypeDef type() {
                return type;
        }

        @Override
        public String toString() {
                StringBuilder sb = new StringBuilder();
                for (SModifier mod : modifiers()) {
                        sb.append(mod.toString().toLowerCase()).append(" ");
                }
                sb.append(name()).append(" : ").append(type().fullName());
                return sb.toString();
        }
}
