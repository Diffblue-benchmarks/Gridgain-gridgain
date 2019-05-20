/*
 * Copyright 2004-2019 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.expression.aggregate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.TreeSet;

import org.apache.ignite.internal.processors.query.h2.H2MemoryTracker;
import org.h2.api.ErrorCode;
import org.h2.engine.Constants;
import org.h2.engine.Database;
import org.h2.engine.Session;
import org.h2.message.DbException;
import org.h2.value.Value;
import org.h2.value.ValueNull;

/**
 * Data stored while calculating an aggregate that needs collecting of all
 * values or a distinct aggregate.
 *
 * <p>
 * NULL values are not collected. {@link #getValue(Database, int)}
 * method returns {@code null}. Use {@link #getArray()} for instances of this
 * class instead.
 * </p>
 */
class AggregateDataCollecting extends AggregateData implements Iterable<Value> {

    private final boolean distinct;

    Collection<Value> values;

    private Value shared;

    /**
     * Creates new instance of data for collecting aggregates.
     *
     * @param distinct if distinct is used
     */
    AggregateDataCollecting(boolean distinct) {
        this.distinct = distinct;
    }

    @Override
    void add(Session ses, Value v) {
        if (v == ValueNull.INSTANCE) {
            return;
        }
        Collection<Value> c = values;
        H2MemoryTracker memTracker;
        if (c == null) {
            values = c = distinct ? new TreeSet<>(ses.getDatabase().getCompareMode()) : new ArrayList<Value>();
        }
        if (c.add(v) && (memTracker = ses.queryMemoryTracker()) != null) {
            long size = distinct ? 40 /* TreeMap.Entry */ : Constants.MEMORY_POINTER;

            if (v.track())
                size += v.getMemory();

            memTracker.allocate(size);
        }
    }

    @Override
    Value getValue(Database database, int dataType) {
        return null;
    }

    /**
     * Returns the count of values.
     *
     * @return the count of values
     */
    int getCount() {
        return values != null ? values.size() : 0;
    }

    /**
     * Returns array with values or {@code null}.
     *
     * @return array with values or {@code null}
     */
    Value[] getArray() {
        Collection<Value> values = this.values;
        if (values == null) {
            return null;
        }
        return values.toArray(new Value[0]);
    }

    @Override
    public Iterator<Value> iterator() {
        return values != null ? values.iterator() : Collections.<Value>emptyIterator();
    }

    /**
     * Sets value of a shared argument.
     *
     * @param shared the shared value
     */
    void setSharedArgument(Value shared) {
        if (this.shared == null) {
            this.shared = shared;
        } else if (!this.shared.equals(shared)) {
            throw DbException.get(ErrorCode.INVALID_VALUE_2, "Inverse distribution function argument",
                    this.shared.getTraceSQL() + "<>" + shared.getTraceSQL());
        }
    }

    /**
     * Returns value of a shared argument.
     *
     * @return value of a shared argument
     */
    Value getSharedArgument() {
        return shared;
    }

}
