/*
 * Copyright (c) 1996, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * Portions Copyright IBM Corporation, 2001. All Rights Reserved.
 */

package java.math;

import static java.math.BigInteger.LONG_MASK;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectStreamException;
import java.io.StreamCorruptedException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

import jdk.internal.access.JavaLangAccess;
import jdk.internal.access.SharedSecrets;
import jdk.internal.math.FormattedFPDecimal;
import jdk.internal.util.DecimalDigits;
import jdk.internal.vm.annotation.Stable;

/**
 * Immutable, arbitrary-precision signed decimal numbers.  A {@code
 * BigDecimal} consists of an arbitrary precision integer
 * <i>{@linkplain unscaledValue() unscaled value}</i> and a 32-bit
 * integer <i>{@linkplain scale() scale}</i>.  If the
 * scale is zero or positive, the scale is the number of digits to
 * the right of the decimal point.  If the scale is negative, the
 * unscaled value of the number is multiplied by ten to the power of
 * the negation of the scale.  The value of the number represented by
 * the {@code BigDecimal} is therefore
 * <code>(unscaledValue &times; 10<sup>-scale</sup>)</code>.
 *
 * <p>The {@code BigDecimal} class provides operations for
 * arithmetic, scale manipulation, rounding, comparison, hashing, and
 * format conversion.  The {@link #toString} method provides a
 * canonical representation of a {@code BigDecimal}.
 *
 * <p>The {@code BigDecimal} class gives its user complete control
 * over rounding behavior.  If no rounding mode is specified and the
 * exact result cannot be represented, an {@code ArithmeticException}
 * is thrown; otherwise, calculations can be carried out to a chosen
 * precision and rounding mode by supplying an appropriate {@link
 * MathContext} object to the operation.  In either case, eight
 * <em>rounding modes</em> are provided for the control of rounding.
 * Using the integer fields in this class (such as {@link
 * #ROUND_HALF_UP}) to represent rounding mode is deprecated; the
 * enumeration values of the {@code RoundingMode} {@code enum}, (such
 * as {@link RoundingMode#HALF_UP}) should be used instead.
 *
 * <p>When a {@code MathContext} object is supplied with a precision
 * setting of 0 (for example, {@link MathContext#UNLIMITED}),
 * arithmetic operations are exact, as are the arithmetic methods
 * which take no {@code MathContext} object. As a corollary of
 * computing the exact result, the rounding mode setting of a {@code
 * MathContext} object with a precision setting of 0 is not used and
 * thus irrelevant.  In the case of divide, the exact quotient could
 * have an infinitely long decimal expansion; for example, 1 divided
 * by 3.  If the quotient has a nonterminating decimal expansion and
 * the operation is specified to return an exact result, an {@code
 * ArithmeticException} is thrown.  Otherwise, the exact result of the
 * division is returned, as done for other operations.
 *
 * <p>When the precision setting is not 0, the rules of {@code
 * BigDecimal} arithmetic are broadly compatible with selected modes
 * of operation of the arithmetic defined in ANSI X3.274-1996 and ANSI
 * X3.274-1996/AM 1-2000 (section 7.4).  Unlike those standards,
 * {@code BigDecimal} includes many rounding modes.  Any conflicts
 * between these ANSI standards and the {@code BigDecimal}
 * specification are resolved in favor of {@code BigDecimal}.
 *
 * <p>Since the same numerical value can have different
 * representations (with different scales), the rules of arithmetic
 * and rounding must specify both the numerical result and the scale
 * used in the result's representation.
 *
 * The different representations of the same numerical value are
 * called members of the same <i>cohort</i>. The {@linkplain
 * compareTo(BigDecimal) natural order} of {@code BigDecimal}
 * considers members of the same cohort to be equal to each other. In
 * contrast, the {@link equals equals} method requires both the
 * numerical value and representation to be the same for equality to
 * hold. The results of methods like {@link #scale()} and {@link
 * #unscaledValue()} will differ for numerically equal values with
 * different representations.
 *
 * <p>In general the rounding modes and precision setting determine
 * how operations return results with a limited number of digits when
 * the exact result has more digits (perhaps infinitely many in the
 * case of division and square root) than the number of digits returned.
 *
 * First, the total number of digits to return is specified by the
 * {@code MathContext}'s {@code precision} setting; this determines
 * the result's <i>precision</i>.  The digit count starts from the
 * leftmost nonzero digit of the exact result.  The rounding mode
 * determines how any discarded trailing digits affect the returned
 * result.
 *
 * <p>For all arithmetic operators, the operation is carried out as
 * though an exact intermediate result were first calculated and then
 * rounded to the number of digits specified by the precision setting
 * (if necessary), using the selected rounding mode.  If the exact
 * result is not returned, some digit positions of the exact result
 * are discarded.  When rounding increases the magnitude of the
 * returned result, it is possible for a new digit position to be
 * created by a carry propagating to a leading {@literal "9"} digit.
 * For example, rounding the value 999.9 to three digits rounding up
 * would be numerically equal to one thousand, represented as
 * 100&times;10<sup>1</sup>.  In such cases, the new {@literal "1"} is
 * the leading digit position of the returned result.
 *
 * <p>For methods and constructors with a {@code MathContext}
 * parameter, if the result is inexact but the rounding mode is {@link
 * RoundingMode#UNNECESSARY UNNECESSARY}, an {@code
 * ArithmeticException} will be thrown.
 *
 * <p>Besides a logical exact result, each arithmetic operation has a
 * preferred scale for representing a result.  The preferred
 * scale for each operation is listed in the table below.
 *
 * <table class="striped" style="text-align:left">
 * <caption>Preferred Scales for Results of Arithmetic Operations
 * </caption>
 * <thead>
 * <tr><th scope="col">Operation</th><th scope="col">Preferred Scale of Result</th></tr>
 * </thead>
 * <tbody>
 * <tr><th scope="row">Add</th><td>max(addend.scale(), augend.scale())</td>
 * <tr><th scope="row">Subtract</th><td>max(minuend.scale(), subtrahend.scale())</td>
 * <tr><th scope="row">Multiply</th><td>multiplier.scale() + multiplicand.scale()</td>
 * <tr><th scope="row">Divide</th><td>dividend.scale() - divisor.scale()</td>
 * <tr><th scope="row">Square root</th><td>radicand.scale()/2</td>
 * </tbody>
 * </table>
 *
 * These scales are the ones used by the methods which return exact
 * arithmetic results; except that an exact divide may have to use a
 * larger scale since the exact result may have more digits.  For
 * example, {@code 1/32} is {@code 0.03125}.
 *
 * <p>Before rounding, the scale of the logical exact intermediate
 * result is the preferred scale for that operation.  If the exact
 * numerical result cannot be represented in {@code precision}
 * digits, rounding selects the set of digits to return and the scale
 * of the result is reduced from the scale of the intermediate result
 * to the least scale which can represent the {@code precision}
 * digits actually returned.  If the exact result can be represented
 * with at most {@code precision} digits, the representation
 * of the result with the scale closest to the preferred scale is
 * returned.  In particular, an exactly representable quotient may be
 * represented in fewer than {@code precision} digits by removing
 * trailing zeros and decreasing the scale.  For example, rounding to
 * three digits using the {@linkplain RoundingMode#FLOOR floor}
 * rounding mode, <br>
 *
 * {@code 19/100 = 0.19   // integer=19,  scale=2} <br>
 *
 * but<br>
 *
 * {@code 21/110 = 0.190  // integer=190, scale=3} <br>
 *
 * <p>Note that for add, subtract, and multiply, the reduction in
 * scale will equal the number of digit positions of the exact result
 * which are discarded. If the rounding causes a carry propagation to
 * create a new high-order digit position, an additional digit of the
 * result is discarded than when no new digit position is created.
 *
 * <p>Other methods may have slightly different rounding semantics.
 * For example, the result of the {@code pow} method using the
 * {@linkplain #pow(int, MathContext) specified algorithm} can
 * occasionally differ from the rounded mathematical result by more
 * than one unit in the last place, one <i>{@linkplain #ulp() ulp}</i>.
 *
 * <p>Two types of operations are provided for manipulating the scale
 * of a {@code BigDecimal}: scaling/rounding operations and decimal
 * point motion operations.  Scaling/rounding operations ({@link
 * #setScale setScale} and {@link #round round}) return a
 * {@code BigDecimal} whose value is approximately (or exactly) equal
 * to that of the operand, but whose scale or precision is the
 * specified value; that is, they increase or decrease the precision
 * of the stored number with minimal effect on its value.  Decimal
 * point motion operations ({@link #movePointLeft movePointLeft} and
 * {@link #movePointRight movePointRight}) return a
 * {@code BigDecimal} created from the operand by moving the decimal
 * point a specified distance in the specified direction.
 *
 * <p>As a 32-bit integer, the set of values for the scale is large,
 * but bounded. If the scale of a result would exceed the range of a
 * 32-bit integer, either by overflow or underflow, the operation may
 * throw an {@code ArithmeticException}.
 *
 * <p>For the sake of brevity and clarity, pseudo-code is used
 * throughout the descriptions of {@code BigDecimal} methods.  The
 * pseudo-code expression {@code (i + j)} is shorthand for "a
 * {@code BigDecimal} whose value is that of the {@code BigDecimal}
 * {@code i} added to that of the {@code BigDecimal}
 * {@code j}." The pseudo-code expression {@code (i == j)} is
 * shorthand for "{@code true} if and only if the
 * {@code BigDecimal} {@code i} represents the same value as the
 * {@code BigDecimal} {@code j}." Other pseudo-code expressions
 * are interpreted similarly.  Square brackets are used to represent
 * the particular {@code BigInteger} and scale pair defining a
 * {@code BigDecimal} value; for example [19, 2] is the
 * {@code BigDecimal} numerically equal to 0.19 having a scale of 2.
 *
 * <p>All methods and constructors for this class throw
 * {@code NullPointerException} when passed a {@code null} object
 * reference for any input parameter.
 *
 * @apiNote Care should be exercised if {@code BigDecimal} objects are
 * used as keys in a {@link java.util.SortedMap SortedMap} or elements
 * in a {@link java.util.SortedSet SortedSet} since {@code
 * BigDecimal}'s <i>{@linkplain compareTo(BigDecimal) natural
 * ordering}</i> is <em>inconsistent with equals</em>.  See {@link
 * Comparable}, {@link java.util.SortedMap} or {@link
 * java.util.SortedSet} for more information.
 *
 * <h2>Relation to IEEE 754 Decimal Arithmetic</h2>
 *
 * Starting with its 2008 revision, the <cite>IEEE 754 Standard for
 * Floating-point Arithmetic</cite> has covered decimal formats and
 * operations. While there are broad similarities in the decimal
 * arithmetic defined by IEEE 754 and by this class, there are notable
 * differences as well. The fundamental similarity shared by {@code
 * BigDecimal} and IEEE 754 decimal arithmetic is the conceptual
 * operation of computing the mathematical infinitely precise real
 * number value of an operation and then mapping that real number to a
 * representable decimal floating-point value under a <em>rounding
 * policy</em>. The rounding policy is called a {@linkplain
 * RoundingMode rounding mode} for {@code BigDecimal} and called a
 * rounding-direction attribute in IEEE 754-2019. When the exact value
 * is not representable, the rounding policy determines which of the
 * two representable decimal values bracketing the exact value is
 * selected as the computed result. The notion of a <em>preferred
 * scale/preferred exponent</em> is also shared by both systems.
 *
 * <p>For differences, IEEE 754 includes several kinds of values not
 * modeled by {@code BigDecimal} including negative zero, signed
 * infinities, and NaN (not-a-number). IEEE 754 defines formats, which
 * are parameterized by base (binary or decimal), number of digits of
 * precision, and exponent range. A format determines the set of
 * representable values. Most operations accept as input one or more
 * values of a given format and produce a result in the same format.
 * A {@code BigDecimal}'s {@linkplain scale() scale} is equivalent to
 * negating an IEEE 754 value's exponent. {@code BigDecimal} values do
 * not have a format in the same sense; all values have the same
 * possible range of scale/exponent and the {@linkplain
 * unscaledValue() unscaled value} has arbitrary precision. Instead,
 * for the {@code BigDecimal} operations taking a {@code MathContext}
 * parameter, if the {@code MathContext} has a nonzero precision, the
 * set of possible representable values for the result is determined
 * by the precision of the {@code MathContext} argument. For example
 * in {@code BigDecimal}, if a nonzero three-digit number and a
 * nonzero four-digit number are multiplied together in the context of
 * a {@code MathContext} object having a precision of three, the
 * result will have three digits (assuming no overflow or underflow,
 * etc.).
 *
 * <p>The rounding policies implemented by {@code BigDecimal}
 * operations indicated by {@linkplain RoundingMode rounding modes}
 * are a proper superset of the IEEE 754 rounding-direction
 * attributes.
 *
 * <p>{@code BigDecimal} arithmetic will most resemble IEEE 754
 * decimal arithmetic if a {@code MathContext} corresponding to an
 * IEEE 754 decimal format, such as {@linkplain MathContext#DECIMAL64
 * decimal64} or {@linkplain MathContext#DECIMAL128 decimal128} is
 * used to round all starting values and intermediate operations. The
 * numerical values computed can differ if the exponent range of the
 * IEEE 754 format being approximated is exceeded since a {@code
 * MathContext} does not constrain the scale of {@code BigDecimal}
 * results. Operations that would generate a NaN or exact infinity,
 * such as dividing by zero, throw an {@code ArithmeticException} in
 * {@code BigDecimal} arithmetic.
 *
 * <h2><a id=algorithmicComplexity>Algorithmic Complexity</a></h2>
 *
 * Operations on {@code BigDecimal} values have a range of algorithmic
 * complexities; in general, those complexities are a function of both
 * the size of the unscaled value as well as the size of the
 * scale. For example, an {@linkplain BigDecimal#multiply(BigDecimal)
 * exact multiply} of two {@code BigDecimal} values is subject to the
 * same {@linkplain BigInteger##algorithmicComplexity complexity
 * constraints} as {@code BigInteger} multiply of the unscaled
 * values. In contrast, a {@code BigDecimal} value with a compact
 * representation like {@code new BigDecimal(1E-1000000000)} has a
 * {@link toPlainString} result with over one billion characters.
 *
 * <p>Operations may also allocate and compute on intermediate
 * results, potentially those allocations may be as large as in
 * proportion to the running time of the algorithm.
 *
 * <p>Users of {@code BigDecimal} concerned with bounding the running
 * time or space of operations can screen out {@code BigDecimal}
 * values with unscaled values or scales above a chosen magnitude.
 *
 * @see BigInteger
 * @see MathContext
 * @see RoundingMode
 * @see java.util.SortedMap
 * @see java.util.SortedSet
 * @spec https://standards.ieee.org/ieee/754/6210/
 *       IEEE Standard for Floating-Point Arithmetic
 *
 * @author  Josh Bloch
 * @author  Mike Cowlishaw
 * @author  Joseph D. Darcy
 * @author  Sergey V. Kuksenko
 * @since 1.1
 */
public class BigDecimal extends Number implements Comparable<BigDecimal> {
    private static final JavaLangAccess JLA = SharedSecrets.getJavaLangAccess();

    /*
     * Let l = log_2(10).
     * Then, L < l < L + ulp(L) / 2, that is, L = roundTiesToEven(l).
     */
    private static final double L = 3.321928094887362;

    private static final int P_F = Float.PRECISION;  // 24
    private static final int Q_MIN_F = Float.MIN_EXPONENT - (P_F - 1);  // -149
    private static final int Q_MAX_F = Float.MAX_EXPONENT - (P_F - 1);  // 104

    private static final int P_D = Double.PRECISION;  // 53
    private static final int Q_MIN_D = (Double.MIN_EXPONENT - (P_D - 1));  // -1_074
    private static final int Q_MAX_D = (Double.MAX_EXPONENT - (P_D - 1));  // 971

    /**
     * The unscaled value of this BigDecimal, as returned by {@link
     * #unscaledValue}.
     *
     * @serial
     * @see #unscaledValue
     */
    private final BigInteger intVal;

    /**
     * The scale of this BigDecimal, as returned by {@link #scale()}.
     *
     * @serial
     * @see #scale()
     */
    private final int scale;  // Note: this may have any value, so
                              // calculations must be done in longs

    /**
     * The number of decimal digits in this BigDecimal, or 0 if the
     * number of digits are not known (lookaside information).  If
     * nonzero, the value is guaranteed correct.  Use the precision()
     * method to obtain and set the value if it might be 0.  This
     * field is mutable until set nonzero.
     *
     * @since  1.5
     */
    private transient int precision;

    /**
     * Used to store the canonical string representation, if computed.
     */
    private transient String stringCache;

    /**
     * Sentinel value for {@link #intCompact} indicating the
     * significand information is only available from {@code intVal}.
     */
    static final long INFLATED = Long.MIN_VALUE;

    private static final BigInteger INFLATED_BIGINT = BigInteger.valueOf(INFLATED);

    /**
     * If the absolute value of the significand of this BigDecimal is
     * less than or equal to {@code Long.MAX_VALUE}, the value can be
     * compactly stored in this field and used in computations.
     */
    private final transient long intCompact;

    // All 18-digit base ten strings fit into a long; not all 19-digit
    // strings will
    private static final int MAX_COMPACT_DIGITS = 18;

    /* Appease the serialization gods */
    @java.io.Serial
    private static final long serialVersionUID = 6108874887143696463L;

    // Cache of common small BigDecimal values.
    @Stable
    private static final BigDecimal[] ZERO_THROUGH_TEN = {
        new BigDecimal(BigInteger.ZERO,       0,  0, 1),
        new BigDecimal(BigInteger.ONE,        1,  0, 1),
        new BigDecimal(BigInteger.TWO,        2,  0, 1),
        new BigDecimal(BigInteger.valueOf(3), 3,  0, 1),
        new BigDecimal(BigInteger.valueOf(4), 4,  0, 1),
        new BigDecimal(BigInteger.valueOf(5), 5,  0, 1),
        new BigDecimal(BigInteger.valueOf(6), 6,  0, 1),
        new BigDecimal(BigInteger.valueOf(7), 7,  0, 1),
        new BigDecimal(BigInteger.valueOf(8), 8,  0, 1),
        new BigDecimal(BigInteger.valueOf(9), 9,  0, 1),
        new BigDecimal(BigInteger.TEN,        10, 0, 2),
    };

    // Cache of zero scaled by 0 - 15
    @Stable
    private static final BigDecimal[] ZERO_SCALED_BY = {
        ZERO_THROUGH_TEN[0],
        new BigDecimal(BigInteger.ZERO, 0, 1, 1),
        new BigDecimal(BigInteger.ZERO, 0, 2, 1),
        new BigDecimal(BigInteger.ZERO, 0, 3, 1),
        new BigDecimal(BigInteger.ZERO, 0, 4, 1),
        new BigDecimal(BigInteger.ZERO, 0, 5, 1),
        new BigDecimal(BigInteger.ZERO, 0, 6, 1),
        new BigDecimal(BigInteger.ZERO, 0, 7, 1),
        new BigDecimal(BigInteger.ZERO, 0, 8, 1),
        new BigDecimal(BigInteger.ZERO, 0, 9, 1),
        new BigDecimal(BigInteger.ZERO, 0, 10, 1),
        new BigDecimal(BigInteger.ZERO, 0, 11, 1),
        new BigDecimal(BigInteger.ZERO, 0, 12, 1),
        new BigDecimal(BigInteger.ZERO, 0, 13, 1),
        new BigDecimal(BigInteger.ZERO, 0, 14, 1),
        new BigDecimal(BigInteger.ZERO, 0, 15, 1),
    };

    // Half of Long.MIN_VALUE & Long.MAX_VALUE.
    private static final long HALF_LONG_MAX_VALUE = Long.MAX_VALUE / 2;
    private static final long HALF_LONG_MIN_VALUE = Long.MIN_VALUE / 2;

    // Constants
    /**
     * The value 0, with a scale of 0.
     *
     * @since  1.5
     */
    public static final BigDecimal ZERO =
        ZERO_THROUGH_TEN[0];

    /**
     * The value 1, with a scale of 0.
     *
     * @since  1.5
     */
    public static final BigDecimal ONE =
        ZERO_THROUGH_TEN[1];

    /**
     * The value 2, with a scale of 0.
     *
     * @since  19
     */
    public static final BigDecimal TWO =
        ZERO_THROUGH_TEN[2];

    /**
     * The value 10, with a scale of 0.
     *
     * @since  1.5
     */
    public static final BigDecimal TEN =
        ZERO_THROUGH_TEN[10];

    // Constructors

    /**
     * Trusted package private constructor.
     * Trusted simply means if val is INFLATED, intVal could not be null and
     * if intVal is null, val could not be INFLATED.
     */
    BigDecimal(BigInteger intVal, long val, int scale, int prec) {
        this.scale = scale;
        this.precision = prec;
        this.intCompact = val;
        this.intVal = intVal;
    }

    /**
     * Translates a character array representation of a
     * {@code BigDecimal} into a {@code BigDecimal}, accepting the
     * same sequence of characters as the {@link #BigDecimal(String)}
     * constructor, while allowing a sub-array to be specified.
     *
     * @implNote If the sequence of characters is already available
     * within a character array, using this constructor is faster than
     * converting the {@code char} array to string and using the
     * {@code BigDecimal(String)} constructor.
     *
     * @param  in {@code char} array that is the source of characters.
     * @param  offset first character in the array to inspect.
     * @param  len number of characters to consider.
     * @throws NumberFormatException if {@code in} is not a valid
     *         representation of a {@code BigDecimal} or the defined subarray
     *         is not wholly within {@code in}.
     * @since  1.5
     */
    public BigDecimal(char[] in, int offset, int len) {
        this(in,offset,len,MathContext.UNLIMITED);
    }

    /**
     * Translates a character array representation of a
     * {@code BigDecimal} into a {@code BigDecimal}, accepting the
     * same sequence of characters as the {@link #BigDecimal(String)}
     * constructor, while allowing a sub-array to be specified and
     * with rounding according to the context settings.
     *
     * @implNote If the sequence of characters is already available
     * within a character array, using this constructor is faster than
     * converting the {@code char} array to string and using the
     * {@code BigDecimal(String)} constructor.
     *
     * @param  in {@code char} array that is the source of characters.
     * @param  offset first character in the array to inspect.
     * @param  len number of characters to consider.
     * @param  mc the context to use.
     * @throws NumberFormatException if {@code in} is not a valid
     *         representation of a {@code BigDecimal} or the defined subarray
     *         is not wholly within {@code in}.
     * @since  1.5
     */
    public BigDecimal(char[] in, int offset, int len, MathContext mc) {
        // protect against huge length, negative values, and integer overflow
        try {
            Objects.checkFromIndexSize(offset, len, in.length);
        } catch (IndexOutOfBoundsException e) {
            throw new NumberFormatException
                ("Bad offset or len arguments for char[] input.");
        }

        // This is the primary string to BigDecimal constructor; all
        // incoming strings end up here; it uses explicit (inline)
        // parsing for speed and generates at most one intermediate
        // (temporary) object (a char[] array) for non-compact case.

        // Use locals for all fields values until completion
        int prec = 0;                 // record precision value
        long scl = 0;                 // record scale value
        long rs = 0;                  // the compact value in long
        BigInteger rb = null;         // the inflated value in BigInteger
        // use array bounds checking to handle too-long, len == 0,
        // bad offset, etc.
        try {
            // handle the sign
            boolean isneg = false;          // assume positive
            if (in[offset] == '-') {
                isneg = true;               // leading minus means negative
                offset++;
                len--;
            } else if (in[offset] == '+') { // leading + allowed
                offset++;
                len--;
            }

            // should now be at numeric part of the significand
            boolean dot = false;             // true when there is a '.'
            char c;                          // current character
            boolean isCompact = (len <= MAX_COMPACT_DIGITS);
            // integer significand array & idx is the index to it. The array
            // is ONLY used when we can't use a compact representation.
            int idx = 0;
            if (isCompact) {
                // First compact case, we need not to preserve the character
                // and we can just compute the value in place.
                for (; len > 0; offset++, len--) {
                    c = in[offset];
                    if ((c == '0')) { // have zero
                        if (prec == 0)
                            prec = 1;
                        else if (rs != 0) {
                            rs *= 10;
                            ++prec;
                        } // else digit is a redundant leading zero
                        if (dot)
                            ++scl;
                    } else if ((c >= '1' && c <= '9')) { // have digit
                        int digit = c - '0';
                        if (prec != 1 || rs != 0)
                            ++prec; // prec unchanged if preceded by 0s
                        rs = rs * 10 + digit;
                        if (dot)
                            ++scl;
                    } else if (c == '.') {   // have dot
                        // have dot
                        if (dot) // two dots
                            throw new NumberFormatException("Character array"
                                + " contains more than one decimal point.");
                        dot = true;
                    } else if (Character.isDigit(c)) { // slow path
                        int digit = Character.digit(c, 10);
                        if (digit == 0) {
                            if (prec == 0)
                                prec = 1;
                            else if (rs != 0) {
                                rs *= 10;
                                ++prec;
                            } // else digit is a redundant leading zero
                        } else {
                            if (prec != 1 || rs != 0)
                                ++prec; // prec unchanged if preceded by 0s
                            rs = rs * 10 + digit;
                        }
                        if (dot)
                            ++scl;
                    } else if ((c == 'e') || (c == 'E')) {
                        scl -= parseExp(in, offset, len);
                        break; // [saves a test]
                    } else {
                        throw new NumberFormatException("Character " + c
                            + " is neither a decimal digit number, decimal point, nor"
                            + " \"e\" notation exponential mark.");
                    }
                }
                if (prec == 0) // no digits found
                    throw new NumberFormatException("No digits found.");
                rs = isneg ? -rs : rs;
                int mcp = mc.precision;
                int drop = prec - mcp; // prec has range [1, MAX_INT], mcp has range [0, MAX_INT];
                                       // therefore, this subtraction cannot overflow
                if (mcp > 0 && drop > 0) {  // do rounding
                    while (drop > 0) {
                        scl -= drop;
                        rs = divideAndRound(rs, LONG_TEN_POWERS_TABLE[drop], mc.roundingMode.oldMode);
                        prec = longDigitLength(rs);
                        drop = prec - mcp;
                    }
                }
            } else {
                char[] coeff = new char[len];
                for (; len > 0; offset++, len--) {
                    c = in[offset];
                    // have digit
                    if ((c >= '0' && c <= '9') || Character.isDigit(c)) {
                        // First compact case, we need not to preserve the character
                        // and we can just compute the value in place.
                        if (c == '0' || Character.digit(c, 10) == 0) {
                            if (prec == 0) {
                                coeff[idx] = c;
                                prec = 1;
                            } else if (idx != 0) {
                                coeff[idx++] = c;
                                ++prec;
                            } // else c must be a redundant leading zero
                        } else {
                            if (prec != 1 || idx != 0)
                                ++prec; // prec unchanged if preceded by 0s
                            coeff[idx++] = c;
                        }
                        if (dot)
                            ++scl;
                        continue;
                    }
                    // have dot
                    if (c == '.') {
                        // have dot
                        if (dot) // two dots
                            throw new NumberFormatException("Character array"
                                + " contains more than one decimal point.");
                        dot = true;
                        continue;
                    }
                    // exponent expected
                    if ((c != 'e') && (c != 'E'))
                        throw new NumberFormatException("Character array"
                            + " is missing \"e\" notation exponential mark.");
                    scl -= parseExp(in, offset, len);
                    break; // [saves a test]
                }
                // here when no characters left
                if (prec == 0) // no digits found
                    throw new NumberFormatException("No digits found.");
                // Remove leading zeros from precision (digits count)
                rb = new BigInteger(coeff, isneg ? -1 : 1, prec);
                rs = compactValFor(rb);
                int mcp = mc.precision;
                if (mcp > 0 && (prec > mcp)) {
                    if (rs == INFLATED) {
                        int drop = prec - mcp;
                        while (drop > 0) {
                            scl -= drop;
                            rb = divideAndRoundByTenPow(rb, drop, mc.roundingMode.oldMode);
                            rs = compactValFor(rb);
                            if (rs != INFLATED) {
                                prec = longDigitLength(rs);
                                break;
                            }
                            prec = bigDigitLength(rb);
                            drop = prec - mcp;
                        }
                    }
                    if (rs != INFLATED) {
                        int drop = prec - mcp;
                        while (drop > 0) {
                            scl -= drop;
                            rs = divideAndRound(rs, LONG_TEN_POWERS_TABLE[drop], mc.roundingMode.oldMode);
                            prec = longDigitLength(rs);
                            drop = prec - mcp;
                        }
                        rb = null;
                    }
                }
            }
        } catch (ArrayIndexOutOfBoundsException | NegativeArraySizeException e) {
            NumberFormatException nfe = new NumberFormatException();
            nfe.initCause(e);
            throw nfe;
        }
        if ((int) scl != scl) // overflow
            throw new NumberFormatException("Exponent overflow.");
        this.scale = (int) scl;
        this.precision = prec;
        this.intCompact = rs;
        this.intVal = rb;
    }

    /*
     * parse exponent
     */
    private static long parseExp(char[] in, int offset, int len){
        long exp = 0;
        offset++;
        char c = in[offset];
        len--;
        boolean negexp = (c == '-');
        // optional sign
        if (negexp || c == '+') {
            offset++;
            c = in[offset];
            len--;
        }
        if (len <= 0) // no exponent digits
            throw new NumberFormatException("No exponent digits.");
        // skip leading zeros in the exponent
        while (len > 10 && (c=='0' || (Character.digit(c, 10) == 0))) {
            offset++;
            c = in[offset];
            len--;
        }
        if (len > 10) // too many nonzero exponent digits
            throw new NumberFormatException("Too many nonzero exponent digits.");
        // c now holds first digit of exponent
        for (;; len--) {
            int v;
            if (c >= '0' && c <= '9') {
                v = c - '0';
            } else {
                v = Character.digit(c, 10);
                if (v < 0) // not a digit
                    throw new NumberFormatException("Not a digit.");
            }
            exp = exp * 10 + v;
            if (len == 1)
                break; // that was final character
            offset++;
            c = in[offset];
        }
        if (negexp) // apply sign
            exp = -exp;
        return exp;
    }

    /**
     * Translates a character array representation of a
     * {@code BigDecimal} into a {@code BigDecimal}, accepting the
     * same sequence of characters as the {@link #BigDecimal(String)}
     * constructor.
     *
     * @implNote If the sequence of characters is already available
     * as a character array, using this constructor is faster than
     * converting the {@code char} array to string and using the
     * {@code BigDecimal(String)} constructor.
     *
     * @param in {@code char} array that is the source of characters.
     * @throws NumberFormatException if {@code in} is not a valid
     *         representation of a {@code BigDecimal}.
     * @since  1.5
     */
    public BigDecimal(char[] in) {
        this(in, 0, in.length);
    }

    /**
     * Translates a character array representation of a
     * {@code BigDecimal} into a {@code BigDecimal}, accepting the
     * same sequence of characters as the {@link #BigDecimal(String)}
     * constructor and with rounding according to the context
     * settings.
     *
     * @implNote If the sequence of characters is already available
     * as a character array, using this constructor is faster than
     * converting the {@code char} array to string and using the
     * {@code BigDecimal(String)} constructor.
     *
     * @param  in {@code char} array that is the source of characters.
     * @param  mc the context to use.
     * @throws NumberFormatException if {@code in} is not a valid
     *         representation of a {@code BigDecimal}.
     * @since  1.5
     */
    public BigDecimal(char[] in, MathContext mc) {
        this(in, 0, in.length, mc);
    }

    /**
     * Translates the string representation of a {@code BigDecimal}
     * into a {@code BigDecimal}.  The string representation consists
     * of an optional sign, {@code '+'} (<code> '&#92;u002B'</code>) or
     * {@code '-'} (<code>'&#92;u002D'</code>), followed by a sequence of
     * zero or more decimal digits ("the integer"), optionally
     * followed by a fraction, optionally followed by an exponent.
     *
     * <p>The fraction consists of a decimal point followed by zero
     * or more decimal digits.  The string must contain at least one
     * digit in either the integer or the fraction.  The number formed
     * by the sign, the integer and the fraction is referred to as the
     * <i>significand</i>.
     *
     * <p>The exponent consists of the character {@code 'e'}
     * (<code>'&#92;u0065'</code>) or {@code 'E'} (<code>'&#92;u0045'</code>)
     * followed by one or more decimal digits.
     *
     * <p>More formally, the strings this constructor accepts are
     * described by the following grammar:
     * <blockquote>
     * <dl>
     * <dt><i>BigDecimalString:</i>
     * <dd><i>Sign<sub>opt</sub> Significand Exponent<sub>opt</sub></i>
     * <dt><i>Sign:</i>
     * <dd>{@code +}
     * <dd>{@code -}
     * <dt><i>Significand:</i>
     * <dd><i>IntegerPart</i> {@code .} <i>FractionPart<sub>opt</sub></i>
     * <dd>{@code .} <i>FractionPart</i>
     * <dd><i>IntegerPart</i>
     * <dt><i>IntegerPart:</i>
     * <dd><i>Digits</i>
     * <dt><i>FractionPart:</i>
     * <dd><i>Digits</i>
     * <dt><i>Exponent:</i>
     * <dd><i>ExponentIndicator SignedInteger</i>
     * <dt><i>ExponentIndicator:</i>
     * <dd>{@code e}
     * <dd>{@code E}
     * <dt><i>SignedInteger:</i>
     * <dd><i>Sign<sub>opt</sub> Digits</i>
     * <dt><i>Digits:</i>
     * <dd><i>Digit</i>
     * <dd><i>Digits Digit</i>
     * <dt><i>Digit:</i>
     * <dd>any character for which {@link Character#isDigit}
     * returns {@code true}, including 0, 1, 2 ...
     * </dl>
     * </blockquote>
     *
     * <p>The scale of the returned {@code BigDecimal} will be the
     * number of digits in the fraction, or zero if the string
     * contains no decimal point, subject to adjustment for any
     * exponent; if the string contains an exponent, the exponent is
     * subtracted from the scale.  The value of the resulting scale
     * must lie between {@code Integer.MIN_VALUE} and
     * {@code Integer.MAX_VALUE}, inclusive.
     *
     * <p>The character-to-digit mapping is provided by {@link
     * java.lang.Character#digit} set to convert to radix 10.  The
     * String may not contain any extraneous characters (whitespace,
     * for example).
     *
     * <p><b>Examples:</b><br>
     * The value of the returned {@code BigDecimal} is equal to
     * <i>significand</i> &times; 10<sup>&nbsp;<i>exponent</i></sup>.
     * For each string on the left, the resulting representation
     * [{@code BigInteger}, {@code scale}] is shown on the right.
     * <pre>
     * "0"            [0,0]
     * "0.00"         [0,2]
     * "123"          [123,0]
     * "-123"         [-123,0]
     * "1.23E3"       [123,-1]
     * "1.23E+3"      [123,-1]
     * "12.3E+7"      [123,-6]
     * "12.0"         [120,1]
     * "12.3"         [123,1]
     * "0.00123"      [123,5]
     * "-1.23E-12"    [-123,14]
     * "1234.5E-4"    [12345,5]
     * "0E+7"         [0,-7]
     * "-0"           [0,0]
     * </pre>
     *
     * @apiNote For values other than {@code float} and
     * {@code double} NaN and &plusmn;Infinity, this constructor is
     * compatible with the values returned by {@link Float#toString}
     * and {@link Double#toString}.  This is generally the preferred
     * way to convert a {@code float} or {@code double} into a
     * BigDecimal, as it doesn't suffer from the unpredictability of
     * the {@link #BigDecimal(double)} constructor.
     *
     * @param val String representation of {@code BigDecimal}.
     *
     * @throws NumberFormatException if {@code val} is not a valid
     *         representation of a {@code BigDecimal}.
     */
    public BigDecimal(String val) {
        this(val.toCharArray(), 0, val.length());
    }

    /**
     * Translates the string representation of a {@code BigDecimal}
     * into a {@code BigDecimal}, accepting the same strings as the
     * {@link #BigDecimal(String)} constructor, with rounding
     * according to the context settings.
     *
     * @param  val string representation of a {@code BigDecimal}.
     * @param  mc the context to use.
     * @throws NumberFormatException if {@code val} is not a valid
     *         representation of a BigDecimal.
     * @since  1.5
     */
    public BigDecimal(String val, MathContext mc) {
        this(val.toCharArray(), 0, val.length(), mc);
    }

    /**
     * Translates a {@code double} into a {@code BigDecimal} which
     * is the exact decimal representation of the {@code double}'s
     * binary floating-point value.  The scale of the returned
     * {@code BigDecimal} is the smallest value such that
     * <code>(10<sup>scale</sup> &times; val)</code> is an integer.
     * <p>
     * <b>Notes:</b>
     * <ol>
     * <li>
     * The results of this constructor can be somewhat unpredictable.
     * One might assume that writing {@code new BigDecimal(0.1)} in
     * Java creates a {@code BigDecimal} which is exactly equal to
     * 0.1 (an unscaled value of 1, with a scale of 1), but it is
     * actually equal to
     * 0.1000000000000000055511151231257827021181583404541015625.
     * This is because 0.1 cannot be represented exactly as a
     * {@code double} (or, for that matter, as a binary fraction of
     * any finite length).  Thus, the value that is being passed
     * <em>in</em> to the constructor is not exactly equal to 0.1,
     * appearances notwithstanding.
     *
     * <li>
     * The {@code String} constructor, on the other hand, is
     * perfectly predictable: writing {@code new BigDecimal("0.1")}
     * creates a {@code BigDecimal} which is <em>exactly</em> equal to
     * 0.1, as one would expect.  Therefore, it is generally
     * recommended that the {@linkplain #BigDecimal(String)
     * String constructor} be used in preference to this one.
     *
     * <li>
     * When a {@code double} must be used as a source for a
     * {@code BigDecimal}, note that this constructor provides an
     * exact conversion; it does not give the same result as
     * converting the {@code double} to a {@code String} using the
     * {@link Double#toString(double)} method and then using the
     * {@link #BigDecimal(String)} constructor.  To get that result,
     * use the {@code static} {@link #valueOf(double)} method.
     * </ol>
     *
     * @param val {@code double} value to be converted to
     *        {@code BigDecimal}.
     * @throws NumberFormatException if {@code val} is infinite or NaN.
     */
    public BigDecimal(double val) {
        this(val,MathContext.UNLIMITED);
    }

    /**
     * Translates a {@code double} into a {@code BigDecimal}, with
     * rounding according to the context settings.  The scale of the
     * {@code BigDecimal} is the smallest value such that
     * <code>(10<sup>scale</sup> &times; val)</code> is an integer.
     *
     * <p>The results of this constructor can be somewhat unpredictable
     * and its use is generally not recommended; see the notes under
     * the {@link #BigDecimal(double)} constructor.
     *
     * @param  val {@code double} value to be converted to
     *         {@code BigDecimal}.
     * @param  mc the context to use.
     * @throws NumberFormatException if {@code val} is infinite or NaN.
     * @since  1.5
     */
    public BigDecimal(double val, MathContext mc) {
        if (Double.isInfinite(val) || Double.isNaN(val))
            throw new NumberFormatException("Infinite or NaN");
        // Translate the double into sign, exponent and significand, according
        // to the formulae in JLS, Section 20.10.22.
        long valBits = Double.doubleToLongBits(val);
        int sign = ((valBits >> 63) == 0 ? 1 : -1);
        int exponent = (int) ((valBits >> 52) & 0x7ffL);
        long significand = (exponent == 0
                ? (valBits & ((1L << 52) - 1)) << 1
                : (valBits & ((1L << 52) - 1)) | (1L << 52));
        exponent -= 1075;
        // At this point, val == sign * significand * 2**exponent.

        /*
         * Special case zero to suppress nonterminating normalization and bogus
         * scale calculation.
         */
        if (significand == 0) {
            this.intVal = BigInteger.ZERO;
            this.scale = 0;
            this.intCompact = 0;
            this.precision = 1;
            return;
        }
        // Normalize
        while ((significand & 1) == 0) { // i.e., significand is even
            significand >>= 1;
            exponent++;
        }
        int scl = 0;
        // Calculate intVal and scale
        BigInteger rb;
        long compactVal = sign * significand;
        if (exponent == 0) {
            rb = (compactVal == INFLATED) ? INFLATED_BIGINT : null;
        } else {
            if (exponent < 0) {
                rb = BigInteger.valueOf(5).pow(-exponent).multiply(compactVal);
                scl = -exponent;
            } else { //  (exponent > 0)
                rb = BigInteger.TWO.pow(exponent).multiply(compactVal);
            }
            compactVal = compactValFor(rb);
        }
        int prec = 0;
        int mcp = mc.precision;
        if (mcp > 0) { // do rounding
            int mode = mc.roundingMode.oldMode;
            int drop;
            if (compactVal == INFLATED) {
                prec = bigDigitLength(rb);
                drop = prec - mcp;
                while (drop > 0) {
                    scl = checkScaleNonZero((long) scl - drop);
                    rb = divideAndRoundByTenPow(rb, drop, mode);
                    compactVal = compactValFor(rb);
                    if (compactVal != INFLATED) {
                        break;
                    }
                    prec = bigDigitLength(rb);
                    drop = prec - mcp;
                }
            }
            if (compactVal != INFLATED) {
                prec = longDigitLength(compactVal);
                drop = prec - mcp;
                while (drop > 0) {
                    scl = checkScaleNonZero((long) scl - drop);
                    compactVal = divideAndRound(compactVal, LONG_TEN_POWERS_TABLE[drop], mc.roundingMode.oldMode);
                    prec = longDigitLength(compactVal);
                    drop = prec - mcp;
                }
                rb = null;
            }
        }
        this.intVal = rb;
        this.intCompact = compactVal;
        this.scale = scl;
        this.precision = prec;
    }

    /**
     * Accept no subclasses.
     */
    private static BigInteger toStrictBigInteger(BigInteger val) {
        return (val.getClass() == BigInteger.class) ?
            val :
            new BigInteger(val.toByteArray().clone());
    }

    /**
     * Translates a {@code BigInteger} into a {@code BigDecimal}.
     * The scale of the {@code BigDecimal} is zero.
     *
     * @param val {@code BigInteger} value to be converted to
     *            {@code BigDecimal}.
     */
    public BigDecimal(BigInteger val) {
        scale = 0;
        intVal = toStrictBigInteger(val);
        intCompact = compactValFor(intVal);
    }

    /**
     * Translates a {@code BigInteger} into a {@code BigDecimal}
     * rounding according to the context settings.  The scale of the
     * {@code BigDecimal} is zero.
     *
     * @param val {@code BigInteger} value to be converted to
     *            {@code BigDecimal}.
     * @param  mc the context to use.
     * @since  1.5
     */
    public BigDecimal(BigInteger val, MathContext mc) {
        this(toStrictBigInteger(val), 0, mc);
    }

    /**
     * Translates a {@code BigInteger} unscaled value and an
     * {@code int} scale into a {@code BigDecimal}.  The value of
     * the {@code BigDecimal} is
     * <code>(unscaledVal &times; 10<sup>-scale</sup>)</code>.
     *
     * @param unscaledVal unscaled value of the {@code BigDecimal}.
     * @param scale scale of the {@code BigDecimal}.
     */
    public BigDecimal(BigInteger unscaledVal, int scale) {
        // Negative scales are now allowed
        this.intVal = toStrictBigInteger(unscaledVal);
        this.intCompact = compactValFor(this.intVal);
        this.scale = scale;
    }

    /**
     * Translates a {@code BigInteger} unscaled value and an
     * {@code int} scale into a {@code BigDecimal}, with rounding
     * according to the context settings.  The value of the
     * {@code BigDecimal} is <code>(unscaledVal &times;
     * 10<sup>-scale</sup>)</code>, rounded according to the
     * {@code precision} and rounding mode settings.
     *
     * @param  unscaledVal unscaled value of the {@code BigDecimal}.
     * @param  scale scale of the {@code BigDecimal}.
     * @param  mc the context to use.
     * @since  1.5
     */
    public BigDecimal(BigInteger unscaledVal, int scale, MathContext mc) {
        unscaledVal = toStrictBigInteger(unscaledVal);
        long compactVal = compactValFor(unscaledVal);
        int mcp = mc.precision;
        int prec = 0;
        if (mcp > 0) { // do rounding
            int mode = mc.roundingMode.oldMode;
            if (compactVal == INFLATED) {
                prec = bigDigitLength(unscaledVal);
                int drop = prec - mcp;
                while (drop > 0) {
                    scale = checkScaleNonZero((long) scale - drop);
                    unscaledVal = divideAndRoundByTenPow(unscaledVal, drop, mode);
                    compactVal = compactValFor(unscaledVal);
                    if (compactVal != INFLATED) {
                        break;
                    }
                    prec = bigDigitLength(unscaledVal);
                    drop = prec - mcp;
                }
            }
            if (compactVal != INFLATED) {
                prec = longDigitLength(compactVal);
                int drop = prec - mcp;     // drop can't be more than 18
                while (drop > 0) {
                    scale = checkScaleNonZero((long) scale - drop);
                    compactVal = divideAndRound(compactVal, LONG_TEN_POWERS_TABLE[drop], mode);
                    prec = longDigitLength(compactVal);
                    drop = prec - mcp;
                }
                unscaledVal = null;
            }
        }
        this.intVal = unscaledVal;
        this.intCompact = compactVal;
        this.scale = scale;
        this.precision = prec;
    }

    /**
     * Translates an {@code int} into a {@code BigDecimal}.  The
     * scale of the {@code BigDecimal} is zero.
     *
     * @param val {@code int} value to be converted to
     *            {@code BigDecimal}.
     * @since  1.5
     */
    public BigDecimal(int val) {
        this.intCompact = val;
        this.scale = 0;
        this.intVal = null;
    }

    /**
     * Translates an {@code int} into a {@code BigDecimal}, with
     * rounding according to the context settings.  The scale of the
     * {@code BigDecimal}, before any rounding, is zero.
     *
     * @param  val {@code int} value to be converted to {@code BigDecimal}.
     * @param  mc the context to use.
     * @since  1.5
     */
    public BigDecimal(int val, MathContext mc) {
        int mcp = mc.precision;
        long compactVal = val;
        int scl = 0;
        int prec = 0;
        if (mcp > 0) { // do rounding
            prec = longDigitLength(compactVal);
            int drop = prec - mcp; // drop can't be more than 18
            while (drop > 0) {
                scl = checkScaleNonZero((long) scl - drop);
                compactVal = divideAndRound(compactVal, LONG_TEN_POWERS_TABLE[drop], mc.roundingMode.oldMode);
                prec = longDigitLength(compactVal);
                drop = prec - mcp;
            }
        }
        this.intVal = null;
        this.intCompact = compactVal;
        this.scale = scl;
        this.precision = prec;
    }

    /**
     * Translates a {@code long} into a {@code BigDecimal}.  The
     * scale of the {@code BigDecimal} is zero.
     *
     * @param val {@code long} value to be converted to {@code BigDecimal}.
     * @since  1.5
     */
    public BigDecimal(long val) {
        this.intCompact = val;
        this.intVal = (val == INFLATED) ? INFLATED_BIGINT : null;
        this.scale = 0;
    }

    /**
     * Translates a {@code long} into a {@code BigDecimal}, with
     * rounding according to the context settings.  The scale of the
     * {@code BigDecimal}, before any rounding, is zero.
     *
     * @param  val {@code long} value to be converted to {@code BigDecimal}.
     * @param  mc the context to use.
     * @since  1.5
     */
    public BigDecimal(long val, MathContext mc) {
        int mcp = mc.precision;
        int mode = mc.roundingMode.oldMode;
        int prec = 0;
        int scl = 0;
        BigInteger rb = (val == INFLATED) ? INFLATED_BIGINT : null;
        if (mcp > 0) { // do rounding
            if (val == INFLATED) {
                prec = 19;
                int drop = prec - mcp;
                while (drop > 0) {
                    scl = checkScaleNonZero((long) scl - drop);
                    rb = divideAndRoundByTenPow(rb, drop, mode);
                    val = compactValFor(rb);
                    if (val != INFLATED) {
                        break;
                    }
                    prec = bigDigitLength(rb);
                    drop = prec - mcp;
                }
            }
            if (val != INFLATED) {
                prec = longDigitLength(val);
                int drop = prec - mcp;
                while (drop > 0) {
                    scl = checkScaleNonZero((long) scl - drop);
                    val = divideAndRound(val, LONG_TEN_POWERS_TABLE[drop], mc.roundingMode.oldMode);
                    prec = longDigitLength(val);
                    drop = prec - mcp;
                }
                rb = null;
            }
        }
        this.intVal = rb;
        this.intCompact = val;
        this.scale = scl;
        this.precision = prec;
    }

    // Static Factory Methods

    /**
     * Translates a {@code long} unscaled value and an
     * {@code int} scale into a {@code BigDecimal}.
     *
     * @apiNote This static factory method is provided in preference
     * to a ({@code long}, {@code int}) constructor because it allows
     * for reuse of frequently used {@code BigDecimal} values.
     *
     * @param unscaledVal unscaled value of the {@code BigDecimal}.
     * @param scale scale of the {@code BigDecimal}.
     * @return a {@code BigDecimal} whose value is
     *         <code>(unscaledVal &times; 10<sup>-scale</sup>)</code>.
     */
    public static BigDecimal valueOf(long unscaledVal, int scale) {
        if (scale == 0)
            return valueOf(unscaledVal);
        else if (unscaledVal == 0) {
            return zeroValueOf(scale);
        }
        return new BigDecimal(unscaledVal == INFLATED ?
                              INFLATED_BIGINT : null,
                              unscaledVal, scale, 0);
    }

    /**
     * Translates a {@code long} value into a {@code BigDecimal}
     * with a scale of zero.
     *
     * @apiNote This static factory method is provided in preference
     * to a ({@code long}) constructor because it allows for reuse of
     * frequently used {@code BigDecimal} values.
     *
     * @param val value of the {@code BigDecimal}.
     * @return a {@code BigDecimal} whose value is {@code val}.
     */
    public static BigDecimal valueOf(long val) {
        if (val >= 0 && val < ZERO_THROUGH_TEN.length)
            return ZERO_THROUGH_TEN[(int)val];
        else if (val != INFLATED)
            return new BigDecimal(null, val, 0, 0);
        return new BigDecimal(INFLATED_BIGINT, val, 0, 0);
    }

    static BigDecimal valueOf(long unscaledVal, int scale, int prec) {
        if (scale == 0 && unscaledVal >= 0 && unscaledVal < ZERO_THROUGH_TEN.length) {
            return ZERO_THROUGH_TEN[(int) unscaledVal];
        } else if (unscaledVal == 0) {
            return zeroValueOf(scale);
        }
        return new BigDecimal(unscaledVal == INFLATED ? INFLATED_BIGINT : null,
                unscaledVal, scale, prec);
    }

    static BigDecimal valueOf(BigInteger intVal, int scale, int prec) {
        long val = compactValFor(intVal);
        if (val == 0) {
            return zeroValueOf(scale);
        } else if (scale == 0 && val >= 0 && val < ZERO_THROUGH_TEN.length) {
            return ZERO_THROUGH_TEN[(int) val];
        }
        return new BigDecimal(intVal, val, scale, prec);
    }

    static BigDecimal zeroValueOf(int scale) {
        if (scale >= 0 && scale < ZERO_SCALED_BY.length)
            return ZERO_SCALED_BY[scale];
        else
            return new BigDecimal(BigInteger.ZERO, 0, scale, 1);
    }

    /**
     * Translates a {@code double} into a {@code BigDecimal}, using
     * the {@code double}'s canonical string representation provided
     * by the {@link Double#toString(double)} method.
     *
     * @apiNote This is generally the preferred way to convert a
     * {@code double} into a {@code BigDecimal}, as
     * the value returned is equal to that resulting from constructing
     * a {@code BigDecimal} from the result of using {@link
     * Double#toString(double)}.
     * <p>
     * While a {@code float} argument {@code v} can be passed to this method,
     * the result often contains many more trailing digits than the precision
     * of a {@code float}.
     * Consider using {@code new BigDecimal(Float.toString(v))} instead.
     *
     * @param  val {@code double} to convert to a {@code BigDecimal}.
     * @return a {@code BigDecimal} whose value is equal to or approximately
     *         equal to the value of {@code val}.
     * @throws NumberFormatException if {@code val} is infinite or NaN.
     * @since  1.5
     */
    public static BigDecimal valueOf(double val) {
        if (!Double.isFinite(val)) {
            throw new NumberFormatException("Infinite or NaN");
        }

        var fmt = FormattedFPDecimal.valueForDoubleToString(Math.abs(val));
        long s = fmt.getSignificand();
        if (val < 0) {
            // Original s is never negative, so no overflow
            s = -s;
        }

        return valueOf(s, -fmt.getExp(), fmt.getPrecision());
    }

    // Arithmetic Operations
    /**
     * Returns a {@code BigDecimal} whose value is {@code (this +
     * augend)}, and whose scale is {@code max(this.scale(),
     * augend.scale())}.
     *
     * @param  augend value to be added to this {@code BigDecimal}.
     * @return {@code this + augend}
     */
    public BigDecimal add(BigDecimal augend) {
        if (this.intCompact != INFLATED) {
            if ((augend.intCompact != INFLATED)) {
                return add(this.intCompact, this.scale, augend.intCompact, augend.scale);
            } else {
                return add(this.intCompact, this.scale, augend.intVal, augend.scale);
            }
        } else {
            if ((augend.intCompact != INFLATED)) {
                return add(augend.intCompact, augend.scale, this.intVal, this.scale);
            } else {
                return add(this.intVal, this.scale, augend.intVal, augend.scale);
            }
        }
    }

    /**
     * Returns a {@code BigDecimal} whose value is {@code (this + augend)},
     * with rounding according to the context settings.
     *
     * If either number is zero and the precision setting is nonzero then
     * the other number, rounded if necessary, is used as the result.
     *
     * @param  augend value to be added to this {@code BigDecimal}.
     * @param  mc the context to use.
     * @return {@code this + augend}, rounded as necessary.
     * @since  1.5
     */
    public BigDecimal add(BigDecimal augend, MathContext mc) {
        if (mc.precision == 0)
            return add(augend);
        BigDecimal lhs = this;

        // If either number is zero then the other number, rounded and
        // scaled if necessary, is used as the result.
        {
            boolean lhsIsZero = lhs.signum() == 0;
            boolean augendIsZero = augend.signum() == 0;

            if (lhsIsZero || augendIsZero) {
                int preferredScale = Math.max(lhs.scale(), augend.scale());
                BigDecimal result;

                if (lhsIsZero && augendIsZero)
                    return zeroValueOf(preferredScale);
                result = lhsIsZero ? doRound(augend, mc) : doRound(lhs, mc);

                if (result.scale() == preferredScale)
                    return result;
                else if (result.scale() > preferredScale) {
                    return stripZerosToMatchScale(result.intVal, result.intCompact, result.scale, preferredScale);
                } else { // result.scale < preferredScale
                    int precisionDiff = mc.precision - result.precision();
                    int scaleDiff     = preferredScale - result.scale();

                    if (precisionDiff >= scaleDiff)
                        return result.setScale(preferredScale); // can achieve target scale
                    else
                        return result.setScale(result.scale() + precisionDiff);
                }
            }
        }

        long padding = (long) lhs.scale - augend.scale;
        if (padding != 0) { // scales differ; alignment needed
            BigDecimal[] arg = preAlign(lhs, augend, padding, mc);
            matchScale(arg);
            lhs = arg[0];
            augend = arg[1];
        }
        return doRound(lhs.inflated().add(augend.inflated()), lhs.scale, mc);
    }

    /**
     * Returns an array of length two, the sum of whose entries is
     * equal to the rounded sum of the {@code BigDecimal} arguments.
     *
     * <p>If the digit positions of the arguments have a sufficient
     * gap between them, the value smaller in magnitude can be
     * condensed into a {@literal "sticky bit"} and the end result will
     * round the same way <em>if</em> the precision of the final
     * result does not include the high order digit of the small
     * magnitude operand.
     *
     * <p>Note that while strictly speaking this is an optimization,
     * it makes a much wider range of additions practical.
     *
     * <p>This corresponds to a pre-shift operation in a fixed
     * precision floating-point adder; this method is complicated by
     * variable precision of the result as determined by the
     * MathContext.  A more nuanced operation could implement a
     * {@literal "right shift"} on the smaller magnitude operand so
     * that the number of digits of the smaller operand could be
     * reduced even though the significands partially overlapped.
     */
    private BigDecimal[] preAlign(BigDecimal lhs, BigDecimal augend, long padding, MathContext mc) {
        assert padding != 0;
        BigDecimal big;
        BigDecimal small;

        if (padding < 0) { // lhs is big; augend is small
            big = lhs;
            small = augend;
        } else { // lhs is small; augend is big
            big = augend;
            small = lhs;
        }

        /*
         * This is the estimated scale of an ulp of the result; it assumes that
         * the result doesn't have a carry-out on a true add (e.g. 999 + 1 =>
         * 1000) or any subtractive cancellation on borrowing (e.g. 100 - 1.2 =>
         * 98.8)
         */
        long estResultUlpScale = (long) big.scale - big.precision() + mc.precision;

        /*
         * The low-order digit position of big is big.scale().  This
         * is true regardless of whether big has a positive or
         * negative scale.  The high-order digit position of small is
         * small.scale - (small.precision() - 1).  To do the full
         * condensation, the digit positions of big and small must be
         * disjoint *and* the digit positions of small should not be
         * directly visible in the result.
         */
        long smallHighDigitPos = (long) small.scale - small.precision() + 1;
        if (smallHighDigitPos > big.scale + 2 && // big and small disjoint
            smallHighDigitPos > estResultUlpScale + 2) { // small digits not visible
            small = BigDecimal.valueOf(small.signum(), this.checkScale(Math.max(big.scale, estResultUlpScale) + 3));
        }

        // Since addition is symmetric, preserving input order in
        // returned operands doesn't matter
        BigDecimal[] result = {big, small};
        return result;
    }

    /**
     * Returns a {@code BigDecimal} whose value is {@code (this -
     * subtrahend)}, and whose scale is {@code max(this.scale(),
     * subtrahend.scale())}.
     *
     * @param  subtrahend value to be subtracted from this {@code BigDecimal}.
     * @return {@code this - subtrahend}
     */
    public BigDecimal subtract(BigDecimal subtrahend) {
        if (this.intCompact != INFLATED) {
            if ((subtrahend.intCompact != INFLATED)) {
                return add(this.intCompact, this.scale, -subtrahend.intCompact, subtrahend.scale);
            } else {
                return add(this.intCompact, this.scale, subtrahend.intVal.negate(), subtrahend.scale);
            }
        } else {
            if ((subtrahend.intCompact != INFLATED)) {
                // Pair of subtrahend values given before pair of
                // values from this BigDecimal to avoid need for
                // method overloading on the specialized add method
                return add(-subtrahend.intCompact, subtrahend.scale, this.intVal, this.scale);
            } else {
                return add(this.intVal, this.scale, subtrahend.intVal.negate(), subtrahend.scale);
            }
        }
    }

    /**
     * Returns a {@code BigDecimal} whose value is {@code (this - subtrahend)},
     * with rounding according to the context settings.
     *
     * If {@code subtrahend} is zero then this, rounded if necessary, is used as the
     * result.  If this is zero then the result is {@code subtrahend.negate(mc)}.
     *
     * @param  subtrahend value to be subtracted from this {@code BigDecimal}.
     * @param  mc the context to use.
     * @return {@code this - subtrahend}, rounded as necessary.
     * @since  1.5
     */
    public BigDecimal subtract(BigDecimal subtrahend, MathContext mc) {
        if (mc.precision == 0)
            return subtract(subtrahend);
        // share the special rounding code in add()
        return add(subtrahend.negate(), mc);
    }

    /**
     * Returns a {@code BigDecimal} whose value is <code>(this &times;
     * multiplicand)</code>, and whose scale is {@code (this.scale() +
     * multiplicand.scale())}.
     *
     * @param  multiplicand value to be multiplied by this {@code BigDecimal}.
     * @return {@code this * multiplicand}
     */
    public BigDecimal multiply(BigDecimal multiplicand) {
        int productScale = checkScale((long) scale + multiplicand.scale);
        if (this.intCompact != INFLATED) {
            if ((multiplicand.intCompact != INFLATED)) {
                return multiply(this.intCompact, multiplicand.intCompact, productScale);
            } else {
                return multiply(this.intCompact, multiplicand.intVal, productScale);
            }
        } else {
            if ((multiplicand.intCompact != INFLATED)) {
                return multiply(multiplicand.intCompact, this.intVal, productScale);
            } else {
                return multiply(this.intVal, multiplicand.intVal, productScale);
            }
        }
    }

    /**
     * Returns a {@code BigDecimal} whose value is <code>(this &times;
     * multiplicand)</code>, with rounding according to the context settings.
     *
     * @param  multiplicand value to be multiplied by this {@code BigDecimal}.
     * @param  mc the context to use.
     * @return {@code this * multiplicand}, rounded as necessary.
     * @since  1.5
     */
    public BigDecimal multiply(BigDecimal multiplicand, MathContext mc) {
        if (mc.precision == 0)
            return multiply(multiplicand);
        int productScale = checkScale((long) scale + multiplicand.scale);
        if (this.intCompact != INFLATED) {
            if ((multiplicand.intCompact != INFLATED)) {
                return multiplyAndRound(this.intCompact, multiplicand.intCompact, productScale, mc);
            } else {
                return multiplyAndRound(this.intCompact, multiplicand.intVal, productScale, mc);
            }
        } else {
            if ((multiplicand.intCompact != INFLATED)) {
                return multiplyAndRound(multiplicand.intCompact, this.intVal, productScale, mc);
            } else {
                return multiplyAndRound(this.intVal, multiplicand.intVal, productScale, mc);
            }
        }
    }

    /**
     * Returns a {@code BigDecimal} whose value is {@code (this /
     * divisor)}, and whose scale is as specified.  If rounding must
     * be performed to generate a result with the specified scale, the
     * specified rounding mode is applied.
     *
     * @deprecated The method {@link #divide(BigDecimal, int, RoundingMode)}
     * should be used in preference to this legacy method.
     *
     * @param  divisor value by which this {@code BigDecimal} is to be divided.
     * @param  scale scale of the {@code BigDecimal} quotient to be returned.
     * @param  roundingMode rounding mode to apply.
     * @return {@code this / divisor}
     * @throws ArithmeticException if {@code divisor} is zero,
     *         {@code roundingMode==ROUND_UNNECESSARY} and
     *         the specified scale is insufficient to represent the result
     *         of the division exactly.
     * @throws IllegalArgumentException if {@code roundingMode} does not
     *         represent a valid rounding mode.
     * @see    #ROUND_UP
     * @see    #ROUND_DOWN
     * @see    #ROUND_CEILING
     * @see    #ROUND_FLOOR
     * @see    #ROUND_HALF_UP
     * @see    #ROUND_HALF_DOWN
     * @see    #ROUND_HALF_EVEN
     * @see    #ROUND_UNNECESSARY
     */
    @Deprecated(since="9")
    public BigDecimal divide(BigDecimal divisor, int scale, int roundingMode) {
        if (roundingMode < ROUND_UP || roundingMode > ROUND_UNNECESSARY)
            throw new IllegalArgumentException("Invalid rounding mode");
        if (this.intCompact != INFLATED) {
            if ((divisor.intCompact != INFLATED)) {
                return divide(this.intCompact, this.scale, divisor.intCompact, divisor.scale, scale, roundingMode);
            } else {
                return divide(this.intCompact, this.scale, divisor.intVal, divisor.scale, scale, roundingMode);
            }
        } else {
            if ((divisor.intCompact != INFLATED)) {
                return divide(this.intVal, this.scale, divisor.intCompact, divisor.scale, scale, roundingMode);
            } else {
                return divide(this.intVal, this.scale, divisor.intVal, divisor.scale, scale, roundingMode);
            }
        }
    }

    /**
     * Returns a {@code BigDecimal} whose value is {@code (this /
     * divisor)}, and whose scale is as specified.  If rounding must
     * be performed to generate a result with the specified scale, the
     * specified rounding mode is applied.
     *
     * @param  divisor value by which this {@code BigDecimal} is to be divided.
     * @param  scale scale of the {@code BigDecimal} quotient to be returned.
     * @param  roundingMode rounding mode to apply.
     * @return {@code this / divisor}
     * @throws ArithmeticException if {@code divisor} is zero,
     *         {@code roundingMode==RoundingMode.UNNECESSARY} and
     *         the specified scale is insufficient to represent the result
     *         of the division exactly.
     * @since 1.5
     */
    public BigDecimal divide(BigDecimal divisor, int scale, RoundingMode roundingMode) {
        return divide(divisor, scale, roundingMode.oldMode);
    }

    /**
     * Returns a {@code BigDecimal} whose value is {@code (this /
     * divisor)}, and whose scale is {@code this.scale()}.  If
     * rounding must be performed to generate a result with the given
     * scale, the specified rounding mode is applied.
     *
     * @deprecated The method {@link #divide(BigDecimal, RoundingMode)}
     * should be used in preference to this legacy method.
     *
     * @param  divisor value by which this {@code BigDecimal} is to be divided.
     * @param  roundingMode rounding mode to apply.
     * @return {@code this / divisor}
     * @throws ArithmeticException if {@code divisor==0}, or
     *         {@code roundingMode==ROUND_UNNECESSARY} and
     *         {@code this.scale()} is insufficient to represent the result
     *         of the division exactly.
     * @throws IllegalArgumentException if {@code roundingMode} does not
     *         represent a valid rounding mode.
     * @see    #ROUND_UP
     * @see    #ROUND_DOWN
     * @see    #ROUND_CEILING
     * @see    #ROUND_FLOOR
     * @see    #ROUND_HALF_UP
     * @see    #ROUND_HALF_DOWN
     * @see    #ROUND_HALF_EVEN
     * @see    #ROUND_UNNECESSARY
     */
    @Deprecated(since="9")
    public BigDecimal divide(BigDecimal divisor, int roundingMode) {
        return this.divide(divisor, scale, roundingMode);
    }

    /**
     * Returns a {@code BigDecimal} whose value is {@code (this /
     * divisor)}, and whose scale is {@code this.scale()}.  If
     * rounding must be performed to generate a result with the given
     * scale, the specified rounding mode is applied.
     *
     * @param  divisor value by which this {@code BigDecimal} is to be divided.
     * @param  roundingMode rounding mode to apply.
     * @return {@code this / divisor}
     * @throws ArithmeticException if {@code divisor==0}, or
     *         {@code roundingMode==RoundingMode.UNNECESSARY} and
     *         {@code this.scale()} is insufficient to represent the result
     *         of the division exactly.
     * @since 1.5
     */
    public BigDecimal divide(BigDecimal divisor, RoundingMode roundingMode) {
        return this.divide(divisor, scale, roundingMode.oldMode);
    }

    /**
     * Returns a {@code BigDecimal} whose value is {@code (this /
     * divisor)}, and whose preferred scale is {@code (this.scale() -
     * divisor.scale())}; if the exact quotient cannot be
     * represented (because it has a non-terminating decimal
     * expansion) an {@code ArithmeticException} is thrown.
     *
     * @param  divisor value by which this {@code BigDecimal} is to be divided.
     * @throws ArithmeticException if the exact quotient does not have a
     *         terminating decimal expansion, including dividing by zero
     * @return {@code this / divisor}
     * @since 1.5
     * @author Joseph D. Darcy
     */
    public BigDecimal divide(BigDecimal divisor) {
        /*
         * Handle zero cases first.
         */
        if (divisor.signum() == 0) {   // x/0
            if (this.signum() == 0)    // 0/0
                throw new ArithmeticException("Division undefined");  // NaN
            throw new ArithmeticException("Division by zero");
        }

        // Calculate preferred scale
        int preferredScale = saturateLong((long) this.scale - divisor.scale);

        if (this.signum() == 0) // 0/y
            return zeroValueOf(preferredScale);
        else {
            /*
             * If the quotient this/divisor has a terminating decimal
             * expansion, the expansion can have no more than
             * (a.precision() + ceil(10*b.precision)/3) digits.
             * Therefore, create a MathContext object with this
             * precision and do a divide with the UNNECESSARY rounding
             * mode.
             */
            MathContext mc = new MathContext( (int)Math.min(this.precision() +
                                                            (long)Math.ceil(10.0*divisor.precision()/3.0),
                                                            Integer.MAX_VALUE),
                                              RoundingMode.UNNECESSARY);
            BigDecimal quotient;
            try {
                quotient = this.divide(divisor, mc);
            } catch (ArithmeticException e) {
                throw new ArithmeticException("Non-terminating decimal expansion; " +
                                              "no exact representable decimal result.");
            }

            int quotientScale = quotient.scale();

            // divide(BigDecimal, mc) tries to adjust the quotient to
            // the desired one by removing trailing zeros; since the
            // exact divide method does not have an explicit digit
            // limit, we can add zeros too.
            if (preferredScale > quotientScale)
                return quotient.setScale(preferredScale, ROUND_UNNECESSARY);

            return quotient;
        }
    }

    /**
     * Returns a {@code BigDecimal} whose value is {@code (this /
     * divisor)}, with rounding according to the context settings.
     *
     * @param  divisor value by which this {@code BigDecimal} is to be divided.
     * @param  mc the context to use.
     * @return {@code this / divisor}, rounded as necessary.
     * @throws ArithmeticException if the result is inexact but the
     *         rounding mode is {@code UNNECESSARY} or
     *         {@code mc.precision == 0} and the quotient has a
     *         non-terminating decimal expansion, including dividing by zero
     * @since  1.5
     */
    public BigDecimal divide(BigDecimal divisor, MathContext mc) {
        int mcp = mc.precision;
        if (mcp == 0)
            return divide(divisor);

        BigDecimal dividend = this;
        long preferredScale = (long)dividend.scale - divisor.scale;
        // Now calculate the answer.  We use the existing
        // divide-and-round method, but as this rounds to scale we have
        // to normalize the values here to achieve the desired result.
        // For x/y we first handle y=0 and x=0, and then normalize x and
        // y to give x' and y' with the following constraints:
        //   (a) 0.1 <= x' < 1
        //   (b)  x' <= y' < 10*x'
        // Dividing x'/y' with the required scale set to mc.precision then
        // will give a result in the range 0.1 to 1 rounded to exactly
        // the right number of digits (except in the case of a result of
        // 1.000... which can arise when x=y, or when rounding overflows
        // The 1.000... case will reduce properly to 1.
        if (divisor.signum() == 0) {      // x/0
            if (dividend.signum() == 0)    // 0/0
                throw new ArithmeticException("Division undefined");  // NaN
            throw new ArithmeticException("Division by zero");
        }
        if (dividend.signum() == 0) // 0/y
            return zeroValueOf(saturateLong(preferredScale));
        int xscale = dividend.precision();
        int yscale = divisor.precision();
        if(dividend.intCompact!=INFLATED) {
            if(divisor.intCompact!=INFLATED) {
                return divide(dividend.intCompact, xscale, divisor.intCompact, yscale, preferredScale, mc);
            } else {
                return divide(dividend.intCompact, xscale, divisor.intVal, yscale, preferredScale, mc);
            }
        } else {
            if(divisor.intCompact!=INFLATED) {
                return divide(dividend.intVal, xscale, divisor.intCompact, yscale, preferredScale, mc);
            } else {
                return divide(dividend.intVal, xscale, divisor.intVal, yscale, preferredScale, mc);
            }
        }
    }

    /**
     * Returns a {@code BigDecimal} whose value is the integer part
     * of the quotient {@code (this / divisor)} rounded down.  The
     * preferred scale of the result is {@code (this.scale() -
     * divisor.scale())}.
     *
     * @param  divisor value by which this {@code BigDecimal} is to be divided.
     * @return The integer part of {@code this / divisor}.
     * @throws ArithmeticException if {@code divisor==0}
     * @since  1.5
     */
    public BigDecimal divideToIntegralValue(BigDecimal divisor) {
        // Calculate preferred scale
        int preferredScale = saturateLong((long) this.scale - divisor.scale);
        if (this.compareMagnitude(divisor) < 0) {
            // much faster when this << divisor
            return zeroValueOf(preferredScale);
        }

        if (this.signum() == 0 && divisor.signum() != 0)
            return this.setScale(preferredScale, ROUND_UNNECESSARY);

        // Perform a divide with enough digits to round to a correct
        // integer value; then remove any fractional digits

        int maxDigits = (int)Math.min(this.precision() +
                                      (long)Math.ceil(10.0*divisor.precision()/3.0) +
                                      Math.abs((long)this.scale() - divisor.scale()) + 2,
                                      Integer.MAX_VALUE);
        BigDecimal quotient = this.divide(divisor, new MathContext(maxDigits,
                                                                   RoundingMode.DOWN));
        if (quotient.scale > 0) {
            quotient = quotient.setScale(0, RoundingMode.DOWN);
            quotient = stripZerosToMatchScale(quotient.intVal, quotient.intCompact, quotient.scale, preferredScale);
        }

        if (quotient.scale < preferredScale) {
            // pad with zeros if necessary
            quotient = quotient.setScale(preferredScale, ROUND_UNNECESSARY);
        }

        return quotient;
    }

    /**
     * Returns a {@code BigDecimal} whose value is the integer part
     * of {@code (this / divisor)}.  Since the integer part of the
     * exact quotient does not depend on the rounding mode, the
     * rounding mode does not affect the values returned by this
     * method.  The preferred scale of the result is
     * {@code (this.scale() - divisor.scale())}.  An
     * {@code ArithmeticException} is thrown if the integer part of
     * the exact quotient needs more than {@code mc.precision}
     * digits.
     *
     * @param  divisor value by which this {@code BigDecimal} is to be divided.
     * @param  mc the context to use.
     * @return The integer part of {@code this / divisor}.
     * @throws ArithmeticException if {@code divisor==0}
     * @throws ArithmeticException if {@code mc.precision} {@literal >} 0 and the result
     *         requires a precision of more than {@code mc.precision} digits.
     * @since  1.5
     * @author Joseph D. Darcy
     */
    public BigDecimal divideToIntegralValue(BigDecimal divisor, MathContext mc) {
        if (mc.precision == 0 || // exact result
            (this.compareMagnitude(divisor) < 0)) // zero result
            return divideToIntegralValue(divisor);

        // Calculate preferred scale
        int preferredScale = saturateLong((long)this.scale - divisor.scale);

        /*
         * Perform a normal divide to mc.precision digits.  If the
         * remainder has absolute value less than the divisor, the
         * integer portion of the quotient fits into mc.precision
         * digits.  Next, remove any fractional digits from the
         * quotient and adjust the scale to the preferred value.
         */
        BigDecimal result = this.divide(divisor, new MathContext(mc.precision, RoundingMode.DOWN));

        if (result.scale() < 0) {
            /*
             * Result is an integer. See if quotient represents the
             * full integer portion of the exact quotient; if it does,
             * the computed remainder will be less than the divisor.
             */
            BigDecimal product = result.multiply(divisor);
            // If the quotient is the full integer value,
            // |dividend-product| < |divisor|.
            if (this.subtract(product).compareMagnitude(divisor) >= 0) {
                throw new ArithmeticException("Division impossible");
            }
        } else if (result.scale() > 0) {
            /*
             * Integer portion of quotient will fit into precision
             * digits; recompute quotient to scale 0 to avoid double
             * rounding and then try to adjust, if necessary.
             */
            result = result.setScale(0, RoundingMode.DOWN);
        }
        // else result.scale() == 0;

        int precisionDiff;
        if ((preferredScale > result.scale()) &&
            (precisionDiff = mc.precision - result.precision()) > 0) {
            return result.setScale(result.scale() +
                                   Math.min(precisionDiff, preferredScale - result.scale) );
        } else {
            return stripZerosToMatchScale(result.intVal,result.intCompact,result.scale,preferredScale);
        }
    }

    /**
     * Returns a {@code BigDecimal} whose value is {@code (this % divisor)}.
     *
     * <p>The remainder is given by
     * {@code this.subtract(this.divideToIntegralValue(divisor).multiply(divisor))}.
     * Note that this is <em>not</em> the modulo operation (the result can be
     * negative).
     *
     * @param  divisor value by which this {@code BigDecimal} is to be divided.
     * @return {@code this % divisor}.
     * @throws ArithmeticException if {@code divisor==0}
     * @since  1.5
     */
    public BigDecimal remainder(BigDecimal divisor) {
        BigDecimal[] divrem = this.divideAndRemainder(divisor);
        return divrem[1];
    }


    /**
     * Returns a {@code BigDecimal} whose value is {@code (this %
     * divisor)}, with rounding according to the context settings.
     * The {@code MathContext} settings affect the implicit divide
     * used to compute the remainder.  The remainder computation
     * itself is by definition exact.  Therefore, the remainder may
     * contain more than {@code mc.getPrecision()} digits.
     *
     * <p>The remainder is given by
     * {@code this.subtract(this.divideToIntegralValue(divisor,
     * mc).multiply(divisor))}.  Note that this is not the modulo
     * operation (the result can be negative).
     *
     * @param  divisor value by which this {@code BigDecimal} is to be divided.
     * @param  mc the context to use.
     * @return {@code this % divisor}, rounded as necessary.
     * @throws ArithmeticException if {@code divisor==0}
     * @throws ArithmeticException if the result is inexact but the
     *         rounding mode is {@code UNNECESSARY}, or {@code mc.precision}
     *         {@literal >} 0 and the result of {@code this.divideToIntegralValue(divisor)} would
     *         require a precision of more than {@code mc.precision} digits.
     * @see    #divideToIntegralValue(java.math.BigDecimal, java.math.MathContext)
     * @since  1.5
     */
    public BigDecimal remainder(BigDecimal divisor, MathContext mc) {
        BigDecimal[] divrem = this.divideAndRemainder(divisor, mc);
        return divrem[1];
    }

    /**
     * Returns a two-element {@code BigDecimal} array containing the
     * result of {@code divideToIntegralValue} followed by the result of
     * {@code remainder} on the two operands.
     *
     * <p>Note that if both the integer quotient and remainder are
     * needed, this method is faster than using the
     * {@code divideToIntegralValue} and {@code remainder} methods
     * separately because the division need only be carried out once.
     *
     * @param  divisor value by which this {@code BigDecimal} is to be divided,
     *         and the remainder computed.
     * @return a two element {@code BigDecimal} array: the quotient
     *         (the result of {@code divideToIntegralValue}) is the initial element
     *         and the remainder is the final element.
     * @throws ArithmeticException if {@code divisor==0}
     * @see    #divideToIntegralValue(java.math.BigDecimal, java.math.MathContext)
     * @see    #remainder(java.math.BigDecimal, java.math.MathContext)
     * @since  1.5
     */
    public BigDecimal[] divideAndRemainder(BigDecimal divisor) {
        // we use the identity  x = i * y + r to determine r
        BigDecimal[] result = new BigDecimal[2];

        result[0] = this.divideToIntegralValue(divisor);
        result[1] = this.subtract(result[0].multiply(divisor));
        return result;
    }

    /**
     * Returns a two-element {@code BigDecimal} array containing the
     * result of {@code divideToIntegralValue} followed by the result of
     * {@code remainder} on the two operands calculated with rounding
     * according to the context settings.
     *
     * <p>Note that if both the integer quotient and remainder are
     * needed, this method is faster than using the
     * {@code divideToIntegralValue} and {@code remainder} methods
     * separately because the division need only be carried out once.
     *
     * @param  divisor value by which this {@code BigDecimal} is to be divided,
     *         and the remainder computed.
     * @param  mc the context to use.
     * @return a two element {@code BigDecimal} array: the quotient
     *         (the result of {@code divideToIntegralValue}) is the
     *         initial element and the remainder is the final element.
     * @throws ArithmeticException if {@code divisor==0}
     * @throws ArithmeticException if the result is inexact but the
     *         rounding mode is {@code UNNECESSARY}, or {@code mc.precision}
     *         {@literal >} 0 and the result of {@code this.divideToIntegralValue(divisor)} would
     *         require a precision of more than {@code mc.precision} digits.
     * @see    #divideToIntegralValue(java.math.BigDecimal, java.math.MathContext)
     * @see    #remainder(java.math.BigDecimal, java.math.MathContext)
     * @since  1.5
     */
    public BigDecimal[] divideAndRemainder(BigDecimal divisor, MathContext mc) {
        if (mc.precision == 0)
            return divideAndRemainder(divisor);

        BigDecimal[] result = new BigDecimal[2];
        BigDecimal lhs = this;

        result[0] = lhs.divideToIntegralValue(divisor, mc);
        result[1] = lhs.subtract(result[0].multiply(divisor));
        return result;
    }

    /**
     * Returns an approximation to the square root of {@code this}
     * with rounding according to the context settings.
     *
     * <p>The preferred scale of the returned result is equal to
     * {@code this.scale()/2}. The value of the returned result is
     * always within one ulp of the exact decimal value for the
     * precision in question.  If the rounding mode is {@link
     * RoundingMode#HALF_UP HALF_UP}, {@link RoundingMode#HALF_DOWN
     * HALF_DOWN}, or {@link RoundingMode#HALF_EVEN HALF_EVEN}, the
     * result is within one half an ulp of the exact decimal value.
     *
     * <p>Special case:
     * <ul>
     * <li> The square root of a number numerically equal to {@code
     * ZERO} is numerically equal to {@code ZERO} with a preferred
     * scale according to the general rule above. In particular, for
     * {@code ZERO}, {@code ZERO.sqrt(mc).equals(ZERO)} is true with
     * any {@code MathContext} as an argument.
     * </ul>
     *
     * @param mc the context to use.
     * @return the square root of {@code this}.
     * @throws ArithmeticException if {@code this} is less than zero.
     * @throws ArithmeticException if an exact result is requested
     * ({@code mc.getPrecision()==0}) and there is no finite decimal
     * expansion of the exact result
     * @throws ArithmeticException if
     * {@code (mc.getRoundingMode()==RoundingMode.UNNECESSARY}) and
     * the exact result cannot fit in {@code mc.getPrecision()}
     * digits.
     * @see BigInteger#sqrt()
     * @since  9
     */
    public BigDecimal sqrt(MathContext mc) {
        final int signum = signum();
        if (signum != 1) {
            switch (signum) {
            case -1 -> throw new ArithmeticException("Attempted square root of negative BigDecimal");
            case 0 -> {
                BigDecimal result = valueOf(0L, scale/2);
                assert squareRootResultAssertions(result, mc);
                return result;
            }
            default -> throw new AssertionError("Bad value from signum");
            }
        }
        /*
         * The main steps of the algorithm below are as follows,
         * first argument reduce the value to an integer
         * using the following relations:
         *
         * x = y * 10 ^ exp
         * sqrt(x) = sqrt(y) * 10^(exp / 2) if exp is even
         * sqrt(x) = sqrt(y*10) * 10^((exp-1)/2) is exp is odd
         *
         * Then use BigInteger.sqrt() on the reduced value to compute
         * the numerical digits of the desired result.
         *
         * Finally, scale back to the desired exponent range and
         * perform any adjustment to get the preferred scale in the
         * representation.
         */

        // The code below favors relative simplicity over checking
        // for special cases that could run faster.
        final int preferredScale = this.scale/2;

        BigDecimal result;
        if (mc.roundingMode == RoundingMode.UNNECESSARY || mc.precision == 0) { // Exact result requested
            // To avoid trailing zeros in the result, strip trailing zeros.
            final BigDecimal stripped = this.stripTrailingZeros();
            final int strippedScale = stripped.scale;

            if ((strippedScale & 1) != 0) // 10*stripped.unscaledValue() can't be an exact square
                throw new ArithmeticException("Computed square root not exact.");

            // Check for even powers of 10. Numerically sqrt(10^2N) = 10^N
            if (stripped.isPowerOfTen()) {
                result = valueOf(1L, strippedScale >> 1);
                // Adjust to requested precision and preferred
                // scale as appropriate.
                return result.adjustToPreferredScale(preferredScale, mc.precision);
            }

            // After stripTrailingZeros, the representation is normalized as
            //
            // unscaledValue * 10^(-scale)
            //
            // where unscaledValue is an integer with the minimum
            // precision for the cohort of the numerical value and the scale is even.
            BigInteger[] sqrtRem = stripped.unscaledValue().sqrtAndRemainder();
            result = new BigDecimal(sqrtRem[0], strippedScale >> 1);

            // If result*result != this numerically or requires too high precision,
            // the square root isn't exact
            if (sqrtRem[1].signum != 0 || mc.precision != 0 && result.precision() > mc.precision)
                throw new ArithmeticException("Computed square root not exact.");

            // Test numerical properties at full precision before any
            // scale adjustments.
            assert squareRootResultAssertions(result, mc);
            // Adjust to requested precision and preferred
            // scale as appropriate.
            return result.adjustToPreferredScale(preferredScale, mc.precision);
        }
        // To allow BigInteger.sqrt() to be used to get the square
        // root, it is necessary to normalize the input so that
        // its integer part is sufficient to get the square root
        // with the desired precision.

        final boolean halfWay = isHalfWay(mc.roundingMode);
        // To obtain a square root with N digits,
        // the radicand must have at least 2*(N-1)+1 == 2*N-1 digits.
        final long minWorkingPrec = ((mc.precision + (halfWay ? 1L : 0L)) << 1) - 1L;
        // normScale is the number of digits to take from the fraction of the input
        long normScale = minWorkingPrec - this.precision() + this.scale;
        normScale += normScale & 1L; // the scale for normalizing must be even

        final long workingScale = this.scale - normScale;
        if (workingScale != (int) workingScale)
            throw new ArithmeticException("Overflow");

        BigDecimal working = new BigDecimal(this.intVal, this.intCompact, (int) workingScale, this.precision);
        BigInteger workingInt = working.toBigInteger();

        BigInteger sqrt;
        long resultScale = normScale >> 1;
        // Round sqrt with the specified settings
        if (halfWay) { // half-way rounding
            BigInteger workingSqrt = workingInt.sqrt();
            // remove the one-tenth digit
            BigInteger[] quotRem10 = workingSqrt.divideAndRemainder(BigInteger.TEN);
            sqrt = quotRem10[0];
            resultScale--;

            boolean increment = false;
            int digit = quotRem10[1].intValue();
            if (digit > 5) {
                increment = true;
            } else if (digit == 5) {
                if (mc.roundingMode == RoundingMode.HALF_UP
                        || mc.roundingMode == RoundingMode.HALF_EVEN && sqrt.testBit(0)
                        // Check if remainder is non-zero
                        || !workingInt.equals(workingSqrt.multiply(workingSqrt))
                        || !working.isInteger()) {
                    increment = true;
                }
            }

            if (increment)
                sqrt = sqrt.add(1L);
        } else {
            switch (mc.roundingMode) {
            case DOWN, FLOOR -> sqrt = workingInt.sqrt(); // No need to round

            case UP, CEILING -> {
                BigInteger[] sqrtRem = workingInt.sqrtAndRemainder();
                sqrt = sqrtRem[0];
                // Check if remainder is non-zero
                if (sqrtRem[1].signum != 0 || !working.isInteger())
                    sqrt = sqrt.add(1L);
            }

            default -> throw new AssertionError("Unexpected value for RoundingMode: " + mc.roundingMode);
            }
        }

        result = new BigDecimal(sqrt, checkScale(sqrt, resultScale), mc); // mc ensures no increase of precision
        // Test numerical properties at full precision before any
        // scale adjustments.
        assert squareRootResultAssertions(result, mc);
        // Adjust to requested precision and preferred
        // scale as appropriate.
        if (result.scale > preferredScale) // else can't increase the result's precision to fit the preferred scale
            result = stripZerosToMatchScale(result.intVal, result.intCompact, result.scale, preferredScale);

        return result;
    }

    /**
     * Assumes {@code (precision() <= maxPrecision || maxPrecision == 0) && this != 0}.
     * @param preferredScale the scale to reach
     * @param maxPrecision the largest precision the result can have.
     *        {@code maxPrecision == 0} means that the result can have arbitrary precision.
     * @return a BigDecimal numerically equivalent to {@code this}, whose precision
     *         does not exceed {@code maxPrecision} and whose scale is the closest
     *         to {@code preferredScale}.
     */
    private BigDecimal adjustToPreferredScale(int preferredScale, int maxPrecision) {
        BigDecimal result = this;
        if (result.scale > preferredScale) {
            result = stripZerosToMatchScale(result.intVal, result.intCompact, result.scale, preferredScale);
        } else if (result.scale < preferredScale) {
            int maxScale = maxPrecision == 0 ?
                preferredScale : (int) Math.min(preferredScale, result.scale + (long) (maxPrecision - result.precision()));
            result = result.setScale(maxScale);
        }
        return result;
    }

    private static boolean isHalfWay(RoundingMode m) {
        return switch (m) {
            case HALF_DOWN, HALF_UP, HALF_EVEN -> true;
            case FLOOR, CEILING, DOWN, UP, UNNECESSARY -> false;
        };
    }

    private BigDecimal square() {
        return this.multiply(this);
    }

    private boolean isPowerOfTen() {
        return BigInteger.ONE.equals(this.unscaledValue());
    }

    /**
     * For nonzero values, check numerical correctness properties of
     * the computed result for the chosen rounding mode.
     *
     * For the directed rounding modes:
     *
     * <ul>
     *
     * <li> For DOWN and FLOOR, result^2 must be {@code <=} the input
     * and (result+ulp)^2 must be {@code >} the input.
     *
     * <li>Conversely, for UP and CEIL, result^2 must be {@code >=}
     * the input and (result-ulp)^2 must be {@code <} the input.
     * </ul>
     */
    private boolean squareRootResultAssertions(BigDecimal result, MathContext mc) {
        if (result.signum() == 0) {
            return squareRootZeroResultAssertions(result, mc);
        } else {
            RoundingMode rm = mc.getRoundingMode();
            BigDecimal ulp = result.ulp();
            BigDecimal neighborUp   = result.add(ulp);
            // Make neighbor down accurate even for powers of ten
            if (result.isPowerOfTen()) {
                ulp = ulp.divide(TEN);
            }
            BigDecimal neighborDown = result.subtract(ulp);

            // Both the starting value and result should be nonzero and positive.
            assert (result.signum() == 1 &&
                    this.signum() == 1) :
                "Bad signum of this and/or its sqrt.";

            switch (rm) {
            case DOWN:
            case FLOOR:
                assert
                    result.square().compareTo(this)     <= 0 &&
                    neighborUp.square().compareTo(this) > 0:
                "Square of result out for bounds rounding " + rm;
                return true;

            case UP:
            case CEILING:
                assert
                    result.square().compareTo(this)       >= 0 &&
                    neighborDown.square().compareTo(this) < 0:
                "Square of result out for bounds rounding " + rm;
                return true;


            case HALF_DOWN:
            case HALF_EVEN:
            case HALF_UP:
                BigDecimal err = result.square().subtract(this).abs();
                BigDecimal errUp = neighborUp.square().subtract(this);
                BigDecimal errDown =  this.subtract(neighborDown.square());
                // All error values should be positive so don't need to
                // compare absolute values.

                int err_comp_errUp = err.compareTo(errUp);
                int err_comp_errDown = err.compareTo(errDown);

                assert
                    errUp.signum()   == 1 &&
                    errDown.signum() == 1 :
                "Errors of neighbors squared don't have correct signs";

                // For breaking a half-way tie, the return value may
                // have a larger error than one of the neighbors. For
                // example, the square root of 2.25 to a precision of
                // 1 digit is either 1 or 2 depending on how the exact
                // value of 1.5 is rounded. If 2 is returned, it will
                // have a larger rounding error than its neighbor 1.
                assert
                    err_comp_errUp   <= 0 ||
                    err_comp_errDown <= 0 :
                "Computed square root has larger error than neighbors for " + rm;

                assert
                    ((err_comp_errUp   == 0 ) ? err_comp_errDown < 0 : true) &&
                    ((err_comp_errDown == 0 ) ? err_comp_errUp   < 0 : true) :
                        "Incorrect error relationships";
                // && could check for digit conditions for ties too
                return true;

            default: // Definition of UNNECESSARY already verified.
                return true;
            }
        }
    }

    private boolean squareRootZeroResultAssertions(BigDecimal result, MathContext mc) {
        return this.compareTo(ZERO) == 0;
    }

    /**
     * Returns a {@code BigDecimal} whose value is
     * <code>(this<sup>n</sup>)</code>, The power is computed exactly, to
     * unlimited precision.
     *
     * <p>The parameter {@code n} must be in the range 0 through
     * 999999999, inclusive.  {@code ZERO.pow(0)} returns {@link
     * #ONE}.
     *
     * Note that future releases may expand the allowable exponent
     * range of this method.
     *
     * @param  n power to raise this {@code BigDecimal} to.
     * @return <code>this<sup>n</sup></code>
     * @throws ArithmeticException if {@code n} is out of range.
     * @since  1.5
     */
    public BigDecimal pow(int n) {
        if (n < 0 || n > 999999999)
            throw new ArithmeticException("Invalid operation");
        // No need to calculate pow(n) if result will over/underflow.
        // Don't attempt to support "supernormal" numbers.
        int newScale = checkScale((long)scale * n);
        return new BigDecimal(this.inflated().pow(n), newScale);
    }


    /**
     * Returns a {@code BigDecimal} whose value is
     * <code>(this<sup>n</sup>)</code>.  The current implementation uses
     * the core algorithm defined in ANSI standard X3.274-1996 with
     * rounding according to the context settings.  In general, the
     * returned numerical value is within two ulps of the exact
     * numerical value for the chosen precision.  Note that future
     * releases may use a different algorithm with a decreased
     * allowable error bound and increased allowable exponent range.
     *
     * <p>The X3.274-1996 algorithm is:
     *
     * <ul>
     * <li> An {@code ArithmeticException} exception is thrown if
     *  <ul>
     *    <li>{@code abs(n) > 999999999}
     *    <li>{@code mc.precision == 0} and {@code n < 0}
     *    <li>{@code mc.precision > 0} and {@code n} has more than
     *    {@code mc.precision} decimal digits
     *  </ul>
     *
     * <li> if {@code n} is zero, {@link #ONE} is returned even if
     * {@code this} is zero, otherwise
     * <ul>
     *   <li> if {@code n} is positive, the result is calculated via
     *   the repeated squaring technique into a single accumulator.
     *   The individual multiplications with the accumulator use the
     *   same math context settings as in {@code mc} except for a
     *   precision increased to {@code mc.precision + elength + 1}
     *   where {@code elength} is the number of decimal digits in
     *   {@code n}.
     *
     *   <li> if {@code n} is negative, the result is calculated as if
     *   {@code n} were positive; this value is then divided into one
     *   using the working precision specified above.
     *
     *   <li> The final value from either the positive or negative case
     *   is then rounded to the destination precision.
     *   </ul>
     * </ul>
     *
     * @param  n power to raise this {@code BigDecimal} to.
     * @param  mc the context to use.
     * @return <code>this<sup>n</sup></code> using the ANSI standard X3.274-1996
     *         algorithm
     * @throws ArithmeticException if the result is inexact but the
     *         rounding mode is {@code UNNECESSARY}, or {@code n} is out
     *         of range.
     * @since  1.5
     */
    public BigDecimal pow(int n, MathContext mc) {
        if (mc.precision == 0)
            return pow(n);
        if (n < -999999999 || n > 999999999)
            throw new ArithmeticException("Invalid operation");
        if (n == 0)
            return ONE;                      // x**0 == 1 in X3.274
        BigDecimal lhs = this;
        MathContext workmc = mc;           // working settings
        int mag = Math.abs(n);               // magnitude of n
        if (mc.precision > 0) {
            int elength = longDigitLength(mag); // length of n in digits
            if (elength > mc.precision)        // X3.274 rule
                throw new ArithmeticException("Invalid operation");
            workmc = new MathContext(mc.precision + elength + 1,
                                      mc.roundingMode);
        }
        // ready to carry out power calculation...
        BigDecimal acc = ONE;           // accumulator
        boolean seenbit = false;        // set once we've seen a 1-bit
        for (int i=1;;i++) {            // for each bit [top bit ignored]
            mag += mag;                 // shift left 1 bit
            if (mag < 0) {              // top bit is set
                seenbit = true;         // OK, we're off
                acc = acc.multiply(lhs, workmc); // acc=acc*x
            }
            if (i == 31)
                break;                  // that was the last bit
            if (seenbit)
                acc=acc.multiply(acc, workmc);   // acc=acc*acc [square]
                // else (!seenbit) no point in squaring ONE
        }
        // if negative n, calculate the reciprocal using working precision
        if (n < 0) // [hence mc.precision>0]
            acc=ONE.divide(acc, workmc);
        // round to final precision and strip zeros
        return doRound(acc, mc);
    }

    /**
     * Returns a {@code BigDecimal} whose value is the absolute value
     * of this {@code BigDecimal}, and whose scale is
     * {@code this.scale()}.
     *
     * @return {@code abs(this)}
     */
    public BigDecimal abs() {
        return (signum() < 0 ? negate() : this);
    }

    /**
     * Returns a {@code BigDecimal} whose value is the absolute value
     * of this {@code BigDecimal}, with rounding according to the
     * context settings.
     *
     * @param mc the context to use.
     * @return {@code abs(this)}, rounded as necessary.
     * @since 1.5
     */
    public BigDecimal abs(MathContext mc) {
        return (signum() < 0 ? negate(mc) : plus(mc));
    }

    /**
     * Returns a {@code BigDecimal} whose value is {@code (-this)},
     * and whose scale is {@code this.scale()}.
     *
     * @return {@code -this}.
     */
    public BigDecimal negate() {
        if (intCompact == INFLATED) {
            return new BigDecimal(intVal.negate(), INFLATED, scale, precision);
        } else {
            return valueOf(-intCompact, scale, precision);
        }
    }

    /**
     * Returns a {@code BigDecimal} whose value is {@code (-this)},
     * with rounding according to the context settings.
     *
     * @param mc the context to use.
     * @return {@code -this}, rounded as necessary.
     * @since  1.5
     */
    public BigDecimal negate(MathContext mc) {
        return negate().plus(mc);
    }

    /**
     * Returns a {@code BigDecimal} whose value is {@code (+this)}, and whose
     * scale is {@code this.scale()}.
     *
     * <p>This method, which simply returns this {@code BigDecimal}
     * is included for symmetry with the unary minus method {@link
     * #negate()}.
     *
     * @return {@code this}.
     * @see #negate()
     * @since  1.5
     */
    public BigDecimal plus() {
        return this;
    }

    /**
     * Returns a {@code BigDecimal} whose value is {@code (+this)},
     * with rounding according to the context settings.
     *
     * <p>The effect of this method is identical to that of the {@link
     * #round(MathContext)} method.
     *
     * @param mc the context to use.
     * @return {@code this}, rounded as necessary.  A zero result will
     *         have a scale of 0.
     * @see    #round(MathContext)
     * @since  1.5
     */
    public BigDecimal plus(MathContext mc) {
        if (mc.precision == 0)                 // no rounding please
            return this;
        return doRound(this, mc);
    }

    /**
     * Returns the signum function of this {@code BigDecimal}.
     *
     * @return -1, 0, or 1 as the value of this {@code BigDecimal}
     *         is negative, zero, or positive.
     */
    public int signum() {
        return (intCompact != INFLATED)?
            Long.signum(intCompact):
            intVal.signum();
    }

    /**
     * Returns the <i>scale</i> of this {@code BigDecimal}.  If zero
     * or positive, the scale is the number of digits to the right of
     * the decimal point.  If negative, the unscaled value of the
     * number is multiplied by ten to the power of the negation of the
     * scale.  For example, a scale of {@code -3} means the unscaled
     * value is multiplied by 1000.
     *
     * @return the scale of this {@code BigDecimal}.
     */
    public int scale() {
        return scale;
    }

    /**
     * Returns the <i>precision</i> of this {@code BigDecimal}.  (The
     * precision is the number of digits in the unscaled value.)
     *
     * <p>The precision of a zero value is 1.
     *
     * @return the precision of this {@code BigDecimal}.
     * @since  1.5
     */
    public int precision() {
        int result = precision;
        if (result == 0) {
            long s = intCompact;
            if (s != INFLATED)
                result = longDigitLength(s);
            else
                result = bigDigitLength(intVal);
            precision = result;
        }
        return result;
    }


    /**
     * Returns a {@code BigInteger} whose value is the <i>unscaled
     * value</i> of this {@code BigDecimal}.  (Computes <code>(this *
     * 10<sup>this.scale()</sup>)</code>.)
     *
     * @return the unscaled value of this {@code BigDecimal}.
     * @since  1.2
     */
    public BigInteger unscaledValue() {
        return this.inflated();
    }

    // Rounding Modes

    /**
     * Rounding mode to round away from zero.  Always increments the
     * digit prior to a nonzero discarded fraction.  Note that this rounding
     * mode never decreases the magnitude of the calculated value.
     *
     * @deprecated Use {@link RoundingMode#UP} instead.
     */
    @Deprecated(since="9")
    public static final int ROUND_UP =           0;

    /**
     * Rounding mode to round towards zero.  Never increments the digit
     * prior to a discarded fraction (i.e., truncates).  Note that this
     * rounding mode never increases the magnitude of the calculated value.
     *
     * @deprecated Use {@link RoundingMode#DOWN} instead.
     */
    @Deprecated(since="9")
    public static final int ROUND_DOWN =         1;

    /**
     * Rounding mode to round towards positive infinity.  If the
     * {@code BigDecimal} is positive, behaves as for
     * {@code ROUND_UP}; if negative, behaves as for
     * {@code ROUND_DOWN}.  Note that this rounding mode never
     * decreases the calculated value.
     *
     * @deprecated Use {@link RoundingMode#CEILING} instead.
     */
    @Deprecated(since="9")
    public static final int ROUND_CEILING =      2;

    /**
     * Rounding mode to round towards negative infinity.  If the
     * {@code BigDecimal} is positive, behave as for
     * {@code ROUND_DOWN}; if negative, behave as for
     * {@code ROUND_UP}.  Note that this rounding mode never
     * increases the calculated value.
     *
     * @deprecated Use {@link RoundingMode#FLOOR} instead.
     */
    @Deprecated(since="9")
    public static final int ROUND_FLOOR =        3;

    /**
     * Rounding mode to round towards {@literal "nearest neighbor"}
     * unless both neighbors are equidistant, in which case round up.
     * Behaves as for {@code ROUND_UP} if the discarded fraction is
     * &ge; 0.5; otherwise, behaves as for {@code ROUND_DOWN}.  Note
     * that this is the rounding mode that most of us were taught in
     * grade school.
     *
     * @deprecated Use {@link RoundingMode#HALF_UP} instead.
     */
    @Deprecated(since="9")
    public static final int ROUND_HALF_UP =      4;

    /**
     * Rounding mode to round towards {@literal "nearest neighbor"}
     * unless both neighbors are equidistant, in which case round
     * down.  Behaves as for {@code ROUND_UP} if the discarded
     * fraction is {@literal >} 0.5; otherwise, behaves as for
     * {@code ROUND_DOWN}.
     *
     * @deprecated Use {@link RoundingMode#HALF_DOWN} instead.
     */
    @Deprecated(since="9")
    public static final int ROUND_HALF_DOWN =    5;

    /**
     * Rounding mode to round towards the {@literal "nearest neighbor"}
     * unless both neighbors are equidistant, in which case, round
     * towards the even neighbor.  Behaves as for
     * {@code ROUND_HALF_UP} if the digit to the left of the
     * discarded fraction is odd; behaves as for
     * {@code ROUND_HALF_DOWN} if it's even.  Note that this is the
     * rounding mode that minimizes cumulative error when applied
     * repeatedly over a sequence of calculations.
     *
     * @deprecated Use {@link RoundingMode#HALF_EVEN} instead.
     */
    @Deprecated(since="9")
    public static final int ROUND_HALF_EVEN =    6;

    /**
     * Rounding mode to assert that the requested operation has an exact
     * result, hence no rounding is necessary.  If this rounding mode is
     * specified on an operation that yields an inexact result, an
     * {@code ArithmeticException} is thrown.
     *
     * @deprecated Use {@link RoundingMode#UNNECESSARY} instead.
     */
    @Deprecated(since="9")
    public static final int ROUND_UNNECESSARY =  7;


    // Scaling/Rounding Operations

    /**
     * Returns a {@code BigDecimal} rounded according to the
     * {@code MathContext} settings.  If the precision setting is 0 then
     * no rounding takes place.
     *
     * <p>The effect of this method is identical to that of the
     * {@link #plus(MathContext)} method.
     *
     * @param mc the context to use.
     * @return a {@code BigDecimal} rounded according to the
     *         {@code MathContext} settings.
     * @see    #plus(MathContext)
     * @since  1.5
     */
    public BigDecimal round(MathContext mc) {
        return plus(mc);
    }

    /**
     * Returns a {@code BigDecimal} whose scale is the specified
     * value, and whose unscaled value is determined by multiplying or
     * dividing this {@code BigDecimal}'s unscaled value by the
     * appropriate power of ten to maintain its overall value.  If the
     * scale is reduced by the operation, the unscaled value must be
     * divided (rather than multiplied), and the value may be changed;
     * in this case, the specified rounding mode is applied to the
     * division.
     *
     * @apiNote Since BigDecimal objects are immutable, calls of
     * this method do <em>not</em> result in the original object being
     * modified, contrary to the usual convention of having methods
     * named <code>set<i>X</i></code> mutate field <i>{@code X}</i>.
     * Instead, {@code setScale} returns an object with the proper
     * scale; the returned object may or may not be newly allocated.
     *
     * @param  newScale scale of the {@code BigDecimal} value to be returned.
     * @param  roundingMode The rounding mode to apply.
     * @return a {@code BigDecimal} whose scale is the specified value,
     *         and whose unscaled value is determined by multiplying or
     *         dividing this {@code BigDecimal}'s unscaled value by the
     *         appropriate power of ten to maintain its overall value.
     * @throws ArithmeticException if {@code roundingMode==UNNECESSARY}
     *         and the specified scaling operation would require
     *         rounding.
     * @see    RoundingMode
     * @since  1.5
     */
    public BigDecimal setScale(int newScale, RoundingMode roundingMode) {
        return setScale(newScale, roundingMode.oldMode);
    }

    /**
     * Returns a {@code BigDecimal} whose scale is the specified
     * value, and whose unscaled value is determined by multiplying or
     * dividing this {@code BigDecimal}'s unscaled value by the
     * appropriate power of ten to maintain its overall value.  If the
     * scale is reduced by the operation, the unscaled value must be
     * divided (rather than multiplied), and the value may be changed;
     * in this case, the specified rounding mode is applied to the
     * division.
     *
     * @apiNote Since BigDecimal objects are immutable, calls of
     * this method do <em>not</em> result in the original object being
     * modified, contrary to the usual convention of having methods
     * named <code>set<i>X</i></code> mutate field <i>{@code X}</i>.
     * Instead, {@code setScale} returns an object with the proper
     * scale; the returned object may or may not be newly allocated.
     *
     * @deprecated The method {@link #setScale(int, RoundingMode)} should
     * be used in preference to this legacy method.
     *
     * @param  newScale scale of the {@code BigDecimal} value to be returned.
     * @param  roundingMode The rounding mode to apply.
     * @return a {@code BigDecimal} whose scale is the specified value,
     *         and whose unscaled value is determined by multiplying or
     *         dividing this {@code BigDecimal}'s unscaled value by the
     *         appropriate power of ten to maintain its overall value.
     * @throws ArithmeticException if {@code roundingMode==ROUND_UNNECESSARY}
     *         and the specified scaling operation would require
     *         rounding.
     * @throws IllegalArgumentException if {@code roundingMode} does not
     *         represent a valid rounding mode.
     * @see    #ROUND_UP
     * @see    #ROUND_DOWN
     * @see    #ROUND_CEILING
     * @see    #ROUND_FLOOR
     * @see    #ROUND_HALF_UP
     * @see    #ROUND_HALF_DOWN
     * @see    #ROUND_HALF_EVEN
     * @see    #ROUND_UNNECESSARY
     */
    @Deprecated(since="9")
    public BigDecimal setScale(int newScale, int roundingMode) {
        if (roundingMode < ROUND_UP || roundingMode > ROUND_UNNECESSARY)
            throw new IllegalArgumentException("Invalid rounding mode");

        int oldScale = this.scale;
        if (newScale == oldScale)        // easy case
            return this;
        if (this.signum() == 0)            // zero can have any scale
            return zeroValueOf(newScale);
        if(this.intCompact!=INFLATED) {
            long rs = this.intCompact;
            if (newScale > oldScale) {
                int raise = checkScale((long) newScale - oldScale);
                if ((rs = longMultiplyPowerTen(rs, raise)) != INFLATED) {
                    return valueOf(rs,newScale);
                }
                BigInteger rb = bigMultiplyPowerTen(raise);
                return new BigDecimal(rb, INFLATED, newScale, (precision > 0) ? precision + raise : 0);
            } else {
                // newScale < oldScale -- drop some digits
                // Can't predict the precision due to the effect of rounding.
                int drop = checkScale((long) oldScale - newScale);
                if (drop < LONG_TEN_POWERS_TABLE.length) {
                    return divideAndRound(rs, LONG_TEN_POWERS_TABLE[drop], newScale, roundingMode, newScale);
                } else {
                    return divideAndRound(this.inflated(), bigTenToThe(drop), newScale, roundingMode, newScale);
                }
            }
        } else {
            if (newScale > oldScale) {
                int raise = checkScale((long) newScale - oldScale);
                BigInteger rb = bigMultiplyPowerTen(this.intVal,raise);
                return new BigDecimal(rb, INFLATED, newScale, (precision > 0) ? precision + raise : 0);
            } else {
                // newScale < oldScale -- drop some digits
                // Can't predict the precision due to the effect of rounding.
                int drop = checkScale((long) oldScale - newScale);
                if (drop < LONG_TEN_POWERS_TABLE.length)
                    return divideAndRound(this.intVal, LONG_TEN_POWERS_TABLE[drop], newScale, roundingMode,
                                          newScale);
                else
                    return divideAndRound(this.intVal,  bigTenToThe(drop), newScale, roundingMode, newScale);
            }
        }
    }

    /**
     * Returns a {@code BigDecimal} whose scale is the specified
     * value, and whose value is numerically equal to this
     * {@code BigDecimal}'s.  Throws an {@code ArithmeticException}
     * if this is not possible.
     *
     * <p>This call is typically used to increase the scale, in which
     * case it is guaranteed that there exists a {@code BigDecimal}
     * of the specified scale and the correct value.  The call can
     * also be used to reduce the scale if the caller knows that the
     * {@code BigDecimal} has sufficiently many zeros at the end of
     * its fractional part (i.e., factors of ten in its integer value)
     * to allow for the rescaling without changing its value.
     *
     * <p>This method returns the same result as the two-argument
     * versions of {@code setScale}, but saves the caller the trouble
     * of specifying a rounding mode in cases where it is irrelevant.
     *
     * @apiNote Since {@code BigDecimal} objects are immutable,
     * calls of this method do <em>not</em> result in the original
     * object being modified, contrary to the usual convention of
     * having methods named <code>set<i>X</i></code> mutate field
     * <i>{@code X}</i>.  Instead, {@code setScale} returns an
     * object with the proper scale; the returned object may or may
     * not be newly allocated.
     *
     * @param  newScale scale of the {@code BigDecimal} value to be returned.
     * @return a {@code BigDecimal} whose scale is the specified value, and
     *         whose unscaled value is determined by multiplying or dividing
     *         this {@code BigDecimal}'s unscaled value by the appropriate
     *         power of ten to maintain its overall value.
     * @throws ArithmeticException if the specified scaling operation would
     *         require rounding.
     * @see    #setScale(int, int)
     * @see    #setScale(int, RoundingMode)
     */
    public BigDecimal setScale(int newScale) {
        return setScale(newScale, ROUND_UNNECESSARY);
    }

    // Decimal Point Motion Operations

    /**
     * Returns a {@code BigDecimal} which is equivalent to this one
     * with the decimal point moved {@code n} places to the left.  If
     * {@code n} is non-negative, the call merely adds {@code n} to
     * the scale.  If {@code n} is negative, the call is equivalent
     * to {@code movePointRight(-n)}.  The {@code BigDecimal}
     * returned by this call has value <code>(this &times;
     * 10<sup>-n</sup>)</code> and scale {@code max(this.scale()+n,
     * 0)}.
     *
     * @param  n number of places to move the decimal point to the left.
     * @return a {@code BigDecimal} which is equivalent to this one with the
     *         decimal point moved {@code n} places to the left.
     * @throws ArithmeticException if scale overflows.
     */
    public BigDecimal movePointLeft(int n) {
        if (n == 0 && scale >= 0) return this;

        // Cannot use movePointRight(-n) in case of n==Integer.MIN_VALUE
        int newScale = checkScale((long)scale + n);
        BigDecimal num = new BigDecimal(intVal, intCompact, newScale, 0);
        return num.scale < 0 ? num.setScale(0, ROUND_UNNECESSARY) : num;
    }

    /**
     * Returns a {@code BigDecimal} which is equivalent to this one
     * with the decimal point moved {@code n} places to the right.
     * If {@code n} is non-negative, the call merely subtracts
     * {@code n} from the scale.  If {@code n} is negative, the call
     * is equivalent to {@code movePointLeft(-n)}.  The
     * {@code BigDecimal} returned by this call has value <code>(this
     * &times; 10<sup>n</sup>)</code> and scale {@code max(this.scale()-n,
     * 0)}.
     *
     * @param  n number of places to move the decimal point to the right.
     * @return a {@code BigDecimal} which is equivalent to this one
     *         with the decimal point moved {@code n} places to the right.
     * @throws ArithmeticException if scale overflows.
     */
    public BigDecimal movePointRight(int n) {
        if (n == 0 && scale >= 0) return this;

        // Cannot use movePointLeft(-n) in case of n==Integer.MIN_VALUE
        int newScale = checkScale((long)scale - n);
        BigDecimal num = new BigDecimal(intVal, intCompact, newScale, 0);
        return num.scale < 0 ? num.setScale(0, ROUND_UNNECESSARY) : num;
    }

    /**
     * Returns a BigDecimal whose numerical value is equal to
     * ({@code this} * 10<sup>n</sup>).  The scale of
     * the result is {@code (this.scale() - n)}.
     *
     * @param n the exponent power of ten to scale by
     * @return a BigDecimal whose numerical value is equal to
     * ({@code this} * 10<sup>n</sup>)
     * @throws ArithmeticException if the scale would be
     *         outside the range of a 32-bit integer.
     *
     * @since 1.5
     */
    public BigDecimal scaleByPowerOfTen(int n) {
        return new BigDecimal(intVal, intCompact,
                              checkScale((long)scale - n), precision);
    }

    /**
     * Returns a {@code BigDecimal} which is numerically equal to
     * this one but with any trailing zeros removed from the
     * representation.  For example, stripping the trailing zeros from
     * the {@code BigDecimal} value {@code 600.0}, which has
     * [{@code BigInteger}, {@code scale}] components equal to
     * [6000, 1], yields {@code 6E2} with [{@code BigInteger},
     * {@code scale}] components equal to [6, -2].  If
     * this BigDecimal is numerically equal to zero, then
     * {@code BigDecimal.ZERO} is returned.
     *
     * @return a numerically equal {@code BigDecimal} with any
     * trailing zeros removed.
     * @throws ArithmeticException if scale overflows.
     * @since 1.5
     */
    public BigDecimal stripTrailingZeros() {
        return intCompact == 0 || (intVal != null && intVal.signum() == 0)
                ? BigDecimal.ZERO
                : stripZerosToMatchScale(intVal, intCompact, scale, Long.MIN_VALUE);
    }

    // Comparison Operations

    /**
     * Compares this {@code BigDecimal} numerically with the specified
     * {@code BigDecimal}.  Two {@code BigDecimal} objects that are
     * equal in value but have a different scale (like 2.0 and 2.00)
     * are considered equal by this method. Such values are in the
     * same <i>cohort</i>.
     *
     * This method is provided in preference to individual methods for
     * each of the six boolean comparison operators ({@literal <}, ==,
     * {@literal >}, {@literal >=}, !=, {@literal <=}).  The suggested
     * idiom for performing these comparisons is: {@code
     * (x.compareTo(y)} &lt;<i>op</i>&gt; {@code 0)}, where
     * &lt;<i>op</i>&gt; is one of the six comparison operators.

     * @apiNote
     * Note: this class has a natural ordering that is inconsistent with equals.
     * The behavior of comparing the result of this method for
     * equality to 0 is analogous to checking the {@linkplain
     * Double##fpNumericalEq numerical equality} of {@code double} values.
     *
     * @param  val {@code BigDecimal} to which this {@code BigDecimal} is
     *         to be compared.
     * @return -1, 0, or 1 as this {@code BigDecimal} is numerically
     *          less than, equal to, or greater than {@code val}.
     */
    @Override
    public int compareTo(BigDecimal val) {
        // Quick path for equal scale and non-inflated case.
        if (scale == val.scale) {
            long xs = intCompact;
            long ys = val.intCompact;
            if (xs != INFLATED && ys != INFLATED)
                return xs != ys ? ((xs > ys) ? 1 : -1) : 0;
        }
        int xsign = this.signum();
        int ysign = val.signum();
        if (xsign != ysign)
            return (xsign > ysign) ? 1 : -1;
        if (xsign == 0)
            return 0;
        int cmp = compareMagnitude(val);
        return (xsign > 0) ? cmp : -cmp;
    }

    /**
     * Version of compareTo that ignores sign.
     */
    private int compareMagnitude(BigDecimal val) {
        // Match scales, avoid unnecessary inflation
        long ys = val.intCompact;
        long xs = this.intCompact;
        if (xs == 0)
            return (ys == 0) ? 0 : -1;
        if (ys == 0)
            return 1;

        long sdiff = (long)this.scale - val.scale;
        if (sdiff != 0) {
            // Avoid matching scales if the (adjusted) exponents differ
            long xae = (long)this.precision() - this.scale;   // [-1]
            long yae = (long)val.precision() - val.scale;     // [-1]
            if (xae < yae)
                return -1;
            if (xae > yae)
                return 1;
            if (sdiff < 0) {
                // The cases sdiff <= Integer.MIN_VALUE intentionally fall through.
                if ( sdiff > Integer.MIN_VALUE &&
                      (xs == INFLATED ||
                      (xs = longMultiplyPowerTen(xs, (int)-sdiff)) == INFLATED) &&
                     ys == INFLATED) {
                    BigInteger rb = bigMultiplyPowerTen((int)-sdiff);
                    return rb.compareMagnitude(val.intVal);
                }
            } else { // sdiff > 0
                // The cases sdiff > Integer.MAX_VALUE intentionally fall through.
                if ( sdiff <= Integer.MAX_VALUE &&
                      (ys == INFLATED ||
                      (ys = longMultiplyPowerTen(ys, (int)sdiff)) == INFLATED) &&
                     xs == INFLATED) {
                    BigInteger rb = val.bigMultiplyPowerTen((int)sdiff);
                    return this.intVal.compareMagnitude(rb);
                }
            }
        }
        if (xs != INFLATED)
            return (ys != INFLATED) ? longCompareMagnitude(xs, ys) : -1;
        else if (ys != INFLATED)
            return 1;
        else
            return this.intVal.compareMagnitude(val.intVal);
    }

    /**
     * Compares this {@code BigDecimal} with the specified {@code
     * Object} for equality.  Unlike {@link #compareTo(BigDecimal)
     * compareTo}, this method considers two {@code BigDecimal}
     * objects equal only if they are equal in value and
     * scale. Therefore 2.0 is not equal to 2.00 when compared by this
     * method since the former has [{@code BigInteger}, {@code scale}]
     * components equal to [20, 1] while the latter has components
     * equal to [200, 2].
     *
     * @apiNote
     * One example that shows how 2.0 and 2.00 are <em>not</em>
     * substitutable for each other under some arithmetic operations
     * are the two expressions:<br>
     * {@code new BigDecimal("2.0" ).divide(BigDecimal.valueOf(3),
     * HALF_UP)} which evaluates to 0.7 and <br>
     * {@code new BigDecimal("2.00").divide(BigDecimal.valueOf(3),
     * HALF_UP)} which evaluates to 0.67.
     * The behavior of this method is analogous to checking the
     * {@linkplain Double##repEquivalence representation equivalence}
     * of {@code double} values.
     *
     * @param  x {@code Object} to which this {@code BigDecimal} is
     *         to be compared.
     * @return {@code true} if and only if the specified {@code Object} is a
     *         {@code BigDecimal} whose value and scale are equal to this
     *         {@code BigDecimal}'s.
     * @see    #compareTo(java.math.BigDecimal)
     * @see    #hashCode
     */
    @Override
    public boolean equals(Object x) {
        if (!(x instanceof BigDecimal xDec))
            return false;
        if (x == this)
            return true;
        if (scale != xDec.scale)
            return false;
        long s = this.intCompact;
        long xs = xDec.intCompact;
        if (s != INFLATED) {
            if (xs == INFLATED)
                xs = compactValFor(xDec.intVal);
            return xs == s;
        } else if (xs != INFLATED)
            return xs == compactValFor(this.intVal);

        return this.inflated().equals(xDec.inflated());
    }

    /**
     * Returns the minimum of this {@code BigDecimal} and
     * {@code val}.
     *
     * @param  val value with which the minimum is to be computed.
     * @return the {@code BigDecimal} whose value is the lesser of this
     *         {@code BigDecimal} and {@code val}.  If they are equal,
     *         as defined by the {@link #compareTo(BigDecimal) compareTo}
     *         method, {@code this} is returned.
     * @see    #compareTo(java.math.BigDecimal)
     */
    public BigDecimal min(BigDecimal val) {
        return (compareTo(val) <= 0 ? this : val);
    }

    /**
     * Returns the maximum of this {@code BigDecimal} and {@code val}.
     *
     * @param  val value with which the maximum is to be computed.
     * @return the {@code BigDecimal} whose value is the greater of this
     *         {@code BigDecimal} and {@code val}.  If they are equal,
     *         as defined by the {@link #compareTo(BigDecimal) compareTo}
     *         method, {@code this} is returned.
     * @see    #compareTo(java.math.BigDecimal)
     */
    public BigDecimal max(BigDecimal val) {
        return (compareTo(val) >= 0 ? this : val);
    }

    // Hash Function

    /**
     * Returns the hash code for this {@code BigDecimal}.
     * The hash code is computed as a function of the {@linkplain
     * unscaledValue() unscaled value} and the {@linkplain scale()
     * scale} of this {@code BigDecimal}.
     *
     * @apiNote
     * Two {@code BigDecimal} objects that are numerically equal but
     * differ in scale (like 2.0 and 2.00) will generally <em>not</em>
     * have the same hash code.
     *
     * @return hash code for this {@code BigDecimal}.
     * @see #equals(Object)
     */
    @Override
    public int hashCode() {
        if (intCompact != INFLATED) {
            long val2 = (intCompact < 0)? -intCompact : intCompact;
            int temp = (int)( ((int)(val2 >>> 32)) * 31  +
                              (val2 & LONG_MASK));
            return 31*((intCompact < 0) ?-temp:temp) + scale;
        } else
            return 31*intVal.hashCode() + scale;
    }

    // Format Converters

    /**
     * Returns the string representation of this {@code BigDecimal},
     * using scientific notation if an exponent is needed.
     *
     * <p>A standard canonical string form of the {@code BigDecimal}
     * is created as though by the following steps: first, the
     * absolute value of the unscaled value of the {@code BigDecimal}
     * is converted to a string in base ten using the characters
     * {@code '0'} through {@code '9'} with no leading zeros (except
     * if its value is zero, in which case a single {@code '0'}
     * character is used).
     *
     * <p>Next, an <i>adjusted exponent</i> is calculated; this is the
     * negated scale, plus the number of characters in the converted
     * unscaled value, less one.  That is,
     * {@code -scale+(ulength-1)}, where {@code ulength} is the
     * length of the absolute value of the unscaled value in decimal
     * digits (its <i>precision</i>).
     *
     * <p>If the scale is greater than or equal to zero and the
     * adjusted exponent is greater than or equal to {@code -6}, the
     * number will be converted to a character form without using
     * exponential notation.  In this case, if the scale is zero then
     * no decimal point is added and if the scale is positive a
     * decimal point will be inserted with the scale specifying the
     * number of characters to the right of the decimal point.
     * {@code '0'} characters are added to the left of the converted
     * unscaled value as necessary.  If no character precedes the
     * decimal point after this insertion then a conventional
     * {@code '0'} character is prefixed.
     *
     * <p>Otherwise (that is, if the scale is negative, or the
     * adjusted exponent is less than {@code -6}), the number will be
     * converted to a character form using exponential notation.  In
     * this case, if the converted {@code BigInteger} has more than
     * one digit a decimal point is inserted after the first digit.
     * An exponent in character form is then suffixed to the converted
     * unscaled value (perhaps with inserted decimal point); this
     * comprises the letter {@code 'E'} followed immediately by the
     * adjusted exponent converted to a character form.  The latter is
     * in base ten, using the characters {@code '0'} through
     * {@code '9'} with no leading zeros, and is always prefixed by a
     * sign character {@code '-'} (<code>'&#92;u002D'</code>) if the
     * adjusted exponent is negative, {@code '+'}
     * (<code>'&#92;u002B'</code>) otherwise).
     *
     * <p>Finally, the entire string is prefixed by a minus sign
     * character {@code '-'} (<code>'&#92;u002D'</code>) if the unscaled
     * value is less than zero.  No sign character is prefixed if the
     * unscaled value is zero or positive.
     *
     * <p><b>Examples:</b>
     * <p>For each representation [<i>unscaled value</i>, <i>scale</i>]
     * on the left, the resulting string is shown on the right.
     * <pre>
     * [123,0]      "123"
     * [-123,0]     "-123"
     * [123,-1]     "1.23E+3"
     * [123,-3]     "1.23E+5"
     * [123,1]      "12.3"
     * [123,5]      "0.00123"
     * [123,10]     "1.23E-8"
     * [-123,12]    "-1.23E-10"
     * </pre>
     *
     * <b>Notes:</b>
     * <ol>
     *
     * <li>There is a one-to-one mapping between the distinguishable
     * {@code BigDecimal} values and the result of this conversion.
     * That is, every distinguishable {@code BigDecimal} value
     * (unscaled value and scale) has a unique string representation
     * as a result of using {@code toString}.  If that string
     * representation is converted back to a {@code BigDecimal} using
     * the {@link #BigDecimal(String)} constructor, then the original
     * value will be recovered.
     *
     * <li>The string produced for a given number is always the same;
     * it is not affected by locale.  This means that it can be used
     * as a canonical string representation for exchanging decimal
     * data, or as a key for a Hashtable, etc.  Locale-sensitive
     * number formatting and parsing is handled by the {@link
     * java.text.NumberFormat} class and its subclasses.
     *
     * <li>The {@link #toEngineeringString} method may be used for
     * presenting numbers with exponents in engineering notation, and the
     * {@link #setScale(int,RoundingMode) setScale} method may be used for
     * rounding a {@code BigDecimal} so it has a known number of digits after
     * the decimal point.
     *
     * <li>The digit-to-character mapping provided by
     * {@code Character.forDigit} is used.
     *
     * </ol>
     *
     * @return string representation of this {@code BigDecimal}.
     * @see    Character#forDigit
     * @see    #BigDecimal(java.lang.String)
     */
    @Override
    public String toString() {
        String sc = stringCache;
        if (sc == null) {
            stringCache = sc = layoutChars(true);
        }
        return sc;
    }

    /**
     * Returns a string representation of this {@code BigDecimal},
     * using engineering notation if an exponent is needed.
     *
     * <p>Returns a string that represents the {@code BigDecimal} as
     * described in the {@link #toString()} method, except that if
     * exponential notation is used, the power of ten is adjusted to
     * be a multiple of three (engineering notation) such that the
     * integer part of nonzero values will be in the range 1 through
     * 999.  If exponential notation is used for zero values, a
     * decimal point and one or two fractional zero digits are used so
     * that the scale of the zero value is preserved.  Note that
     * unlike the output of {@link #toString()}, the output of this
     * method is <em>not</em> guaranteed to recover the same [integer,
     * scale] pair of this {@code BigDecimal} if the output string is
     * converting back to a {@code BigDecimal} using the {@linkplain
     * #BigDecimal(String) string constructor}.  The result of this method meets
     * the weaker constraint of always producing a numerically equal
     * result from applying the string constructor to the method's output.
     *
     * @return string representation of this {@code BigDecimal}, using
     *         engineering notation if an exponent is needed.
     * @since  1.5
     */
    public String toEngineeringString() {
        return layoutChars(false);
    }

    /**
     * Returns a string representation of this {@code BigDecimal}
     * without an exponent field.  For values with a positive scale,
     * the number of digits to the right of the decimal point is used
     * to indicate scale.  For values with a zero or negative scale,
     * the resulting string is generated as if the value were
     * converted to a numerically equal value with zero scale and as
     * if all the trailing zeros of the zero scale value were present
     * in the result.
     *
     * The entire string is prefixed by a minus sign character '-'
     * (<code>'&#92;u002D'</code>) if the unscaled value is less than
     * zero. No sign character is prefixed if the unscaled value is
     * zero or positive.
     *
     * Note that if the result of this method is passed to the
     * {@linkplain #BigDecimal(String) string constructor}, only the
     * numerical value of this {@code BigDecimal} will necessarily be
     * recovered; the representation of the new {@code BigDecimal}
     * may have a different scale.  In particular, if this
     * {@code BigDecimal} has a negative scale, the string resulting
     * from this method will have a scale of zero when processed by
     * the string constructor.
     *
     * (This method behaves analogously to the {@code toString}
     * method in 1.4 and earlier releases.)
     *
     * @return a string representation of this {@code BigDecimal}
     * without an exponent field.
     * @since 1.5
     * @see #toString()
     * @see #toEngineeringString()
     */
    public String toPlainString() {
        if(scale==0) {
            if(intCompact!=INFLATED) {
                return Long.toString(intCompact);
            } else {
                return intVal.toString();
            }
        }
        if(this.scale<0) { // No decimal point
            if(signum()==0) {
                return "0";
            }
            int trailingZeros = checkScaleNonZero((-(long)scale));
            String str = intCompact != INFLATED
                ? Long.toString(intCompact)
                : intVal.toString();
            int len = str.length() + trailingZeros;
            if (len < 0) {
                throw new OutOfMemoryError("too large to fit in a String");
            }
            StringBuilder buf = new StringBuilder(len);
            buf.append(str);
            buf.repeat('0', trailingZeros);
            return buf.toString();
        }
        String str;
        if(intCompact!=INFLATED) {
            str = Long.toString(Math.abs(intCompact));
        } else {
            str = intVal.abs().toString();
        }
        return getValueString(signum(), str, scale);
    }

    /* Returns a digit.digit string */
    private static String getValueString(int signum, String intString, int scale) {
        /* Insert decimal point */
        StringBuilder buf;
        int insertionPoint = intString.length() - scale;
        if (insertionPoint == 0) {  /* Point goes just before intVal */
            return (signum<0 ? "-0." : "0.") + intString;
        } else if (insertionPoint > 0) { /* Point goes inside intVal */
            buf = new StringBuilder(intString);
            buf.insert(insertionPoint, '.');
            if (signum < 0)
                buf.insert(0, '-');
        } else { /* We must insert zeros between point and intVal */
            int len = (signum < 0 ? 3 : 2) + scale;
            if (len < 0) {
                throw new OutOfMemoryError("too large to fit in a String");
            }
            buf = new StringBuilder(len);
            buf.append(signum<0 ? "-0." : "0.");
            buf.repeat('0', -insertionPoint);  // insertionPoint != MIN_VALUE
            buf.append(intString);
        }
        return buf.toString();
    }

    /**
     * @return {@code true} if and only if {@code this == this.toBigInteger()}
     */
    boolean isInteger() {
        if (scale <= 0 || signum() == 0)
            return true;

        // Get an upper bound of precision() without using big powers of 10 (see bigDigitLength())
        int digitLen = precision != 0 ? precision
            : (intCompact != INFLATED ? precision() : (digitLengthLower(unscaledValue()) + 1));
        return digitLen > scale && stripZerosToMatchScale(intVal, intCompact, scale, 0L).scale == 0;
    }

    /**
     * Converts this {@code BigDecimal} to a {@code BigInteger}.
     * This conversion is analogous to the
     * <i>narrowing primitive conversion</i> from {@code double} to
     * {@code long} as defined in
     * <cite>The Java Language Specification</cite>:
     * any fractional part of this
     * {@code BigDecimal} will be discarded.  Note that this
     * conversion can lose information about the precision of the
     * {@code BigDecimal} value.
     * <p>
     * To have an exception thrown if the conversion is inexact (in
     * other words if a nonzero fractional part is discarded), use the
     * {@link #toBigIntegerExact()} method.
     *
     * @return this {@code BigDecimal} converted to a {@code BigInteger}.
     * @jls 5.1.3 Narrowing Primitive Conversion
     */
    public BigInteger toBigInteger() {
        // force to an integer, quietly
        return this.setScale(0, ROUND_DOWN).inflated();
    }

    /**
     * Converts this {@code BigDecimal} to a {@code BigInteger},
     * checking for lost information.  An exception is thrown if this
     * {@code BigDecimal} has a nonzero fractional part.
     *
     * @return this {@code BigDecimal} converted to a {@code BigInteger}.
     * @throws ArithmeticException if {@code this} has a nonzero
     *         fractional part.
     * @since  1.5
     */
    public BigInteger toBigIntegerExact() {
        // round to an integer, with Exception if decimal part non-0
        return this.setScale(0, ROUND_UNNECESSARY).inflated();
    }

    /**
     * Converts this {@code BigDecimal} to a {@code long}.
     * This conversion is analogous to the
     * <i>narrowing primitive conversion</i> from {@code double} to
     * {@code short} as defined in
     * <cite>The Java Language Specification</cite>:
     * any fractional part of this
     * {@code BigDecimal} will be discarded, and if the resulting
     * "{@code BigInteger}" is too big to fit in a
     * {@code long}, only the low-order 64 bits are returned.
     * Note that this conversion can lose information about the
     * overall magnitude and precision of this {@code BigDecimal} value as well
     * as return a result with the opposite sign.
     *
     * @return this {@code BigDecimal} converted to a {@code long}.
     * @jls 5.1.3 Narrowing Primitive Conversion
     */
    @Override
    public long longValue(){
        if (intCompact != INFLATED && scale == 0) {
            return intCompact;
        } else {
            // Fastpath zero and small values
            if (this.signum() == 0 || fractionOnly() ||
                // Fastpath very large-scale values that will result
                // in a truncated value of zero. If the scale is -64
                // or less, there are at least 64 powers of 10 in the
                // value of the numerical result. Since 10 = 2*5, in
                // that case there would also be 64 powers of 2 in the
                // result, meaning all 64 bits of a long will be zero.
                scale <= -64) {
                return 0;
            } else {
                return toBigInteger().longValue();
            }
        }
    }

    /**
     * Return true if a nonzero BigDecimal has an absolute value less
     * than one; i.e. only has fraction digits.
     */
    private boolean fractionOnly() {
        assert this.signum() != 0;
        return this.precision() <= this.scale;
    }

    /**
     * Converts this {@code BigDecimal} to a {@code long}, checking
     * for lost information.  If this {@code BigDecimal} has a
     * nonzero fractional part or is out of the possible range for a
     * {@code long} result then an {@code ArithmeticException} is
     * thrown.
     *
     * @return this {@code BigDecimal} converted to a {@code long}.
     * @throws ArithmeticException if {@code this} has a nonzero
     *         fractional part, or will not fit in a {@code long}.
     * @since  1.5
     */
    public long longValueExact() {
        if (intCompact != INFLATED && scale == 0)
            return intCompact;

        // Fastpath zero
        if (this.signum() == 0)
            return 0;

        // Fastpath numbers less than 1.0 (the latter can be very slow
        // to round if very small)
        if (fractionOnly())
            throw new ArithmeticException("Rounding necessary");

        /*
         * If more than 19 digits in integer part it cannot possibly fit.
         * Ensure that arithmetic does not overflow, so instead of
         *      precision() - scale > 19
         * prefer
         *      precision() - 19 > scale
         * since precision() > 0, so the lhs cannot overflow.
         */
        if (precision() - 19 > scale) // [OK for negative scale too]
            throw new java.lang.ArithmeticException("Overflow");

        // round to an integer, with Exception if decimal part non-0
        BigDecimal num = this.setScale(0, ROUND_UNNECESSARY);
        if (num.precision() >= 19) // need to check carefully
            LongOverflow.check(num);
        return num.inflated().longValue();
    }

    private static class LongOverflow {
        /** BigInteger equal to Long.MIN_VALUE. */
        private static final BigInteger LONGMIN = BigInteger.valueOf(Long.MIN_VALUE);

        /** BigInteger equal to Long.MAX_VALUE. */
        private static final BigInteger LONGMAX = BigInteger.valueOf(Long.MAX_VALUE);

        public static void check(BigDecimal num) {
            BigInteger intVal = num.inflated();
            if (intVal.compareTo(LONGMIN) < 0 ||
                intVal.compareTo(LONGMAX) > 0)
                throw new java.lang.ArithmeticException("Overflow");
        }
    }

    /**
     * Converts this {@code BigDecimal} to an {@code int}.
     * This conversion is analogous to the
     * <i>narrowing primitive conversion</i> from {@code double} to
     * {@code short} as defined in
     * <cite>The Java Language Specification</cite>:
     * any fractional part of this
     * {@code BigDecimal} will be discarded, and if the resulting
     * "{@code BigInteger}" is too big to fit in an
     * {@code int}, only the low-order 32 bits are returned.
     * Note that this conversion can lose information about the
     * overall magnitude and precision of this {@code BigDecimal}
     * value as well as return a result with the opposite sign.
     *
     * @return this {@code BigDecimal} converted to an {@code int}.
     * @jls 5.1.3 Narrowing Primitive Conversion
     */
    @Override
    public int intValue() {
        return  (intCompact != INFLATED && scale == 0) ?
            (int)intCompact :
            (int)longValue();
    }

    /**
     * Converts this {@code BigDecimal} to an {@code int}, checking
     * for lost information.  If this {@code BigDecimal} has a
     * nonzero fractional part or is out of the possible range for an
     * {@code int} result then an {@code ArithmeticException} is
     * thrown.
     *
     * @return this {@code BigDecimal} converted to an {@code int}.
     * @throws ArithmeticException if {@code this} has a nonzero
     *         fractional part, or will not fit in an {@code int}.
     * @since  1.5
     */
    public int intValueExact() {
       long num;
       num = this.longValueExact();     // will check decimal part
       if ((int)num != num)
           throw new java.lang.ArithmeticException("Overflow");
       return (int)num;
    }

    /**
     * Converts this {@code BigDecimal} to a {@code short}, checking
     * for lost information.  If this {@code BigDecimal} has a
     * nonzero fractional part or is out of the possible range for a
     * {@code short} result then an {@code ArithmeticException} is
     * thrown.
     *
     * @return this {@code BigDecimal} converted to a {@code short}.
     * @throws ArithmeticException if {@code this} has a nonzero
     *         fractional part, or will not fit in a {@code short}.
     * @since  1.5
     */
    public short shortValueExact() {
       long num;
       num = this.longValueExact();     // will check decimal part
       if ((short)num != num)
           throw new java.lang.ArithmeticException("Overflow");
       return (short)num;
    }

    /**
     * Converts this {@code BigDecimal} to a {@code byte}, checking
     * for lost information.  If this {@code BigDecimal} has a
     * nonzero fractional part or is out of the possible range for a
     * {@code byte} result then an {@code ArithmeticException} is
     * thrown.
     *
     * @return this {@code BigDecimal} converted to a {@code byte}.
     * @throws ArithmeticException if {@code this} has a nonzero
     *         fractional part, or will not fit in a {@code byte}.
     * @since  1.5
     */
    public byte byteValueExact() {
       long num;
       num = this.longValueExact();     // will check decimal part
       if ((byte)num != num)
           throw new java.lang.ArithmeticException("Overflow");
       return (byte)num;
    }

    /**
     * Converts this {@code BigDecimal} to a {@code float}.
     * This conversion is similar to the
     * <i>narrowing primitive conversion</i> from {@code double} to
     * {@code float} as defined in
     * <cite>The Java Language Specification</cite>:
     * if this {@code BigDecimal} has too great a
     * magnitude to represent as a {@code float}, it will be
     * converted to {@link Float#NEGATIVE_INFINITY} or {@link
     * Float#POSITIVE_INFINITY} as appropriate.  Note that even when
     * the return value is finite, this conversion can lose
     * information about the precision of the {@code BigDecimal}
     * value.
     *
     * @return this {@code BigDecimal} converted to a {@code float}.
     * @jls 5.1.3 Narrowing Primitive Conversion
     */
    @Override
    public float floatValue() {
        /* For details, see the extensive comments in doubleValue(). */
        if (intCompact != INFLATED) {
            float v = intCompact;
            if (scale == 0) {
                return v;
            }
            /*
             * The discussion for the double case also applies here. That is,
             * the following test is precise for all long values except for
             * Long.MAX_VALUE but the result is correct nevertheless.
             */
            if ((long) v == intCompact) {
                if (0 < scale && scale < FLOAT_10_POW.length) {
                    return v / FLOAT_10_POW[scale];
                }
                if (0 > scale && scale > -FLOAT_10_POW.length) {
                    return v * FLOAT_10_POW[-scale];
                }
            }
        }
        return fullFloatValue();
    }

    private float fullFloatValue() {
        if (intCompact == 0) {
            return 0.0f;
        }
        BigInteger w = unscaledValue().abs();
        long qb = w.bitLength() - (long) Math.ceil(scale * L);
        if (qb < Q_MIN_F - 2) {  // qb < -151
            return signum() * 0.0f;
        }
        if (qb > Q_MAX_F + P_F + 1) {  // qb > 129
            return signum() * Float.POSITIVE_INFINITY;
        }
        if (scale < 0) {
            return signum() * w.multiply(bigTenToThe(-scale)).floatValue();
        }
        if (scale == 0) {
            return signum() * w.floatValue();
        }
        int ql = (int) qb - (P_F + 3);
        BigInteger pow10 = bigTenToThe(scale);
        BigInteger m, n;
        if (ql <= 0) {
            m = w.shiftLeft(-ql);
            n = pow10;
        } else {
            m = w;
            n = pow10.shiftLeft(ql);
        }
        BigInteger[] qr = m.divideAndRemainder(n);
        int i = qr[0].intValue();
        int sb = qr[1].signum();
        int dq = (Integer.SIZE - (P_F + 2)) - Integer.numberOfLeadingZeros(i);
        int eq = (Q_MIN_F - 2) - ql;
        if (dq >= eq) {
            return signum() * Math.scalb((float) (i | sb), ql);
        }
        int mask = (1 << eq) - 1;
        int j = i >> eq | (Integer.signum(i & mask)) | sb;
        return signum() * Math.scalb((float) j, Q_MIN_F - 2);
    }

    /**
     * Converts this {@code BigDecimal} to a {@code double}.
     * This conversion is similar to the
     * <i>narrowing primitive conversion</i> from {@code double} to
     * {@code float} as defined in
     * <cite>The Java Language Specification</cite>:
     * if this {@code BigDecimal} has too great a
     * magnitude represent as a {@code double}, it will be
     * converted to {@link Double#NEGATIVE_INFINITY} or {@link
     * Double#POSITIVE_INFINITY} as appropriate.  Note that even when
     * the return value is finite, this conversion can lose
     * information about the precision of the {@code BigDecimal}
     * value.
     *
     * @return this {@code BigDecimal} converted to a {@code double}.
     * @jls 5.1.3 Narrowing Primitive Conversion
     */
    @Override
    public double doubleValue() {
        /*
         * Attempt a fast path when the significand is compact and the
         * scale is small enough.
         */
        if (intCompact != INFLATED) {
            double v = intCompact;
            if (scale == 0) {
                /* v is the result of a single rounding. */
                return v;
            }
            /*
             * The test (long) (double) l == l to check whether l is an exact
             * double is always accurate, except for l = Long.MAX_VALUE.
             * This special case is not an issue, though, as explained below.
             */
            if ((long) v == intCompact) {
                /*
                 * If intCompact != Long.MAX_VALUE, v is exactly equal to it
                 * and 10^|scale| is an exact double when 0 < |scale| <= 22.
                 * Hence, the multiplication or division below are on exact
                 * doubles, so the result is subject to a single rounding.
                 *
                 * If intCompact = Long.MAX_VALUE, v is not exactly equal to it.
                 * Luckily, when 0 < |scale| <= 22, full precision computations
                 * show that the end result as computed here is correct anyway,
                 * despite being the outcome of 2 roundings.
                 */
                if (0 < scale && scale < DOUBLE_10_POW.length) {
                    return v / DOUBLE_10_POW[scale];
                }
                if (0 > scale && scale > -DOUBLE_10_POW.length) {
                    return v * DOUBLE_10_POW[-scale];
                }
            }
        }
        return fullDoubleValue();
    }

    private double fullDoubleValue() {
        /*
         * This method works on all instances but might throw or consume a lot
         * of memory and cpu on huge scales or huge significands.
         *
         * It is expected that this computations might exhaust memory or consume
         * an unreasonable amount of cpu when both the significand and the scale
         * are huge and conjure to meet MIN < |this| < MAX, where MIN and MAX
         * are approximately Double.MIN_VALUE and Double.MAX_VALUE, resp.
         */
        if (intCompact == 0) {
            return 0.0;
        }

        /*
         * Let
         *      w = |unscaledValue()|
         *      s = scale
         *      bl = w.bitLength()
         *      P = Double.PRECISION  // 53
         *      Q_MIN = Double.MIN_EXPONENT - (P - 1)  // -1_074
         *      Q_MAX = Double.MAX_EXPONENT - (P - 1)  // 971
         * Thus
         *      |this| = w 10^{-s}
         *      Double.MIN_VALUE = 2^Q_MIN
         *      Double.MAX_VALUE = (2^P - 1) 2^Q_MAX
         * Here w > 0, so 2^{bl-1} <= w < 2^bl, hence
         *      bl = floor(log_2(w)) + 1
         *
         * To determine the return value, it helps to define real beta
         * and integer q meeting
         *      w 10^{-s} = beta 2^q such that 2^{P+1} <= beta < 2^{P+2}
         * Note that floor(log_2(beta)) = P + 1.
         * The reason for having beta meet these inequalities rather than the
         * more "natural" 2^{P-1} <= beta < 2^P will become clearer below.
         * (They ensure that there's room for a "round" and a "sticky" bit.)
         *
         * Determining beta and q, however, requires costly computations.
         * Instead, try to quickly determine integer bounds ql, qh such that
         * ql <= q <= qh and with qh - ql as small as reasonably possible.
         * They help to quickly filter out most values that do not round
         * to a finite, non-zero double.
         *
         * To this end, let l = log_2(10). Then
         *      log_2(w) - s l = log_2(w 10^{-s}) = log_2(beta) + q
         * Mathematically, for any real x, y:
         *      floor(x) + floor(y) <= floor(x + y) <= floor(x) + floor(y) + 1
         *      floor(-x) = -ceil(x)
         * Therefore, remembering that
         *      floor(log_2(w)) = bl - 1 and floor(log_2(beta)) = P + 1
         * the above leads to
         *      bl - ceil(s l) - P - 2 <= q <= bl - ceil(s l) - P - 1
         *
         * However, ceil(s l) is still a purely mathematical quantity.
         * To determine computable bounds for it, let L = roundTiesToEven(l)
         * and let u = 2^{-P} (see the comment about constant L).
         * Let * denote multiplication on doubles, which is subject to errors.
         * Then, since all involved values are not subnormals, it follows that
         * (see any textbook on numerical algorithms):
         *      s * L = s l (1 + delta_1) (1 + delta_2) = s l (1 + theta)
         * where |delta_i| <= u, |theta| <= 2u / (1 - 2u) < 4u = 2^{2-P}
         * The delta_i account for the relative error of l and of *.
         * Note that s (the int scale) converts exactly as double.
         * Hence, as 3 < l < 4
         *      |s * L - s l| = |s| l |theta| < 2^31 4 2^{2-P} = 2^{-18} < 1
         * For reals x, y, |x - y| <= 1 entails |ceil(x) - ceil(y)| <= 1. Thus,
         *      ceil(s * L) - 1 <= ceil(s l) <= ceil(s * L) + 1
         *
         * Using these inequalities implies
         *      bl - ceil(s * L) - P - 3 <= q <= bl - ceil(s * L) - P
         * finally leading to the definitions
         *      qb = bl - ceil(s * L), ql = qb - P - 3, qh = qb - P
         * meeting
         *      ql <= q <= qh and qh - ql = 3, which is small enough.
         * Note that qb doesn't always fit in an int.
         *
         * To filter out most values that round to 0 or infinity, define
         *      ZCO = 1/2 2^Q_MIN = 2^{Q_MIN-1}    (zero cutoff)
         *      ICO = (2^P - 1/2) 2^Q_MAX    (infinity cutoff)
         * Return [+/-]0 iff |this| <= ZCO, [+/-]infinity iff |this| >= ICO.
         *
         * To play safely, whenever 2^{P+2} 2^qh <= ZCO then
         *      |this| = beta 2^q < 2^{P+2} 2^qh <= ZCO
         * Now, 2^{P+2} 2^qh <= ZCO means the same as P + 2 + qh < Q_MIN,
         * leading to
         *      if qb < Q_MIN - 2 then return [+/-]0
         *
         * Similarly, whenever 2^{P+1} 2^ql >= 2^P 2^Q_MAX then
         *      |this| = beta 2^q >= 2^{P+1} 2^ql >= 2^P 2^Q_MAX > ICO
         * Here, 2^{P+1} 2^ql >= 2^P 2^Q_MAX is equivalent to ql + 2 > Q_MAX,
         * which entails
         *      if qb > Q_MAX + P + 1 then return [+/-]infinity
         *
         * Observe that |s * L| <= 2^31 4 = 2^33, so
         *      (long) ceil(s * L) = ceil(s * L)
         * since all integers <= 2^P are exact doubles.
         */
        BigInteger w = unscaledValue().abs();
        long qb = w.bitLength() - (long) Math.ceil(scale * L);
        if (qb < Q_MIN_D - 2) {  // qb < -1_076
            return signum() * 0.0;
        }
        if (qb > Q_MAX_D + P_D + 1) {  // qb > 1_025
            /* If s <= -309 then qb >= 1_027, so these cases all end up here. */
            return signum() * Double.POSITIVE_INFINITY;
        }

        /*
         * There's still a small chance to return [+/-]0 or [+/-]infinity.
         * But rather than chasing for specific cases, do the full computations.
         * Here, Q_MIN - 2 <= qb <= Q_MAX + P + 1
         */
        if (scale < 0) {
            /*
             * Here -309 < s < 0, so w 10^{-s} is an integer: delegate to
             * BigInteger.doubleValue() without further ado.
             * Also, |this| < 10^309, so the integers involved are manageable.
             */
            return signum() * w.multiply(bigTenToThe(-scale)).doubleValue();
        }
        if (scale == 0) {
            return signum() * w.doubleValue();
        }

        /*
         * This last case has s > 0 and sometimes unmanageable large integers.
         * It is expected that these computations might exhaust memory or
         * consume an unreasonable amount of cpu when both w and s are huge.
         *
         * Assume a number eta >= 2^{P+1} and split it into i = floor(eta)
         * and f = eta - i. Thus i >= 2^{P+1} and 0 <= f < 1.
         * Define sb = 0 iff f = 0 and sb = 1 iff f > 0.
         * Let j = i | sb (| denotes bitwise "or").
         * j has at least P + 2 bits to accommodate P most significand bits
         * (msb), 1 rounding bit rb just to the right of them and 1 "sticky" bit
         * sb as its least significant bit, as depicted here:
         * eta = | P msb | rb | ... | lsb | bits of fraction f...
         * i   = | P msb | rb | ... | lsb |
         * j   = | P msb | rb | ... | sb  |
         * All the bits in eta, i and j to the left of lsb or sb are identical.
         * It's not hard to see that
         *      roundTiesToEven(eta) = roundTiesToEven(j)
         *
         * To apply the above, define
         *      eta = (w/10^s) 2^{-ql}
         * which meets
         *      eta = (w/10^s) 2^{-q} 2^{q-ql} = beta 2^{q-ql} = beta 2^dq
         * where dq = q - ql. Therefore, since ql <= q <= qh = ql + 3
         *      2^{P+1} <= eta < 2^{P+2}    iff q = ql
         *      2^{P+2} <= eta < 2^{P+3}    iff q = ql + 1
         *      2^{P+3} <= eta < 2^{P+4}    iff q = ql + 2
         *      2^{P+4} <= eta < 2^{P+5}    iff q = ql + 3
         * There are no other cases. The same holds for i = floor(eta),
         * which therefore fits in a long, as P + 5 < Long.SIZE:
         *      2^{P+1} <= i < 2^{P+2}      iff q = ql
         *      2^{P+2} <= i < 2^{P+3}      iff q = ql + 1
         *      2^{P+3} <= i < 2^{P+4}      iff q = ql + 2
         *      2^{P+4} <= i < 2^{P+5}      iff q = ql + 3
         * This shows dq = bitLength(i) - (P + 2).
         *
         * Let integer m = w 2^{-ql} if ql <= 0, or m = w if ql > 0 and
         * let integer n = 10^s if ql <= 0, or n = 10^s 2^ql if ql > 0.
         * It follows that eta = m/n, i = m // n, (// is integer division)
         * and f = (m \\ n) / n (\\ is binary "mod" (remainder)).
         * Of course, f > 0 iff m \\ n > 0, hence sb = signum(m \\ n).
         *
         * If q >= Q_MIN - 2 then |this| is in the normal range or overflows.
         * With eq = Q_MIN - 2 - ql the condition is the same as dq >= eq.
         * Provided |this| = eta 2^ql does not overflow, it follows that
         *      roundTiesToEven(|this|) = roundTiesToEven(eta) 2^ql
         *          = roundTiesToEven(j) 2^ql = scalb((double) j, ql)
         * If |this| overflows, however, so does scalb((double) j, ql). Thus,
         * in either case
         *      roundTiesToEven(|this|) = scalb((double) j, ql)
         *
         * When q < Q_MIN - 2, that is, when dq < eq, |this| is in the
         * subnormal range. The integer j needs to be shortened to ensure that
         * the precision is gradually shortened for the final significand.
         *      |this| = eta 2^ql = (eta/2^eq) 2^{Q_MIN-2}
         * Compare eta and i as depicted here
         * eta = | msb | eq lsb | bits of fraction f...
         * i   = | msb | eq lsb |
         * where there are eq least significant bits in the right section.
         * To obtain j in this case, shift i to the right by eq positions and
         * thereafter "or" its least significant bit with signum(eq lsb) and
         * with sb as defined above. This leads to
         *      roundTiesToEven(|this|) = scalb((double) j, Q_MIN - 2)
         */
        int ql = (int) qb - (P_D + 3);  // narrowing qb to an int is safe
        BigInteger pow10 = bigTenToThe(scale);
        BigInteger m, n;
        if (ql <= 0) {
            m = w.shiftLeft(-ql);
            n = pow10;
        } else {
            m = w;
            n = pow10.shiftLeft(ql);
        }

        BigInteger[] qr = m.divideAndRemainder(n);
        long i = qr[0].longValue();
        int sb = qr[1].signum();
        int dq = (Long.SIZE - (P_D + 2)) - Long.numberOfLeadingZeros(i);
        int eq = (Q_MIN_D - 2) - ql;
        if (dq >= eq) {
            return signum() * Math.scalb((double) (i | sb), ql);
        }

        /* Subnormal */
        long mask = (1L << eq) - 1;
        long j = i >> eq | Long.signum(i & mask) | sb;
        return signum() * Math.scalb((double) j, Q_MIN_D - 2);
    }

    /**
     * Powers of 10 which can be represented exactly in {@code
     * double}.
     */
    @Stable
    private static final double[] DOUBLE_10_POW = {
        1.0e0,  1.0e1,  1.0e2,  1.0e3,  1.0e4,  1.0e5,
        1.0e6,  1.0e7,  1.0e8,  1.0e9,  1.0e10, 1.0e11,
        1.0e12, 1.0e13, 1.0e14, 1.0e15, 1.0e16, 1.0e17,
        1.0e18, 1.0e19, 1.0e20, 1.0e21, 1.0e22
    };

    /**
     * Powers of 10 which can be represented exactly in {@code
     * float}.
     */
    @Stable
    private static final float[] FLOAT_10_POW = {
        1.0e0f, 1.0e1f, 1.0e2f, 1.0e3f, 1.0e4f, 1.0e5f,
        1.0e6f, 1.0e7f, 1.0e8f, 1.0e9f, 1.0e10f
    };

    /**
     * Returns the size of an ulp, a unit in the last place, of this
     * {@code BigDecimal}.  An ulp of a nonzero {@code BigDecimal}
     * value is the positive distance between this value and the
     * {@code BigDecimal} value next larger in magnitude with the
     * same number of digits.  An ulp of a zero value is numerically
     * equal to 1 with the scale of {@code this}.  The result is
     * stored with the same scale as {@code this} so the result
     * for zero and nonzero values is equal to {@code [1,
     * this.scale()]}.
     *
     * @return the size of an ulp of {@code this}
     * @since 1.5
     */
    public BigDecimal ulp() {
        return BigDecimal.valueOf(1, this.scale(), 1);
    }

    /**
     * Lay out this {@code BigDecimal} into a {@code char[]} array.
     * The Java 1.2 equivalent to this was called {@code getValueString}.
     *
     * @param  sci {@code true} for Scientific exponential notation;
     *          {@code false} for Engineering
     * @return string with canonical string representation of this
     *         {@code BigDecimal}
     */
    private String layoutChars(boolean sci) {
        long intCompact = this.intCompact;
        int scale = this.scale;
        if (scale == 0)                      // zero scale is trivial
            return (intCompact != INFLATED) ?
                Long.toString(intCompact):
                intVal.toString();
        if (scale == 2  &&
            intCompact >= 0 && intCompact < Integer.MAX_VALUE) {
            // currency fast path
            int lowInt = (int)intCompact % 100;
            int highInt = (int)intCompact / 100;
            int highIntSize = DecimalDigits.stringSize(highInt);
            byte[] buf = new byte[highIntSize + 3];
            DecimalDigits.uncheckedGetCharsLatin1(highInt, highIntSize, buf);
            buf[highIntSize] = '.';
            DecimalDigits.uncheckedPutPairLatin1(buf, highIntSize + 1, lowInt);
            try {
                return JLA.uncheckedNewStringNoRepl(buf, StandardCharsets.ISO_8859_1);
            } catch (CharacterCodingException cce) {
                throw new AssertionError(cce);
            }
        }

        char[] coeff;
        int offset;  // offset is the starting index for coeff array
        // Get the significand as an absolute value
        if (intCompact != INFLATED) {
            // All non negative longs can be made to fit into 19 character array.
            coeff = new char[19];
            offset = DecimalDigits.getChars(Math.abs(intCompact), coeff.length, coeff);
        } else {
            offset = 0;
            coeff  = intVal.abs().toString().toCharArray();
        }

        // Construct a buffer, with sufficient capacity for all cases.
        // If E-notation is needed, length will be: +1 if negative, +1
        // if '.' needed, +2 for "E+", + up to 10 for adjusted exponent.
        // Otherwise it could have +1 if negative, plus leading "0.00000"
        StringBuilder buf = new StringBuilder(32);;
        if (signum() < 0)             // prefix '-' if negative
            buf.append('-');
        int coeffLen = coeff.length - offset;
        long adjusted = -(long)scale + (coeffLen -1);
        if ((scale >= 0) && (adjusted >= -6)) { // plain number
            int pad = scale - coeffLen;         // count of padding zeros
            if (pad >= 0) {                     // 0.xxx form
                buf.append('0');
                buf.append('.');
                for (; pad>0; pad--) {
                    buf.append('0');
                }
                buf.append(coeff, offset, coeffLen);
            } else {                         // xx.xx form
                buf.append(coeff, offset, -pad);
                buf.append('.');
                buf.append(coeff, -pad + offset, scale);
            }
        } else { // E-notation is needed
            if (sci) {                       // Scientific notation
                buf.append(coeff[offset]);   // first character
                if (coeffLen > 1) {          // more to come
                    buf.append('.');
                    buf.append(coeff, offset + 1, coeffLen - 1);
                }
            } else {                         // Engineering notation
                int sig = (int)(adjusted % 3);
                if (sig < 0)
                    sig += 3;                // [adjusted was negative]
                adjusted -= sig;             // now a multiple of 3
                sig++;
                if (signum() == 0) {
                    switch (sig) {
                    case 1:
                        buf.append('0'); // exponent is a multiple of three
                        break;
                    case 2:
                        buf.append("0.00");
                        adjusted += 3;
                        break;
                    case 3:
                        buf.append("0.0");
                        adjusted += 3;
                        break;
                    default:
                        throw new AssertionError("Unexpected sig value " + sig);
                    }
                } else if (sig >= coeffLen) {   // significand all in integer
                    buf.append(coeff, offset, coeffLen);
                    // may need some zeros, too
                    for (int i = sig - coeffLen; i > 0; i--) {
                        buf.append('0');
                    }
                } else {                     // xx.xxE form
                    buf.append(coeff, offset, sig);
                    buf.append('.');
                    buf.append(coeff, offset + sig, coeffLen - sig);
                }
            }
            if (adjusted != 0) {             // [!sci could have made 0]
                buf.append('E');
                if (adjusted > 0)            // force sign for positive
                    buf.append('+');
                buf.append(adjusted);
            }
        }
        return buf.toString();
    }

    /**
     * Return 10 to the power n, as a {@code BigInteger}.
     *
     * @param  n the power of ten to be returned (>=0)
     * @return a {@code BigInteger} with the value (10<sup>n</sup>)
     */
    private static BigInteger bigTenToThe(int n) {
        if (n < 0)
            return BigInteger.ZERO;

        if (n < BIG_TEN_POWERS_TABLE_MAX) {
            BigInteger[] pows = BIG_TEN_POWERS_TABLE;
            if (n < pows.length)
                return pows[n];
            else
                return expandBigIntegerTenPowers(n);
        }

        return BigInteger.TEN.pow(n);
    }

    /**
     * Expand the BIG_TEN_POWERS_TABLE array to contain at least 10**n.
     *
     * @param n the power of ten to be returned (>=0)
     * @return a {@code BigDecimal} with the value (10<sup>n</sup>) and
     *         in the meantime, the BIG_TEN_POWERS_TABLE array gets
     *         expanded to the size greater than n.
     */
    private static BigInteger expandBigIntegerTenPowers(int n) {
        synchronized(BigDecimal.class) {
            BigInteger[] pows = BIG_TEN_POWERS_TABLE;
            int curLen = pows.length;
            // The following comparison and the above synchronized statement is
            // to prevent multiple threads from expanding the same array.
            if (curLen <= n) {
                int newLen = curLen << 1;
                while (newLen <= n) {
                    newLen <<= 1;
                }
                pows = Arrays.copyOf(pows, newLen);
                for (int i = curLen; i < newLen; i++) {
                    pows[i] = pows[i - 1].multiply(BigInteger.TEN);
                }
                // Based on the following facts:
                // 1. pows is a private local variable;
                // 2. the following store is a volatile store.
                // the newly created array elements can be safely published.
                BIG_TEN_POWERS_TABLE = pows;
            }
            return pows[n];
        }
    }

    @Stable
    private static final long[] LONG_TEN_POWERS_TABLE = {
        1,                     // 0 / 10^0
        10,                    // 1 / 10^1
        100,                   // 2 / 10^2
        1000,                  // 3 / 10^3
        10000,                 // 4 / 10^4
        100000,                // 5 / 10^5
        1000000,               // 6 / 10^6
        10000000,              // 7 / 10^7
        100000000,             // 8 / 10^8
        1000000000,            // 9 / 10^9
        10000000000L,          // 10 / 10^10
        100000000000L,         // 11 / 10^11
        1000000000000L,        // 12 / 10^12
        10000000000000L,       // 13 / 10^13
        100000000000000L,      // 14 / 10^14
        1000000000000000L,     // 15 / 10^15
        10000000000000000L,    // 16 / 10^16
        100000000000000000L,   // 17 / 10^17
        1000000000000000000L   // 18 / 10^18
    };

    private static volatile BigInteger[] BIG_TEN_POWERS_TABLE = {
        BigInteger.ONE,
        BigInteger.valueOf(10),
        BigInteger.valueOf(100),
        BigInteger.valueOf(1000),
        BigInteger.valueOf(10000),
        BigInteger.valueOf(100000),
        BigInteger.valueOf(1000000),
        BigInteger.valueOf(10000000),
        BigInteger.valueOf(100000000),
        BigInteger.valueOf(1000000000),
        BigInteger.valueOf(10000000000L),
        BigInteger.valueOf(100000000000L),
        BigInteger.valueOf(1000000000000L),
        BigInteger.valueOf(10000000000000L),
        BigInteger.valueOf(100000000000000L),
        BigInteger.valueOf(1000000000000000L),
        BigInteger.valueOf(10000000000000000L),
        BigInteger.valueOf(100000000000000000L),
        BigInteger.valueOf(1000000000000000000L)
    };

    private static final int BIG_TEN_POWERS_TABLE_INITLEN =
        BIG_TEN_POWERS_TABLE.length;
    private static final int BIG_TEN_POWERS_TABLE_MAX =
        16 * BIG_TEN_POWERS_TABLE_INITLEN;

    @Stable
    private static final long[] THRESHOLDS_TABLE = {
        Long.MAX_VALUE,                     // 0
        Long.MAX_VALUE/10L,                 // 1
        Long.MAX_VALUE/100L,                // 2
        Long.MAX_VALUE/1000L,               // 3
        Long.MAX_VALUE/10000L,              // 4
        Long.MAX_VALUE/100000L,             // 5
        Long.MAX_VALUE/1000000L,            // 6
        Long.MAX_VALUE/10000000L,           // 7
        Long.MAX_VALUE/100000000L,          // 8
        Long.MAX_VALUE/1000000000L,         // 9
        Long.MAX_VALUE/10000000000L,        // 10
        Long.MAX_VALUE/100000000000L,       // 11
        Long.MAX_VALUE/1000000000000L,      // 12
        Long.MAX_VALUE/10000000000000L,     // 13
        Long.MAX_VALUE/100000000000000L,    // 14
        Long.MAX_VALUE/1000000000000000L,   // 15
        Long.MAX_VALUE/10000000000000000L,  // 16
        Long.MAX_VALUE/100000000000000000L, // 17
        Long.MAX_VALUE/1000000000000000000L // 18
    };

    /**
     * Compute val * 10 ^ n; return this product if it is
     * representable as a long, INFLATED otherwise.
     */
    private static long longMultiplyPowerTen(long val, int n) {
        if (val == 0 || n <= 0)
            return val;
        long[] tab = LONG_TEN_POWERS_TABLE;
        long[] bounds = THRESHOLDS_TABLE;
        if (n < tab.length && n < bounds.length) {
            long tenpower = tab[n];
            if (val == 1)
                return tenpower;
            if (Math.abs(val) <= bounds[n])
                return val * tenpower;
        }
        return INFLATED;
    }

    /**
     * Compute this * 10 ^ n.
     * Needed mainly to allow special casing to trap zero value
     */
    private BigInteger bigMultiplyPowerTen(int n) {
        if (n <= 0)
            return this.inflated();

        if (intCompact != INFLATED)
            return bigTenToThe(n).multiply(intCompact);
        else
            return intVal.multiply(bigTenToThe(n));
    }

    /**
     * Returns appropriate BigInteger from intVal field if intVal is
     * null, i.e. the compact representation is in use.
     */
    private BigInteger inflated() {
        if (intVal == null) {
            return BigInteger.valueOf(intCompact);
        }
        return intVal;
    }

    /**
     * Match the scales of two {@code BigDecimal}s to align their
     * least significant digits.
     *
     * <p>If the scales of val[0] and val[1] differ, rescale
     * (non-destructively) the lower-scaled {@code BigDecimal} so
     * they match.  That is, the lower-scaled reference will be
     * replaced by a reference to a new object with the same scale as
     * the other {@code BigDecimal}.
     *
     * @param  val array of two elements referring to the two
     *         {@code BigDecimal}s to be aligned.
     */
    private static void matchScale(BigDecimal[] val) {
        if (val[0].scale < val[1].scale) {
            val[0] = val[0].setScale(val[1].scale, ROUND_UNNECESSARY);
        } else if (val[1].scale < val[0].scale) {
            val[1] = val[1].setScale(val[0].scale, ROUND_UNNECESSARY);
        }
    }

    private static class UnsafeHolder {
        private static final jdk.internal.misc.Unsafe unsafe
                = jdk.internal.misc.Unsafe.getUnsafe();
        private static final long intCompactOffset
                = unsafe.objectFieldOffset(BigDecimal.class, "intCompact");
        private static final long intValOffset
                = unsafe.objectFieldOffset(BigDecimal.class, "intVal");
        private static final long scaleOffset
                = unsafe.objectFieldOffset(BigDecimal.class, "scale");

        static void setIntValAndScale(BigDecimal bd, BigInteger intVal, int scale) {
            unsafe.putReference(bd, intValOffset, intVal);
            unsafe.putInt(bd, scaleOffset, scale);
            unsafe.putLong(bd, intCompactOffset, compactValFor(intVal));
        }

        static void setIntValVolatile(BigDecimal bd, BigInteger val) {
            unsafe.putReferenceVolatile(bd, intValOffset, val);
        }
    }

    /**
     * Reconstitute the {@code BigDecimal} instance from a stream (that is,
     * deserialize it).
     *
     * @param  s the stream being read.
     * @throws IOException if an I/O error occurs
     * @throws ClassNotFoundException if a serialized class cannot be loaded
     */
    @java.io.Serial
    private void readObject(java.io.ObjectInputStream s)
        throws IOException, ClassNotFoundException {
        // prepare to read the fields
        ObjectInputStream.GetField fields = s.readFields();
        BigInteger serialIntVal = (BigInteger) fields.get("intVal", null);

        // Validate field data
        if (serialIntVal == null) {
            throw new StreamCorruptedException("Null or missing intVal in BigDecimal stream");
        }
        // Validate provenance of serialIntVal object
        serialIntVal = toStrictBigInteger(serialIntVal);

        // Any integer value is valid for scale
        int serialScale = fields.get("scale", 0);

        UnsafeHolder.setIntValAndScale(this, serialIntVal, serialScale);
    }

    /**
     * Serialization without data not supported for this class.
     */
    @java.io.Serial
    private void readObjectNoData()
        throws ObjectStreamException {
        throw new InvalidObjectException("Deserialized BigDecimal objects need data");
    }

   /**
    * Serialize this {@code BigDecimal} to the stream in question
    *
    * @param  s the stream to serialize to.
    * @throws IOException if an I/O error occurs
    */
    @java.io.Serial
   private void writeObject(java.io.ObjectOutputStream s)
       throws IOException {
       // Must inflate to maintain compatible serial form.
       if (this.intVal == null)
           UnsafeHolder.setIntValVolatile(this, BigInteger.valueOf(this.intCompact));
       // Could reset intVal back to null if it has to be set.
       s.defaultWriteObject();
   }

    /**
     * Returns the length of the absolute value of a {@code long}, in decimal
     * digits.
     *
     * @param x the {@code long}
     * @return the length of the unscaled value, in decimal digits.
     */
    static int longDigitLength(long x) {
        /*
         * As described in "Bit Twiddling Hacks" by Sean Anderson,
         * (http://graphics.stanford.edu/~seander/bithacks.html)
         * integer log 10 of x is within 1 of (1233/4096)* (1 +
         * integer log 2 of x). The fraction 1233/4096 approximates
         * log10(2). So we first do a version of log2 (a variant of
         * Long class with pre-checks and opposite directionality) and
         * then scale and check against powers table. This is a little
         * simpler in present context than the version in Hacker's
         * Delight sec 11-4. Adding one to bit length allows comparing
         * downward from the LONG_TEN_POWERS_TABLE that we need
         * anyway.
         */
        assert x != BigDecimal.INFLATED;
        if (x < 0)
            x = -x;
        if (x < 10) // must screen for 0, might as well 10
            return 1;
        int r = ((64 - Long.numberOfLeadingZeros(x) + 1) * 1233) >>> 12;
        long[] tab = LONG_TEN_POWERS_TABLE;
        // if r >= length, must have max possible digits for long
        return (r >= tab.length || x < tab[r]) ? r : r + 1;
    }

    /**
     * Returns the length of the absolute value of a BigInteger, in
     * decimal digits.
     *
     * @param b the BigInteger
     * @return the length of the unscaled value, in decimal digits
     */
    private static int bigDigitLength(BigInteger b) {
        /*
         * Same idea as the long version, but we need a better
         * approximation of log10(2). Using 646456993/2^31
         * is accurate up to max possible reported bitLength.
         */
        if (b.signum == 0)
            return 1;
        int r = digitLengthLower(b);
        return b.compareMagnitude(bigTenToThe(r)) < 0 ? r : r + 1;
    }

    /**
     * @return an integer {@code r} such that {@code 10^(r-1) <= abs(b) < 10^(r+1)}.
     */
    private static int digitLengthLower(BigInteger b) {
        return (int) (((b.abs().bitLength() + 1L) * 646456993L) >>> 31);
    }

    /**
     * Check a scale for Underflow or Overflow.  If this BigDecimal is
     * nonzero, throw an exception if the scale is outof range. If this
     * is zero, saturate the scale to the extreme value of the right
     * sign if the scale is out of range.
     *
     * @param val The new scale.
     * @throws ArithmeticException (overflow or underflow) if the new
     *         scale is out of range.
     * @return validated scale as an int.
     */
    private int checkScale(long val) {
        int asInt = (int)val;
        if (asInt != val) {
            asInt = val>Integer.MAX_VALUE ? Integer.MAX_VALUE : Integer.MIN_VALUE;
            BigInteger b;
            if (intCompact != 0 &&
                ((b = intVal) == null || b.signum() != 0))
                throw new ArithmeticException(asInt>0 ? "Underflow":"Overflow");
        }
        return asInt;
    }

    /**
     * Returns the compact value for given {@code BigInteger}, or
     * INFLATED if too big. Relies on internal representation of
     * {@code BigInteger}.
     */
    private static long compactValFor(BigInteger b) {
        int[] m = b.mag;
        int len = m.length;
        if (len == 0)
            return 0;
        int d = m[0];
        if (len > 2 || (len == 2 && d < 0))
            return INFLATED;

        long u = (len == 2)?
            (((long) m[1] & LONG_MASK) + (((long)d) << 32)) :
            (((long)d)   & LONG_MASK);
        return (b.signum < 0)? -u : u;
    }

    private static int longCompareMagnitude(long x, long y) {
        if (x < 0)
            x = -x;
        if (y < 0)
            y = -y;
        return Long.compare(x, y);
    }

    private static int saturateLong(long s) {
        int i = (int)s;
        return (s == i) ? i : (s < 0 ? Integer.MIN_VALUE : Integer.MAX_VALUE);
    }

    /*
     * Internal printing routine
     */
    private static void print(String name, BigDecimal bd) {
        System.err.format("%s:\tintCompact %d\tintVal %d\tscale %d\tprecision %d%n",
                          name,
                          bd.intCompact,
                          bd.intVal,
                          bd.scale,
                          bd.precision);
    }

    /**
     * Check internal invariants of this BigDecimal.  These invariants
     * include:
     *
     * <ul>
     *
     * <li>The object must be initialized; either intCompact must not be
     * INFLATED or intVal is non-null.  Both of these conditions may
     * be true.
     *
     * <li>If both intCompact and intVal and set, their values must be
     * consistent.
     *
     * <li>If precision is nonzero, it must have the right value.
     * </ul>
     *
     * Note: Since this is an audit method, we are not supposed to change the
     * state of this BigDecimal object.
     */
    private BigDecimal audit() {
        if (intCompact == INFLATED) {
            if (intVal == null) {
                print("audit", this);
                throw new AssertionError("null intVal");
            }
            // Check precision
            if (precision > 0 && precision != bigDigitLength(intVal)) {
                print("audit", this);
                throw new AssertionError("precision mismatch");
            }
        } else {
            if (intVal != null) {
                long val = intVal.longValue();
                if (val != intCompact) {
                    print("audit", this);
                    throw new AssertionError("Inconsistent state, intCompact=" +
                                             intCompact + "\t intVal=" + val);
                }
            }
            // Check precision
            if (precision > 0 && precision != longDigitLength(intCompact)) {
                print("audit", this);
                throw new AssertionError("precision mismatch");
            }
        }
        return this;
    }

    /* the same as checkScale where value!=0 */
    private static int checkScaleNonZero(long val) {
        int asInt = (int)val;
        if (asInt != val) {
            throw new ArithmeticException(asInt>0 ? "Underflow":"Overflow");
        }
        return asInt;
    }

    private static int checkScale(long intCompact, long val) {
        int asInt = (int)val;
        if (asInt != val) {
            asInt = val>Integer.MAX_VALUE ? Integer.MAX_VALUE : Integer.MIN_VALUE;
            if (intCompact != 0)
                throw new ArithmeticException(asInt>0 ? "Underflow":"Overflow");
        }
        return asInt;
    }

    private static int checkScale(BigInteger intVal, long val) {
        int asInt = (int)val;
        if (asInt != val) {
            asInt = val>Integer.MAX_VALUE ? Integer.MAX_VALUE : Integer.MIN_VALUE;
            if (intVal.signum() != 0)
                throw new ArithmeticException(asInt>0 ? "Underflow":"Overflow");
        }
        return asInt;
    }

    /**
     * Returns a {@code BigDecimal} rounded according to the MathContext
     * settings;
     * If rounding is needed a new {@code BigDecimal} is created and returned.
     *
     * @param val the value to be rounded
     * @param mc the context to use.
     * @return a {@code BigDecimal} rounded according to the MathContext
     *         settings.  May return {@code value}, if no rounding needed.
     * @throws ArithmeticException if the rounding mode is
     *         {@code RoundingMode.UNNECESSARY} and the
     *         result is inexact.
     */
    private static BigDecimal doRound(BigDecimal val, MathContext mc) {
        int mcp = mc.precision;
        boolean wasDivided = false;
        if (mcp > 0) {
            BigInteger intVal = val.intVal;
            long compactVal = val.intCompact;
            int scale = val.scale;
            int prec = val.precision();
            int mode = mc.roundingMode.oldMode;
            int drop;
            if (compactVal == INFLATED) {
                drop = prec - mcp;
                while (drop > 0) {
                    scale = checkScaleNonZero((long) scale - drop);
                    intVal = divideAndRoundByTenPow(intVal, drop, mode);
                    wasDivided = true;
                    compactVal = compactValFor(intVal);
                    if (compactVal != INFLATED) {
                        prec = longDigitLength(compactVal);
                        break;
                    }
                    prec = bigDigitLength(intVal);
                    drop = prec - mcp;
                }
            }
            if (compactVal != INFLATED) {
                drop = prec - mcp;  // drop can't be more than 18
                while (drop > 0) {
                    scale = checkScaleNonZero((long) scale - drop);
                    compactVal = divideAndRound(compactVal, LONG_TEN_POWERS_TABLE[drop], mc.roundingMode.oldMode);
                    wasDivided = true;
                    prec = longDigitLength(compactVal);
                    drop = prec - mcp;
                    intVal = null;
                }
            }
            return wasDivided ? new BigDecimal(intVal,compactVal,scale,prec) : val;
        }
        return val;
    }

    /*
     * Returns a {@code BigDecimal} created from {@code long} value with
     * given scale rounded according to the MathContext settings
     */
    private static BigDecimal doRound(long compactVal, int scale, MathContext mc) {
        int mcp = mc.precision;
        if (mcp > 0 && mcp < 19) {
            int prec = longDigitLength(compactVal);
            int drop = prec - mcp;  // drop can't be more than 18
            while (drop > 0) {
                scale = checkScaleNonZero((long) scale - drop);
                compactVal = divideAndRound(compactVal, LONG_TEN_POWERS_TABLE[drop], mc.roundingMode.oldMode);
                prec = longDigitLength(compactVal);
                drop = prec - mcp;
            }
            return valueOf(compactVal, scale, prec);
        }
        return valueOf(compactVal, scale);
    }

    /*
     * Returns a {@code BigDecimal} created from {@code BigInteger} value with
     * given scale rounded according to the MathContext settings
     */
    private static BigDecimal doRound(BigInteger intVal, int scale, MathContext mc) {
        int mcp = mc.precision;
        int prec = 0;
        if (mcp > 0) {
            long compactVal = compactValFor(intVal);
            int mode = mc.roundingMode.oldMode;
            int drop;
            if (compactVal == INFLATED) {
                prec = bigDigitLength(intVal);
                drop = prec - mcp;
                while (drop > 0) {
                    scale = checkScaleNonZero((long) scale - drop);
                    intVal = divideAndRoundByTenPow(intVal, drop, mode);
                    compactVal = compactValFor(intVal);
                    if (compactVal != INFLATED) {
                        break;
                    }
                    prec = bigDigitLength(intVal);
                    drop = prec - mcp;
                }
            }
            if (compactVal != INFLATED) {
                prec = longDigitLength(compactVal);
                drop = prec - mcp;     // drop can't be more than 18
                while (drop > 0) {
                    scale = checkScaleNonZero((long) scale - drop);
                    compactVal = divideAndRound(compactVal, LONG_TEN_POWERS_TABLE[drop], mc.roundingMode.oldMode);
                    prec = longDigitLength(compactVal);
                    drop = prec - mcp;
                }
                return valueOf(compactVal,scale,prec);
            }
        }
        return new BigDecimal(intVal,INFLATED,scale,prec);
    }

    /*
     * Divides {@code BigInteger} value by ten power.
     */
    private static BigInteger divideAndRoundByTenPow(BigInteger intVal, int tenPow, int roundingMode) {
        if (tenPow < LONG_TEN_POWERS_TABLE.length)
            intVal = divideAndRound(intVal, LONG_TEN_POWERS_TABLE[tenPow], roundingMode);
        else
            intVal = divideAndRound(intVal, bigTenToThe(tenPow), roundingMode);
        return intVal;
    }

    /**
     * Internally used for division operation for division {@code long} by
     * {@code long}.
     * The returned {@code BigDecimal} object is the quotient whose scale is set
     * to the passed in scale. If the remainder is not zero, it will be rounded
     * based on the passed in roundingMode. Also, if the remainder is zero and
     * the last parameter, i.e. preferredScale is NOT equal to scale, the
     * trailing zeros of the result is stripped to match the preferredScale.
     */
    private static BigDecimal divideAndRound(long ldividend, long ldivisor, int scale, int roundingMode,
                                             int preferredScale) {

        int qsign; // quotient sign
        long q = ldividend / ldivisor; // store quotient in long
        if (roundingMode == ROUND_DOWN && scale == preferredScale)
            return valueOf(q, scale);
        long r = ldividend % ldivisor; // store remainder in long
        qsign = ((ldividend < 0) == (ldivisor < 0)) ? 1 : -1;
        if (r != 0) {
            boolean increment = needIncrement(ldivisor, roundingMode, qsign, q, r);
            return valueOf((increment ? q + qsign : q), scale);
        } else {
            if (preferredScale != scale)
                return createAndStripZerosToMatchScale(q, scale, preferredScale);
            else
                return valueOf(q, scale);
        }
    }

    /**
     * Divides {@code long} by {@code long} and do rounding based on the
     * passed in roundingMode.
     */
    private static long divideAndRound(long ldividend, long ldivisor, int roundingMode) {
        int qsign; // quotient sign
        long q = ldividend / ldivisor; // store quotient in long
        if (roundingMode == ROUND_DOWN)
            return q;
        long r = ldividend % ldivisor; // store remainder in long
        qsign = ((ldividend < 0) == (ldivisor < 0)) ? 1 : -1;
        if (r != 0) {
            boolean increment = needIncrement(ldivisor, roundingMode, qsign, q,     r);
            return increment ? q + qsign : q;
        } else {
            return q;
        }
    }

    /**
     * Shared logic of need increment computation.
     */
    private static boolean commonNeedIncrement(int roundingMode, int qsign,
                                        int cmpFracHalf, boolean oddQuot) {
        switch(roundingMode) {
        case ROUND_UNNECESSARY:
            throw new ArithmeticException("Rounding necessary");

        case ROUND_UP: // Away from zero
            return true;

        case ROUND_DOWN: // Towards zero
            return false;

        case ROUND_CEILING: // Towards +infinity
            return qsign > 0;

        case ROUND_FLOOR: // Towards -infinity
            return qsign < 0;

        default: // Some kind of half-way rounding
            assert roundingMode >= ROUND_HALF_UP &&
                roundingMode <= ROUND_HALF_EVEN: "Unexpected rounding mode" + RoundingMode.valueOf(roundingMode);

            if (cmpFracHalf < 0 ) // We're closer to higher digit
                return false;
            else if (cmpFracHalf > 0 ) // We're closer to lower digit
                return true;
            else { // half-way
                assert cmpFracHalf == 0;

                return switch (roundingMode) {
                    case ROUND_HALF_DOWN -> false;
                    case ROUND_HALF_UP   -> true;
                    case ROUND_HALF_EVEN -> oddQuot;

                    default -> throw new AssertionError("Unexpected rounding mode" + roundingMode);
                };
            }
        }
    }

    /**
     * Tests if quotient has to be incremented according the roundingMode
     */
    private static boolean needIncrement(long ldivisor, int roundingMode,
                                         int qsign, long q, long r) {
        assert r != 0L;

        int cmpFracHalf;
        if (r <= HALF_LONG_MIN_VALUE || r > HALF_LONG_MAX_VALUE) {
            cmpFracHalf = 1; // 2 * r can't fit into long
        } else {
            cmpFracHalf = longCompareMagnitude(2 * r, ldivisor);
        }

        return commonNeedIncrement(roundingMode, qsign, cmpFracHalf, (q & 1L) != 0L);
    }

    /**
     * Divides {@code BigInteger} value by {@code long} value and
     * do rounding based on the passed in roundingMode.
     */
    private static BigInteger divideAndRound(BigInteger bdividend, long ldivisor, int roundingMode) {
        // Descend into mutables for faster remainder checks
        MutableBigInteger mdividend = new MutableBigInteger(bdividend.mag);
        // store quotient
        MutableBigInteger mq = new MutableBigInteger();
        // store quotient & remainder in long
        long r = mdividend.divide(ldivisor, mq);
        // record remainder is zero or not
        boolean isRemainderZero = (r == 0);
        // quotient sign
        int qsign = (ldivisor < 0) ? -bdividend.signum : bdividend.signum;
        if (!isRemainderZero) {
            if(needIncrement(ldivisor, roundingMode, qsign, mq, r)) {
                mq.add(MutableBigInteger.ONE);
            }
        }
        return mq.toBigInteger(qsign);
    }

    /**
     * Internally used for division operation for division {@code BigInteger}
     * by {@code long}.
     * The returned {@code BigDecimal} object is the quotient whose scale is set
     * to the passed in scale. If the remainder is not zero, it will be rounded
     * based on the passed in roundingMode. Also, if the remainder is zero and
     * the last parameter, i.e. preferredScale is NOT equal to scale, the
     * trailing zeros of the result is stripped to match the preferredScale.
     */
    private static BigDecimal divideAndRound(BigInteger bdividend,
                                             long ldivisor, int scale, int roundingMode, int preferredScale) {
        // Descend into mutables for faster remainder checks
        MutableBigInteger mdividend = new MutableBigInteger(bdividend.mag);
        // store quotient
        MutableBigInteger mq = new MutableBigInteger();
        // store quotient & remainder in long
        long r = mdividend.divide(ldivisor, mq);
        // record remainder is zero or not
        boolean isRemainderZero = (r == 0);
        // quotient sign
        int qsign = (ldivisor < 0) ? -bdividend.signum : bdividend.signum;
        if (!isRemainderZero) {
            if(needIncrement(ldivisor, roundingMode, qsign, mq, r)) {
                mq.add(MutableBigInteger.ONE);
            }
            return mq.toBigDecimal(qsign, scale);
        } else {
            if (preferredScale != scale) {
                long compactVal = mq.toCompactValue(qsign);
                if(compactVal!=INFLATED) {
                    return createAndStripZerosToMatchScale(compactVal, scale, preferredScale);
                }
                BigInteger intVal =  mq.toBigInteger(qsign);
                return createAndStripZerosToMatchScale(intVal,scale, preferredScale);
            } else {
                return mq.toBigDecimal(qsign, scale);
            }
        }
    }

    /**
     * Tests if quotient has to be incremented according the roundingMode
     */
    private static boolean needIncrement(long ldivisor, int roundingMode,
                                         int qsign, MutableBigInteger mq, long r) {
        assert r != 0L;

        int cmpFracHalf;
        if (r <= HALF_LONG_MIN_VALUE || r > HALF_LONG_MAX_VALUE) {
            cmpFracHalf = 1; // 2 * r can't fit into long
        } else {
            cmpFracHalf = longCompareMagnitude(2 * r, ldivisor);
        }

        return commonNeedIncrement(roundingMode, qsign, cmpFracHalf, mq.isOdd());
    }

    /**
     * Divides {@code BigInteger} value by {@code BigInteger} value and
     * do rounding based on the passed in roundingMode.
     */
    private static BigInteger divideAndRound(BigInteger bdividend, BigInteger bdivisor, int roundingMode) {
        boolean isRemainderZero; // record remainder is zero or not
        int qsign; // quotient sign
        // Descend into mutables for faster remainder checks
        MutableBigInteger mdividend = new MutableBigInteger(bdividend.mag);
        MutableBigInteger mq = new MutableBigInteger();
        MutableBigInteger mdivisor = new MutableBigInteger(bdivisor.mag);
        MutableBigInteger mr = mdividend.divide(mdivisor, mq);
        isRemainderZero = mr.isZero();
        qsign = (bdividend.signum != bdivisor.signum) ? -1 : 1;
        if (!isRemainderZero) {
            if (needIncrement(mdivisor, roundingMode, qsign, mq, mr)) {
                mq.add(MutableBigInteger.ONE);
            }
        }
        return mq.toBigInteger(qsign);
    }

    /**
     * Internally used for division operation for division {@code BigInteger}
     * by {@code BigInteger}.
     * The returned {@code BigDecimal} object is the quotient whose scale is set
     * to the passed in scale. If the remainder is not zero, it will be rounded
     * based on the passed in roundingMode. Also, if the remainder is zero and
     * the last parameter, i.e. preferredScale is NOT equal to scale, the
     * trailing zeros of the result is stripped to match the preferredScale.
     */
    private static BigDecimal divideAndRound(BigInteger bdividend, BigInteger bdivisor, int scale, int roundingMode,
                                             int preferredScale) {
        boolean isRemainderZero; // record remainder is zero or not
        int qsign; // quotient sign
        // Descend into mutables for faster remainder checks
        MutableBigInteger mdividend = new MutableBigInteger(bdividend.mag);
        MutableBigInteger mq = new MutableBigInteger();
        MutableBigInteger mdivisor = new MutableBigInteger(bdivisor.mag);
        MutableBigInteger mr = mdividend.divide(mdivisor, mq);
        isRemainderZero = mr.isZero();
        qsign = (bdividend.signum != bdivisor.signum) ? -1 : 1;
        if (!isRemainderZero) {
            if (needIncrement(mdivisor, roundingMode, qsign, mq, mr)) {
                mq.add(MutableBigInteger.ONE);
            }
            return mq.toBigDecimal(qsign, scale);
        } else {
            if (preferredScale != scale) {
                long compactVal = mq.toCompactValue(qsign);
                if (compactVal != INFLATED) {
                    return createAndStripZerosToMatchScale(compactVal, scale, preferredScale);
                }
                BigInteger intVal = mq.toBigInteger(qsign);
                return createAndStripZerosToMatchScale(intVal, scale, preferredScale);
            } else {
                return mq.toBigDecimal(qsign, scale);
            }
        }
    }

    /**
     * Tests if quotient has to be incremented according the roundingMode
     */
    private static boolean needIncrement(MutableBigInteger mdivisor, int roundingMode,
                                         int qsign, MutableBigInteger mq, MutableBigInteger mr) {
        assert !mr.isZero();
        int cmpFracHalf = mr.compareHalf(mdivisor);
        return commonNeedIncrement(roundingMode, qsign, cmpFracHalf, mq.isOdd());
    }

    /**
     * {@code FIVE_TO_2_TO[n] == 5^(2^n)}
     */
    @Stable
    private static final BigInteger[] FIVE_TO_2_TO = new BigInteger[16 + 1];

    static {
        BigInteger pow = FIVE_TO_2_TO[0] = BigInteger.valueOf(5L);
        for (int i = 1; i < FIVE_TO_2_TO.length; i++)
            FIVE_TO_2_TO[i] = pow = pow.multiply(pow);
    }

    /**
     * @param n a non-negative integer
     * @return {@code 5^(2^n)}
     */
    private static BigInteger fiveToTwoToThe(int n) {
        int i = Math.min(n, FIVE_TO_2_TO.length - 1);
        BigInteger pow = FIVE_TO_2_TO[i];
        for (; i < n; i++)
            pow = pow.multiply(pow);

        return pow;
    }

    private static final double LOG_5_OF_2 = 0.43067655807339306; // double closest to log5(2)

    /**
     * Remove insignificant trailing zeros from this
     * {@code BigInteger} value until the preferred scale is reached or no
     * more zeros can be removed.  If the preferred scale is less than
     * Integer.MIN_VALUE, all the trailing zeros will be removed.
     * Assumes {@code intVal != 0}.
     *
     * @return new {@code BigDecimal} with a scale possibly reduced
     * to be closed to the preferred scale.
     * @throws ArithmeticException if scale overflows.
     */
    private static BigDecimal createAndStripZerosToMatchScale(BigInteger intVal, int scale, long preferredScale) {
        // avoid overflow of scale - preferredScale
        preferredScale = Math.clamp(preferredScale, Integer.MIN_VALUE - 1L, Integer.MAX_VALUE);
        int powsOf2 = intVal.getLowestSetBit();
        // scale - preferredScale >= remainingZeros >= max{n : (intVal % 10^n) == 0 && n <= scale - preferredScale}
        // a multiple of 10^n must be a multiple of 2^n
        long remainingZeros = Math.min(scale - preferredScale, powsOf2);
        if (remainingZeros <= 0L)
            return valueOf(intVal, scale, 0);

        final int sign = intVal.signum;
        if (sign < 0)
            intVal = intVal.negate(); // speed up computation of shiftRight() and bitLength()

        intVal = intVal.shiftRight(powsOf2); // remove powers of 2
        // Let k = max{n : (intVal % 5^n) == 0}, m = max{n : 5^n <= intVal}, so m >= k.
        // Let b = intVal.bitLength(). It can be shown that
        // | b * LOG_5_OF_2 - b log5(2) | < 2^(-21) (fp viz. real arithmetic),
        // which entails m <= maxPowsOf5 <= m + 1, where maxPowsOf5 is as below.
        // Hence, maxPowsOf5 >= k.
        long maxPowsOf5 = Math.round(intVal.bitLength() * LOG_5_OF_2);
        remainingZeros = Math.min(remainingZeros, maxPowsOf5);

        BigInteger[] qr; // quotient-remainder pair
        // Remove 5^(2^i) from the factors of intVal, until 5^remainingZeros < 5^(2^i).
        // Let z = max{n >= 0 : ((intVal * 2^powsOf2) % 10^n) == 0 && n <= scale - preferredScale},
        // then the condition min(scale - preferredScale, powsOf2) >= remainingZeros >= z
        // and the values ((intVal * 2^powsOf2) / 10^z) and (scale - z)
        // are preserved invariants after each iteration.
        // Note that if intVal % 5^(2^i) != 0, the loop condition will become false.
        for (int i = 0; remainingZeros >= 1L << i; i++) {
            final int exp = 1 << i;
            qr = intVal.divideAndRemainder(fiveToTwoToThe(i));
            if (qr[1].signum != 0) { // non-0 remainder
                remainingZeros = exp - 1;
            } else {
                intVal = qr[0];
                scale = checkScale(intVal, (long) scale - exp); // could Overflow
                remainingZeros -= exp;
                powsOf2 -= exp;
            }
        }

        // bitLength(remainingZeros) == min{n >= 0 : 5^(2^n) > 5^remainingZeros}
        // so, while the loop condition is true,
        // the invariant i == max{n : 5^(2^n) <= 5^remainingZeros},
        // which is equivalent to i == bitLength(remainingZeros) - 1,
        // is preserved at the beginning of each iteration.
        // Note that the loop stops exactly when remainingZeros == 0.
        // Using the same definition of z for the first loop, the invariants
        // min(scale - preferredScale, powsOf2) >= remainingZeros >= z,
        // ((intVal * 2^powsOf2) / 10^z) and (scale - z)
        // are preserved in this loop as well, so, when the loop ends,
        // remainingZeros == 0 implies z == 0, hence (intVal * 2^powsOf2) and scale
        // have the correct values to return.
        for (int i = BigInteger.bitLengthForLong(remainingZeros) - 1; i >= 0; i--) {
            final int exp = 1 << i;
            qr = intVal.divideAndRemainder(fiveToTwoToThe(i));
            if (qr[1].signum != 0) { // non-0 remainder
                remainingZeros = exp - 1;
            } else {
                intVal = qr[0];
                scale = checkScale(intVal, (long) scale - exp); // could Overflow
                remainingZeros -= exp;
                powsOf2 -= exp;

                if (remainingZeros < exp >> 1) // else i == bitLength(remainingZeros) already
                    i = BigInteger.bitLengthForLong(remainingZeros);
            }
        }

        intVal = intVal.shiftLeft(powsOf2); // restore remaining powers of 2
        return valueOf(sign >= 0 ? intVal : intVal.negate(), scale, 0);
    }

    /**
     * Remove insignificant trailing zeros from this
     * {@code long} value until the preferred scale is reached or no
     * more zeros can be removed.  If the preferred scale is less than
     * Integer.MIN_VALUE, all the trailing zeros will be removed.
     * Assumes {@code compactVal != 0 && compactVal != INFLATED}.
     *
     * @return new {@code BigDecimal} with a scale possibly reduced
     * to be closed to the preferred scale.
     * @throws ArithmeticException if scale overflows.
     */
    private static BigDecimal createAndStripZerosToMatchScale(long compactVal, int scale, long preferredScale) {
        while (compactVal % 10L == 0L && scale > preferredScale) {
            compactVal /= 10L;
            scale = checkScale(compactVal, scale - 1L); // could Overflow
        }
        return valueOf(compactVal, scale);
    }

    /**
     * Assumes {@code intVal != 0 && intCompact != 0}.
     */
    private static BigDecimal stripZerosToMatchScale(BigInteger intVal, long intCompact, int scale, long preferredScale) {
        return intCompact != INFLATED
            ? createAndStripZerosToMatchScale(intCompact, scale, preferredScale)
            : createAndStripZerosToMatchScale(intVal == null ? INFLATED_BIGINT : intVal, scale, preferredScale);
    }

    /*
     * returns INFLATED if overflow
     */
    private static long add(long xs, long ys){
        long sum = xs + ys;
        // See "Hacker's Delight" section 2-12 for explanation of
        // the overflow test.
        if ( (((sum ^ xs) & (sum ^ ys))) >= 0L) { // not overflowed
            return sum;
        }
        return INFLATED;
    }

    private static BigDecimal add(long xs, long ys, int scale){
        long sum = add(xs, ys);
        if (sum!=INFLATED)
            return BigDecimal.valueOf(sum, scale);
        return new BigDecimal(BigInteger.valueOf(xs).add(ys), scale);
    }

    private static BigDecimal add(final long xs, int scale1, final long ys, int scale2) {
        long sdiff = (long) scale1 - scale2;
        if (sdiff == 0) {
            return add(xs, ys, scale1);
        } else if (sdiff < 0) {
            int raise = checkScale(xs,-sdiff);
            long scaledX = longMultiplyPowerTen(xs, raise);
            if (scaledX != INFLATED) {
                return add(scaledX, ys, scale2);
            } else {
                BigInteger bigsum = bigMultiplyPowerTen(xs,raise).add(ys);
                return ((xs^ys)>=0) ? // same sign test
                    new BigDecimal(bigsum, INFLATED, scale2, 0)
                    : valueOf(bigsum, scale2, 0);
            }
        } else {
            int raise = checkScale(ys,sdiff);
            long scaledY = longMultiplyPowerTen(ys, raise);
            if (scaledY != INFLATED) {
                return add(xs, scaledY, scale1);
            } else {
                BigInteger bigsum = bigMultiplyPowerTen(ys,raise).add(xs);
                return ((xs^ys)>=0) ?
                    new BigDecimal(bigsum, INFLATED, scale1, 0)
                    : valueOf(bigsum, scale1, 0);
            }
        }
    }

    private static BigDecimal add(final long xs, int scale1, BigInteger snd, int scale2) {
        int rscale = scale1;
        long sdiff = (long)rscale - scale2;
        boolean sameSigns =  (Long.signum(xs) == snd.signum);
        BigInteger sum;
        if (sdiff < 0) {
            int raise = checkScale(xs,-sdiff);
            rscale = scale2;
            long scaledX = longMultiplyPowerTen(xs, raise);
            if (scaledX == INFLATED) {
                sum = snd.add(bigMultiplyPowerTen(xs,raise));
            } else {
                sum = snd.add(scaledX);
            }
        } else { //if (sdiff > 0) {
            int raise = checkScale(snd,sdiff);
            snd = bigMultiplyPowerTen(snd,raise);
            sum = snd.add(xs);
        }
        return (sameSigns) ?
            new BigDecimal(sum, INFLATED, rscale, 0) :
            valueOf(sum, rscale, 0);
    }

    private static BigDecimal add(BigInteger fst, int scale1, BigInteger snd, int scale2) {
        int rscale = scale1;
        long sdiff = (long)rscale - scale2;
        if (sdiff != 0) {
            if (sdiff < 0) {
                int raise = checkScale(fst,-sdiff);
                rscale = scale2;
                fst = bigMultiplyPowerTen(fst,raise);
            } else {
                int raise = checkScale(snd,sdiff);
                snd = bigMultiplyPowerTen(snd,raise);
            }
        }
        BigInteger sum = fst.add(snd);
        return (fst.signum == snd.signum) ?
                new BigDecimal(sum, INFLATED, rscale, 0) :
                valueOf(sum, rscale, 0);
    }

    private static BigInteger bigMultiplyPowerTen(long value, int n) {
        if (n <= 0)
            return BigInteger.valueOf(value);
        return bigTenToThe(n).multiply(value);
    }

    private static BigInteger bigMultiplyPowerTen(BigInteger value, int n) {
        if (n <= 0)
            return value;
        if(n<LONG_TEN_POWERS_TABLE.length) {
                return value.multiply(LONG_TEN_POWERS_TABLE[n]);
        }
        return value.multiply(bigTenToThe(n));
    }

    /**
     * Returns a {@code BigDecimal} whose value is {@code (xs /
     * ys)}, with rounding according to the context settings.
     *
     * Fast path - used only when (xscale <= yscale && yscale < 18
     *  && mc.presision<18) {
     */
    private static BigDecimal divideSmallFastPath(final long xs, int xscale,
                                                  final long ys, int yscale,
                                                  long preferredScale, MathContext mc) {
        int mcp = mc.precision;
        int roundingMode = mc.roundingMode.oldMode;

        assert (xscale <= yscale) && (yscale < 18) && (mcp < 18);
        int xraise = yscale - xscale; // xraise >=0
        long scaledX = (xraise==0) ? xs :
            longMultiplyPowerTen(xs, xraise); // can't overflow here!
        BigDecimal quotient;

        int cmp = longCompareMagnitude(scaledX, ys);
        if(cmp > 0) { // satisfy constraint (b)
            yscale -= 1; // [that is, divisor *= 10]
            int scl = checkScaleNonZero(preferredScale + yscale - xscale + mcp);
            if (checkScaleNonZero((long) mcp + yscale - xscale) > 0) {
                // assert newScale >= xscale
                int raise = checkScaleNonZero((long) mcp + yscale - xscale);
                long scaledXs;
                if ((scaledXs = longMultiplyPowerTen(xs, raise)) == INFLATED) {
                    quotient = null;
                    if((mcp-1) >=0 && (mcp-1)<LONG_TEN_POWERS_TABLE.length) {
                        quotient = multiplyDivideAndRound(LONG_TEN_POWERS_TABLE[mcp-1], scaledX, ys, scl, roundingMode, checkScaleNonZero(preferredScale));
                    }
                    if(quotient==null) {
                        BigInteger rb = bigMultiplyPowerTen(scaledX,mcp-1);
                        quotient = divideAndRound(rb, ys,
                                                  scl, roundingMode, checkScaleNonZero(preferredScale));
                    }
                } else {
                    quotient = divideAndRound(scaledXs, ys, scl, roundingMode, checkScaleNonZero(preferredScale));
                }
            } else {
                int newScale = checkScaleNonZero((long) xscale - mcp);
                // assert newScale >= yscale
                if (newScale == yscale) { // easy case
                    quotient = divideAndRound(xs, ys, scl, roundingMode,checkScaleNonZero(preferredScale));
                } else {
                    int raise = checkScaleNonZero((long) newScale - yscale);
                    long scaledYs;
                    if ((scaledYs = longMultiplyPowerTen(ys, raise)) == INFLATED) {
                        BigInteger rb = bigMultiplyPowerTen(ys,raise);
                        quotient = divideAndRound(BigInteger.valueOf(xs),
                                                  rb, scl, roundingMode,checkScaleNonZero(preferredScale));
                    } else {
                        quotient = divideAndRound(xs, scaledYs, scl, roundingMode,checkScaleNonZero(preferredScale));
                    }
                }
            }
        } else {
            // abs(scaledX) <= abs(ys)
            // result is "scaledX * 10^msp / ys"
            int scl = checkScaleNonZero(preferredScale + yscale - xscale + mcp);
            if(cmp==0) {
                // abs(scaleX)== abs(ys) => result will be scaled 10^mcp + correct sign
                quotient = roundedTenPower(((scaledX < 0) == (ys < 0)) ? 1 : -1, mcp, scl, checkScaleNonZero(preferredScale));
            } else {
                // abs(scaledX) < abs(ys)
                long scaledXs;
                if ((scaledXs = longMultiplyPowerTen(scaledX, mcp)) == INFLATED) {
                    quotient = null;
                    if(mcp<LONG_TEN_POWERS_TABLE.length) {
                        quotient = multiplyDivideAndRound(LONG_TEN_POWERS_TABLE[mcp], scaledX, ys, scl, roundingMode, checkScaleNonZero(preferredScale));
                    }
                    if(quotient==null) {
                        BigInteger rb = bigMultiplyPowerTen(scaledX,mcp);
                        quotient = divideAndRound(rb, ys,
                                                  scl, roundingMode, checkScaleNonZero(preferredScale));
                    }
                } else {
                    quotient = divideAndRound(scaledXs, ys, scl, roundingMode, checkScaleNonZero(preferredScale));
                }
            }
        }
        // doRound, here, only affects 1000000000 case.
        return doRound(quotient,mc);
    }

    /**
     * Returns a {@code BigDecimal} whose value is {@code (xs /
     * ys)}, with rounding according to the context settings.
     */
    private static BigDecimal divide(final long xs, int xscale, final long ys, int yscale, long preferredScale, MathContext mc) {
        int mcp = mc.precision;
        if(xscale <= yscale && yscale < 18 && mcp<18) {
            return divideSmallFastPath(xs, xscale, ys, yscale, preferredScale, mc);
        }
        if (compareMagnitudeNormalized(xs, xscale, ys, yscale) > 0) {// satisfy constraint (b)
            yscale -= 1; // [that is, divisor *= 10]
        }
        int roundingMode = mc.roundingMode.oldMode;
        // In order to find out whether the divide generates the exact result,
        // we avoid calling the above divide method. 'quotient' holds the
        // return BigDecimal object whose scale will be set to 'scl'.
        int scl = checkScaleNonZero(preferredScale + yscale - xscale + mcp);
        BigDecimal quotient;
        if (checkScaleNonZero((long) mcp + yscale - xscale) > 0) {
            int raise = checkScaleNonZero((long) mcp + yscale - xscale);
            long scaledXs;
            if ((scaledXs = longMultiplyPowerTen(xs, raise)) == INFLATED) {
                BigInteger rb = bigMultiplyPowerTen(xs,raise);
                quotient = divideAndRound(rb, ys, scl, roundingMode, checkScaleNonZero(preferredScale));
            } else {
                quotient = divideAndRound(scaledXs, ys, scl, roundingMode, checkScaleNonZero(preferredScale));
            }
        } else {
            int newScale = checkScaleNonZero((long) xscale - mcp);
            // assert newScale >= yscale
            if (newScale == yscale) { // easy case
                quotient = divideAndRound(xs, ys, scl, roundingMode,checkScaleNonZero(preferredScale));
            } else {
                int raise = checkScaleNonZero((long) newScale - yscale);
                long scaledYs;
                if ((scaledYs = longMultiplyPowerTen(ys, raise)) == INFLATED) {
                    BigInteger rb = bigMultiplyPowerTen(ys,raise);
                    quotient = divideAndRound(BigInteger.valueOf(xs),
                                              rb, scl, roundingMode,checkScaleNonZero(preferredScale));
                } else {
                    quotient = divideAndRound(xs, scaledYs, scl, roundingMode,checkScaleNonZero(preferredScale));
                }
            }
        }
        // doRound, here, only affects 1000000000 case.
        return doRound(quotient,mc);
    }

    /**
     * Returns a {@code BigDecimal} whose value is {@code (xs /
     * ys)}, with rounding according to the context settings.
     */
    private static BigDecimal divide(BigInteger xs, int xscale, long ys, int yscale, long preferredScale, MathContext mc) {
        // Normalize dividend & divisor so that both fall into [0.1, 0.999...]
        if ((-compareMagnitudeNormalized(ys, yscale, xs, xscale)) > 0) {// satisfy constraint (b)
            yscale -= 1; // [that is, divisor *= 10]
        }
        int mcp = mc.precision;
        int roundingMode = mc.roundingMode.oldMode;

        // In order to find out whether the divide generates the exact result,
        // we avoid calling the above divide method. 'quotient' holds the
        // return BigDecimal object whose scale will be set to 'scl'.
        BigDecimal quotient;
        int scl = checkScaleNonZero(preferredScale + yscale - xscale + mcp);
        if (checkScaleNonZero((long) mcp + yscale - xscale) > 0) {
            int raise = checkScaleNonZero((long) mcp + yscale - xscale);
            BigInteger rb = bigMultiplyPowerTen(xs,raise);
            quotient = divideAndRound(rb, ys, scl, roundingMode, checkScaleNonZero(preferredScale));
        } else {
            int newScale = checkScaleNonZero((long) xscale - mcp);
            // assert newScale >= yscale
            if (newScale == yscale) { // easy case
                quotient = divideAndRound(xs, ys, scl, roundingMode,checkScaleNonZero(preferredScale));
            } else {
                int raise = checkScaleNonZero((long) newScale - yscale);
                long scaledYs;
                if ((scaledYs = longMultiplyPowerTen(ys, raise)) == INFLATED) {
                    BigInteger rb = bigMultiplyPowerTen(ys,raise);
                    quotient = divideAndRound(xs, rb, scl, roundingMode,checkScaleNonZero(preferredScale));
                } else {
                    quotient = divideAndRound(xs, scaledYs, scl, roundingMode,checkScaleNonZero(preferredScale));
                }
            }
        }
        // doRound, here, only affects 1000000000 case.
        return doRound(quotient, mc);
    }

    /**
     * Returns a {@code BigDecimal} whose value is {@code (xs /
     * ys)}, with rounding according to the context settings.
     */
    private static BigDecimal divide(long xs, int xscale, BigInteger ys, int yscale, long preferredScale, MathContext mc) {
        // Normalize dividend & divisor so that both fall into [0.1, 0.999...]
        if (compareMagnitudeNormalized(xs, xscale, ys, yscale) > 0) {// satisfy constraint (b)
            yscale -= 1; // [that is, divisor *= 10]
        }
        int mcp = mc.precision;
        int roundingMode = mc.roundingMode.oldMode;

        // In order to find out whether the divide generates the exact result,
        // we avoid calling the above divide method. 'quotient' holds the
        // return BigDecimal object whose scale will be set to 'scl'.
        BigDecimal quotient;
        int scl = checkScaleNonZero(preferredScale + yscale - xscale + mcp);
        if (checkScaleNonZero((long) mcp + yscale - xscale) > 0) {
            int raise = checkScaleNonZero((long) mcp + yscale - xscale);
            BigInteger rb = bigMultiplyPowerTen(xs,raise);
            quotient = divideAndRound(rb, ys, scl, roundingMode, checkScaleNonZero(preferredScale));
        } else {
            int newScale = checkScaleNonZero((long) xscale - mcp);
            int raise = checkScaleNonZero((long) newScale - yscale);
            BigInteger rb = bigMultiplyPowerTen(ys,raise);
            quotient = divideAndRound(BigInteger.valueOf(xs), rb, scl, roundingMode,checkScaleNonZero(preferredScale));
        }
        // doRound, here, only affects 1000000000 case.
        return doRound(quotient, mc);
    }

    /**
     * Returns a {@code BigDecimal} whose value is {@code (xs /
     * ys)}, with rounding according to the context settings.
     */
    private static BigDecimal divide(BigInteger xs, int xscale, BigInteger ys, int yscale, long preferredScale, MathContext mc) {
        // Normalize dividend & divisor so that both fall into [0.1, 0.999...]
        if (compareMagnitudeNormalized(xs, xscale, ys, yscale) > 0) {// satisfy constraint (b)
            yscale -= 1; // [that is, divisor *= 10]
        }
        int mcp = mc.precision;
        int roundingMode = mc.roundingMode.oldMode;

        // In order to find out whether the divide generates the exact result,
        // we avoid calling the above divide method. 'quotient' holds the
        // return BigDecimal object whose scale will be set to 'scl'.
        BigDecimal quotient;
        int scl = checkScaleNonZero(preferredScale + yscale - xscale + mcp);
        if (checkScaleNonZero((long) mcp + yscale - xscale) > 0) {
            int raise = checkScaleNonZero((long) mcp + yscale - xscale);
            BigInteger rb = bigMultiplyPowerTen(xs,raise);
            quotient = divideAndRound(rb, ys, scl, roundingMode, checkScaleNonZero(preferredScale));
        } else {
            int newScale = checkScaleNonZero((long) xscale - mcp);
            int raise = checkScaleNonZero((long) newScale - yscale);
            BigInteger rb = bigMultiplyPowerTen(ys,raise);
            quotient = divideAndRound(xs, rb, scl, roundingMode,checkScaleNonZero(preferredScale));
        }
        // doRound, here, only affects 1000000000 case.
        return doRound(quotient, mc);
    }

    /*
     * performs divideAndRound for (dividend0*dividend1, divisor)
     * returns null if quotient can't fit into long value;
     */
    private static BigDecimal multiplyDivideAndRound(long dividend0, long dividend1, long divisor, int scale, int roundingMode,
                                                     int preferredScale) {
        int qsign = Long.signum(dividend0)*Long.signum(dividend1)*Long.signum(divisor);
        dividend0 = Math.abs(dividend0);
        dividend1 = Math.abs(dividend1);
        divisor = Math.abs(divisor);
        // multiply dividend0 * dividend1
        long d0_hi = dividend0 >>> 32;
        long d0_lo = dividend0 & LONG_MASK;
        long d1_hi = dividend1 >>> 32;
        long d1_lo = dividend1 & LONG_MASK;
        long product = d0_lo * d1_lo;
        long d0 = product & LONG_MASK;
        long d1 = product >>> 32;
        product = d0_hi * d1_lo + d1;
        d1 = product & LONG_MASK;
        long d2 = product >>> 32;
        product = d0_lo * d1_hi + d1;
        d1 = product & LONG_MASK;
        d2 += product >>> 32;
        long d3 = d2>>>32;
        d2 &= LONG_MASK;
        product = d0_hi*d1_hi + d2;
        d2 = product & LONG_MASK;
        d3 = ((product>>>32) + d3) & LONG_MASK;
        final long dividendHi = make64(d3,d2);
        final long dividendLo = make64(d1,d0);
        // divide
        return divideAndRound128(dividendHi, dividendLo, divisor, qsign, scale, roundingMode, preferredScale);
    }

    private static final long DIV_NUM_BASE = (1L<<32); // Number base (32 bits).

    /*
     * divideAndRound 128-bit value by long divisor.
     * returns null if quotient can't fit into long value;
     * Specialized version of Knuth's division
     */
    private static BigDecimal divideAndRound128(final long dividendHi, final long dividendLo, long divisor, int sign,
                                                int scale, int roundingMode, int preferredScale) {
        if (dividendHi >= divisor) {
            return null;
        }

        final int shift = Long.numberOfLeadingZeros(divisor);
        divisor <<= shift;

        final long v1 = divisor >>> 32;
        final long v0 = divisor & LONG_MASK;

        long tmp = dividendLo << shift;
        long u1 = tmp >>> 32;
        long u0 = tmp & LONG_MASK;

        tmp = (dividendHi << shift) | (dividendLo >>> 64 - shift);
        long u2 = tmp & LONG_MASK;
        long q1 = Long.divideUnsigned(tmp, v1);
        long r_tmp = Long.remainderUnsigned(tmp, v1);

        while(q1 >= DIV_NUM_BASE || unsignedLongCompare(q1*v0, make64(r_tmp, u1))) {
            q1--;
            r_tmp += v1;
            if (r_tmp >= DIV_NUM_BASE)
                break;
        }

        tmp = mulsub(u2,u1,v1,v0,q1);
        u1 = tmp & LONG_MASK;
        long q0 = Long.divideUnsigned(tmp, v1);
        r_tmp = Long.remainderUnsigned(tmp, v1);

        while(q0 >= DIV_NUM_BASE || unsignedLongCompare(q0*v0,make64(r_tmp,u0))) {
            q0--;
            r_tmp += v1;
            if (r_tmp >= DIV_NUM_BASE)
                break;
        }

        if((int)q1 < 0) {
            // result (which is positive and unsigned here)
            // can't fit into long due to sign bit is used for value
            MutableBigInteger mq = new MutableBigInteger(new int[]{(int)q1, (int)q0});
            if (roundingMode == ROUND_DOWN && scale == preferredScale) {
                return mq.toBigDecimal(sign, scale);
            }
            long r = mulsub(u1, u0, v1, v0, q0) >>> shift;
            if (r != 0) {
                if(needIncrement(divisor >>> shift, roundingMode, sign, mq, r)){
                    mq.add(MutableBigInteger.ONE);
                }
                return mq.toBigDecimal(sign, scale);
            } else {
                if (preferredScale != scale) {
                    BigInteger intVal =  mq.toBigInteger(sign);
                    return createAndStripZerosToMatchScale(intVal,scale, preferredScale);
                } else {
                    return mq.toBigDecimal(sign, scale);
                }
            }
        }

        long q = make64(q1,q0);
        q*=sign;

        if (roundingMode == ROUND_DOWN && scale == preferredScale)
            return valueOf(q, scale);

        long r = mulsub(u1, u0, v1, v0, q0) >>> shift;
        if (r != 0) {
            boolean increment = needIncrement(divisor >>> shift, roundingMode, sign, q, r);
            return valueOf((increment ? q + sign : q), scale);
        } else {
            if (preferredScale != scale) {
                return createAndStripZerosToMatchScale(q, scale, preferredScale);
            } else {
                return valueOf(q, scale);
            }
        }
    }

    /*
     * calculate divideAndRound for ldividend*10^raise / divisor
     * when abs(dividend)==abs(divisor);
     */
    private static BigDecimal roundedTenPower(int qsign, int raise, int scale, int preferredScale) {
        if (scale > preferredScale) {
            int diff = scale - preferredScale;
            if(diff < raise) {
                return scaledTenPow(raise - diff, qsign, preferredScale);
            } else {
                return valueOf(qsign,scale-raise);
            }
        } else {
            return scaledTenPow(raise, qsign, scale);
        }
    }

    static BigDecimal scaledTenPow(int n, int sign, int scale) {
        if (n < LONG_TEN_POWERS_TABLE.length)
            return valueOf(sign*LONG_TEN_POWERS_TABLE[n],scale);
        else {
            BigInteger unscaledVal = bigTenToThe(n);
            if(sign==-1) {
                unscaledVal = unscaledVal.negate();
            }
            return new BigDecimal(unscaledVal, INFLATED, scale, n+1);
        }
    }

    private static long make64(long hi, long lo) {
        return hi<<32 | lo;
    }

    private static long mulsub(long u1, long u0, final long v1, final long v0, long q0) {
        long tmp = u0 - q0*v0;
        return make64(u1 + (tmp>>>32) - q0*v1,tmp & LONG_MASK);
    }

    private static boolean unsignedLongCompare(long one, long two) {
        return (one+Long.MIN_VALUE) > (two+Long.MIN_VALUE);
    }

    private static boolean unsignedLongCompareEq(long one, long two) {
        return (one+Long.MIN_VALUE) >= (two+Long.MIN_VALUE);
    }


    // Compare Normalize dividend & divisor so that both fall into [0.1, 0.999...]
    private static int compareMagnitudeNormalized(long xs, int xscale, long ys, int yscale) {
        // assert xs!=0 && ys!=0
        int sdiff = xscale - yscale;
        if (sdiff != 0) {
            if (sdiff < 0) {
                xs = longMultiplyPowerTen(xs, -sdiff);
            } else { // sdiff > 0
                ys = longMultiplyPowerTen(ys, sdiff);
            }
        }
        if (xs != INFLATED)
            return (ys != INFLATED) ? longCompareMagnitude(xs, ys) : -1;
        else
            return 1;
    }

    // Compare Normalize dividend & divisor so that both fall into [0.1, 0.999...]
    private static int compareMagnitudeNormalized(long xs, int xscale, BigInteger ys, int yscale) {
        // assert "ys can't be represented as long"
        if (xs == 0)
            return -1;
        int sdiff = xscale - yscale;
        if (sdiff < 0) {
            if (longMultiplyPowerTen(xs, -sdiff) == INFLATED ) {
                return bigMultiplyPowerTen(xs, -sdiff).compareMagnitude(ys);
            }
        }
        return -1;
    }

    // Compare Normalize dividend & divisor so that both fall into [0.1, 0.999...]
    private static int compareMagnitudeNormalized(BigInteger xs, int xscale, BigInteger ys, int yscale) {
        int sdiff = xscale - yscale;
        if (sdiff < 0) {
            return bigMultiplyPowerTen(xs, -sdiff).compareMagnitude(ys);
        } else { // sdiff >= 0
            return xs.compareMagnitude(bigMultiplyPowerTen(ys, sdiff));
        }
    }

    private static long multiply(long x, long y){
                long product = x * y;
        long ax = Math.abs(x);
        long ay = Math.abs(y);
        if (((ax | ay) >>> 31 == 0) || (y == 0) || (product / y == x)){
                        return product;
                }
        return INFLATED;
    }

    private static BigDecimal multiply(long x, long y, int scale) {
        long product = multiply(x, y);
        if(product!=INFLATED) {
            return valueOf(product,scale);
        }
        return new BigDecimal(BigInteger.valueOf(x).multiply(y),INFLATED,scale,0);
    }

    private static BigDecimal multiply(long x, BigInteger y, int scale) {
        if(x==0) {
            return zeroValueOf(scale);
        }
        return new BigDecimal(y.multiply(x),INFLATED,scale,0);
    }

    private static BigDecimal multiply(BigInteger x, BigInteger y, int scale) {
        return new BigDecimal(x.multiply(y),INFLATED,scale,0);
    }

    /**
     * Multiplies two long values and rounds according {@code MathContext}
     */
    private static BigDecimal multiplyAndRound(long x, long y, int scale, MathContext mc) {
        long product = multiply(x, y);
        if(product!=INFLATED) {
            return doRound(product, scale, mc);
        }
        // attempt to do it in 128 bits
        int rsign = 1;
        if(x < 0) {
            x = -x;
            rsign = -1;
        }
        if(y < 0) {
            y = -y;
            rsign *= -1;
        }
        // multiply dividend0 * dividend1
        long m0_hi = x >>> 32;
        long m0_lo = x & LONG_MASK;
        long m1_hi = y >>> 32;
        long m1_lo = y & LONG_MASK;
        product = m0_lo * m1_lo;
        long m0 = product & LONG_MASK;
        long m1 = product >>> 32;
        product = m0_hi * m1_lo + m1;
        m1 = product & LONG_MASK;
        long m2 = product >>> 32;
        product = m0_lo * m1_hi + m1;
        m1 = product & LONG_MASK;
        m2 += product >>> 32;
        long m3 = m2>>>32;
        m2 &= LONG_MASK;
        product = m0_hi*m1_hi + m2;
        m2 = product & LONG_MASK;
        m3 = ((product>>>32) + m3) & LONG_MASK;
        final long mHi = make64(m3,m2);
        final long mLo = make64(m1,m0);
        BigDecimal res = doRound128(mHi, mLo, rsign, scale, mc);
        if(res!=null) {
            return res;
        }
        res = new BigDecimal(BigInteger.valueOf(x).multiply(y*rsign), INFLATED, scale, 0);
        return doRound(res,mc);
    }

    private static BigDecimal multiplyAndRound(long x, BigInteger y, int scale, MathContext mc) {
        if(x==0) {
            return zeroValueOf(scale);
        }
        return doRound(y.multiply(x), scale, mc);
    }

    private static BigDecimal multiplyAndRound(BigInteger x, BigInteger y, int scale, MathContext mc) {
        return doRound(x.multiply(y), scale, mc);
    }

    /**
     * rounds 128-bit value according {@code MathContext}
     * returns null if result can't be repsented as compact BigDecimal.
     */
    private static BigDecimal doRound128(long hi, long lo, int sign, int scale, MathContext mc) {
        int mcp = mc.precision;
        int drop;
        BigDecimal res = null;
        if(((drop = precision(hi, lo) - mcp) > 0)&&(drop<LONG_TEN_POWERS_TABLE.length)) {
            scale = checkScaleNonZero((long)scale - drop);
            res = divideAndRound128(hi, lo, LONG_TEN_POWERS_TABLE[drop], sign, scale, mc.roundingMode.oldMode, scale);
        }
        if(res!=null) {
            return doRound(res,mc);
        }
        return null;
    }

    @Stable
    private static final long[][] LONGLONG_TEN_POWERS_TABLE = {
        {   0L, 0x8AC7230489E80000L },  //10^19
        {       0x5L, 0x6bc75e2d63100000L },  //10^20
        {       0x36L, 0x35c9adc5dea00000L },  //10^21
        {       0x21eL, 0x19e0c9bab2400000L  },  //10^22
        {       0x152dL, 0x02c7e14af6800000L  },  //10^23
        {       0xd3c2L, 0x1bcecceda1000000L  },  //10^24
        {       0x84595L, 0x161401484a000000L  },  //10^25
        {       0x52b7d2L, 0xdcc80cd2e4000000L  },  //10^26
        {       0x33b2e3cL, 0x9fd0803ce8000000L  },  //10^27
        {       0x204fce5eL, 0x3e25026110000000L  },  //10^28
        {       0x1431e0faeL, 0x6d7217caa0000000L  },  //10^29
        {       0xc9f2c9cd0L, 0x4674edea40000000L  },  //10^30
        {       0x7e37be2022L, 0xc0914b2680000000L  },  //10^31
        {       0x4ee2d6d415bL, 0x85acef8100000000L  },  //10^32
        {       0x314dc6448d93L, 0x38c15b0a00000000L  },  //10^33
        {       0x1ed09bead87c0L, 0x378d8e6400000000L  },  //10^34
        {       0x13426172c74d82L, 0x2b878fe800000000L  },  //10^35
        {       0xc097ce7bc90715L, 0xb34b9f1000000000L  },  //10^36
        {       0x785ee10d5da46d9L, 0x00f436a000000000L  },  //10^37
        {       0x4b3b4ca85a86c47aL, 0x098a224000000000L  },  //10^38
    };

    /*
     * returns precision of 128-bit value
     */
    private static int precision(long hi, long lo){
        if(hi==0) {
            if(lo>=0) {
                return longDigitLength(lo);
            }
            return (unsignedLongCompareEq(lo, LONGLONG_TEN_POWERS_TABLE[0][1])) ? 20 : 19;
            // 0x8AC7230489E80000L  = unsigned 2^19
        }
        int r = ((128 - Long.numberOfLeadingZeros(hi) + 1) * 1233) >>> 12;
        int idx = r-19;
        return (idx >= LONGLONG_TEN_POWERS_TABLE.length || longLongCompareMagnitude(hi, lo,
                                                                                    LONGLONG_TEN_POWERS_TABLE[idx][0], LONGLONG_TEN_POWERS_TABLE[idx][1])) ? r : r + 1;
    }

    /*
     * returns true if 128 bit number <hi0,lo0> is less than <hi1,lo1>
     * hi0 & hi1 should be non-negative
     */
    private static boolean longLongCompareMagnitude(long hi0, long lo0, long hi1, long lo1) {
        if(hi0!=hi1) {
            return hi0<hi1;
        }
        return (lo0+Long.MIN_VALUE) <(lo1+Long.MIN_VALUE);
    }

    private static BigDecimal divide(long dividend, int dividendScale, long divisor, int divisorScale, int scale, int roundingMode) {
        if (checkScale(dividend,(long)scale + divisorScale) > dividendScale) {
            int newScale = scale + divisorScale;
            int raise = newScale - dividendScale;
            if(raise<LONG_TEN_POWERS_TABLE.length) {
                long xs = dividend;
                if ((xs = longMultiplyPowerTen(xs, raise)) != INFLATED) {
                    return divideAndRound(xs, divisor, scale, roundingMode, scale);
                }
                BigDecimal q = multiplyDivideAndRound(LONG_TEN_POWERS_TABLE[raise], dividend, divisor, scale, roundingMode, scale);
                if(q!=null) {
                    return q;
                }
            }
            BigInteger scaledDividend = bigMultiplyPowerTen(dividend, raise);
            return divideAndRound(scaledDividend, divisor, scale, roundingMode, scale);
        } else {
            int newScale = checkScale(divisor,(long)dividendScale - scale);
            int raise = newScale - divisorScale;
            if(raise<LONG_TEN_POWERS_TABLE.length) {
                long ys = divisor;
                if ((ys = longMultiplyPowerTen(ys, raise)) != INFLATED) {
                    return divideAndRound(dividend, ys, scale, roundingMode, scale);
                }
            }
            BigInteger scaledDivisor = bigMultiplyPowerTen(divisor, raise);
            return divideAndRound(BigInteger.valueOf(dividend), scaledDivisor, scale, roundingMode, scale);
        }
    }

    private static BigDecimal divide(BigInteger dividend, int dividendScale, long divisor, int divisorScale, int scale, int roundingMode) {
        if (checkScale(dividend,(long)scale + divisorScale) > dividendScale) {
            int newScale = scale + divisorScale;
            int raise = newScale - dividendScale;
            BigInteger scaledDividend = bigMultiplyPowerTen(dividend, raise);
            return divideAndRound(scaledDividend, divisor, scale, roundingMode, scale);
        } else {
            int newScale = checkScale(divisor,(long)dividendScale - scale);
            int raise = newScale - divisorScale;
            if(raise<LONG_TEN_POWERS_TABLE.length) {
                long ys = divisor;
                if ((ys = longMultiplyPowerTen(ys, raise)) != INFLATED) {
                    return divideAndRound(dividend, ys, scale, roundingMode, scale);
                }
            }
            BigInteger scaledDivisor = bigMultiplyPowerTen(divisor, raise);
            return divideAndRound(dividend, scaledDivisor, scale, roundingMode, scale);
        }
    }

    private static BigDecimal divide(long dividend, int dividendScale, BigInteger divisor, int divisorScale, int scale, int roundingMode) {
        if (checkScale(dividend,(long)scale + divisorScale) > dividendScale) {
            int newScale = scale + divisorScale;
            int raise = newScale - dividendScale;
            BigInteger scaledDividend = bigMultiplyPowerTen(dividend, raise);
            return divideAndRound(scaledDividend, divisor, scale, roundingMode, scale);
        } else {
            int newScale = checkScale(divisor,(long)dividendScale - scale);
            int raise = newScale - divisorScale;
            BigInteger scaledDivisor = bigMultiplyPowerTen(divisor, raise);
            return divideAndRound(BigInteger.valueOf(dividend), scaledDivisor, scale, roundingMode, scale);
        }
    }

    private static BigDecimal divide(BigInteger dividend, int dividendScale, BigInteger divisor, int divisorScale, int scale, int roundingMode) {
        if (checkScale(dividend,(long)scale + divisorScale) > dividendScale) {
            int newScale = scale + divisorScale;
            int raise = newScale - dividendScale;
            BigInteger scaledDividend = bigMultiplyPowerTen(dividend, raise);
            return divideAndRound(scaledDividend, divisor, scale, roundingMode, scale);
        } else {
            int newScale = checkScale(divisor,(long)dividendScale - scale);
            int raise = newScale - divisorScale;
            BigInteger scaledDivisor = bigMultiplyPowerTen(divisor, raise);
            return divideAndRound(dividend, scaledDivisor, scale, roundingMode, scale);
        }
    }

}
