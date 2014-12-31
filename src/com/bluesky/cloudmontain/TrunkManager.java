package com.bluesky.cloudmontain;

import java.net.DatagramPacket;
import java.net.InetSocketAddress;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * Trunk Manager, responsbile for:
 *  - registration
 *  - call management
 *      + call validation
 *      + call setup
 *      + call maintainance
 *      + call teardown
 *
 * Created by liangc on 31/12/14.
 */
public class TrunkManager {
    /** public methods */

    public TrunkManager(){
        // create udp service
        UDPService.Configuration udpSvcConfig = new UDPService.Configuration();
        //TODO: read configuration from database
        int port    = 32000;
        udpSvcConfig.addrLocal = new InetSocketAddress(port);
        mUdpService = new UDPService(udpSvcConfig);
    }

    public void start(){
        mUdpService.start();
    }

    public void stop(){

    }

    /** private inner classes */
    private class UdpRxHandler implements UDPService.CompletionHandler {
        @Override
        public void completed(DatagramPacket packet){
            TrunkManagerMessage msg = new TrunkManagerMessage(TrunkManagerMessage.MSG_RXED_PACKET, packet);
            mMsgQueue.put(msg);
        }
    }

    private class TrunkManagerMessage {
        public TrunkManagerMessage(int messageType){
            mMessageType = messageType;
            mObject = obj;
        }

        public TrunkManagerMessage(int messageType, Object obj){
            mMessageType = messageType;
            mObject = obj;
        }

        public int getType(){
            return mMessageType;
        }

        public Object getObject(){
            return mObject;
        }

        private int mMessageType    = MSG_INVALID;
        private Object  mObject = null;

        public static final int MSG_INVALID = 0;
        public static final int MSG_RXED_PACKET = 1;
    }


    /** private methods */


    /** private members */
    UDPService  mUdpService = null;
    BlockingQueue<TrunkManagerMessage> mMsgQueue   = null;

}
