package ninja.trek;

public interface IMouseMixin {
    double getCapturedDeltaX();
    double getCapturedDeltaY();
    double getLastScrollValue();
    void setLastScrollValue(double value);
}
