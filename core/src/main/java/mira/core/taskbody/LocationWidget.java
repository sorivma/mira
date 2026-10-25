package mira.core.taskbody;

public final class LocationWidget extends TaskWidget {
    public int dimension, x, y, z;
    public LocationWidget() { super("Location"); }
    private LocationWidget(String id, String title) { super(id, title); }
    @Override public LocationWidget copy() {
        LocationWidget value = new LocationWidget(id, title);
        value.dimension = dimension; value.x = x; value.y = y; value.z = z; return value;
    }
}
