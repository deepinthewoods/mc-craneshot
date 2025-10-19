package ninja.trek.nodes.model;

public enum EasingCurve {
    LINEAR,
    EASE_IN,
    EASE_OUT,
    EASE_IN_OUT;

    public double apply(double t) {
        t = Math.max(0.0, Math.min(1.0, t));
        return switch (this) {
            case LINEAR -> t;
            case EASE_IN -> t * t;
            case EASE_OUT -> 1.0 - (1.0 - t) * (1.0 - t);
            case EASE_IN_OUT -> {
                if (t < 0.5) {
                    double u = t * 2.0;
                    yield 0.5 * u * u;
                } else {
                    double u = (1.0 - t) * 2.0;
                    yield 1.0 - 0.5 * u * u;
                }
            }
        };
    }
}

