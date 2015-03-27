package com.bluesky.cloudmontain.repeator;

import com.bluesky.common.SubscriberDatabase;
import com.bluesky.common.CallInformation;
import com.bluesky.common.GlobalConstants;
import com.bluesky.common.NamedTimerTask;
import com.bluesky.common.OLog;
import com.bluesky.protocol.*;

import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.util.EnumMap;
import java.util.List;
import java.util.Timer;

/**
 * Created by liangc on 08/02/15.
 */
public class CallProcessor {
    public interface Observer{
        public void callEnd();
    }

    public CallProcessor(long grp_id, long su_id,
                         final Repeator rptr,  final SubscriberDatabase database,
                         final Timer timer, final OLog logger){
        TAG = "CP[" + grp_id + "]";
        mRptr = rptr;
        mLogger = logger;
        mTimer = timer;
        mOnlineSubs = database.getOnlineMembers(grp_id);
        initializeSM();
        mStateNode.entry();
    }

    public void packetReceived(DatagramPacket packet){
        saveStateContext();
        mStateNode.packetReceived(packet);
        updateStateContext();
    }

    public void timerExpired(NamedTimerTask timerTask){
        saveStateContext();
        mStateNode.timerExpired(timerTask);
        updateStateContext();
    }

    private void saveStateContext(){
        mStateOrig = mState;
    }

    private void updateStateContext(){
        if(mState!=mStateOrig){
            mStateNode.exit();
            mStateNode = mStateMap.get(mState);
            mStateNode.entry();
        }
    }

    private void recordCallInfo(CallInit callInit, DatagramPacket packet){
        mCallInfo.mSenderIpPort = new InetSocketAddress(packet.getAddress(), packet.getPort());
        mCallInfo.mSequence = callInit.getSequence();
        mCallInfo.mSourceId = callInit.getSource();
        mCallInfo.mTargetId = callInit.getTarget();
    }

    /** forward packet to all group members, except current transmitting SU */
    private void forwardToGrpMembers(ProtocolBase proto){
        mLastTime = System.nanoTime();
        mRptr.repeat(mOnlineSubs, mCallInfo, proto);
    }

    protected NamedTimerTask createTimerTask(){
        ++mTimerSeed;
        mLogger.d(TAG, "create timerTask[" + mTimerSeed +"]");
        return new NamedTimerTask(mTimerSeed) {
            @Override
            public void run() {
                (CallProcessor.this).timerExpired(this);
            }
        };
    }

    /** rearm flywheel for given duration
     *
     * @param dur
     */
    private void rearmFlyWheel(long dur){
        if(mFlywheelTimerTask!=null){
            mFlywheelTimerTask.cancel();
        }
        mFlywheelTimerTask = createTimerTask();
        mTimer.schedule(mFlywheelTimerTask, dur);
    }

    private void cancelFlywheel(){
        if(mFlywheelTimerTask!=null){
            mFlywheelTimerTask.cancel();
            mFlywheelTimerTask = null;
        }
    }
    /** synthesize callInit based on last callInit seq, and send to all grp members
     *
     */
    private void sendCallInit(){
        CallInit callInit = new CallInit(mCallInfo.mTargetId, mCallInfo.mSourceId, ++mCallInitSeq);
        forwardToGrpMembers(callInit);
    }

    /** synthesize callTerm based on last callTerm seq, and send to all grp members
     *
     */
    private void sendCallTerm(){
        CallTerm callTerm = new CallTerm(mCallInfo.mTargetId, mCallInfo.mSourceId,
                ++mCallTermAudioSeq, --mCallHangCountdown);
        forwardToGrpMembers(callTerm);
    }


    ////////////////////////////// private members ////////////////////////////
    short mCallInitSeq, mCallTermSeq, mCallTermAudioSeq;
    short mCallHangCountdown;
    long mLastTime;
    protected int mTimerSeed = 0;

    List<SubscriberDatabase.OnlineRecord> mOnlineSubs;
    NamedTimerTask mFlywheelTimerTask;
    final Timer mTimer;
    final Repeator mRptr;
    final CallInformation mCallInfo = new CallInformation();
    final OLog mLogger;

    /////////////////////////////////// sub states ///////////////////////////
    private enum State {
        IDLE,
        INIT,
        TXING,
        HANG,
    }

    private class StateNode {
        public void entry(){};
        public void exit(){};
        public void timerExpired(NamedTimerTask timerTask){};
        public void packetReceived(DatagramPacket packet){};
    }

    private class StateIdle extends StateNode{

        @Override
        public void entry() {
            mLogger.d(TAG, "entry idle");
        }

        @Override
        public void exit() {
            mLogger.d(TAG, "exit idle");
        }

        @Override
        public void timerExpired(NamedTimerTask timerTask) {

        }

        @Override
        public void packetReceived(DatagramPacket packet) {
            ProtocolBase proto = ProtocolFactory.getProtocol(packet);
            if(proto.getType() == ProtocolBase.PTYPE_CALL_INIT){
                CallInit callInit = (CallInit) proto;
                recordCallInfo(callInit, packet);
                mCallInitSeq = callInit.getSequence();
                forwardToGrpMembers(callInit);
                mState = State.INIT;
            }
        }
    }

    /** start call init sequence
     * - repeat caller's callInit/Data/Term packets
     * - for 20ms timeout, sync/send callInit on behalf of caller
     * - for 120ms timeout, fall to call hang to terminate the session
     *  NOTE: for all following states, we timeout the call session  and return to idle state.
     *  Right now, time out is 6 packet, i.e. 20ms * 6.
     */
    private class StateInit extends StateNode{

        @Override
        public void entry() {
            mLogger.d(TAG, "entry init");
            rearmFlyWheel(GlobalConstants.CALL_FLYWHEEL_PERIOD);
            mTimerTask = createTimerTask();
            mTimer.schedule(mTimerTask, GlobalConstants.CALL_PACKET_INTERVAL);
        }

        @Override
        public void exit() {
            if( mTimerTask != null){
                mTimerTask.cancel();
                mTimerTask = null;
            }
            mLogger.d(TAG, "exit init");
        }

        @Override
        public void timerExpired(NamedTimerTask timerTask) {
            if( timerTask == mFlywheelTimerTask ){
                mLogger.i(TAG, "flywheel times out in " + mState);
                mState = State.HANG;
            } else if( timerTask == mTimerTask ) {
                mLogger.i(TAG, "init: timer exp");
                sendCallInit();
                rearmTxTimer();
            }
        }

        @Override
        public void packetReceived(DatagramPacket packet) {
            if(!validatePacket(packet)){
                return;
            }
            ProtocolBase proto = ProtocolFactory.getProtocol(packet);
            short protoType = proto.getType();
            switch(protoType){
                case ProtocolBase.PTYPE_CALL_INIT:
                    CallInit callInit = (CallInit) proto;
                    mCallInitSeq = callInit.getSequence();
                    forwardToGrpMembers(proto);
                    rearmTxTimer();
                    rearmFlyWheel(GlobalConstants.CALL_FLYWHEEL_PERIOD);
                    break;
                case ProtocolBase.PTYPE_CALL_DATA:
                    forwardToGrpMembers(proto);
                    mState = State.TXING;
                    break;
                case ProtocolBase.PTYPE_CALL_TERM:
                    mCallTermSeq = proto.getSequence();
                    mState = State.HANG;
                    break;
                default:
                    mLogger.d(TAG, "init: rxed unexp packet:" + proto.toString());
                    break;
            }
        }

        /** rearm tx timer per current elapse time
         *
         */
        private void rearmTxTimer(){
            mTimerTask = createTimerTask();
            long timeNow = System.nanoTime();
            long delay = GlobalConstants.CALL_PACKET_INTERVAL + (mLastTime - timeNow) / (1000L * 1000);
            if( delay < 0){
                delay = 1;
            }
            mTimer.schedule(mTimerTask, delay);
        }

        private boolean validatePacket(DatagramPacket packet){
            if( !packet.getAddress().equals(mCallInfo.mSenderIpPort.getAddress())
                    || packet.getPort() != mCallInfo.mSenderIpPort.getPort())
            {
                mLogger.d(TAG, "state=" + mState +
                        ", unexp sender:" + packet.getAddress() + ":" + packet.getPort() +
                        ", (exp:" + mCallInfo.mSenderIpPort.getAddress() + ":" + mCallInfo.mSenderIpPort.getPort()
                );
                return false;
            }

            ProtocolBase proto = ProtocolFactory.getProtocol(packet);
            long suid = proto.getSource();
            long tgtid = proto.getTarget();

            if( suid != mCallInfo.mSourceId || tgtid != mCallInfo.mTargetId ){
                mLogger.d(TAG, "init: call init for different tgt, src=" + suid + ", target=" + tgtid);
                return false;
            }
            return true;
        }

        NamedTimerTask mTimerTask;
    }

    /** transmitting state
     *  - repeat caller's calldata/callterm
     *  - for 120ms flywheel timeout, fall to call hang
     */
    private class StateTxing extends StateNode{

        @Override
        public void entry() {
            mLogger.d(TAG, "entry txing");
            rearmFlyWheel(GlobalConstants.CALL_FLYWHEEL_PERIOD);
        }

        @Override
        public void exit() {
            mLogger.d(TAG, "exit txing");
        }

        @Override
        public void timerExpired(NamedTimerTask timerTask){
            if( timerTask == mFlywheelTimerTask ){
                mLogger.i(TAG, "flywheel times out in " + mState);
                mState = State.HANG;
            }
        }

        @Override
        public void packetReceived(DatagramPacket packet) {
            if(!validatePacket(packet)){
                return;
            }
            ProtocolBase proto = ProtocolFactory.getProtocol(packet);
            short protoType = proto.getType();
            switch(protoType){
                case ProtocolBase.PTYPE_CALL_INIT:
                    forwardToGrpMembers(proto);
                    rearmFlyWheel(GlobalConstants.CALL_FLYWHEEL_PERIOD);
                    break;
                case ProtocolBase.PTYPE_CALL_DATA:
                    mCallTermSeq = proto.getSequence();
                    forwardToGrpMembers(proto);
                    rearmFlyWheel(GlobalConstants.CALL_FLYWHEEL_PERIOD);
                    break;
                case ProtocolBase.PTYPE_CALL_TERM:
                    mCallTermSeq = proto.getSequence();
                    mState = State.HANG;
                    break;
                default:
                    mLogger.d(TAG, "init: rxed unexp packet:" + proto.toString());
                    break;
            }
        }

        private boolean validatePacket(DatagramPacket packet){
            if( !packet.getAddress().equals(mCallInfo.mSenderIpPort.getAddress())
                    || packet.getPort() != mCallInfo.mSenderIpPort.getPort())
            {
                mLogger.d(TAG, "state=" + mState +
                        ", unexp sender:" + packet.getAddress() + ":" + packet.getPort() +
                        ", (exp:" + mCallInfo.mSenderIpPort.getAddress() + ":" + mCallInfo.mSenderIpPort.getPort()
                );
                return false;
            }

            ProtocolBase proto = ProtocolFactory.getProtocol(packet);
            long suid = proto.getSource();
            long tgtid = proto.getTarget();

            if( suid != mCallInfo.mSourceId || tgtid != mCallInfo.mTargetId ){
                mLogger.d(TAG, "init: call init for different tgt, src=" + suid + ", target=" + tgtid);
                return false;
            }
            return true;
        }
    }

    /** call hang state
     *  - repeat caller's calldata, but drop its callterm
     *  - for call init, go to call init
     *  - for call hang timeout, fall to idle, right now, it's fixed at 5s.
     *  - for 20ms timeout, sync/send call term
     */
    private class StateHang extends StateNode {

        @Override
        public void entry() {
            mLogger.d(TAG, "entry call hang");
//            mCallHangEntryTime = System.nanoTime();
            mCallHangCountdown = GlobalConstants.CALL_HANG_COUNTDOWN;
            sendCallTerm();
            rearmTxTimer();
        }

        @Override
        public void exit() {
            if( mTimerTask != null){
                mTimerTask.cancel();
                mTimerTask = null;
            }
            mLogger.d(TAG, "exit call hang");
        }

        @Override
        public void timerExpired(NamedTimerTask timerTask) {
            if( timerTask == mTimerTask ) {
                mLogger.i(TAG, "init: timer exp");
                sendCallTerm();
                if( 0 == mCallHangCountdown){
                    mLogger.i(TAG, "call hang over");
                    mState = State.IDLE;
                } else {
                    rearmTxTimer();
                }
            }
        }

        @Override
        public void packetReceived(DatagramPacket packet) {
            if(!validatePacket(packet)){
                return;
            }
            ProtocolBase proto = ProtocolFactory.getProtocol(packet);
            short protoType = proto.getType();
            switch(protoType){
                case ProtocolBase.PTYPE_CALL_INIT:
                    recordCallInfo((CallInit)proto, packet);
                    forwardToGrpMembers(proto);
                    mState =  State.INIT;
                    break;
                case ProtocolBase.PTYPE_CALL_DATA:
                    forwardToGrpMembers(proto);
                    break;
                case ProtocolBase.PTYPE_CALL_TERM:
                    // discard
                    break;
                default:
                    mLogger.d(TAG, "hang: rxed unexp packet:" + proto.toString());
                    break;
            }
        }

        /** rearm tx timer per current elapse time
         *
         */
        private void rearmTxTimer(){
            mTimerTask = createTimerTask();
            long delay =  (GlobalConstants.CALL_HANG_COUNTDOWN - mCallHangCountdown )
                    * GlobalConstants.CALL_PACKET_INTERVAL / (1000L * 1000);
            if( delay < 0){
                delay = 1;
            }
            mTimer.schedule(mTimerTask, delay);
        }

        /** only allow callinit from same group
         *
         * @param packet
         * @return
         */
        private boolean validatePacket(DatagramPacket packet){
            ProtocolBase proto = ProtocolFactory.getProtocol(packet);
            long suid = proto.getSource();
            long tgtid = proto.getTarget();
            boolean valid = false;
            switch( proto.getType()){
                case ProtocolBase.PTYPE_CALL_INIT:
                    if( tgtid != mCallInfo.mTargetId ){
                        mLogger.d(TAG, "hang state: call init for different tgt, src=" + suid + ", target=" + tgtid);
                    } else {
                        valid = true;
                    }
                    break;
                case ProtocolBase.PTYPE_CALL_DATA:
                case ProtocolBase.PTYPE_CALL_TERM:
                    if( !packet.getAddress().equals(mCallInfo.mSenderIpPort.getAddress())
                            || packet.getPort() != mCallInfo.mSenderIpPort.getPort())
                    {
                        mLogger.d(TAG, "state=" + mState +
                            ", unexp sender:" + packet.getAddress() + ":" + packet.getPort() +
                            ", (exp:" + mCallInfo.mSenderIpPort.getAddress() + ":" + mCallInfo.mSenderIpPort.getPort()
                        );
                        break;
                    }
                    if( tgtid != mCallInfo.mTargetId || suid != mCallInfo.mSourceId){
                        mLogger.d(TAG, "hang state: call term for different tgt, src=" + suid + ", target=" + tgtid);
                        break;
                    }
                    valid = true;
                    break;
            }

            return valid;
        }

        NamedTimerTask mTimerTask;
//        Long mCallHangEntryTime;
    }

    private void initializeSM(){
        StateNode aState;
        aState = new StateIdle();
        mStateMap.put(State.IDLE, aState);

        aState = new StateInit();
        mStateMap.put(State.INIT, aState);
        aState = new StateTxing();
        mStateMap.put(State.TXING, aState);
        aState = new StateHang();
        mStateMap.put(State.HANG, aState);

        mState = State.IDLE;
        mStateNode = mStateMap.get(mState);
    }
    State mState, mStateOrig;
    StateNode mStateNode;
    final EnumMap<State, StateNode> mStateMap = new EnumMap<State, StateNode>(State.class);

    String TAG;
}
