/*
 * Copyright 2019 GridGain Systems, Inc. and Contributors.
 *
 * Licensed under the GridGain Community Edition License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.gridgain.com/products/software/community-edition/gridgain-community-edition-license
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ignite.ml.selection.paramgrid;

import java.util.HashMap;
import java.util.Map;

/**
 * Keeps the grid of parameters.
 */
public class ParamGrid {
    /** Parameter values by parameter index. */
    private Map<Integer, Double[]> paramValuesByParamIdx = new HashMap<>();

    /** Parameter names by parameter index. */
    private Map<Integer, String> paramNamesByParamIdx = new HashMap<>();

    /** Parameter counter. */
    private int paramCntr;

    /** */
    public Map<Integer, Double[]> getParamValuesByParamIdx() {
        return paramValuesByParamIdx;
    }

    /**
     * Adds a grid for the specific hyper parameter.
     * @param paramName The parameter name.
     * @param params The array of the given hyper parameter values.
     * @return The updated ParamGrid.
     */
    public ParamGrid addHyperParam(String paramName, Double[] params) {
        paramValuesByParamIdx.put(paramCntr, params);
        paramNamesByParamIdx.put(paramCntr, paramName);
        paramCntr++;
        return this;
    }

    /** */
    public String getParamNameByIndex(int idx) {
        return paramNamesByParamIdx.get(idx);
    }
}
