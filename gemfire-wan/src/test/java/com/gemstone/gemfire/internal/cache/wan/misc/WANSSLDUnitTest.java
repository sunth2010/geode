package com.gemstone.gemfire.internal.cache.wan.misc;

import com.gemstone.gemfire.cache.Region;
import com.gemstone.gemfire.internal.cache.wan.WANTestBase;

import dunit.DistributedTestCase;

public class WANSSLDUnitTest extends WANTestBase{

  public WANSSLDUnitTest(String name) {
    super(name);
  }

  public void setUp() throws Exception {
    super.setUp();
  }
 
  public void testSenderSSLReceiverSSL(){
    Integer lnPort = (Integer)vm0.invoke(WANTestBase.class,
        "createFirstLocatorWithDSId", new Object[] { 1 });
    Integer nyPort = (Integer)vm1.invoke(WANTestBase.class,
        "createFirstRemoteLocator", new Object[] { 2, lnPort });

    vm2.invoke(WANTestBase.class, "createReceiverWithSSL", new Object[] { nyPort });

    vm4.invoke(WANTestBase.class, "createCacheWithSSL", new Object[] { lnPort });

    vm4.invoke(WANTestBase.class, "createSender", new Object[] { "ln", 2,
        false, 100, 10, false, false, null, true });

    vm2.invoke(WANTestBase.class, "createReplicatedRegion", new Object[] {
        testName + "_RR", null, isOffHeap() });

    vm4.invoke(WANTestBase.class, "startSender", new Object[] { "ln" });

    vm4.invoke(WANTestBase.class, "createReplicatedRegion", new Object[] {
        testName + "_RR", "ln", isOffHeap() });

    vm4.invoke(WANTestBase.class, "doPuts", new Object[] { testName + "_RR",
        1000 });

    vm2.invoke(WANTestBase.class, "validateRegionSize", new Object[] {
        testName + "_RR", 1000 });
  }
  
  public void testSenderNoSSLReceiverSSL() {
    addExpectedException("Unexpected IOException");
    addExpectedException("SSL Error");
    addExpectedException("Unrecognized SSL message");
    try {
      Integer lnPort = (Integer)vm0.invoke(WANTestBase.class,
          "createFirstLocatorWithDSId", new Object[] { 1 });
      Integer nyPort = (Integer)vm1.invoke(WANTestBase.class,
          "createFirstRemoteLocator", new Object[] { 2, lnPort });

      vm2.invoke(WANTestBase.class, "createReceiverWithSSL",
          new Object[] { nyPort });

      vm4.invoke(WANTestBase.class, "createCache", new Object[] { lnPort });

      vm4.invoke(WANTestBase.class, "createSender", new Object[] { "ln", 2,
          false, 100, 10, false, false, null, true });

      vm2.invoke(WANTestBase.class, "createReplicatedRegion", new Object[] {
          testName + "_RR", null, isOffHeap() });

      vm4.invoke(WANTestBase.class, "startSender", new Object[] { "ln" });

      vm4.invoke(WANTestBase.class, "createReplicatedRegion", new Object[] {
          testName + "_RR", "ln", isOffHeap() });

      vm4.invoke(WANTestBase.class, "doPuts", new Object[] { testName + "_RR",
          1000 });

      vm2.invoke(WANTestBase.class, "validateRegionSize", new Object[] {
          testName + "_RR", 1000 });
      fail("Expected exception as only Receiver is SSL enabled. Not Sender");
    }
    catch (Exception e) {
      assertTrue(e.getCause().getMessage().contains("Server expecting SSL connection"));
    }
  }
  
  public void testSenderSSLReceiverNoSSL(){
    addExpectedException("Acceptor received unknown");
    addExpectedException("failed accepting client");
      Integer lnPort = (Integer)vm0.invoke(WANTestBase.class,
          "createFirstLocatorWithDSId", new Object[] { 1 });
      Integer nyPort = (Integer)vm1.invoke(WANTestBase.class,
          "createFirstRemoteLocator", new Object[] { 2, lnPort });

      vm2.invoke(WANTestBase.class, "createReceiver", new Object[] { nyPort });

      vm4.invoke(WANTestBase.class, "createCacheWithSSL",
          new Object[] { lnPort });

      vm4.invoke(WANTestBase.class, "createSender", new Object[] { "ln", 2,
          false, 100, 10, false, false, null, true });

      vm2.invoke(WANTestBase.class, "createReplicatedRegion", new Object[] {
          testName + "_RR", null, isOffHeap() });

      vm4.invoke(WANTestBase.class, "startSender", new Object[] { "ln" });

      vm4.invoke(WANTestBase.class, "createReplicatedRegion", new Object[] {
          testName + "_RR", "ln", isOffHeap() });

      vm4.invoke(WANTestBase.class, "doPuts", new Object[] { testName + "_RR",
          1 });

      Boolean doesSizeMatch = (Boolean)vm2.invoke(WANSSLDUnitTest.class, "ValidateSSLRegionSize", new Object[] {
          testName + "_RR", 1 });
      
      assertFalse(doesSizeMatch);
  }
  
  public static boolean ValidateSSLRegionSize (String regionName, final int regionSize) {
      final Region r = cache.getRegion(Region.SEPARATOR + regionName);
      assertNotNull(r);
      WaitCriterion wc = new WaitCriterion() {
        public boolean done() {
          return false;
        }

        public String description() {
          return null;
        }
      };
      DistributedTestCase.waitForCriterion(wc, 2000, 500, false);
      
      if(r.size() == regionSize){
        return true;
      }
      return false;
  }
}
