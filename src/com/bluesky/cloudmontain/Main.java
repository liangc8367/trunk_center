package com.bluesky.cloudmontain;

import java.lang.Thread;
import java.net.DatagramSocket;
import java.net.DatagramPacket;

/**
 * Trunk Center, the repeater for PTTApp, responsible for
 *  - registration
 *  - call management
 *      + call validation
 *      + call setup
 *      + call maintainance
 *      + call teardown
 *
 *  Author: Liang C
 *  Revision:
 *      - 2014-12-31: initial version
 */

public class Main {

    public static void main(String[] args) {
        TrunkManager trunkManager = new TrunkManager();
        trunkManager.start();
        while(true){
            try {
                Thread.sleep(1000);
            }catch (Exception e){
                System.out.print("exception: " + e);
            }
        }
    }
}
