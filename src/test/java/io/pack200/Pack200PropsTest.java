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
 * @bug 6575373 6969063
 * @summary verify default properties of the packer/unpacker and segment limit
 * @modules java.logging
 *          jdk.compiler
 *          jdk.zipfs
 * @compile -XDignore.symbol.file Utils.java Pack200Props.java
 * @run main Pack200Props
 * @author ksrini
 */

package io.pack200;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import io.pack200.Pack200;
import io.pack200.Pack200.Packer;
import org.junit.Test;

import java.util.logging.Logger;

/*
 * Run this against a large jar file, by default the packer should generate only
 * one segment, parse the output of the packer to verify if this is indeed true.
 */

public class Pack200PropsTest {

    final static Logger log = Logger.getLogger("Pack200Props");

    @Test
    public void verifySegmentLimit() throws Exception {
        File outFile = new File("target/test.pack");
        
        log.info("creating jar");
        File testJar = TestUtils.createRtJar();

        List<String> cmdsList = new ArrayList<>();
        cmdsList.add(TestUtils.getJavaCmd());
        cmdsList.add("-Xshare:off");
        cmdsList.add("-Xmx1280m");
        cmdsList.add("-cp");
        cmdsList.add("target/classes");
        cmdsList.add("io.pack200.Driver");
        cmdsList.add("--effort=1");
        cmdsList.add("--verbose");
        cmdsList.add("--no-gzip");
        cmdsList.add(outFile.getName());
        cmdsList.add(testJar.getAbsolutePath());
        List<String> outList = TestUtils.runExec(cmdsList);

        log.info("verifying");
        int count = 0;
        for (String line : outList) {
            System.out.println(line);
            if (line.matches(".*Transmitted.*files of.*input bytes in a segment of.*bytes")) {
                count++;
            }
        }
        log.info("fini");
        if (count == 0) {
            throw new RuntimeException("no segments or no output ????");
        } else if (count > 1) {
            throw new RuntimeException("multiple segments detected, expected 1");
        }
    }

    @Test
    public void verifyDefaults() {
        log.info("start");
        Map<String, String> expectedDefaults = new HashMap<>();
        Packer p = Pack200.newPacker();
        expectedDefaults.put("io.pack200.disable.native",
                p.FALSE);
        expectedDefaults.put("io.pack200.verbose", "0");
        expectedDefaults.put(p.CLASS_ATTRIBUTE_PFX + "CompilationID", "RUH");
        expectedDefaults.put(p.CLASS_ATTRIBUTE_PFX + "SourceID", "RUH");
        expectedDefaults.put(p.CODE_ATTRIBUTE_PFX + "CharacterRangeTable",
                "NH[PHPOHIIH]");
        expectedDefaults.put(p.CODE_ATTRIBUTE_PFX + "CoverageTable",
                "NH[PHHII]");
        expectedDefaults.put(p.DEFLATE_HINT, p.KEEP);
        expectedDefaults.put(p.EFFORT, "5");
        expectedDefaults.put(p.KEEP_FILE_ORDER, p.TRUE);
        expectedDefaults.put(p.MODIFICATION_TIME, p.KEEP);
        expectedDefaults.put(p.SEGMENT_LIMIT, "-1");
        expectedDefaults.put(p.UNKNOWN_ATTRIBUTE, p.PASS);

        Map<String, String> props = p.properties();
        int errors = 0;
        for (String key : expectedDefaults.keySet()) {
            String def = expectedDefaults.get(key);
            String x = props.get(key);
            if (x == null) {
                System.out.println("Error: key not found:" + key);
                errors++;
            } else {
                if (!def.equals(x)) {
                    System.out.println("Error: key " + key
                            + "\n  value expected: " + def
                            + "\n  value obtained: " + x);
                    errors++;
                }
            }
        }
        log.info("fini");
        if (errors > 0) {
            throw new RuntimeException(errors +
                    " error(s) encountered in default properties verification");
        }
    }
}

