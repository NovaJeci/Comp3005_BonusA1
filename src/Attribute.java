/**
 * One schema column. `qualifier` is the name of the relation that
 * currently "owns" this column -- it is NEVER null in this design:
 *
 *  - A base relation's columns are qualified by the relation's own name
 *    from the moment RelationLoader creates them.
 *  - rename[N](...) replaces every column's qualifier with N.
 *  - select/project/union/intersect/minus never change qualifiers.
 *  - times/join concatenate both sides' columns as-is.
 *
 * This is what makes the times/join collision check in Evaluator work:
 * "Emp times Emp" collides because both sides' columns are still
 * qualified "Emp", but "rename[E2](Emp) times Emp" doesn't, because one
 * side's columns are now qualified "E2". It's also why case 20 (the self
 * join) genuinely needs rename -- see Evaluator.crossSchema().
 */
public final class Attribute {
    public final String qualifier;
    public final String name;

    public Attribute(String qualifier, String name) {
        this.qualifier = qualifier;
        this.name = name;
    }

    public String qualifiedName() {
        return qualifier + "." + name;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Attribute other && qualifier.equals(other.qualifier) && name.equals(other.name);
    }

    @Override
    public int hashCode() {
        return qualifier.hashCode() * 31 + name.hashCode();
    }

    @Override
    public String toString() {
        return qualifiedName();
    }
}
