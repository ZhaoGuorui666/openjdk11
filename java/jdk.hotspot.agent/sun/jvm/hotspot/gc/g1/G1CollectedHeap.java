/*
 * Copyright (c) 2011, 2018, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */

package sun.jvm.hotspot.gc.g1;

import java.io.PrintStream;
import java.util.Iterator;
import java.util.Observable;
import java.util.Observer;

import sun.jvm.hotspot.debugger.Address;
import sun.jvm.hotspot.gc.shared.CollectedHeap;
import sun.jvm.hotspot.gc.shared.CollectedHeapName;
import sun.jvm.hotspot.gc.shared.SpaceClosure;
import sun.jvm.hotspot.gc.shared.PrintRegionClosure;
import sun.jvm.hotspot.memory.MemRegion;
import sun.jvm.hotspot.runtime.VM;
import sun.jvm.hotspot.runtime.VMObjectFactory;
import sun.jvm.hotspot.types.AddressField;
import sun.jvm.hotspot.types.CIntegerField;
import sun.jvm.hotspot.types.Type;
import sun.jvm.hotspot.types.TypeDataBase;
import sun.jvm.hotspot.tools.HeapSummary;

// Mirror class for G1CollectedHeap.

public class G1CollectedHeap extends CollectedHeap {
    // HeapRegionManager _hrm;
    static private long hrmFieldOffset;
    // MemRegion _g1_reserved;
    static private long g1ReservedFieldOffset;
    // size_t _summary_bytes_used;
    static private CIntegerField summaryBytesUsedField;
    // G1MonitoringSupport* _g1mm;
    static private AddressField g1mmField;
    // HeapRegionSet _old_set;
    static private long oldSetFieldOffset;
    // HeapRegionSet _archive_set;
    static private long archiveSetFieldOffset;
    // HeapRegionSet _humongous_set;
    static private long humongousSetFieldOffset;

    static {
        VM.registerVMInitializedObserver(new Observer() {
                public void update(Observable o, Object data) {
                    initialize(VM.getVM().getTypeDataBase());
                }
            });
    }

    static private synchronized void initialize(TypeDataBase db) {
        Type type = db.lookupType("G1CollectedHeap");

        hrmFieldOffset = type.getField("_hrm").getOffset();
        summaryBytesUsedField = type.getCIntegerField("_summary_bytes_used");
        g1mmField = type.getAddressField("_g1mm");
        oldSetFieldOffset = type.getField("_old_set").getOffset();
        archiveSetFieldOffset = type.getField("_archive_set").getOffset();
        humongousSetFieldOffset = type.getField("_humongous_set").getOffset();
    }

    public long capacity() {
        return hrm().capacity();
    }

    public long used() {
        return summaryBytesUsedField.getValue(addr);
    }

    public long n_regions() {
        return hrm().length();
    }

    public HeapRegionManager hrm() {
        Address hrmAddr = addr.addOffsetTo(hrmFieldOffset);
        return (HeapRegionManager) VMObjectFactory.newObject(HeapRegionManager.class,
                                                         hrmAddr);
    }

    public G1MonitoringSupport g1mm() {
        Address g1mmAddr = g1mmField.getValue(addr);
        return (G1MonitoringSupport) VMObjectFactory.newObject(G1MonitoringSupport.class, g1mmAddr);
    }

    public HeapRegionSetBase oldSet() {
        Address oldSetAddr = addr.addOffsetTo(oldSetFieldOffset);
        return (HeapRegionSetBase) VMObjectFactory.newObject(HeapRegionSetBase.class,
                                                             oldSetAddr);
    }

    public HeapRegionSetBase archiveSet() {
        Address archiveSetAddr = addr.addOffsetTo(archiveSetFieldOffset);
        return (HeapRegionSetBase) VMObjectFactory.newObject(HeapRegionSetBase.class,
                                                             archiveSetAddr);
    }

    public HeapRegionSetBase humongousSet() {
        Address humongousSetAddr = addr.addOffsetTo(humongousSetFieldOffset);
        return (HeapRegionSetBase) VMObjectFactory.newObject(HeapRegionSetBase.class,
                                                             humongousSetAddr);
    }

    private Iterator<HeapRegion> heapRegionIterator() {
        return hrm().heapRegionIterator();
    }

    public void heapRegionIterate(SpaceClosure scl) {
        Iterator<HeapRegion> iter = heapRegionIterator();
        while (iter.hasNext()) {
            HeapRegion hr = iter.next();
            scl.doSpace(hr);
        }
    }

    public CollectedHeapName kind() {
        return CollectedHeapName.G1;
    }

    @Override
    public void printOn(PrintStream tty) {
        MemRegion mr = reservedRegion();

        tty.print("garbage-first heap");
        tty.print(" [" + mr.start() + ", " + mr.end() + "]");
        tty.println(" region size " + (HeapRegion.grainBytes() / 1024) + "K");

        HeapSummary sum = new HeapSummary();
        sum.printG1HeapSummary(this);
    }

    public void printRegionDetails(PrintStream tty) {
        PrintRegionClosure prc = new PrintRegionClosure(tty);
        heapRegionIterate(prc);
    }

    public G1CollectedHeap(Address addr) {
        super(addr);
    }
}
