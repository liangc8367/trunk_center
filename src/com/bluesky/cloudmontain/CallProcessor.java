package com.bluesky.cloudmontain;

import com.bluesky.common.CallInformation;
import com.bluesky.common.GlobalConstants;
import com.bluesky.common.NamedTimerTask;
import com.bluesky.common.OLog;
import com.bluesky.protocol.CallInit;
import com.bluesky.protocol.ProtocolBase;
import com.bluesky.protocol.ProtocolFactory;

import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.EnumMap;
import java.util.Timer;

/**
 * Created by liangc on 08/02/15.
 */
public class CallProcessor {
    public interface Observer{
        public void callEnd();
    }

    public CallProcessor(long grp_id, long su_id, final OLog logger){
        TAG = "CP[" + grp_id + "]";
        mLogger = logger;
        mTimer = new Timer(TAG);
        initializeSM();
        mStateNode.entry();
    }

    public void packetReceived(DatagramPacket packet){
        if(!validatePacket(packet)){
            return;
        }
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
        mCallInfo.mSuid = callInit.getSuid();
        mCallInfo.mTargetId = callInit.getTargetId();
    }

    // to validate/authenticate incoming packet, it shall be from current transmitter,
    // unless current state is hang or idle.
    private boolean validatePacket(DatagramPacket packet){
        switch(mState){
            case INIT:
            case TXING:
            {
                if( packet.getAddress() != mCallInfo.mSenderIpPort.getAddress()
                    || packet.getPort() != mCallInfo.mSenderIpPort.getPort() )
                {
                    mLogger.d(TAG, "state=" + mState + ", unexp sender:"
                            + packet.getAddress() + ":" + packet.getPort());
                    return false;
                }
            }
                break;
            case HANG:
                break;
            default:
                break;
        }

        return true;
    }

    /** forward packet to all group members, except current transmitting SU */
    private void forwardToGrpMembers(DatagramPacket packet){

    }

    private NamedTimerTask createTimerTask(){
        return null;
    }

    private void rearmFlyWheel(){

    }

    private void sendCallInit(){

    }

    NamedTimerTask mFlywheelTimerTask;
    Timer mTimer;
    final CallInformation mCallInfo = new CallInformation();
    final OLog mLogger;

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
            short protoType = ProtocolBase.peepType(ByteBuffer.wrap(packet.getData()));
            if(protoType == ProtocolBase.PTYPE_CALL_INIT){
                // TODO: assert type is call-init
                CallInit callInit = (CallInit) ProtocolFactory.getProtocol(packet);
                recordCallInfo(callInit, packet);
                forwardToGrpMembers(packet);
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
            rearmFlyWheel();
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
            ProtocolBase proto = ProtocolFactory.getProtocol(packet);
            short protoType = proto.getType();
            switch(protoType){
                case ProtocolBase.PTYPE_CALL_INIT:
                    forwardToGrpMembers(packet);
                    rearmTxTimer();
                    rearmFlyWheel();
                    break;
                case ProtocolBase.PTYPE_CALL_DATA:
                    forwardToGrpMembers(packet);
                    mState = State.TXING;
                    break;
                case ProtocolBase.PTYPE_CALL_TERM:
                    forwardToGrpMembers(packet);
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
            mTimer.schedule(mTimerTask, GlobalConstants.CALL_PACKET_INTERVAL);
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
            rearmFlyWheel();
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
            ProtocolBase proto = ProtocolFactory.getProtocol(packet);
            short protoType = proto.getType();
            switch(protoType){
                case ProtocolBase.PTYPE_CALL_INIT:
                case ProtocolBase.PTYPE_CALL_DATA:
                    forwardToGrpMembers(packet);
                    rearmFlyWheel();
                    break;
                case ProtocolBase.PTYPE_CALL_TERM:
                    forwardToGrpMembers(packet);
                    mState = State.HANG;
                    break;
                default:
                    mLogger.d(TAG, "init: rxed unexp packet:" + proto.toString());
                    break;
            }
        }
    }

    /** call hang state
     *  - repeat caller's calldata/callterm
     *  - for call init, go to call init
     *  - for call hang timeout, fall to idle
     *  - for 20ms timeout, sync/send call term
     */
    private class StateHang extends StateNode {

        @Override
        public void entry() {
            mLogger.d(TAG, "entry call hang");
        }

        @Override
        public void exit() {
            mLogger.d(TAG, "exit call hang");
        }

        @Override
        public void timerExpired(NamedTimerTask timerTask) {
            if( timerTask == mFlywheelTimerTask ){
                mLogger.i(TAG, "flywheel times out in " + mState);
                mState = State.IDLE;
                return;
            } else if( timerTask == mTimerTask ) {
                mLogger.i(TAG, "init: timer exp");
                sendCallInit();
                rearmTxTimer();
            }
        }

        @Override
        public void packetReceived(DatagramPacket packet) {
            ProtocolBase proto = ProtocolFactory.getProtocol(packet);
            short protoType = proto.getType();
            switch(protoType){
                case ProtocolBase.PTYPE_CALL_INIT:
                    //TODO: validate call sequence, or start a new call
                    forwardToGrpMembers(packet);
                    mState =  State.INIT;
                    break;
                case ProtocolBase.PTYPE_CALL_DATA:
                    forwardToGrpMembers(packet);
                    rearmFlyWheel();
                    break;
                case ProtocolBase.PTYPE_CALL_TERM:
                    forwardToGrpMembers(packet);
                    rearmTxTimer();
                    rearmFlyWheel();
                    break;
                default:
                    mLogger.d(TAG, "hang: rxed unexp packet:" + proto.toString());
                    break;
            }
        }

        NamedTimerTask mTimerTask;
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
