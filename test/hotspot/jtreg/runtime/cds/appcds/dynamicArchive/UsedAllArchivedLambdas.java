/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

/*
 * @test
 * @summary Test exhaustive use of archived lambda proxy classes.
 * @requires vm.cds
 * @library /test/lib /test/hotspot/jtreg/runtime/cds/appcds
 *          /test/hotspot/jtreg/runtime/cds/appcds/dynamicArchive/test-classes
 * @build UsedAllArchivedLambdasApp jdk.test.whitebox.WhiteBox LambdaVerification
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar used_all_lambda.jar UsedAllArchivedLambdasApp LambdaVerification
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar WhiteBox.jar jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:. UsedAllArchivedLambdas
 */

import jdk.test.lib.helpers.ClassFileInstaller;

public class UsedAllArchivedLambdas extends DynamicArchiveTestBase {
    public static void main(String[] args) throws Exception {
        runTest(UsedAllArchivedLambdas::test);
    }

    static void test() throws Exception {
        String topArchiveName = getNewArchiveName();
        String appJar = ClassFileInstaller.getJarPath("used_all_lambda.jar");
        String mainClass = "UsedAllArchivedLambdasApp";
        String wbJar = ClassFileInstaller.getJarPath("WhiteBox.jar");
        String use_whitebox_jar = "-Xbootclasspath/a:" + wbJar;

        dump(topArchiveName,
            "-XX:+UnlockDiagnosticVMOptions",
            "-XX:+WhiteBoxAPI", "-Xlog:cds+dynamic=info",
            use_whitebox_jar,
            "-cp", appJar, mainClass)
            .assertNormalExit(output -> {
                output.shouldContain("Archiving hidden UsedAllArchivedLambdasApp$$Lambda")
                      .shouldHaveExitValue(0);
            });

        run(topArchiveName,
            "-XX:+UnlockDiagnosticVMOptions",
            "-XX:+WhiteBoxAPI",
            use_whitebox_jar,
            "-Xlog:aot=debug",
            "-Xlog:cds=debug",
            "-cp", appJar, mainClass, "run")
            .assertNormalExit(output -> {
                output.shouldContain("Used all dynamic archived lambda proxy classes for: UsedAllArchivedLambdasApp run()Ljava/lang/Runnable;")
                      .shouldHaveExitValue(0);
            });
    }
}
