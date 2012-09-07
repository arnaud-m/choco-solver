/*
 * Copyright (c) 1999-2012, Ecole des Mines de Nantes
 * All rights reserved.
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of the Ecole des Mines de Nantes nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE REGENTS AND CONTRIBUTORS ``AS IS'' AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE REGENTS AND CONTRIBUTORS BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

/**
 * Created by IntelliJ IDEA.
 * User: Jean-Guillaume Fages
 * Date: 03/10/11
 * Time: 19:56
 */

package solver.constraints.propagators.gary.tsp.directed.position;

import choco.kernel.ESat;
import choco.kernel.common.util.procedure.PairProcedure;
import choco.kernel.common.util.tools.ArrayUtils;
import choco.kernel.memory.IStateInt;
import gnu.trove.list.array.TIntArrayList;
import solver.Solver;
import solver.constraints.Constraint;
import solver.constraints.propagators.Propagator;
import solver.constraints.propagators.PropagatorPriority;
import solver.exception.ContradictionException;
import solver.variables.EventType;
import solver.variables.IntVar;
import solver.variables.Variable;
import solver.variables.delta.IGraphDeltaMonitor;
import solver.variables.graph.INeighbors;
import solver.variables.graph.directedGraph.DirectedGraphVar;
import solver.variables.graph.directedGraph.IDirectedGraph;

import java.util.BitSet;

/**
 * @PropAnn(tested = {BENCHMARK})
 */
public class PropPosInTourGraphReactor extends Propagator {

    //***********************************************************************************
    // VARIABLES
    //***********************************************************************************

    DirectedGraphVar g;
    IGraphDeltaMonitor gdm;
    int n;
    IntVar[] intVars;
    private PairProcedure arcEnforced, arcRemoved;
    IStateInt nR;
    IStateInt[] sccOf;
    INeighbors[] outArcs;
    IDirectedGraph rg;
    // data for algorithms
    BitSet done;
    TIntArrayList nextSCCnodes = new TIntArrayList();
    TIntArrayList currentSet = new TIntArrayList();
    TIntArrayList nextSet = new TIntArrayList();
    TIntArrayList tmp = null;

    //***********************************************************************************
    // CONSTRUCTORS
    //***********************************************************************************

    public PropPosInTourGraphReactor(IntVar[] intVars, DirectedGraphVar graph, Constraint constraint, Solver solver) {
        super(ArrayUtils.append(new Variable[]{graph}, intVars), solver, constraint, PropagatorPriority.LINEAR);
        g = graph;
        gdm = g.monitorDelta(this);
        this.intVars = intVars;
        this.n = g.getEnvelopGraph().getNbNodes();
        arcEnforced = new EnfArc();
        arcRemoved = new RemArc();
        done = new BitSet(n);
    }

    public PropPosInTourGraphReactor(IntVar[] intVars, DirectedGraphVar graph, Constraint constraint, Solver solver,
                                     IStateInt nR, IStateInt[] sccOf, INeighbors[] outArcs, IDirectedGraph rg) {
        this(intVars, graph, constraint, solver);
        this.nR = nR;
        this.sccOf = sccOf;
        this.outArcs = outArcs;
        this.rg = rg;
    }

    //***********************************************************************************
    // METHODS
    //***********************************************************************************

    @Override
    public void propagate(int evtmask) throws ContradictionException {
        if ((evtmask & EventType.FULL_PROPAGATION.mask) != 0) {
            int s;
            for (int i = 0; i < n; i++) {
                s = g.getKernelGraph().getSuccessorsOf(i).getFirstElement();
                if (s == -1) {
                    for (int j = 0; j < n; j++) {
                        if (!g.getEnvelopGraph().arcExists(i, j)) {
                            remArc(i, j);
                        }
                    }
                } else {
                    enfArc(i, s);
                }
            }
        }
        graphTrasversal();
        gdm.freeze();
        gdm.unfreeze();
    }

    @Override
    public void propagate(int idxVarInProp, int mask) throws ContradictionException {
        if (idxVarInProp == 0) {
            gdm.freeze();
            gdm.forEachArc(arcEnforced, EventType.ENFORCEARC);
            gdm.forEachArc(arcRemoved, EventType.REMOVEARC);
            gdm.unfreeze();
        }
        forcePropagate(EventType.CUSTOM_PROPAGATION);
    }

    @Override
    public int getPropagationConditions(int vIdx) {
        return EventType.REMOVEARC.mask + EventType.ENFORCEARC.mask
                + EventType.DECUPP.mask + EventType.INCLOW.mask;
    }

    @Override
    public int getPropagationConditions() {
        return EventType.FULL_PROPAGATION.mask + EventType.CUSTOM_PROPAGATION.mask;
    }

    @Override
    public ESat isEntailed() {
        return ESat.UNDEFINED;
    }

    //***********************************************************************************
    // GRAPH TRASVERSALS
    //***********************************************************************************

    private void graphTrasversal() throws ContradictionException {
        if (rg == null) {
            BFS();
            BFSfromEnd();
        } else {
            BFS_RG();
            BFSfromEnd_RG();
        }
    }

    private void BFS() throws ContradictionException {
        done.clear();
        currentSet.clear();
        nextSet.clear();
        tmp = null;
        int x = 0;
        nextSet.add(x);
        int level = 0;
        while (nextSet.size() > 0) {
            tmp = currentSet;
            currentSet = nextSet;
            nextSet = tmp;
            nextSet.clear();
            for (int i = currentSet.size() - 1; i >= 0; i--) {
                x = currentSet.get(i);
                intVars[x].updateLowerBound(level, aCause);
                INeighbors nei = g.getEnvelopGraph().getSuccessorsOf(x);
                for (int j = nei.getFirstElement(); j >= 0; j = nei.getNextElement()) {
                    if (!done.get(j)) {
                        nextSet.add(j);
                        done.set(j);
                    }
                }
            }
            level++;
        }
    }

    private void BFS_RG() throws ContradictionException {
        done.clear();
        currentSet.clear();
        nextSet.clear();
        nextSCCnodes.clear();
        tmp = null;
        /// --------
        int x = 0;
        int nbNode = 0;
        nextSet.add(x);
        int level = 0;
        int scc = sccOf[x].get();
        while (scc != -1) {
            while (nextSet.size() > 0) {
                tmp = currentSet;
                currentSet = nextSet;
                nextSet = tmp;
                nextSet.clear();
                for (int i = currentSet.size() - 1; i >= 0; i--) {
                    nbNode++;
                    x = currentSet.get(i);
                    intVars[x].updateLowerBound(level, aCause);
                    INeighbors nei = g.getEnvelopGraph().getSuccessorsOf(x);
                    for (int j = nei.getFirstElement(); j >= 0; j = nei.getNextElement()) {
                        if (!done.get(j)) {
                            done.set(j);
                            if (sccOf[j].get() == scc) {
                                nextSet.add(j);
                            } else {
                                nextSCCnodes.add(j);
                            }
                        }
                    }
                }
                level++;
            }
            scc = rg.getSuccessorsOf(scc).getFirstElement();
            tmp = nextSet;
            nextSet = nextSCCnodes;
            nextSCCnodes = tmp;
            nextSCCnodes.clear();
            if (level > nbNode) {
                throw new UnsupportedOperationException();
            }
            level = nbNode;
        }
    }

    private void BFSfromEnd() throws ContradictionException {
        done.clear();
        currentSet.clear();
        nextSet.clear();
        tmp = null;
        /// --------
        int x = n - 1;
        nextSet.add(x);
        int level = n - 1;
        while (nextSet.size() > 0) {
            tmp = currentSet;
            currentSet = nextSet;
            nextSet = tmp;
            nextSet.clear();
            for (int i = currentSet.size() - 1; i >= 0; i--) {
                x = currentSet.get(i);
                intVars[x].updateUpperBound(level, aCause);
                INeighbors nei = g.getEnvelopGraph().getPredecessorsOf(x);
                for (int j = nei.getFirstElement(); j >= 0; j = nei.getNextElement()) {
                    if (!done.get(j)) {
                        nextSet.add(j);
                        done.set(j);
                    }
                }
            }
            level--;
        }
    }

    private void BFSfromEnd_RG() throws ContradictionException {
        done.clear();
        currentSet.clear();
        nextSet.clear();
        nextSCCnodes.clear();
        tmp = null;
        /// --------
        int x = n - 1;
        nextSet.add(x);
        int level = n - 1;
        int nbNodes = n - 1;
        int scc = sccOf[x].get();
        while (scc != -1) {
            while (nextSet.size() > 0) {
                tmp = currentSet;
                currentSet = nextSet;
                nextSet = tmp;
                nextSet.clear();
                for (int i = currentSet.size() - 1; i >= 0; i--) {
                    nbNodes--;
                    x = currentSet.get(i);
                    intVars[x].updateUpperBound(level, aCause);
                    INeighbors nei = g.getEnvelopGraph().getPredecessorsOf(x);
                    for (int j = nei.getFirstElement(); j >= 0; j = nei.getNextElement()) {
                        if (!done.get(j)) {
                            done.set(j);
                            if (sccOf[j].get() == scc) {
                                nextSet.add(j);
                            } else {
                                nextSCCnodes.add(j);
                            }
                        }
                    }
                }
                level--;
            }
            scc = rg.getPredecessorsOf(scc).getFirstElement();
            tmp = nextSet;
            nextSet = nextSCCnodes;
            nextSCCnodes = tmp;
            nextSCCnodes.clear();
            if (level < nbNodes) {
                throw new UnsupportedOperationException();
            }
            level = nbNodes;
        }
    }

    //***********************************************************************************
    // PROCEDURES
    //***********************************************************************************

    private void enfArc(int from, int to) throws ContradictionException {
        intVars[from].updateUpperBound(intVars[to].getUB() - 1, aCause);
        intVars[to].updateLowerBound(intVars[from].getLB() + 1, aCause);
    }

    private void remArc(int from, int to) throws ContradictionException {
        if (from != to) {
            if (intVars[from].instantiated()) {
                intVars[to].removeValue(intVars[from].getValue() + 1, aCause);
            }
            if (intVars[to].instantiated()) {
                intVars[from].removeValue(intVars[to].getValue() - 1, aCause);
            }
        }
    }

    private class EnfArc implements PairProcedure {
        @Override
        public void execute(int i, int j) throws ContradictionException {
            enfArc(i, j);
        }
    }

    private class RemArc implements PairProcedure {
        @Override
        public void execute(int i, int j) throws ContradictionException {
            remArc(i, j);
        }
    }
}
