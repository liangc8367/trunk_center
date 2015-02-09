package com.bluesky.cloudmontain;

import com.bluesky.common.CallInformation;
import com.bluesky.common.NamedTimerTask;
import com.bluesky.common.OLog;
import com.bluesky.protocol.CallInit;
import com.bluesky.protocol.ProtocolBase;
import com.bluesky.protocol.ProtocolFactory;

import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.EnumMap;

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
        mCallInfo.mSuid = callInit.getSuid();
        mCallInfo.mTargetId = callInit.getTargetId();
    }

    /** forward packet to all group members, except current transmitting SU */
    private void forwardToGrpMembers(DatagramPacket packet){

    }

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
                // TODO: validate? assert should be enough
                CallInit callInit = (CallInit) ProtocolFactory.getProtocol(packet);
                recordCallInfo(callInit, packet);
                forwardToGrpMembers(packet);
                mState = State.INIT;
            }
        }
    }

    private class StateInit extends StateNode{

        @Override
        public void entry() {
            super.entry();
        }

        @Override
        public void exit() {
            super.exit();
        }

        @Override
        public void timerExpired(NamedTimerTask timerTask) {
            super.timerExpired(timerTask);
        }

        @Override
        public void packetReceived(DatagramPacket packet) {
            super.packetReceived(packet);
        }
    }

    private class StateTxing extends StateNode{

        @Override
        public void entry() {
            super.entry();
        }

        @Override
        public void exit() {
            super.exit();
        }

        @Override
        public void timerExpired(NamedTimerTask timerTask) {
            super.timerExpired(timerTask);
        }

        @Override
        public void packetReceived(DatagramPacket packet) {
            super.packetReceived(packet);
        }
    }

    private class StateHang extends StateNode {

        @Override
        public void entry() {
            super.entry();
        }

        @Override
        public void exit() {
            super.exit();
        }

        @Override
        public void timerExpired(NamedTimerTask timerTask) {
            super.timerExpired(timerTask);
        }

        @Override
        public void packetReceived(DatagramPacket packet) {
            super.packetReceived(packet);
        }
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
