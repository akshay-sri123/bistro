package org.conceptoriented.bistro.core;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class Tests {

    @BeforeClass
    public static void setUpClass() {
    }

    @Before
    public void setUp() {
    }

    @Test
    public void schemaTest() // Schema operations
    {
        // Create and configure: schema, tables, keyColumns
        Schema s = new Schema("My Schema");

        Table t = s.createTable("T");

        Column c1 = s.createColumn("A", t);
        Column c2 = s.createColumn("B", t);
        Column c3 = s.createColumn("C", t);

        s.deleteColumn(c2);
        assertEquals(2, t.getColumns().size());

        s.deleteTable(t);
        assertEquals(1, s.getTables().size()); // Only primitive tables
    }

    @Test
    public void dataTest() { // Manual operations table id ranges
        // Prepopulate table for experiments with column data
        Schema s = new Schema("My Schema");
        Table t = s.createTable("My Table");
        Column c1 = s.createColumn("My Column", t);

        long id = t.add();
        assertEquals(0, id);
        long id2 = t.add();
        c1.setValue(id2, 1.0);
        assertEquals(1.0, (double) c1.getValue(id2), Double.MIN_VALUE);

        Column c2 = s.createColumn("My Column 2", t);
        long id3 = t.add();
        c2.setValue(id3, "StringValue");
        assertEquals("StringValue", (String) c2.getValue(id3));

        assertEquals(0, t.getIdRange().start);
        assertEquals(3, t.getIdRange().end);

        // Records.
        // Working with multiple keyColumns (records)
        long id4 = t.add();
        List<Column> cols = Arrays.asList(c1, c2);
        List<Object> vals = Arrays.asList(2.0, "StringValue 2");
        t.setValues(id4, cols, vals);
        assertEquals(2.0, (double) c1.getValue(id4), Double.MIN_VALUE);
        assertEquals("StringValue 2", (String) c2.getValue(id4));

        // Record search
        vals = Arrays.asList(2.0, "StringValue 2");
        long found_id = t.find(vals, cols, false); // Record exist
        assertEquals(id4, found_id);

        vals = Arrays.asList(2.0, "Non-existing value");
        found_id = t.find(vals, cols, false); // Record does not exist
        assertTrue(found_id < 0);

        vals = Arrays.asList(5.0, "String value 5"); // Record does not exist
        found_id = t.find(vals, cols, true); // Add if not found
        assertEquals(4, found_id);

        vals = Arrays.asList(5L, "String value 5"); // Record exist but we specify different type (integer instead of double)
        found_id = t.find(vals, cols, false);
        assertTrue(found_id < 0); // Not found because of different types: Long is not comparable with Double
    }

    @Test
    public void removeTest() { // Rest deletion operations

        Schema s = new Schema("My Schema");
        Table t = s.createTable("T");
        Column c1 = s.createColumn("C1", t);
        Column c2 = s.createColumn("C1", t);

        List<Column> cols = Arrays.asList(c1, c2);
        long id;
        long count;

        t.add(5);
        t.setValues(0, cols, Arrays.asList(1.0, Instant.parse("2018-01-01T00:01:00.000Z"))); // Oldest record
        t.setValues(1, cols, Arrays.asList(2.0, Instant.parse("2018-01-01T00:02:00.000Z")));
        t.setValues(2, cols, Arrays.asList(3.0, Instant.parse("2018-01-01T00:03:00.000Z")));
        t.setValues(3, cols, Arrays.asList(4.0, Instant.parse("2018-01-01T00:04:00.000Z")));
        t.setValues(4, cols, Arrays.asList(5.0, Instant.parse("2018-01-01T00:05:00.000Z"))); // Newest record

        // Remove all c1<2.0 which means 1 record with id 0
        count = t.remove(c1, 2.0);
        assertEquals(1, count);
        assertEquals(0, t.getRemovedRange().start);
        assertEquals(1, t.getRemovedRange().end);
        assertEquals(4, t.getLength());

        // This record has been already deleted before so no changes expected
        count = t.remove(c2, Instant.parse("2018-01-01T00:02:00.000Z"));
        assertEquals(0, count);
        assertEquals(0, t.getRemovedRange().start);
        assertEquals(1, t.getRemovedRange().end);
        assertEquals(4, t.getLength());

        // Remove 2 older records
        count = t.remove(c2, Instant.parse("2018-01-01T00:03:03.000Z"));
        assertEquals(2, count);
        assertEquals(0, t.getRemovedRange().start);
        assertEquals(3, t.getRemovedRange().end);
        assertEquals(2, t.getLength());

        s.evaluate();
        // After evaluation the removed range has to be reset
        assertEquals(3, t.getRemovedRange().start);
        assertEquals(3, t.getRemovedRange().end);
        assertEquals(2, t.getLength());

        // Remove more records than exist by specifying very large threshold
        count = t.remove(c2, Instant.parse("2018-01-01T01:03:03.000Z"));
        assertEquals(2, count);
        assertEquals(3, t.getRemovedRange().start);
        assertEquals(5, t.getRemovedRange().end);
        assertEquals(0, t.getLength());

    }

}
