package com.plefy.app.backend.inference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.plefy.app.model.CellType;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

/** Unit tests for {@link TypeInferrer}: dominant type selection, confidence, and formats. */
public class TypeInferrerTest {

    private static final double EPS = 1e-9;

    @Test
    public void allEmptyColumnYieldsEmptyZeroConfidence() {
        TypeInferrer.Result r = TypeInferrer.infer(Arrays.asList(null, "", "   "));
        assertEquals(CellType.EMPTY, r.getType());
        assertEquals(0.0, r.getConfidence(), EPS);
        assertNull(r.getFormat());
        assertEquals(0, r.getNonEmptyCount());
    }

    @Test
    public void nullListYieldsEmpty() {
        TypeInferrer.Result r = TypeInferrer.infer(null);
        assertEquals(CellType.EMPTY, r.getType());
        assertEquals(0.0, r.getConfidence(), EPS);
    }

    @Test
    public void pureIntegerColumnFullConfidence() {
        TypeInferrer.Result r = TypeInferrer.infer(Arrays.asList("1", "2", "3", "4"));
        assertEquals(CellType.INTEGER, r.getType());
        assertEquals(1.0, r.getConfidence(), EPS);
        assertEquals(4, r.getNonEmptyCount());
    }

    @Test
    public void mixedColumnPicksDominantWithFractionalConfidence() {
        // 4 integers, 1 text -> INTEGER dominant, confidence 4/5.
        TypeInferrer.Result r = TypeInferrer.infer(
                Arrays.asList("10", "20", "30", "40", "oops"));
        assertEquals(CellType.INTEGER, r.getType());
        assertEquals(4.0 / 5.0, r.getConfidence(), EPS);
        assertEquals(5, r.getNonEmptyCount());
    }

    @Test
    public void emptyCellsAreIgnoredInConfidence() {
        // Empties do not count toward nonEmpty, so confidence stays 1.0.
        TypeInferrer.Result r = TypeInferrer.infer(
                Arrays.asList("1", null, "2", "", "3"));
        assertEquals(CellType.INTEGER, r.getType());
        assertEquals(1.0, r.getConfidence(), EPS);
        assertEquals(3, r.getNonEmptyCount());
    }

    @Test
    public void dateColumnCapturesRepresentativeFormat() {
        TypeInferrer.Result r = TypeInferrer.infer(
                Arrays.asList("2024-03-15", "2024-04-01", "2024-05-20"));
        assertEquals(CellType.DATE, r.getType());
        assertEquals(1.0, r.getConfidence(), EPS);
        assertEquals("yyyy-MM-dd", r.getFormat());
    }

    @Test
    public void nonDateDominantHasNullFormat() {
        TypeInferrer.Result r = TypeInferrer.infer(
                Arrays.asList("apple", "banana", "cherry"));
        assertEquals(CellType.TEXT, r.getType());
        assertNull(r.getFormat());
    }

    @Test
    public void currencyColumnDominant() {
        TypeInferrer.Result r = TypeInferrer.infer(
                Arrays.asList("$1,234.50", "($500)", "$99.99"));
        assertEquals(CellType.CURRENCY, r.getType());
        assertTrue(r.getConfidence() > 0.99);
    }

    @Test
    public void singleValueColumn() {
        TypeInferrer.Result r = TypeInferrer.infer(Collections.singletonList("hello"));
        assertEquals(CellType.TEXT, r.getType());
        assertEquals(1.0, r.getConfidence(), EPS);
    }
}
