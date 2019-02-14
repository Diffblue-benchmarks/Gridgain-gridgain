/*
 *                   GridGain Community Edition Licensing
 *                   Copyright 2019 GridGain Systems, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License") modified with Commons Clause
 * Restriction; you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * Commons Clause Restriction
 *
 * The Software is provided to you by the Licensor under the License, as defined below, subject to
 * the following condition.
 *
 * Without limiting other conditions in the License, the grant of rights under the License will not
 * include, and the License does not grant to you, the right to Sell the Software.
 * For purposes of the foregoing, “Sell” means practicing any or all of the rights granted to you
 * under the License to provide to third parties, for a fee or other consideration (including without
 * limitation fees for hosting or consulting/ support services related to the Software), a product or
 * service whose value derives, entirely or substantially, from the functionality of the Software.
 * Any license notice or attribution required by the License must also include this Commons Clause
 * License Condition notice.
 *
 * For purposes of the clause above, the “Licensor” is Copyright 2019 GridGain Systems, Inc.,
 * the “License” is the Apache License, Version 2.0, and the Software is the GridGain Community
 * Edition software provided with this notice.
 */

package org.apache.ignite.scalar.tests

import org.apache.ignite.IgniteCache
import org.apache.ignite.cache.CachePeekMode
import org.apache.ignite.cluster.ClusterNode
import org.apache.ignite.scalar.scalar._
import org.junit.runner.RunWith
import org.scalatest._
import org.scalatest.junit.JUnitRunner

import scala.collection.JavaConversions._

/**
 * Tests for Scalar cache queries API.
 */
@RunWith(classOf[JUnitRunner])
class ScalarCacheQueriesSpec extends FunSpec with ShouldMatchers with BeforeAndAfterAll {
    /** Entries count. */
    private val ENTRY_CNT = 10

    /** Words. */
    private val WORDS = List("", "one", "two", "three", "four", "five",
        "six", "seven", "eight", "nine", "ten")

    /** Node. */
    private var n: ClusterNode = null

    /** Cache. */
    private var c: IgniteCache[Int, ObjectValue] = null

    /**
     * Start node and put data to cache.
     */
    override def beforeAll() {
        n = start("modules/scalar/src/test/resources/spring-cache.xml").cluster().localNode

        c = cache$[Int, ObjectValue]("default").get

        (1 to ENTRY_CNT).foreach(i => c.put(i, ObjectValue(i, "str " + WORDS(i))))

        assert(c.size(Array.empty[CachePeekMode]:_*) == ENTRY_CNT)

        c.foreach(e => println(e.getKey + " -> " + e.getValue))
    }

    /**
     * Stop node.
     */
    override def afterAll() {
        stop()
    }

    describe("Scalar cache queries API") {
        it("should correctly execute SCAN queries") {
            var res = c.scan(classOf[ObjectValue], (k: Int, v: ObjectValue) => k > 5 && v.intVal < 8).getAll

            assert(res.size == 2)

            res.foreach(t => assert(t.getKey > 5 && t.getKey < 8 && t.getKey == t.getValue.intVal))

            res = c.scan((k: Int, v: ObjectValue) => k > 5 && v.intVal < 8).getAll

            assert(res.size == 2)

            res.foreach(t => assert(t.getKey > 5 && t.getKey < 8 && t.getKey == t.getValue.intVal))

            res = c.scan(classOf[ObjectValue], (k: Int, v: ObjectValue) => k > 5 && v.intVal < 8).getAll

            assert(res.size == 2)

            res.foreach(t => assert(t.getKey > 5 && t.getKey < 8 && t.getKey == t.getValue.intVal))

            res = c.scan((k: Int, v: ObjectValue) => k > 5 && v.intVal < 8).getAll

            assert(res.size == 2)

            res.foreach(t => assert(t.getKey > 5 && t.getKey < 8 && t.getKey == t.getValue.intVal))
        }

        it("should correctly execute SQL queries") {
            var res = c.sql(classOf[ObjectValue], "intVal > 5").getAll

            assert(res.size == ENTRY_CNT - 5)

            res.foreach(t => assert(t.getKey > 5 && t.getKey == t.getValue.intVal))

            res = c.sql(classOf[ObjectValue], "intVal > ?", 5).getAll

            assert(res.size == ENTRY_CNT - 5)

            res.foreach(t => assert(t.getKey > 5 && t.getKey == t.getValue.intVal))

            res = c.sql("intVal > 5").getAll

            assert(res.size == ENTRY_CNT - 5)

            res.foreach(t => assert(t.getKey > 5 && t.getKey == t.getValue.intVal))

            res = c.sql("intVal > ?", 5).getAll

            assert(res.size == ENTRY_CNT - 5)

            res.foreach(t => assert(t.getKey > 5 && t.getKey == t.getValue.intVal))

            res = c.sql(classOf[ObjectValue], "intVal > 5").getAll

            assert(res.size == ENTRY_CNT - 5)

            res.foreach(t => assert(t.getKey > 5 && t.getKey == t.getValue.intVal))

            res = c.sql(classOf[ObjectValue], "intVal > ?", 5).getAll

            assert(res.size == ENTRY_CNT - 5)

            res.foreach(t => assert(t.getKey > 5 && t.getKey == t.getValue.intVal))

            res.foreach(t => assert(t.getKey > 5 && t.getKey == t.getValue.intVal))

            res = c.sql("intVal > 5").getAll

            assert(res.size == ENTRY_CNT - 5)

            res.foreach(t => assert(t.getKey > 5 && t.getKey == t.getValue.intVal))

            res = c.sql("intVal > ?", 5).getAll

            assert(res.size == ENTRY_CNT - 5)

            res.foreach(t => assert(t.getKey > 5 && t.getKey == t.getValue.intVal))
        }

        it("should correctly execute TEXT queries") {
            var res = c.text(classOf[ObjectValue], "str").getAll

            assert(res.size == ENTRY_CNT)

            res = c.text(classOf[ObjectValue], "five").getAll

            assert(res.size == 1)
            assert(res.head.getKey == 5)

            res = c.text("str").getAll

            assert(res.size == ENTRY_CNT)

            res = c.text("five").getAll

            assert(res.size == 1)
            assert(res.head.getKey == 5)

            res = c.text(classOf[ObjectValue], "str").getAll

            assert(res.size == ENTRY_CNT)

            res = c.text(classOf[ObjectValue], "five").getAll

            assert(res.size == 1)
            assert(res.head.getKey == 5)

            res = c.text("str").getAll

            assert(res.size == ENTRY_CNT)

            res = c.text("five").getAll

            assert(res.size == 1)
            assert(res.head.getKey == 5)
        }

        it("should correctly execute fields queries") {
            var res = c.sqlFields("select intVal from ObjectValue where intVal > 5").getAll

            assert(res.size == ENTRY_CNT - 5)

            res.foreach(t => assert(t.size == 1 && t.head.asInstanceOf[Int] > 5))

            res = c.sqlFields("select intVal from ObjectValue where intVal > ?", 5).getAll

            assert(res.size == ENTRY_CNT - 5)

            res.foreach(t => assert(t.size == 1 && t.head.asInstanceOf[Int] > 5))
        }

        it("should correctly execute queries with multiple arguments") {
            val res = c.sql("from ObjectValue where intVal in (?, ?, ?)", 1, 2, 3).getAll

            assert(res.size == 3)
        }
    }
}

/**
 * Object for queries.
 */
private case class ObjectValue(
    /** Integer value. */
    @ScalarCacheQuerySqlField
    intVal: Int,

    /** String value. */
    @ScalarCacheQueryTextField
    strVal: String
) {
    override def toString: String = {
        "ObjectValue [" + intVal + ", " + strVal + "]"
    }
}
