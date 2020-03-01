/*
 * Copyright (c) 2010, 2018, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 6712743 6991164 7168401
 * @summary verify package versions
 * @compile -XDignore.symbol.file Utils.java PackageVersionTest.java
 * @run main PackageVersionTest
 * @author ksrini
 */

package io.pack200;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.jar.JarFile;
import io.pack200.Pack200;
import io.pack200.Pack200.Packer;
import io.pack200.Pack200.Unpacker;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.*;

public class PackageVersionTest {
    private static final File  javaHome = new File(System.getProperty("java.home"));

    public final static int JAVA5_PACKAGE_MAJOR_VERSION = 150;
    public final static int JAVA5_PACKAGE_MINOR_VERSION = 7;

    public final static int JAVA6_PACKAGE_MAJOR_VERSION = 160;
    public final static int JAVA6_PACKAGE_MINOR_VERSION = 1;

    public final static int JAVA7_PACKAGE_MAJOR_VERSION = 170;
    public final static int JAVA7_PACKAGE_MINOR_VERSION = 1;

    @Test
    public void testNoIndyClasses() throws Exception {
        // a jar file devoid of indy classes must generate 160.1 package file
        createClassFile("Test7");
        verifyPack("target/Test7.class", JAVA6_PACKAGE_MAJOR_VERSION,
                JAVA6_PACKAGE_MINOR_VERSION);
    }

    @Test
    public void testNoClassFiles() throws Exception {
        // test for resource file, ie. no class files
        createClassFile("Test6");
        verifyPack("target/Test6.java", JAVA5_PACKAGE_MAJOR_VERSION,
                JAVA5_PACKAGE_MINOR_VERSION);
    }

    @Test
    public void verify6991164() {
        Unpacker unpacker = Pack200.newUnpacker();
        String versionStr = unpacker.toString();
        String expected = "Pack200, Vendor: " +
                System.getProperty("java.vendor") + ", Version: " +
                JAVA7_PACKAGE_MAJOR_VERSION + "." + JAVA7_PACKAGE_MINOR_VERSION;
        if (!versionStr.equals(expected)) {
            System.out.println("Expected: " + expected);
            System.out.println("Obtained: " + versionStr);
            throw new RuntimeException("did not get expected string " + expected);
        }
    }

    private void createClassFile(String name) {
        createJavaFile(name);
        String target = name.substring(name.length() - 1);
        String javacCmds[] = {
            "-source",
            "7",
            "-target",
            "7",
            "-Xlint:-options",
            "target/" + name + ".java"
        };
        TestUtils.compiler(javacCmds);
    }

    private void createJavaFile(String name) {
        PrintStream ps = null;
        FileOutputStream fos = null;
        File outputFile = new File("target", name + ".java");
        outputFile.delete();
        try {
            fos = new FileOutputStream(outputFile);
            ps = new PrintStream(fos);
            ps.format("public class %s {}", name);
        } catch (IOException ioe) {
            throw new RuntimeException("creation of test file failed");
        } finally {
            TestUtils.close(ps);
            TestUtils.close(fos);
        }
    }

    private void verifyPack(String filename, int expected_major, int expected_minor) throws Exception {
        File jarFileName = new File("target/test.jar");
        jarFileName.delete();
        String jargs[] = {
            "cvf",
            jarFileName.getAbsolutePath(),
            filename
        };
        TestUtils.jar(jargs);
        JarFile jfin = null;

        try {
            jfin = new JarFile(jarFileName);
            Packer packer = Pack200.newPacker();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            packer.pack(jfin, baos);
            baos.flush();
            baos.close();
            byte[] buf = baos.toByteArray();

            int minor = buf[4] & 0x000000ff;
            int major = buf[5] & 0x000000ff;

            assertEquals("Expected version", expected_major + "." + expected_minor, major + "." + minor);
            
        } finally {
            TestUtils.close(jfin);
            jarFileName.delete();
        }
    }
}
