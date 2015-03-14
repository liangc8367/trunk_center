package test.com.bluesky.cloudmontain; 

import com.bluesky.cloudmontain.repeator.Repeator;
import com.bluesky.cloudmontain.database.SubscriberDatabase;
import com.bluesky.common.CallInformation;
import com.bluesky.common.UDPService;
import com.bluesky.protocol.CallData;
import com.bluesky.protocol.CallInit;
import com.bluesky.protocol.CallTerm;

import org.mockito.Mock;
import org.mockito.Mockito;
import static org.mockito.Mockito.*;
import org.mockito.runners.MockitoJUnitRunner;

import org.junit.runner.RunWith;
import org.junit.Test;


import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;


/** 
* Repeator Tester. 
* 
* @author <Authors name> 
* @since <pre>Feb 15, 2015</pre> 
* @version 1.0 
*/
@RunWith(MockitoJUnitRunner.class)
public class RepeatorTest { 

@Mock
UDPService udpSvcMock;

@Test
public void repeatTest1() {
    /// prepare online list
    List<SubscriberDatabase.OnlineRecord> onlines = new LinkedList<SubscriberDatabase.OnlineRecord>();
    long su1 = 10;
    long su2 = 20;
    long su3 = 30;
    long grp = 100;
    InetSocketAddress addr1 = new InetSocketAddress("host1", 100);
    InetSocketAddress addr2 = new InetSocketAddress("host2", 200);
    InetSocketAddress addr3= new InetSocketAddress("host2", 300);

    SubscriberDatabase.OnlineRecord record1 = new SubscriberDatabase.OnlineRecord(su1, addr1);
    onlines.add(record1);

    SubscriberDatabase.OnlineRecord record2 = new SubscriberDatabase.OnlineRecord(su2, addr2);
    onlines.add(record2);

    SubscriberDatabase.OnlineRecord record3 = new SubscriberDatabase.OnlineRecord(su3, addr3);
    onlines.add(record3);

    //
    Repeator rptr = new Repeator(udpSvcMock);
    CallInformation callInfo = new CallInformation();
    callInfo.mSourceId = su2;

    short seq = 1100;
    // test CallInit/CallTerm
    CallInit callInit = new CallInit(grp, su2, seq);
    rptr.repeat(onlines, callInfo, callInit);

    Mockito.verify(udpSvcMock, times(1)).send(eq(addr1), any(ByteBuffer.class));
    Mockito.verify(udpSvcMock, times(1)).send(eq(addr2), any(ByteBuffer.class));
    Mockito.verify(udpSvcMock, times(1)).send(eq(addr3), any(ByteBuffer.class));

    // test CallInit/CallTerm
    // code smell
    Mockito.reset(udpSvcMock);

    CallTerm callTerm = new CallTerm(grp, su2, (short)0);
    rptr.repeat(onlines, callInfo, callTerm);
    Mockito.verify(udpSvcMock, times(1)).send(eq(addr1), any(ByteBuffer.class));
    Mockito.verify(udpSvcMock, times(1)).send(eq(addr2), any(ByteBuffer.class));
    Mockito.verify(udpSvcMock, times(1)).send(eq(addr3), any(ByteBuffer.class));

    // test CallData
    Mockito.reset(udpSvcMock);
    ByteBuffer buf = ByteBuffer.allocate(1);
    CallData callData = new CallData(grp, su2, (short)0, buf);
    rptr.repeat(onlines, callInfo, callData);

    Mockito.verify(udpSvcMock, times(1)).send(eq(addr1), isA(ByteBuffer.class));
    Mockito.verify(udpSvcMock, times(0)).send(eq(addr2), isA(ByteBuffer.class));
    Mockito.verify(udpSvcMock, times(1)).send(eq(addr3), isA(ByteBuffer.class));

}

} 
