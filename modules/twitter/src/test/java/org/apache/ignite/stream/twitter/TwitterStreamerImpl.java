/*
 *                    GridGain Community Edition Licensing
 *                    Copyright 2019 GridGain Systems, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License") modified with Commons Clause
 *  Restriction; you may not use this file except in compliance with the License. You may obtain a
 *  copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the
 *  License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied. See the License for the specific language governing permissions
 *  and limitations under the License.
 *
 *  Commons Clause Restriction
 *
 *  The Software is provided to you by the Licensor under the License, as defined below, subject to
 *  the following condition.
 *
 *  Without limiting other conditions in the License, the grant of rights under the License will not
 *  include, and the License does not grant to you, the right to Sell the Software.
 *  For purposes of the foregoing, “Sell” means practicing any or all of the rights granted to you
 *  under the License to provide to third parties, for a fee or other consideration (including without
 *  limitation fees for hosting or consulting/ support services related to the Software), a product or
 *  service whose value derives, entirely or substantially, from the functionality of the Software.
 *  Any license notice or attribution required by the License must also include this Commons Clause
 *  License Condition notice.
 *
 *  For purposes of the clause above, the “Licensor” is Copyright 2019 GridGain Systems, Inc.,
 *  the “License” is the Apache License, Version 2.0, and the Software is the GridGain Community
 *  Edition software provided with this notice.
 */

package org.apache.ignite.stream.twitter;

import com.twitter.hbc.core.Client;
import com.twitter.hbc.core.HttpHosts;
import com.twitter.hbc.core.endpoint.StreamingEndpoint;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import org.apache.ignite.internal.util.typedef.internal.U;
import org.apache.ignite.lang.IgniteBiTuple;
import org.apache.ignite.stream.StreamSingleTupleExtractor;
import twitter4j.Status;
import twitter4j.TwitterException;
import twitter4j.TwitterObjectFactory;

/**
 * Long, String (With Mocked server support) implementation of TwitterStreamer.
 */
public class TwitterStreamerImpl extends TwitterStreamer<Long, String> {
    /** Mocked server support. */
    HttpHosts hosts;

    /**
     * @param oAuthSettings OAuth Settings
     */
    public TwitterStreamerImpl(OAuthSettings oAuthSettings) {
        super(oAuthSettings);

        setSingleTupleExtractor(new TwitterStreamSingleTupleExtractorImpl());
    }

    /**
     * @param hosts hosts.
     */
    public void setHosts(HttpHosts hosts) {
        this.hosts = hosts;
    }

    /** {@inheritDoc} */
    @Override protected Client buildClient(BlockingQueue<String> tweetQueue, HttpHosts hosts,
        StreamingEndpoint endpoint) {
        return super.buildClient(tweetQueue, this.hosts, endpoint);
    }

    /**
     * Long, String Tweet Single Tuple Extractor.
     */
    class TwitterStreamSingleTupleExtractorImpl implements StreamSingleTupleExtractor<String, Long, String> {
        @Override public Map.Entry<Long, String> extract(String tweet) {
            try {
                Status status = TwitterObjectFactory.createStatus(tweet);

                return new IgniteBiTuple<>(status.getId(), status.getText());
            }
            catch (TwitterException e) {
                U.error(log, e);

                return null;
            }
        }
    }

}
