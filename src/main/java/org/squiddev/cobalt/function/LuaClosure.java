/*
 * The MIT License (MIT)
 *
 * Original Source: Copyright (c) 2009-2011 Luaj.org. All rights reserved.
 * Modifications: Copyright (c) 2015-2020 SquidDev
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
package org.squiddev.cobalt.function;

import org.squiddev.cobalt.LuaTable;
import org.squiddev.cobalt.Prototype;

/**
 * A lua function that provides a coroutine.
 */
public abstract class LuaClosure extends LuaFunction {
	public LuaClosure() {
	}

	public LuaClosure(LuaTable env) {
		super(env);
	}

	/**
	 * Get the prototype for this closure
	 *
	 * @return The prototype's closure
	 */
	public abstract Prototype getPrototype();

	public abstract Upvalue getUpvalue(int i);

	public abstract void setUpvalue(int i, Upvalue upvalue);

	@Override
	public LuaClosure checkClosure() {
		return this;
	}

	@Override
	public boolean isClosure() {
		return true;
	}

	@Override
	public LuaClosure optClosure(LuaClosure defval) {
		return this;
	}
}
