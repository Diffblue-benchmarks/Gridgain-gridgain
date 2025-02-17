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

package org.apache.ignite.internal.processors.security;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.apache.ignite.IgniteSystemProperties;
import org.apache.ignite.plugin.security.SecurityPermission;

/**
 * Security utilities.
 */
public class SecurityUtils {
    /** Default serialization version. */
    private static final int DFLT_SERIALIZE_VERSION = isSecurityCompatibilityMode() ? 1 : 2;

    /** Current serialization version. */
    private static final ThreadLocal<Integer> SERIALIZE_VERSION = new ThreadLocal<Integer>(){
        @Override protected Integer initialValue() {
            return DFLT_SERIALIZE_VERSION;
        }
    };

    /**
     * Private constructor.
     */
    private SecurityUtils() {
    }

    /**
     * @return Security compatibility mode flag.
     */
    public static boolean isSecurityCompatibilityMode() {
        return IgniteSystemProperties.getBoolean(IgniteSystemProperties.IGNITE_SECURITY_COMPATIBILITY_MODE, false);
    }

    /**
     * @param ver Serialize version.
     */
    public static void serializeVersion(int ver) {
        SERIALIZE_VERSION.set(ver);
    }

    /**
     * @return Serialize version.
     */
    public static int serializeVersion() {
        return SERIALIZE_VERSION.get();
    }

    /**
     * Sets default serialize version {@link #DFLT_SERIALIZE_VERSION}.
     */
    public static void restoreDefaultSerializeVersion() {
        serializeVersion(DFLT_SERIALIZE_VERSION);
    }

    /**
     * @return Allow all service permissions.
     */
    public static Map<String, Collection<SecurityPermission>> compatibleServicePermissions() {
        Map<String, Collection<SecurityPermission>> srvcPerms = new HashMap<>();

        srvcPerms.put("*", Arrays.asList(
            SecurityPermission.SERVICE_CANCEL,
            SecurityPermission.SERVICE_DEPLOY,
            SecurityPermission.SERVICE_INVOKE));

        return srvcPerms;
    }
}
