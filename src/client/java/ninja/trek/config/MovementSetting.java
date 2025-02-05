package ninja.trek.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface MovementSetting {
    String label();
    String description() default "";
    double min() default 0;
    double max() default 100;
    MovementSettingType type() default MovementSettingType.SLIDER;
}