/*
 * Copyright (c) 2010, 2014, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.TimeZone;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import org.junit.Ignore;
import org.junit.Test;

/*
 * @test
 * @bug 6966740
 * @summary verify identical timestamps, unpacked in any timezone
 * @compile -XDignore.symbol.file Utils.java TimeStamp.java
 * @run main/othervm TimeStamp
 * @author ksrini
 */

/**
 * First we pack the file in some time zone say India, then we unpack the  file
 * in the current time zone, and ensure the timestamp recorded in the unpacked
 * jar are the same.
 */
public class TimeStampTest {
    static final TimeZone tz = TimeZone.getDefault();

    @Test
    @Ignore("Requires Java 9 (ZipEntry.get/setLocalTime)")
    public void testTimestamps() throws IOException {

        // make a local copy of our test file
        File srcFile = TestUtils.getGoldenJar();
        File goldenFile = new File("target", "golden.jar");
        TestUtils.copyFile(srcFile, goldenFile);

        JarFile goldenJarFile = new JarFile(goldenFile);
        File packFile = new File("target", "golden.pack");

        // set the test timezone and pack the file
        TimeZone.setDefault(TimeZone.getTimeZone("IST"));
        TestUtils.pack(goldenJarFile, packFile);
        TimeZone.setDefault(tz);   // reset the timezone

        // unpack in the  test timezone
        File istFile = new File("target", "golden.jar.java.IST");
        unpackJava(packFile, istFile);
        verifyJar(goldenFile, istFile);
        istFile.delete();

        // unpack in some other timezone
        File pstFile = new File("target", "golden.jar.java.PST");
        unpackJava(packFile, pstFile);
        verifyJar(goldenFile, pstFile);
        pstFile.delete();

        // repeat the test for unpack200 tool.
        istFile = new File("target", "golden.jar.native.IST");
        unpackNative(packFile, istFile);
        verifyJar(goldenFile, istFile);
        istFile.delete();

        pstFile = new File("target", "golden.jar.native.PST");
        unpackNative(packFile, pstFile);
        verifyJar(goldenFile, pstFile);
        pstFile.delete();
    }

    static void unpackNative(File packFile, File outFile) {
        String name = outFile.getName();
        String tzname = name.substring(name.lastIndexOf(".") + 1);
        HashMap<String, String> env = new HashMap<>();
        switch(tzname) {
            case "PST":
                env.put("TZ", "US/Pacific");
                break;
            case "IST":
                env.put("TZ", "Asia/Calcutta");
                break;
            default:
                throw new RuntimeException("not implemented: " + tzname);
        }
        List<String> cmdsList = new ArrayList<>();
        cmdsList.add(TestUtils.getJavaCmd());
        cmdsList.add("-cp");
        cmdsList.add("target/classes");
        cmdsList.add("io.pack200.Driver");
        cmdsList.add("--unpack");
        cmdsList.add(packFile.getAbsolutePath());
        cmdsList.add(outFile.getAbsolutePath());
        TestUtils.runExec(cmdsList, env);
    }

    static void unpackJava(File packFile, File outFile) throws IOException {
        String name = outFile.getName();
        String tzname = name.substring(name.lastIndexOf(".") + 1);
        JarOutputStream jos = null;
        try {
            TimeZone.setDefault(TimeZone.getTimeZone(tzname));
            jos = new JarOutputStream(new FileOutputStream(outFile));
            System.out.println("Using timezone: " + TimeZone.getDefault());
            TestUtils.unpackj(packFile, jos);
        } finally {
            TestUtils.close(jos);
            TimeZone.setDefault(tz); // always reset
        }
    }

    static void verifyJar(File f1, File f2) throws IOException {
        int errors = 0;
        JarFile jf1 = null;
        JarFile jf2 = null;
        try {
            jf1 = new JarFile(f1);
            jf2 = new JarFile(f2);
            System.out.println("Verifying: " + f1 + " and " + f2);
            for (JarEntry je1 : Collections.list(jf1.entries())) {
                JarEntry je2 = jf2.getJarEntry(je1.getName());
                if (je1.getTime() != je2.getTime()) {
                    System.out.println("Error:");
                    System.out.println("  expected:" + jf1.getName() + ":"
                            + je1.getName() + ":" + je1.getTime());
                    System.out.println("  obtained:" + jf2.getName() + ":"
                            + je2.getName() + ":" + je2.getTime());
                    errors++;
                }
            }
        } finally {
            TestUtils.close(jf1);
            TestUtils.close(jf2);
        }
        if (errors > 0) {
            throw new RuntimeException("FAIL:" + errors + " error(s) encounted");
        }
    }
}
