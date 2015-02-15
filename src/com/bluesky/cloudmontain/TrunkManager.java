package com.bluesky.cloudmontain;

import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.HashMap;
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
            long suid = reg.getSUID();

            // validation
            Ack ack;
            if(mUserDatabase.hasSubscriber(suid)) {
                mUserDatabase.online(suid, sender);
                ack = new Ack(true, ByteBuffer.wrap(packet.getData()));
                LOGGER.i(TAG, "registration from: " + sender + ", legitimate SU:" + suid);
            } else {
                ack = new Ack(false, ByteBuffer.wrap(packet.getData()));
                LOGGER.i(TAG, "registration from: " + sender + ",  unknown SU:" + suid);
            }
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

            LOGGER.d(TAG, ProtocolHelpers.peepProtocol(packet));
            short protoType = ProtocolBase.peepType(ByteBuffer.wrap(packet.getData()));
            switch (protoType) {
                case ProtocolBase.PTYPE_REGISTRATION:

                    TrunkManagerMessage msg = new TrunkManagerMessage(TrunkManagerMessage.MSG_RXED_PACKET, packet);
                    try {
                        mMsgQueue.put(msg); //<== may be blocked if we use cap-limited queue.
                    } catch (Exception e) {
                        LOGGER.w(TAG, "exp: " + e);
                    }
                    break;
                case ProtocolBase.PTYPE_CALL_INIT:
                case ProtocolBase.PTYPE_CALL_DATA:
                case ProtocolBase.PTYPE_CALL_TERM:
                    ProtocolBase proto = ProtocolFactory.getProtocol(packet);
                    CallProcessor cp = findCallProcessor(proto);
                    if(cp!=null){
                        cp.packetReceived(packet);
                    }
                    break;
                default:
                    break;
            }
//            EchoingCallProcessor.EvRxedPacket event = mCallProcessor.new EvRxedPacket(packet);
//            mCallProcessorExecutor.execute(event);
        }
    }

    private CallProcessor findCallProcessor(ProtocolBase proto){
        short protoType = proto.getType();
        long source = 0, target =0;
        switch( protoType ){
            case ProtocolBase.PTYPE_CALL_INIT:
                CallInit callInit = (CallInit) proto;
                source = callInit.getSuid();
                target = callInit.getTargetId();
                break;
            case ProtocolBase.PTYPE_CALL_DATA:
                CallData callData = (CallData) proto;
                source = callData.getSuid();
                target = callData.getTargetId();
                break;
            case ProtocolBase.PTYPE_CALL_TERM:
                CallTerm callTerm = (CallTerm)proto;
                source = callTerm.getSuid();
                target = callTerm.getTargetId();
                break;
            default:
                break;
        }
        if( source == 0){
            LOGGER.d(TAG, "invalid packet(type = " + protoType + ", or source (id=" + source +")");
            return null;
        }

        if( !mUserDatabase.isGroupMember(source, target)){
            LOGGER.d(TAG, "illegal call attemp from " + source + " to " + target);
            return null;
        }

        CallProcessor cp = mCPs.get(target);
        if(cp == null){
//            cp = new CallProcessor(target, source, mRepeater, mUserDatabase, LOGGER);
            cp = createCallProcessor(target, source);
            mCPs.put(new Long(target), cp);
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


    private void createEchoingCallProcessor(){
        mCallProcessorExecutor = Executors.newSingleThreadExecutor();
        mCallProcessor  = new EchoingCallProcessor(mCallProcessorExecutor, mUdpService);
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

    /** create callprocessor, and its executor service.
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
        ThreadedCP cp = new ThreadedCP(exec, target, suid, mRepeater, mUserDatabase,LOGGER);
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

    private EchoingCallProcessor    mCallProcessor;
    private ExecutorService         mCallProcessorExecutor;
    private final SubscriberDatabase mUserDatabase = new SubscriberDatabase();

    private final HashMap<Long, CallProcessor> mCPs = new HashMap<Long, CallProcessor>();
    private final HashMap<Long, ExecutorService> mExecs = new HashMap<Long, ExecutorService>();

    private final static OLog LOGGER = new XLog();
    private static final String TAG    = "TrunkMgr";

}
