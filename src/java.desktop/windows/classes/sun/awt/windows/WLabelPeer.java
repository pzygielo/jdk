/*
 * Copyright (c) 1996, 2013, Oracle and/or its affiliates. All rights reserved.
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
package sun.awt.windows;

import java.awt.*;
import java.awt.peer.*;

final class WLabelPeer extends WComponentPeer implements LabelPeer {

    // ComponentPeer overrides

    @Override
    public Dimension getMinimumSize() {
        FontMetrics fm = getFontMetrics(((Label)target).getFont());
        String label = ((Label)target).getText();
        if (label == null)
            label = "";
        return new Dimension(fm.stringWidth(label) + 14, fm.getHeight() + 8);
    }

    native void lazyPaint();
    @Override
    synchronized void start() {
        super.start();
        // if need then paint label
        lazyPaint();
    }
    // LabelPeer implementation

    @Override
    public boolean shouldClearRectBeforePaint() {
        return false;
    }

    @Override
    public native void setText(String label);
    @Override
    public native void setAlignment(int alignment);

    // Toolkit & peer internals

    WLabelPeer(Label target) {
        super(target);
    }

    @Override
    native void create(WComponentPeer parent);

    @Override
    void initialize() {
        Label   l = (Label)target;

        String  txt = l.getText();
        if (txt != null) {
            setText(txt);
        }

        int align = l.getAlignment();
        if (align != Label.LEFT) {
            setAlignment(align);
        }

        Color bg = ((Component)target).getBackground();
        if (bg != null) {
            setBackground(bg);
        }

        super.initialize();
    }
}
