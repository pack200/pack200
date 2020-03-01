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

/*
 * @test
 * @bug 8001163
 * @summary tests simple TypeAnnotations in classfiles
 * @compile -XDignore.symbol.file ../Utils.java
 *          TestTypeAnnotations.java TargetTypes.java
 *          TypeUseTarget.java Lambda.java
 * @run main TestTypeAnnotations
 * @author ksrini
 */

package io.pack200.typeannos;

import java.io.File;
import java.io.IOException;

import io.pack200.TestUtils;
import org.junit.Test;

public class TypeAnnotationsTest {
    
    @Test
    public void testAnnotations() throws IOException {
        File testFile = new File("target", "ta.jar");
        TestUtils.jar("cvf", testFile.getAbsolutePath(),
                "-C", TestUtils.TEST_CLS_DIR.getAbsolutePath() + "/io/pack200/typeannos",
                ".");
        TestUtils.testWithRepack(testFile, "--unknown-attribute=error");
    }
}
