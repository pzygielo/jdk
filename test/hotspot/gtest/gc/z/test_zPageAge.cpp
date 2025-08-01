/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/z/zPageAge.hpp"
#include "unittest.hpp"

TEST(ZPageAgeRangeTest, test) {
  ZPageAgeRange rangeEden = ZPageAgeRangeEden;
  EXPECT_EQ(rangeEden.first(), ZPageAge::eden);
  EXPECT_EQ(rangeEden.last(), ZPageAge::eden);

  ZPageAgeRange rangeYoung = ZPageAgeRangeYoung;
  EXPECT_EQ(rangeYoung.first(), ZPageAge::eden);
  EXPECT_EQ(rangeYoung.last(), ZPageAge::survivor14);

  ZPageAgeRange rangeSurvivor = ZPageAgeRangeSurvivor;
  EXPECT_EQ(rangeSurvivor.first(), ZPageAge::survivor1);
  EXPECT_EQ(rangeSurvivor.last(), ZPageAge::survivor14);

  ZPageAgeRange rangeRelocation = ZPageAgeRangeRelocation;
  EXPECT_EQ(rangeRelocation.first(), ZPageAge::survivor1);
  EXPECT_EQ(rangeRelocation.last(), ZPageAge::old);

  ZPageAgeRange rangeOld = ZPageAgeRangeOld;
  EXPECT_EQ(rangeOld.first(), ZPageAge::old);
  EXPECT_EQ(rangeOld.last(), ZPageAge::old);

  ZPageAgeRange rangeAll = ZPageAgeRangeAll;
  EXPECT_EQ(rangeAll.first(), ZPageAge::eden);
  EXPECT_EQ(rangeAll.last(), ZPageAge::old);
}
