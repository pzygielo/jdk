/*
 * Copyright (c) 2002, 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2012, 2019 SAP SE. All rights reserved.
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
 *
 */

#include "asm/macroAssembler.inline.hpp"
#include "runtime/os.hpp"
#include "runtime/stubRoutines.hpp"
#include "runtime/vm_version.hpp"
#include "utilities/byteswap.hpp"

// Implementation of the platform-specific part of StubRoutines - for
// a description of how to extend it, see the stubRoutines.hpp file.


#define __ masm->

// CRC constant compute functions
static juint fold_byte(juint w, juint reverse_poly) {
  for (int i = 0; i < 8; i++) {
    int poly_if_odd = (-(w & 1)) & reverse_poly;
    w = (w >> 1) ^ poly_if_odd;
  }
  return w;
}

static juint fold_word(juint w, juint reverse_poly) {
  for (int i = 0; i < 32; i++) {
    int poly_if_odd = (-(w & 1)) & reverse_poly;
    w = (w >> 1) ^ poly_if_odd;
  }
  return w;
}

static julong numberOfLeadingZeros(julong p) {
  julong l = 1ull << 63;
  for (int i = 0; i < 64; ++i) {
    if (p & l) return i;
    l >>= 1;
  }
  return 64;
}

static julong compute_inverse_poly(julong long_poly) {
  // 2^64 / p
  julong mod = 0, div = 0;
  int d = numberOfLeadingZeros(long_poly);
  int s = d + 1;
  do {
    mod ^= (long_poly << s);
    div |= (1L << s);
    s = d - numberOfLeadingZeros(mod);
  } while (s >= 0);
  return div;
}

static address _crc_table_addr    = nullptr;
static address _crc32c_table_addr = nullptr;

address StubRoutines::crc_table_addr() {
  if (_crc_table_addr == nullptr) {
    _crc_table_addr = StubRoutines::ppc::generate_crc_constants(REVERSE_CRC32_POLY);
  }
  return _crc_table_addr;
}
address StubRoutines::crc32c_table_addr() {
  if (_crc32c_table_addr == nullptr) {
    _crc32c_table_addr = StubRoutines::ppc::generate_crc_constants(REVERSE_CRC32C_POLY);
  }
  return _crc32c_table_addr;
}

// Constants to fold n words as needed by macroAssembler.
address StubRoutines::ppc::generate_crc_constants(juint reverse_poly) {
  // Layout of constant table:
  // >= Power8: 1 table for single byte folding + constants for fast vector implementation
  const int vector_size = 16 * (CRC32_UNROLL_FACTOR2 + CRC32_UNROLL_FACTOR / CRC32_UNROLL_FACTOR2);

  const int size = CRC32_TABLE_SIZE + vector_size;
  const address consts = (address)os::malloc(size, mtInternal);
  if (consts == nullptr) {
    vm_exit_out_of_memory(size, OOM_MALLOC_ERROR, "CRC constants: no enough space");
  }
  juint* ptr = (juint*)consts;

  // Simple table used for single byte folding
  for (int i = 0; i < 256; ++i) {
    ptr[i] = fold_byte(i, reverse_poly);
  }

  // >= Power8: vector constants
  juint* ptr1 = (juint*)(consts + CRC32_TABLE_SIZE);
  guarantee(((intptr_t)ptr1 & 0xF) == 0, "16-byte alignment needed");

  // Generate constants for outer loop
  juint v0, v1, v2, v3 = 1;
  for (int i = 0; i < CRC32_UNROLL_FACTOR2 - 1; ++i) {
    v0 = fold_word(v3, reverse_poly);
    v1 = fold_word(v0, reverse_poly);
    v2 = fold_word(v1, reverse_poly);
    v3 = fold_word(v2, reverse_poly);
#ifdef VM_LITTLE_ENDIAN
    ptr1[4*i  ] = v3;
    ptr1[4*i+1] = v2;
    ptr1[4*i+2] = v3;
    ptr1[4*i+3] = v2;
#else
    ptr1[4*i  ] = v2;
    ptr1[4*i+1] = v3;
    ptr1[4*i+2] = v2;
    ptr1[4*i+3] = v3;
#endif
  }

  // Generate constants for inner loop
  juint* ptr2 = ptr1 + 4 * (CRC32_UNROLL_FACTOR2 - 1);
  v3 = 1; // Restart from scratch.
  for (int i = 0; i < CRC32_UNROLL_FACTOR; ++i) {
    v0 = fold_word(v3, reverse_poly);
    v1 = fold_word(v0, reverse_poly);
    v2 = fold_word(v1, reverse_poly);
    v3 = fold_word(v2, reverse_poly);
    if (i % CRC32_UNROLL_FACTOR2 == 0) {
      int idx = CRC32_UNROLL_FACTOR / CRC32_UNROLL_FACTOR2 - 1 - i / CRC32_UNROLL_FACTOR2;
      for (int j = 0; j < 4; ++j) {
#ifdef VM_LITTLE_ENDIAN
        ptr2[4*idx  ] = v3;
        ptr2[4*idx+1] = v2;
        ptr2[4*idx+2] = v1;
        ptr2[4*idx+3] = v0;
#else
        ptr2[4*idx  ] = v0;
        ptr2[4*idx+1] = v1;
        ptr2[4*idx+2] = v2;
        ptr2[4*idx+3] = v3;
#endif
      }
    }
  }

  // Constants to reduce 64 to 32 bit as needed by macroAssembler.
  juint* ptr3 = ptr2 + 4 * (CRC32_UNROLL_FACTOR / CRC32_UNROLL_FACTOR2);
  julong* c = (julong*)ptr3;
  julong long_poly = (((julong)reverse_poly) << 1) | 1;
  julong inverse_long_poly = compute_inverse_poly(long_poly);
#ifdef VM_LITTLE_ENDIAN
  c[0] = inverse_long_poly;
  c[1] = long_poly;
#else
  c[0] = long_poly;
  c[1] = inverse_long_poly;
#endif

#ifdef ASSERT
  if (reverse_poly == REVERSE_CRC32_POLY) {
    assert(INVERSE_REVERSE_CRC32_POLY == inverse_long_poly, "sanity");
  } else if (reverse_poly == REVERSE_CRC32C_POLY) {
    assert(INVERSE_REVERSE_CRC32C_POLY == inverse_long_poly, "sanity");
  }
#endif

  //printf("inv poly: 0x%016llx\n", (long long unsigned int)inverse_long_poly);

  return consts;
}
