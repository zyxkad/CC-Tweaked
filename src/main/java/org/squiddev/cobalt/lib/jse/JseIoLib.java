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
package org.squiddev.cobalt.lib.jse;

import org.squiddev.cobalt.LuaString;
import org.squiddev.cobalt.function.LibFunction;
import org.squiddev.cobalt.lib.IoLib;

import java.io.*;

/**
 * Subclass of {@link IoLib} and therefore {@link LibFunction} which implements the lua standard {@code io}
 * library for the JSE platform.
 *
 * It uses RandomAccessFile to implement seek on files.
 *
 * This has been implemented to match as closely as possible the behavior in the corresponding library in C.
 *
 * @see LibFunction
 * @see JsePlatform
 * @see IoLib
 * @see <a href="http://www.lua.org/manual/5.1/manual.html#5.7">http://www.lua.org/manual/5.1/manual.html#5.7</a>
 */
public class JseIoLib extends IoLib {

	public JseIoLib() {
		super();
	}

	@Override
	protected File wrapStandardStream(InputStream stream) throws IOException {
		return new FileImpl(stream, true);
	}

	@Override
	protected File wrapStandardStream(OutputStream stream) throws IOException {
		return new FileImpl(stream, true);
	}

	@Override
	protected File openFile(String filename, boolean readMode, boolean appendMode, boolean updateMode, boolean binaryMode) throws IOException {
		RandomAccessFile f = new RandomAccessFile(filename, readMode ? "r" : "rw");
		if (appendMode) {
			f.seek(f.length());
		} else {
			if (!readMode) {
				f.setLength(0);
			}
		}
		return new FileImpl(f);
	}

	@Override
	protected File openProgram(String prog, String mode) throws IOException {
		final Process p = Runtime.getRuntime().exec(prog);
		return "w".equals(mode) ?
			new FileImpl(p.getOutputStream(), false) :
			new FileImpl(p.getInputStream(), false);
	}

	@Override
	protected File tmpFile() throws IOException {
		java.io.File f = java.io.File.createTempFile(".luaj", "bin");
		f.deleteOnExit();
		return new FileImpl(new RandomAccessFile(f, "rw"));
	}

	private final class FileImpl extends File {
		private final RandomAccessFile file;
		private final InputStream is;
		private final OutputStream os;
		private boolean closed = false;
		private boolean nobuffer = false;
		private final boolean isStandard;

		private FileImpl(RandomAccessFile file, InputStream is, OutputStream os, boolean isStandard) {
			this.file = file;
			this.is = is != null ? is.markSupported() ? is : new BufferedInputStream(is) : null;
			this.os = os;
			this.isStandard = isStandard;
		}

		private FileImpl(RandomAccessFile f) {
			this(f, null, null, false);
		}

		private FileImpl(InputStream i, boolean isStandard) {
			this(null, i, null, isStandard);
		}

		private FileImpl(OutputStream o, boolean isStandard) {
			this(null, null, o, isStandard);
		}

		@Override
		public String toString() {
			return "file (" + (isclosed() ? "closed" : hashCode()) + ")";
		}

		@Override
		public boolean isstdfile() {
			return isStandard;
		}

		@Override
		public void close() throws IOException {
			closed = true;
			if (file != null) {
				file.close();
			}
		}

		@Override
		public void flush() throws IOException {
			if (os != null) {
				os.flush();
			}
		}

		@Override
		public void write(LuaString s) throws IOException {
			if (os != null) {
				os.write(s.bytes, s.offset, s.length);
			} else if (file != null) {
				file.write(s.bytes, s.offset, s.length);
			} else {
				throw new IOException("not implemented");
			}
			if (nobuffer) {
				flush();
			}
		}

		@Override
		public boolean isclosed() {
			return closed;
		}

		@Override
		public int seek(String option, int pos) throws IOException {
			if (file != null) {
				if ("set".equals(option)) {
					file.seek(pos);
				} else if ("end".equals(option)) {
					file.seek(file.length() + pos);
				} else {
					file.seek(file.getFilePointer() + pos);
				}
				return (int) file.getFilePointer();
			}
			throw new IOException("not implemented");
		}

		@Override
		public void setvbuf(String mode, int size) {
			nobuffer = "no".equals(mode);
		}

		// get length remaining to read
		@Override
		public int remaining() throws IOException {
			return file != null ? (int) (file.length() - file.getFilePointer()) : -1;
		}

		// peek ahead one character
		@Override
		public int peek() throws IOException {
			if (is != null) {
				is.mark(1);
				int c = is.read();
				is.reset();
				return c;
			} else if (file != null) {
				long fp = file.getFilePointer();
				int c = file.read();
				file.seek(fp);
				return c;
			}
			throw new IOException("not implemented");
		}

		// return char if read, -1 if eof, throw IOException on other exception
		@Override
		public int read() throws IOException {
			if (is != null) {
				return is.read();
			} else if (file != null) {
				return file.read();
			}
			throw new IOException("not implemented");
		}

		// return number of bytes read if positive, -1 if eof, throws IOException
		@Override
		public int read(byte[] bytes, int offset, int length) throws IOException {
			if (file != null) {
				return file.read(bytes, offset, length);
			} else if (is != null) {
				return is.read(bytes, offset, length);
			} else {
				throw new IOException("not implemented");
			}
		}
	}
}
