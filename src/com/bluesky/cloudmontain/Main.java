package com.bluesky.cloudmontain;

import java.lang.Thread;

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
            Thread.sleep(1000);
        }
    }
}
