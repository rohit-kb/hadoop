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

package org.apache.hadoop.mapreduce.v2.app.speculate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestDataStatistics {

  private static final double TOL = 0.001;

  @Test
  public void testEmptyDataStatistics() throws Exception {
    DataStatistics statistics = new DataStatistics();
    assertEquals(0, statistics.count(), TOL);
    assertEquals(0, statistics.mean(), TOL);
    assertEquals(0, statistics.var(), TOL);
    assertEquals(0, statistics.std(), TOL);
    assertEquals(0, statistics.outlier(1.0f), TOL);
  }
  
  @Test
  public void testSingleEntryDataStatistics() throws Exception {
    DataStatistics statistics = new DataStatistics(17.29);
    assertEquals(1, statistics.count(), TOL);
    assertEquals(17.29, statistics.mean(), TOL);
    assertEquals(0, statistics.var(), TOL);
    assertEquals(0, statistics.std(), TOL);
    assertEquals(17.29, statistics.outlier(1.0f), TOL);
  }
  
  @Test
  public void testMultiEntryDataStatistics() throws Exception {
    DataStatistics statistics = new DataStatistics();
    statistics.add(17);
    statistics.add(29);
    assertEquals(2, statistics.count(), TOL);
    assertEquals(23.0, statistics.mean(), TOL);
    assertEquals(36.0, statistics.var(), TOL);
    assertEquals(6.0, statistics.std(), TOL);
    assertEquals(29.0, statistics.outlier(1.0f), TOL);
 }
  
  @Test
  public void testUpdateStatistics() throws Exception {
    DataStatistics statistics = new DataStatistics(17);
    statistics.add(29);
    assertEquals(2, statistics.count(), TOL);
    assertEquals(23.0, statistics.mean(), TOL);
    assertEquals(36.0, statistics.var(), TOL);

    statistics.updateStatistics(17, 29);
    assertEquals(2, statistics.count(), TOL);
    assertEquals(29.0, statistics.mean(), TOL);
    assertEquals(0.0, statistics.var(), TOL);
  }
}
