package com.bluesky.cloudmontain;

import com.bluesky.common.NamedTimerTask;
import com.bluesky.common.OLog;

import java.net.DatagramPacket;
import java.util.concurrent.ExecutorService;

/** call processor, running in dedicated thread context
 * Created by liangc on 14/02/15.
 */
public class ThreadedCP extends CallProcessor{
    public ThreadedCP(ExecutorService exec, long grp_id, long su_id, final Repeator rptr,  SubscriberDatabase database, final OLog logger){
        super(grp_id, su_id, rptr, database, logger);
        mExec = exec;
    }

    final ExecutorService mExec;

    ///////////////////// triggers, relay ////////////////////////////////////
    private abstract class Trigger implements Runnable {
    }

    private class TriggerTimerExp extends Trigger{
        public TriggerTimerExp(NamedTimerTask timerTask){
            mTimerTask = timerTask;
        }

        @Override
        public void run(){
            ThreadedCP.this.timerExpiredInternal(mTimerTask);
        }
        NamedTimerTask mTimerTask;
    }

    private class TriggerPacketRxed extends Trigger {
        public TriggerPacketRxed(DatagramPacket packet) {
            mPacket = packet;
        }
        @Override
        public void run(){
            ThreadedCP.this.packetReceivedInternal(mPacket);
        }
        DatagramPacket mPacket;
    }

    @Override
    public void packetReceived(DatagramPacket packet) {
        TriggerPacketRxed tgPkt = new TriggerPacketRxed(packet);
        mExec.execute(tgPkt);
    }

    @Override
    protected NamedTimerTask createTimerTask() {
        ++mTimerSeed;
        mLogger.d(TAG, "create timer Task[" + mTimerSeed + "]");
        return new NamedTimerTask(mTimerSeed) {
            @Override
            public void run() {
                TriggerTimerExp tgTimer = new TriggerTimerExp(this);
                mExec.execute(tgTimer);
            }
        };
    }

    private void timerExpiredInternal(NamedTimerTask timerTask){
        super.timerExpired(timerTask);
    }

    private void packetReceivedInternal(DatagramPacket packet){
        super.packetReceived(packet);
    }

}
