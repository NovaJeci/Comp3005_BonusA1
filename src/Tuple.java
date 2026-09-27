import java.util.List;

/**
 * One row. Equality/hashCode delegate to List<Value>'s own element-wise
 * contract, which in turn uses Value.equals()/hashCode() -- this is what
 * lets a java.util.Set<Tuple> implement "a relation is a set of tuples"
 * (duplicate collapsing) for free, everywhere a relation is built.
 */
public final class Tuple {
    public final List<Value> values;

    public Tuple(List<Value> values) {
        this.values = values;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Tuple other && values.equals(other.values);
    }

    @Override
    public int hashCode() {
        return values.hashCode();
    }
}
