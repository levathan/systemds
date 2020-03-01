/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.tugraz.sysds.runtime.instructions.fed;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.tugraz.sysds.common.Types;
import org.tugraz.sysds.runtime.DMLRuntimeException;
import org.tugraz.sysds.runtime.controlprogram.caching.MatrixObject;
import org.tugraz.sysds.runtime.controlprogram.context.ExecutionContext;
import org.tugraz.sysds.runtime.controlprogram.federated.*;
import org.tugraz.sysds.runtime.functionobjects.KahanFunction;
import org.tugraz.sysds.runtime.functionobjects.ValueFunction;
import org.tugraz.sysds.runtime.instructions.cp.CPInstruction;
import org.tugraz.sysds.runtime.instructions.cp.CPOperand;
import org.tugraz.sysds.runtime.instructions.cp.KahanObject;
import org.tugraz.sysds.runtime.instructions.cp.ScalarObject;
import org.tugraz.sysds.runtime.instructions.fed.BinaryFEDInstruction;
import org.tugraz.sysds.runtime.instructions.fed.FEDInstruction;
import org.tugraz.sysds.runtime.matrix.data.MatrixBlock;
import org.tugraz.sysds.runtime.matrix.data.MatrixValue;
import org.tugraz.sysds.runtime.matrix.operators.BinaryOperator;
import org.tugraz.sysds.runtime.matrix.operators.Operator;
import org.tugraz.sysds.runtime.matrix.operators.ScalarOperator;
import org.tugraz.sysds.runtime.meta.MatrixCharacteristics;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Future;

public class BinaryMatrixScalarFEDInstruction extends BinaryFEDInstruction
{
    protected BinaryMatrixScalarFEDInstruction(
            Operator op,
            CPOperand in1,
            CPOperand in2,
            CPOperand out,
            String opcode,
            String istr) {
        super(FEDType.Binary, op, in1, in2, out, opcode, istr);
    }

    @Override
    public void processInstruction(ExecutionContext ec) {
        MatrixObject matrix = ec.getMatrixObject(input1.isMatrix() ? input1 : input2);
        ScalarObject scalar = ec.getScalarInput(input2.isScalar() ? input2 : input1);

        ScalarOperator sc_op = (ScalarOperator) _optr;
        sc_op = sc_op.setConstant(scalar.getDoubleValue());

        if (!matrix.isFederated())
            throw new DMLRuntimeException("Trying to execute federated operation on non federated matrix");

        MatrixBlock ret = new MatrixBlock((int)matrix.getNumRows(), (int) matrix.getNumColumns(), false);
        try {

            //Keep track on federated execution ond matrix shards
            List<Pair<FederatedRange, Future<FederatedResponse>>> idResponsePairs = new ArrayList<>();

            //execute federated operation
            for (Map.Entry<FederatedRange, FederatedData> entry : matrix.getFedMapping().entrySet()) {
                FederatedData shard = entry.getValue();
                if (!shard.isInitialized())
                    throw new DMLRuntimeException("Not all FederatedData was initialized for federated matrix");
                Future<FederatedResponse> future = shard.executeFederatedOperation(
                        new FederatedRequest(FederatedRequest.FedMethod.SCALAR, sc_op), true);

                idResponsePairs.add(new ImmutablePair<>(entry.getKey(), future));
            }

            for (Pair<FederatedRange, Future<FederatedResponse>> idResponsePair : idResponsePairs) {
                FederatedRange range = idResponsePair.getLeft();
                //wait for fed workers finishing their work
                FederatedResponse federatedResponse = idResponsePair.getRight().get();
                if (!federatedResponse.isSuccessful())
                    throw new DMLRuntimeException("Federated binary operation failed: " + federatedResponse.getErrorMessage());

                MatrixBlock shard = (MatrixBlock) federatedResponse.getData();

                ret.copy(
                        range.getBeginDimsInt()[0],
                        range.getEndDimsInt()[0]-1,
                        range.getBeginDimsInt()[1],
                        range.getEndDimsInt()[1]-1,
                        shard,
                        false);
            }

        } catch (Exception e) {
            throw new DMLRuntimeException("Federated binary operation failed", e);
        }

        if(ret.getNumRows() != matrix.getNumRows() || ret.getNumColumns() != matrix.getNumColumns())
            throw new DMLRuntimeException("Federated binary operation returns invalid matrix dimension");


        ec.setMatrixOutput(output.getName(), ret);

    }
}
