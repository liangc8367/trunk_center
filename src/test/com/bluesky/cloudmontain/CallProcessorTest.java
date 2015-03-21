package test.com.bluesky.cloudmontain; 

import com.bluesky.cloudmontain.repeator.CallProcessor;
import com.bluesky.cloudmontain.repeator.Repeator;
import com.bluesky.common.SubscriberDatabase;
import com.bluesky.common.*;
import com.bluesky.protocol.CallData;
import com.bluesky.protocol.CallInit;
import com.bluesky.protocol.CallTerm;
import com.bluesky.protocol.ProtocolBase;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;

import static org.mockito.AdditionalMatchers.*;
import static org.mockito.Mockito.*;

import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Timer;

/** 
* CallProcessor Tester. 
* 
* @author <Authors name> 
* @since <pre>Feb 16, 2015</pre> 
* @version 1.0 
*/
@RunWith(MockitoJUnitRunner.class)
public class CallProcessorTest {

   @Mock
   Repeator rptr;
   @Mock
   SubscriberDatabase database;
   @Mock
   Timer timer;
   @Spy
   Timer spiedTimer = new Timer("tm");

   @Mock
   OLog logger;

   long su1 = 10;
   long su2 = 20;
   long su3 = 30;
   long grp = 100;
   long grp2 = 200;
   InetSocketAddress addr1 = new InetSocketAddress("host1", 100);
   InetSocketAddress addr2 = new InetSocketAddress("host2", 200);
   InetSocketAddress addr3= new InetSocketAddress("host2", 300);

   SubscriberDatabase.OnlineRecord records[] = {
           new SubscriberDatabase.OnlineRecord(su1, addr1),
           new SubscriberDatabase.OnlineRecord(su2, addr2),
           new SubscriberDatabase.OnlineRecord(su3, addr3)
   };

   CallProcessor cp;

   private void resetMocked(){
      Mockito.reset(rptr);
      Mockito.reset(database);
      Mockito.reset(timer);
      Mockito.reset(logger);
   }

   private void rxedCallInit(long target, long src, InetSocketAddress addr){

      CallInit callInit = new CallInit(target, src, (short)0);
      ByteBuffer payload = ByteBuffer.allocate(callInit.getSize());
      callInit.serialize(payload);
      DatagramPacket pkt = new DatagramPacket(payload.array(), payload.capacity());
      pkt.setSocketAddress(addr);

      cp.packetReceived(pkt);

   }

   private void rxedCallTerm(long target, long src, InetSocketAddress addr){

      CallTerm callTerm = new CallTerm(target, src, (short)0);
      ByteBuffer payload = ByteBuffer.allocate(callTerm.getSize());
      callTerm.serialize(payload);
      DatagramPacket pkt = new DatagramPacket(payload.array(), payload.capacity());
      pkt.setSocketAddress(addr);

      cp.packetReceived(pkt);

   }

   private void rxedCallData(long target, long src, InetSocketAddress addr){
      ByteBuffer buf = ByteBuffer.allocate(1);
      CallData callData = new CallData(target, src, (short)0, buf);
      ByteBuffer payload = ByteBuffer.allocate(callData.getSize());
      callData.serialize(payload);
      DatagramPacket pkt = new DatagramPacket(payload.array(), payload.capacity());
      pkt.setSocketAddress(addr);

      cp.packetReceived(pkt);
   }

   /** test IDLE state of the CP */
   @Test
   public void testIdle_MockTimer() throws Exception {
      /// prepare mocked dependencies
      resetMocked();

      Mockito.when(database.getOnlineMembers(anyLong())).thenReturn(Arrays.asList(records));

      /// create Cp
      cp = new CallProcessor(grp, su2, rptr, database, timer, logger);

      /// test idle -> init from su2
      rxedCallInit(grp, su2, addr2);

      // verify rptr was called
      CallInformation callInfo = new CallInformation();
      Mockito.verify(rptr, times(1)).repeat(
              anyListOf(SubscriberDatabase.OnlineRecord.class),
              any(CallInformation.class),
              isA(CallInit.class));

      // verify 20ms timer was set
      Mockito.verify(timer, times(1)).schedule(isA(NamedTimerTask.class),leq(GlobalConstants.CALL_PACKET_INTERVAL));

      // verify flywheel timer was set
      Mockito.verify(timer, times(1)).schedule(isA(NamedTimerTask.class),
              and(gt(GlobalConstants.CALL_PACKET_INTERVAL), leq(GlobalConstants.CALL_FLYWHEEL_PERIOD)));


   }

   /** test Init state
    *    - callInit from sender got repeated, flywheel and tx timer got set
    *    - callInit from others (based on IP) got discarded
    *    - callTerm from others (based on IP) got discarded
    *    - callData from others (based on IP) got discarded
    *    - callTerm from sender got repeated, flywheel and tx timer got set
    */
   @Test
   public void testInit_MockTimer() throws Exception {
      /// prepare mocked dependencies
      resetMocked();

      Mockito.when(database.getOnlineMembers(anyLong())).thenReturn(Arrays.asList(records));

      /// create Cp
      cp = new CallProcessor(grp, su2, rptr, database, timer, logger);

      /// test idle -> init from su2
      rxedCallInit(grp, su2, addr2);

      // verify rptr was called
      CallInformation callInfo = new CallInformation();
      Mockito.verify(rptr, times(1)).repeat(
              anyListOf(SubscriberDatabase.OnlineRecord.class),
              any(CallInformation.class),
              isA(CallInit.class));

      // verify 20ms timer was set
      Mockito.verify(timer, times(1)).schedule(isA(NamedTimerTask.class),leq(GlobalConstants.CALL_PACKET_INTERVAL));

      // verify flywheel timer was set
      Mockito.verify(timer, times(1)).schedule(isA(NamedTimerTask.class),
              and(gt(GlobalConstants.CALL_PACKET_INTERVAL), leq(GlobalConstants.CALL_FLYWHEEL_PERIOD)));

      /////////////////////////////////////////////////////////////////////////////
      /// callInit from sender got repeated, flywheel and tx timer got set
      /////////////////////////////////////////////////////////////////////////////
      resetMocked();
      rxedCallInit(grp, su2, addr2);

      Mockito.verify(rptr, times(1)).repeat(
              anyListOf(SubscriberDatabase.OnlineRecord.class),
              any(CallInformation.class),
              isA(CallInit.class));

      // verify 20ms timer was not set
      Mockito.verify(timer, times(1)).schedule(isA(NamedTimerTask.class),leq(GlobalConstants.CALL_PACKET_INTERVAL));

      // verify flywheel timer was set
      Mockito.verify(timer, times(1)).schedule(isA(NamedTimerTask.class),
              and(gt(GlobalConstants.CALL_PACKET_INTERVAL), leq(GlobalConstants.CALL_FLYWHEEL_PERIOD)));

      /////////////////////////////////////////////////////////////////////////////
      /// callInit from others (based on IP) got discarded
      /////////////////////////////////////////////////////////////////////////////
      resetMocked();
      rxedCallInit(grp, su2, addr1);

      Mockito.verify(rptr, times(0)).repeat(
              anyListOf(SubscriberDatabase.OnlineRecord.class),
              any(CallInformation.class),
              any(ProtocolBase.class));

      // verify 20ms timer was not set
      Mockito.verify(timer, times(0)).schedule(isA(NamedTimerTask.class),anyLong());

      /////////////////////////////////////////////////////////////////////////////
      /// callData from others (based on IP) got discarded
      /////////////////////////////////////////////////////////////////////////////
      resetMocked();
      rxedCallData(grp, su2, addr1);


      Mockito.verify(rptr, times(0)).repeat(
              anyListOf(SubscriberDatabase.OnlineRecord.class),
              any(CallInformation.class),
              any(ProtocolBase.class));

      // verify no timer was set
      Mockito.verify(timer, times(0)).schedule(isA(NamedTimerTask.class),anyLong());

      /////////////////////////////////////////////////////////////////////////////
      /// callTerm from others (based on IP) got discarded
      /////////////////////////////////////////////////////////////////////////////
      resetMocked();
      rxedCallTerm(grp, su1, addr2);

      Mockito.verify(rptr, times(0)).repeat(
              anyListOf(SubscriberDatabase.OnlineRecord.class),
              any(CallInformation.class),
              any(ProtocolBase.class));

      // verify no timer was set
      Mockito.verify(timer, times(0)).schedule(isA(NamedTimerTask.class),anyLong());


      /////////////////////////////////////////////////////////////////////////////
      /// callTerm from sender got repeated, flywheel and tx timer got set
      /////////////////////////////////////////////////////////////////////////////
      resetMocked();
      rxedCallTerm(grp, su2, addr2);

      Mockito.verify(rptr, times(1)).repeat(
              anyListOf(SubscriberDatabase.OnlineRecord.class),
              any(CallInformation.class),
              isA(CallTerm.class));

      // verify 20ms timer was not set
      Mockito.verify(timer, times(1)).schedule(isA(NamedTimerTask.class),leq(GlobalConstants.CALL_PACKET_INTERVAL));

      // verify flywheel timer was set
      Mockito.verify(timer, times(1)).schedule(isA(NamedTimerTask.class),
              and(gt(GlobalConstants.CALL_PACKET_INTERVAL), leq(GlobalConstants.CALL_HANG_PERIOD)));
   }

   /** test idle=>init, with single incoming CallInit
    *     - spy on real Timer
    */
   @Test
   public void testInit_SpyOnTimer() throws Exception {
      /// prepare mocked dependencies
      resetMocked();

      Mockito.when(database.getOnlineMembers(anyLong())).thenReturn(Arrays.asList(records));

      /// create Cp
      cp = new CallProcessor(grp, su2, rptr, database, spiedTimer, logger);

      /// test idle -> init from su2
      rxedCallInit(grp, su2, addr2);

      // wait flywheel
      Thread.currentThread().sleep(GlobalConstants.CALL_FLYWHEEL_PERIOD + GlobalConstants.CALL_HANG_PERIOD + 1000);

      // verify # of callInit
      int expPktNumber = (int)(GlobalConstants.CALL_FLYWHEEL_PERIOD / GlobalConstants.CALL_PACKET_INTERVAL);
      Mockito.verify(rptr, atMost(expPktNumber)).repeat(
              anyListOf(SubscriberDatabase.OnlineRecord.class),
              any(CallInformation.class),
              isA(CallInit.class));

      Mockito.verify(rptr, atLeast(expPktNumber - 10)).repeat(
              anyListOf(SubscriberDatabase.OnlineRecord.class),
              any(CallInformation.class),
              isA(CallInit.class));

      // verify # of callTerm
      expPktNumber = (int)(GlobalConstants.CALL_HANG_PERIOD / GlobalConstants.CALL_PACKET_INTERVAL);
      Mockito.verify(rptr, atMost(expPktNumber)).repeat(
              anyListOf(SubscriberDatabase.OnlineRecord.class),
              any(CallInformation.class),
              isA(CallTerm.class));

      Mockito.verify(rptr, atLeast(expPktNumber - 10)).repeat(
              anyListOf(SubscriberDatabase.OnlineRecord.class),
              any(CallInformation.class),
              isA(CallTerm.class));


   }


   /** test idle=>init=>Tx, with single incoming CallInit, using mock timer
    *    verify that:
    *     - callData from sender (based on IP) got repeated, flywheel got reset with flywheel value
    *     - callInit from sender got repeated, flywheel got reset with flywheel value
    *     - callData from other sender (based on IP) got discarded, even with the same grp/suid
    *     - callData from sender, but targeting to other grp, got discarded
    *     - callTerm from others got discarded
    *     - callInit from others got discarded
    *     - callTerm from sender got repeated, flywheel got reset with call-hang value
    */
   @Test
   public void testTxing_MockTimer() throws Exception {
      /// prepare mocked dependencies
      resetMocked();

      Mockito.when(database.getOnlineMembers(anyLong())).thenReturn(Arrays.asList(records));

      /// create Cp
      cp = new CallProcessor(grp, su2, rptr, database, timer, logger);

      /// test idle -> init from su2
      rxedCallInit(grp, su2, addr2);

      // verify callInit was repeated
      Mockito.verify(rptr, times(1)).repeat(
              anyListOf(SubscriberDatabase.OnlineRecord.class),
              any(CallInformation.class),
              isA(CallInit.class));

      /////////////////////////////////////////////////////////////////////////////
      /// verify callData from sender (based on IP) & grp got repeated, flywheel got reset with flywheel value
      /////////////////////////////////////////////////////////////////////////////
      resetMocked();

      rxedCallData(grp, su2, addr2);

      Mockito.verify(rptr, times(1)).repeat(
              anyListOf(SubscriberDatabase.OnlineRecord.class),
              any(CallInformation.class),
              isA(CallData.class));

      // verify 20ms timer was not set
      Mockito.verify(timer, times(0)).schedule(isA(NamedTimerTask.class),leq(GlobalConstants.CALL_PACKET_INTERVAL));

      // verify flywheel timer was set
      Mockito.verify(timer, times(1)).schedule(isA(NamedTimerTask.class),
              and(gt(GlobalConstants.CALL_PACKET_INTERVAL), leq(GlobalConstants.CALL_FLYWHEEL_PERIOD)));

      /////////////////////////////////////////////////////////////////////////////
      /// callInit from sender got repeated, flywheel got reset with flywheel value
      /////////////////////////////////////////////////////////////////////////////
      resetMocked();

      rxedCallInit(grp, su2, addr2);

      Mockito.verify(rptr, times(1)).repeat(
              anyListOf(SubscriberDatabase.OnlineRecord.class),
              any(CallInformation.class),
              isA(CallInit.class));

      // verify 20ms timer was not set
      Mockito.verify(timer, times(0)).schedule(isA(NamedTimerTask.class),leq(GlobalConstants.CALL_PACKET_INTERVAL));

      // verify flywheel timer was set
      Mockito.verify(timer, times(1)).schedule(isA(NamedTimerTask.class),
              and(gt(GlobalConstants.CALL_PACKET_INTERVAL), leq(GlobalConstants.CALL_FLYWHEEL_PERIOD)));

      /////////////////////////////////////////////////////////////////////////////
      /// callData from other sender (based on IP) got discarded, even with the same grp/suid
      /////////////////////////////////////////////////////////////////////////////
      resetMocked();

      rxedCallData(grp, su2, addr1);

      Mockito.verify(rptr, times(0)).repeat(
              anyListOf(SubscriberDatabase.OnlineRecord.class),
              any(CallInformation.class),
              any(ProtocolBase.class));

      // verify no timer was set
      Mockito.verify(timer, times(0)).schedule(isA(NamedTimerTask.class),anyLong());

      /////////////////////////////////////////////////////////////////////////////
      /// callData from sender, but targeting to other grp, got discarded
      /////////////////////////////////////////////////////////////////////////////

      resetMocked();

      rxedCallData(grp2, su2, addr2);

      Mockito.verify(rptr, times(0)).repeat(
              anyListOf(SubscriberDatabase.OnlineRecord.class),
              any(CallInformation.class),
              any(ProtocolBase.class));

      // verify no timer was set
      Mockito.verify(timer, times(0)).schedule(isA(NamedTimerTask.class),anyLong());

      /////////////////////////////////////////////////////////////////////////////
      /// callTerm from others got discarded
      /////////////////////////////////////////////////////////////////////////////
      resetMocked();

      rxedCallTerm(grp, su1, addr2);

      Mockito.verify(rptr, times(0)).repeat(
              anyListOf(SubscriberDatabase.OnlineRecord.class),
              any(CallInformation.class),
              any(ProtocolBase.class));

      // verify no timer was set
      Mockito.verify(timer, times(0)).schedule(isA(NamedTimerTask.class),anyLong());

      /////////////////////////////////////////////////////////////////////////////
      /// callInit from others got discarded
      /////////////////////////////////////////////////////////////////////////////
      resetMocked();

      rxedCallInit(grp, su1, addr2);

      Mockito.verify(rptr, times(0)).repeat(
              anyListOf(SubscriberDatabase.OnlineRecord.class),
              any(CallInformation.class),
              any(ProtocolBase.class));

      // verify no timer was set
      Mockito.verify(timer, times(0)).schedule(isA(NamedTimerTask.class),anyLong());

      /////////////////////////////////////////////////////////////////////////////
      /// callTerm from sender got repeated, flywheel and tx timer got set
      /////////////////////////////////////////////////////////////////////////////
      resetMocked();

      rxedCallTerm(grp, su2, addr2);

      Mockito.verify(rptr, times(1)).repeat(
              anyListOf(SubscriberDatabase.OnlineRecord.class),
              any(CallInformation.class),
              isA(CallTerm.class));

      // verify 20ms timer was not set
      Mockito.verify(timer, times(1)).schedule(isA(NamedTimerTask.class),leq(GlobalConstants.CALL_PACKET_INTERVAL));

      // verify flywheel timer was set
      Mockito.verify(timer, times(1)).schedule(isA(NamedTimerTask.class),
              and(gt(GlobalConstants.CALL_PACKET_INTERVAL), leq(GlobalConstants.CALL_HANG_PERIOD)));

   }

   /** test hang state, using mock timer
    *    verify that:
    *     - callTerm from sender got repeated, tx timer got set, but hang timer was not set
    *     - callData from sender (based on IP) got repeated, tx timer and hang flywheel got set
    *     - callData from other sender (based on IP) got discarded, even with the same grp/suid
    *     - callData from sender, but targeting to other grp, got discarded
    */
   @Test
   public void testHang_MockTimer(){
      /// prepare mocked dependencies
      resetMocked();

      Mockito.when(database.getOnlineMembers(anyLong())).thenReturn(Arrays.asList(records));

      /// create Cp
      cp = new CallProcessor(grp, su2, rptr, database, timer, logger);

      /// test idle -> init from su2
      rxedCallInit(grp, su2, addr2);

      // verify callInit was repeated
      Mockito.verify(rptr, times(1)).repeat(
              anyListOf(SubscriberDatabase.OnlineRecord.class),
              any(CallInformation.class),
              isA(CallInit.class));

      // transition init=>hang
      resetMocked();
      rxedCallTerm(grp, su2, addr2);

      Mockito.verify(rptr, times(1)).repeat(
              anyListOf(SubscriberDatabase.OnlineRecord.class),
              any(CallInformation.class),
              isA(CallTerm.class));

      // verify 20ms timer was not set
      Mockito.verify(timer, times(1)).schedule(isA(NamedTimerTask.class),leq(GlobalConstants.CALL_PACKET_INTERVAL));

      // verify flywheel timer was set
      Mockito.verify(timer, times(1)).schedule(isA(NamedTimerTask.class),
              and(gt(GlobalConstants.CALL_PACKET_INTERVAL), leq(GlobalConstants.CALL_HANG_PERIOD)));


      /////////////////////////////////////////////////////////////////////////////
      /// callTerm from sender got repeated, tx timer got set, but hang timer was not set
      /////////////////////////////////////////////////////////////////////////////
      resetMocked();
      rxedCallTerm(grp, su2, addr2);

      Mockito.verify(rptr, times(1)).repeat(
              anyListOf(SubscriberDatabase.OnlineRecord.class),
              any(CallInformation.class),
              isA(CallTerm.class));

      // verify 20ms timer was set
      Mockito.verify(timer, times(1)).schedule(isA(NamedTimerTask.class),leq(GlobalConstants.CALL_PACKET_INTERVAL));

      // verify flywheel timer was not set
      Mockito.verify(timer, times(0)).schedule(isA(NamedTimerTask.class),
              and(gt(GlobalConstants.CALL_PACKET_INTERVAL), leq(GlobalConstants.CALL_HANG_PERIOD)));

      /////////////////////////////////////////////////////////////////////////////
      /// callData from sender (based on IP) got repeated, tx timer and hang flywheel got set
      /////////////////////////////////////////////////////////////////////////////
      resetMocked();
      rxedCallData(grp, su2, addr2);

      Mockito.verify(rptr, times(1)).repeat(
              anyListOf(SubscriberDatabase.OnlineRecord.class),
              any(CallInformation.class),
              isA(CallData.class));

      // verify 20ms timer was set
      Mockito.verify(timer, times(0)).schedule(isA(NamedTimerTask.class),leq(GlobalConstants.CALL_PACKET_INTERVAL));

      // verify flywheel timer was not set
      Mockito.verify(timer, times(1)).schedule(isA(NamedTimerTask.class),
              and(gt(GlobalConstants.CALL_PACKET_INTERVAL), leq(GlobalConstants.CALL_HANG_PERIOD)));

      /////////////////////////////////////////////////////////////////////////////
      /// from other sender (based on IP) got discarded, even with the same grp/suid
      /////////////////////////////////////////////////////////////////////////////

      resetMocked();
      rxedCallData(grp, su2, addr1);

      Mockito.verify(rptr, times(0)).repeat(
              anyListOf(SubscriberDatabase.OnlineRecord.class),
              any(CallInformation.class),
              any(ProtocolBase.class));

      // verify 20ms timer was set
      Mockito.verify(timer, times(0)).schedule(isA(NamedTimerTask.class),anyLong());

      /////////////////////////////////////////////////////////////////////////////
      /// callData from sender, but targeting to other grp, got discarded
      /////////////////////////////////////////////////////////////////////////////

      resetMocked();
      rxedCallData(grp2, su2, addr2);

      Mockito.verify(rptr, times(0)).repeat(
              anyListOf(SubscriberDatabase.OnlineRecord.class),
              any(CallInformation.class),
              any(ProtocolBase.class));

      Mockito.verify(rptr, times(0)).repeat(
              anyListOf(SubscriberDatabase.OnlineRecord.class),
              any(CallInformation.class),
              any(ProtocolBase.class));

      // verify 20ms timer was set
      Mockito.verify(timer, times(0)).schedule(isA(NamedTimerTask.class),anyLong());
   }

   /** test hang state, using mock timer
    *    verify that:
    *     - callInit from sender to other grp got discarded, no timer got set
    *     - callInit from sender got repeated, flywheel and tx timer got set
    */
   @Test
   public void testHang2Init1_MockTimer(){

      /// prepare mocked dependencies
      resetMocked();

      Mockito.when(database.getOnlineMembers(anyLong())).thenReturn(Arrays.asList(records));

      /// create Cp
      cp = new CallProcessor(grp, su2, rptr, database, timer, logger);

      /// test idle -> init from su2
      rxedCallInit(grp, su2, addr2);

      // verify callInit was repeated
      Mockito.verify(rptr, times(1)).repeat(
              anyListOf(SubscriberDatabase.OnlineRecord.class),
              any(CallInformation.class),
              isA(CallInit.class));

      // transition init=>hang
      resetMocked();
      rxedCallTerm(grp, su2, addr2);

      Mockito.verify(rptr, times(1)).repeat(
              anyListOf(SubscriberDatabase.OnlineRecord.class),
              any(CallInformation.class),
              isA(CallTerm.class));

      // verify 20ms timer was not set
      Mockito.verify(timer, times(1)).schedule(isA(NamedTimerTask.class),leq(GlobalConstants.CALL_PACKET_INTERVAL));

      // verify flywheel timer was set
      Mockito.verify(timer, times(1)).schedule(isA(NamedTimerTask.class),
              and(gt(GlobalConstants.CALL_PACKET_INTERVAL), leq(GlobalConstants.CALL_HANG_PERIOD)));

      /////////////////////////////////////////////////////////////////////////////
      /// callInit from sender to other grp got discarded, no timer got set
      /////////////////////////////////////////////////////////////////////////////
      resetMocked();
      rxedCallInit(grp2, su2, addr2);

      Mockito.verify(rptr, times(0)).repeat(
              anyListOf(SubscriberDatabase.OnlineRecord.class),
              any(CallInformation.class),
              any(ProtocolBase.class));

      // verify no timer got set
      Mockito.verify(timer, times(0)).schedule(isA(NamedTimerTask.class),anyLong());

      /////////////////////////////////////////////////////////////////////////////
      /// callInit from sender got repeated, flywheel and tx timer got set
      /////////////////////////////////////////////////////////////////////////////
      resetMocked();
      rxedCallInit(grp, su2, addr2);

      // verify callInit was repeated
      Mockito.verify(rptr, times(1)).repeat(
              anyListOf(SubscriberDatabase.OnlineRecord.class),
              any(CallInformation.class),
              isA(CallInit.class));

      // verify 20ms timer was not set
      Mockito.verify(timer, times(1)).schedule(isA(NamedTimerTask.class),leq(GlobalConstants.CALL_PACKET_INTERVAL));

      // verify flywheel timer was set
      Mockito.verify(timer, times(1)).schedule(isA(NamedTimerTask.class),
              and(gt(GlobalConstants.CALL_PACKET_INTERVAL), leq(GlobalConstants.CALL_FLYWHEEL_PERIOD)));
   }

   /** test hang state, using mock timer
    *    verify that:
    *     - callInit from others to same grp got repeated, flywheel and tx timer got set
    */
   @Test
   public void testHang2Init2_MockTimer(){
      /// prepare mocked dependencies
      resetMocked();

      Mockito.when(database.getOnlineMembers(anyLong())).thenReturn(Arrays.asList(records));

      /// create Cp
      cp = new CallProcessor(grp, su2, rptr, database, timer, logger);

      /// test idle -> init from su2
      rxedCallInit(grp, su2, addr2);

      // verify callInit was repeated
      Mockito.verify(rptr, times(1)).repeat(
              anyListOf(SubscriberDatabase.OnlineRecord.class),
              any(CallInformation.class),
              isA(CallInit.class));

      // transition init=>hang
      resetMocked();
      rxedCallTerm(grp, su2, addr2);

      Mockito.verify(rptr, times(1)).repeat(
              anyListOf(SubscriberDatabase.OnlineRecord.class),
              any(CallInformation.class),
              isA(CallTerm.class));

      // verify 20ms timer was not set
      Mockito.verify(timer, times(1)).schedule(isA(NamedTimerTask.class),leq(GlobalConstants.CALL_PACKET_INTERVAL));

      // verify flywheel timer was set
      Mockito.verify(timer, times(1)).schedule(isA(NamedTimerTask.class),
              and(gt(GlobalConstants.CALL_PACKET_INTERVAL), leq(GlobalConstants.CALL_HANG_PERIOD)));

      /////////////////////////////////////////////////////////////////////////////
      /// callInit from others to same grp got repeated, flywheel and tx timer got set
      /////////////////////////////////////////////////////////////////////////////
      resetMocked();
      rxedCallInit(grp, su1, addr1);

      // verify callInit was repeated
      Mockito.verify(rptr, times(1)).repeat(
              anyListOf(SubscriberDatabase.OnlineRecord.class),
              any(CallInformation.class),
              isA(CallInit.class));

      // verify 20ms timer was not set
      Mockito.verify(timer, times(1)).schedule(isA(NamedTimerTask.class),leq(GlobalConstants.CALL_PACKET_INTERVAL));

      // verify flywheel timer was set
      Mockito.verify(timer, times(1)).schedule(isA(NamedTimerTask.class),
              and(gt(GlobalConstants.CALL_PACKET_INTERVAL), leq(GlobalConstants.CALL_FLYWHEEL_PERIOD)));

      //////// now, cp should be in callInit state for callInit from su1
      resetMocked();
      rxedCallInit(grp, su1, addr1);

      // verify callInit was repeated
      Mockito.verify(rptr, times(1)).repeat(
              anyListOf(SubscriberDatabase.OnlineRecord.class),
              any(CallInformation.class),
              isA(CallInit.class));

      // verify 20ms timer was not set
      Mockito.verify(timer, times(1)).schedule(isA(NamedTimerTask.class),leq(GlobalConstants.CALL_PACKET_INTERVAL));

      // verify flywheel timer was set
      Mockito.verify(timer, times(1)).schedule(isA(NamedTimerTask.class),
              and(gt(GlobalConstants.CALL_PACKET_INTERVAL), leq(GlobalConstants.CALL_FLYWHEEL_PERIOD)));

      //////// now, cp should be in callInit state for callInit from su1, verify callInit from su2 got discarded
      resetMocked();
      rxedCallInit(grp, su2, addr2);

      // verify callInit was repeated
      Mockito.verify(rptr, times(0)).repeat(
              anyListOf(SubscriberDatabase.OnlineRecord.class),
              any(CallInformation.class),
              any(ProtocolBase.class));

      // no timer was not set
      Mockito.verify(timer, times(0)).schedule(isA(NamedTimerTask.class), anyLong());

   }

   /**
* 
* Method: updateStateContext() 
* 
*/ 
@Test
public void testUpdateStateContext() throws Exception { 
/*
try { 
   Method method = CallProcessor.getClass().getMethod("updateStateContext"); 
   method.setAccessible(true); 
   method.invoke(<Object>, <Parameters>); 
} catch(NoSuchMethodException e) { 
} catch(IllegalAccessException e) { 
} catch(InvocationTargetException e) { 
} 
*/ 
} 


} 
