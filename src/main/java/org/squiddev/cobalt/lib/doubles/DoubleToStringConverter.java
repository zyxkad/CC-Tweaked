/*
 * Copyright 2010 the V8 project authors. All rights reserved.
 * Java Port Copyright 2021 sir-maniac. All Rights reserved.
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *    * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of Google Inc. nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 *
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.squiddev.cobalt.lib.doubles;

import org.checkerframework.checker.signedness.qual.Unsigned;
import org.squiddev.cobalt.Buffer;

import static java.util.Objects.requireNonNull;
import static org.squiddev.cobalt.lib.doubles.Assert.*;

public class DoubleToStringConverter {
	public static final Symbols ECMA_SCRIPT_SYMBOLS = new Symbols("Infinity", "NaN", 'e');
	/**
	 * When calling toFixed with a double > 10^MAX_FIXED_DIGITS_BEFORE_POINT
	 * or a requested_digits parameter > MAX_FIXED_DIGITS_AFTER_POINT then the
	 * function returns false.
	 */
	public static final int MAX_FIXED_DIGITS_BEFORE_POINT = 308;
	private static final double FIRST_NON_FIXED = 1e308; // exponent must be the same as MAX_FIXED_DIGITS_BEFORE_POINT
	public static final int MAX_FIXED_DIGITS_AFTER_POINT = 100;

	/**
	 * When calling toExponential with a requested_digits
	 * parameter > MAX_EXPONENTIAL_DIGITS then the function returns false.
	 */
	public static final int MAX_EXPONENTIAL_DIGITS = 120;

	/**
	 * When calling toPrecision with a requested_digits
	 * parameter < MIN_PRECISION_DIGITS or requested_digits > MAX_PRECISION_DIGITS
	 * then the function returns false.
	 */
	public static final int MIN_PRECISION_DIGITS = 1;
	/**
	 * When calling toPrecision with a requested_digits
	 * parameter < MIN_PRECISION_DIGITS or requested_digits > MAX_PRECISION_DIGITS
	 * then the function returns false.
	 */
	public static final int MAX_PRECISION_DIGITS = 120;

	/**
	 * The maximal number of digits that are needed to emit a double in base 10.
	 * A higher precision can be achieved by using more digits, but the shortest
	 * accurate representation of any double will never use more digits than
	 * BASE_10_MAXIMAL_LENGTH.
	 * Note that doubleToAscii null-terminates its input. So the given buffer
	 * should be at least BASE_10_MAXIMAL_LENGTH + 1 characters long.
	 */
	public static final int BASE_10_MAXIMAL_LENGTH = 17;

	public static final int EXPONENTIAL_REP_CAPACITY = MAX_EXPONENTIAL_DIGITS + 2;
	public static final int FIXED_REP_CAPACITY = MAX_FIXED_DIGITS_BEFORE_POINT + MAX_FIXED_DIGITS_AFTER_POINT + 1;
	public static final int PRECISION_REP_CAPACITY = MAX_PRECISION_DIGITS + 1;

	public static final int MAX_EXPONENT_LENGTH = 5;

	@SuppressWarnings("ImplicitNumericConversion")
	private static final int ASCII_ZERO = '0';

	public static class Flags {
		/**
		 * No special flags (0)
		 */
		public static final int NO_FLAGS = 0;
		/**
		 * When the number is converted into exponent
		 * form, emits a '+' for positive exponents. Example: <code>1.2e+2</code>
		 */
		public static final int EMIT_POSITIVE_EXPONENT_SIGN = 1;
		/**
		 * When the input number is an integer and is
		 * converted into decimal format then a trailing decimal point is appended.
		 * <p/>
		 * Example: <code>2345.0</code> is converted to <code>"2345.".</code>
		 */
		public static final int EMIT_TRAILING_DECIMAL_POINT = 2;
		/**
		 * In addition to a trailing decimal point emits a trailing '0'-character.
		 * This flag requires the <code>EMIT_TRAILING_DECIMAL_POINT</code> flag.
		 * <p/>
		 * Example: <code>2345.0</code> is converted to <code>"2345.0".</code>
		 */
		public static final int EMIT_TRAILING_ZERO_AFTER_POINT = 4;
		/**
		 * <code>"-0.0"</code> is converted to <code>"0.0"</code>.
		 */
		public static final int UNIQUE_ZERO = 8;
		/**
		 * Trailing zeros are removed from the fractional portion
		 * of the result in precision mode. Matches C++ <code>printf</code>'s %g.
		 * <p/>
		 * When EMIT_TRAILING_ZERO_AFTER_POINT is also given, one trailing zero is
		 * preserved.
		 */
		public static final int NO_TRAILING_ZERO = 16;
	}


	private final int flags;
	private final PrecisionPolicy precisionPolicy;
	private final int minExponentWidth;

	/**
	 * Construct a <code>DoubleToStringConvertor</code>.
	 * <p/>
	 * Flags should be a bit-or combination of the possible Flags members.
	 *  <ul>
	 *    <li><code>NO_FLAGS</code>: no special flags</li>
	 *    <li><code>EMIT_POSITIVE_EXPONENT_SIGN</code>: when the number is converted into exponent
	 *        form, emits a '+' for positive exponents. Example: 1.2e+2</li>
	 *    <li><code>EMIT_TRAILING_DECIMAL_POINT</code>: when the input number is an integer and is
	 *        converted into decimal format then a trailing decimal point is appended.
	 *        Example: 2345.0 is converted to "2345.".</li>
	 *    <li><code>EMIT_TRAILING_ZERO_AFTER_POINT</code>: in addition to a trailing decimal point
	 *        emits a trailing '0'-character. This flag requires the
	 *             EMIT_TRAILING_DECIMAL_POINT flag.
	 *        Example: 2345.0 is converted to "2345.0".</li>
	 *    <li><code>UNIQUE_ZERO</code>: "-0.0" is converted to "0.0".</li>
	 *    <li><code>NO_TRAILING_ZERO</code>: Trailing zeros are removed from the fractional portion
	 *        of the result in precision mode. Matches C++ <code>printf</code>'s %g.
	 *        When EMIT_TRAILING_ZERO_AFTER_POINT is also given, one trailing zero is
	 *        preserved.</li>
	 *  </ul>
	 * <p/>
	 *
	 * @param flags           the bit-or combination of {@link Flags}
	 * @param precisionPolicy the parameters that configure when {@link #toPrecision}
	 *                        output switches to exponential representation, see
	 *                        {@link PrecisionPolicy#PrecisionPolicy(int, int)}
	 * @see Symbols#Symbols(String, String, int)
	 * @see PrecisionPolicy#PrecisionPolicy(int, int)
	 */
	public DoubleToStringConverter(int flags,
								   PrecisionPolicy precisionPolicy) {
		this(flags,
			precisionPolicy,
			0);
	}

	/**
	 * Construct a <code>DoubleToStringConvertor</code>.
	 * <p/>
	 * Flags should be a bit-or combination of the possible Flags-enum.
	 *  <ul>
	 *    <li><code>NO_FLAGS</code>: no special flags</li>
	 *    <li><code>EMIT_POSITIVE_EXPONENT_SIGN</code>: when the number is converted into exponent
	 *        form, emits a '+' for positive exponents. Example: 1.2e+2</li>
	 *    <li><code>EMIT_TRAILING_DECIMAL_POINT</code>: when the input number is an integer and is
	 *        converted into decimal format then a trailing decimal point is appended.
	 *        Example: 2345.0 is converted to "2345.".</li>
	 *    <li><code>EMIT_TRAILING_ZERO_AFTER_POINT</code>: in addition to a trailing decimal point
	 *        emits a trailing '0'-character. This flag requires the
	 *             EMIT_TRAILING_DECIMAL_POINT flag.
	 *        Example: 2345.0 is converted to "2345.0".</li>
	 *    <li><code>UNIQUE_ZERO</code>: "-0.0" is converted to "0.0".</li>
	 *    <li><code>NO_TRAILING_ZERO</code>: Trailing zeros are removed from the fractional portion
	 *        of the result in precision mode. Matches C++ <code>printf</code>'s %g.
	 *        When EMIT_TRAILING_ZERO_AFTER_POINT is also given, one trailing zero is
	 *        preserved.</li>
	 *  </ul>
	 * <p/>
	 * The <code>minExponentWidth</code> is used for exponential representations.
	 * The converter adds leading '0's to the exponent until the exponent
	 *   is at least minExponentWidth digits long.
	 * <p/>
	 * The <code>minExponentWidth</code> is clamped to 5.
	 * As such, the exponent may never have more than 5 digits in total.<br/>
	 *
	 * @param flags            the bit-or combination of {@link Flags}
	 * @param precisionPolicy  the parameters that configure when {@link #toPrecision}
	 *                         output switches to exponential representation, see
	 *                         {@link PrecisionPolicy#PrecisionPolicy(int, int)}
	 * @param minExponentWidth The converter adds leading '0's to the exponent until the exponent
	 *                         is at least <code>minExponentWidth</code> digits long, clamped to 5
	 * @see Symbols#Symbols(String, String, int)
	 * @see PrecisionPolicy#PrecisionPolicy(int, int)
	 */
	public DoubleToStringConverter(int flags,
								   PrecisionPolicy precisionPolicy,
								   int minExponentWidth) {
		this.flags = flags;
		this.precisionPolicy = requireNonNull(precisionPolicy);
		this.minExponentWidth = minExponentWidth;
		// When 'trailing zero after the point' is set, then 'trailing point'
		// must be set too.
		if ((flags & Flags.EMIT_TRAILING_DECIMAL_POINT) == 0 && (flags & Flags.EMIT_TRAILING_ZERO_AFTER_POINT) != 0) {
			throw new IllegalArgumentException("the flag EMIT_TRAILING_DECIMAL_POINT must be set when " +
				"EMIT_TRAILING_ZERO_AFTER_POINT is set");
		}
	}

	/**
	 * Returns a converter following the EcmaScript specification.
	 * <p/>
	 * <b>Flags:</b> UNIQUE_ZERO and EMIT_POSITIVE_EXPONENT_SIGN.<br/>
	 * <b>Special values:</b> "Infinity" and "NaN".
	 * Lower case 'e' for exponential values.<br/>
	 * <b>PrecisionPolicy.maxLeadingZeros</b>: 6<br/>
	 * <b>PrecisionPolicy.maxTrailingZeroes</b>: 0<br/>
	 */
	@SuppressWarnings("ImplicitNumericConversion")
	public static DoubleToStringConverter ecmaScriptConverter() {
		int flags = Flags.UNIQUE_ZERO | Flags.EMIT_POSITIVE_EXPONENT_SIGN;
		return new DoubleToStringConverter(flags,
			new PrecisionPolicy(6, 0));
	}

	/**
	 * If the value is a special value (NaN or Infinity) constructs the
	 * corresponding string using the configured infinity/nan-symbol.
	 * If either of them is NULL or the value is not special then the
	 * function returns false.
	 */
	private void handleSpecialValues(double value, FormatOptions fo, Buffer resultBuilder) {
		boolean sign = value < 0.0;

		int effectiveWidth = fo.getWidth();
		if (sign || fo.isExplicitPlus() || fo.isSpaceWhenPositive()) effectiveWidth--;

		String symbol;
		boolean isInfinite = Double.isInfinite(value);
		if (isInfinite) {
			symbol = fo.getSymbols().getInfinitySymbol();
		} else if (Double.isNaN(value)) {
			symbol = fo.getSymbols().getNanSymbol();
		} else {
			throw new IllegalStateException("Unreachable");
		}

		if (!fo.isLeftAdjust() && symbol.length() < effectiveWidth) {
			addPadding(resultBuilder, ' ', effectiveWidth - symbol.length());
		}

		if (sign) {
			resultBuilder.append('-');
		} else if (fo.isExplicitPlus()) {
			resultBuilder.append('+');
		} else if (fo.isSpaceWhenPositive()) {
			resultBuilder.append(' ');
		}
		resultBuilder.append(symbol);

		if (fo.isLeftAdjust() && symbol.length() < effectiveWidth) {
			addPadding(resultBuilder, ' ', effectiveWidth - symbol.length());
		}
	}

	/**
	 * Constructs an exponential representation (i.e. 1.234e56).
	 * The given exponent assumes a decimal point after the first decimal digit.
	 */
	private void createExponentialRepresentation(
		final DecimalRepBuf decimalDigits,
		double value,
		int length,
		int exponent,
		FormatOptions fo,
		Buffer resultBuilder
	) {
		requireArg(decimalDigits.length() != 0, "decimalDigits must not be empty");
		requireArg(length <= decimalDigits.length(), "length must be smaller than decimalDigits");

		if (assertEnabled()) assertThat((double) exponent < 1e4);
		ExponentPart exponentPart = createExponentPart(exponent);

		boolean emitTrailingPoint = fo.isAlternateForm() || (flags & Flags.EMIT_TRAILING_DECIMAL_POINT) != 0;
		boolean emitTrailingZero = (flags & Flags.EMIT_TRAILING_ZERO_AFTER_POINT) != 0;

		int padWidth = 0;
		if (fo.getWidth() > 0) {
			int valueWidth = calculateExpWidth(length, fo, exponentPart, shouldEmitMinus(value), emitTrailingPoint, emitTrailingZero);
			padWidth = fo.getWidth() - valueWidth;
		}

		if (padWidth > 0 && !fo.isLeftAdjust() && !fo.isZeroPad()) {
			addPadding(resultBuilder, ' ', padWidth);
		}

		appendSign(value, fo, resultBuilder);

		if (padWidth > 0 && !fo.isLeftAdjust() && fo.isZeroPad()) {
			addPadding(resultBuilder, '0', padWidth);
		}

		resultBuilder.append(decimalDigits.charAt(0));
		if (length != 1) {
			resultBuilder.append('.');
			resultBuilder.append(decimalDigits.getBuffer(), 1, length - 1);
		} else if (fo.isAlternateForm()) {
			resultBuilder.append('.');
		}
		resultBuilder.append((char) fo.getSymbols().getExponentCharacter());
		resultBuilder.append(exponentPart.getBuffer(), exponentPart.getStart(), exponentPart.length());

		// zeroPad is ignored if leftAdjust is set
		if (padWidth > 0 && fo.isLeftAdjust()) {
			addPadding(resultBuilder, ' ', padWidth);
		}
	}

	private ExponentPart createExponentPart(int exponent) {
		boolean sign = false;
		if (exponent < 0) {
			sign = true;
			exponent = -exponent;
		}

		// +1 to make room for '-' or '+'
		char[] buffer = new char[MAX_EXPONENT_LENGTH + 1];
		int firstCharPos = MAX_EXPONENT_LENGTH + 1;
		if (exponent == 0) {
			buffer[--firstCharPos] = '0';
		} else {
			while (exponent > 0) {
				buffer[--firstCharPos] = (char) (ASCII_ZERO + (exponent % 10));
				exponent /= 10;
			}
		}
		// Add prefix '0' to make exponent width >= min(min_exponent_with_, MAX_EXPONENT_LENGTH)
		// For example: convert 1e+9 -> 1e+09, if min_exponent_with_ is set to 2
		while ((MAX_EXPONENT_LENGTH + 1) - firstCharPos < Math.min(minExponentWidth, MAX_EXPONENT_LENGTH)) {
			buffer[--firstCharPos] = '0';
		}

		if (sign) {
			buffer[--firstCharPos] = '-';
		} else {
			if ((flags & Flags.EMIT_POSITIVE_EXPONENT_SIGN) != 0) {
				buffer[--firstCharPos] = '+';
			}
		}

		return new ExponentPart(buffer, firstCharPos, (MAX_EXPONENT_LENGTH + 1) - firstCharPos);
	}

	/**
	 * Creates a decimal representation (i.e 1234.5678).
	 * <p/>
	 *
	 * @param digitsAfterPoint width of fractional part, must always be big enough to print all digits passed
	 */
	private void createDecimalRepresentation(
		DecimalRepBuf decimalDigits,
		double value,
		int digitsAfterPoint,
		FormatOptions fo,
		Buffer resultBuilder
	) {
		int decimalPoint = decimalDigits.getPointPosition();
		int digitsLength = decimalDigits.length();

		if (digitsLength > decimalPoint + digitsAfterPoint) {
			throw new IllegalArgumentException("too many digits for given digitAfterPoint");
		}

		boolean emitTrailingPoint = fo.isAlternateForm() || (flags & Flags.EMIT_TRAILING_DECIMAL_POINT) != 0;
		boolean emitTrailingZero = (flags & Flags.EMIT_TRAILING_ZERO_AFTER_POINT) != 0;

		int padWidth = 0;
		if (fo.getWidth() > 0) {
			int valueWidth = calculateDecimalWidth(decimalDigits, fo, digitsAfterPoint,
				shouldEmitMinus(value), emitTrailingPoint, emitTrailingZero);
			padWidth = fo.getWidth() - valueWidth;
		}

		// space padding before the number and sign
		if (padWidth > 0 && !fo.isLeftAdjust() && !fo.isZeroPad()) {
			addPadding(resultBuilder, ' ', padWidth);
		}

		appendSign(value, fo, resultBuilder);

		// zero padding after the sign, before the rest of the number
		if (padWidth > 0 && !fo.isLeftAdjust() && fo.isZeroPad()) {
			addPadding(resultBuilder, '0', padWidth);
		}

		// Create a representation that is padded with zeros if needed.
		if (decimalPoint <= 0) {
			// "0.00000decimal_rep" or "0.000decimal_rep00".
			resultBuilder.append('0');
			if (digitsAfterPoint > 0) {
				resultBuilder.append('.');

				addPadding(resultBuilder, '0', -decimalPoint);
				if (assertEnabled()) assertThat(digitsLength <= digitsAfterPoint - (-decimalPoint));
				resultBuilder.append(decimalDigits.getBuffer(), 0, decimalDigits.length());
				int remainingDigits = digitsAfterPoint - (-decimalPoint) - digitsLength;
				addPadding(resultBuilder, '0', remainingDigits);
			}
		} else if (decimalPoint >= digitsLength) {
			// "decimal_rep0000.00000" or "decimalRep.0000".
			resultBuilder.append(decimalDigits.getBuffer(), 0, decimalDigits.length());
			addPadding(resultBuilder, '0', decimalPoint - digitsLength);
			if (digitsAfterPoint > 0) {
				resultBuilder.append('.');
				addPadding(resultBuilder, '0', digitsAfterPoint);
			}
		} else {
			// "decima.l_rep000".
			if (assertEnabled()) assertThat(digitsAfterPoint > 0);
			resultBuilder.append(decimalDigits.getBuffer(), 0, decimalPoint);
			resultBuilder.append('.');
			if (assertEnabled()) assertThat(digitsLength - decimalPoint <= digitsAfterPoint);
			resultBuilder.append(decimalDigits.getBuffer(), decimalPoint, digitsLength - decimalPoint);
			int remainingDigits = digitsAfterPoint - (digitsLength - decimalPoint);
			addPadding(resultBuilder, '0', remainingDigits);
		}
		if (digitsAfterPoint == 0 && emitTrailingPoint) {
			resultBuilder.append('.');
			if (emitTrailingZero) {
				resultBuilder.append('0');
			}
		}

		if (padWidth > 0 && fo.isLeftAdjust()) {
			addPadding(resultBuilder,
				fo.isZeroPad() ? '0' : ' ',
				padWidth);
		}
	}

	/**
	 * Calculate the print width of the decimal digits.
	 */
	private int calculateDecimalWidth(
		DecimalRepBuf decimalDigits, FormatOptions fo,
		int digitsAfterPoint, boolean emitMinus, boolean emitTrailingPoint, boolean emitTrailingZero
	) {
		int decimalPoint = decimalDigits.getPointPosition();
		int digitsLength = decimalDigits.length();

		int valueWidth;
		if (digitsLength == 0) {
			// empty decimalDigits means "0"
			valueWidth = 1 + (digitsAfterPoint > 0 ? 1 + digitsAfterPoint : 0);
		} else if (decimalPoint <= 0) {
			// "0." + digits after point
			valueWidth = 2 + digitsAfterPoint;
		} else {
			if (digitsLength > 0) {
				// digits before decimal + ("." + digits after point)
				valueWidth = decimalPoint + (digitsAfterPoint > 0 ? 1 + digitsAfterPoint : 0);
			} else {
				// no digits means "0" + ("." + digits after point)
				valueWidth = 1 + (digitsAfterPoint > 0 ? 1 + digitsAfterPoint : 0);
			}
		}
		if (digitsAfterPoint == 0 && emitTrailingPoint) {
			valueWidth += emitTrailingZero ? 2 : 1;         // trailing point, possible zero as well
		}

		if (emitMinus || fo.isExplicitPlus() || fo.isSpaceWhenPositive()) {
			valueWidth++;                                   // '-', '+', or ' '
		}
		return valueWidth;
	}

	private int calculateExpWidth(
		int digitsLength, FormatOptions fo, ExponentPart exponentPart,
		boolean emitMinus, boolean emitTrailingPoint, boolean emitTrailingZero
	) {
		// length of digits + exponent digits + decimal point + exponent character
		int valueWidth = digitsLength + exponentPart.length() + ((digitsLength > 1) ? 2 : 1);
		if (emitMinus || fo.isExplicitPlus() || fo.isSpaceWhenPositive()) {
			valueWidth++;
		}
		if (digitsLength == 1 && emitTrailingPoint) {
			valueWidth += emitTrailingZero ? 2 : 1;
		}
		return valueWidth;
	}


	/**
	 * Computes a decimal representation with a fixed number of digits after the
	 * decimal point. The last emitted digit is rounded.
	 * <p/>
	 * <b>Examples:</b><br/>
	 * <code>
	 * toFixed(3.12, 1) -> "3.1"<br/>
	 * toFixed(3.1415, 3) -> "3.142"<br/>
	 * toFixed(1234.56789, 4) -> "1234.5679"<br/>
	 * toFixed(1.23, 5) -> "1.23000"<br/>
	 * toFixed(0.1, 4) -> "0.1000"<br/>
	 * toFixed(1e30, 2) -> "1000000000000000019884624838656.00"<br/>
	 * toFixed(0.1, 30) -> "0.100000000000000005551115123126"<br/>
	 * toFixed(0.1, 17) -> "0.10000000000000001"<br/>
	 * </code>
	 * <p/>
	 * If <code>requestedDigits</code> equals 0, then the tail of the result depends on
	 * the <code>EMIT_TRAILING_DECIMAL_POINT</code> and <code>EMIT_TRAILING_ZERO_AFTER_POINT</code>.
	 * <p/>
	 * Examples, for requestedDigits == 0,
	 * let <code>EMIT_TRAILING_DECIMAL_POINT</code> and <code>EMIT_TRAILING_ZERO_AFTER_POINT</code> be<br/>
	 * <table>
	 *   <tr><td>false and false: then</td> <td> 123.45 -> 123   </td></tr>
	 *   <tr><td/>                          <td> 0.678 -> 1      </td></tr>
	 *   <tr><td>true and false: then</td>  <td> 123.45 -> 123.  </td></tr>
	 *   <tr><td/>                          <td> 0.678 -> 1.     </td></tr>
	 *   <tr><td>true and true: then</td>   <td> 123.45 -> 123.0 </td></tr>
	 *   <tr><td/>                          <td> 0.678 -> 1.0    </td></tr>
	 * </table>
	 * <p/>
	 *
	 * @param requestedDigits the number of digits to the right of the decimal point, the last emitted digit is rounded
	 * @param formatOptions
	 * @throws IllegalArgumentException if <code>requestedDigits > MAX_FIXED_DIGITS_BEFORE_POINT</code> or
	 *                                  if <code>value > 10^MAX_FIXED_DIGITS_BEFORE_POINT</code>
	 *                                  <p/>
	 *                                  These two conditions imply that the result for non-special values
	 *                                  never contains more than<br/>
	 *                                  <code>1 + MAX_FIXED_DIGITS_BEFORE_POINT + 1 +
	 *                                  MAX_FIXED_DIGITS_AFTER_POINT</code><br/>
	 *                                  characters (one additional character for the sign, and one for the decimal point).
	 */
	public void toFixed(
		double value,
		int requestedDigits,
		FormatOptions formatOptions,
		Buffer resultBuilder
	) {
		// DOUBLE_CONVERSION_ASSERT(MAX_FIXED_DIGITS_BEFORE_POINT == 60);

		if (new Ieee.Double(value).isSpecial()) {
			handleSpecialValues(value, formatOptions, resultBuilder);
			return;
		}

		if (requestedDigits > MAX_FIXED_DIGITS_AFTER_POINT) {
			throw new IllegalArgumentException("requestedDigits too large. max: " + MAX_FIXED_DIGITS_BEFORE_POINT +
				"(MAX_FIXED_DIGITS_BEFORE_POINT) got: " + requestedDigits);
		}
		if (value > FIRST_NON_FIXED || value < -FIRST_NON_FIXED) {
			throw new IllegalArgumentException("value >= 10^" + MAX_FIXED_DIGITS_BEFORE_POINT +
				"(10^MAX_FIXED_DIGITS_BEFORE_POINT) got: " + value);
		}

		// Find a sufficiently precise decimal representation of n.
		DecimalRepBuf decimalRep = new DecimalRepBuf(FIXED_REP_CAPACITY);
		doubleToAscii(value, DtoaMode.FIXED, requestedDigits, decimalRep);

		createDecimalRepresentation(decimalRep, value, requestedDigits, formatOptions, resultBuilder);
	}

	/**
	 * Computes a representation in exponential format with <code>requestedDigits</code>
	 * after the decimal point. The last emitted digit is rounded.
	 * <p/>
	 * Examples with <b>EMIT_POSITIVE_EXPONENT_SIGN</b> deactivated, and
	 * exponent_character set to <code>'e'</code>.
	 * <p/>
	 * <code>
	 * toExponential(3.12, 1) -> "3.1e0"<br/>
	 * toExponential(5.0, 3) -> "5.000e0"<br/>
	 * toExponential(0.001, 2) -> "1.00e-3"<br/>
	 * toExponential(3.1415, 4) -> "3.1415e0"<br/>
	 * toExponential(3.1415, 3) -> "3.142e0"<br/>
	 * toExponential(123456789000000, 3) -> "1.235e14"<br/>
	 * toExponential(1000000000000000019884624838656.0, 32) ->
	 * "1.00000000000000001988462483865600e30"<br/>
	 * toExponential(1234, 0) -> "1e3"<br/>
	 * </code>
	 *
	 * @param requestedDigits number of digits after the decimal point(last digit rounded)
	 * @param formatOptions
	 * @throws IllegalArgumentException if <code>requestedDigits > MAX_EXPONENTIAL_DIGITS</code>
	 */
	public void toExponential(double value, int requestedDigits, FormatOptions formatOptions, Buffer resultBuilder) {
		if (new Ieee.Double(value).isSpecial()) {
			handleSpecialValues(value, formatOptions, resultBuilder);
			return;
		}

		if (requestedDigits < 0) {
			throw new IllegalArgumentException(
				String.format("requestedDigits must be >= 0. got: %d",
					requestedDigits));
		}

		if (requestedDigits > MAX_EXPONENTIAL_DIGITS) {
			throw new IllegalArgumentException(
				String.format("requestedDigits must be less than %d. got: %d",
					MAX_EXPONENTIAL_DIGITS, requestedDigits));
		}


		// DOUBLE_CONVERSION_ASSERT(EXPONENTIAL_REP_CAPACITY > BASE_10_MAXIMAL_LENGTH);
		DecimalRepBuf decimalRep = new DecimalRepBuf(EXPONENTIAL_REP_CAPACITY);

		doubleToAscii(value, DtoaMode.PRECISION, requestedDigits + 1,
			decimalRep);
		if (assertEnabled()) assertThat(decimalRep.length() <= requestedDigits + 1);

		decimalRep.zeroExtend(requestedDigits + 1);

		int exponent = decimalRep.getPointPosition() - 1;
		createExponentialRepresentation(decimalRep,
			value,
			decimalRep.length(),
			exponent,
			formatOptions,
			resultBuilder);
	}

	/**
	 * Computes 'precision' leading digits of the given 'value' and returns them
	 * either in exponential or decimal format, depending on
	 * PrecisionPolicy.max{Leading|Trailing}Zeros (given to the
	 * constructor).
	 * <p/>
	 * The last computed digit is rounded.
	 * </p>
	 * Example with PrecisionPolicy.maxLeadingZeros = 6.
	 * <p/>
	 * <code>
	 * toPrecision(0.0000012345, 2) -> "0.0000012"<br/>
	 * toPrecision(0.00000012345, 2) -> "1.2e-7"<br/>
	 * </code>
	 * <p/>
	 * Similarily the converter may add up to
	 * PrecisionPolicy.maxTrailingZeros in precision mode to avoid
	 * returning an exponential representation. A zero added by the
	 * <b>EMIT_TRAILING_ZERO_AFTER_POINT</b> flag is counted for this limit.
	 * <p/>
	 * Examples for PrecisionPolicy.maxTrailingZeros = 1:
	 * </p>
	 * <code>
	 * toPrecision(230.0, 2) -> "230"<br/>
	 * toPrecision(230.0, 2) -> "230."  with EMIT_TRAILING_DECIMAL_POINT.<br/>
	 * toPrecision(230.0, 2) -> "2.3e2" with EMIT_TRAILING_ZERO_AFTER_POINT.<br/>
	 * </code>
	 * <p/>
	 * Examples for PrecisionPolicy.maxTrailingZeros = 3, and no
	 * EMIT_TRAILING_ZERO_AFTER_POINT:
	 * </p>
	 * <code>
	 * toPrecision(123450.0, 6) -> "123450"<br/>
	 * toPrecision(123450.0, 5) -> "123450"<br/>
	 * toPrecision(123450.0, 4) -> "123500"<br/>
	 * toPrecision(123450.0, 3) -> "123000"<br/>
	 * toPrecision(123450.0, 2) -> "1.2e5"<br/>
	 * </code>
	 * <p/>
	 *
	 * @throws IllegalArgumentException when <code>precision < MIN_PRECISION_DIGITS</code> or
	 *                                  <code>precision > MAX_PRECISION_DIGITS</code>
	 */
	public void toPrecision(double value, int precision, FormatOptions formatOptions, Buffer resultBuilder) {
		if (new Ieee.Double(value).isSpecial()) {
			handleSpecialValues(value, formatOptions, resultBuilder);
			return;
		}

		if (precision < MIN_PRECISION_DIGITS || precision > MAX_PRECISION_DIGITS) {
			throw new IllegalArgumentException(String.format(
				"argument precision must be in range (%d,%d)", MIN_PRECISION_DIGITS, MAX_PRECISION_DIGITS));
		}

		// Find a sufficiently precise decimal representation of n.
		// Add one for the terminating null character.
		DecimalRepBuf decimalRep = new DecimalRepBuf(PRECISION_REP_CAPACITY);
		doubleToAscii(value, DtoaMode.PRECISION, precision, decimalRep);
		if (assertEnabled()) assertThat(decimalRep.length() <= precision);

		// The exponent if we print the number as x.xxeyyy. That is with the
		// decimal point after the first digit.
		int decimalPoint = decimalRep.getPointPosition();
		int exponent = decimalPoint - 1;

		int extraZero = ((flags & Flags.EMIT_TRAILING_ZERO_AFTER_POINT) != 0) ? 1 : 0;
		boolean asExponential =
			(-decimalPoint + 1 > precisionPolicy.getMaxLeadingZeroes()) ||
				(decimalPoint - precision + extraZero >
					precisionPolicy.getMaxTrailingZeroes());
		if ((flags & Flags.NO_TRAILING_ZERO) != 0 && !formatOptions.isAlternateForm()) {
			// Truncate trailing zeros that occur after the decimal point (if exponential,
			// that is everything after the first digit).
			decimalRep.truncateZeros(asExponential);
			// Clamp precision to avoid the code below re-adding the zeros.
			precision = Math.min(precision, decimalRep.length());
		}
		if (asExponential) {
			// Fill buffer to contain 'precision' digits.
			// Usually the buffer is already at the correct length, but 'doubleToAscii'
			// is allowed to return less characters.
			decimalRep.zeroExtend(precision);

			createExponentialRepresentation(decimalRep,
				value,
				precision,
				exponent,
				formatOptions,
				resultBuilder);
		} else {
			createDecimalRepresentation(decimalRep,
				value,
				Math.max(0, precision - decimalRep.getPointPosition()),
				formatOptions,
				resultBuilder);
		}
	}

	@SuppressWarnings("operation.mixed.unsignedrhs")
	private boolean shouldEmitMinus(double value) {
		boolean uniqueZero = ((flags & Flags.UNIQUE_ZERO) != 0);
		return (Double.doubleToRawLongBits(value) & Ieee.Double.SIGN_MASK) != 0 &&
			(value != 0.0 || !uniqueZero);
	}

	private void appendSign(double value, FormatOptions formatOptions, Buffer resultBuilder) {
		if (shouldEmitMinus(value)) {
			resultBuilder.append('-');
		} else if (formatOptions.isSpaceWhenPositive()) {
			resultBuilder.append(' ');
		} else if (formatOptions.isExplicitPlus()) {
			resultBuilder.append('+');
		}
	}

	public enum DtoaMode {
		// Produce a fixed number of digits after the decimal point.
		// For instance fixed(0.1, 4) becomes 0.1000
		// If the input number is big, the output will be big.
		FIXED,
		// Fixed number of digits (independent of the decimal point).
		PRECISION
	}

	private static BigNumDtoa.BignumDtoaMode dtoaToBignumDtoaMode(DoubleToStringConverter.DtoaMode dtoaMode) {
		switch (dtoaMode) {
			case FIXED: return BigNumDtoa.BignumDtoaMode.FIXED;
			case PRECISION: return BigNumDtoa.BignumDtoaMode.PRECISION;
			default:
				throw new IllegalStateException("Unreachable");
		}
	}

	/**
	 * Converts the given double <code>v</code> to digit characters. <code>v</code> must
	 * not be <code>NaN</code>, <code>+Infinity</code>, or <code>-Infinity</code>.
	 * <p/>
	 * The result should be interpreted as <code>buffer * 10^(outPoint-outLength)</code>.
	 * <p/>
	 * The digits are written to the  <code>buffer</code> in the platform's charset, which is
	 * often UTF-8 (with ASCII-range digits) but may be another charset, such
	 * as EBCDIC.
	 * <p/>
	 * The output depends on the given mode:<br/>
	 * <ul>
	 *  <li>
	 *     {@link DtoaMode#FIXED FIXED}: produces digits necessary to print a given number with
	 *    'requestedDigits' digits after the decimal outPoint. The produced digits
	 *    might be too short in which case the caller has to fill the remainder
	 *    with '0's.
	 *    <p/>
	 *    <b>Example:</b> toFixed(0.001, 5) is allowed to return  <code>buffer="1", outPoint=-2</code>.
	 *    <p/>
	 *    Halfway cases are rounded towards +/-Infinity (away from 0). The call
	 *    toFixed(0.15, 2) thus returns  buffer="2", outPoint=0.
	 *    <p/>
	 *    The returned buffer may contain digits that would be truncated from the
	 *    shortest representation of the input.
	 *    <p/>
	 *  </li>
	 *  <li>
	 *    {@link DtoaMode#PRECISION PRECISION}: produces 'requestedDigits' where the first digit is not '0'.
	 *    Even though the outLength of produced digits usually equals
	 *    'requestedDigits', the function is allowed to return fewer digits, in
	 *    which case the caller has to fill the missing digits with '0's.
	 *    <p/>
	 *    Halfway cases are again rounded away from 0.
	 *  </li>
	 * </ul>
	 * <p/>
	 * <code>doubleToAscii</code> expects the given buffer to be big enough to hold all
	 *   digits. The requestedDigits parameter and the padding-zeroes limit the size of the
	 *   output. Don't forget the decimal point and the exponent character when
	 *   computing the maximal output size.
	 *
	 * @param v               the value to be converted to digits
	 *                        <p/>
	 * @param mode            the {@link DtoaMode DtoaMode} used for the conversion
	 *                        <p/>
	 * @param requestedDigits for <b>FIXED</b> the number of digits after teh decimal point,
	 *                        for <b>PRECISION</b> the number of digits where the first digit is not '0',
	 *                        <p/>
	 * @param buffer          the {@link DecimalRepBuf} initialized with enough space for the conversion(explained above). On
	 *                        successful completion this buffer contains the digits,
	 *                        the {@link DecimalRepBuf#getPointPosition() pointPosition},
	 *                        and the {@link DecimalRepBuf#getSign() sign} of the number.
	 */
	public static void doubleToAscii(double v, DtoaMode mode, int requestedDigits, DecimalRepBuf buffer) {
		if (assertEnabled()) requireArg(!new Ieee.Double(v).isSpecial(), "value can't be a special value");
		requireArg(requestedDigits >= 0, "requestedDigits must be >= 0");

		// begin with an empty buffer
		buffer.reset();

		if (new Ieee.Double(v).sign() < 0) {
			buffer.setSign(true);
			v = -v;
		} else {
			buffer.setSign(false);
		}

		if (mode == DtoaMode.PRECISION && requestedDigits == 0) {
			return;
		}

		if (v == 0.0) {
			buffer.append(0);
			buffer.setPointPosition(1);
			return;
		}

		boolean fastWorked;
		switch (mode) {
			case FIXED:
				fastWorked = FixedDtoa.fastFixedDtoa(v, requestedDigits, buffer);
				break;
			case PRECISION:
				fastWorked = FastDtoa.fastDtoa(v, requestedDigits, buffer);
				break;
			default:
				throw new IllegalStateException("Unreachable");
		}
		if (fastWorked) return;

		buffer.reset();
		// If the fast dtoa didn't succeed use the slower bignum version.
		BigNumDtoa.BignumDtoaMode dtoaMode = dtoaToBignumDtoaMode(mode);
		BigNumDtoa.bignumDtoa(v, dtoaMode, requestedDigits, buffer);
	}

	/**
	 * Add character padding to the builder. If count is non-positive,
	 * nothing is added to the builder.
	 */
	private static void addPadding(Buffer sb, @Unsigned int character, int count) {
		for (int i = count; i > 0; i--) {
			sb.append((char) character);
		}
	}

	/**
	 * Parameter object for the symbols used during conversion.
	 *
	 * @see #Symbols(String, String, int)
	 */
	public static class Symbols {
		private final String infinitySymbol;
		private final String nanSymbol;
		private final @Unsigned int exponentCharacter;

		/**
		 * Construct a symbols parameter object
		 * <p/>
		 * <code>infinitySymbol</code> and <code>nanSymbol</code> provide the string representation for these
		 * special values. If the string is NULL and the special value is encountered
		 * then the conversion functions return false.
		 * <p/>
		 * The <code>exponentCharacter</code> is used in exponential representations. It is
		 * usually 'e' or 'E'.<br/>
		 *
		 * @param infinitySymbol    string representation of 'infinity' special value
		 * @param nanSymbol         string representation of 'NaN' special value
		 * @param exponentCharacter used in exponential representations, it is usually 'e' or 'E'
		 */
		public Symbols(String infinitySymbol, String nanSymbol, @Unsigned int exponentCharacter) {
			this.infinitySymbol = requireNonNull(infinitySymbol);
			this.nanSymbol = requireNonNull(nanSymbol);
			this.exponentCharacter = exponentCharacter;
		}

		public String getInfinitySymbol() {
			return infinitySymbol;
		}

		public String getNanSymbol() {
			return nanSymbol;
		}

		public @Unsigned int getExponentCharacter() {
			return exponentCharacter;
		}
	}

	/**
	 * Parameter object configuring usage of {@link DoubleToStringConverter#toPrecision}
	 *
	 * @see #PrecisionPolicy(int, int)
	 */
	public static class PrecisionPolicy {
		private final int maxLeadingZeroes;
		private final int maxTrailingZeroes;

		/**
		 * When converting to precision mode the converter may add
		 * max_leading_padding_zeroes before returning the number in exponential
		 * format.
		 * <p/>
		 * Example with maxLeadingZeroes = 6.<br/>
		 * <code>
		 * toPrecision(0.0000012345, 2) -> "0.0000012"<br/>
		 * toPrecision(0.00000012345, 2) -> "1.2e-7"<br/>
		 * </code>
		 * <p/>
		 * Similarily the converter may add up to
		 * maxTrailingZeroes in precision mode to avoid
		 * returning an exponential representation. A zero added by the
		 * <code>EMIT_TRAILING_ZERO_AFTER_POINT</code> flag is counted for this limit.
		 * <p/>
		 * Examples for maxTrailingZeroes = 1:<br/>
		 * <code>
		 * toPrecision(230.0, 2) -> "230"<br/>
		 * toPrecision(230.0, 2) -> "230."  with EMIT_TRAILING_DECIMAL_POINT.<br/>
		 * toPrecision(230.0, 2) -> "2.3e2" with EMIT_TRAILING_ZERO_AFTER_POINT.<br/>
		 * </code>
		 *
		 * @param maxLeadingZeroes  Maximum allowed leading zeros before switching to exponential representation
		 * @param maxTrailingZeroes Maximum allowed trailing zeros before switching to exponential representation
		 */
		public PrecisionPolicy(int maxLeadingZeroes, int maxTrailingZeroes) {
			this.maxLeadingZeroes = maxLeadingZeroes;
			this.maxTrailingZeroes = maxTrailingZeroes;
		}

		public int getMaxLeadingZeroes() {
			return maxLeadingZeroes;
		}

		public int getMaxTrailingZeroes() {
			return maxTrailingZeroes;
		}
	}

	public static class FormatOptions {
		private final Symbols symbols;
		private final boolean explicitPlus;
		private final boolean spaceWhenPositive;
		private final boolean alternateForm;
		private final int width;
		private final boolean zeroPad;
		private final boolean leftAdjust;

		/**
		 * @param explicitPlus  if true, and the number is positive a '+' is emitted before the number
		 * @param alternateForm if true, the formatted output will end with '.' even if fractional part is zero,
		 *                      also turns off {@link Flags#NO_TRAILING_ZERO} flag
		 * @param width         total width to pad the number or <code>-1</code> if no padding requested
		 * @param zeroPad       pad with zeros instead of spaces
		 * @param leftAdjust    add padding to the end of instead of the beginning
		 */
		public FormatOptions(Symbols symbols,
							 boolean explicitPlus,
							 boolean spaceWhenPositive,
							 boolean alternateForm,
							 int width,
							 boolean zeroPad,
							 boolean leftAdjust) {
			this.symbols = symbols;
			this.explicitPlus = explicitPlus;
			this.spaceWhenPositive = spaceWhenPositive;
			this.alternateForm = alternateForm;
			this.width = width;
			this.zeroPad = zeroPad;
			this.leftAdjust = leftAdjust;
		}

		public Symbols getSymbols() {
			return symbols;
		}

		public boolean isExplicitPlus() {
			return explicitPlus;
		}

		public boolean isSpaceWhenPositive() {
			return spaceWhenPositive;
		}

		public boolean isAlternateForm() {
			return alternateForm;
		}

		public int getWidth() {
			return width;
		}

		public boolean isZeroPad() {
			return zeroPad;
		}

		public boolean isLeftAdjust() {
			return leftAdjust;
		}
	}

	private static class ExponentPart {
		private final char[] buffer;
		private final int start;
		private final int length;

		public ExponentPart(char[] buffer, int start, int length) {
			this.buffer = buffer;
			this.start = start;
			this.length = length;
		}

		public char[] getBuffer() {
			return buffer;
		}

		public int getStart() {
			return start;
		}

		public int length() {
			return length;
		}

		@Override
		public String toString() {
			return String.valueOf(buffer, start, length);
		}
	}
}
