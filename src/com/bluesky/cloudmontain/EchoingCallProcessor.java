package com.bluesky.cloudmontain;

import java.net.DatagramPacket;
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
        @Override
        public void run(){

        }
    }

    /** public methods and members */

    public EchoingCallProcessor(){

    }

    public void handleUdpPacket(DatagramPacket packet){
        LOGGER.info(TAG + "rxed packet from " + packet.getAddress());
    }


    /** private methods and members */


    static final String TAG = "EchoingCP";
    private final static Logger LOGGER  = Logger.getLogger(UDPService.class.getName());
}
