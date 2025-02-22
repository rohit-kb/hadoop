/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.mapred;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * test SkipBadRecords
 * 
 * 
 */
public class TestSkipBadRecords {
  @Test
  @Timeout(value = 5)
  public void testSkipBadRecords() {
    // test default values
    Configuration conf = new Configuration();
    assertEquals(2, SkipBadRecords.getAttemptsToStartSkipping(conf));
    assertTrue(SkipBadRecords.getAutoIncrMapperProcCount(conf));
    assertTrue(SkipBadRecords.getAutoIncrReducerProcCount(conf));
    assertEquals(0, SkipBadRecords.getMapperMaxSkipRecords(conf));
    assertEquals(0, SkipBadRecords.getReducerMaxSkipGroups(conf), 0);
    assertNull(SkipBadRecords.getSkipOutputPath(conf));

    // test setters
    SkipBadRecords.setAttemptsToStartSkipping(conf, 5);
    SkipBadRecords.setAutoIncrMapperProcCount(conf, false);
    SkipBadRecords.setAutoIncrReducerProcCount(conf, false);
    SkipBadRecords.setMapperMaxSkipRecords(conf, 6L);
    SkipBadRecords.setReducerMaxSkipGroups(conf, 7L);
    JobConf jc= new JobConf();
    SkipBadRecords.setSkipOutputPath(jc, new Path("test"));
    
    // test getters 
    assertEquals(5, SkipBadRecords.getAttemptsToStartSkipping(conf));
    assertFalse(SkipBadRecords.getAutoIncrMapperProcCount(conf));
    assertFalse(SkipBadRecords.getAutoIncrReducerProcCount(conf));
    assertEquals(6L, SkipBadRecords.getMapperMaxSkipRecords(conf));
    assertEquals(7L, SkipBadRecords.getReducerMaxSkipGroups(conf), 0);
    assertEquals("test",SkipBadRecords.getSkipOutputPath(jc).toString());
    
  }

}
