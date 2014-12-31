package com.bluesky.cloudmontain;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

import java.util.logging.Logger;

/**
 * UDP Service is responsible for send/receive packets to/from
 * connected PTTApp server.
 * Since Andriod doesn't support asynchronous UDP socket, I have
 * to implement such asynchronous behaviour in this class, so Signaling
 * service won't be blocked on receiving.
 *
 * UDP Service is a threaded class for receiving, while sedning is done
 * in caller's context.
 *
 * Created by liangc on 28/12/14.
 */
public class UDPService extends Thread{
    /** Configuration for UDP Service */
    static public class Configuration {
        /* local addr & port */
        public InetSocketAddress addrLocal;

        /* thread name, priority */
    }

    /** Completion handler */
    static public interface CompletionHandler {
        /** invoked when receive is done */
        public void completed(DatagramPacket packet);
    }

    /** public methods of UDP Service */

    public UDPService(Configuration config){
        super("UdpSvc");
        mConfig = config;
    }

    public void setCompletionHandler(CompletionHandler handler){
        mRegisteredHandler = handler;
    }

    public boolean startService(){
        try{
            start();
        } catch (IllegalThreadStateException e){
            LOGGER.warning(TAG + "UDP Service has already started");
            return false;
        }
        return true;
    }

    public boolean stopService(){
        mRunning = false;
        //TODO: send interrupt
        return true;
    }

    public boolean send(DatagramPacket packet){
        if(mSocket != null){
            try {
                mSocket.send(packet);
            } catch (IOException e){
                LOGGER.warning(TAG + "send failed:" + e);
                return false;
            }
            return true;
        }
        return false;
    }

    public boolean send(ByteBuffer payload){
        DatagramPacket pkt  = new DatagramPacket(payload.array(), payload.capacity());
        return send(pkt);
    }


    public void run(){
        if(!bind()){
            return;
        }

        while(mRunning){
            byte[]          rxedBuffer = new byte[MAX_UDP_PACKET_LENGTH];
            DatagramPacket  rxedPacket = new DatagramPacket(rxedBuffer, MAX_UDP_PACKET_LENGTH);
            try {
                mSocket.receive(rxedPacket);
            }catch (IOException e){
                LOGGER.warning(TAG + "rxed failed:" + e);
                continue;
            }
            if (mRegisteredHandler != null) {
                mRegisteredHandler.completed(rxedPacket);
            } else {
                rxedPacket = null;
            }

        }
    }

    /** synchronous receive,
     *  @NOTE: not implemented yet, better to throw exception
     */
    public void receive(){
        ;
    }

    /** private methods */
    /** bind and bind udp socket per configuration
     *
     * @return true if success, else false
     */
    private boolean bind(){
        try {
            mSocket = new DatagramSocket(mConfig.addrLocal);
            mSocket.bind(mConfig.addrLocal);
        }catch ( Exception e ){
            LOGGER.warning(TAG + "failed to bind:" + e);
            mSocket = null;
            return false;
        }
        return true;
    }




    /** private members */
    Configuration   mConfig = null;
    boolean         mRunning = false;
    DatagramSocket  mSocket = null;
    CompletionHandler   mRegisteredHandler = null;

    private final static String TAG = GlobalConstants.TAG + ":UDPSvc:";
    private final static Logger LOGGER  = Logger.getLogger(UDPService.class.getName());
    private final static int MAX_UDP_PACKET_LENGTH = 1000; //TODO: to make it even smaller
}
