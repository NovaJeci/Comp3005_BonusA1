import java.util.Set;

/**
 * A relation: a schema plus a SET of tuples. Using LinkedHashSet wherever
 * one of these is built (RelationLoader, Evaluator) gives duplicate
 * removal automatically while keeping a deterministic (insertion) order
 * for output and testing.
 */
public final class Relation {
    public final Schema schema;
    public final Set<Tuple> tuples;

    public Relation(Schema schema, Set<Tuple> tuples) {
        this.schema = schema;
        this.tuples = tuples;
    }
}
