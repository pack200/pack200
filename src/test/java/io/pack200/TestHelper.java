/*
 * Copyright (c) 2008, 2020, Oracle and/or its affiliates. All rights reserved.
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

package io.pack200;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * This class provides some common utilities for the launcher tests.
 */
class TestHelper {

    protected TestResult doExec(String...cmds) {
        return doExec(null, null, cmds);
    }

    /*
     * A method which executes a java cmd and returns the results in a container
     */
    protected TestResult doExec(Map<String, String> envToSet, Set<String> envToRemove, String...cmds) {
        String cmdStr = "";
        for (String x : cmds) {
            cmdStr = cmdStr.concat(x + " ");
        }
        ProcessBuilder pb = new ProcessBuilder(cmds);
        Map<String, String> env = pb.environment();
        if (envToRemove != null) {
            for (String key : envToRemove) {
                env.remove(key);
            }
        }
        if (envToSet != null) {
            env.putAll(envToSet);
        }
        BufferedReader rdr = null;
        try {
            List<String> outputList = new ArrayList<>();
            pb.redirectErrorStream(true);
            Process p = pb.start();
            rdr = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String in = rdr.readLine();
            while (in != null) {
                outputList.add(in);
                in = rdr.readLine();
            }
            p.waitFor();
            p.destroy();

            return new TestHelper.TestResult(cmdStr, p.exitValue(), outputList,
                    env, new Throwable("current stack of the test"));
        } catch (Exception ex) {
            ex.printStackTrace();
            throw new RuntimeException(ex.getMessage());
        }
    }

    /*
     * A class to encapsulate the test results and stuff, with some ease
     * of use methods to check the test results.
     */
    static class TestResult {
        PrintWriter status;
        StringWriter sw;
        int exitValue;
        List<String> testOutput;
        Map<String, String> env;
        Throwable t;
        boolean testStatus;
        int testExitValue = 0;

        public TestResult(String str, int rv, List<String> oList, Map<String, String> env, Throwable t) {
            sw = new StringWriter();
            status = new PrintWriter(sw);
            status.println("Executed command: " + str + "\n");
            exitValue = rv;
            testOutput = oList;
            this.env = env;
            this.t = t;
            testStatus = true;
        }

        void appendError(String x) {
            testStatus = false;
            testExitValue++;
            status.println("###TestError###: " + x);
        }

        void indentStatus(String x) {
            status.println("  " + x);
        }

        boolean isOK() {
            return exitValue == 0;
        }

        @Override
        public String toString() {
            status.println("++++Begin Test Info++++");
            status.println("Test Status: " + (testStatus ? "PASS" : "FAIL"));
            status.println("++++Test Environment++++");
            for (String x : env.keySet()) {
                indentStatus(x + "=" + env.get(x));
            }
            status.println("++++Test Output++++");
            for (String x : testOutput) {
                indentStatus(x);
            }
            status.println("++++Test Stack Trace++++");
            status.println(t.toString());
            for (StackTraceElement e : t.getStackTrace()) {
                indentStatus(e.toString());
            }
            status.println("++++End of Test Info++++");
            status.flush();
            String out = sw.toString();
            status.close();
            return out;
        }
    }
}
