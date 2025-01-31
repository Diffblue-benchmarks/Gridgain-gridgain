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

package org.apache.ignite.tests.pojos;

import org.apache.ignite.cache.affinity.AffinityKeyMapped;
import org.apache.ignite.cache.query.annotations.QuerySqlField;

import java.io.Serializable;

/**
 * Simple POJO without getters/setters which could be stored as a key in Ignite cache
 */
public class SimplePersonId implements Serializable {
    /** */
    @AffinityKeyMapped
    @QuerySqlField(name = "company_code")
    public String companyCode;

    /** */
    @AffinityKeyMapped
    @QuerySqlField(name = "department_code")
    public String departmentCode;

    /** */
    @QuerySqlField(name = "person_num")
    public long personNum;

    /** */
    public SimplePersonId() {
    }

    /** */
    public SimplePersonId(PersonId personId) {
        this.companyCode = personId.getCompanyCode();
        this.departmentCode = personId.getDepartmentCode();
        this.personNum = personId.getPersonNumber();
    }

    /** */
    public SimplePersonId(String companyCode, String departmentCode, long personNum) {
        this.companyCode = companyCode;
        this.departmentCode = departmentCode;
        this.personNum = personNum;
    }

    /** {@inheritDoc} */
    @SuppressWarnings("SimplifiableIfStatement")
    @Override public boolean equals(Object obj) {
        if (obj == null || !(obj instanceof SimplePersonId))
            return false;

        SimplePersonId id = (SimplePersonId)obj;

        if ((companyCode != null && !companyCode.equals(id.companyCode)) ||
            (id.companyCode != null && !id.companyCode.equals(companyCode)))
            return false;

        if ((companyCode != null && !companyCode.equals(id.companyCode)) ||
            (id.companyCode != null && !id.companyCode.equals(companyCode)))
            return false;

        return personNum == id.personNum;
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        String code = (companyCode == null ? "" : companyCode) +
            (departmentCode == null ? "" : departmentCode) +
                personNum;

        return code.hashCode();
    }
}
