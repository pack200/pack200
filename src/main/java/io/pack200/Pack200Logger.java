/*
 * Copyright (c) 2003, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

import java.util.logging.Level;
import java.util.logging.Logger;

import static io.pack200.Utils.*;

class Pack200Logger {

    static final Pack200Logger log = new Pack200Logger("io.pack200.Pack200");

    private final String name;
    private Logger logger;
    Pack200Logger(String name) {
        this.name = name;
    }

    private synchronized Logger getLogger() {
        if (logger == null) {
            logger = Logger.getLogger(name);
        }
        return logger;
    }

    public void warning(String msg, Object param) {
            getLogger().log(Level.WARNING, msg, param);
        }

    public void warning(String msg) {
        warning(msg, null);
    }

    public void info(String msg) {
        int verbose = currentPropMap().getInteger(DEBUG_VERBOSE);
        if (verbose > 0) {
            if (nolog) {
                System.out.println(msg);
            } else {
                getLogger().info(msg);
            }
        }
    }

    public void fine(String msg) {
        int verbose = currentPropMap().getInteger(DEBUG_VERBOSE);
        if (verbose > 0) {
                System.out.println(msg);
        }
    }
}
