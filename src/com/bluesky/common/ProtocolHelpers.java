package com.bluesky.common;

import com.bluesky.common.UDPService;
import com.bluesky.protocol.*;

import java.net.DatagramPacket;
import java.util.logging.Logger;

/**
 * misc helper func to process protocol
 * Created by liangc on 11/01/15.
 */
public class ProtocolHelpers {
    static public void peepProtocol(DatagramPacket packet){
        ProtocolBase proto = ProtocolFactory.getProtocol(packet);
        switch( proto.getType() ){
            case ProtocolBase.PTYPE_REGISTRATION:
                LOGGER.info(((Registration)proto).toString());
                break;
            case ProtocolBase.PTYPE_ACK:
                LOGGER.info(((Ack)proto).toString());
                break;
            case ProtocolBase.PTYPE_CALL_INIT:
                LOGGER.info(((CallInit)proto).toString());
                break;
            case ProtocolBase.PTYPE_CALL_DATA:
                LOGGER.info(((CallData)proto).toString());
                break;
            case ProtocolBase.PTYPE_CALL_TERM:
                LOGGER.info(((CallTerm)proto).toString());
                break;
            default:
                LOGGER.warning(TAG + "unknown type: " + proto.getType());
        }
    }


    static final String TAG = "ProtoHelper: ";
    static final Logger LOGGER  = Logger.getLogger(UDPService.class.getName());
}
