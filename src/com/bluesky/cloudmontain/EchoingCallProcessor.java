package com.bluesky.cloudmontain;

import com.bluesky.protocol.*;


import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.EnumMap;
import java.util.Random;
import java.util.Timer;
import java.util.concurrent.ExecutorService;
import java.util.logging.Logger;

/**
 * a simple demo of call processor, just echoing whatever received from SU.
 * a runnable/thread entity
 *
 * Created by liangc on 11/01/15.
 */
public class EchoingCallProcessor {
    /** trigger events */
    public abstract class TriggerEvent implements Runnable {

    }

    public class EvRxedPacket extends TriggerEvent {
        public EvRxedPacket(DatagramPacket packet){
            mPacket = packet;
        }
        @Override
        public void run(){
            EchoingCallProcessor.this.handleUdpPacket(mPacket);
        }
        DatagramPacket  mPacket;
    }

    public class EvTimerExpired extends TriggerEvent {
        public EvTimerExpired(NamedTimerTask timerTask){
            mTimerTask = timerTask;
        }
        @Override
        public void run(){
            EchoingCallProcessor.this.handleTimerExpiration(mTimerTask);
        }

        NamedTimerTask mTimerTask;
    }

    /** public methods and members */

    public EchoingCallProcessor(ExecutorService executor, UDPService udpService){
        mExecutor = executor;
        mUdpService = udpService;
        initializeStateMachine();
        mTimer  = new Timer(TAG+"tm");
    }

    public void release(){
        mTimer.cancel();
        mTimer = null;
    }



    /** private methods and members */
    private enum State {
        IDLE,
        CALL_RECEIVING,
        CALL_HANG,
        CALL_TRANSMITTING
    }

    private class StateNode {
        void handleUdpPacket(DatagramPacket packet){};
        void handleTimerExpiration(NamedTimerTask timerTask){};
        void entry(){};
        void exit(){};
    }

    private class StateIdle extends StateNode{
        @Override
        public void handleUdpPacket(DatagramPacket packet){
            short protoType = ProtocolBase.peepType(ByteBuffer.wrap(packet.getData()));
            switch (protoType){
                case ProtocolBase.PTYPE_CALL_INIT:
                    recordCallInfo(packet);
                    mState = State.CALL_RECEIVING;
                    break;
                default:
                    LOGGER.warning(TAG + "packet outorder? type = " + protoType);
                    break;
            }
        }

        @Override
        void handleTimerExpiration(NamedTimerTask timerTask) {

        }

        @Override
        void entry() {
            LOGGER.info(TAG + "idle");
        }

        @Override
        void exit() {
            super.exit();
        }
    }

    private class StateReceiving extends StateNode {

        @Override
        void handleUdpPacket(DatagramPacket packet) {
            short protoType = ProtocolBase.peepType(ByteBuffer.wrap(packet.getData()));
            switch (protoType){
                case ProtocolBase.PTYPE_CALL_DATA:
                    recordCall(packet);
                    rearmTimer();
                    break;
                case ProtocolBase.PTYPE_CALL_TERM:
                    stopRecording(packet);
                    mState = State.CALL_HANG;
                    break;
                default:
                    LOGGER.warning(TAG + "packet outorder? type = " + protoType);
                    break;
            }
        }

        @Override
        void handleTimerExpiration(NamedTimerTask timerTask) {
            if( timerTask != mTimerTask ){
                LOGGER.warning(TAG + "lingering timer task: " + timerTask.id());
            }
            LOGGER.info(TAG + "flywheel time expired");
            mState = State.IDLE;
        }

        @Override
        void entry() {
            LOGGER.info(TAG + "receiving");
            armTimer();
        }

        @Override
        void exit() {
            mTimerTask.cancel();
            mTimerTask = null;
        }

        private void armTimer(){
            mTimerTask = createTimerTask();
            mTimer.schedule(mTimerTask, GlobalConstants.CALL_FLYWHEEL_PERIOD);
        }
        private void rearmTimer(){
            mTimerTask.cancel();
            armTimer();
        }

        NamedTimerTask   mTimerTask;

    }

    private class StateCallHang extends StateNode {

        @Override
        void handleUdpPacket(DatagramPacket packet) {
            short protoType = ProtocolBase.peepType(ByteBuffer.wrap(packet.getData()));
            switch (protoType){
                case ProtocolBase.PTYPE_CALL_INIT:
                    recordCallInfo(packet);
                    mState = State.CALL_RECEIVING;
                    break;
                default:
                    LOGGER.warning(TAG + "packet outorder? type = " + protoType);
                    break;
            }
        }

        @Override
        void handleTimerExpiration(NamedTimerTask timerTask) {
            if( timerTask == mRandomTxTimerTask ){
                LOGGER.info(TAG + "simulating PTT");
                mState = State.CALL_TRANSMITTING;
            } else if ( timerTask == mTimerTask ){
                LOGGER.info(TAG + "call hang ended");
                mState = State.IDLE;
            } else {
                LOGGER.warning(TAG + "lingering timer task: " + timerTask.id());
            }
        }

        @Override
        void entry() {
            LOGGER.info(TAG + "call hang");
            armTimer();
        }

        @Override
        void exit() {
            mTimerTask.cancel();
            mTimerTask = null;

            mRandomTxTimerTask.cancel();
            mRandomTxTimerTask = null;
        }

        private void armTimer(){
            mTimerTask = createTimerTask();
            mTimer.schedule(mTimerTask, GlobalConstants.CALL_HANG_PERIOD);

            mRandomTxTimerTask = createTimerTask();
            mTimer.schedule(mRandomTxTimerTask, (new Random()).nextInt(GlobalConstants.CALL_HANG_PERIOD + 1));
        }
//        private void rearmTimer(){
//            mTimerTask.cancel();
//            armTimer();
//        }

        NamedTimerTask  mTimerTask;
        NamedTimerTask  mRandomTxTimerTask;

    }

    private class StateTransmitting extends StateNode {

        @Override
        void handleUdpPacket(DatagramPacket packet) {
            short protoType = ProtocolBase.peepType(ByteBuffer.wrap(packet.getData()));
            switch (protoType) {
//                case ProtocolBase.PTYPE_CALL_INIT:
//                    recordCallInfo(packet);
//                    mState = State.CALL_RECEIVING;
//                    break;
                default:
                    LOGGER.warning(TAG + "packet outorder? type = " + protoType);
            }
        }

        @Override
        void handleTimerExpiration(NamedTimerTask timerTask) {
            if ( timerTask == mTimerTask ){
                switch (mTxStep ) {
                    case TX_INIT:
                        sendCallInit();
                        ++mTxCount;
                        if (mTxCount >= GlobalConstants.CALL_PREAMBLE_NUMBER) {
                            mTxStep = TX_DATA;
                            mTxCount = 0;
                        }
                        break;
                    case TX_DATA:
                        if (sendCallData()) {
                            ++mTxCount;
                        } else {
                            LOGGER.info(TAG + "sent " + mTxCount + " audio data packets");
                            mTxStep = TX_TERM;
                            sendCallTerm();
                            mTxCount = 1;
                        }
                        break;
                    case TX_TERM:
                        sendCallTerm();
                        ++mTxCount;
                        if (mTxCount >= GlobalConstants.CALL_TERM_NUMBER) {
                            mState = State.IDLE; //<== we go to idle, otherwise, we will repeat the tx again
                        }
                        break;
                    default:
                        LOGGER.warning(TAG + "wrong Tx state: " + mTxStep);
                        break;
                }

                rearmTimer();
            } else {
                LOGGER.warning(TAG + "lingering timer task: " + timerTask.id());
            }
        }

        @Override
        void entry() {
            LOGGER.info(TAG + "transmitting");
            armTimer();
            sendCallInit();
            ++mTxCount;
            mTxStep = TX_INIT;
        }

        @Override
        void exit() {
            mTimerTask.cancel();
            mTimerTask = null;
        }

        private void armTimer(){
            mTimerTask = createTimerTask();
            mTimer.schedule(mTimerTask, GlobalConstants.CALL_PACKET_INTERVAL);
        }
        private void rearmTimer(){
            mTimerTask.cancel();
            armTimer();
        }

        NamedTimerTask  mTimerTask;

        static final int TX_INIT = 0;
        static final int TX_DATA = 1;
        static final int TX_TERM = 2;

        int             mTxCount = 0;
        int             mTxStep;
    }

    private void handleUdpPacket(DatagramPacket packet){
//        LOGGER.info(TAG + "rxed packet from " + packet.getAddress());
        State   origState = mState;
        mStateNode.handleUdpPacket(packet);
        if( origState != mState ){
            mStateNode.exit();
            mStateNode = mStateMap.get(mState);
            mStateNode.entry();
        }

    }

    private void handleTimerExpiration(NamedTimerTask timerTask){
        LOGGER.info(TAG + "timer[" + timerTask.id() + "] expired");

        State   origState = mState;
        mStateNode.handleTimerExpiration(timerTask);
        if( origState != mState ){
            mStateNode.exit();
            mStateNode = mStateMap.get(mState);
            mStateNode.entry();
        }
    }

    private NamedTimerTask createTimerTask(){
        ++mTimerSeed;
        LOGGER.info(TAG + "create timerTask[" + mTimerSeed  + "]");
        return new NamedTimerTask(mTimerSeed){
            @Override
            public void run() {
                EvTimerExpired tmExpired = new EvTimerExpired(this);
                mExecutor.execute(tmExpired);
            }
        };
    }

    private void recordCallInfo(DatagramPacket packet){
        short protoType = ProtocolBase.peepType(ByteBuffer.wrap(packet.getData()));
        if(protoType == ProtocolBase.PTYPE_CALL_INIT){
            CallInit callInit = (CallInit) ProtocolFactory.getProtocol(packet);
            mCallInfo   = new CallInformation();
            mCallInfo.mSenderIpPort = new InetSocketAddress(packet.getAddress(), packet.getPort());
            mCallInfo.mSequence = callInit.getSequence();
            mCallInfo.mSuid = callInit.getSuid();
            mCallInfo.mTargetId = callInit.getTargetId();

            try {
                mOutStream = new BufferedOutputStream(new FileOutputStream(AUDIO_FILE_NAME));
                mOutStream.write(AMR_FILE_HEADER_SINGLE_CHANNEL.getBytes());

            } catch (Exception e){
                LOGGER.warning(TAG + "error in creating: " + e);
            }
        }
    }

    private void recordCall(DatagramPacket packet){
        short protoType = ProtocolBase.peepType(ByteBuffer.wrap(packet.getData()));
        if(protoType == ProtocolBase.PTYPE_CALL_DATA){
            CallData callData = (CallData) ProtocolFactory.getProtocol(packet);

            //TODO: validate call data
            try {
                mOutStream.write(callData.getAudioData().array(),
                        callData.getAudioData().arrayOffset(),
                        callData.getAudioData().limit()
                );

            }catch (Exception e){
                LOGGER.warning(TAG + "error in writing: " + e);
            }
        }
    }

    private void stopRecording(DatagramPacket packet){
        try {
            mOutStream.flush();
            mOutStream.close();
        } catch (Exception e){
            LOGGER.warning(TAG + "error in closing:" + e);
        }
    }

    private void sendCallInit(){
        mTxSeq = (short) (new Random()).nextInt();
        CallInit preamble = new CallInit(mCallInfo.mTargetId, GlobalConstants.SUID_TRUNK_MANAGER);
        preamble.setSequence(++mTxSeq);
        ByteBuffer payload = ByteBuffer.allocate(preamble.getSize());
        preamble.serialize(payload);
        mUdpService.send(mCallInfo.mSenderIpPort, payload);

        if( mInStream == null) {
            try {
                mInStream = new BufferedInputStream(new FileInputStream(AUDIO_FILE_NAME));
                mInStream.skip(AMR_FILE_HEADER_SINGLE_CHANNEL.length());
            } catch (Exception e) {
                LOGGER.warning(TAG + "failed to open: " + AUDIO_FILE_NAME + ", " + e);
            }
        }
    }

    /** send callData
     *
     * @return true if not EOF
     */
    private boolean sendCallData(){
        byte[] buffer = new byte[GlobalConstants.COMPRESSED_20MS_AUDIO_SIZE];
        int sz;
        try{
            sz = mInStream.read(buffer, 0, GlobalConstants.COMPRESSED_20MS_AUDIO_SIZE);
        } catch (Exception e){
            LOGGER.warning(TAG + "failed to read:" + e);
            return false;
        }

        if( sz == -1 ){
            LOGGER.info(TAG + "end of audio file");
            return false;
        }

        CallData callData = new CallData(
                mCallInfo.mTargetId,
                GlobalConstants.SUID_TRUNK_MANAGER,
                (short)(mTxSeq + 0x100),
                ByteBuffer.wrap(buffer, 0, sz));
        callData.setSequence(++mTxSeq);
        ByteBuffer payload = ByteBuffer.allocate(callData.getSize());
        callData.serialize(payload);
        mUdpService.send(mCallInfo.mSenderIpPort, payload);

        return true;
    }

    private void sendCallTerm(){
        try{
            mInStream.close();
            mInStream = null;
        } catch( Exception e){
            LOGGER.warning(TAG + "error happened in close " + e);
        }
        CallTerm callTerm = new CallTerm(
                mCallInfo.mTargetId,
                GlobalConstants.SUID_TRUNK_MANAGER
        );
        callTerm.setSequence(++mTxSeq);
        ByteBuffer payload = ByteBuffer.allocate(callTerm.getSize());
        callTerm.serialize(payload);
        mUdpService.send(mCallInfo.mSenderIpPort, payload);

    }



    private void initializeStateMachine(){
        mStateMap = new EnumMap<State, StateNode>(State.class);
        StateNode aState;
        aState = new StateIdle();
        mStateMap.put(State.IDLE, aState);
        aState = new StateReceiving();
        mStateMap.put(State.CALL_RECEIVING, aState);
        aState = new StateCallHang();
        mStateMap.put(State.CALL_HANG, aState);
        aState = new StateTransmitting();
        mStateMap.put(State.CALL_TRANSMITTING, aState);

        mState = State.IDLE;
        mStateNode = mStateMap.get(mState);
        mStateNode.entry();
    }


    State               mState = State.IDLE;
    StateNode           mStateNode;
    EnumMap<State, StateNode> mStateMap;

    Timer               mTimer;
    int                 mTimerSeed = 0;
    ExecutorService     mExecutor;
    UDPService          mUdpService;

    CallInformation     mCallInfo;
    short               mTxSeq;

    BufferedOutputStream mOutStream;
    BufferedInputStream  mInStream;
    static final String AUDIO_FILE_NAME = "audio.amr";
    static final String AMR_FILE_HEADER_SINGLE_CHANNEL = "#!AMR\n";

    static final String TAG = "EchoingCP: ";
    static final Logger LOGGER  = Logger.getLogger(UDPService.class.getName());
}
