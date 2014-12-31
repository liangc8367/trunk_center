package com.bluesky.cloudmontain;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.logging.Logger;

import com.bluesky.protocol.*;

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

        // message queue
        mMsgQueue   = new LinkedBlockingDeque<TrunkManagerMessage>();

        // setup thread
        mProcessor  = new TrunkMessageProcessor();
        mThread = new Thread(mProcessor, TAG);
    }

    public void start(){
        mUdpService.startService();
        mThread.start();
    }

    public void stop(){
        mUdpService.stopService();
        //TODO: stop mThread

    }

    /** private inner classes */
    private class TrunkMessageProcessor implements Runnable {
        public void run(){
            while(true) {
                TrunkManagerMessage msg = mMsgQueue.take();
                int msgType = msg.getType();
                switch (msgType) {
                    case TrunkManagerMessage.MSG_RXED_PACKET:
                        handleUdpPacket((DatagramPacket)msg.getObject());
                        break;
                    default:
                        break;
                }
            }
        }

        private void handleUdpPacket(DatagramPacket packet){
            InetAddress sender = packet.getAddress();
            ProtocolBase proto = ProtocolFactory.getProtocol(ByteBuffer.wrap(packet.getData()));
            switch(proto.getType()){
                case ProtocolBase.PTYPE_REGISTRATION:
                    Registration reg = (Registration) proto;
                    handleRegistration(reg, sender);
                    break;
                default:
                    break;
            }
        }

        private void handleRegistration(Registration reg, InetAddress sender){
            // validation
            LOGGER.info(TAG + "registration from: " + sender);

            // ack
            Ack ack = new Ack(true, reg);
            int size = ack.getSize();
            ByteBuffer payload = ByteBuffer.allocate(size);
            ack.serialize(payload);

            mUdpService.send(sender, payload);
        }
    }

    private class UdpRxHandler implements UDPService.CompletionHandler {
        @Override
        public void completed(DatagramPacket packet){
            TrunkManagerMessage msg = new TrunkManagerMessage(TrunkManagerMessage.MSG_RXED_PACKET, packet);
            mMsgQueue.put(msg); //<== may be blocked if we use cap-limited queue.
        }
    }

    private class TrunkManagerMessage {
        public TrunkManagerMessage(int messageType){
            mMessageType = messageType;
            mObject = null;
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
    private UDPService  mUdpService = null;
    private BlockingQueue<TrunkManagerMessage> mMsgQueue   = null;
    private Thread      mThread = null;
    private TrunkMessageProcessor   mProcessor  = null;
    private final static Logger LOGGER  = Logger.getLogger(UDPService.class.getName());
    private static final String TAG    = "TrunkMgr";

}
