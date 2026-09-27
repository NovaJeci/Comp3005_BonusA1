import java.util.List;

/**
 * An ordered list of columns, plus the logic for resolving an AttrRef
 * (from a parsed Condition or project list) to a column index.
 */
public final class Schema {
    public final List<Attribute> columns;

    public Schema(List<Attribute> columns) {
        this.columns = columns;
    }

    public int size() {
        return columns.size();
    }

    public Attribute get(int i) {
        return columns.get(i);
    }

    /**
     * Resolves an attribute reference. A QUALIFIED reference (Emp.DID) must
     * match exactly one (qualifier, name) pair. An UNQUALIFIED reference
     * (Age) must match exactly one column by name alone -- zero matches is
     * an unknown-attribute name error, and more than one match is an
     * ambiguous-attribute name error (this can genuinely happen after a
     * times/join, e.g. two relations that both have a "Name" column).
     */
    public int resolve(Operand.AttrRef ref) {
        if (ref.qualifier != null) {
            for (int i = 0; i < columns.size(); i++) {
                Attribute a = columns.get(i);
                if (a.qualifier.equals(ref.qualifier) && a.name.equals(ref.name)) {
                    return i;
                }
            }
            throw new NameException("unknown attribute '" + ref.qualifier + "." + ref.name + "'");
        }
        int found = -1;
        for (int i = 0; i < columns.size(); i++) {
            if (columns.get(i).name.equals(ref.name)) {
                if (found != -1) {
                    throw new NameException("ambiguous attribute '" + ref.name
                            + "' -- qualify it, e.g. " + columns.get(found).qualifier + "." + ref.name);
                }
                found = i;
            }
        }
        if (found == -1) {
            throw new NameException("unknown attribute '" + ref.name + "'");
        }
        return found;
    }
}
