package com.bluesky.cloudmontain;

import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.logging.Logger;

import com.bluesky.common.*;
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
        // sequence number
        mSeqNumber = GlobalConstants.INIT_SEQ_NUMBER;
        // create udp service
        UDPService.Configuration udpSvcConfig = new UDPService.Configuration();
        udpSvcConfig.addrLocal = new InetSocketAddress(GlobalConstants.TRUNK_CENTER_PORT);
        udpSvcConfig.addrRemote = new InetSocketAddress(0);
        udpSvcConfig.clientMode = false;
        mUdpService = new UDPService(udpSvcConfig, LOGGER);

        createEchoingCallProcessor();

        // register callback
        mRxHandler  = new UdpRxHandler();
        mUdpService.setCompletionHandler(mRxHandler);

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
                TrunkManagerMessage msg;
                try {
                    msg = mMsgQueue.take();
                } catch (Exception e){
                    LOGGER.w(TAG, "exp: " + e);
                    continue;
                }
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
            short protoType = ProtocolBase.peepType(ByteBuffer.wrap(packet.getData()));
            switch(protoType){
                case ProtocolBase.PTYPE_REGISTRATION:
                    handleRegistration(packet);
                    break;
                default:
                    break;
            }
        }

        private void handleRegistration(DatagramPacket packet){
            InetSocketAddress sender = new InetSocketAddress(packet.getAddress(), packet.getPort());
            Registration reg = (Registration)ProtocolFactory.getProtocol(packet);

            // validation
            LOGGER.i(TAG, "registration from: " + sender);

            // ack
            Ack ack = new Ack(true, ByteBuffer.wrap(packet.getData()));
            ack.setSequence(++mSeqNumber);
            int size = ack.getSize();
            ByteBuffer payload = ByteBuffer.allocate(size);
            ack.serialize(payload);

            mUdpService.send(sender, payload);

        }
    }

    private class UdpRxHandler implements UDPService.CompletionHandler {
        @Override
        public void completed(DatagramPacket packet){

            ProtocolHelpers.peepProtocol(packet);
            short protoType = ProtocolBase.peepType(ByteBuffer.wrap(packet.getData()));
            if (protoType == ProtocolBase.PTYPE_REGISTRATION ) {

                TrunkManagerMessage msg = new TrunkManagerMessage(TrunkManagerMessage.MSG_RXED_PACKET, packet);
                try {
                    mMsgQueue.put(msg); //<== may be blocked if we use cap-limited queue.
                } catch (Exception e) {
                    LOGGER.w(TAG, "exp: " + e);
                }
            } else {
                EchoingCallProcessor.EvRxedPacket event = mCallProcessor.new EvRxedPacket(packet);
                mCallProcessorExecutor.execute(event);
            }
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


    private void createEchoingCallProcessor(){
        mCallProcessorExecutor = Executors.newSingleThreadExecutor();
        mCallProcessor  = new EchoingCallProcessor(mCallProcessorExecutor, mUdpService);
    }

    /** private methods */


    /** private members */
    private short mSeqNumber    = 0;
    private UDPService  mUdpService = null;
    private BlockingQueue<TrunkManagerMessage> mMsgQueue   = null;
    private Thread      mThread = null;
    private TrunkMessageProcessor   mProcessor  = null;
    private UdpRxHandler    mRxHandler;

    private EchoingCallProcessor    mCallProcessor;
    private ExecutorService         mCallProcessorExecutor;

    private final static OLog LOGGER = new XLog();
    private static final String TAG    = "TrunkMgr";

}
