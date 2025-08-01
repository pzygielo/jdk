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
 * (C) Copyright Taligent, Inc. 1996, 1997 - All Rights Reserved
 * (C) Copyright IBM Corp. 1996 - 1998 - All Rights Reserved
 *
 *   The original version of this source code and documentation is copyrighted
 * and owned by Taligent, Inc., a wholly-owned subsidiary of IBM. These
 * materials are provided under terms of a License Agreement between Taligent
 * and Sun. This technology is protected by multiple US and International
 * patents. This notice and attribution to Taligent may not be removed.
 *   Taligent is a registered trademark of Taligent, Inc.
 *
 */

package java.text;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.text.spi.NumberFormatProvider;
import java.util.ArrayList;
import java.util.Currency;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import sun.util.locale.provider.LocaleProviderAdapter;
import sun.util.locale.provider.ResourceBundleBasedAdapter;

/**
 * {@code DecimalFormat} is a concrete subclass of
 * {@code NumberFormat} that formats decimal numbers in a localized manner.
 * It has a variety of features designed to make it possible to parse and format
 * numbers in any locale, including support for Western, Arabic, and Indic digits.
 * It also supports different kinds of numbers, including integers (123), fixed-point
 * numbers (123.4), scientific notation (1.23E4), percentages (12%), and
 * currency amounts ($123).
 *
 * <h2>Getting a DecimalFormat</h2>
 *
 * To obtain a standard decimal format for a specific locale, including the default locale,
 * it is recommended to call one of the {@code NumberFormat}
 * {@link NumberFormat##factory_methods factory methods}, such as {@link NumberFormat#getInstance()}.
 * These factory methods may not always return a {@code DecimalFormat}
 * depending on the locale-service provider implementation
 * installed. Thus, to use an instance method defined by {@code DecimalFormat},
 * the {@code NumberFormat} returned by the factory method should be
 * type checked before converted to {@code DecimalFormat}. If the installed locale-sensitive
 * service implementation does not support the given {@code Locale}, the parent
 * locale chain will be looked up, and a {@code Locale} used that is supported.
 *
 * <p>If the factory methods are not desired, use one of the constructors such
 * as {@link #DecimalFormat(String) DecimalFormat(String pattern)}. See the {@link
 * ##patterns Pattern} section for more information on the {@code pattern} parameter.
 *
 * <h2>Using DecimalFormat</h2>
 * The following is an example of formatting and parsing,
 * {@snippet lang=java :
 * NumberFormat nFmt = NumberFormat.getCurrencyInstance(Locale.US);
 * if (nFmt instanceof DecimalFormat dFmt) {
 *     // pattern match to DecimalFormat to use setPositiveSuffix(String)
 *     dFmt.setPositiveSuffix(" dollars");
 *     dFmt.format(100000); // returns "$100,000.00 dollars"
 *     dFmt.parse("$100,000.00 dollars"); // returns 100000
 * }
 * }
 *
 *
 * <h2 id="formatting">Formatting and Parsing</h2>
 * <h3 id="rounding">Rounding</h3>
 *
 * When formatting, {@code DecimalFormat} can adjust its rounding using {@link
 * #setRoundingMode(RoundingMode)}. By default, it uses
 * {@link java.math.RoundingMode#HALF_EVEN RoundingMode.HALF_EVEN}.
 *
 * <h3>Digits</h3>
 *
 * When formatting, {@code DecimalFormat} uses the ten consecutive
 * characters starting with the localized zero digit defined in the
 * {@code DecimalFormatSymbols} object as digits.
 * <p>When parsing, these digits as well as all Unicode decimal digits, as
 * defined by {@link Character#digit Character.digit}, are recognized.
 *
 * <h3 id="digit_limits"> Integer and Fraction Digit Limits </h3>
 * The integer and fraction digit limits are set by either applying a {@link ##patterns
 * pattern} or using one of the appropriate {@code DecimalFormat} setter methods,
 * for example, {@link #setMinimumFractionDigits(int)}. These limits have no impact
 * on parsing behavior.
 *
 * <h3>Special Values</h3>
 * <ul>
 * <li><p><b>Not a Number</b> ({@code NaN}) is formatted as a string,
 * which is typically given as "NaN". This string is determined by {@link
 * DecimalFormatSymbols#getNaN()}. This is the only value for which the prefixes
 * and suffixes are not attached.
 *
 * <li><p><b>Infinity</b> is formatted as a string, which is typically given as
 * "&#8734;" ({@code U+221E}), with the positive or negative prefixes and suffixes
 * attached. This string is determined by {@link DecimalFormatSymbols#getInfinity()}.
 *
 * <li><p><b>Negative zero</b> ({@code "-0"}) parses to
 * <ul>
 * <li>{@code BigDecimal(0)} if {@code isParseBigDecimal()} is
 * true
 * <li>{@code Long(0)} if {@code isParseBigDecimal()} is false
 *     and {@code isParseIntegerOnly()} is true
 * <li>{@code Double(-0.0)} if both {@code isParseBigDecimal()}
 * and {@code isParseIntegerOnly()} are false
 * </ul>
 * </ul>
 *
 * <h2><a id="synchronization">Synchronization</a></h2>
 *
 * <p>
 * Decimal formats are generally not synchronized.
 * It is recommended to create separate format instances for each thread.
 * If multiple threads access a format concurrently, it must be synchronized
 * externally.
 *
 * <h2 id="patterns">DecimalFormat Pattern</h2>
 *
 * A {@code DecimalFormat} comprises a <em>pattern</em> and a set of
 * <em>symbols</em>. The pattern may be set directly using {@code applyPattern()},
 * or indirectly using the various API methods. The symbols are stored in a {@code
 * DecimalFormatSymbols} object. When using the {@code NumberFormat} factory
 * methods, the pattern and symbols are created from the locale-sensitive service
 * implementation installed.
 *
 * <p> {@code DecimalFormat} patterns have the following syntax:
 * <blockquote><pre>
 * <i>Pattern:</i>
 *         <i>PositivePattern</i>
 *         <i>PositivePattern</i> ; <i>NegativePattern</i>
 * <i>PositivePattern:</i>
 *         <i>Prefix<sub>opt</sub></i> <i>Number</i> <i>Suffix<sub>opt</sub></i>
 * <i>NegativePattern:</i>
 *         <i>Prefix<sub>opt</sub></i> <i>Number</i> <i>Suffix<sub>opt</sub></i>
 * <i>Prefix:</i>
 *         Any characters except the {@linkplain ##special_pattern_character
 *         special pattern characters}
 * <i>Suffix:</i>
 *         Any characters except the {@linkplain ##special_pattern_character
 *         special pattern characters}
 * <i>Number:</i>
 *         <i>Integer</i> <i>Exponent<sub>opt</sub></i>
 *         <i>Integer</i> . <i>Fraction</i> <i>Exponent<sub>opt</sub></i>
 * <i>Integer:</i>
 *         <i>MinimumInteger</i>
 *         #
 *         # <i>Integer</i>
 *         # , <i>Integer</i>
 * <i>MinimumInteger:</i>
 *         0
 *         0 <i>MinimumInteger</i>
 *         0 , <i>MinimumInteger</i>
 * <i>Fraction:</i>
 *         <i>MinimumFraction<sub>opt</sub></i> <i>OptionalFraction<sub>opt</sub></i>
 * <i>MinimumFraction:</i>
 *         0 <i>MinimumFraction<sub>opt</sub></i>
 * <i>OptionalFraction:</i>
 *         # <i>OptionalFraction<sub>opt</sub></i>
 * <i>Exponent:</i>
 *         E <i>MinimumExponent</i>
 * <i>MinimumExponent:</i>
 *         0 <i>MinimumExponent<sub>opt</sub></i>
 * </pre></blockquote>
 *
 * <h3><a id="special_pattern_character">Special Pattern Characters</a></h3>
 *
 * <p>The special characters in the table below are interpreted syntactically when
 * used in the DecimalFormat pattern.
 * They must be quoted, unless noted otherwise, if they are to appear in the
 * prefix or suffix as literals.
 *
 * <p> The characters in the {@code Symbol} column are used in non-localized
 * patterns. The corresponding characters in the {@code Localized Symbol} column are used
 * in localized patterns, with the characters in {@code Symbol} losing their
 * syntactical meaning. Two exceptions are the currency sign ({@code U+00A4}) and
 * quote ({@code U+0027}), which are not localized.
 * <p>
 * Non-localized patterns should be used when calling {@link #applyPattern(String)}.
 * Localized patterns should be used when calling {@link #applyLocalizedPattern(String)}.
 *
 * <blockquote>
 * <table class="striped">
 * <caption style="display:none">Chart showing symbol, location, localized, and meaning.</caption>
 * <thead>
 *     <tr>
 *          <th scope="col" style="text-align:left">Symbol
 *          <th scope="col" style="text-align:left">Localized Symbol
 *          <th scope="col" style="text-align:left">Location
 *          <th scope="col" style="text-align:left;width:50%">Meaning
 * </thead>
 * <tbody>
 *     <tr>
 *          <th scope="row">{@code 0}
 *          <td>{@link DecimalFormatSymbols#getZeroDigit()}
 *          <td>Number
 *          <td>Digit
 *     <tr>
 *          <th scope="row">{@code #}
 *          <td>{@link DecimalFormatSymbols#getDigit()}
 *          <td>Number
 *          <td>Digit, zero shows as absent
 *     <tr>
 *          <th scope="row">{@code .}
 *          <td>{@link DecimalFormatSymbols#getDecimalSeparator()}
 *          <td>Number
 *          <td>Decimal separator or monetary decimal separator
 *     <tr>
 *          <th scope="row">{@code - (U+002D)}
 *          <td>{@link DecimalFormatSymbols#getMinusSign()}
 *          <td>Number
 *          <td>Minus sign
 *     <tr>
 *          <th scope="row">{@code ,}
 *          <td>{@link DecimalFormatSymbols#getGroupingSeparator()}
 *          <td>Number
 *          <td>Grouping separator or monetary grouping separator
 *     <tr>
 *          <th scope="row">{@code E}
 *          <td>{@link DecimalFormatSymbols#getExponentSeparator()}
 *          <td>Number
 *          <td>Separates mantissa and exponent in scientific notation. This value
 *              is case sensistive. <em>Need not be quoted in prefix or suffix.</em>
 *     <tr>
 *          <th scope="row">{@code ;}
 *          <td>{@link DecimalFormatSymbols#getPatternSeparator()}
 *          <td>Subpattern boundary
 *          <td>Separates positive and negative subpatterns
 *     <tr>
 *          <th scope="row">{@code %}
 *          <td>{@link DecimalFormatSymbols#getPercent()}
 *          <td>Prefix or suffix
 *          <td>Multiply by 100 and show as percentage
 *     <tr>
 *          <th scope="row">&permil; ({@code U+2030})
 *          <td>{@link DecimalFormatSymbols#getPerMill()}
 *          <td>Prefix or suffix
 *          <td>Multiply by 1000 and show as per mille value
 *     <tr>
 *          <th scope="row">&#164; ({@code U+00A4})
 *          <td> n/a (not localized)
 *          <td>Prefix or suffix
 *          <td>Currency sign, replaced by currency symbol.  If
 *              doubled, replaced by international currency symbol.
 *              If present in a pattern, the monetary decimal/grouping separators
 *              are used instead of the decimal/grouping separators.
 *     <tr>
 *          <th scope="row">{@code ' (U+0027)}
 *          <td> n/a (not localized)
 *          <td>Prefix or suffix
 *          <td>Used to quote special characters in a prefix or suffix,
 *              for example, {@code "'#'#"} formats 123 to
 *              {@code "#123"}.  To create a single quote
 *              itself, use two in a row: {@code "# o''clock"}.
 * </tbody>
 * </table>
 * </blockquote>
 *
 * <h3>Maximum Digits Derivation</h3>
 * For any given {@code DecimalFormat} pattern, if the pattern is not
 * in scientific notation, the maximum number of integer digits will not be
 * derived from the pattern, and instead set to {@link Integer#MAX_VALUE}.
 * Otherwise, if the pattern is in scientific notation, the maximum number of
 * integer digits will be derived from the pattern. This derivation is detailed
 * in the {@link ##scientific_notation Scientific Notation} section. {@link
 * #setMaximumIntegerDigits(int)} can be used to manually adjust the maximum
 * integer digits.
 *
 * <h3>Negative Subpatterns</h3>
 * A {@code DecimalFormat} pattern contains a positive and negative
 * subpattern, for example, {@code "#,##0.00;(#,##0.00)"}.  Each
 * subpattern has a prefix, numeric part, and suffix. The negative subpattern
 * is optional; if absent, then the positive subpattern prefixed with the
 * minus sign {@code '-' (U+002D HYPHEN-MINUS)} is used as the
 * negative subpattern. That is, {@code "0.00"} alone is equivalent to
 * {@code "0.00;-0.00"}.  If there is an explicit negative subpattern, it
 * serves only to specify the negative prefix and suffix; the number of digits,
 * minimal digits, and other characteristics are all the same as the positive
 * pattern. That means that {@code "#,##0.0#;(#)"} produces precisely
 * the same behavior as {@code "#,##0.0#;(#,##0.0#)"}.
 *
 * <p>The prefixes, suffixes, and various symbols used for infinity, digits,
 * grouping separators, decimal separators, etc. may be set to arbitrary
 * values, and they will appear properly during formatting.  However, care must
 * be taken that the symbols and strings do not conflict, or parsing will be
 * unreliable.  For example, either the positive and negative prefixes or the
 * suffixes must be distinct for {@code DecimalFormat.parse()} to be able
 * to distinguish positive from negative values.  (If they are identical, then
 * {@code DecimalFormat} will behave as if no negative subpattern was
 * specified.)  Another example is that the decimal separator and grouping
 * separator should be distinct characters, or parsing will be impossible.
 *
 * <h3>Grouping Separator</h3>
 * <p>The grouping separator is commonly used for thousands, but in some
 * locales it separates ten-thousands. The grouping size is a constant number
 * of digits between the grouping characters, such as 3 for 100,000,000 or 4 for
 * 1,0000,0000. If you supply a pattern with multiple grouping characters, the
 * interval between the last one and the end of the integer is the one that is
 * used. For example, {@code "#,##,###,####"} == {@code "######,####"} ==
 * {@code "##,####,####"}.
 *
 * <h3 id="scientific_notation">Scientific Notation</h3>
 *
 * <p>Numbers in scientific notation are expressed as the product of a mantissa
 * and a power of ten, for example, 1234 can be expressed as 1.234 x 10^3.  The
 * mantissa is often in the range 1.0 &le; x {@literal <} 10.0, but it need not
 * be.
 * {@code DecimalFormat} can be instructed to format and parse scientific
 * notation <em>only via a pattern</em>; there is currently no factory method
 * that creates a scientific notation format.  In a pattern, the exponent
 * character immediately followed by one or more digit characters indicates
 * scientific notation.  Example: {@code "0.###E0"} formats the number
 * 1234 as {@code "1.234E3"}.
 *
 * <ul>
 * <li>The number of digit characters after the exponent character gives the
 * minimum exponent digit count.  There is no maximum.  Negative exponents are
 * formatted using the localized minus sign, <em>not</em> the prefix and suffix
 * from the pattern.  This allows patterns such as {@code "0.###E0 m/s"}.
 *
 * <li>The <em>maximum integer</em> digits is the sum of '0's and '#'s
 * prior to the decimal point. The <em>minimum integer</em> digits is the
 * sum of the '0's prior to the decimal point. The <em>maximum fraction</em>
 * and <em>minimum fraction</em> digits follow the same rules, but apply to the
 * digits after the decimal point but before the exponent. For example, the
 * following pattern: {@code "#00.0####E0"} would have a minimum number of
 * integer digits = 2("00") and a maximum number of integer digits = 3("#00"). It
 * would have a minimum number of fraction digits = 1("0") and a maximum number of fraction
 * digits= 5("0####").
 *
 * <li>The minimum and maximum number of integer digits are interpreted
 * together:
 *
 * <ul>
 * <li>If the maximum number of integer digits is greater than their minimum number
 * and greater than 1, it forces the exponent to be a multiple of the maximum
 * number of integer digits, and the minimum number of integer digits to be
 * interpreted as 1.  The most common use of this is to generate
 * <em>engineering notation</em>, in which the exponent is a multiple of three,
 * e.g., {@code "##0.#####E0"}. Using this pattern, the number 12345
 * formats to {@code "12.345E3"}, and 123456 formats to
 * {@code "123.456E3"}.
 *
 * <li>Otherwise, the minimum number of integer digits is achieved by adjusting the
 * exponent.  Example: 0.00123 formatted with {@code "00.###E0"} yields
 * {@code "12.3E-4"}.
 * </ul>
 *
 * <li>For a given number, the amount of significant digits in
 * the mantissa can be calculated as such
 *
 * <blockquote><pre>
 * <i>Mantissa Digits:</i>
 *         min(max(Minimum Pattern Digits, Original Number Digits), Maximum Pattern Digits)
 * <i>Minimum pattern Digits:</i>
 *         <i>Minimum Integer Digits</i> + <i>Minimum Fraction Digits</i>
 * <i>Maximum pattern Digits:</i>
 *         <i>Maximum Integer Digits</i> + <i>Maximum Fraction Digits</i>
 * <i>Original Number Digits:</i>
 *         The amount of significant digits in the number to be formatted
 * </pre></blockquote>
 *
 * This means that generally, a mantissa will have up to the combined maximum integer
 * and fraction digits, if the original number itself has enough significant digits. However,
 * if there are more minimum pattern digits than significant digits in the original number,
 * the mantissa will have significant digits that equals the combined
 * minimum integer and fraction digits. The number of significant digits
 * does not affect parsing.
 *
 * <p>It should be noted, that the integer portion of the mantissa will give
 * any excess digits to the fraction portion, whether it be for precision or
 * for satisfying the total amount of combined minimum digits.
 *
 * <p>This behavior can be observed in the following example,
 * {@snippet lang=java :
 *     DecimalFormat df = new DecimalFormat("#000.000##E0");
 *     df.format(12); // returns "12.0000E0"
 *     df.format(123456789) // returns "1.23456789E8"
 * }
 *
 * <li>Exponential patterns may not contain grouping separators.
 * </ul>
 *
 * @implSpec
 * When formatting a {@code Number} other than {@code BigInteger} and
 * {@code BigDecimal}, {@code 309} is used as the upper limit for integer digits,
 * and {@code 340} as the upper limit for fraction digits. This occurs, even if
 * one of the {@code DecimalFormat} getter methods, for example, {@link #getMinimumFractionDigits()}
 * returns a numerically greater value.
 *
 * @spec         https://www.unicode.org/reports/tr35
 *               Unicode Locale Data Markup Language (LDML)
 * @see          <a href="http://docs.oracle.com/javase/tutorial/i18n/format/decimalFormat.html">Java Tutorial</a>
 * @see          NumberFormat
 * @see          DecimalFormatSymbols
 * @see          ParsePosition
 * @see          Locale
 * @author       Mark Davis
 * @author       Alan Liu
 * @since 1.1
 */
public class DecimalFormat extends NumberFormat {

    /**
     * Creates a DecimalFormat using the default pattern and symbols
     * for the default {@link java.util.Locale.Category#FORMAT FORMAT} locale.
     * This is a convenient way to obtain a
     * DecimalFormat when internationalization is not the main concern.
     *
     * @apiNote To obtain standard formats for a given locale, use the
     * {@code NumberFormat} factory methods such as {@link
     * NumberFormat#getNumberInstance(Locale)}. These factories will return the most
     * appropriate subclass of NumberFormat for a given locale.
     * @see NumberFormat#getInstance(Locale)
     * @see NumberFormat#getNumberInstance(Locale)
     * @see NumberFormat#getCurrencyInstance(Locale)
     * @see NumberFormat#getPercentInstance(Locale)
     */
    @SuppressWarnings("this-escape")
    public DecimalFormat() {
        // Get the pattern for the default locale.
        Locale def = Locale.getDefault(Locale.Category.FORMAT);
        LocaleProviderAdapter adapter = LocaleProviderAdapter.getAdapter(NumberFormatProvider.class, def);
        if (!(adapter instanceof ResourceBundleBasedAdapter)) {
            adapter = LocaleProviderAdapter.getResourceBundleBased();
        }
        String[] all = adapter.getLocaleResources(def).getNumberPatterns();

        // Always applyPattern after the symbols are set
        this.symbols = DecimalFormatSymbols.getInstance(def);
        applyPattern(all[0], false);
    }


    /**
     * Creates a DecimalFormat using the given pattern and the symbols
     * for the default {@link java.util.Locale.Category#FORMAT FORMAT} locale.
     * This is a convenient way to obtain a
     * DecimalFormat when internationalization is not the main concern.
     * The number of maximum integer digits is usually not derived from the pattern.
     * See the note in the {@link ##patterns Patterns} section for more detail.
     *
     * @apiNote To obtain standard formats for a given locale, use the
     * {@code NumberFormat} factory methods such as {@link
     * NumberFormat#getNumberInstance(Locale)}. These factories will return the most
     * appropriate subclass of NumberFormat for a given locale.
     * @param pattern a non-localized pattern string.
     * @throws    NullPointerException if {@code pattern} is null
     * @throws    IllegalArgumentException if the given pattern is invalid.
     * @see NumberFormat#getInstance(Locale)
     * @see NumberFormat#getNumberInstance(Locale)
     * @see NumberFormat#getCurrencyInstance(Locale)
     * @see NumberFormat#getPercentInstance(Locale)
     */
    @SuppressWarnings("this-escape")
    public DecimalFormat(String pattern) {
        // Always applyPattern after the symbols are set
        this.symbols = DecimalFormatSymbols.getInstance(Locale.getDefault(Locale.Category.FORMAT));
        applyPattern(pattern, false);
    }


    /**
     * Creates a DecimalFormat using the given pattern and symbols.
     * Use this constructor when you need to completely customize the
     * behavior of the format.
     * The number of maximum integer digits is usually not derived from the pattern.
     * See the note in the {@link ##patterns Patterns} section for more detail.
     *
     * @apiNote To obtain standard formats for a given locale, use the
     * {@code NumberFormat} factory methods such as {@link
     * NumberFormat#getInstance(Locale)} or {@link NumberFormat#getCurrencyInstance(Locale)}.
     * If you need only minor adjustments to a standard format, you can modify
     * the format returned by a NumberFormat factory method.
     * @param pattern a non-localized pattern string
     * @param symbols the set of symbols to be used
     * @throws    NullPointerException if any of the given arguments is null
     * @throws    IllegalArgumentException if the given pattern is invalid
     * @see NumberFormat#getInstance(Locale)
     * @see NumberFormat#getNumberInstance(Locale)
     * @see NumberFormat#getCurrencyInstance(Locale)
     * @see NumberFormat#getPercentInstance(Locale)
     * @see java.text.DecimalFormatSymbols
     */
    @SuppressWarnings("this-escape")
    public DecimalFormat (String pattern, DecimalFormatSymbols symbols) {
        // Always applyPattern after the symbols are set
        this.symbols = (DecimalFormatSymbols)symbols.clone();
        applyPattern(pattern, false);
    }


    // Overrides
    /**
     * Formats a number and appends the resulting text to the given string
     * buffer.
     * The number can be of any subclass of {@link java.lang.Number}.
     *
     * @implSpec This implementation uses the maximum precision permitted.
     * @param number     the number to format
     * @param toAppendTo the {@code StringBuffer} to which the formatted
     *                   text is to be appended
     * @param pos        keeps track on the position of the field within the
     *                   returned string. For example, for formatting a number
     *                   {@code 1234567.89} in {@code Locale.US} locale,
     *                   if the given {@code fieldPosition} is
     *                   {@link NumberFormat#INTEGER_FIELD}, the begin index
     *                   and end index of {@code fieldPosition} will be set
     *                   to 0 and 9, respectively for the output string
     *                   {@code 1,234,567.89}.
     * @return           the value passed in as {@code toAppendTo}
     * @throws           IllegalArgumentException if {@code number} is
     *                   null or not an instance of {@code Number}.
     * @throws           NullPointerException if {@code toAppendTo} or
     *                   {@code pos} is null
     * @throws           ArithmeticException if rounding is needed with rounding
     *                   mode being set to RoundingMode.UNNECESSARY
     * @see              java.text.FieldPosition
     */
    @Override
    public final StringBuffer format(Object number,
                                     StringBuffer toAppendTo,
                                     FieldPosition pos) {
        return switch (number) {
            case Long l -> format(l.longValue(), toAppendTo, pos);
            case Integer i -> format(i.longValue(), toAppendTo, pos);
            case Short s -> format(s.longValue(), toAppendTo, pos);
            case Byte b -> format(b.longValue(), toAppendTo, pos);
            case AtomicInteger ai -> format(ai.longValue(), toAppendTo, pos);
            case AtomicLong al -> format(al.longValue(), toAppendTo, pos);
            case BigInteger bi when bi.bitLength() < 64 -> format(bi.longValue(), toAppendTo, pos);
            case BigDecimal bd -> format(bd, StringBufFactory.of(toAppendTo), pos).asStringBuffer();
            case BigInteger bi -> format(bi, StringBufFactory.of(toAppendTo), pos).asStringBuffer();
            case Number n -> format(n.doubleValue(), toAppendTo, pos);
            case null, default -> throw new IllegalArgumentException("Cannot format given Object as a Number");
        };
    }

    @Override
    final StringBuf format(Object number,
                           StringBuf toAppendTo,
                           FieldPosition pos) {
        return switch (number) {
            case Long l -> format(l.longValue(), toAppendTo, pos);
            case Integer i -> format(i.longValue(), toAppendTo, pos);
            case Short s -> format(s.longValue(), toAppendTo, pos);
            case Byte b -> format(b.longValue(), toAppendTo, pos);
            case AtomicInteger ai -> format(ai.longValue(), toAppendTo, pos);
            case AtomicLong al -> format(al.longValue(), toAppendTo, pos);
            case BigInteger bi when bi.bitLength() < 64 -> format(bi.longValue(), toAppendTo, pos);
            case BigDecimal bd -> format(bd, toAppendTo, pos);
            case BigInteger bi -> format(bi, toAppendTo, pos);
            case Number n -> format(n.doubleValue(), toAppendTo, pos);
            case null, default -> throw new IllegalArgumentException("Cannot format given Object as a Number");
        };
    }

    /**
     * Formats a double to produce a string.
     * @param number    The double to format
     * @param result    where the text is to be appended
     * @param fieldPosition    keeps track on the position of the field within
     *                         the returned string. For example, for formatting
     *                         a number {@code 1234567.89} in {@code Locale.US}
     *                         locale, if the given {@code fieldPosition} is
     *                         {@link NumberFormat#INTEGER_FIELD}, the begin index
     *                         and end index of {@code fieldPosition} will be set
     *                         to 0 and 9, respectively for the output string
     *                         {@code 1,234,567.89}.
     * @throws    NullPointerException if {@code result} or
     *            {@code fieldPosition} is {@code null}
     * @throws    ArithmeticException if rounding is needed with rounding
     *            mode being set to RoundingMode.UNNECESSARY
     * @return The formatted number string
     * @see java.text.FieldPosition
     */
    @Override
    public StringBuffer format(double number, StringBuffer result,
                               FieldPosition fieldPosition) {
        return format(number, StringBufFactory.of(result), fieldPosition).asStringBuffer();
    }

    @Override
    StringBuf format(double number, StringBuf result,
                     FieldPosition fieldPosition) {
        // If fieldPosition is a DontCareFieldPosition instance we can
        // try to go to fast-path code.
        boolean tryFastPath = false;
        if (fieldPosition == DontCareFieldPosition.INSTANCE)
            tryFastPath = true;
        else {
            fieldPosition.setBeginIndex(0);
            fieldPosition.setEndIndex(0);
        }

        if (tryFastPath) {
            String tempResult = fastFormat(number);
            if (tempResult != null) {
                result.append(tempResult);
                return result;
            }
        }

        // if fast-path could not work, we fallback to standard code.
        return format(number, result, fieldPosition.getFieldDelegate());
    }

    /**
     * Formats a double to produce a string.
     * @param number    The double to format
     * @param result    where the text is to be appended
     * @param delegate notified of locations of sub fields
     * @throws          ArithmeticException if rounding is needed with rounding
     *                  mode being set to RoundingMode.UNNECESSARY
     * @return The formatted number string
     */
    StringBuf format(double number, StringBuf result,
                     FieldDelegate delegate) {

        boolean nanOrInfinity = handleNaN(number, result, delegate);
        if (nanOrInfinity) {
            return result;
        }

        /* Detecting whether a double is negative is easy with the exception of
         * the value -0.0.  This is a double which has a zero mantissa (and
         * exponent), but a negative sign bit.  It is semantically distinct from
         * a zero with a positive sign bit, and this distinction is important
         * to certain kinds of computations.  However, it's a little tricky to
         * detect, since (-0.0 == 0.0) and !(-0.0 < 0.0).  How then, you may
         * ask, does it behave distinctly from +0.0?  Well, 1/(-0.0) ==
         * -Infinity.  Proper detection of -0.0 is needed to deal with the
         * issues raised by bugs 4106658, 4106667, and 4147706.  Liu 7/6/98.
         */
        boolean isNegative = ((number < 0.0) || (number == 0.0 && 1/number < 0.0)) ^ (multiplier < 0);

        if (multiplier != 1) {
            number *= multiplier;
        }

        nanOrInfinity = handleInfinity(number, result, delegate, isNegative);
        if (nanOrInfinity) {
            return result;
        }

        if (isNegative) {
            number = -number;
        }

        // at this point we are guaranteed a nonnegative finite number.
        assert (number >= 0 && !Double.isInfinite(number));
        return doubleSubformat(number, result, delegate, isNegative);
    }

    /**
     * Checks if the given {@code number} is {@code Double.NaN}. if yes;
     * appends the NaN symbol to the result string. The NaN string is
     * determined by the DecimalFormatSymbols object.
     * @param number the double number to format
     * @param result where the text is to be appended
     * @param delegate notified of locations of sub fields
     * @return true, if number is a NaN; false otherwise
     */
    boolean handleNaN(double number, StringBuf result,
            FieldDelegate delegate) {
        if (Double.isNaN(number)
                || (Double.isInfinite(number) && multiplier == 0)) {
            int iFieldStart = result.length();
            result.append(symbols.getNaN());
            delegate.formatted(INTEGER_FIELD, Field.INTEGER, Field.INTEGER,
                    iFieldStart, result.length(), result);
            return true;
        }
        return false;
    }

    /**
     * Checks if the given {@code number} is {@code Double.NEGATIVE_INFINITY}
     * or {@code Double.POSITIVE_INFINITY}. if yes;
     * appends the infinity string to the result string. The infinity string is
     * determined by the DecimalFormatSymbols object.
     * @param number the double number to format
     * @param result where the text is to be appended
     * @param delegate notified of locations of sub fields
     * @param isNegative whether the given {@code number} is negative
     * @return true, if number is a {@code Double.NEGATIVE_INFINITY} or
     *         {@code Double.POSITIVE_INFINITY}; false otherwise
     */
    boolean handleInfinity(double number, StringBuf result,
            FieldDelegate delegate, boolean isNegative) {
        if (Double.isInfinite(number)) {
            if (isNegative) {
                append(result, negativePrefix, delegate,
                       getNegativePrefixFieldPositions(), Field.SIGN);
            } else {
                append(result, positivePrefix, delegate,
                       getPositivePrefixFieldPositions(), Field.SIGN);
            }

            int iFieldStart = result.length();
            result.append(symbols.getInfinity());
            delegate.formatted(INTEGER_FIELD, Field.INTEGER, Field.INTEGER,
                               iFieldStart, result.length(), result);

            if (isNegative) {
                append(result, negativeSuffix, delegate,
                       getNegativeSuffixFieldPositions(), Field.SIGN);
            } else {
                append(result, positiveSuffix, delegate,
                       getPositiveSuffixFieldPositions(), Field.SIGN);
            }

            return true;
        }
        return false;
    }

    StringBuf doubleSubformat(double number, StringBuf result,
            FieldDelegate delegate, boolean isNegative) {
        synchronized (digitList) {
            int maxIntDigits = super.getMaximumIntegerDigits();
            int minIntDigits = super.getMinimumIntegerDigits();
            int maxFraDigits = super.getMaximumFractionDigits();
            int minFraDigits = super.getMinimumFractionDigits();

            digitList.set(isNegative, number, useExponentialNotation
                    ? maxIntDigits + maxFraDigits : maxFraDigits,
                    !useExponentialNotation);
            return subformat(result, delegate, isNegative, false,
                    maxIntDigits, minIntDigits, maxFraDigits, minFraDigits);
        }
    }

    /**
     * Format a long to produce a string.
     * @param number    The long to format
     * @param result    where the text is to be appended
     * @param fieldPosition    keeps track on the position of the field within
     *                         the returned string. For example, for formatting
     *                         a number {@code 123456789} in {@code Locale.US}
     *                         locale, if the given {@code fieldPosition} is
     *                         {@link NumberFormat#INTEGER_FIELD}, the begin index
     *                         and end index of {@code fieldPosition} will be set
     *                         to 0 and 11, respectively for the output string
     *                         {@code 123,456,789}.
     * @throws          NullPointerException if {@code result} or
     *                  {@code fieldPosition} is {@code null}
     * @throws          ArithmeticException if rounding is needed with rounding
     *                  mode being set to RoundingMode.UNNECESSARY
     * @return The formatted number string
     * @see java.text.FieldPosition
     */
    @Override
    public StringBuffer format(long number, StringBuffer result,
                               FieldPosition fieldPosition) {
        fieldPosition.setBeginIndex(0);
        fieldPosition.setEndIndex(0);

        return format(number, StringBufFactory.of(result), fieldPosition.getFieldDelegate()).asStringBuffer();
    }

    StringBuf format(long number, StringBuf result,
                     FieldPosition fieldPosition) {
        fieldPosition.setBeginIndex(0);
        fieldPosition.setEndIndex(0);

        return format(number, result, fieldPosition.getFieldDelegate());
    }

    /**
     * Format a long to produce a string.
     * @param number    The long to format
     * @param result    where the text is to be appended
     * @param delegate notified of locations of sub fields
     * @return The formatted number string
     * @throws           ArithmeticException if rounding is needed with rounding
     *                   mode being set to RoundingMode.UNNECESSARY
     * @see java.text.FieldPosition
     */
    StringBuf format(long number, StringBuf result,
                     FieldDelegate delegate) {
        boolean isNegative = (number < 0);
        if (isNegative) {
            number = -number;
        }

        // In general, long values always represent real finite numbers, so
        // we don't have to check for +/- Infinity or NaN.  However, there
        // is one case we have to be careful of:  The multiplier can push
        // a number near MIN_VALUE or MAX_VALUE outside the legal range.  We
        // check for this before multiplying, and if it happens we use
        // BigInteger instead.
        boolean useBigInteger = false;
        if (number < 0) { // This can only happen if number == Long.MIN_VALUE.
            if (multiplier != 0) {
                useBigInteger = true;
            }
        } else if (multiplier != 1 && multiplier != 0) {
            long cutoff = Long.MAX_VALUE / multiplier;
            if (cutoff < 0) {
                cutoff = -cutoff;
            }
            useBigInteger = (number > cutoff);
        }

        if (useBigInteger) {
            if (isNegative) {
                number = -number;
            }
            BigInteger bigIntegerValue = BigInteger.valueOf(number);
            return format(bigIntegerValue, result, delegate, true);
        }

        number *= multiplier;
        if (number == 0) {
            isNegative = false;
        } else {
            if (multiplier < 0) {
                number = -number;
                isNegative = !isNegative;
            }
        }

        synchronized(digitList) {
            int maxIntDigits = super.getMaximumIntegerDigits();
            int minIntDigits = super.getMinimumIntegerDigits();
            int maxFraDigits = super.getMaximumFractionDigits();
            int minFraDigits = super.getMinimumFractionDigits();

            digitList.set(isNegative, number,
                     useExponentialNotation ? maxIntDigits + maxFraDigits : 0);

            return subformat(result, delegate, isNegative, true,
                       maxIntDigits, minIntDigits, maxFraDigits, minFraDigits);
        }
    }

    /**
     * Formats a BigDecimal to produce a string.
     * @param number    The BigDecimal to format
     * @param result    where the text is to be appended
     * @param fieldPosition    keeps track on the position of the field within
     *                         the returned string. For example, for formatting
     *                         a number {@code 1234567.89} in {@code Locale.US}
     *                         locale, if the given {@code fieldPosition} is
     *                         {@link NumberFormat#INTEGER_FIELD}, the begin index
     *                         and end index of {@code fieldPosition} will be set
     *                         to 0 and 9, respectively for the output string
     *                         {@code 1,234,567.89}.
     * @return The formatted number string
     * @throws           ArithmeticException if rounding is needed with rounding
     *                   mode being set to RoundingMode.UNNECESSARY
     * @see java.text.FieldPosition
     */
    private StringBuf format(BigDecimal number, StringBuf result,
                             FieldPosition fieldPosition) {
        fieldPosition.setBeginIndex(0);
        fieldPosition.setEndIndex(0);
        return format(number, result, fieldPosition.getFieldDelegate());
    }

    /**
     * Formats a BigDecimal to produce a string.
     * @param number    The BigDecimal to format
     * @param result    where the text is to be appended
     * @param delegate notified of locations of sub fields
     * @throws           ArithmeticException if rounding is needed with rounding
     *                   mode being set to RoundingMode.UNNECESSARY
     * @return The formatted number string
     */
    StringBuf format(BigDecimal number, StringBuf result,
                     FieldDelegate delegate) {
        if (multiplier != 1) {
            number = number.multiply(getBigDecimalMultiplier());
        }
        boolean isNegative = number.signum() == -1;
        if (isNegative) {
            number = number.negate();
        }

        synchronized(digitList) {
            int maxIntDigits = getMaximumIntegerDigits();
            int minIntDigits = getMinimumIntegerDigits();
            int maxFraDigits = getMaximumFractionDigits();
            int minFraDigits = getMinimumFractionDigits();
            int maximumDigits = maxIntDigits + maxFraDigits;

            digitList.set(isNegative, number, useExponentialNotation ?
                ((maximumDigits < 0) ? Integer.MAX_VALUE : maximumDigits) :
                maxFraDigits, !useExponentialNotation);

            return subformat(result, delegate, isNegative, false,
                maxIntDigits, minIntDigits, maxFraDigits, minFraDigits);
        }
    }

    /**
     * Format a BigInteger to produce a string.
     * @param number    The BigInteger to format
     * @param result    where the text is to be appended
     * @param fieldPosition    keeps track on the position of the field within
     *                         the returned string. For example, for formatting
     *                         a number {@code 123456789} in {@code Locale.US}
     *                         locale, if the given {@code fieldPosition} is
     *                         {@link NumberFormat#INTEGER_FIELD}, the begin index
     *                         and end index of {@code fieldPosition} will be set
     *                         to 0 and 11, respectively for the output string
     *                         {@code 123,456,789}.
     * @return The formatted number string
     * @throws           ArithmeticException if rounding is needed with rounding
     *                   mode being set to RoundingMode.UNNECESSARY
     * @see java.text.FieldPosition
     */
    private StringBuf format(BigInteger number, StringBuf result,
                             FieldPosition fieldPosition) {
        fieldPosition.setBeginIndex(0);
        fieldPosition.setEndIndex(0);

        return format(number, result, fieldPosition.getFieldDelegate(), false);
    }

    /**
     * Format a BigInteger to produce a string.
     * @param number    The BigInteger to format
     * @param result    where the text is to be appended
     * @param delegate notified of locations of sub fields
     * @return The formatted number string
     * @throws           ArithmeticException if rounding is needed with rounding
     *                   mode being set to RoundingMode.UNNECESSARY
     * @see java.text.FieldPosition
     */
    StringBuf format(BigInteger number, StringBuf result,
                     FieldDelegate delegate, boolean formatLong) {
        if (multiplier != 1) {
            number = number.multiply(getBigIntegerMultiplier());
        }
        boolean isNegative = number.signum() == -1;
        if (isNegative) {
            number = number.negate();
        }

        synchronized(digitList) {
            int maxIntDigits, minIntDigits, maxFraDigits, minFraDigits, maximumDigits;
            if (formatLong) {
                maxIntDigits = super.getMaximumIntegerDigits();
                minIntDigits = super.getMinimumIntegerDigits();
                maxFraDigits = super.getMaximumFractionDigits();
                minFraDigits = super.getMinimumFractionDigits();
                maximumDigits = maxIntDigits + maxFraDigits;
            } else {
                maxIntDigits = getMaximumIntegerDigits();
                minIntDigits = getMinimumIntegerDigits();
                maxFraDigits = getMaximumFractionDigits();
                minFraDigits = getMinimumFractionDigits();
                maximumDigits = maxIntDigits + maxFraDigits;
                if (maximumDigits < 0) {
                    maximumDigits = Integer.MAX_VALUE;
                }
            }

            digitList.set(isNegative, number,
                          useExponentialNotation ? maximumDigits : 0);

            return subformat(result, delegate, isNegative, true,
                maxIntDigits, minIntDigits, maxFraDigits, minFraDigits);
        }
    }

    /**
     * Formats an Object producing an {@code AttributedCharacterIterator}.
     * You can use the returned {@code AttributedCharacterIterator}
     * to build the resulting String, as well as to determine information
     * about the resulting String.
     * <p>
     * Each attribute key of the AttributedCharacterIterator will be of type
     * {@code NumberFormat.Field}, with the attribute value being the
     * same as the attribute key.
     *
     * @throws    NullPointerException if obj is null.
     * @throws    IllegalArgumentException when the Format cannot format the
     *            given object.
     * @throws           ArithmeticException if rounding is needed with rounding
     *                   mode being set to RoundingMode.UNNECESSARY
     * @param obj The object to format
     * @return AttributedCharacterIterator describing the formatted value.
     * @since 1.4
     */
    @Override
    public AttributedCharacterIterator formatToCharacterIterator(Object obj) {
        CharacterIteratorFieldDelegate delegate =
                new CharacterIteratorFieldDelegate();
        StringBuf sb = StringBufFactory.of();
        switch (obj) {
            case Double d -> format(d.doubleValue(), sb, delegate);
            case Float f -> format(f.doubleValue(), sb, delegate);
            case Long l -> format(l.longValue(), sb, delegate);
            case Integer i -> format(i.longValue(), sb, delegate);
            case Short s -> format(s.longValue(), sb, delegate);
            case Byte b -> format(b.longValue(), sb, delegate);
            case AtomicInteger ai -> format(ai.longValue(), sb, delegate);
            case AtomicLong al -> format(al.longValue(), sb, delegate);
            case BigDecimal bd -> format(bd, sb, delegate);
            case BigInteger bi -> format(bi, sb, delegate, false);
            case null -> throw new NullPointerException(
                    "formatToCharacterIterator must be passed non-null object");
            default -> throw new IllegalArgumentException(
                    "Cannot format given Object as a Number");
        }
        return delegate.getIterator(sb.toString());
    }

    // ==== Begin fast-path formatting logic for double =========================

    /* Fast-path formatting will be used for format(double ...) methods iff a
     * number of conditions are met (see checkAndSetFastPathStatus()):
     * - Only if instance properties meet the right predefined conditions.
     * - The abs value of the double to format is <= Integer.MAX_VALUE.
     *
     * The basic approach is to split the binary to decimal conversion of a
     * double value into two phases:
     * * The conversion of the integer portion of the double.
     * * The conversion of the fractional portion of the double
     *   (limited to two or three digits).
     *
     * The isolation and conversion of the integer portion of the double is
     * straightforward. The conversion of the fraction is more subtle and relies
     * on some rounding properties of double to the decimal precisions in
     * question.  Using the terminology of BigDecimal, this fast-path algorithm
     * is applied when a double value has a magnitude less than Integer.MAX_VALUE
     * and rounding is to nearest even and the destination format has two or
     * three digits of *scale* (digits after the decimal point).
     *
     * Under a rounding to nearest even policy, the returned result is a digit
     * string of a number in the (in this case decimal) destination format
     * closest to the exact numerical value of the (in this case binary) input
     * value.  If two destination format numbers are equally distant, the one
     * with the last digit even is returned.  To compute such a correctly rounded
     * value, some information about digits beyond the smallest returned digit
     * position needs to be consulted.
     *
     * In general, a guard digit, a round digit, and a sticky *bit* are needed
     * beyond the returned digit position.  If the discarded portion of the input
     * is sufficiently large, the returned digit string is incremented.  In round
     * to nearest even, this threshold to increment occurs near the half-way
     * point between digits.  The sticky bit records if there are any remaining
     * trailing digits of the exact input value in the new format; the sticky bit
     * is consulted only in close to half-way rounding cases.
     *
     * Given the computation of the digit and bit values, rounding is then
     * reduced to a table lookup problem.  For decimal, the even/odd cases look
     * like this:
     *
     * Last   Round   Sticky
     * 6      5       0      => 6   // exactly halfway, return even digit.
     * 6      5       1      => 7   // a little bit more than halfway, round up.
     * 7      5       0      => 8   // exactly halfway, round up to even.
     * 7      5       1      => 8   // a little bit more than halfway, round up.
     * With analogous entries for other even and odd last-returned digits.
     *
     * However, decimal negative powers of 5 smaller than 0.5 are *not* exactly
     * representable as binary fraction.  In particular, 0.005 (the round limit
     * for a two-digit scale) and 0.0005 (the round limit for a three-digit
     * scale) are not representable. Therefore, for input values near these cases
     * the sticky bit is known to be set which reduces the rounding logic to:
     *
     * Last   Round   Sticky
     * 6      5       1      => 7   // a little bit more than halfway, round up.
     * 7      5       1      => 8   // a little bit more than halfway, round up.
     *
     * In other words, if the round digit is 5, the sticky bit is known to be
     * set.  If the round digit is something other than 5, the sticky bit is not
     * relevant.  Therefore, some of the logic about whether or not to increment
     * the destination *decimal* value can occur based on tests of *binary*
     * computations of the binary input number.
     */

    /**
     * Check validity of using fast-path for this instance. If fast-path is valid
     * for this instance, sets fast-path state as true and initializes fast-path
     * utility fields as needed.
     *
     * This method is supposed to be called rarely, otherwise that will break the
     * fast-path performance. That means avoiding frequent changes of the
     * properties of the instance, since for most properties, each time a change
     * happens, a call to this method is needed at the next format call.
     *
     * FAST-PATH RULES:
     *  Similar to the default DecimalFormat instantiation case.
     *  More precisely:
     *  - HALF_EVEN rounding mode,
     *  - isGroupingUsed() is true,
     *  - groupingSize of 3,
     *  - multiplier is 1,
     *  - Decimal separator not mandatory,
     *  - No use of exponential notation,
     *  - minimumIntegerDigits is exactly 1 and maximumIntegerDigits at least 10
     *  - For number of fractional digits, the exact values found in the default case:
     *     Currency : min = max = 2.
     *     Decimal  : min = 0. max = 3.
     *
     */
    private boolean checkAndSetFastPathStatus() {

        boolean fastPathWasOn = isFastPath;

        if ((roundingMode == RoundingMode.HALF_EVEN) &&
            (isGroupingUsed()) &&
            (groupingSize == 3) &&
            (multiplier == 1) &&
            (!decimalSeparatorAlwaysShown) &&
            (!useExponentialNotation)) {

            // The fast-path algorithm is semi-hardcoded against
            //  minimumIntegerDigits and maximumIntegerDigits.
            isFastPath = ((minimumIntegerDigits == 1) &&
                          (maximumIntegerDigits >= 10));

            // The fast-path algorithm is hardcoded against
            //  minimumFractionDigits and maximumFractionDigits.
            if (isFastPath) {
                if (isCurrencyFormat) {
                    if ((minimumFractionDigits != 2) ||
                        (maximumFractionDigits != 2))
                        isFastPath = false;
                } else if ((minimumFractionDigits != 0) ||
                           (maximumFractionDigits != 3))
                    isFastPath = false;
            }
        } else
            isFastPath = false;

        resetFastPathData(fastPathWasOn);
        fastPathCheckNeeded = false;

        /*
         * Returns true after successfully checking the fast path condition and
         * setting the fast path data. The return value is used by the
         * fastFormat() method to decide whether to call the resetFastPathData
         * method to reinitialize fast path data or is it already initialized
         * in this method.
         */
        return true;
    }

    private void resetFastPathData(boolean fastPathWasOn) {
        // Since some instance properties may have changed while still falling
        // in the fast-path case, we need to reinitialize fastPathData anyway.
        if (isFastPath) {
            // We need to instantiate fastPathData if not already done.
            if (fastPathData == null) {
                fastPathData = new FastPathData();
            }

            // Sets up the locale specific constants used when formatting.
            // '0' is our default representation of zero.
            fastPathData.zeroDelta = symbols.getZeroDigit() - '0';
            fastPathData.groupingChar = isCurrencyFormat ?
                    symbols.getMonetaryGroupingSeparator() :
                    symbols.getGroupingSeparator();

            // Sets up fractional constants related to currency/decimal pattern.
            fastPathData.fractionalMaxIntBound = (isCurrencyFormat)
                    ? 99 : 999;
            fastPathData.fractionalScaleFactor = (isCurrencyFormat)
                    ? 100.0d : 1000.0d;

            // Records the need for adding prefix or suffix
            fastPathData.positiveAffixesRequired
                    = !positivePrefix.isEmpty() || !positiveSuffix.isEmpty();
            fastPathData.negativeAffixesRequired
                    = !negativePrefix.isEmpty() || !negativeSuffix.isEmpty();

            // Creates a cached char container for result, with max possible size.
            int maxNbIntegralDigits = 10;
            int maxNbGroups = 3;
            int containerSize
                    = Math.max(positivePrefix.length(), negativePrefix.length())
                    + maxNbIntegralDigits + maxNbGroups + 1
                    + maximumFractionDigits
                    + Math.max(positiveSuffix.length(), negativeSuffix.length());

            fastPathData.fastPathContainer = new char[containerSize];

            // Sets up prefix and suffix char arrays constants.
            fastPathData.charsPositiveSuffix = positiveSuffix.toCharArray();
            fastPathData.charsNegativeSuffix = negativeSuffix.toCharArray();
            fastPathData.charsPositivePrefix = positivePrefix.toCharArray();
            fastPathData.charsNegativePrefix = negativePrefix.toCharArray();

            // Sets up fixed index positions for integral and fractional digits.
            // Sets up decimal point in cached result container.
            int longestPrefixLength
                    = Math.max(positivePrefix.length(),
                            negativePrefix.length());
            int decimalPointIndex
                    = maxNbIntegralDigits + maxNbGroups + longestPrefixLength;

            fastPathData.integralLastIndex = decimalPointIndex - 1;
            fastPathData.fractionalFirstIndex = decimalPointIndex + 1;
            fastPathData.fastPathContainer[decimalPointIndex]
                    = isCurrencyFormat
                            ? symbols.getMonetaryDecimalSeparator()
                            : symbols.getDecimalSeparator();

        } else if (fastPathWasOn) {
            // Previous state was fast-path and is no more.
            // Resets cached array constants.
            fastPathData.fastPathContainer = null;
            fastPathData.charsPositiveSuffix = null;
            fastPathData.charsNegativeSuffix = null;
            fastPathData.charsPositivePrefix = null;
            fastPathData.charsNegativePrefix = null;
        }
    }

    /**
     * Returns true if rounding-up must be done on {@code scaledFractionalPartAsInt},
     * false otherwise.
     *
     * This is a utility method that takes correct half-even rounding decision on
     * passed fractional value at the scaled decimal point (2 digits for currency
     * case and 3 for decimal case), when the approximated fractional part after
     * scaled decimal point is exactly 0.5d.  This is done by means of exact
     * calculations on the {@code fractionalPart} floating-point value.
     *
     * This method is supposed to be called by private {@code fastDoubleFormat}
     * method only.
     *
     * The algorithms used for the exact calculations are :
     *
     * The <b><i>FastTwoSum</i></b> algorithm, from T.J.Dekker, described in the
     * papers  "<i>A  Floating-Point   Technique  for  Extending  the  Available
     * Precision</i>"  by Dekker, and  in "<i>Adaptive  Precision Floating-Point
     * Arithmetic and Fast Robust Geometric Predicates</i>" from J.Shewchuk.
     *
     * A modified version of <b><i>Sum2S</i></b> cascaded summation described in
     * "<i>Accurate Sum and Dot Product</i>" from Takeshi Ogita and All.  As
     * Ogita says in this paper this is an equivalent of the Kahan-Babuska's
     * summation algorithm because we order the terms by magnitude before summing
     * them. For this reason we can use the <i>FastTwoSum</i> algorithm rather
     * than the more expensive Knuth's <i>TwoSum</i>.
     *
     * We do this to avoid a more expensive exact "<i>TwoProduct</i>" algorithm,
     * like those described in Shewchuk's paper above. See comments in the code
     * below.
     *
     * @param  fractionalPart The  fractional value  on which  we  take rounding
     * decision.
     * @param scaledFractionalPartAsInt The integral part of the scaled
     * fractional value.
     *
     * @return the decision that must be taken regarding half-even rounding.
     */
    private boolean exactRoundUp(double fractionalPart,
                                 int scaledFractionalPartAsInt) {

        /* exactRoundUp() method is called by fastDoubleFormat() only.
         * The precondition expected to be verified by the passed parameters is :
         * scaledFractionalPartAsInt ==
         *     (int) (fractionalPart * fastPathData.fractionalScaleFactor).
         * This is ensured by fastDoubleFormat() code.
         */

        /* We first calculate roundoff error made by fastDoubleFormat() on
         * the scaled fractional part. We do this with exact calculation on the
         * passed fractionalPart. Rounding decision will then be taken from roundoff.
         */

        /* ---- TwoProduct(fractionalPart, scale factor (i.e. 1000.0d or 100.0d)).
         *
         * The below is an optimized exact "TwoProduct" calculation of passed
         * fractional part with scale factor, using Ogita's Sum2S cascaded
         * summation adapted as Kahan-Babuska equivalent by using FastTwoSum
         * (much faster) rather than Knuth's TwoSum.
         *
         * We can do this because we order the summation from smallest to
         * greatest, so that FastTwoSum can be used without any additional error.
         *
         * The "TwoProduct" exact calculation needs 17 flops. We replace this by
         * a cascaded summation of FastTwoSum calculations, each involving an
         * exact multiply by a power of 2.
         *
         * Doing so saves overall 4 multiplications and 1 addition compared to
         * using traditional "TwoProduct".
         *
         * The scale factor is either 100 (currency case) or 1000 (decimal case).
         * - when 1000, we replace it by (1024 - 16 - 8) = 1000.
         * - when 100,  we replace it by (128  - 32 + 4) =  100.
         * Every multiplication by a power of 2 (1024, 128, 32, 16, 8, 4) is exact.
         *
         */
        double approxMax;    // Will always be positive.
        double approxMedium; // Will always be negative.
        double approxMin;

        double fastTwoSumApproximation = 0.0d;
        double fastTwoSumRoundOff = 0.0d;
        double bVirtual = 0.0d;

        if (isCurrencyFormat) {
            // Scale is 100 = 128 - 32 + 4.
            // Multiply by 2**n is a shift. No roundoff. No error.
            approxMax    = fractionalPart * 128.00d;
            approxMedium = - (fractionalPart * 32.00d);
            approxMin    = fractionalPart * 4.00d;
        } else {
            // Scale is 1000 = 1024 - 16 - 8.
            // Multiply by 2**n is a shift. No roundoff. No error.
            approxMax    = fractionalPart * 1024.00d;
            approxMedium = - (fractionalPart * 16.00d);
            approxMin    = - (fractionalPart * 8.00d);
        }

        // Shewchuk/Dekker's FastTwoSum(approxMedium, approxMin).
        assert(-approxMedium >= Math.abs(approxMin));
        fastTwoSumApproximation = approxMedium + approxMin;
        bVirtual = fastTwoSumApproximation - approxMedium;
        fastTwoSumRoundOff = approxMin - bVirtual;
        double approxS1 = fastTwoSumApproximation;
        double roundoffS1 = fastTwoSumRoundOff;

        // Shewchuk/Dekker's FastTwoSum(approxMax, approxS1);
        assert(approxMax >= Math.abs(approxS1));
        fastTwoSumApproximation = approxMax + approxS1;
        bVirtual = fastTwoSumApproximation - approxMax;
        fastTwoSumRoundOff = approxS1 - bVirtual;
        double roundoff1000 = fastTwoSumRoundOff;
        double approx1000 = fastTwoSumApproximation;
        double roundoffTotal = roundoffS1 + roundoff1000;

        // Shewchuk/Dekker's FastTwoSum(approx1000, roundoffTotal);
        assert(approx1000 >= Math.abs(roundoffTotal));
        fastTwoSumApproximation = approx1000 + roundoffTotal;
        bVirtual = fastTwoSumApproximation - approx1000;

        // Now we have got the roundoff for the scaled fractional
        double scaledFractionalRoundoff = roundoffTotal - bVirtual;

        // ---- TwoProduct(fractionalPart, scale (i.e. 1000.0d or 100.0d)) end.

        /* ---- Taking the rounding decision
         *
         * We take rounding decision based on roundoff and half-even rounding
         * rule.
         *
         * The above TwoProduct gives us the exact roundoff on the approximated
         * scaled fractional, and we know that this approximation is exactly
         * 0.5d, since that has already been tested by the caller
         * (fastDoubleFormat).
         *
         * Decision comes first from the sign of the calculated exact roundoff.
         * - Since being exact roundoff, it cannot be positive with a scaled
         *   fractional less than 0.5d, as well as negative with a scaled
         *   fractional greater than 0.5d. That leaves us with following 3 cases.
         * - positive, thus scaled fractional == 0.500....0fff ==> round-up.
         * - negative, thus scaled fractional == 0.499....9fff ==> don't round-up.
         * - is zero,  thus scaled fractioanl == 0.5 ==> half-even rounding applies :
         *    we round-up only if the integral part of the scaled fractional is odd.
         *
         */
        if (scaledFractionalRoundoff > 0.0) {
            return true;
        } else if (scaledFractionalRoundoff < 0.0) {
            return false;
        } else if ((scaledFractionalPartAsInt & 1) != 0) {
            return true;
        }

        return false;

        // ---- Taking the rounding decision end
    }

    /**
     * Collects integral digits from passed {@code number}, while setting
     * grouping chars as needed. Updates {@code firstUsedIndex} accordingly.
     *
     * Loops downward starting from {@code backwardIndex} position (inclusive).
     *
     * @param number  The int value from which we collect digits.
     * @param digitsBuffer The char array container where digits and grouping chars
     *  are stored.
     * @param backwardIndex the position from which we start storing digits in
     *  digitsBuffer.
     *
     */
    private void collectIntegralDigits(int number,
                                       char[] digitsBuffer,
                                       int backwardIndex) {
        int index = backwardIndex;
        int q;
        int r;
        while (number > 999) {
            // Generates 3 digits per iteration.
            q = number / 1000;
            r = number - (q << 10) + (q << 4) + (q << 3); // -1024 +16 +8 = 1000.
            number = q;

            digitsBuffer[index--] = DigitArrays.DigitOnes1000[r];
            digitsBuffer[index--] = DigitArrays.DigitTens1000[r];
            digitsBuffer[index--] = DigitArrays.DigitHundreds1000[r];
            digitsBuffer[index--] = fastPathData.groupingChar;
        }

        // Collects last 3 or less digits.
        digitsBuffer[index] = DigitArrays.DigitOnes1000[number];
        if (number > 9) {
            digitsBuffer[--index]  = DigitArrays.DigitTens1000[number];
            if (number > 99)
                digitsBuffer[--index]   = DigitArrays.DigitHundreds1000[number];
        }

        fastPathData.firstUsedIndex = index;
    }

    /**
     * Collects the 2 (currency) or 3 (decimal) fractional digits from passed
     * {@code number}, starting at {@code startIndex} position
     * inclusive.  There is no punctuation to set here (no grouping chars).
     * Updates {@code fastPathData.lastFreeIndex} accordingly.
     *
     *
     * @param number  The int value from which we collect digits.
     * @param digitsBuffer The char array container where digits are stored.
     * @param startIndex the position from which we start storing digits in
     *  digitsBuffer.
     *
     */
    private void collectFractionalDigits(int number,
                                         char[] digitsBuffer,
                                         int startIndex) {
        int index = startIndex;

        char digitOnes = DigitArrays.DigitOnes1000[number];
        char digitTens = DigitArrays.DigitTens1000[number];

        if (isCurrencyFormat) {
            // Currency case. Always collects fractional digits.
            digitsBuffer[index++] = digitTens;
            digitsBuffer[index++] = digitOnes;
        } else if (number != 0) {
            // Decimal case. Hundreds will always be collected
            digitsBuffer[index++] = DigitArrays.DigitHundreds1000[number];

            // Ending zeros won't be collected.
            if (digitOnes != '0') {
                digitsBuffer[index++] = digitTens;
                digitsBuffer[index++] = digitOnes;
            } else if (digitTens != '0')
                digitsBuffer[index++] = digitTens;

        } else
            // This is decimal pattern and fractional part is zero.
            // We must remove decimal point from result.
            index--;

        fastPathData.lastFreeIndex = index;
    }

    /**
     * Internal utility.
     * Adds the passed {@code prefix} and {@code suffix} to {@code container}.
     *
     * @param container  Char array container which to prepend/append the
     *  prefix/suffix.
     * @param prefix     Char sequence to prepend as a prefix.
     * @param suffix     Char sequence to append as a suffix.
     *
     */
    //    private void addAffixes(boolean isNegative, char[] container) {
    private void addAffixes(char[] container, char[] prefix, char[] suffix) {

        // We add affixes only if needed (affix length > 0).
        int pl = prefix.length;
        int sl = suffix.length;
        if (pl != 0) prependPrefix(prefix, pl, container);
        if (sl != 0) appendSuffix(suffix, sl, container);

    }

    /**
     * Prepends the passed {@code prefix} chars to given result
     * {@code container}.  Updates {@code fastPathData.firstUsedIndex}
     * accordingly.
     *
     * @param prefix The prefix characters to prepend to result.
     * @param len The number of chars to prepend.
     * @param container Char array container which to prepend the prefix
     */
    private void prependPrefix(char[] prefix,
                               int len,
                               char[] container) {

        fastPathData.firstUsedIndex -= len;
        int startIndex = fastPathData.firstUsedIndex;

        // If prefix to prepend is only 1 char long, just assigns this char.
        // If prefix is less or equal 4, we use a dedicated algorithm that
        //  has shown to run faster than System.arraycopy.
        // If more than 4, we use System.arraycopy.
        if (len == 1)
            container[startIndex] = prefix[0];
        else if (len <= 4) {
            int dstLower = startIndex;
            int dstUpper = dstLower + len - 1;
            int srcUpper = len - 1;
            container[dstLower] = prefix[0];
            container[dstUpper] = prefix[srcUpper];

            if (len > 2)
                container[++dstLower] = prefix[1];
            if (len == 4)
                container[--dstUpper] = prefix[2];
        } else
            System.arraycopy(prefix, 0, container, startIndex, len);
    }

    /**
     * Appends the passed {@code suffix} chars to given result
     * {@code container}.  Updates {@code fastPathData.lastFreeIndex}
     * accordingly.
     *
     * @param suffix The suffix characters to append to result.
     * @param len The number of chars to append.
     * @param container Char array container which to append the suffix
     */
    private void appendSuffix(char[] suffix,
                              int len,
                              char[] container) {

        int startIndex = fastPathData.lastFreeIndex;

        // If suffix to append is only 1 char long, just assigns this char.
        // If suffix is less or equal 4, we use a dedicated algorithm that
        //  has shown to run faster than System.arraycopy.
        // If more than 4, we use System.arraycopy.
        if (len == 1)
            container[startIndex] = suffix[0];
        else if (len <= 4) {
            int dstLower = startIndex;
            int dstUpper = dstLower + len - 1;
            int srcUpper = len - 1;
            container[dstLower] = suffix[0];
            container[dstUpper] = suffix[srcUpper];

            if (len > 2)
                container[++dstLower] = suffix[1];
            if (len == 4)
                container[--dstUpper] = suffix[2];
        } else
            System.arraycopy(suffix, 0, container, startIndex, len);

        fastPathData.lastFreeIndex += len;
    }

    /**
     * Converts digit chars from {@code digitsBuffer} to current locale.
     *
     * Must be called before adding affixes since we refer to
     * {@code fastPathData.firstUsedIndex} and {@code fastPathData.lastFreeIndex},
     * and do not support affixes (for speed reason).
     *
     * We loop backward starting from last used index in {@code fastPathData}.
     *
     * @param digitsBuffer The char array container where the digits are stored.
     */
    private void localizeDigits(char[] digitsBuffer) {

        // We will localize only the digits, using the groupingSize,
        // and taking into account fractional part.

        // First take into account fractional part.
        int digitsCounter =
            fastPathData.lastFreeIndex - fastPathData.fractionalFirstIndex;

        // The case when there is no fractional digits.
        if (digitsCounter < 0)
            digitsCounter = groupingSize;

        // Only the digits remains to localize.
        for (int cursor = fastPathData.lastFreeIndex - 1;
             cursor >= fastPathData.firstUsedIndex;
             cursor--) {
            if (digitsCounter != 0) {
                // This is a digit char, we must localize it.
                digitsBuffer[cursor] += (char)fastPathData.zeroDelta;
                digitsCounter--;
            } else {
                // Decimal separator or grouping char. Reinit counter only.
                digitsCounter = groupingSize;
            }
        }
    }

    /**
     * This is the main entry point for the fast-path format algorithm.
     *
     * At this point we are sure to be in the expected conditions to run it.
     * This algorithm builds the formatted result and puts it in the dedicated
     * {@code fastPathData.fastPathContainer}.
     *
     * @param d the double value to be formatted.
     * @param negative Flag precising if {@code d} is negative.
     */
    private void fastDoubleFormat(double d,
                                  boolean negative) {

        char[] container = fastPathData.fastPathContainer;

        /*
         * The principle of the algorithm is to :
         * - Break the passed double into its integral and fractional parts
         *    converted into integers.
         * - Then decide if rounding up must be applied or not by following
         *    the half-even rounding rule, first using approximated scaled
         *    fractional part.
         * - For the difficult cases (approximated scaled fractional part
         *    being exactly 0.5d), we refine the rounding decision by calling
         *    exactRoundUp utility method that both calculates the exact roundoff
         *    on the approximation and takes correct rounding decision.
         * - We round-up the fractional part if needed, possibly propagating the
         *    rounding to integral part if we meet a "all-nine" case for the
         *    scaled fractional part.
         * - We then collect digits from the resulting integral and fractional
         *   parts, also setting the required grouping chars on the fly.
         * - Then we localize the collected digits if needed, and
         * - Finally prepend/append prefix/suffix if any is needed.
         */

        // Exact integral part of d.
        int integralPartAsInt = (int) d;

        // Exact fractional part of d (since we subtract it's integral part).
        double exactFractionalPart = d - (double) integralPartAsInt;

        // Approximated scaled fractional part of d (due to multiplication).
        double scaledFractional =
            exactFractionalPart * fastPathData.fractionalScaleFactor;

        // Exact integral part of scaled fractional above.
        int fractionalPartAsInt = (int) scaledFractional;

        // Exact fractional part of scaled fractional above.
        scaledFractional = scaledFractional - (double) fractionalPartAsInt;

        // Only when scaledFractional is exactly 0.5d do we have to do exact
        // calculations and take fine-grained rounding decision, since
        // approximated results above may lead to incorrect decision.
        // Otherwise comparing against 0.5d (strictly greater or less) is ok.
        boolean roundItUp = false;
        if (scaledFractional >= 0.5d) {
            if (scaledFractional == 0.5d)
                // Rounding need fine-grained decision.
                roundItUp = exactRoundUp(exactFractionalPart, fractionalPartAsInt);
            else
                roundItUp = true;

            if (roundItUp) {
                // Rounds up both fractional part (and also integral if needed).
                if (fractionalPartAsInt < fastPathData.fractionalMaxIntBound) {
                    fractionalPartAsInt++;
                } else {
                    // Propagates rounding to integral part since "all nines" case.
                    fractionalPartAsInt = 0;
                    integralPartAsInt++;
                }
            }
        }

        // Collecting digits.
        collectFractionalDigits(fractionalPartAsInt, container,
                                fastPathData.fractionalFirstIndex);
        collectIntegralDigits(integralPartAsInt, container,
                              fastPathData.integralLastIndex);

        // Localizing digits.
        if (fastPathData.zeroDelta != 0)
            localizeDigits(container);

        // Adding prefix and suffix.
        if (negative) {
            if (fastPathData.negativeAffixesRequired)
                addAffixes(container,
                           fastPathData.charsNegativePrefix,
                           fastPathData.charsNegativeSuffix);
        } else if (fastPathData.positiveAffixesRequired)
            addAffixes(container,
                       fastPathData.charsPositivePrefix,
                       fastPathData.charsPositiveSuffix);
    }

    /**
     * A fast-path shortcut of format(double) to be called by NumberFormat, or by
     * format(double, ...) public methods.
     *
     * If instance can be applied fast-path and passed double is not NaN or
     * Infinity, is in the integer range, we call {@code fastDoubleFormat}
     * after changing {@code d} to its positive value if necessary.
     *
     * Otherwise returns null by convention since fast-path can't be exercized.
     *
     * @param d The double value to be formatted
     *
     * @return the formatted result for {@code d} as a string.
     */
    String fastFormat(double d) {
        boolean isDataSet = false;
        // (Re-)Evaluates fast-path status if needed.
        if (fastPathCheckNeeded) {
            isDataSet = checkAndSetFastPathStatus();
        }

        if (!isFastPath )
            // DecimalFormat instance is not in a fast-path state.
            return null;

        if (!Double.isFinite(d))
            // Should not use fast-path for Infinity and NaN.
            return null;

        // Extracts and records sign of double value, possibly changing it
        // to a positive one, before calling fastDoubleFormat().
        boolean negative = false;
        if (d < 0.0d) {
            negative = true;
            d = -d;
        } else if (d == 0.0d) {
            negative = (Math.copySign(1.0d, d) == -1.0d);
            d = +0.0d;
        }

        if (d > MAX_INT_AS_DOUBLE)
            // Filters out values that are outside expected fast-path range
            return null;
        else {
            if (!isDataSet) {
                /*
                 * If the fast path data is not set through
                 * checkAndSetFastPathStatus() and fulfil the
                 * fast path conditions then reset the data
                 * directly through resetFastPathData()
                 */
                resetFastPathData(isFastPath);
            }
            fastDoubleFormat(d, negative);

        }


        // Returns a new string from updated fastPathContainer.
        return new String(fastPathData.fastPathContainer,
                          fastPathData.firstUsedIndex,
                          fastPathData.lastFreeIndex - fastPathData.firstUsedIndex);

    }

    /**
     * Utility method that sets the {@code DigitList} used by this {@code DecimalFormat}
     * instance.
     *
     * @param number the number to format
     * @param isNegative true, if the number is negative; false otherwise
     * @param maxDigits the max digits
     * @throws AssertionError if provided a Number subclass that is not supported
     *         by {@code DigitList}
     */
    void setDigitList(Number number, boolean isNegative, int maxDigits) {
        switch (number) {
            case Double d -> digitList.set(isNegative, d, maxDigits, true);
            case BigDecimal bd -> digitList.set(isNegative, bd, maxDigits, true);
            case Long l -> digitList.set(isNegative, l, maxDigits);
            case BigInteger bi -> digitList.set(isNegative, bi, maxDigits);
            default -> throw new AssertionError(
                    String.format("DigitList does not support %s", number.getClass().getName()));
        }
    }

    /**
     * {@return the {@code DigitList} used by this {@code DecimalFormat} instance}
     * Declared as package-private, intended for {@code CompactNumberFormat}.
     */
    DigitList getDigitList() {
        return digitList;
    }

    // ======== End fast-path formatting logic for double =========================

    /**
     * Complete the formatting of a finite number.  On entry, the digitList must
     * be filled in with the correct digits.
     */
    private StringBuf subformat(StringBuf result, FieldDelegate delegate,
            boolean isNegative, boolean isInteger,
            int maxIntDigits, int minIntDigits,
            int maxFraDigits, int minFraDigits) {

        // Process prefix
        if (isNegative) {
            append(result, negativePrefix, delegate,
                    getNegativePrefixFieldPositions(), Field.SIGN);
        } else {
            append(result, positivePrefix, delegate,
                    getPositivePrefixFieldPositions(), Field.SIGN);
        }

        // Process number
        subformatNumber(result, delegate, isNegative, isInteger,
                maxIntDigits, minIntDigits, maxFraDigits, minFraDigits);

        // Process suffix
        if (isNegative) {
            append(result, negativeSuffix, delegate,
                    getNegativeSuffixFieldPositions(), Field.SIGN);
        } else {
            append(result, positiveSuffix, delegate,
                    getPositiveSuffixFieldPositions(), Field.SIGN);
        }

        return result;
    }

    /**
     * Subformats number part using the {@code DigitList} of this
     * {@code DecimalFormat} instance.
     * @param result where the text is to be appended
     * @param delegate notified of the location of sub fields
     * @param isNegative true, if the number is negative; false otherwise
     * @param isInteger true, if the number is an integer; false otherwise
     * @param maxIntDigits maximum integer digits
     * @param minIntDigits minimum integer digits
     * @param maxFraDigits maximum fraction digits
     * @param minFraDigits minimum fraction digits
     */
    void subformatNumber(StringBuf result, FieldDelegate delegate,
            boolean isNegative, boolean isInteger,
            int maxIntDigits, int minIntDigits,
            int maxFraDigits, int minFraDigits) {

        char grouping = isCurrencyFormat ?
                symbols.getMonetaryGroupingSeparator() :
                symbols.getGroupingSeparator();
        char zero = symbols.getZeroDigit();
        int zeroDelta = zero - '0'; // '0' is the DigitList representation of zero

        char decimal = isCurrencyFormat ?
                symbols.getMonetaryDecimalSeparator() :
                symbols.getDecimalSeparator();

        /* Per bug 4147706, DecimalFormat must respect the sign of numbers which
         * format as zero.  This allows sensible computations and preserves
         * relations such as signum(1/x) = signum(x), where x is +Infinity or
         * -Infinity.  Prior to this fix, we always formatted zero values as if
         * they were positive.  Liu 7/6/98.
         */
        if (digitList.isZero()) {
            digitList.decimalAt = 0; // Normalize
        }

        if (useExponentialNotation) {
            int iFieldStart = result.length();
            int iFieldEnd = -1;
            int fFieldStart = -1;

            // Minimum integer digits are handled in exponential format by
            // adjusting the exponent.  For example, 0.01234 with 3 minimum
            // integer digits is "123.4E-4".
            // Maximum integer digits are interpreted as indicating the
            // repeating range.  This is useful for engineering notation, in
            // which the exponent is restricted to a multiple of 3.  For
            // example, 0.01234 with 3 maximum integer digits is "12.34e-3".
            // If maximum integer digits are > 1 and are larger than
            // minimum integer digits, then minimum integer digits are
            // ignored.
            int exponent = digitList.decimalAt;
            int repeat = maxIntDigits;
            int minimumIntegerDigits = minIntDigits;
            if (repeat > 1 && repeat > minIntDigits) {
                // A repeating range is defined; adjust to it as follows.
                // If repeat == 3, we have 6,5,4=>3; 3,2,1=>0; 0,-1,-2=>-3;
                // -3,-4,-5=>-6, etc. This takes into account that the
                // exponent we have here is off by one from what we expect;
                // it is for the format 0.MMMMMx10^n.
                if (exponent >= 1) {
                    exponent = ((exponent - 1) / repeat) * repeat;
                } else {
                    // integer division rounds towards 0
                    exponent = ((exponent - repeat) / repeat) * repeat;
                }
                minimumIntegerDigits = 1;
            } else {
                // No repeating range is defined; use minimum integer digits.
                exponent -= minimumIntegerDigits;
            }

            // We now output a minimum number of digits, and more if there
            // are more digits, up to the maximum number of digits.  We
            // place the decimal point after the "integer" digits, which
            // are the first (decimalAt - exponent) digits.
            int minimumDigits = minIntDigits + minFraDigits;
            if (minimumDigits < 0) {    // overflow?
                minimumDigits = Integer.MAX_VALUE;
            }

            // The number of integer digits is handled specially if the number
            // is zero, since then there may be no digits.
            int integerDigits = digitList.isZero() ? minimumIntegerDigits :
                    digitList.decimalAt - exponent;
            if (minimumDigits < integerDigits) {
                minimumDigits = integerDigits;
            }
            int totalDigits = digitList.count;
            if (minimumDigits > totalDigits) {
                totalDigits = minimumDigits;
            }
            boolean addedDecimalSeparator = false;

            for (int i=0; i<totalDigits; ++i) {
                if (i == integerDigits) {
                    // Record field information for caller.
                    iFieldEnd = result.length();

                    result.append(decimal);
                    addedDecimalSeparator = true;

                    // Record field information for caller.
                    fFieldStart = result.length();
                }
                result.append((i < digitList.count) ?
                        (char)(digitList.digits[i] + zeroDelta) :
                        zero);
            }

            if (decimalSeparatorAlwaysShown && totalDigits == integerDigits) {
                // Record field information for caller.
                iFieldEnd = result.length();

                result.append(decimal);
                addedDecimalSeparator = true;

                // Record field information for caller.
                fFieldStart = result.length();
            }

            // Record field information
            if (iFieldEnd == -1) {
                iFieldEnd = result.length();
            }
            delegate.formatted(INTEGER_FIELD, Field.INTEGER, Field.INTEGER,
                    iFieldStart, iFieldEnd, result);
            if (addedDecimalSeparator) {
                delegate.formatted(Field.DECIMAL_SEPARATOR,
                        Field.DECIMAL_SEPARATOR,
                        iFieldEnd, fFieldStart, result);
            }
            if (fFieldStart == -1) {
                fFieldStart = result.length();
            }
            delegate.formatted(FRACTION_FIELD, Field.FRACTION, Field.FRACTION,
                    fFieldStart, result.length(), result);

            // The exponent is output using the pattern-specified minimum
            // exponent digits.  There is no maximum limit to the exponent
            // digits, since truncating the exponent would result in an
            // unacceptable inaccuracy.
            int fieldStart = result.length();

            result.append(symbols.getExponentSeparator());

            delegate.formatted(Field.EXPONENT_SYMBOL, Field.EXPONENT_SYMBOL,
                    fieldStart, result.length(), result);

            // For zero values, we force the exponent to zero.  We
            // must do this here, and not earlier, because the value
            // is used to determine integer digit count above.
            if (digitList.isZero()) {
                exponent = 0;
            }

            boolean negativeExponent = exponent < 0;
            if (negativeExponent) {
                exponent = -exponent;
                fieldStart = result.length();
                result.append(symbols.getMinusSignText());
                delegate.formatted(Field.EXPONENT_SIGN, Field.EXPONENT_SIGN,
                        fieldStart, result.length(), result);
            }
            digitList.set(negativeExponent, exponent);

            int eFieldStart = result.length();

            for (int i=digitList.decimalAt; i<minExponentDigits; ++i) {
                result.append(zero);
            }
            for (int i=0; i<digitList.decimalAt; ++i) {
                result.append((i < digitList.count) ?
                        (char)(digitList.digits[i] + zeroDelta) : zero);
            }
            delegate.formatted(Field.EXPONENT, Field.EXPONENT, eFieldStart,
                    result.length(), result);
        } else {
            int iFieldStart = result.length();

            // Output the integer portion.  Here 'count' is the total
            // number of integer digits we will display, including both
            // leading zeros required to satisfy getMinimumIntegerDigits,
            // and actual digits present in the number.
            int count = minIntDigits;
            int digitIndex = 0; // Index into digitList.fDigits[]
            if (digitList.decimalAt > 0 && count < digitList.decimalAt) {
                count = digitList.decimalAt;
            }

            // Handle the case where getMaximumIntegerDigits() is smaller
            // than the real number of integer digits.  If this is so, we
            // output the least significant max integer digits.  For example,
            // the value 1997 printed with 2 max integer digits is just "97".
            if (count > maxIntDigits) {
                count = maxIntDigits;
                digitIndex = digitList.decimalAt - count;
            }

            int sizeBeforeIntegerPart = result.length();
            for (int i=count-1; i>=0; --i) {
                if (i < digitList.decimalAt && digitIndex < digitList.count) {
                    // Output a real digit
                    result.append((char)(digitList.digits[digitIndex++] + zeroDelta));
                } else {
                    // Output a leading zero
                    result.append(zero);
                }

                // Output grouping separator if necessary.  Don't output a
                // grouping separator if i==0 though; that's at the end of
                // the integer part.
                if (isGroupingUsed() && i>0 && (groupingSize != 0) &&
                        (i % groupingSize == 0)) {
                    int gStart = result.length();
                    result.append(grouping);
                    delegate.formatted(Field.GROUPING_SEPARATOR,
                            Field.GROUPING_SEPARATOR, gStart,
                            result.length(), result);
                }
            }

            // Determine whether or not there are any printable fractional
            // digits.  If we've used up the digits we know there aren't.
            boolean fractionPresent = (minFraDigits > 0) ||
                    (!isInteger && digitIndex < digitList.count);

            // If there is no fraction present, and we haven't printed any
            // integer digits, then print a zero.  Otherwise we won't print
            // _any_ digits, and we won't be able to parse this string.
            if (!fractionPresent && result.length() == sizeBeforeIntegerPart) {
                result.append(zero);
            }

            delegate.formatted(INTEGER_FIELD, Field.INTEGER, Field.INTEGER,
                    iFieldStart, result.length(), result);

            // Output the decimal separator if we always do so.
            int sStart = result.length();
            if (decimalSeparatorAlwaysShown || fractionPresent) {
                result.append(decimal);
            }

            if (sStart != result.length()) {
                delegate.formatted(Field.DECIMAL_SEPARATOR,
                        Field.DECIMAL_SEPARATOR,
                        sStart, result.length(), result);
            }
            int fFieldStart = result.length();

            for (int i=0; i < maxFraDigits; ++i) {
                // Here is where we escape from the loop.  We escape if we've
                // output the maximum fraction digits (specified in the for
                // expression above).
                // We also stop when we've output the minimum digits and either:
                // we have an integer, so there is no fractional stuff to
                // display, or we're out of significant digits.
                if (i >= minFraDigits &&
                        (isInteger || digitIndex >= digitList.count)) {
                    break;
                }

                // Output leading fractional zeros. These are zeros that come
                // after the decimal but before any significant digits. These
                // are only output if abs(number being formatted) < 1.0.
                if (-1-i > (digitList.decimalAt-1)) {
                    result.append(zero);
                    continue;
                }

                // Output a digit, if we have any precision left, or a
                // zero if we don't.  We don't want to output noise digits.
                if (!isInteger && digitIndex < digitList.count) {
                    result.append((char)(digitList.digits[digitIndex++] + zeroDelta));
                } else {
                    result.append(zero);
                }
            }

            // Record field information for caller.
            delegate.formatted(FRACTION_FIELD, Field.FRACTION, Field.FRACTION,
                    fFieldStart, result.length(), result);
        }
    }

    /**
     * Appends the String {@code string} to {@code result}.
     * {@code delegate} is notified of all  the
     * {@code FieldPosition}s in {@code positions}.
     * <p>
     * If one of the {@code FieldPosition}s in {@code positions}
     * identifies a {@code SIGN} attribute, it is mapped to
     * {@code signAttribute}. This is used
     * to map the {@code SIGN} attribute to the {@code EXPONENT}
     * attribute as necessary.
     * <p>
     * This is used by {@code subformat} to add the prefix/suffix.
     */
    private void append(StringBuf result, String string,
                        FieldDelegate delegate,
                        FieldPosition[] positions,
                        Format.Field signAttribute) {
        int start = result.length();

        if (!string.isEmpty()) {
            result.append(string);
            for (int counter = 0, max = positions.length; counter < max;
                 counter++) {
                FieldPosition fp = positions[counter];
                Format.Field attribute = fp.getFieldAttribute();

                if (attribute == Field.SIGN) {
                    attribute = signAttribute;
                }
                delegate.formatted(attribute, attribute,
                                   start + fp.getBeginIndex(),
                                   start + fp.getEndIndex(), result);
            }
        }
    }

    /**
     * {@inheritDoc NumberFormat}
     * <p>
     * Parsing can be done in either a strict or lenient manner, by default it is lenient.
     * <p>
     * Parsing fails when <b>lenient</b>, if the prefix and/or suffix are non-empty
     * and cannot be found due to parsing ending early, or the first character
     * after the prefix cannot be parsed.
     * <p>
     * Parsing fails when <b>strict</b>, if in {@code text},
     * <ul>
     *   <li> The prefix is not found. For example, a {@code Locale.US} currency
     *   format prefix: "{@code $}"
     *   <li> The suffix is not found. For example, a {@code Locale.US} percent
     *   format suffix: "{@code %}"
     *   <li> {@link #isGroupingUsed()} returns {@code true}, and {@link
     *   #getGroupingSize()} is not adhered to
     *   <li> {@link #isGroupingUsed()} returns {@code false}, and the grouping
     *   symbol is found
     *   <li> {@link #isGroupingUsed()} returns {@code true} and the grouping
     *   symbol occurs after the decimal separator
     *   <li> Any other characters are found, that are not the expected symbols,
     *   and are not digits that occur within the numerical portion
     * </ul>
     * <p>
     * The subclass returned depends on the value of {@link #isParseBigDecimal}
     * as well as on the string being parsed.
     * <ul>
     *   <li>If {@code isParseBigDecimal()} is false (the default),
     *       most integer values are returned as {@code Long}
     *       objects, no matter how they are written: {@code "17"} and
     *       {@code "17.000"} both parse to {@code Long(17)}.
     *       Values that cannot fit into a {@code Long} are returned as
     *       {@code Double}s. This includes values with a fractional part,
     *       infinite values, {@code NaN}, and the value -0.0.
     *       {@code DecimalFormat} does <em>not</em> decide whether to
     *       return a {@code Double} or a {@code Long} based on the
     *       presence of a decimal separator in the source string. Doing so
     *       would prevent integers that overflow the mantissa of a double,
     *       such as {@code "-9,223,372,036,854,775,808.00"}, from being
     *       parsed accurately.
     *       <p>
     *       Callers may use the {@code Number} methods
     *       {@code doubleValue}, {@code longValue}, etc., to obtain
     *       the type they want.
     *   <li>If {@code isParseBigDecimal()} is true, values are returned
     *       as {@code BigDecimal} objects. The values are the ones
     *       constructed by {@link java.math.BigDecimal#BigDecimal(String)}
     *       for corresponding strings in locale-independent format. The
     *       special cases negative and positive infinity and NaN are returned
     *       as {@code Double} instances holding the values of the
     *       corresponding {@code Double} constants.
     * </ul>
     * <p>
     * {@code DecimalFormat} parses all Unicode characters that represent
     * decimal digits, as defined by {@code Character.digit()}. In
     * addition, {@code DecimalFormat} also recognizes as digits the ten
     * consecutive characters starting with the localized zero digit defined in
     * the {@code DecimalFormatSymbols} object.
     *
     * @param text the string to be parsed
     * @param pos  A {@code ParsePosition} object with index and error
     *             index information as described above.
     * @return     the parsed value, or {@code null} if the parse fails
     * @throws     NullPointerException if {@code text} or
     *             {@code pos} is null.
     */
    @Override
    public Number parse(String text, ParsePosition pos) {
        // special case NaN
        if (text.regionMatches(pos.index, symbols.getNaN(), 0, symbols.getNaN().length())) {
            pos.index = pos.index + symbols.getNaN().length();
            return Double.valueOf(Double.NaN);
        }

        boolean[] status = new boolean[STATUS_LENGTH];
        if (!subparse(text, pos, positivePrefix, negativePrefix, digitList, false, status)) {
            return null;
        }

        // special case INFINITY
        if (status[STATUS_INFINITE]) {
            if (status[STATUS_POSITIVE] == (multiplier >= 0)) {
                return Double.valueOf(Double.POSITIVE_INFINITY);
            } else {
                return Double.valueOf(Double.NEGATIVE_INFINITY);
            }
        }

        if (multiplier == 0) {
            if (digitList.isZero()) {
                return Double.valueOf(Double.NaN);
            } else if (status[STATUS_POSITIVE]) {
                return Double.valueOf(Double.POSITIVE_INFINITY);
            } else {
                return Double.valueOf(Double.NEGATIVE_INFINITY);
            }
        }

        if (isParseBigDecimal()) {
            BigDecimal bigDecimalResult = digitList.getBigDecimal();

            if (multiplier != 1) {
                try {
                    bigDecimalResult = bigDecimalResult.divide(getBigDecimalMultiplier());
                }
                catch (ArithmeticException e) {  // non-terminating decimal expansion
                    bigDecimalResult = bigDecimalResult.divide(getBigDecimalMultiplier(), roundingMode);
                }
            }

            if (!status[STATUS_POSITIVE]) {
                bigDecimalResult = bigDecimalResult.negate();
            }
            return bigDecimalResult;
        } else {
            boolean gotDouble = true;
            boolean gotLongMinimum = false;
            double  doubleResult = 0.0;
            long    longResult = 0;

            // Finally, have DigitList parse the digits into a value.
            if (digitList.fitsIntoLong(status[STATUS_POSITIVE], isParseIntegerOnly())) {
                gotDouble = false;
                longResult = digitList.getLong();
                if (longResult < 0) {  // got Long.MIN_VALUE
                    gotLongMinimum = true;
                }
            } else {
                doubleResult = digitList.getDouble();
            }

            // Divide by multiplier. We have to be careful here not to do
            // unneeded conversions between double and long.
            if (multiplier != 1) {
                if (gotDouble) {
                    doubleResult /= multiplier;
                } else {
                    // Avoid converting to double if we can
                    if (longResult % multiplier == 0) {
                        longResult /= multiplier;
                    } else {
                        doubleResult = ((double)longResult) / multiplier;
                        gotDouble = true;
                    }
                }
            }

            if (!status[STATUS_POSITIVE] && !gotLongMinimum) {
                doubleResult = -doubleResult;
                longResult = -longResult;
            }

            // At this point, if we divided the result by the multiplier, the
            // result may fit into a long.  We check for this case and return
            // a long if possible.
            // We must do this AFTER applying the negative (if appropriate)
            // in order to handle the case of LONG_MIN; otherwise, if we do
            // this with a positive value -LONG_MIN, the double is > 0, but
            // the long is < 0. We also must retain a double in the case of
            // -0.0, which will compare as == to a long 0 cast to a double
            // (bug 4162852).
            if (multiplier != 1 && gotDouble) {
                longResult = (long)doubleResult;
                gotDouble = ((doubleResult != (double)longResult) ||
                            (doubleResult == 0.0 && 1/doubleResult < 0.0)) &&
                            !isParseIntegerOnly();
            }

            // cast inside of ?: because of binary numeric promotion, JLS 15.25
            return gotDouble ? (Number)doubleResult : (Number)longResult;
        }
    }

    /**
     * Return a BigInteger multiplier.
     */
    private BigInteger getBigIntegerMultiplier() {
        if (bigIntegerMultiplier == null) {
            bigIntegerMultiplier = BigInteger.valueOf(multiplier);
        }
        return bigIntegerMultiplier;
    }
    private transient BigInteger bigIntegerMultiplier;

    /**
     * Return a BigDecimal multiplier.
     */
    private BigDecimal getBigDecimalMultiplier() {
        if (bigDecimalMultiplier == null) {
            bigDecimalMultiplier = new BigDecimal(multiplier);
        }
        return bigDecimalMultiplier;
    }
    private transient BigDecimal bigDecimalMultiplier;

    private static final int STATUS_INFINITE = 0;
    private static final int STATUS_POSITIVE = 1;
    private static final int STATUS_LENGTH   = 2;

    /**
     * Parse the given text into a number.  The text is parsed beginning at
     * parsePosition, until an unparseable character is seen.
     * @param text The string to parse.
     * @param parsePosition The position at which to being parsing.  Upon
     * return, the first unparseable character.
     * @param digits The DigitList to set to the parsed value.
     * @param isExponent If true, parse an exponent.  This means no
     * infinite values and integer only.
     * @param status Upon return contains boolean status flags indicating
     * whether the value was infinite and whether it was positive.
     */
    private final boolean subparse(String text, ParsePosition parsePosition,
                                   String positivePrefix, String negativePrefix,
                                   DigitList digits, boolean isExponent,
                                   boolean[] status) {
        int position = parsePosition.index;
        int oldStart = parsePosition.index;
        boolean gotPositive, gotNegative;

        // check for positivePrefix; take longest
        gotPositive = text.regionMatches(position, positivePrefix, 0,
                positivePrefix.length());
        gotNegative = text.regionMatches(position, negativePrefix, 0,
                negativePrefix.length());

        if (gotPositive && gotNegative) {
            if (positivePrefix.length() > negativePrefix.length()) {
                gotNegative = false;
            } else if (positivePrefix.length() < negativePrefix.length()) {
                gotPositive = false;
            }
        }

        if (gotPositive) {
            position += positivePrefix.length();
        } else if (gotNegative) {
            position += negativePrefix.length();
        } else {
            parsePosition.errorIndex = position;
            return false;
        }

        // position will serve as new index when success, otherwise it will
        // serve as errorIndex when failure
        NumericPosition pos = subparseNumber(text, position, digits, true, isExponent, status);
        position = pos.fullPos;

        // First character after the prefix was un-parseable or parsing integer
        // only with no integer portion. Should fail regardless if lenient or strict.
        if (position == -1) {
            parsePosition.index = oldStart;
            parsePosition.errorIndex = oldStart;
            return false;
        }

        // When strict, text should end with the suffix.
        // When lenient, text only needs to contain the suffix.
        if (!isExponent) {
            if (gotPositive) {
                boolean containsPosSuffix =
                        text.regionMatches(position, positiveSuffix, 0, positiveSuffix.length());
                boolean endsWithPosSuffix =
                        containsPosSuffix && text.length() == position + positiveSuffix.length();
                gotPositive = parseStrict ? endsWithPosSuffix : containsPosSuffix;
            }
            if (gotNegative) {
                boolean containsNegSuffix =
                        text.regionMatches(position, negativeSuffix, 0, negativeSuffix.length());
                boolean endsWithNegSuffix =
                        containsNegSuffix && text.length() == position + negativeSuffix.length();
                gotNegative = parseStrict ? endsWithNegSuffix : containsNegSuffix;
            }

            // If both match, take longest
            if (gotPositive && gotNegative) {
                if (positiveSuffix.length() > negativeSuffix.length()) {
                    gotNegative = false;
                } else if (positiveSuffix.length() < negativeSuffix.length()) {
                    gotPositive = false;
                }
            }

            // Fail if neither or both
            if (gotPositive == gotNegative) {
                parsePosition.errorIndex = position;
                return false;
            }

            // When parsing integer only, index should be int pos
            // If intPos is -1, the entire value was integer and index should be full pos
            if (isParseIntegerOnly() && pos.intPos != -1) {
                parsePosition.index = pos.intPos;
            } else {
                // increment the index by the suffix
                parsePosition.index = position +
                        (gotPositive ? positiveSuffix.length() : negativeSuffix.length());
            }
        } else {
            parsePosition.index = position;
        }

        status[STATUS_POSITIVE] = gotPositive;
        if (parsePosition.index == oldStart) {
            parsePosition.errorIndex = position;
            return false;
        }
        return true;
    }

    /**
     * NumericPosition is a helper record class that stores two indices of interest.
     * {@code fullPos} is either the first unparseable character or -1 in case
     * of no valid number parsed. {@code intPos} reflects the position of
     * a parsed decimal symbol, if one exists. When parsing with {@code isParseIntegerOnly()},
     * {@code fullPos} is used to match the suffix, and reset the {@code ParsePosition}
     * index to {@code intPos}.
     *
     * @param fullPos an index that reflects the full traversal of the numerical String
     * @param intPos an index that reflects the position of a parsed decimal symbol.
     */
    record NumericPosition(int fullPos, int intPos) {}

    /**
     * Parses a number from the given {@code text}. The text is parsed
     * beginning at {@code position}, until an unparseable character is seen.
     *
     * @param text the string to parse
     * @param position the position at which parsing begins
     * @param digits the DigitList to set to the parsed value
     * @param checkExponent whether to check for exponential number
     * @param isExponent if the exponential part is encountered
     * @param status upon return contains boolean status flags indicating
     *               whether the value is infinite and whether it is
     *               positive
     * @return returns a {@code NumericPosition} that stores both a full
     *         traversal index, and an int only index.
     */
    NumericPosition subparseNumber(String text, int position,
                                   DigitList digits, boolean checkExponent,
                                   boolean isExponent, boolean[] status) {
        // process digits or Inf, find decimal position
        status[STATUS_INFINITE] = false;
        int intIndex = -1;
        if (!isExponent && text.regionMatches(position, symbols.getInfinity(), 0,
                symbols.getInfinity().length())) {
            position += symbols.getInfinity().length();
            status[STATUS_INFINITE] = true;
        } else {
            // We now have a string of digits, possibly with grouping symbols,
            // and decimal points.  We want to process these into a DigitList.
            // We don't want to put a bunch of leading zeros into the DigitList
            // though, so we keep track of the location of the decimal point,
            // put only significant digits into the DigitList, and adjust the
            // exponent as needed.

            digits.decimalAt = digits.count = 0;
            char zero = symbols.getZeroDigit();
            char decimal = isCurrencyFormat ?
                    symbols.getMonetaryDecimalSeparator() :
                    symbols.getDecimalSeparator();
            char grouping = isCurrencyFormat ?
                    symbols.getMonetaryGroupingSeparator() :
                    symbols.getGroupingSeparator();
            String exponentString = symbols.getExponentSeparator();
            boolean sawDecimal = false;
            boolean sawDigit = false;
            // Storing as long allows us to maintain accuracy of exponent
            // when the exponent value as well as the decimalAt nears
            // Integer.MAX/MIN value. However, the final expressed value is an int
            long exponent = 0;
            boolean[] expStat = new boolean[STATUS_LENGTH];

            // We have to track digitCount ourselves, because digits.count will
            // pin when the maximum allowable digits is reached.
            int digitCount = 0;
            int prevSeparatorIndex = -groupingSize;
            int startPos = position; // Rely on startPos as index after prefix

            int backup = -1;
            for (; position < text.length(); ++position) {
                char ch = text.charAt(position);

                /* We recognize all digit ranges, not only the Latin digit range
                 * '0'..'9'.  We do so by using the Character.digit() method,
                 * which converts a valid Unicode digit to the range 0..9.
                 *
                 * The character 'ch' may be a digit.  If so, place its value
                 * from 0 to 9 in 'digit'.  First try using the locale digit,
                 * which may or MAY NOT be a standard Unicode digit range.  If
                 * this fails, try using the standard Unicode digit ranges by
                 * calling Character.digit().  If this also fails, digit will
                 * have a value outside the range 0..9.
                 */
                int digit = ch - zero;
                if (digit < 0 || digit > 9) {
                    digit = Character.digit(ch, 10);
                }

                // Enforce the grouping size on the first group
                if (parseStrict && isGroupingUsed() && position == startPos + groupingSize
                        && prevSeparatorIndex == -groupingSize && !sawDecimal
                        && digit >= 0 && digit <= 9) {
                    return new NumericPosition(position, intIndex);
                }

                if (digit == 0) {
                    // Cancel out backup setting (see grouping handler below)
                    backup = -1; // Do this BEFORE continue statement below!!!
                    sawDigit = true;

                    // Handle leading zeros
                    if (digits.count == 0) {
                        // Ignore leading zeros in integer part of number.
                        if (!sawDecimal) {
                            continue;
                        }

                        // If we have seen the decimal, but no significant
                        // digits yet, then we account for leading zeros by
                        // decrementing the digits.decimalAt into negative
                        // values.
                        --digits.decimalAt;
                    } else {
                        ++digitCount;
                        if (!sawDecimal || !isParseIntegerOnly()) {
                            digits.append((char)(digit + '0'));
                        }
                    }
                } else if (digit > 0 && digit <= 9) { // [sic] digit==0 handled above
                    sawDigit = true;
                    ++digitCount;
                    if (!sawDecimal || !isParseIntegerOnly()) {
                        digits.append((char) (digit + '0'));
                    }

                    // Cancel out backup setting (see grouping handler below)
                    backup = -1;
                } else if (!isExponent && ch == decimal) {
                    if (isParseIntegerOnly() && startPos == position) {
                        // Parsing int only with no integer portion, fail
                        return new NumericPosition(-1, intIndex);
                    }
                    // Check grouping size on decimal separator
                    if (parseStrict && isGroupingViolation(position, prevSeparatorIndex)) {
                        return new NumericPosition(
                                groupingViolationIndex(position, prevSeparatorIndex), intIndex);
                    }
                    // If we're only parsing integers, or if we ALREADY saw the
                    // decimal, then don't parse this one.
                    if (sawDecimal) {
                        break;
                    }
                    intIndex = position;
                    digits.decimalAt = digitCount; // Not digits.count!
                    sawDecimal = true;
                } else if (!isExponent && ch == grouping && isGroupingUsed()) {
                    if (parseStrict) {
                        // text should not start with grouping when strict
                        if (position == startPos) {
                            return new NumericPosition(startPos, intIndex);
                        }
                        // when strict, fail if grouping occurs after decimal OR
                        // current group violates grouping size
                        if (sawDecimal || (isGroupingViolation(position, prevSeparatorIndex))) {
                            return new NumericPosition(
                                    groupingViolationIndex(position, prevSeparatorIndex), intIndex);
                        }
                        prevSeparatorIndex = position; // track previous
                    } else {
                        // when lenient, only exit if grouping occurs after decimal
                        // subsequent grouping symbols are allowed when lenient
                        if (sawDecimal) {
                            break;
                        }
                    }
                    // Ignore grouping characters, if we are using them, but
                    // require that they be followed by a digit.  Otherwise
                    // we backup and reprocess them.
                    backup = position;
                } else if (checkExponent && !isExponent
                        && text.regionMatches(position, exponentString, 0, exponentString.length())) {
                    // Process the exponent by recursively calling this method.
                    ParsePosition pos = new ParsePosition(position + exponentString.length());
                    DigitList exponentDigits = new DigitList();

                    if (subparse(text, pos, "", symbols.getMinusSignText(), exponentDigits, true, expStat)) {
                        // We parse the exponent with isExponent == true, thus fitsIntoLong()
                        // only returns false here if the exponent DigitList value exceeds
                        // Long.MAX_VALUE. We do not need to worry about false being
                        // returned for faulty values as they are ignored by DigitList.
                        if (exponentDigits.fitsIntoLong(expStat[STATUS_POSITIVE], true)) {
                            exponent = exponentDigits.getLong();
                            if (!expStat[STATUS_POSITIVE]) {
                                exponent = -exponent;
                            }
                        } else {
                            exponent = expStat[STATUS_POSITIVE] ?
                                    Long.MAX_VALUE : Long.MIN_VALUE;
                        }
                        position = pos.index; // Advance past the exponent
                    }
                    break; // Whether we fail or succeed, we exit this loop
                } else {
                    break;
                }
            }

            // (When strict), within the loop we enforce grouping when encountering
            // decimal/grouping symbols. Once outside loop, we need to check
            // the final grouping, ex: "1,234". Only check the final grouping
            // if we have not seen a decimal separator, to prevent a non needed check,
            // for ex: "1,234.", "1,234.12"
            if (parseStrict) {
                if (!sawDecimal && isGroupingViolation(position, prevSeparatorIndex)) {
                    // -1, since position is incremented by one too many when loop is finished
                    // "1,234%" and "1,234" both end with pos = 5, since '%' breaks
                    // the loop before incrementing position. In both cases, check
                    // should be done at pos = 4
                    return new NumericPosition(
                            groupingViolationIndex(position - 1, prevSeparatorIndex), intIndex);
                }
            }

            // If a grouping symbol is not followed by a digit, it must be
            // backed up to either exit early or fail depending on leniency
            if (backup != -1) {
                position = backup;
            }

            // If there was no decimal point we have an integer
            if (!sawDecimal) {
                digits.decimalAt = digitCount; // Not digits.count!
            }

            // If parsing integer only, adjust exponent if it occurs
            // in integer portion, otherwise ignore it
            if (!sawDecimal || !isParseIntegerOnly()) {
                digits.decimalAt = shiftDecimalAt(digits.decimalAt, exponent);
            }

            // If none of the text string was recognized.  For example, parse
            // "x" with pattern "#0.00" (return index and error index both 0)
            // parse "$" with pattern "$#0.00". (return index 0 and error
            // index 1).
            if (!sawDigit && digitCount == 0) {
                return new NumericPosition(-1, intIndex);
            }
        }
        return new NumericPosition(position, intIndex);
    }

    // Calculate the final decimal position based off the exponent value
    // and the existing decimalAt position. If overflow/underflow, the value
    // should be set as either Integer.MAX/MIN
    private int shiftDecimalAt(int decimalAt, long exponent) {
        try {
            exponent = Math.addExact(decimalAt, exponent);
        } catch (ArithmeticException ex) {
            // If we under/overflow a Long do not bother with the decimalAt
            // As it can only shift up to Integer.MAX/MIN which has no affect
            if (exponent > 0 && decimalAt > 0) {
                return Integer.MAX_VALUE;
            } else {
                return Integer.MIN_VALUE;
            }
        }
        try {
            decimalAt = Math.toIntExact(exponent);
        } catch (ArithmeticException ex) {
            decimalAt = exponent > 0 ? Integer.MAX_VALUE : Integer.MIN_VALUE;
        }
        return decimalAt;
    }

    // Checks to make sure grouping size is not violated. Used when strict.
    private boolean isGroupingViolation(int pos, int prevGroupingPos) {
        assert parseStrict : "Grouping violations should only occur when strict";
        return isGroupingUsed() && // Only violates if using grouping
                // Checks if a previous grouping symbol was seen.
                prevGroupingPos != -groupingSize &&
                // The check itself, - 1 to account for grouping/decimal symbol
                pos - 1 != prevGroupingPos + groupingSize;
    }

    // Calculates the index that violated the grouping size
    // Violation can be over or under the grouping size
    // under - Current group has a grouping size of less than the expected
    // over - Current group has a grouping size of more than the expected
    private int groupingViolationIndex(int pos, int prevGroupingPos) {
        // Both examples assume grouping size of 3 and 0 indexed
        // under ex: "1,23,4". (4) OR "1,,2". (2) When under, violating char is grouping symbol
        // over ex: "1,2345,6. (5) When over, violating char is the excess digit
        // This method is only evaluated when a grouping symbol is found, thus
        // we can take the minimum of either the current pos, or where we expect
        // the current group to have ended
        return Math.min(pos, prevGroupingPos + groupingSize + 1);
    }

    /**
     * Returns a copy of the decimal format symbols, which is generally not
     * changed by the programmer or user.
     * @return a copy of the desired DecimalFormatSymbols
     * @see java.text.DecimalFormatSymbols
     */
    public DecimalFormatSymbols getDecimalFormatSymbols() {
        try {
            // don't allow multiple references
            return (DecimalFormatSymbols) symbols.clone();
        } catch (Exception foo) {
            return null; // should never happen
        }
    }


    /**
     * Sets the decimal format symbols, which is generally not changed
     * by the programmer or user.
     * @param newSymbols desired DecimalFormatSymbols
     * @see java.text.DecimalFormatSymbols
     */
    public void setDecimalFormatSymbols(DecimalFormatSymbols newSymbols) {
        try {
            // don't allow multiple references
            symbols = (DecimalFormatSymbols) newSymbols.clone();
            expandAffixes();
            fastPathCheckNeeded = true;
        } catch (Exception foo) {
            // should never happen
        }
    }

    /**
     * Get the positive prefix.
     * <P>Examples: +123, $123, sFr123
     *
     * @return the positive prefix
     */
    public String getPositivePrefix () {
        return positivePrefix;
    }

    /**
     * Set the positive prefix.
     * <P>Examples: +123, $123, sFr123
     *
     * @param newValue the new positive prefix. Non-null.
     * @throws NullPointerException if {@code newValue} is {@code null}
     */
    public void setPositivePrefix (String newValue) {
        Objects.requireNonNull(newValue, "prefix must not be null");
        positivePrefix = newValue;
        posPrefixPattern = null;
        positivePrefixFieldPositions = null;
        fastPathCheckNeeded = true;
    }

    /**
     * Returns the FieldPositions of the fields in the prefix used for
     * positive numbers. This is not used if the user has explicitly set
     * a positive prefix via {@code setPositivePrefix}. This is
     * lazily created.
     *
     * @return FieldPositions in positive prefix
     */
    private FieldPosition[] getPositivePrefixFieldPositions() {
        if (positivePrefixFieldPositions == null) {
            if (posPrefixPattern != null) {
                positivePrefixFieldPositions = expandAffix(posPrefixPattern);
            } else {
                positivePrefixFieldPositions = EmptyFieldPositionArray;
            }
        }
        return positivePrefixFieldPositions;
    }

    /**
     * Get the negative prefix.
     * <P>Examples: -123, ($123) (with negative suffix), sFr-123
     *
     * @return the negative prefix
     */
    public String getNegativePrefix () {
        return negativePrefix;
    }

    /**
     * Set the negative prefix.
     * <P>Examples: -123, ($123) (with negative suffix), sFr-123
     *
     * @param newValue the new negative prefix. Non-null.
     * @throws NullPointerException if {@code newValue} is {@code null}
     */
    public void setNegativePrefix (String newValue) {
        Objects.requireNonNull(newValue, "prefix must not be null");
        negativePrefix = newValue;
        negPrefixPattern = null;
        fastPathCheckNeeded = true;
    }

    /**
     * Returns the FieldPositions of the fields in the prefix used for
     * negative numbers. This is not used if the user has explicitly set
     * a negative prefix via {@code setNegativePrefix}. This is
     * lazily created.
     *
     * @return FieldPositions in positive prefix
     */
    private FieldPosition[] getNegativePrefixFieldPositions() {
        if (negativePrefixFieldPositions == null) {
            if (negPrefixPattern != null) {
                negativePrefixFieldPositions = expandAffix(negPrefixPattern);
            } else {
                negativePrefixFieldPositions = EmptyFieldPositionArray;
            }
        }
        return negativePrefixFieldPositions;
    }

    /**
     * Get the positive suffix.
     * <P>Example: 123%
     *
     * @return the positive suffix
     */
    public String getPositiveSuffix () {
        return positiveSuffix;
    }

    /**
     * Set the positive suffix.
     * <P>Example: 123%
     *
     * @param newValue the new positive suffix. Non-null.
     * @throws NullPointerException if {@code newValue} is {@code null}
     */
    public void setPositiveSuffix (String newValue) {
        Objects.requireNonNull(newValue, "suffix must not be null");
        positiveSuffix = newValue;
        posSuffixPattern = null;
        fastPathCheckNeeded = true;
    }

    /**
     * Returns the FieldPositions of the fields in the suffix used for
     * positive numbers. This is not used if the user has explicitly set
     * a positive suffix via {@code setPositiveSuffix}. This is
     * lazily created.
     *
     * @return FieldPositions in positive prefix
     */
    private FieldPosition[] getPositiveSuffixFieldPositions() {
        if (positiveSuffixFieldPositions == null) {
            if (posSuffixPattern != null) {
                positiveSuffixFieldPositions = expandAffix(posSuffixPattern);
            } else {
                positiveSuffixFieldPositions = EmptyFieldPositionArray;
            }
        }
        return positiveSuffixFieldPositions;
    }

    /**
     * Get the negative suffix.
     * <P>Examples: -123%, ($123) (with positive suffixes)
     *
     * @return the negative suffix
     */
    public String getNegativeSuffix () {
        return negativeSuffix;
    }

    /**
     * Set the negative suffix.
     * <P>Examples: 123%
     *
     * @param newValue the new negative suffix. Non-null.
     * @throws NullPointerException if {@code newValue} is {@code null}
     */
    public void setNegativeSuffix (String newValue) {
        Objects.requireNonNull(newValue, "suffix must not be null");
        negativeSuffix = newValue;
        negSuffixPattern = null;
        fastPathCheckNeeded = true;
    }

    /**
     * Returns the FieldPositions of the fields in the suffix used for
     * negative numbers. This is not used if the user has explicitly set
     * a negative suffix via {@code setNegativeSuffix}. This is
     * lazily created.
     *
     * @return FieldPositions in positive prefix
     */
    private FieldPosition[] getNegativeSuffixFieldPositions() {
        if (negativeSuffixFieldPositions == null) {
            if (negSuffixPattern != null) {
                negativeSuffixFieldPositions = expandAffix(negSuffixPattern);
            } else {
                negativeSuffixFieldPositions = EmptyFieldPositionArray;
            }
        }
        return negativeSuffixFieldPositions;
    }

    /**
     * Gets the multiplier for use in percent, per mille, and similar
     * formats.
     *
     * @return the multiplier
     * @see #setMultiplier(int)
     */
    public int getMultiplier () {
        return multiplier;
    }

    /**
     * Sets the multiplier for use in percent, per mille, and similar
     * formats.
     * For a percent format, set the multiplier to 100 and the suffixes to
     * have '%' (for Arabic, use the Arabic percent sign).
     * For a per mille format, set the multiplier to 1000 and the suffixes to
     * have '{@code U+2030}'.
     *
     * <P>Example: with multiplier 100, 1.23 is formatted as "123", and
     * "123" is parsed into 1.23. If {@code isParseIntegerOnly()} returns {@code true},
     * "123" is parsed into 1.
     *
     * @param newValue the new multiplier
     * @see #getMultiplier
     */
    public void setMultiplier (int newValue) {
        multiplier = newValue;
        bigDecimalMultiplier = null;
        bigIntegerMultiplier = null;
        fastPathCheckNeeded = true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setGroupingUsed(boolean newValue) {
        super.setGroupingUsed(newValue);
        fastPathCheckNeeded = true;
    }

    /**
     * Return the grouping size. Grouping size is the number of digits between
     * grouping separators in the integer portion of a number.  For example,
     * in the number "123,456.78", the grouping size is 3. Grouping size of
     * zero designates that grouping is not used, which provides the same
     * formatting as if calling {@link #setGroupingUsed(boolean)
     * setGroupingUsed(false)}.
     *
     * @return the grouping size
     * @see #setGroupingSize
     * @see java.text.NumberFormat#isGroupingUsed
     * @see java.text.DecimalFormatSymbols#getGroupingSeparator
     */
    public int getGroupingSize () {
        return groupingSize;
    }

    /**
     * Set the grouping size. Grouping size is the number of digits between
     * grouping separators in the integer portion of a number.  For example,
     * in the number "123,456.78", the grouping size is 3. Grouping size of
     * zero designates that grouping is not used, which provides the same
     * formatting as if calling {@link #setGroupingUsed(boolean)
     * setGroupingUsed(false)}.
     * <p>
     * The value passed in is converted to a byte, which may lose information.
     * Values that are negative or greater than
     * {@link java.lang.Byte#MAX_VALUE Byte.MAX_VALUE}, will throw an
     * {@code IllegalArgumentException}.
     *
     * @param newValue the new grouping size
     * @see #getGroupingSize
     * @see java.text.NumberFormat#setGroupingUsed
     * @see java.text.DecimalFormatSymbols#setGroupingSeparator
     * @throws IllegalArgumentException if {@code newValue} is negative or
     *          greater than {@link java.lang.Byte#MAX_VALUE Byte.MAX_VALUE}
     */
    public void setGroupingSize (int newValue) {
        if (newValue < 0 || newValue > Byte.MAX_VALUE) {
            throw new IllegalArgumentException(
                "newValue is out of valid range. value: " + newValue);
        }
        groupingSize = (byte)newValue;
        fastPathCheckNeeded = true;
    }

    /**
     * Allows you to get the behavior of the decimal separator with integers.
     * (The decimal separator will always appear with decimals.)
     * <P>Example: Decimal ON: 12345 &rarr; 12345.; OFF: 12345 &rarr; 12345
     *
     * @return {@code true} if the decimal separator is always shown;
     *         {@code false} otherwise
     */
    public boolean isDecimalSeparatorAlwaysShown() {
        return decimalSeparatorAlwaysShown;
    }

    /**
     * Allows you to set the behavior of the decimal separator with integers.
     * (The decimal separator will always appear with decimals.)
     * <P>Example: Decimal ON: 12345 &rarr; 12345.; OFF: 12345 &rarr; 12345
     *
     * @param newValue {@code true} if the decimal separator is always shown;
     *                 {@code false} otherwise
     */
    public void setDecimalSeparatorAlwaysShown(boolean newValue) {
        decimalSeparatorAlwaysShown = newValue;
        fastPathCheckNeeded = true;
    }

    /**
     * {@inheritDoc NumberFormat}
     *
     * @see #setStrict(boolean)
     * @see #parse(String, ParsePosition)
     * @since 23
     */
    @Override
    public boolean isStrict() {
        return parseStrict;
    }

    /**
     * {@inheritDoc NumberFormat}
     *
     * @see #isStrict()
     * @see #parse(String, ParsePosition)
     * @since 23
     */
    @Override
    public void setStrict(boolean strict) {
        parseStrict = strict;
    }

    /**
     * Returns whether the {@link #parse(java.lang.String, java.text.ParsePosition)}
     * method returns {@code BigDecimal}. The default value is false.
     *
     * @return {@code true} if the parse method returns BigDecimal;
     *         {@code false} otherwise
     * @see #setParseBigDecimal
     * @since 1.5
     */
    public boolean isParseBigDecimal() {
        return parseBigDecimal;
    }

    /**
     * Sets whether the {@link #parse(java.lang.String, java.text.ParsePosition)}
     * method returns {@code BigDecimal}.
     *
     * @param newValue {@code true} if the parse method returns BigDecimal;
     *                 {@code false} otherwise
     * @see #isParseBigDecimal
     * @since 1.5
     */
    public void setParseBigDecimal(boolean newValue) {
        parseBigDecimal = newValue;
    }

    /**
     * Standard override; no change in semantics.
     */
    @Override
    public Object clone() {
        DecimalFormat other = (DecimalFormat) super.clone();
        other.symbols = (DecimalFormatSymbols) symbols.clone();
        other.digitList = (DigitList) digitList.clone();

        // Fast-path is almost stateless algorithm. The only logical state is the
        // isFastPath flag. In addition fastPathCheckNeeded is a sentinel flag
        // that forces recalculation of all fast-path fields when set to true.
        //
        // There is thus no need to clone all the fast-path fields.
        // We just only need to set fastPathCheckNeeded to true when cloning,
        // and init fastPathData to null as if it were a truly new instance.
        // Every fast-path field will be recalculated (only once) at next usage of
        // fast-path algorithm.
        other.fastPathCheckNeeded = true;
        other.isFastPath = false;
        other.fastPathData = null;

        return other;
    }

    /**
     * Compares the specified object with this {@code DecimalFormat} for equality.
     * Returns true if the object is also a {@code DecimalFormat} and the
     * two formats would format any value the same.
     *
     * @implSpec This method performs an equality check with a notion of class
     * identity based on {@code getClass()}, rather than {@code instanceof}.
     * Therefore, in the equals methods in subclasses, no instance of this class
     * should compare as equal to an instance of a subclass.
     * @param  obj object to be compared for equality
     * @return {@code true} if the specified object is equal to this {@code DecimalFormat}
     * @see Object#equals(Object)
     */
    @Override
    public boolean equals(Object obj)
    {
        if (this == obj) {
            return true;
        }

        if (!super.equals(obj))
            return false; // super does null and class checks

        DecimalFormat other = (DecimalFormat) obj;
        return ((posPrefixPattern == other.posPrefixPattern &&
                 positivePrefix.equals(other.positivePrefix))
                || (posPrefixPattern != null &&
                    posPrefixPattern.equals(other.posPrefixPattern)))
            && ((posSuffixPattern == other.posSuffixPattern &&
                 positiveSuffix.equals(other.positiveSuffix))
                || (posSuffixPattern != null &&
                    posSuffixPattern.equals(other.posSuffixPattern)))
            && ((negPrefixPattern == other.negPrefixPattern &&
                 negativePrefix.equals(other.negativePrefix))
                || (negPrefixPattern != null &&
                    negPrefixPattern.equals(other.negPrefixPattern)))
            && ((negSuffixPattern == other.negSuffixPattern &&
                 negativeSuffix.equals(other.negativeSuffix))
                || (negSuffixPattern != null &&
                    negSuffixPattern.equals(other.negSuffixPattern)))
            && multiplier == other.multiplier
            && groupingSize == other.groupingSize
            && decimalSeparatorAlwaysShown == other.decimalSeparatorAlwaysShown
            && parseBigDecimal == other.parseBigDecimal
            && useExponentialNotation == other.useExponentialNotation
            && (!useExponentialNotation ||
                minExponentDigits == other.minExponentDigits)
            && maximumIntegerDigits == other.maximumIntegerDigits
            && minimumIntegerDigits == other.minimumIntegerDigits
            && maximumFractionDigits == other.maximumFractionDigits
            && minimumFractionDigits == other.minimumFractionDigits
            && roundingMode == other.roundingMode
            && symbols.equals(other.symbols)
            && parseStrict == other.parseStrict;
    }

    /**
     * {@return the hash code for this {@code DecimalFormat}}
     *
     * @implSpec This method calculates the hash code value using the values returned from
     * {@link #getPositivePrefix()} and {@link NumberFormat#hashCode()}.
     * @see Object#hashCode()
     * @see NumberFormat#hashCode()
     */
    @Override
    public int hashCode() {
        return super.hashCode() * 37 + positivePrefix.hashCode();
        // just enough fields for a reasonable distribution
    }

    /**
     * {@return a string identifying this {@code DecimalFormat}, for debugging}
     */
    @Override
    public String toString() {
        return
            """
            DecimalFormat [locale: "%s", pattern: "%s"]
            """.formatted(symbols.getLocale().getDisplayName(), toPattern());
    }

    /**
     * Synthesizes a pattern string that represents the current state
     * of this Format object.
     *
     * @return a pattern string
     * @see #applyPattern
     */
    public String toPattern() {
        return toPattern( false );
    }

    /**
     * Synthesizes a localized pattern string that represents the current
     * state of this Format object.
     *
     * @return a localized pattern string
     * @see #applyPattern
     */
    public String toLocalizedPattern() {
        return toPattern( true );
    }

    /**
     * Expand the affix pattern strings into the expanded affix strings.  If any
     * affix pattern string is null, do not expand it.  This method should be
     * called any time the symbols or the affix patterns change in order to keep
     * the expanded affix strings up to date.
     */
    private void expandAffixes() {
        // Reuse one StringBuilder for better performance
        StringBuilder buffer = new StringBuilder();
        if (posPrefixPattern != null) {
            positivePrefix = expandAffix(posPrefixPattern, buffer);
            positivePrefixFieldPositions = null;
        }
        if (posSuffixPattern != null) {
            positiveSuffix = expandAffix(posSuffixPattern, buffer);
            positiveSuffixFieldPositions = null;
        }
        if (negPrefixPattern != null) {
            negativePrefix = expandAffix(negPrefixPattern, buffer);
            negativePrefixFieldPositions = null;
        }
        if (negSuffixPattern != null) {
            negativeSuffix = expandAffix(negSuffixPattern, buffer);
            negativeSuffixFieldPositions = null;
        }
    }

    /**
     * Expand an affix pattern into an affix string.  All characters in the
     * pattern are literal unless prefixed by QUOTE.  The following characters
     * after QUOTE are recognized: PATTERN_PERCENT, PATTERN_PER_MILLE,
     * PATTERN_MINUS, and CURRENCY_SIGN.  If CURRENCY_SIGN is doubled (QUOTE +
     * CURRENCY_SIGN + CURRENCY_SIGN), it is interpreted as an ISO 4217
     * currency code.  Any other character after a QUOTE represents itself.
     * QUOTE must be followed by another character; QUOTE may not occur by
     * itself at the end of the pattern.
     *
     * @param pattern the non-null, possibly empty pattern
     * @param buffer a scratch StringBuilder; its contents will be lost
     * @return the expanded equivalent of pattern
     */
    private String expandAffix(String pattern, StringBuilder buffer) {
        buffer.setLength(0);
        for (int i=0; i<pattern.length(); ) {
            char c = pattern.charAt(i++);
            if (c == QUOTE) {
                c = pattern.charAt(i++);
                switch (c) {
                case CURRENCY_SIGN:
                    if (i<pattern.length() &&
                        pattern.charAt(i) == CURRENCY_SIGN) {
                        ++i;
                        buffer.append(symbols.getInternationalCurrencySymbol());
                    } else {
                        buffer.append(symbols.getCurrencySymbol());
                    }
                    continue;
                case PATTERN_PERCENT:
                    buffer.append(symbols.getPercentText());
                    continue;
                case PATTERN_PER_MILLE:
                    buffer.append(symbols.getPerMillText());
                    continue;
                case PATTERN_MINUS:
                    buffer.append(symbols.getMinusSignText());
                    continue;
                }
            }
            buffer.append(c);
        }
        return buffer.toString();
    }

    /**
     * Expand an affix pattern into an array of FieldPositions describing
     * how the pattern would be expanded.
     * All characters in the
     * pattern are literal unless prefixed by QUOTE.  The following characters
     * after QUOTE are recognized: PATTERN_PERCENT, PATTERN_PER_MILLE,
     * PATTERN_MINUS, and CURRENCY_SIGN.  If CURRENCY_SIGN is doubled (QUOTE +
     * CURRENCY_SIGN + CURRENCY_SIGN), it is interpreted as an ISO 4217
     * currency code.  Any other character after a QUOTE represents itself.
     * QUOTE must be followed by another character; QUOTE may not occur by
     * itself at the end of the pattern.
     *
     * @param pattern the non-null, possibly empty pattern
     * @return FieldPosition array of the resulting fields.
     */
    private FieldPosition[] expandAffix(String pattern) {
        ArrayList<FieldPosition> positions = null;
        int stringIndex = 0;
        for (int i=0; i<pattern.length(); ) {
            char c = pattern.charAt(i++);
            if (c == QUOTE) {
                Format.Field fieldID = null;
                String string = null;
                c = pattern.charAt(i++);
                switch (c) {
                case CURRENCY_SIGN:
                    if (i<pattern.length() &&
                        pattern.charAt(i) == CURRENCY_SIGN) {
                        ++i;
                        string = symbols.getInternationalCurrencySymbol();
                    } else {
                        string = symbols.getCurrencySymbol();
                    }
                    fieldID = Field.CURRENCY;
                    break;
                case PATTERN_PERCENT:
                    string = symbols.getPercentText();
                    fieldID = Field.PERCENT;
                    break;
                case PATTERN_PER_MILLE:
                    string = symbols.getPerMillText();
                    fieldID = Field.PERMILLE;
                    break;
                case PATTERN_MINUS:
                    string = symbols.getMinusSignText();
                    fieldID = Field.SIGN;
                    break;
                }

                if (fieldID != null && !string.isEmpty()) {
                    if (positions == null) {
                        positions = new ArrayList<>(2);
                    }
                    FieldPosition fp = new FieldPosition(fieldID);
                    fp.setBeginIndex(stringIndex);
                    fp.setEndIndex(stringIndex + string.length());
                    positions.add(fp);
                    stringIndex += string.length();
                    continue;
                }
            }
            stringIndex++;
        }
        if (positions != null) {
            return positions.toArray(EmptyFieldPositionArray);
        }
        return EmptyFieldPositionArray;
    }

    /**
     * Appends an affix pattern to the given StringBuilder, quoting special
     * characters as needed.  Uses the internal affix pattern, if that exists,
     * or the literal affix, if the internal affix pattern is null.  The
     * appended string will generate the same affix pattern (or literal affix)
     * when passed to toPattern().
     *
     * @param buffer the affix string is appended to this
     * @param affixPattern a pattern such as posPrefixPattern; may be null
     * @param expAffix a corresponding expanded affix, such as positivePrefix.
     * Ignored unless affixPattern is null.  If affixPattern is null, then
     * expAffix is appended as a literal affix.
     * @param localized true if the appended pattern should contain localized
     * pattern characters; otherwise, non-localized pattern chars are appended
     */
    private void appendAffix(StringBuilder buffer, String affixPattern,
                             String expAffix, boolean localized) {
        if (affixPattern == null) {
            appendAffix(buffer, expAffix, localized);
        } else {
            int i;
            for (int pos=0; pos<affixPattern.length(); pos=i) {
                i = affixPattern.indexOf(QUOTE, pos);
                if (i < 0) {
                    appendAffix(buffer, affixPattern.substring(pos), localized);
                    break;
                }
                if (i > pos) {
                    appendAffix(buffer, affixPattern.substring(pos, i), localized);
                }
                char c = affixPattern.charAt(++i);
                ++i;
                if (c == QUOTE) {
                    buffer.append(c);
                    // Fall through and append another QUOTE below
                } else if (c == CURRENCY_SIGN &&
                           i<affixPattern.length() &&
                           affixPattern.charAt(i) == CURRENCY_SIGN) {
                    ++i;
                    buffer.append(c);
                    // Fall through and append another CURRENCY_SIGN below
                } else if (localized) {
                    switch (c) {
                    case PATTERN_PERCENT:
                        buffer.append(symbols.getPercentText());
                        continue;
                    case PATTERN_PER_MILLE:
                        buffer.append(symbols.getPerMillText());
                        continue;
                    case PATTERN_MINUS:
                        buffer.append(symbols.getMinusSignText());
                        continue;
                    }
                }
                buffer.append(c);
            }
        }
    }

    /**
     * Append an affix to the given StringBuilder, using quotes if
     * there are special characters.  Single quotes themselves must be
     * escaped in either case.
     */
    private void appendAffix(StringBuilder buffer, String affix, boolean localized) {
        boolean needQuote;
        if (localized) {
            needQuote = affix.indexOf(symbols.getZeroDigit()) >= 0
                || affix.indexOf(symbols.getGroupingSeparator()) >= 0
                || affix.indexOf(symbols.getDecimalSeparator()) >= 0
                || affix.indexOf(symbols.getPercentText()) >= 0
                || affix.indexOf(symbols.getPerMillText()) >= 0
                || affix.indexOf(symbols.getDigit()) >= 0
                || affix.indexOf(symbols.getPatternSeparator()) >= 0
                || affix.indexOf(symbols.getMinusSignText()) >= 0
                || affix.indexOf(CURRENCY_SIGN) >= 0;
        } else {
            needQuote = affix.indexOf(PATTERN_ZERO_DIGIT) >= 0
                || affix.indexOf(PATTERN_GROUPING_SEPARATOR) >= 0
                || affix.indexOf(PATTERN_DECIMAL_SEPARATOR) >= 0
                || affix.indexOf(PATTERN_PERCENT) >= 0
                || affix.indexOf(PATTERN_PER_MILLE) >= 0
                || affix.indexOf(PATTERN_DIGIT) >= 0
                || affix.indexOf(PATTERN_SEPARATOR) >= 0
                || affix.indexOf(PATTERN_MINUS) >= 0
                || affix.indexOf(CURRENCY_SIGN) >= 0;
        }
        if (needQuote) buffer.append('\'');
        if (affix.indexOf('\'') < 0) buffer.append(affix);
        else {
            for (int j=0; j<affix.length(); ++j) {
                char c = affix.charAt(j);
                buffer.append(c);
                if (c == '\'') buffer.append(c);
            }
        }
        if (needQuote) buffer.append('\'');
    }

    /**
     * Implementation of producing a pattern. This method returns a positive and
     * negative (if needed), pattern string in the form of : Prefix (optional)
     * Number Suffix (optional). A NegativePattern is only produced if the
     * prefix or suffix patterns differs.
     */
    private String toPattern(boolean localized) {
        // Determine symbol values; use DFS if localized
        char zeroSymbol = localized ? symbols.getZeroDigit() : PATTERN_ZERO_DIGIT;
        char digitSymbol = localized ? symbols.getDigit() : PATTERN_DIGIT;
        char groupingSymbol = localized ?
                (isCurrencyFormat ? symbols.getMonetaryGroupingSeparator() : symbols.getGroupingSeparator()) :
                PATTERN_GROUPING_SEPARATOR;
        char decimalSymbol = localized ?
                (isCurrencyFormat ? symbols.getMonetaryDecimalSeparator() : symbols.getDecimalSeparator()) :
                PATTERN_DECIMAL_SEPARATOR;
        String exponentSymbol = localized ? symbols.getExponentSeparator() : PATTERN_EXPONENT;
        char patternSeparator = localized ? symbols.getPatternSeparator() : PATTERN_SEPARATOR;

        StringBuilder result = new StringBuilder();
        // j == 1 denotes PositivePattern, j == 0 denotes NegativePattern
        for (int j = 1; j >= 0; --j) {
            if (j == 1) {
                // Append positive and negative (if needed) prefix pattern
                appendAffix(result, posPrefixPattern, positivePrefix, localized);
            } else {
                appendAffix(result, negPrefixPattern, negativePrefix, localized);
            }
            // Append integer digits
            int digitCount = useExponentialNotation ? getMaximumIntegerDigits() :
                    Math.max(groupingSize, getMinimumIntegerDigits()) + 1;
            for (int i = digitCount; i > 0; --i) {
                if (i != digitCount && isGroupingUsed() && groupingSize != 0 &&
                        i % groupingSize == 0) {
                    result.append(groupingSymbol);
                }
                result.append(i <= getMinimumIntegerDigits() ? zeroSymbol : digitSymbol);
            }
            // Append decimal symbol
            if (getMaximumFractionDigits() > 0 || decimalSeparatorAlwaysShown) {
                result.append(decimalSymbol);
            }
            // Append fraction digits
            result.repeat(zeroSymbol, getMinimumFractionDigits());
            result.repeat(digitSymbol, getMaximumFractionDigits() - getMinimumFractionDigits());
            // Append exponent symbol and digits
            if (useExponentialNotation) {
                result.append(exponentSymbol);
                result.repeat(zeroSymbol, minExponentDigits);
            }
            if (j == 1) {
                // Append positive suffix pattern
                appendAffix(result, posSuffixPattern, positiveSuffix, localized);
                if (posEqualsNegPattern()) {
                    // Negative pattern not needed if suffix/prefix are equivalent
                    break;
                }
                result.append(patternSeparator);
            } else {
                appendAffix(result, negSuffixPattern, negativeSuffix, localized);
            }
        }
        return result.toString();
    }

    /**
     * This method returns true if the positive and negative prefix/suffix
     * values are equivalent. This is used to determine if an explicit NegativePattern
     * is required.
     */
    private boolean posEqualsNegPattern() {
        // Check suffix
        return ((negSuffixPattern == posSuffixPattern && // n == p == null
                negativeSuffix.equals(positiveSuffix))
                || (negSuffixPattern != null &&
                negSuffixPattern.equals(posSuffixPattern)))
                && // Check prefix
                ((negPrefixPattern != null && posPrefixPattern != null &&
                negPrefixPattern.equals("'-" + posPrefixPattern)) ||
                (negPrefixPattern == posPrefixPattern && // n == p == null
                negativePrefix.equals(symbols.getMinusSignText() + positivePrefix)));
    }

    /**
     * Apply the given pattern to this Format object.  A pattern is a
     * short-hand specification for the various formatting properties.
     * These properties can also be changed individually through the
     * various setter methods.
     * <p>
     * The number of maximum integer digits is usually not derived from the pattern.
     * See the note in the {@link ##patterns Patterns} section for more detail.
     * For negative numbers, use a second pattern, separated by a semicolon
     * <P>Example {@code "#,#00.0#"} &rarr; 1,234.56
     * <P>This means a minimum of 2 integer digits, 1 fraction digit, and
     * a maximum of 2 fraction digits.
     * <p>Example: {@code "#,#00.0#;(#,#00.0#)"} for negatives in
     * parentheses.
     * <p>In negative patterns, the minimum and maximum counts are ignored;
     * these are presumed to be set in the positive pattern.
     *
     * @param pattern a new pattern
     * @throws    NullPointerException if {@code pattern} is null
     * @throws    IllegalArgumentException if the given pattern is invalid.
     */
    public void applyPattern(String pattern) {
        applyPattern(pattern, false);
    }

    /**
     * Apply the given pattern to this Format object.  The pattern
     * is assumed to be in a localized notation. A pattern is a
     * short-hand specification for the various formatting properties.
     * These properties can also be changed individually through the
     * various setter methods.
     * <p>
     * The number of maximum integer digits is usually not derived from the pattern.
     * See the note in the {@link ##patterns Patterns} section for more detail.
     * For negative numbers, use a second pattern, separated by a semicolon
     * <P>Example {@code "#,#00.0#"} &rarr; 1,234.56
     * <P>This means a minimum of 2 integer digits, 1 fraction digit, and
     * a maximum of 2 fraction digits.
     * <p>Example: {@code "#,#00.0#;(#,#00.0#)"} for negatives in
     * parentheses.
     * <p>In negative patterns, the minimum and maximum counts are ignored;
     * these are presumed to be set in the positive pattern.
     *
     * @param pattern a new pattern
     * @throws    NullPointerException if {@code pattern} is null
     * @throws    IllegalArgumentException if the given pattern is invalid.
     */
    public void applyLocalizedPattern(String pattern) {
        applyPattern(pattern, true);
    }

    /**
     * Does the real work of applying a pattern.
     */
    private void applyPattern(String pattern, boolean localized) {
        char zeroDigit         = PATTERN_ZERO_DIGIT;
        char groupingSeparator = PATTERN_GROUPING_SEPARATOR;
        char decimalSeparator  = PATTERN_DECIMAL_SEPARATOR;
        char percent           = PATTERN_PERCENT;
        char perMill           = PATTERN_PER_MILLE;
        char digit             = PATTERN_DIGIT;
        char separator         = PATTERN_SEPARATOR;
        String exponent        = PATTERN_EXPONENT;
        char minus             = PATTERN_MINUS;
        if (localized) {
            zeroDigit         = symbols.getZeroDigit();
            groupingSeparator = symbols.getGroupingSeparator();
            decimalSeparator  = symbols.getDecimalSeparator();
            percent           = symbols.getPercent();
            perMill           = symbols.getPerMill();
            digit             = symbols.getDigit();
            separator         = symbols.getPatternSeparator();
            exponent          = symbols.getExponentSeparator();
            minus             = symbols.getMinusSign();
        }
        boolean gotNegative = false;
        decimalSeparatorAlwaysShown = false;
        isCurrencyFormat = false;
        useExponentialNotation = false;

        int start = 0;
        for (int j = 1; j >= 0 && start < pattern.length(); --j) {
            boolean inQuote = false;
            StringBuilder prefix = new StringBuilder();
            StringBuilder suffix = new StringBuilder();
            int decimalPos = -1;
            int multiplier = 1;
            int digitLeftCount = 0, zeroDigitCount = 0, digitRightCount = 0;
            byte groupingCount = -1;

            // The phase ranges from 0 to 2.  Phase 0 is the prefix.  Phase 1 is
            // the section of the pattern with digits, decimal separator,
            // grouping characters.  Phase 2 is the suffix.  In phases 0 and 2,
            // percent, per mille, and currency symbols are recognized and
            // translated.  The separation of the characters into phases is
            // strictly enforced; if phase 1 characters are to appear in the
            // suffix, for example, they must be quoted.
            int phase = 0;

            // The affix is either the prefix or the suffix.
            StringBuilder affix = prefix;

            for (int pos = start; pos < pattern.length(); ++pos) {
                char ch = pattern.charAt(pos);
                switch (phase) {
                case 0:
                case 2:
                    // Process the prefix / suffix characters
                    if (inQuote) {
                        // A quote within quotes indicates either the closing
                        // quote or two quotes, which is a quote literal. That
                        // is, we have the second quote in 'do' or 'don''t'.
                        if (ch == QUOTE) {
                            if ((pos+1) < pattern.length() &&
                                pattern.charAt(pos+1) == QUOTE) {
                                ++pos;
                                affix.append("''"); // 'don''t'
                            } else {
                                inQuote = false; // 'do'
                            }
                            continue;
                        }
                    } else {
                        // Process unquoted characters seen in prefix or suffix
                        // phase.
                        if (ch == digit ||
                            ch == zeroDigit ||
                            ch == groupingSeparator ||
                            ch == decimalSeparator) {
                            phase = 1;
                            --pos; // Reprocess this character
                            continue;
                        } else if (ch == CURRENCY_SIGN) {
                            // Use lookahead to determine if the currency sign
                            // is doubled or not.
                            boolean doubled = (pos + 1) < pattern.length() &&
                                pattern.charAt(pos + 1) == CURRENCY_SIGN;
                            if (doubled) { // Skip over the doubled character
                             ++pos;
                            }
                            isCurrencyFormat = true;
                            affix.append(doubled ? "'\u00A4\u00A4" : "'\u00A4");
                            continue;
                        } else if (ch == QUOTE) {
                            // A quote outside quotes indicates either the
                            // opening quote or two quotes, which is a quote
                            // literal. That is, we have the first quote in 'do'
                            // or o''clock.
                            if ((pos+1) < pattern.length() &&
                                pattern.charAt(pos+1) == QUOTE) {
                                ++pos;
                                affix.append("''"); // o''clock
                            } else {
                                inQuote = true; // 'do'
                            }
                            continue;
                        } else if (ch == separator) {
                            // Don't allow separators before we see digit
                            // characters of phase 1, and don't allow separators
                            // in the second pattern (j == 0).
                            if (phase == 0 || j == 0) {
                                throw new IllegalArgumentException("Unquoted special character '" +
                                    ch + "' in pattern \"" + pattern + '"');
                            }
                            start = pos + 1;
                            pos = pattern.length();
                            continue;
                        }

                        // Next handle characters which are appended directly.
                        else if (ch == percent) {
                            if (multiplier != 1) {
                                throw new IllegalArgumentException("Too many percent/per mille characters in pattern \"" +
                                    pattern + '"');
                            }
                            multiplier = 100;
                            affix.append("'%");
                            continue;
                        } else if (ch == perMill) {
                            if (multiplier != 1) {
                                throw new IllegalArgumentException("Too many percent/per mille characters in pattern \"" +
                                    pattern + '"');
                            }
                            multiplier = 1000;
                            affix.append("'\u2030");
                            continue;
                        } else if (ch == minus) {
                            affix.append("'-");
                            continue;
                        }
                    }
                    // Note that if we are within quotes, or if this is an
                    // unquoted, non-special character, then we usually fall
                    // through to here.
                    affix.append(ch);
                    break;

                case 1:
                    // The negative subpattern (j = 0) serves only to specify the
                    // negative prefix and suffix, so all the phase 1 characters
                    // e.g. digits, zeroDigit, groupingSeparator,
                    // decimalSeparator, exponent are ignored
                    if (j == 0) {
                        while (pos < pattern.length()) {
                            char negPatternChar = pattern.charAt(pos);
                            if (negPatternChar == digit
                                    || negPatternChar == zeroDigit
                                    || negPatternChar == groupingSeparator
                                    || negPatternChar == decimalSeparator) {
                                ++pos;
                            } else if (pattern.regionMatches(pos, exponent,
                                    0, exponent.length())) {
                                pos = pos + exponent.length();
                            } else {
                                // Not a phase 1 character, consider it as
                                // suffix and parse it in phase 2
                                --pos; //process it again in outer loop
                                phase = 2;
                                affix = suffix;
                                break;
                            }
                        }
                        continue;
                    }

                    // Process the digits, decimal, and grouping characters. We
                    // record five pieces of information. We expect the digits
                    // to occur in the pattern ####0000.####, and we record the
                    // number of left digits, zero (central) digits, and right
                    // digits. The position of the last grouping character is
                    // recorded (should be somewhere within the first two blocks
                    // of characters), as is the position of the decimal point,
                    // if any (should be in the zero digits). If there is no
                    // decimal point, then there should be no right digits.
                    if (ch == digit) {
                        if (zeroDigitCount > 0) {
                            ++digitRightCount;
                        } else {
                            ++digitLeftCount;
                        }
                        if (groupingCount >= 0 && decimalPos < 0) {
                            ++groupingCount;
                        }
                    } else if (ch == zeroDigit) {
                        if (digitRightCount > 0) {
                            throw new IllegalArgumentException("Unexpected '0' in pattern \"" +
                                pattern + '"');
                        }
                        ++zeroDigitCount;
                        if (groupingCount >= 0 && decimalPos < 0) {
                            ++groupingCount;
                        }
                    } else if (ch == groupingSeparator) {
                        groupingCount = 0;
                    } else if (ch == decimalSeparator) {
                        if (decimalPos >= 0) {
                            throw new IllegalArgumentException("Multiple decimal separators in pattern \"" +
                                pattern + '"');
                        }
                        decimalPos = digitLeftCount + zeroDigitCount + digitRightCount;
                    } else if (pattern.regionMatches(pos, exponent, 0, exponent.length())){
                        if (useExponentialNotation) {
                            throw new IllegalArgumentException("Multiple exponential " +
                                "symbols in pattern \"" + pattern + '"');
                        }
                        useExponentialNotation = true;
                        minExponentDigits = 0;

                        // Use lookahead to parse out the exponential part
                        // of the pattern, then jump into phase 2.
                        pos = pos+exponent.length();
                         while (pos < pattern.length() &&
                               pattern.charAt(pos) == zeroDigit) {
                            ++minExponentDigits;
                            ++pos;
                        }

                        if ((digitLeftCount + zeroDigitCount) < 1 ||
                            minExponentDigits < 1) {
                            throw new IllegalArgumentException("Malformed exponential " +
                                "pattern \"" + pattern + '"');
                        }

                        // Transition to phase 2
                        phase = 2;
                        affix = suffix;
                        --pos;
                        continue;
                    } else {
                        phase = 2;
                        affix = suffix;
                        --pos;
                        continue;
                    }
                    break;
                }
            }

            // Handle patterns with no '0' pattern character. These patterns
            // are legal, but must be interpreted.  "##.###" -> "#0.###".
            // ".###" -> ".0##".
            /* We allow patterns of the form "####" to produce a zeroDigitCount
             * of zero (got that?); although this seems like it might make it
             * possible for format() to produce empty strings, format() checks
             * for this condition and outputs a zero digit in this situation.
             * Having a zeroDigitCount of zero yields a minimum integer digits
             * of zero, which allows proper round-trip patterns.  That is, we
             * don't want "#" to become "#0" when toPattern() is called (even
             * though that's what it really is, semantically).
             */
            if (zeroDigitCount == 0 && digitLeftCount > 0 && decimalPos >= 0) {
                // Handle "###.###" and "###." and ".###"
                int n = decimalPos;
                if (n == 0) { // Handle ".###"
                    ++n;
                }
                digitRightCount = digitLeftCount - n;
                digitLeftCount = n - 1;
                zeroDigitCount = 1;
            }

            // Do syntax checking on the digits.
            if ((decimalPos < 0 && digitRightCount > 0) ||
                (decimalPos >= 0 && (decimalPos < digitLeftCount ||
                 decimalPos > (digitLeftCount + zeroDigitCount))) ||
                 groupingCount == 0 || inQuote) {
                throw new IllegalArgumentException("Malformed pattern \"" +
                    pattern + '"');
            }

            if (j == 1) {
                posPrefixPattern = prefix.toString();
                posSuffixPattern = suffix.toString();
                negPrefixPattern = posPrefixPattern;   // assume these for now
                negSuffixPattern = posSuffixPattern;
                int digitTotalCount = digitLeftCount + zeroDigitCount + digitRightCount;
                /* The effectiveDecimalPos is the position the decimal is at or
                 * would be at if there is no decimal. Note that if decimalPos<0,
                 * then digitTotalCount == digitLeftCount + zeroDigitCount.
                 */
                int effectiveDecimalPos = decimalPos >= 0 ?
                    decimalPos : digitTotalCount;
                setMinimumIntegerDigits(effectiveDecimalPos - digitLeftCount);
                setMaximumIntegerDigits(useExponentialNotation ?
                    digitLeftCount + getMinimumIntegerDigits() :
                    MAXIMUM_INTEGER_DIGITS);
                setMaximumFractionDigits(decimalPos >= 0 ?
                    (digitTotalCount - decimalPos) : 0);
                setMinimumFractionDigits(decimalPos >= 0 ?
                    (digitLeftCount + zeroDigitCount - decimalPos) : 0);
                setGroupingUsed(groupingCount > 0);
                this.groupingSize = (groupingCount > 0) ? groupingCount : 0;
                this.multiplier = multiplier;
                setDecimalSeparatorAlwaysShown(decimalPos == 0 ||
                    decimalPos == digitTotalCount);
            } else {
                negPrefixPattern = prefix.toString();
                negSuffixPattern = suffix.toString();
                gotNegative = true;
            }
        }

        if (pattern.isEmpty()) {
            posPrefixPattern = posSuffixPattern = "";
            setMinimumIntegerDigits(0);
            setMaximumIntegerDigits(MAXIMUM_INTEGER_DIGITS);
            setMinimumFractionDigits(0);
            // As maxFracDigits are fully displayed unlike maxIntDigits
            // Prevent OOME by setting to a much more reasonable value.
            setMaximumFractionDigits(DOUBLE_FRACTION_DIGITS);
        }

        // If there was no negative pattern, or if the negative pattern is
        // identical to the positive pattern, then prepend the minus sign to
        // the positive pattern to form the negative pattern.
        if (!gotNegative ||
            (negPrefixPattern.equals(posPrefixPattern)
             && negSuffixPattern.equals(posSuffixPattern))) {
            negSuffixPattern = posSuffixPattern;
            negPrefixPattern = "'-" + posPrefixPattern;
        }

        expandAffixes();
    }

    /**
     * {@inheritDoc NumberFormat}
     * @param newValue the maximum number of integer digits to be shown.
     * @see #getMaximumIntegerDigits()
     * @see ##digit_limits Integer and Fraction Digit Limits
     */
    @Override
    public void setMaximumIntegerDigits(int newValue) {
        maximumIntegerDigits = Math.clamp(newValue, 0, MAXIMUM_INTEGER_DIGITS);
        super.setMaximumIntegerDigits(Math.min(maximumIntegerDigits, DOUBLE_INTEGER_DIGITS));
        if (minimumIntegerDigits > maximumIntegerDigits) {
            minimumIntegerDigits = maximumIntegerDigits;
            super.setMinimumIntegerDigits(Math.min(minimumIntegerDigits, DOUBLE_INTEGER_DIGITS));
        }
        fastPathCheckNeeded = true;
    }

    /**
     * {@inheritDoc NumberFormat}
     * @param newValue the minimum number of integer digits to be shown.
     * @see #getMinimumIntegerDigits()
     * @see ##digit_limits Integer and Fraction Digit Limits
     */
    @Override
    public void setMinimumIntegerDigits(int newValue) {
        minimumIntegerDigits = Math.clamp(newValue, 0, MAXIMUM_INTEGER_DIGITS);
        super.setMinimumIntegerDigits(Math.min(minimumIntegerDigits, DOUBLE_INTEGER_DIGITS));
        if (minimumIntegerDigits > maximumIntegerDigits) {
            maximumIntegerDigits = minimumIntegerDigits;
            super.setMaximumIntegerDigits(Math.min(maximumIntegerDigits, DOUBLE_INTEGER_DIGITS));
        }
        fastPathCheckNeeded = true;
    }

    /**
     * {@inheritDoc NumberFormat}
     * @param newValue the maximum number of fraction digits to be shown.
     * @see #getMaximumFractionDigits()
     * @see ##digit_limits Integer and Fraction Digit Limits
     */
    @Override
    public void setMaximumFractionDigits(int newValue) {
        maximumFractionDigits = Math.clamp(newValue, 0, MAXIMUM_FRACTION_DIGITS);
        super.setMaximumFractionDigits(Math.min(maximumFractionDigits, DOUBLE_FRACTION_DIGITS));
        if (minimumFractionDigits > maximumFractionDigits) {
            minimumFractionDigits = maximumFractionDigits;
            super.setMinimumFractionDigits(Math.min(minimumFractionDigits, DOUBLE_FRACTION_DIGITS));
        }
        fastPathCheckNeeded = true;
    }

    /**
     * {@inheritDoc NumberFormat}
     * @param newValue the minimum number of fraction digits to be shown.
     * @see #getMinimumFractionDigits()
     * @see ##digit_limits Integer and Fraction Digit Limits
     */
    @Override
    public void setMinimumFractionDigits(int newValue) {
        minimumFractionDigits = Math.clamp(newValue, 0, MAXIMUM_FRACTION_DIGITS);
        super.setMinimumFractionDigits(Math.min(minimumFractionDigits, DOUBLE_FRACTION_DIGITS));
        if (minimumFractionDigits > maximumFractionDigits) {
            maximumFractionDigits = minimumFractionDigits;
            super.setMaximumFractionDigits(Math.min(maximumFractionDigits, DOUBLE_FRACTION_DIGITS));
        }
        fastPathCheckNeeded = true;
    }

    /**
     * {@inheritDoc NumberFormat}
     * <p>Unlike the other digit limits, {@code maximumIntegerDigits} is not
     * updated by {@code DecimalFormats} created or updated with a string pattern.
     * @see #setMaximumIntegerDigits
     * @see ##patterns Pattern Section
     * @see ##digit_limits Integer and Fraction Digit Limits
     */
    @Override
    public int getMaximumIntegerDigits() {
        return maximumIntegerDigits;
    }

    /**
     * {@inheritDoc NumberFormat}
     * @see #setMinimumIntegerDigits
     * @see ##digit_limits Integer and Fraction Digit Limits
     */
    @Override
    public int getMinimumIntegerDigits() {
        return minimumIntegerDigits;
    }

    /**
     * {@inheritDoc NumberFormat}
     * @see #setMaximumFractionDigits
     * @see ##digit_limits Integer and Fraction Digit Limits
     */
    @Override
    public int getMaximumFractionDigits() {
        return maximumFractionDigits;
    }

    /**
     * {@inheritDoc NumberFormat}
     * @see #setMinimumFractionDigits
     * @see ##digit_limits Integer and Fraction Digit Limits
     */
    @Override
    public int getMinimumFractionDigits() {
        return minimumFractionDigits;
    }

    /**
     * Gets the currency used by this decimal format when formatting
     * currency values.
     * The currency is obtained by calling
     * {@link DecimalFormatSymbols#getCurrency DecimalFormatSymbols.getCurrency}
     * on this number format's symbols.
     *
     * @return the currency used by this decimal format, or {@code null}
     * @since 1.4
     */
    @Override
    public Currency getCurrency() {
        return symbols.getCurrency();
    }

    /**
     * Sets the currency used by this number format when formatting
     * currency values. This does not update the minimum or maximum
     * number of fraction digits used by the number format.
     * The currency is set by calling
     * {@link DecimalFormatSymbols#setCurrency DecimalFormatSymbols.setCurrency}
     * on this number format's symbols.
     *
     * @param currency the new currency to be used by this decimal format
     * @throws    NullPointerException if {@code currency} is null
     * @since 1.4
     */
    @Override
    public void setCurrency(Currency currency) {
        if (currency != symbols.getCurrency()) {
            symbols.setCurrency(currency);
            if (isCurrencyFormat) {
                expandAffixes();
            }
        }
        fastPathCheckNeeded = true;
    }

    /**
     * Gets the {@link java.math.RoundingMode} used in this DecimalFormat.
     *
     * @return The {@code RoundingMode} used for this DecimalFormat.
     * @see #setRoundingMode(RoundingMode)
     * @since 1.6
     */
    @Override
    public RoundingMode getRoundingMode() {
        return roundingMode;
    }

    /**
     * Sets the {@link java.math.RoundingMode} used in this DecimalFormat.
     *
     * @param roundingMode The {@code RoundingMode} to be used
     * @see #getRoundingMode()
     * @throws    NullPointerException if {@code roundingMode} is null.
     * @since 1.6
     */
    @Override
    public void setRoundingMode(RoundingMode roundingMode) {
        if (roundingMode == null) {
            throw new NullPointerException();
        }

        this.roundingMode = roundingMode;
        digitList.setRoundingMode(roundingMode);
        fastPathCheckNeeded = true;
    }

    /**
     * Reads the default serializable fields from the stream and performs
     * validations and adjustments for older serialized versions. The
     * validations and adjustments are:
     * <ol>
     * <li>
     * Verify that the superclass's digit count fields correctly reflect
     * the limits imposed on formatting numbers other than
     * {@code BigInteger} and {@code BigDecimal} objects. These
     * limits are stored in the superclass for serialization compatibility
     * with older versions, while the limits for {@code BigInteger} and
     * {@code BigDecimal} objects are kept in this class.
     * If, in the superclass, the minimum or maximum integer digit count is
     * larger than {@code DOUBLE_INTEGER_DIGITS} or if the minimum or
     * maximum fraction digit count is larger than
     * {@code DOUBLE_FRACTION_DIGITS}, then the stream data is invalid
     * and this method throws an {@code InvalidObjectException}.
     * <li>
     * If {@code serialVersionOnStream} is less than 4, initialize
     * {@code roundingMode} to {@link java.math.RoundingMode#HALF_EVEN
     * RoundingMode.HALF_EVEN}.  This field is new with version 4.
     * <li>
     * If {@code serialVersionOnStream} is less than 3, then call
     * the setters for the minimum and maximum integer and fraction digits with
     * the values of the corresponding superclass getters to initialize the
     * fields in this class. The fields in this class are new with version 3.
     * <li>
     * If {@code serialVersionOnStream} is less than 1, indicating that
     * the stream was written by JDK 1.1, initialize
     * {@code useExponentialNotation}
     * to false, since it was not present in JDK 1.1.
     * <li>
     * Set {@code serialVersionOnStream} to the maximum allowed value so
     * that default serialization will work properly if this object is streamed
     * out again.
     * </ol>
     *
     * <p>Stream versions older than 2 will not have the affix pattern variables
     * {@code posPrefixPattern} etc.  As a result, they will be initialized
     * to {@code null}, which means the affix strings will be taken as
     * literal values.  This is exactly what we want, since that corresponds to
     * the pre-version-2 behavior.
     */
    @java.io.Serial
    private void readObject(ObjectInputStream stream)
         throws IOException, ClassNotFoundException
    {
        stream.defaultReadObject();
        digitList = new DigitList();

        // We force complete fast-path reinitialization when the instance is
        // deserialized. See clone() comment on fastPathCheckNeeded.
        fastPathCheckNeeded = true;
        isFastPath = false;
        fastPathData = null;

        if (serialVersionOnStream < 4) {
            setRoundingMode(RoundingMode.HALF_EVEN);
        } else {
            setRoundingMode(getRoundingMode());
        }

        // We only need to check the maximum counts because NumberFormat
        // .readObject has already ensured that the maximum is greater than the
        // minimum count.
        if (super.getMaximumIntegerDigits() > DOUBLE_INTEGER_DIGITS ||
            super.getMaximumFractionDigits() > DOUBLE_FRACTION_DIGITS) {
            throw new InvalidObjectException("Digit count out of range");
        }
        if (serialVersionOnStream < 3) {
            setMaximumIntegerDigits(super.getMaximumIntegerDigits());
            setMinimumIntegerDigits(super.getMinimumIntegerDigits());
            setMaximumFractionDigits(super.getMaximumFractionDigits());
            setMinimumFractionDigits(super.getMinimumFractionDigits());
        }
        if (serialVersionOnStream < 1) {
            // Didn't have exponential fields
            useExponentialNotation = false;
        }

        // Restore the invariant value if groupingSize is invalid.
        if (groupingSize < 0) {
            groupingSize = 3;
        }

        serialVersionOnStream = currentSerialVersion;
    }

    //----------------------------------------------------------------------
    // INSTANCE VARIABLES
    //----------------------------------------------------------------------

    private transient DigitList digitList = new DigitList();

    /**
     * The symbol used as a prefix when formatting positive numbers, e.g. "+".
     *
     * @serial
     * @see #getPositivePrefix
     */
    private String  positivePrefix = "";

    /**
     * The symbol used as a suffix when formatting positive numbers.
     * This is often an empty string.
     *
     * @serial
     * @see #getPositiveSuffix
     */
    private String  positiveSuffix = "";

    /**
     * The symbol used as a prefix when formatting negative numbers, e.g. "-".
     *
     * @serial
     * @see #getNegativePrefix
     */
    private String  negativePrefix = "-";

    /**
     * The symbol used as a suffix when formatting negative numbers.
     * This is often an empty string.
     *
     * @serial
     * @see #getNegativeSuffix
     */
    private String  negativeSuffix = "";

    /**
     * The prefix pattern for non-negative numbers.  This variable corresponds
     * to {@code positivePrefix}.
     *
     * <p>This pattern is expanded by the method {@code expandAffix()} to
     * {@code positivePrefix} to update the latter to reflect changes in
     * {@code symbols}.  If this variable is {@code null} then
     * {@code positivePrefix} is taken as a literal value that does not
     * change when {@code symbols} changes.  This variable is always
     * {@code null} for {@code DecimalFormat} objects older than
     * stream version 2 restored from stream.
     *
     * @serial
     * @since 1.3
     */
    private String posPrefixPattern;

    /**
     * The suffix pattern for non-negative numbers.  This variable corresponds
     * to {@code positiveSuffix}.  This variable is analogous to
     * {@code posPrefixPattern}; see that variable for further
     * documentation.
     *
     * @serial
     * @since 1.3
     */
    private String posSuffixPattern;

    /**
     * The prefix pattern for negative numbers.  This variable corresponds
     * to {@code negativePrefix}.  This variable is analogous to
     * {@code posPrefixPattern}; see that variable for further
     * documentation.
     *
     * @serial
     * @since 1.3
     */
    private String negPrefixPattern;

    /**
     * The suffix pattern for negative numbers.  This variable corresponds
     * to {@code negativeSuffix}.  This variable is analogous to
     * {@code posPrefixPattern}; see that variable for further
     * documentation.
     *
     * @serial
     * @since 1.3
     */
    private String negSuffixPattern;

    /**
     * The multiplier for use in percent, per mille, etc.
     *
     * @serial
     * @see #getMultiplier
     */
    private int     multiplier = 1;

    /**
     * The number of digits between grouping separators in the integer
     * portion of a number.  Must be non-negative and less than or equal to
     * {@link java.lang.Byte#MAX_VALUE Byte.MAX_VALUE} if
     * {@code NumberFormat.groupingUsed} is true.
     *
     * @serial
     * @see #getGroupingSize
     * @see java.text.NumberFormat#isGroupingUsed
     */
    private byte    groupingSize = 3;  // invariant, 0 - 127, if groupingUsed

    /**
     * If true, forces the decimal separator to always appear in a formatted
     * number, even if the fractional part of the number is zero.
     *
     * @serial
     * @see #isDecimalSeparatorAlwaysShown
     */
    private boolean decimalSeparatorAlwaysShown = false;

    /**
     * If true, parse returns BigDecimal wherever possible.
     *
     * @serial
     * @see #isParseBigDecimal
     * @since 1.5
     */
    private boolean parseBigDecimal = false;


    /**
     * True if this object represents a currency format.  This determines
     * whether the monetary decimal/grouping separators are used instead of the normal ones.
     */
    private transient boolean isCurrencyFormat = false;

    /**
     * The {@code DecimalFormatSymbols} object used by this format.
     * It contains the symbols used to format numbers, e.g. the grouping separator,
     * decimal separator, and so on.
     *
     * @serial
     * @see #setDecimalFormatSymbols
     * @see java.text.DecimalFormatSymbols
     */
    private DecimalFormatSymbols symbols = null; // LIU new DecimalFormatSymbols();

    /**
     * True to force the use of exponential (i.e. scientific) notation when formatting
     * numbers.
     *
     * @serial
     * @since 1.2
     */
    private boolean useExponentialNotation;  // Newly persistent in the Java 2 platform v.1.2

    /**
     * True if this {@code DecimalFormat} will parse numbers with strict
     * leniency.
     *
     * @serial
     * @since 23
     */
    private boolean parseStrict = false;

    /**
     * FieldPositions describing the positive prefix String. This is
     * lazily created. Use {@code getPositivePrefixFieldPositions}
     * when needed.
     */
    private transient FieldPosition[] positivePrefixFieldPositions;

    /**
     * FieldPositions describing the positive suffix String. This is
     * lazily created. Use {@code getPositiveSuffixFieldPositions}
     * when needed.
     */
    private transient FieldPosition[] positiveSuffixFieldPositions;

    /**
     * FieldPositions describing the negative prefix String. This is
     * lazily created. Use {@code getNegativePrefixFieldPositions}
     * when needed.
     */
    private transient FieldPosition[] negativePrefixFieldPositions;

    /**
     * FieldPositions describing the negative suffix String. This is
     * lazily created. Use {@code getNegativeSuffixFieldPositions}
     * when needed.
     */
    private transient FieldPosition[] negativeSuffixFieldPositions;

    /**
     * The minimum number of digits used to display the exponent when a number is
     * formatted in exponential notation.  This field is ignored if
     * {@code useExponentialNotation} is not true.
     *
     * @serial
     * @since 1.2
     */
    private byte    minExponentDigits;       // Newly persistent in the Java 2 platform v.1.2

    /**
     * The maximum number of digits allowed in the integer portion of a
     * {@code BigInteger} or {@code BigDecimal} number.
     * {@code maximumIntegerDigits} must be greater than or equal to
     * {@code minimumIntegerDigits}.
     *
     * @serial
     * @see #getMaximumIntegerDigits
     * @since 1.5
     */
    @SuppressWarnings("this-escape")
    private int    maximumIntegerDigits = super.getMaximumIntegerDigits();

    /**
     * The minimum number of digits allowed in the integer portion of a
     * {@code BigInteger} or {@code BigDecimal} number.
     * {@code minimumIntegerDigits} must be less than or equal to
     * {@code maximumIntegerDigits}.
     *
     * @serial
     * @see #getMinimumIntegerDigits
     * @since 1.5
     */
    @SuppressWarnings("this-escape")
    private int    minimumIntegerDigits = super.getMinimumIntegerDigits();

    /**
     * The maximum number of digits allowed in the fractional portion of a
     * {@code BigInteger} or {@code BigDecimal} number.
     * {@code maximumFractionDigits} must be greater than or equal to
     * {@code minimumFractionDigits}.
     *
     * @serial
     * @see #getMaximumFractionDigits
     * @since 1.5
     */
    @SuppressWarnings("this-escape")
    private int    maximumFractionDigits = super.getMaximumFractionDigits();

    /**
     * The minimum number of digits allowed in the fractional portion of a
     * {@code BigInteger} or {@code BigDecimal} number.
     * {@code minimumFractionDigits} must be less than or equal to
     * {@code maximumFractionDigits}.
     *
     * @serial
     * @see #getMinimumFractionDigits
     * @since 1.5
     */
    @SuppressWarnings("this-escape")
    private int    minimumFractionDigits = super.getMinimumFractionDigits();

    /**
     * The {@link java.math.RoundingMode} used in this DecimalFormat.
     *
     * @serial
     * @since 1.6
     */
    private RoundingMode roundingMode = RoundingMode.HALF_EVEN;

    // ------ DecimalFormat fields for fast-path for double algorithm  ------

    /**
     * Helper inner utility class for storing the data used in the fast-path
     * algorithm. Almost all fields related to fast-path are encapsulated in
     * this class.
     *
     * Any {@code DecimalFormat} instance has a {@code fastPathData}
     * reference field that is null unless both the properties of the instance
     * are such that the instance is in the "fast-path" state, and a format call
     * has been done at least once while in this state.
     *
     * Almost all fields are related to the "fast-path" state only and don't
     * change until one of the instance properties is changed.
     *
     * {@code firstUsedIndex} and {@code lastFreeIndex} are the only
     * two fields that are used and modified while inside a call to
     * {@code fastDoubleFormat}.
     *
     */
    private static class FastPathData {
        // --- Temporary fields used in fast-path, shared by several methods.

        /** The first unused index at the end of the formatted result. */
        int lastFreeIndex;

        /** The first used index at the beginning of the formatted result */
        int firstUsedIndex;

        // --- State fields related to fast-path status. Changes due to a
        //     property change only. Set by checkAndSetFastPathStatus() only.

        /** Difference between locale zero and default zero representation. */
        int  zeroDelta;

        /** Locale char for grouping separator. */
        char groupingChar;

        /**  Fixed index position of last integral digit of formatted result */
        int integralLastIndex;

        /**  Fixed index position of first fractional digit of formatted result */
        int fractionalFirstIndex;

        /** Fractional constants depending on decimal|currency state */
        double fractionalScaleFactor;
        int fractionalMaxIntBound;


        /** The char array buffer that will contain the formatted result */
        char[] fastPathContainer;

        /** Suffixes recorded as char array for efficiency. */
        char[] charsPositivePrefix;
        char[] charsNegativePrefix;
        char[] charsPositiveSuffix;
        char[] charsNegativeSuffix;
        boolean positiveAffixesRequired = true;
        boolean negativeAffixesRequired = true;
    }

    /** The format fast-path status of the instance. Logical state. */
    private transient boolean isFastPath = false;

    /** Flag stating need of check and reinit fast-path status on next format call. */
    private transient boolean fastPathCheckNeeded = true;

    /** DecimalFormat reference to its FastPathData */
    private transient FastPathData fastPathData;


    //----------------------------------------------------------------------

    static final int currentSerialVersion = 4;

    /**
     * The internal serial version which says which version was written.
     * Possible values are:
     * <ul>
     * <li><b>0</b> (default): versions before the Java 2 platform v1.2
     * <li><b>1</b>: version for 1.2, which includes the two new fields
     *      {@code useExponentialNotation} and
     *      {@code minExponentDigits}.
     * <li><b>2</b>: version for 1.3 and later, which adds four new fields:
     *      {@code posPrefixPattern}, {@code posSuffixPattern},
     *      {@code negPrefixPattern}, and {@code negSuffixPattern}.
     * <li><b>3</b>: version for 1.5 and later, which adds five new fields:
     *      {@code maximumIntegerDigits},
     *      {@code minimumIntegerDigits},
     *      {@code maximumFractionDigits},
     *      {@code minimumFractionDigits}, and
     *      {@code parseBigDecimal}.
     * <li><b>4</b>: version for 1.6 and later, which adds one new field:
     *      {@code roundingMode}.
     * </ul>
     * @since 1.2
     * @serial
     */
    private int serialVersionOnStream = currentSerialVersion;

    //----------------------------------------------------------------------
    // CONSTANTS
    //----------------------------------------------------------------------

    // ------ Fast-Path for double Constants ------

    /** Maximum valid integer value for applying fast-path algorithm */
    private static final double MAX_INT_AS_DOUBLE = (double) Integer.MAX_VALUE;

    /**
     * The digit arrays used in the fast-path methods for collecting digits.
     * Using 3 constants arrays of chars ensures a very fast collection of digits
     */
    private static class DigitArrays {
        static final char[] DigitOnes1000 = new char[1000];
        static final char[] DigitTens1000 = new char[1000];
        static final char[] DigitHundreds1000 = new char[1000];

        // initialize on demand holder class idiom for arrays of digits
        static {
            int tenIndex = 0;
            int hundredIndex = 0;
            char digitOne = '0';
            char digitTen = '0';
            char digitHundred = '0';
            for (int i = 0;  i < 1000; i++ ) {

                DigitOnes1000[i] = digitOne;
                if (digitOne == '9')
                    digitOne = '0';
                else
                    digitOne++;

                DigitTens1000[i] = digitTen;
                if (i == (tenIndex + 9)) {
                    tenIndex += 10;
                    if (digitTen == '9')
                        digitTen = '0';
                    else
                        digitTen++;
                }

                DigitHundreds1000[i] = digitHundred;
                if (i == (hundredIndex + 99)) {
                    digitHundred++;
                    hundredIndex += 100;
                }
            }
        }
    }
    // ------ Fast-Path for double Constants end ------

    // Constants for characters used in programmatic (unlocalized) patterns.
    private static final char       PATTERN_ZERO_DIGIT         = '0';
    private static final char       PATTERN_GROUPING_SEPARATOR = ',';
    private static final char       PATTERN_DECIMAL_SEPARATOR  = '.';
    private static final char       PATTERN_PER_MILLE          = '\u2030';
    private static final char       PATTERN_PERCENT            = '%';
    private static final char       PATTERN_DIGIT              = '#';
    private static final char       PATTERN_SEPARATOR          = ';';
    private static final String     PATTERN_EXPONENT           = "E";
    private static final char       PATTERN_MINUS              = '-';

    /**
     * The CURRENCY_SIGN is the standard Unicode symbol for currency.  It
     * is used in patterns and substituted with either the currency symbol,
     * or if it is doubled, with the international currency symbol.  If the
     * CURRENCY_SIGN is seen in a pattern, then the decimal/grouping separators
     * are replaced with the monetary decimal/grouping separators.
     *
     * The CURRENCY_SIGN is not localized.
     */
    private static final char       CURRENCY_SIGN = '\u00A4';

    private static final char       QUOTE = '\'';

    private static FieldPosition[] EmptyFieldPositionArray = new FieldPosition[0];

    // Upper limit on integer and fraction digits for a Java double
    static final int DOUBLE_INTEGER_DIGITS  = 309;
    static final int DOUBLE_FRACTION_DIGITS = 340;

    // Upper limit on integer and fraction digits for BigDecimal and BigInteger
    static final int MAXIMUM_INTEGER_DIGITS  = Integer.MAX_VALUE;
    static final int MAXIMUM_FRACTION_DIGITS = Integer.MAX_VALUE;

    // Proclaim JDK 1.1 serial compatibility.
    @java.io.Serial
    static final long serialVersionUID = 864413376551465018L;
}
