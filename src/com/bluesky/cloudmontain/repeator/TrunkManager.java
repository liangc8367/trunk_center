package com.bluesky.cloudmontain.repeator;

import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Timer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;

import com.bluesky.common.SubscriberDatabase;
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
        initSubDatabase();

        // sequence number
        mSeqNumber = GlobalConstants.INIT_SEQ_NUMBER;
        // create udp service
        UDPService.Configuration udpSvcConfig = new UDPService.Configuration();
        udpSvcConfig.addrLocal = new InetSocketAddress(GlobalConstants.TRUNK_CENTER_PORT);
        udpSvcConfig.addrRemote = new InetSocketAddress(0);
        udpSvcConfig.clientMode = false;
        mUdpService = new UDPService(udpSvcConfig, LOGGER);

        mRepeater = new Repeator(mUdpService);

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
            long suid = reg.getSource();
            LOGGER.i(TAG, "registration from: " + sender + ", legitimate SU:" + suid);

            // validation
            Ack ack;
            boolean legitimateSu = false;
            if(mUserDatabase.hasSubscriber(suid)) {
                legitimateSu = true;
                mUserDatabase.online(suid, sender);
            }else{
                LOGGER.i(TAG, "illegitimate su: " + suid);
            }
            ack = new Ack(suid, GlobalConstants.SUID_TRUNK_MANAGER, ++mSeqNumber, legitimateSu, reg);
            int size = ack.getSize();
            ByteBuffer payload = ByteBuffer.allocate(size);
            ack.serialize(payload);
            mUdpService.send(sender, payload);
        }
    }

    private class UdpRxHandler implements UDPService.CompletionHandler {
        @Override
        public void completed(DatagramPacket packet){
            ProtocolBase proto = ProtocolFactory.getProtocol(packet);
            LOGGER.d(TAG, "rxed:" + proto);

            switch (proto.getType()) {
                case ProtocolBase.PTYPE_REGISTRATION:

                    TrunkManagerMessage msg = new TrunkManagerMessage(TrunkManagerMessage.MSG_RXED_PACKET, packet);
                    try {
                        mMsgQueue.put(msg);
                    } catch (Exception e) {
                        LOGGER.w(TAG, "exp: " + e);
                    }
                    break;
                case ProtocolBase.PTYPE_CALL_INIT:
                case ProtocolBase.PTYPE_CALL_DATA:
                case ProtocolBase.PTYPE_CALL_TERM:
                    CallProcessor cp = findCallProcessor(proto);
                    if(cp!=null){
                        cp.packetReceived(packet);
                    }
                    break;
                default:
                    break;
            }
        }
    }

    private CallProcessor findCallProcessor(ProtocolBase proto){
        if( !mUserDatabase.isGroupMember(proto.getSource(), proto.getTarget())){
            LOGGER.d(TAG, "illegal call attempt from " + proto.getSource() + " to " + proto.getTarget());
            return null;
        }

        CallProcessor cp = mCPs.get(proto.getTarget());
        if(cp == null){
            cp = createCallProcessor(proto.getTarget(), proto.getSource());
            if( cp != null) {
                mCPs.put(new Long(proto.getTarget()), cp);
            } else {
                LOGGER.w(TAG, "failed to create cp for target " + proto.getTarget());
            }
        }
        return cp;
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

    private void initSubDatabase(){
        for(long i=2; i<10; i++) {
            mUserDatabase.addSubscriber(i);
        }
        for(long i=0x900; i< 0x905; i++){
            mUserDatabase.addGroup(i);
        }

        for(long i=2; i<10; i++) {
            mUserDatabase.signup(i, 0x900);
        }

        mUserDatabase.signup(4, 0x901);
        mUserDatabase.signup(6, 0x901);
        mUserDatabase.signup(8, 0x901);
        mUserDatabase.signup(3, 0x092);
        mUserDatabase.signup(5, 0x092);
        mUserDatabase.signup(3, 0x093);
        mUserDatabase.signup(4, 0x903);
        mUserDatabase.signup(5, 0x903);
    }

    /** create repeator, and its executor service.
     *      it's not possible to use threadpool, because we have to ensure all methods of
     *      a cp has to be run in the same thread context, as a way to eliminate race
     *      condition. I may consider to create a ExecuteService pool in future.
     * @param suid
     * @param target
     * @return
     */
    private CallProcessor createCallProcessor(long suid, long target)
    {
        ExecutorService exec = Executors.newSingleThreadExecutor();
        ThreadedCP cp = new ThreadedCP(exec, target, suid, mRepeater, mUserDatabase, mTimer, LOGGER);
        return cp;
    }


    /** private members */
    private short mSeqNumber    = 0;
    private UDPService  mUdpService = null;
    private BlockingQueue<TrunkManagerMessage> mMsgQueue   = null;
    private Thread      mThread = null;
    private TrunkMessageProcessor   mProcessor  = null;
    private UdpRxHandler    mRxHandler;

    private Repeator mRepeater;

    private ExecutorService         mCallProcessorExecutor;
    private final SubscriberDatabase mUserDatabase = new SubscriberDatabase();
    private final Timer mTimer = new Timer("tm");

    private final HashMap<Long, CallProcessor> mCPs = new HashMap<Long, CallProcessor>();
    private final HashMap<Long, ExecutorService> mExecs = new HashMap<Long, ExecutorService>();

    private final static OLog LOGGER = new XLog();
    private static final String TAG    = "TrunkMgr";

}
