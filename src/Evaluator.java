import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Executes a parsed Expr tree bottom-up against a set of base relations.
 *
 * Instrumentation (Section 8.2): selectComparisons and joinComparisons
 * count actual work done, not an estimate -- selectComparisons increments
 * once per tuple examined by a select, joinComparisons once per PAIR
 * examined by a join. These are public fields rather than something
 * returned only at the end, since a single query can contain more than
 * one select/join and the performance study only ever runs one top-level
 * operator per measurement anyway.
 */
public final class Evaluator {
    private final Map<String, Relation> baseRelations;

    public long selectComparisons = 0;
    public long joinComparisons = 0;

    public Evaluator(Map<String, Relation> baseRelations) {
        this.baseRelations = baseRelations;
    }

    public Relation evaluate(Expr expr) {
        if (expr instanceof Expr.RelationRef r) {
            Relation rel = baseRelations.get(r.name);
            if (rel == null) {
                throw new NameException("unknown relation '" + r.name + "'");
            }
            return rel;
        }
        if (expr instanceof Expr.SelectOp s) {
            return selectOp(s.condition, evaluate(s.input));
        }
        if (expr instanceof Expr.ProjectOp p) {
            return projectOp(p.attrs, evaluate(p.input));
        }
        if (expr instanceof Expr.RenameOp rn) {
            return renameOp(rn.newName, evaluate(rn.input));
        }
        if (expr instanceof Expr.BinaryOp b) {
            Relation left = evaluate(b.left);
            Relation right = evaluate(b.right);
            switch (b.kind) {
                case UNION: return unionOp(left, right);
                case INTERSECT: return intersectOp(left, right);
                case MINUS: return minusOp(left, right);
                case TIMES: return timesOp(left, right);
                default: throw new IllegalStateException("unreachable");
            }
        }
        if (expr instanceof Expr.JoinOp j) {
            return joinOp(j.condition, evaluate(j.left), evaluate(j.right));
        }
        throw new IllegalStateException("unknown Expr node: " + expr.getClass());
    }

    // -----------------------------------------------------------------
    // unary operators
    // -----------------------------------------------------------------

    private Relation selectOp(Condition cond, Relation input) {
        Set<Tuple> result = new LinkedHashSet<>();
        for (Tuple t : input.tuples) {
            selectComparisons++; // once per tuple EXAMINED, whether or not it's kept
            if (evalCondition(cond, t, input.schema)) {
                result.add(t);
            }
        }
        return new Relation(input.schema, result); // schema unchanged, per Section 4.3
    }

    private Relation projectOp(List<Operand.AttrRef> attrs, Relation input) {
        List<Integer> indices = new ArrayList<>();
        Set<Attribute> seen = new HashSet<>();
        for (Operand.AttrRef ref : attrs) {
            int idx = input.schema.resolve(ref);
            Attribute resolved = input.schema.get(idx);
            // case 24: project[Name, Name](R) is rejected rather than silently collapsed --
            // a schema with the same column twice would make every later reference to it
            // ambiguous, so treating the duplicate as an error keeps the rest of the system
            // (Schema.resolve's ambiguity check especially) meaningful.
            if (!seen.add(resolved)) {
                throw new SchemaException("duplicate attribute '" + resolved + "' in project list");
            }
            indices.add(idx);
        }
        List<Attribute> newColumns = new ArrayList<>();
        for (int idx : indices) newColumns.add(input.schema.get(idx));
        Schema newSchema = new Schema(newColumns);

        Set<Tuple> result = new LinkedHashSet<>(); // Set => duplicate tuples removed, per spec
        for (Tuple t : input.tuples) {
            List<Value> vals = new ArrayList<>();
            for (int idx : indices) vals.add(t.values.get(idx));
            result.add(new Tuple(vals));
        }
        return new Relation(newSchema, result);
    }

    private Relation renameOp(String newName, Relation input) {
        List<Attribute> newColumns = new ArrayList<>();
        for (Attribute a : input.schema.columns) {
            newColumns.add(new Attribute(newName, a.name)); // same attribute names, new owner
        }
        return new Relation(new Schema(newColumns), input.tuples);
    }

    // -----------------------------------------------------------------
    // binary operators
    // -----------------------------------------------------------------

    /**
     * Builds the combined schema for times/join and checks for a qualified-name collision
     * ("If qualified names still collide, that is an error", Section 4.3). This is exactly
     * why case 20's self join needs rename[E2](Emp): without it, both sides of "Emp join Emp"
     * would still be qualified "Emp", and every column would collide with itself.
     */
    private Schema crossSchema(Relation left, Relation right) {
        List<Attribute> newColumns = new ArrayList<>(left.schema.columns);
        newColumns.addAll(right.schema.columns);
        Set<Attribute> seen = new HashSet<>();
        for (Attribute a : newColumns) {
            if (!seen.add(a)) {
                throw new SchemaException("'" + a
                        + "' appears on both sides -- use rename to disambiguate before combining these relations");
            }
        }
        return new Schema(newColumns);
    }

    private Relation timesOp(Relation left, Relation right) {
        Schema newSchema = crossSchema(left, right);
        Set<Tuple> result = new LinkedHashSet<>();
        for (Tuple lt : left.tuples) {
            for (Tuple rt : right.tuples) {
                List<Value> vals = new ArrayList<>(lt.values);
                vals.addAll(rt.values);
                result.add(new Tuple(vals));
            }
        }
        return new Relation(newSchema, result);
    }

    /**
     * join[c] is DEFINED (Section 4.3) as times followed by select[c] -- same result set as
     * timesOp(left,right) filtered by evalCondition. It's implemented as one direct nested
     * loop instead of literally calling timesOp()+selectOp() so that joinComparisons counts
     * exactly the join's own pairs, without also incrementing selectComparisons (which should
     * only count actual select[...] operators elsewhere in a query).
     */
    private Relation joinOp(Condition cond, Relation left, Relation right) {
        Schema newSchema = crossSchema(left, right);
        Set<Tuple> result = new LinkedHashSet<>();
        for (Tuple lt : left.tuples) {
            for (Tuple rt : right.tuples) {
                joinComparisons++; // every pair, whether or not it matches -- see Section 8.2
                List<Value> vals = new ArrayList<>(lt.values);
                vals.addAll(rt.values);
                Tuple combined = new Tuple(vals);
                if (evalCondition(cond, combined, newSchema)) {
                    result.add(combined);
                }
            }
        }
        return new Relation(newSchema, result);
    }

    private Relation unionOp(Relation left, Relation right) {
        checkUnionCompatible(left, right, "union");
        Set<Tuple> result = new LinkedHashSet<>(left.tuples);
        result.addAll(right.tuples);
        return new Relation(left.schema, result); // schema of the LEFT input, per Section 4.3
    }

    private Relation intersectOp(Relation left, Relation right) {
        checkUnionCompatible(left, right, "intersect");
        Set<Tuple> result = new LinkedHashSet<>();
        for (Tuple t : left.tuples) {
            if (right.tuples.contains(t)) result.add(t);
        }
        return new Relation(left.schema, result);
    }

    private Relation minusOp(Relation left, Relation right) {
        checkUnionCompatible(left, right, "minus");
        Set<Tuple> result = new LinkedHashSet<>();
        for (Tuple t : left.tuples) {
            if (!right.tuples.contains(t)) result.add(t);
        }
        return new Relation(left.schema, result);
    }

    /**
     * Union compatibility (Section 4.3): same attribute COUNT, same NAMES in the same order,
     * and compatible TYPES position by position. Qualifiers are deliberately NOT compared --
     * only the left relation's schema survives the operation (see the three methods above),
     * so its qualifiers are what matter downstream; requiring both sides' qualifiers to match
     * too would make e.g. "Emp union rename[Emp](OtherEmp)" fail even when the actual column
     * names and data line up.
     */
    private void checkUnionCompatible(Relation left, Relation right, String opName) {
        if (left.schema.size() != right.schema.size()) {
            throw new SchemaException("cannot " + opName + ": relations have "
                    + left.schema.size() + " and " + right.schema.size() + " attributes");
        }
        for (int i = 0; i < left.schema.size(); i++) {
            String ln = left.schema.get(i).name;
            String rn = right.schema.get(i).name;
            if (!ln.equals(rn)) {
                throw new SchemaException("cannot " + opName + ": attribute " + (i + 1)
                        + " is named '" + ln + "' on the left but '" + rn + "' on the right");
            }
        }
        for (int i = 0; i < left.schema.size(); i++) {
            String lt = columnType(left, i);
            String rt = columnType(right, i);
            if (lt != null && rt != null && !lt.equals(rt)) {
                throw new SchemaException("cannot " + opName + ": attribute '" + left.schema.get(i).name
                        + "' is " + lt + " on the left but " + rt + " on the right");
            }
        }
    }

    /** Infers a column's type from its data; null if the relation has no tuples to sample from. */
    private String columnType(Relation r, int index) {
        for (Tuple t : r.tuples) {
            return t.values.get(index).typeName();
        }
        return null;
    }

    // -----------------------------------------------------------------
    // condition evaluation
    // -----------------------------------------------------------------

    private boolean evalCondition(Condition cond, Tuple tuple, Schema schema) {
        if (cond instanceof Condition.Or o) {
            return evalCondition(o.left, tuple, schema) || evalCondition(o.right, tuple, schema);
        }
        if (cond instanceof Condition.And a) {
            return evalCondition(a.left, tuple, schema) && evalCondition(a.right, tuple, schema);
        }
        if (cond instanceof Condition.Not n) {
            return !evalCondition(n.inner, tuple, schema);
        }
        if (cond instanceof Condition.Comparison c) {
            Value left = resolveOperand(c.left, tuple, schema);
            Value right = resolveOperand(c.right, tuple, schema);
            return compare(left, c.op, right);
        }
        throw new IllegalStateException("unknown Condition node: " + cond.getClass());
    }

    private Value resolveOperand(Operand operand, Tuple tuple, Schema schema) {
        if (operand instanceof Operand.NumberLit n) {
            return new Value.NumValue(new BigDecimal(n.text));
        }
        if (operand instanceof Operand.StringLit s) {
            return new Value.StrValue(s.value);
        }
        if (operand instanceof Operand.AttrRef ref) {
            return tuple.values.get(schema.resolve(ref));
        }
        throw new IllegalStateException("unknown Operand node: " + operand.getClass());
    }

    private boolean compare(Value left, TokenType op, Value right) {
        if (!left.getClass().equals(right.getClass())) {
            throw new TypeException("cannot compare " + left.typeName() + " and " + right.typeName());
        }
        switch (op) {
            case EQ: return left.equals(right);
            case NEQ: return !left.equals(right);
            case LT: return orderCompare(left, right) < 0;
            case LE: return orderCompare(left, right) <= 0;
            case GT: return orderCompare(left, right) > 0;
            case GE: return orderCompare(left, right) >= 0;
            default: throw new IllegalStateException("not a comparison operator: " + op);
        }
    }

    private int orderCompare(Value left, Value right) {
        if (left instanceof Value.NumValue ln && right instanceof Value.NumValue rn) {
            return ln.value.compareTo(rn.value);
        }
        if (left instanceof Value.StrValue ls && right instanceof Value.StrValue rs) {
            return ls.value.compareTo(rs.value);
        }
        throw new IllegalStateException("unreachable -- compare() already type-checked this");
    }
}
