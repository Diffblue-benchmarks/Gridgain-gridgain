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

package org.apache.ignite.internal.processors.cache.persistence.standbycluster.join.persistence;

import org.apache.ignite.internal.processors.cache.persistence.standbycluster.join.JoinActiveNodeToActiveCluster;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.internal.processors.cache.persistence.standbycluster.AbstractNodeJoinTemplate;
import org.junit.Test;

/**
 *
 */
public class JoinActiveNodeToActiveClusterWithPersistence extends JoinActiveNodeToActiveCluster {
    /** {@inheritDoc} */
    @Override protected IgniteConfiguration cfg(String name) throws Exception {
        return persistentCfg(super.cfg(name));
    }

    /**
     * @param b Builder.
     * @return Builder.
     */
    private AbstractNodeJoinTemplate.JoinNodeTestPlanBuilder persistent(AbstractNodeJoinTemplate.JoinNodeTestPlanBuilder b) {
        b.afterClusterStarted(
            b.checkCacheEmpty()
        ).stateAfterJoin(
            false
        ).afterNodeJoin(
            b.checkCacheEmpty()
        ).afterActivate(
            b.checkCacheNotEmpty()
        );

        return b;
    }

    /** {@inheritDoc} */
    @Override public AbstractNodeJoinTemplate.JoinNodeTestPlanBuilder withOutConfigurationTemplate() throws Exception {
        AbstractNodeJoinTemplate.JoinNodeTestPlanBuilder b = persistent(super.withOutConfigurationTemplate());

        b.afterActivate(b.checkCacheOnlySystem());

        return b;
    }

    /** {@inheritDoc} */
    @Override public AbstractNodeJoinTemplate.JoinNodeTestPlanBuilder joinClientWithOutConfigurationTemplate() throws Exception {
        AbstractNodeJoinTemplate.JoinNodeTestPlanBuilder b = persistent(super.joinClientWithOutConfigurationTemplate());

        b.afterActivate(b.checkCacheOnlySystem());

        return b;
    }

    /** {@inheritDoc} */
    @Test
    @Override public void testJoinWithOutConfiguration() throws Exception {
        withOutConfigurationTemplate().execute();
    }

    /** {@inheritDoc} */
    @Test
    @Override public void testJoinClientWithOutConfiguration() throws Exception {
        joinClientWithOutConfigurationTemplate().execute();
    }

    /** {@inheritDoc} */
    @Test
    @Override public void testJoinClientStaticCacheConfigurationDifferentOnBoth() throws Exception {
        staticCacheConfigurationDifferentOnBothTemplate().execute();
    }

    /** {@inheritDoc} */
    @Test
    @Override public void testJoinClientStaticCacheConfigurationInCluster() throws Exception {
        staticCacheConfigurationInClusterTemplate().execute();
    }

    /** {@inheritDoc} */
    @Override public AbstractNodeJoinTemplate.JoinNodeTestPlanBuilder staticCacheConfigurationOnJoinTemplate() throws Exception {
        return persistent(super.staticCacheConfigurationOnJoinTemplate());
    }

    /** {@inheritDoc} */
    @Override public AbstractNodeJoinTemplate.JoinNodeTestPlanBuilder staticCacheConfigurationInClusterTemplate() throws Exception {
        return persistent(super.staticCacheConfigurationInClusterTemplate());
    }

    /** {@inheritDoc} */
    @Override public AbstractNodeJoinTemplate.JoinNodeTestPlanBuilder staticCacheConfigurationSameOnBothTemplate() throws Exception {
        return persistent(super.staticCacheConfigurationSameOnBothTemplate());
    }

    /** {@inheritDoc} */
    @Override public AbstractNodeJoinTemplate.JoinNodeTestPlanBuilder staticCacheConfigurationDifferentOnBothTemplate() throws Exception {
        return persistent(super.staticCacheConfigurationDifferentOnBothTemplate());
    }

    /** {@inheritDoc} */
    @Override public AbstractNodeJoinTemplate.JoinNodeTestPlanBuilder joinClientStaticCacheConfigurationOnJoinTemplate() throws Exception {
        return persistent(super.joinClientStaticCacheConfigurationOnJoinTemplate());
    }

    /** {@inheritDoc} */
    @Override public AbstractNodeJoinTemplate.JoinNodeTestPlanBuilder joinClientStaticCacheConfigurationInClusterTemplate() throws Exception {
        return persistent(super.joinClientStaticCacheConfigurationInClusterTemplate());
    }

    /** {@inheritDoc} */
    @Override public AbstractNodeJoinTemplate.JoinNodeTestPlanBuilder joinClientStaticCacheConfigurationDifferentOnBothTemplate() throws Exception {
        return persistent(super.joinClientStaticCacheConfigurationDifferentOnBothTemplate());
    }
}
