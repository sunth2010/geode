/*=========================================================================
 * Copyright (c) 2010-2014 Pivotal Software, Inc. All Rights Reserved.
 * This product is protected by U.S. and international copyright
 * and intellectual property laws. Pivotal products are covered by
 * one or more patents listed at http://www.pivotal.io/patents.
 *=========================================================================
 */
package com.gemstone.gemfire.cache.query.cq.dunit;


import com.gemstone.gemfire.cache.query.internal.cq.CqServiceImpl;

import dunit.SerializableRunnable;

/**
 * Test class for testing {@link CqServiceImpl#EXECUTE_QUERY_DURING_INIT} flag
 *
 */
public class CqDataUsingPoolOptimizedExecuteDUnitTest extends CqDataUsingPoolDUnitTest{

  public CqDataUsingPoolOptimizedExecuteDUnitTest(String name) {
    super(name);
  }

  public void setUp() throws Exception {
    addExpectedException("Read timed out");
    addExpectedException("java.net.SocketException");
    super.setUp();
    invokeInEveryVM(new SerializableRunnable("set test hook") {
      public void run() {
        CqServiceImpl.EXECUTE_QUERY_DURING_INIT = false;
      }
    });
  }
  
  @Override
  public void tearDown2() throws Exception {
    invokeInEveryVM(new SerializableRunnable("getSystem") {
      public void run() {
        CqServiceImpl.EXECUTE_QUERY_DURING_INIT = true;
      }
    });
    super.tearDown2();
  }
}
