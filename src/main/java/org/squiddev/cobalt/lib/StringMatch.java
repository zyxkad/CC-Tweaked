package org.squiddev.cobalt.lib;

import org.squiddev.cobalt.*;
import org.squiddev.cobalt.debug.DebugHandler;
import org.squiddev.cobalt.function.VarArgFunction;

import static org.squiddev.cobalt.Constants.*;
import static org.squiddev.cobalt.ValueFactory.valueOf;
import static org.squiddev.cobalt.ValueFactory.varargsOf;
import static org.squiddev.cobalt.lib.StringLib.L_ESC;

class StringMatch {
	private static final LuaString SPECIALS = valueOf("^$*+?.([%-");
	private static final int MAX_CAPTURES = 32;

	private static final int CAP_UNFINISHED = -1;
	private static final int CAP_POSITION = -2;

	private static final byte MASK_ALPHA = 0x01;
	private static final byte MASK_LOWERCASE = 0x02;
	private static final byte MASK_UPPERCASE = 0x04;
	private static final byte MASK_DIGIT = 0x08;
	private static final byte MASK_PUNCT = 0x10;
	private static final byte MASK_SPACE = 0x20;
	private static final byte MASK_CONTROL = 0x40;
	private static final byte MASK_HEXDIGIT = (byte) 0x80;

	private static final byte[] CHAR_TABLE;

	static {
		CHAR_TABLE = new byte[256];

		for (int i = 0; i < 256; ++i) {
			final char c = (char) i;
			CHAR_TABLE[i] = (byte) ((Character.isDigit(c) ? MASK_DIGIT : 0) |
				(Character.isLowerCase(c) ? MASK_LOWERCASE : 0) |
				(Character.isUpperCase(c) ? MASK_UPPERCASE : 0) |
				((c < ' ' || c == 0x7F) ? MASK_CONTROL : 0));
			if ((c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F') || (c >= '0' && c <= '9')) {
				CHAR_TABLE[i] |= MASK_HEXDIGIT;
			}
			if ((c >= '!' && c <= '/') || (c >= ':' && c <= '@')) {
				CHAR_TABLE[i] |= MASK_PUNCT;
			}
			if ((CHAR_TABLE[i] & (MASK_LOWERCASE | MASK_UPPERCASE)) != 0) {
				CHAR_TABLE[i] |= MASK_ALPHA;
			}
		}

		CHAR_TABLE[' '] = MASK_SPACE;
		CHAR_TABLE['\r'] |= MASK_SPACE;
		CHAR_TABLE['\n'] |= MASK_SPACE;
		CHAR_TABLE['\t'] |= MASK_SPACE;
		CHAR_TABLE[0x0B] |= MASK_SPACE; // \v
		CHAR_TABLE['\f'] |= MASK_SPACE;
	}

	/**
	 * string.find (s, pattern [, init [, plain]])
	 *
	 * Looks for the first match of pattern in the string s.
	 * If it finds a match, then find returns the indices of s
	 * where this occurrence starts and ends; otherwise, it returns nil.
	 * A third, optional numerical argument init specifies where to start the search;
	 * its default value is 1 and may be negative. A value of true as a fourth,
	 * optional argument plain turns off the pattern matching facilities,
	 * so the function does a plain "find substring" operation,
	 * with no characters in pattern being considered "magic".
	 * Note that if plain is given, then init must be given as well.
	 *
	 * If the pattern has captures, then in a successful match the captured values
	 * are also returned, after the two indices.
	 *
	 * @throws LuaError On invalid arguments.
	 */
	static Varargs find(LuaState state, Varargs args) throws LuaError {
		return str_find_aux(state, args, true);
	}

	/**
	 * string.gmatch (s, pattern)
	 *
	 * Returns an iterator function that, each time it is called, returns the next captures
	 * from pattern over string s. If pattern specifies no captures, then the
	 * whole match is produced in each call.
	 *
	 * As an example, the following loop
	 * s = "hello world from Lua"
	 * for w in string.gmatch(s, "%a+") do
	 * print(w)
	 * end
	 *
	 * will iterate over all the words from string s, printing one per line.
	 * The next example collects all pairs key=value from the given string into a table:
	 * t = {}
	 * s = "from=world, to=Lua"
	 * for k, v in string.gmatch(s, "(%w+)=(%w+)") do
	 * t[k] = v
	 * end
	 *
	 * For this function, a '^' at the start of a pattern does not work as an anchor,
	 * as this would prevent the iteration.
	 */
	static Varargs gmatch(LuaState state, Varargs args) throws LuaError {
		LuaString src = args.arg(1).checkLuaString();
		LuaString pat = args.arg(2).checkLuaString();
		return new GMatchAux(state, src, pat);
	}

	/**
	 * string.gsub (s, pattern, repl [, n])
	 * Returns a copy of s in which all (or the first n, if given) occurrences of the
	 * pattern have been replaced by a replacement string specified by repl, which
	 * may be a string, a table, or a function. gsub also returns, as its second value,
	 * the total number of matches that occurred.
	 *
	 * If repl is a string, then its value is used for replacement.
	 * The character % works as an escape character: any sequence in repl of the form %n,
	 * with n between 1 and 9, stands for the value of the n-th captured substring (see below).
	 * The sequence %0 stands for the whole match. The sequence %% stands for a single %.
	 *
	 * If repl is a table, then the table is queried for every match, using the first capture
	 * as the key; if the pattern specifies no captures, then the whole match is used as the key.
	 *
	 * If repl is a function, then this function is called every time a match occurs,
	 * with all captured substrings passed as arguments, in order; if the pattern specifies
	 * no captures, then the whole match is passed as a sole argument.
	 *
	 * If the value returned by the table query or by the function call is a string or a number,
	 * then it is used as the replacement string; otherwise, if it is false or nil,
	 * then there is no replacement (that is, the original match is kept in the string).
	 *
	 * Here are some examples:
	 * x = string.gsub("hello world", "(%w+)", "%1 %1")
	 * --> x="hello hello world world"
	 *
	 * x = string.gsub("hello world", "%w+", "%0 %0", 1)
	 * --> x="hello hello world"
	 *
	 * x = string.gsub("hello world from Lua", "(%w+)%s*(%w+)", "%2 %1")
	 * --> x="world hello Lua from"
	 *
	 * x = string.gsub("home = $HOME, user = $USER", "%$(%w+)", os.getenv)
	 * --> x="home = /home/roberto, user = roberto"
	 *
	 * x = string.gsub("4+5 = $return 4+5$", "%$(.-)%$", function (s)
	 * return loadstring(s)()
	 * end)
	 * --> x="4+5 = 9"
	 *
	 * local t = {name="lua", version="5.1"}
	 * x = string.gsub("$name-$version.tar.gz", "%$(%w+)", t)
	 * --> x="lua-5.1.tar.gz"
	 */
	static Varargs gsubRun(LuaState state, GSubState gsub, Varargs result) throws LuaError, UnwindThrowable {
		LuaString src = gsub.string;
		final int srclen = src.length();
		LuaString p = gsub.pattern;
		LuaValue repl = gsub.replace;
		int max_s = gsub.maxS;
		final boolean anchor = p.length() > 0 && p.charAt(0) == '^';

		Buffer lbuf = gsub.buffer;
		MatchState ms = gsub.ms;

		int soffset = 0;
		while (gsub.n < max_s) {
			ms.reset();
			int res;

			if (gsub.count == GSubState.EMPTY) {
				// We haven't matched so we'll match here
				gsub.count = res = ms.match(soffset, anchor ? 1 : 0);

				if (res != -1) {
					gsub.n++;
					ms.add_value(state, lbuf, soffset, res, repl);
				}
			} else {
				// Otherwise we've yielded so "finish" this replacement
				res = gsub.count;
				ms.finishAddValue(lbuf, soffset, res, result.first());
			}

			// And reset that state
			gsub.count = GSubState.EMPTY;

			if (res != -1 && res > soffset) {
				soffset = res;
			} else if (soffset < srclen) {
				lbuf.append((byte) src.luaByte(soffset++));
			} else {
				break;
			}
			if (anchor) {
				break;
			}
		}
		lbuf.append(src.substring(soffset, srclen));
		return varargsOf(lbuf.toLuaString(), valueOf(gsub.n));
	}

	/**
	 * string.match (s, pattern [, init])
	 *
	 * Looks for the first match of pattern in the string s. If it finds one,
	 * then match returns the captures from the pattern; otherwise it returns
	 * nil. If pattern specifies no captures, then the whole match is returned.
	 * A third, optional numerical argument init specifies where to start the
	 * search; its default value is 1 and may be negative.
	 */
	static Varargs match(LuaState state, Varargs args) throws LuaError {
		return str_find_aux(state, args, false);
	}

	/**
	 * This utility method implements both string.find and string.match.
	 */
	private static Varargs str_find_aux(LuaState state, Varargs args, boolean find) throws LuaError {
		LuaString s = args.arg(1).checkLuaString();
		LuaString pat = args.arg(2).checkLuaString();
		int init = args.arg(3).optInteger(1);

		if (init > 0) {
			init = Math.min(init - 1, s.length());
		} else if (init < 0) {
			init = Math.max(0, s.length() + init);
		}

		boolean fastMatch = find && (args.arg(4).toBoolean() || pat.indexOfAny(SPECIALS) == -1);

		if (fastMatch) {
			int result = s.indexOf(pat, init);
			if (result != -1) {
				return varargsOf(valueOf(result + 1), valueOf(result + pat.length()));
			}
		} else {
			MatchState ms = new MatchState(state.debug, s, pat);

			boolean anchor = false;
			int poff = 0;
			if (pat.length() > 0 && pat.luaByte(0) == '^') {
				anchor = true;
				poff = 1;
			}

			int soff = init;
			do {
				int res;
				ms.reset();
				if ((res = ms.match(soff, poff)) != -1) {
					if (find) {
						return varargsOf(valueOf(soff + 1), valueOf(res), ms.push_captures(false, soff, res));
					} else {
						return ms.push_captures(true, soff, res);
					}
				}
			} while (soff++ < s.length() && !anchor);
		}
		return NIL;
	}

	static class GMatchAux extends VarArgFunction {
		private final int srclen;
		private final MatchState ms;
		private int soffset;

		public GMatchAux(LuaState state, LuaString src, LuaString pat) {
			this.srclen = src.length();
			this.ms = new MatchState(state.debug, src, pat);
			this.soffset = 0;
		}

		@Override
		public Varargs invoke(LuaState state, Varargs args) throws LuaError {
			for (; soffset < srclen; soffset++) {
				ms.reset();
				int res = ms.match(soffset, 0);
				if (res >= 0) {
					int soff = soffset;
					soffset = res;
					if (res == soff) soffset++;
					return ms.push_captures(true, soff, res);
				}
			}
			return NIL;
		}
	}

	static final class GSubState {
		static final int EMPTY = -2;

		final Buffer buffer;
		final LuaString string;
		final LuaString pattern;
		final LuaValue replace;
		final int maxS;
		int n;

		MatchState ms;
		int count;

		GSubState(LuaState state, LuaString src, LuaString pattern, LuaValue replace, int maxS) {
			this.buffer = new Buffer(src.length);
			this.string = src;
			this.pattern = pattern;
			this.replace = replace;
			this.maxS = maxS;

			ms = new MatchState(state.debug, src, pattern);
			count = EMPTY;
		}
	}

	static class MatchState {
		private final DebugHandler handler;
		final LuaString s;
		final LuaString p;
		int level;
		int[] cinit;
		int[] clen;

		MatchState(DebugHandler handler, LuaString s, LuaString pattern) {
			this.handler = handler;
			this.s = s;
			this.p = pattern;
			this.level = 0;
			this.cinit = new int[MAX_CAPTURES];
			this.clen = new int[MAX_CAPTURES];
		}

		void reset() {
			level = 0;
		}

		private void add_s(Buffer lbuf, LuaString news, int soff, int e) throws LuaError {
			int l = news.length();
			for (int i = 0; i < l; ++i) {
				byte b = (byte) news.luaByte(i);
				if (b != L_ESC) {
					lbuf.append(b);
				} else {
					++i; // skip ESC
					b = i < l ? (byte) news.luaByte(i) : 0;
					if (!Character.isDigit((char) b)) {
						lbuf.append(b);
					} else if (b == '0') {
						lbuf.append(s.substring(soff, e));
					} else {
						lbuf.append(push_onecapture(b - '1', soff, e).strvalue());
					}
				}
			}
		}

		public void add_value(LuaState state, Buffer lbuf, int soffset, int end, LuaValue repl) throws LuaError, UnwindThrowable {
			LuaValue replace;
			switch (repl.type()) {
				case TSTRING:
				case TNUMBER:
					add_s(lbuf, repl.strvalue(), soffset, end);
					return;

				case TFUNCTION:
					// TODO: Ensure yields are handled correctly
					replace = OperationHelper.invoke(state, repl, push_captures(true, soffset, end)).first();
					break;

				case TTABLE: {
					// Need to call push_onecapture here for the error checking
					replace = OperationHelper.getTable(state, repl, push_onecapture(0, soffset, end));
					break;
				}

				default:
					throw new LuaError("bad argument: string/function/table expected");
			}

			finishAddValue(lbuf, soffset, end, replace);
		}

		public void finishAddValue(Buffer lbuf, int soffset, int end, LuaValue repl) throws LuaError {
			if (!repl.toBoolean()) {
				repl = s.substring(soffset, end);
			} else if (!repl.isString()) {
				throw new LuaError("invalid replacement value (a " + repl.typeName() + ")");
			}
			lbuf.append(repl.strvalue());
		}

		Varargs push_captures(boolean wholeMatch, int soff, int end) throws LuaError {
			int nlevels = (this.level == 0 && wholeMatch) ? 1 : this.level;
			switch (nlevels) {
				case 0:
					return NONE;
				case 1:
					return push_onecapture(0, soff, end);
			}
			LuaValue[] v = new LuaValue[nlevels];
			for (int i = 0; i < nlevels; ++i) {
				v[i] = push_onecapture(i, soff, end);
			}
			return varargsOf(v);
		}

		private LuaValue push_onecapture(int i, int soff, int end) throws LuaError {
			if (i >= this.level) {
				if (i == 0) {
					return s.substring(soff, end);
				} else {
					throw new LuaError("invalid capture index");
				}
			} else {
				int l = clen[i];
				if (l == CAP_UNFINISHED) {
					throw new LuaError("unfinished capture");
				}
				if (l == CAP_POSITION) {
					return valueOf(cinit[i] + 1);
				} else {
					int begin = cinit[i];
					return s.substring(begin, begin + l);
				}
			}
		}

		private int check_capture(int l) throws LuaError {
			l -= '1';
			if (l < 0 || l >= level || this.clen[l] == CAP_UNFINISHED) {
				throw new LuaError("invalid capture index");
			}
			return l;
		}

		private int capture_to_close() throws LuaError {
			int level = this.level;
			for (level--; level >= 0; level--) {
				if (clen[level] == CAP_UNFINISHED) {
					return level;
				}
			}
			throw new LuaError("invalid pattern capture");
		}

		int classend(int poffset) throws LuaError {
			switch (p.luaByte(poffset++)) {
				case L_ESC:
					if (poffset == p.length()) {
						throw new LuaError("malformed pattern (ends with %)");
					}
					return poffset + 1;

				case '[':
					if (poffset == p.length()) throw new LuaError("malformed pattern (missing ']')");

					if (p.luaByte(poffset) == '^') {
						poffset++;
						if (poffset == p.length()) throw new LuaError("malformed pattern (missing ']')");
					}

					do {
						if (p.luaByte(poffset++) == L_ESC && poffset < p.length()) poffset++;
						if (poffset == p.length()) throw new LuaError("malformed pattern (missing ']')");
					} while (p.luaByte(poffset) != ']');
					return poffset + 1;
				default:
					return poffset;
			}
		}

		static boolean match_class(int c, int cl) {
			final char lcl = Character.toLowerCase((char) cl);
			int cdata = CHAR_TABLE[c];

			boolean res;
			switch (lcl) {
				case 'a':
					res = (cdata & MASK_ALPHA) != 0;
					break;
				case 'd':
					res = (cdata & MASK_DIGIT) != 0;
					break;
				case 'l':
					res = (cdata & MASK_LOWERCASE) != 0;
					break;
				case 'u':
					res = (cdata & MASK_UPPERCASE) != 0;
					break;
				case 'c':
					res = (cdata & MASK_CONTROL) != 0;
					break;
				case 'p':
					res = (cdata & MASK_PUNCT) != 0;
					break;
				case 's':
					res = (cdata & MASK_SPACE) != 0;
					break;
				case 'w':
					res = (cdata & (MASK_ALPHA | MASK_DIGIT)) != 0;
					break;
				case 'x':
					res = (cdata & MASK_HEXDIGIT) != 0;
					break;
				case 'z':
					res = (c == 0);
					break;
				default:
					return cl == c;
			}
			return (lcl == cl) ? res : !res;
		}

		boolean matchbracketclass(int c, int poff, int ec) {
			boolean sig = true;
			if (p.luaByte(poff + 1) == '^') {
				sig = false;
				poff++;
			}
			while (++poff < ec) {
				if (p.luaByte(poff) == L_ESC) {
					poff++;
					if (match_class(c, p.luaByte(poff))) {
						return sig;
					}
				} else if ((p.luaByte(poff + 1) == '-') && (poff + 2 < ec)) {
					poff += 2;
					if (p.luaByte(poff - 2) <= c && c <= p.luaByte(poff)) {
						return sig;
					}
				} else if (p.luaByte(poff) == c) return sig;
			}
			return !sig;
		}

		boolean singlematch(int c, int poff, int ep) {
			switch (p.luaByte(poff)) {
				case '.':
					return true;
				case L_ESC:
					return match_class(c, p.luaByte(poff + 1));
				case '[':
					return matchbracketclass(c, poff, ep - 1);
				default:
					return p.luaByte(poff) == c;
			}
		}

		/**
		 * Perform pattern matching. If there is a match, returns offset into s
		 * where match ends, otherwise returns -1.
		 */
		int match(int soffset, int poffset) throws LuaError {
			while (true) {
				handler.poll();

				// Check if we are at the end of the pattern -
				// equivalent to the '\0' case in the C version, but our pattern
				// string is not NUL-terminated.
				if (poffset == p.length()) {
					return soffset;
				}
				switch (p.luaByte(poffset)) {
					case '(':
						if (++poffset < p.length() && p.luaByte(poffset) == ')') {
							return start_capture(soffset, poffset + 1, CAP_POSITION);
						} else {
							return start_capture(soffset, poffset, CAP_UNFINISHED);
						}
					case ')':
						return end_capture(soffset, poffset + 1);
					case L_ESC:
						if (poffset + 1 == p.length()) {
							throw new LuaError("malformed pattern (ends with '%')");
						}
						switch (p.luaByte(poffset + 1)) {
							case 'b':
								soffset = matchbalance(soffset, poffset + 2);
								if (soffset == -1) return -1;
								poffset += 4;
								continue;
							case 'f': {
								poffset += 2;
								if (poffset == p.length() || p.luaByte(poffset) != '[') {
									throw new LuaError("missing '[' after '%f' in pattern");
								}
								int ep = classend(poffset);
								int previous = (soffset == 0) ? 0 : s.luaByte(soffset - 1);
								if (matchbracketclass(previous, poffset, ep - 1) || (soffset < s.length && !matchbracketclass(s.luaByte(soffset), poffset, ep - 1))) {
									return -1;
								}
								poffset = ep;
								continue;
							}
							default: {
								int c = p.luaByte(poffset + 1);
								if (Character.isDigit((char) c)) {
									soffset = match_capture(soffset, c);
									if (soffset == -1) {
										return -1;
									}
									return match(soffset, poffset + 2);
								}
							}
						}
						break;
					case '$':
						if (poffset + 1 == p.length()) {
							return (soffset == s.length()) ? soffset : -1;
						}
				}
				int ep = classend(poffset);
				boolean m = soffset < s.length() && singlematch(s.luaByte(soffset), poffset, ep);
				int pc = (ep < p.length()) ? p.luaByte(ep) : '\0';

				switch (pc) {
					case '?':
						int res;
						if (m && ((res = match(soffset + 1, ep + 1)) != -1)) {
							return res;
						}
						poffset = ep + 1;
						continue;
					case '*':
						return max_expand(soffset, poffset, ep);
					case '+':
						return (m ? max_expand(soffset + 1, poffset, ep) : -1);
					case '-':
						return min_expand(soffset, poffset, ep);
					default:
						if (!m) {
							return -1;
						}
						soffset++;
						poffset = ep;
				}
			}
		}

		int max_expand(int soff, int poff, int ep) throws LuaError {
			int i = 0;
			while (soff + i < s.length() &&
				singlematch(s.luaByte(soff + i), poff, ep)) {
				i++;
			}
			while (i >= 0) {
				int res = match(soff + i, ep + 1);
				if (res != -1) {
					return res;
				}
				i--;
			}
			return -1;
		}

		int min_expand(int soff, int poff, int ep) throws LuaError {
			for (; ; ) {
				int res = match(soff, ep + 1);
				if (res != -1) {
					return res;
				} else if (soff < s.length() && singlematch(s.luaByte(soff), poff, ep)) {
					soff++;
				} else {
					return -1;
				}
			}
		}

		int start_capture(int soff, int poff, int what) throws LuaError {
			int res;
			int level = this.level;
			if (level >= MAX_CAPTURES) {
				throw new LuaError("too many captures");
			}
			cinit[level] = soff;
			clen[level] = what;
			this.level = level + 1;
			if ((res = match(soff, poff)) == -1) {
				this.level--;
			}
			return res;
		}

		int end_capture(int soff, int poff) throws LuaError {
			int l = capture_to_close();
			int res;
			clen[l] = soff - cinit[l];
			if ((res = match(soff, poff)) == -1) {
				clen[l] = CAP_UNFINISHED;
			}
			return res;
		}

		int match_capture(int soff, int l) throws LuaError {
			l = check_capture(l);
			int len = clen[l];
			if ((s.length() - soff) >= len &&
				LuaString.equals(s, cinit[l], s, soff, len)) {
				return soff + len;
			} else {
				return -1;
			}
		}

		int matchbalance(int soff, int poff) throws LuaError {
			final int plen = p.length();
			if (poff == plen || poff + 1 == plen) {
				throw new LuaError("unbalanced pattern");
			}
			if (soff >= s.length() || s.luaByte(soff) != p.luaByte(poff)) {
				return -1;
			} else {
				int b = p.luaByte(poff);
				int e = p.luaByte(poff + 1);
				int cont = 1;
				while (++soff < s.length()) {
					if (s.luaByte(soff) == e) {
						if (--cont == 0) return soff + 1;
					} else if (s.luaByte(soff) == b) cont++;
				}
			}
			return -1;
		}
	}

	static boolean isWhitespace(byte b) {
		return (CHAR_TABLE[b & 0xFF] & MASK_SPACE) != 0;
	}
}
