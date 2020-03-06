/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2018 SAP SE. All rights reserved.
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

/**
 * @test
 * @summary Validate and test -?, -h and --help flags. All tools in the jdk
 *          should take the same flags to display the help message. These
 *          flags should be documented in the printed help message. The
 *          tool should quit without error code after displaying the
 *          help message (if there  is no other problem with the command
 *          line).
 *          Also check that tools that used to accept -help still do
 *          so. Test that tools that never accepted -help don't do so
 *          in future. I.e., check that the tool returns with the same
 *          return code as called with an invalid flag, and does not
 *          print anything containing '-help' in that case.
 * @compile HelpFlagsTest.java
 * @run main HelpFlagsTest
 */

package io.pack200;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.junit.Test;

import static org.junit.Assert.*;

public class HelpFlagsTest extends TestHelper {

    private class ToolSpec {
        String name;

        List<String> commandLine;

        ToolSpec(String name, List<String> commandLine) {
            this.name = name;
            this.commandLine = commandLine;
        }

        TestResult run(String flag) {
            List<String> commandLine = new ArrayList<>(this.commandLine);
            commandLine.add(flag);

            TestResult tr = doExec(commandLine.toArray(new String[0]));
            System.out.println("Testing " + name);
            System.out.println("#> " + String.join(" ", this.commandLine) + " " + flag);
            tr.testOutput.forEach(System.out::println);
            System.out.println("#> echo $?");
            System.out.println(tr.exitValue);

            return tr;
        }
    }

    @Test
    public void testPack200HelpFlags() {
        ToolSpec tool = new ToolSpec("pack200",   Arrays.asList(TestUtils.getJavaCmd(), "-cp", "target/classes", "io.pack200.Driver"));
        testHelpFlags(tool);
    }

    @Test
    public void testUnpack200HelpFlags() {
        ToolSpec tool = new ToolSpec("unpack200", Arrays.asList(TestUtils.getJavaCmd(), "-cp", "target/classes", "io.pack200.Driver", "--unpack"));
        testHelpFlags(tool);
    }

    private void testHelpFlags(ToolSpec tool) {
        // The test analyses the help messages printed. It assumes english help messages.
        Locale.setDefault(Locale.ENGLISH);

        // Test for help flags to be supported.
        testTool(tool, "-?", 0);
        testTool(tool, "-h", 0);
        testTool(tool, "--help", 0);

        // Check that the return code is correct for an invalid flag.
        testInvalidFlag(tool, "-asdfxgr", 2);

        // Test for legacy -help flag.
        testLegacyFlag(tool, 0);
    }

    /**
     * Check whether 'flag' appears in 'line' as a word of itself. It must not
     * be a substring of a word, as then similar flags might be matched.
     * E.g.: --help matches in the documentation of --help-extra.
     * This works only with english locale, as some tools have translated
     * usage messages.
     */
    private boolean findFlagInLine(String line, String flag) {
        if (line.contains(flag) &&
            !line.contains("nknown") &&                       // Some tools say 'Unknown option "<flag>"',
            !line.contains("invalid flag") &&                 // 'invalid flag: <flag>'
            !line.contains("invalid option")) {               // or 'invalid option: <flag>'. Skip that.
            // There might be several appearances of 'flag' in
            // 'line'. (-h as substring of --help).
            int flagLen = flag.length();
            int lineLen = line.length();
            for (int i = line.indexOf(flag); i >= 0; i = line.indexOf(flag, i+1)) {
                // There should be a space before 'flag' in 'line', or it's right at the beginning.
                if (i > 0 &&
                    line.charAt(i-1) != ' ' &&
                    line.charAt(i-1) != '[' &&  // jarsigner
                    line.charAt(i-1) != '|' &&  // jstatd
                    line.charAt(i-1) != '\t') { // jjs
                    continue;
                }
                // There should be a space or comma after 'flag' in 'line', or it's just at the end.
                int posAfter = i + flagLen;
                if (posAfter < lineLen &&
                    line.charAt(posAfter) != ' ' &&
                    line.charAt(posAfter) != ',' &&
                    line.charAt(posAfter) != '[' && // jar
                    line.charAt(posAfter) != ']' && // jarsigner
                    line.charAt(posAfter) != ')' && // jfr
                    line.charAt(posAfter) != '|' && // jstatd
                    line.charAt(posAfter) != ':' && // jps
                    line.charAt(posAfter) != '"') { // keytool
                    continue;
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Checks whether tool supports flag 'flag' and documents it in the help message.
     */
    private void testTool(ToolSpec tool, String flag, int exitcode) {
        TestResult result = tool.run(flag);

        // Check that the tool accepted the flag.
        if (exitcode == 0 && !result.isOK()) {
            fail(tool.name + " " + flag + " has exit code " + result.exitValue);
        }

        // Check there is a help message listing the flag.
        boolean foundFlag = false;
        for (String y : result.testOutput) {
            if (!foundFlag && findFlagInLine(y, flag)) {
                foundFlag = true;
                System.out.println("Found documentation of '" + flag + "': '" + y.trim() +"'");
            }
        }
        if (!foundFlag) {
            fail(tool.name + " does not document " + flag + " in help message");
        }
    }

    /**
     * Test the tool supports legacy option -help, but does not document it.
     */
    private void testLegacyFlag(ToolSpec tool, int exitcode) {
        TestResult result = tool.run("-help");

        // Check that the tool accepted the flag.
        if (exitcode == 0 && !result.isOK()) {
            fail(tool.name + " -help has exit code " + result.exitValue);
        }

        // Check there is _no_ documentation of -help.
        boolean foundFlag = false;
        for (String y : result.testOutput) {
            if (!foundFlag && findFlagInLine(y, "-help")) {
                foundFlag = true;
                System.out.println("Found documentation of '-help': '" + y.trim() +"'");
            }
        }
        if (foundFlag) {
            fail(tool.name + " does document -help in help message. This legacy flag should not be documented");
        }
    }

    /**
     * Test that the tool exits with the exit code expected for
     * invalid flags. In general, one would expect this to be != 0,
     * but currently a row of tools exit with 0 in this case.
     * The output should not ask to get help with flag '-help'.
     */
    private void testInvalidFlag(ToolSpec tool, String flag, int exitcode) {
        TestResult result = tool.run(flag);

        // Check that the tool did exit with the expected return code.
        if (!((exitcode == result.exitValue) ||
              // Windows reports -1 where unix reports 255.
              (result.exitValue < 0 && exitcode == result.exitValue + 256))) {
            fail(tool.name + " " + flag + " should not be accepted. But it has exit code " + result.exitValue);
        }

        // Check there is _no_ documentation of -help.
        boolean foundFlag = false;
        for (String y : result.testOutput) {
            if (!foundFlag && findFlagInLine(y, "-help")) {  // javac
                foundFlag = true;
                System.out.println("Found documentation of '-help': '" + y.trim() +"'");
            }
        }

        if (foundFlag) {
            fail(tool.name + " does document -help in error message. This legacy flag should not be documented");
        }
    }
}
