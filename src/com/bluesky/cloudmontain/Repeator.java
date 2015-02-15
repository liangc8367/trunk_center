package com.bluesky.cloudmontain;

import com.bluesky.common.CallInformation;
import com.bluesky.common.UDPService;
import com.bluesky.protocol.ProtocolBase;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.ListIterator;

/** repeat protocol packets to downlink
 *  - for call init and call term, repeat to all grp members, including the sender
 *  - for call data, repeat to other grp members, excluding the sender
 * Created by liangc on 14/02/15.
 */
public class Repeator {
    public Repeator(UDPService udpService){
        mUdpSvc = udpService;
    }

    public void repeat(List<SubscriberDatabase.OnlineRecord> onlineSus, CallInformation callInfo, ProtocolBase proto){
        ByteBuffer payload = ByteBuffer.allocate(proto.getSize());
        proto.serialize(payload);

        short type = proto.getType();
        for(ListIterator<SubscriberDatabase.OnlineRecord> it = onlineSus.listIterator(); it.hasNext();){
            SubscriberDatabase.OnlineRecord record = it.next();
            if( record.su_id != callInfo.mSuid ){
                mUdpSvc.send(record.addr, payload);
            } else if ( type == ProtocolBase.PTYPE_CALL_TERM || type == ProtocolBase.PTYPE_CALL_INIT){
                mUdpSvc.send(record.addr, payload);
            }
        }
    }

    UDPService mUdpSvc;
}
