package com.bluesky.cloudmontain.main;

import com.bluesky.cloudmontain.repeator.TrunkManager;

import java.lang.Thread;

/**
 * Trunk Center, the repeator for PTTApp, responsible for
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
        System.out.println("Trunking Control Manager started.");
        TrunkManager trunkManager = new TrunkManager();
        trunkManager.start();
        while(true){
            try {
                Thread.sleep(1000);
            }catch (Exception e){
                System.out.println("exception: " + e);
            }
        }
    }
}
