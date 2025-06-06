/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

import java.util.*;
import sun.security.util.LocalizedMessage;
import java.text.MessageFormat;

/*
 * @test
 * @bug 8168075 8196215
 * @summary Ensure that security message formatting code is capable of
 *     displaying all messages.
 * @modules java.base/sun.security.util
 *          java.base/sun.security.util.resources
 * @run main MessageFormatting
 * @run main/othervm -Duser.country=SA -Duser.language=ar MessageFormatting
 */

public class MessageFormatting {

    private static final Object[] MSG_ARGS = new Integer[]{0, 1, 2};

    public static void main(String[] args) throws Exception {

        ListResourceBundle resources = new sun.security.util.resources.security();
        Enumeration<String> keys = resources.getKeys();
        while (keys.hasMoreElements()) {
            String curKey = keys.nextElement();
            String formattedString =
                LocalizedMessage.getNonlocalized(curKey, MSG_ARGS);
            String msg = resources.getString(curKey);
            String expectedString = formatIfNecessary(msg, MSG_ARGS);
            if (!formattedString.equals(expectedString)) {
                System.err.println("Expected string:");
                System.err.println(expectedString);
                System.err.println("Actual string:");
                System.err.println(formattedString);
                throw new Exception("Incorrect message string");
            }
        }
    }

    private static String formatIfNecessary(String str, Object[] args) {
        // message formatting code only formats messages with arguments
        if (str.indexOf('{') < 0) {
            return str;
        }
        Locale loc = Locale.of("en", "US");
        MessageFormat format = new MessageFormat(str, loc);
        return format.format(args);
    }
}
