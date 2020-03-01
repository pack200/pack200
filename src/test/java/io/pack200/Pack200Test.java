/*
 * Copyright (c) 2007, 2018, Oracle and/or its affiliates. All rights reserved.
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
  * @bug 6521334 6712743 8007902 8151901
  * @requires (sun.arch.data.model == "64" & os.maxMemory >= 4g)
  * @summary test general packer/unpacker functionality
  *          using native and java unpackers
  * @modules jdk.management
  *          jdk.zipfs
  * @compile -XDignore.symbol.file Utils.java Pack200Test.java
  * @run main/othervm/timeout=1200 -Xmx1280m -Xshare:off Pack200Test
  */

package io.pack200;

import java.util.*;
import java.io.*;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.jar.*;

import org.junit.Test;

/**
 * Tests the packing/unpacking via the APIs.
 */
public class Pack200Test {

    private static ArrayList <File> jarList = new ArrayList<File>();
    static final MemoryMXBean mmxbean = ManagementFactory.getMemoryMXBean();
    static final long m0 = getUsedMemory();
    static final int LEAK_TOLERANCE = 21000; // OS and GC related variations.
    // enable leak checks only if required, GC charecteristics vary on
    // platforms and this may not yield consistent results
    static final boolean LEAK_CHECK = Boolean.getBoolean("Pack200Test.enableLeakCheck");

    static long getUsedMemory() {
        mmxbean.gc();
        mmxbean.gc();
        mmxbean.gc();
        return mmxbean.getHeapMemoryUsage().getUsed()/1024;
    }

    private static void leakCheck() throws Exception {
        if (!LEAK_CHECK)
            return;
        long diff = getUsedMemory() - m0;
        System.out.println("  Info: memory diff = " + diff + "K");
        if (diff > LEAK_TOLERANCE) {
            throw new Exception("memory leak detected " + diff);
        }
    }

    private static void doPackUnpack() throws Exception {
        for (File in : jarList) {
            JarOutputStream javaUnpackerStream = null;
            JarOutputStream nativeUnpackerStream = null;
            JarFile jarFile = null;
            try {
                jarFile = new JarFile(in);

                // Write out to a test scratch area
                File packFile = new File("target", in.getName() + TestUtils.PACK_FILE_EXT);

                System.out.println("Packing [" + in.toString() + "]");
                // Call the packer
                TestUtils.pack(jarFile, packFile);
                System.out.println("Done Packing [" + in.toString() + "]");
                jarFile.close();
                System.out.println("Start leak check");
                leakCheck();

                System.out.println("  Unpacking using java unpacker");
                File javaUnpackedJar = new File("target", "java-" + in.getName());
                // Write out to current directory, jtreg will setup a scratch area
                javaUnpackerStream = new JarOutputStream(
                        new FileOutputStream(javaUnpackedJar));
                TestUtils.unpackj(packFile, javaUnpackerStream);
                javaUnpackerStream.close();
                System.out.println("  Testing...java unpacker");
                leakCheck();
                // Ok we have unpacked the file, lets test it.
                TestUtils.doCompareVerify(in.getAbsoluteFile(), javaUnpackedJar);

                System.out.println("  Unpacking using native unpacker");
                // Write out to current directory
                File nativeUnpackedJar = new File("target", "native-" + in.getName());
                nativeUnpackerStream = new JarOutputStream(
                        new FileOutputStream(nativeUnpackedJar));
                TestUtils.unpackn(packFile, nativeUnpackerStream);
                nativeUnpackerStream.close();
                System.out.println("  Testing...native unpacker");
                leakCheck();
                // the unpackers (native and java) should produce identical bits
                // so we use use bit wise compare, the verification compare is
                // very expensive wrt. time.
                TestUtils.doCompareBitWise(javaUnpackedJar, nativeUnpackedJar);
                System.out.println("Done.");
            } finally {
                TestUtils.close(nativeUnpackerStream);
                TestUtils.close(javaUnpackerStream);
                TestUtils.close(jarFile);
            }
        }
        TestUtils.cleanup(); // cleanup artifacts, if successful run
    }

    @Test
    public void testPackUnpack() throws Exception {
        // select the jars carefully, adding more jars will increase the
        // testing time.
        jarList.add(TestUtils.createRtJar());
        jarList.add(TestUtils.getGoldenJar());
        System.out.println(jarList);
        doPackUnpack();
    }
}
