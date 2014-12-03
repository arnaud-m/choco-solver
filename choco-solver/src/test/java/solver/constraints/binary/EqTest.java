/**
 *  Copyright (c) 1999-2014, Ecole des Mines de Nantes
 *  All rights reserved.
 *  Redistribution and use in source and binary forms, with or without
 *  modification, are permitted provided that the following conditions are met:
 *
 *      * Redistributions of source code must retain the above copyright
 *        notice, this list of conditions and the following disclaimer.
 *      * Redistributions in binary form must reproduce the above copyright
 *        notice, this list of conditions and the following disclaimer in the
 *        documentation and/or other materials provided with the distribution.
 *      * Neither the name of the Ecole des Mines de Nantes nor the
 *        names of its contributors may be used to endorse or promote products
 *        derived from this software without specific prior written permission.
 *
 *  THIS SOFTWARE IS PROVIDED BY THE REGENTS AND CONTRIBUTORS ``AS IS'' AND ANY
 *  EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 *  WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 *  DISCLAIMED. IN NO EVENT SHALL THE REGENTS AND CONTRIBUTORS BE LIABLE FOR ANY
 *  DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 *  (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 *  LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 *  ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 *  (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 *  SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.chocosolver.solver.constraints.binary;

import org.chocosolver.solver.Solver;
import org.chocosolver.solver.constraints.ICF;
import org.chocosolver.solver.variables.IntVar;
import org.chocosolver.solver.variables.VF;
import org.chocosolver.util.ESat;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * <br/>
 *
 * @author Charles Prud'homme
 * @since 12/06/13
 */
public class EqTest {

    @Test(groups = "1s")
    public void test1() {
        Solver s = new Solver();
        IntVar two1 = VF.fixed(2, s);
        IntVar two2 = VF.fixed(2, s);
        s.post(ICF.arithm(two1, "=", two2));
        Assert.assertTrue(s.findSolution());
        Assert.assertEquals(ESat.TRUE, s.isSatisfied());
    }


    @Test(groups = "1s")
    public void test2() {
        Solver s = new Solver();
        IntVar three = VF.fixed(3, s);
        IntVar two = VF.fixed(2, s);
        s.post(ICF.arithm(three, "-", two, "=", 1));
        Assert.assertTrue(s.findSolution());
        Assert.assertEquals(ESat.TRUE, s.isSatisfied());
    }

    @Test(groups = "1s")
    public void test3() {
        Solver s = new Solver();
        IntVar three = VF.fixed(3, s);
        IntVar two = VF.fixed(2, s);
        s.post(ICF.arithm(three, "=", two, "+", 1));
        Assert.assertTrue(s.findSolution());
        Assert.assertEquals(ESat.TRUE, s.isSatisfied());
    }
}
