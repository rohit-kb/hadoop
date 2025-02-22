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
package org.apache.hadoop.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestShutdownThreadsHelper {
  private Runnable sampleRunnable = new Runnable() {
    @Override
    public void run() {
      try {
        Thread.sleep(2 * ShutdownThreadsHelper.SHUTDOWN_WAIT_MS);
      } catch (InterruptedException ie)  {
        System.out.println("Thread interrupted");
      }
    }
  };

  @Test
  @Timeout(value = 3)
  public void testShutdownThread() {
    Thread thread = new Thread(sampleRunnable);
    thread.start();
    boolean ret = ShutdownThreadsHelper.shutdownThread(thread);
    boolean isTerminated = !thread.isAlive();
    assertEquals(ret, isTerminated, "Incorrect return value");
    assertTrue(isTerminated, "Thread is not shutdown");

  }

  @Test
  public void testShutdownThreadPool() throws InterruptedException {
    ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
    executor.execute(sampleRunnable);
    boolean ret = ShutdownThreadsHelper.shutdownExecutorService(executor);
    boolean isTerminated = executor.isTerminated();
    assertEquals(ret, isTerminated, "Incorrect return value");
    assertTrue(isTerminated, "ExecutorService is not shutdown");
  }
}
