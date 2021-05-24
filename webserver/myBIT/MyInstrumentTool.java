/* ICount.java
 * Sample program using BIT -- counts the number of instructions executed.
 *
 * Copyright (c) 1997, The Regents of the University of Colorado. All
 * Rights Reserved.
 * 
 * Permission to use and copy this software and its documentation for
 * NON-COMMERCIAL purposes and without fee is hereby granted provided
 * that this copyright notice appears in all copies. If you wish to use
 * or wish to have others use BIT for commercial purposes please contact,
 * Stephen V. O'Neil, Director, Office of Technology Transfer at the
 * University of Colorado at Boulder (303) 492-5647.
 */

package myBIT;

import BIT.highBIT.*;
import java.io.*;
import java.util.*;

public class MyInstrumentTool {
    public static void main(String argv[]) {
        File file_in = new File(argv[0]);
        String infilenames[] = file_in.list();

        for (int i = 0; i < infilenames.length; i++) {
            String infilename = infilenames[i];
            if (infilename.endsWith(".class")) {
				ClassInfo ci = new ClassInfo(argv[0] + System.getProperty("file.separator") + infilename);
                Routine routine = null;
                for (Enumeration e = ci.getRoutines().elements(); e.hasMoreElements(); ) {
                    routine = (Routine) e.nextElement();
                    
                    for (Enumeration b = routine.getBasicBlocks().elements(); b.hasMoreElements(); ) {
                        BasicBlock bb = (BasicBlock) b.nextElement();
                        bb.addBefore("pt/ulisboa/tecnico/cnv/server/WebServer", "count", new Integer(1));
                    }
                }
                //this adds a method to the end of program
                routine.addAfter("pt/ulisboa/tecnico/cnv/server/WebServer", "end", new Integer(1));
                ci.write(argv[1] + System.getProperty("file.separator") + infilename);
            }
        }
    }
}