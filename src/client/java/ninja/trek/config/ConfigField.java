package ninja.trek.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ConfigField {
    String name() default "";  // Display name in config UI
    String description() default ""; // Optional description/tooltip

    double min() default Double.NEGATIVE_INFINITY;  // Min value for numbers
    double max() default Double.POSITIVE_INFINITY;  // Max value for numbers

    boolean sliderControl() default false;  // Use slider instead of text input for numbers

    // Special value to indicate this field should not be configurable at runtime
    boolean readonly() default false;
}