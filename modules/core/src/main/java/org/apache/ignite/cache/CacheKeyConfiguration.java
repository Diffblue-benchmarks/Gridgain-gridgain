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

package org.apache.ignite.cache;

import java.io.Serializable;
import java.lang.reflect.Field;
import org.apache.ignite.cache.affinity.AffinityKeyMapped;
import org.apache.ignite.internal.util.typedef.internal.S;

/**
 * Configuration defining various aspects of cache keys without explicit usage of annotations on user classes.
 */
public class CacheKeyConfiguration implements Serializable {
    /** */
    private static final long serialVersionUID = 0L;

    /** Type name. */
    private String typeName;

    /** Affinity key field name. */
    private String affKeyFieldName;

    /**
     * Creates an empty cache key configuration that should be populated via setters.
     */
    public CacheKeyConfiguration() {
        // Convenience no-op constructor.
    }

    /**
     * @param keyCls Key class.
     */
    public CacheKeyConfiguration(Class keyCls) {
        typeName = keyCls.getName();

        for (; keyCls != Object.class && keyCls != null; keyCls = keyCls.getSuperclass()) {
            for (Field f : keyCls.getDeclaredFields()) {
                if (f.getAnnotation(AffinityKeyMapped.class) != null) {
                    affKeyFieldName = f.getName();

                    return;
                }
            }
        }
    }

    /**
     * Creates cache key configuration with given type name and affinity field name.
     *
     * @param typeName Type name.
     * @param affKeyFieldName Affinity field name.
     */
    public CacheKeyConfiguration(String typeName, String affKeyFieldName) {
        this.typeName = typeName;
        this.affKeyFieldName = affKeyFieldName;
    }

    /**
     * Sets type name for which affinity field name is being defined.
     *
     * @return Type name.
     */
    public String getTypeName() {
        return typeName;
    }

    /**
     * @param typeName Type name for which affinity field name is being defined.
     * @return {@code this} for chaining.
     */
    public CacheKeyConfiguration setTypeName(String typeName) {
        this.typeName = typeName;

        return this;
    }

    /**
     * Gets affinity key field name.
     *
     * @return Affinity key field name.
     */
    public String getAffinityKeyFieldName() {
        return affKeyFieldName;
    }

    /**
     * Sets affinity key field name.
     *
     * @param affKeyFieldName Affinity key field name.
     * @return {@code this} for chaining.
     */
    public CacheKeyConfiguration setAffinityKeyFieldName(String affKeyFieldName) {
        this.affKeyFieldName = affKeyFieldName;

        return this;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(CacheKeyConfiguration.class, this);
    }
}
