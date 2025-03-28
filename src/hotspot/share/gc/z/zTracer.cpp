/*
 * Copyright (c) 2016, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/shared/gcId.hpp"
#include "gc/z/zGeneration.inline.hpp"
#include "gc/z/zGlobals.hpp"
#include "gc/z/zPageType.hpp"
#include "gc/z/zStat.hpp"
#include "gc/z/zTracer.hpp"
#include "jfr/jfrEvents.hpp"
#include "runtime/safepointVerifiers.hpp"
#include "utilities/debug.hpp"
#include "utilities/macros.hpp"
#if INCLUDE_JFR
#include "jfr/metadata/jfrSerializer.hpp"
#endif

#if INCLUDE_JFR

class ZPageTypeConstant : public JfrSerializer {
public:
  virtual void serialize(JfrCheckpointWriter& writer) {
    writer.write_count(3);
    writer.write_key((u8)ZPageType::small);
    writer.write("Small");
    writer.write_key((u8)ZPageType::medium);
    writer.write("Medium");
    writer.write_key((u8)ZPageType::large);
    writer.write("Large");
  }
};

class ZStatisticsCounterTypeConstant : public JfrSerializer {
public:
  virtual void serialize(JfrCheckpointWriter& writer) {
    writer.write_count(ZStatCounter::count());
    for (ZStatCounter* counter = ZStatCounter::first(); counter != nullptr; counter = counter->next()) {
      writer.write_key(counter->id());
      writer.write(counter->name());
    }
  }
};

class ZStatisticsSamplerTypeConstant : public JfrSerializer {
public:
  virtual void serialize(JfrCheckpointWriter& writer) {
    writer.write_count(ZStatSampler::count());
    for (ZStatSampler* sampler = ZStatSampler::first(); sampler != nullptr; sampler = sampler->next()) {
      writer.write_key(sampler->id());
      writer.write(sampler->name());
    }
  }
};

static void register_jfr_type_serializers() {
  JfrSerializer::register_serializer(TYPE_ZPAGETYPETYPE,
                                     true /* permit_cache */,
                                     new ZPageTypeConstant());
  JfrSerializer::register_serializer(TYPE_ZSTATISTICSCOUNTERTYPE,
                                     true /* permit_cache */,
                                     new ZStatisticsCounterTypeConstant());
  JfrSerializer::register_serializer(TYPE_ZSTATISTICSSAMPLERTYPE,
                                     true /* permit_cache */,
                                     new ZStatisticsSamplerTypeConstant());
}

#endif // INCLUDE_JFR

ZMinorTracer::ZMinorTracer()
  : GCTracer(ZMinor) {}

ZMajorTracer::ZMajorTracer()
  : GCTracer(ZMajor) {}

void ZGenerationTracer::report_start(const Ticks& timestamp) {
  _start = timestamp;
}

void ZYoungTracer::report_end(const Ticks& timestamp) {
  NoSafepointVerifier nsv;

  EventZYoungGarbageCollection e(UNTIMED);
  e.set_gcId(GCId::current());
  e.set_tenuringThreshold(ZGeneration::young()->tenuring_threshold());
  e.set_starttime(_start);
  e.set_endtime(timestamp);
  e.commit();
}

void ZOldTracer::report_end(const Ticks& timestamp) {
  NoSafepointVerifier nsv;

  EventZOldGarbageCollection e(UNTIMED);
  e.set_gcId(GCId::current());
  e.set_starttime(_start);
  e.set_endtime(timestamp);
  e.commit();
}

void ZTracer::initialize() {
  JFR_ONLY(register_jfr_type_serializers();)
}

void ZTracer::send_stat_counter(const ZStatCounter& counter, uint64_t increment, uint64_t value) {
  NoSafepointVerifier nsv;

  JfrNonReentrant<EventZStatisticsCounter> e;
  if (e.should_commit()) {
    e.set_id(counter.id());
    e.set_increment(increment);
    e.set_value(value);
    e.commit();
  }
}

void ZTracer::send_stat_sampler(const ZStatSampler& sampler, uint64_t value) {
  NoSafepointVerifier nsv;

  JfrNonReentrant<EventZStatisticsSampler> e;
  if (e.should_commit()) {
    e.set_id(sampler.id());
    e.set_value(value);
    e.commit();
  }
}

void ZTracer::send_thread_phase(const char* name, const Ticks& start, const Ticks& end) {
  NoSafepointVerifier nsv;

  EventZThreadPhase e(UNTIMED);
  if (e.should_commit()) {
    e.set_gcId(GCId::current_or_undefined());
    e.set_name(name);
    e.set_starttime(start);
    e.set_endtime(end);
    e.commit();
  }
}

void ZTracer::send_thread_debug(const char* name, const Ticks& start, const Ticks& end) {
  NoSafepointVerifier nsv;

  EventZThreadDebug e(UNTIMED);
  if (e.should_commit()) {
    e.set_gcId(GCId::current_or_undefined());
    e.set_name(name);
    e.set_starttime(start);
    e.set_endtime(end);
    e.commit();
  }
}
