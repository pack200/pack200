/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

/*
 * @test
 * @bug 8003549 8007297
 * @summary tests class files instruction formats introduced in JSR-335
 * @compile -XDignore.symbol.file Utils.java InstructionTests.java
 * @run main InstructionTests
 * @author ksrini
 */
public class InstructionTests {

    /*
     * the following should produce invokestatic and invokespecial
     * on InterfaceMethodRefs vs. MethodRefs, packer/unpacker should work
     */
    @Test
    public void testInvokeOpCodes() throws Exception {
        List<String> scratch = new ArrayList<>();
        final String fname = "A";
        String javaFileName = fname + TestUtils.JAVA_FILE_EXT;
        scratch.add("interface I {");
        scratch.add("    default void forEach(){}");
        scratch.add("    static void next() {}");
        scratch.add("}");
        scratch.add("class A implements I {");
        scratch.add("    public void forEach(Object o){");
        scratch.add("        I.super.forEach();");
        scratch.add("        I.next();");
        scratch.add("    }");
        scratch.add("}");
        File cwd = new File("target");
        File javaFile = new File(cwd, javaFileName);
        TestUtils.createFile(javaFile, scratch);

        // -g to compare LVT and LNT entries
        TestUtils.compiler("-g", javaFile.getAbsolutePath());

        File propsFile = new File("target/pack.props");
        scratch.clear();
        scratch.add("io.pack200.class.format.error=error");
        scratch.add("pack.unknown.attribute=error");
        TestUtils.createFile(propsFile, scratch);
        // jar the file up
        File testjarFile = new File(cwd, "test" + TestUtils.JAR_FILE_EXT);
        TestUtils.jar("cvf", testjarFile.getAbsolutePath(), "target/test-classes");

        TestUtils.testWithRepack(testjarFile, "--config-file=" + propsFile.getAbsolutePath());
        
        javaFile.delete();
        propsFile.delete();
        testjarFile.delete();
    }
}
