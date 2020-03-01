/*
 * Copyright (c) 2012, 2014, Oracle and/or its affiliates. All rights reserved.
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
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

/*
 * @test
 * @bug 7184145
 * @summary tests repacking of a simple named jarfile.
 * @compile -XDignore.symbol.file Utils.java RepackTest.java
 * @run main RepackTest
 * @author ksrini
 */
public class RepackTest {

    /*
     * there are two cases we need to test, where the file in question is
     * orphaned, ie. without a parent ie. not qualified by a parent path
     * relative nor absolute
     * case 1: src and dest are the same
     * case 2: src and dest are different
     */
    @Test
    public void testRepack() throws IOException {

        // make a copy of the test specimen to local directory
        File testFile = new File("target/src_tools.jar");
        TestUtils.copyFile(TestUtils.getGoldenJar(), testFile);
        List<String> cmdsList = new ArrayList<>();

        // case 1:
        cmdsList.add(TestUtils.getJavaCmd());
        cmdsList.add("-cp");
        cmdsList.add("target/classes");
        cmdsList.add("io.pack200.Driver");
        cmdsList.add("--repack");
        cmdsList.add(testFile.getAbsolutePath());
        TestUtils.runExec(cmdsList);

        // case 2:
        File dstFile = new File("target/dst_tools.jar");
        cmdsList.clear();
        cmdsList.add(TestUtils.getJavaCmd());
        cmdsList.add("-cp");
        cmdsList.add("target/classes");
        cmdsList.add("io.pack200.Driver");
        cmdsList.add("--repack");
        cmdsList.add(dstFile.getAbsolutePath());
        cmdsList.add(testFile.getAbsolutePath());
        TestUtils.runExec(cmdsList);

        // tidy up
        testFile.delete();
        dstFile.delete();
    }
}
